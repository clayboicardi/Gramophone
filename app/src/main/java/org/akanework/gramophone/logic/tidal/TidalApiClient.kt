package org.akanework.gramophone.logic.tidal

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.akanework.gramophone.BuildConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TidalApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0
    private val tokenMutex = Mutex()

    private suspend fun ensureToken(): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        val existing = accessToken
        if (existing != null && now < tokenExpiresAt - 60_000) return existing
        return fetchToken().also { accessToken = it }
    }

    private suspend fun fetchToken(): String = withContext(Dispatchers.IO) {
        val credentials = "${BuildConfig.TIDAL_CLIENT_ID}:${BuildConfig.TIDAL_CLIENT_SECRET}"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()
        val request = Request.Builder()
            .url("https://auth.tidal.com/v1/oauth2/token")
            .header("Authorization", "Basic $encoded")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        val json = JSONObject(response.body!!.string())
        if (!response.isSuccessful) {
            throw TidalApiException("Auth failed: ${response.code} ${json.optString("error")}")
        }
        tokenExpiresAt = System.currentTimeMillis() + json.getLong("expires_in") * 1000
        json.getString("access_token")
    }

    suspend fun search(query: String, countryCode: String = "US"): TidalSearchResult =
        withContext(Dispatchers.IO) {
            val token = ensureToken()
            val albums = searchAlbums(query, countryCode, token)
            val tracks = searchTracks(query, countryCode, token)
            TidalSearchResult(albums, tracks)
        }

    private fun searchAlbums(
        query: String, countryCode: String, token: String
    ): List<TidalAlbum> {
        val url = "https://openapi.tidal.com/v2/searchResults/${encodeQuery(query)}" +
                "/relationships/albums?countryCode=$countryCode&include=albums"
        val json = executeGet(url, token)
        val included = json.optJSONArray("included") ?: return emptyList()
        val albums = mutableListOf<TidalAlbum>()
        for (i in 0 until included.length()) {
            val item = included.getJSONObject(i)
            if (item.getString("type") != "albums") continue
            val attrs = item.getJSONObject("attributes")
            val id = item.getString("id")
            // Extract artist name from relationships if available
            val artistName = extractArtistName(item) ?: ""
            // Extract cover art URL from relationships
            val coverUrl = extractCoverUrl(item)
            albums.add(
                TidalAlbum(
                    id = id,
                    title = attrs.getString("title"),
                    artistName = artistName,
                    coverUrl = coverUrl,
                    trackCount = attrs.optInt("numberOfItems", 0),
                    releaseDate = attrs.optString("releaseDate").ifEmpty { null }
                )
            )
        }
        return albums
    }

    private fun searchTracks(
        query: String, countryCode: String, token: String
    ): List<TidalTrack> {
        val url = "https://openapi.tidal.com/v2/searchResults/${encodeQuery(query)}" +
                "/relationships/tracks?countryCode=$countryCode&include=tracks"
        val json = executeGet(url, token)
        val included = json.optJSONArray("included") ?: return emptyList()
        val tracks = mutableListOf<TidalTrack>()
        for (i in 0 until included.length()) {
            val item = included.getJSONObject(i)
            if (item.getString("type") != "tracks") continue
            val attrs = item.getJSONObject("attributes")
            val id = item.getString("id")
            val artistName = extractArtistName(item) ?: ""
            val coverUrl = extractCoverUrl(item)
            tracks.add(
                TidalTrack(
                    id = id,
                    title = attrs.getString("title"),
                    artistName = artistName,
                    albumTitle = null,
                    duration = parseIsoDuration(attrs.optString("duration", "PT0S")),
                    trackNumber = attrs.optInt("trackNumber", 0),
                    coverUrl = coverUrl
                )
            )
        }
        return tracks
    }

    private fun executeGet(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.api+json")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body!!.string()
        if (response.code == 401) {
            // Token expired, caller should retry
            accessToken = null
            throw TidalApiException("Unauthorized (token expired)")
        }
        if (!response.isSuccessful) {
            throw TidalApiException("API error: ${response.code} $body")
        }
        return JSONObject(body)
    }

    private fun extractArtistName(resource: JSONObject): String? {
        val relationships = resource.optJSONObject("relationships") ?: return null
        val artists = relationships.optJSONObject("artists") ?: return null
        val data = artists.optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        // The artist name might be in meta or we might need to look it up
        // in the included array. For now, return the first artist's name if available.
        val first = data.getJSONObject(0)
        return first.optJSONObject("meta")?.optString("name")
    }

    private fun extractCoverUrl(resource: JSONObject): String? {
        val relationships = resource.optJSONObject("relationships") ?: return null
        // Try coverArt or imageCover relationship
        val coverArt = relationships.optJSONObject("coverArt")
            ?: relationships.optJSONObject("album")
        val data = coverArt?.optJSONArray("data") ?: coverArt?.optJSONObject("data")
        // If we have an image ID, construct the URL
        val imageId = when {
            data is JSONObject -> data.optString("id")
            data is org.json.JSONArray && data.length() > 0 -> data.getJSONObject(0).optString("id")
            else -> null
        }
        if (imageId != null && imageId.isNotEmpty()) {
            // Tidal image URL pattern: replace dashes with slashes in UUID
            val path = imageId.replace("-", "/")
            return "https://resources.tidal.com/images/$path/320x320.jpg"
        }
        return null
    }

    private fun encodeQuery(query: String): String {
        return java.net.URLEncoder.encode(query, "UTF-8")
    }

    companion object {
        fun parseIsoDuration(iso: String): Int {
            // Parse ISO 8601 duration like "PT3M45S" or "PT46M17S" to seconds
            var seconds = 0
            val matcher = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(iso)
            if (matcher != null) {
                val hours = matcher.groupValues[1].toIntOrNull() ?: 0
                val minutes = matcher.groupValues[2].toIntOrNull() ?: 0
                val secs = matcher.groupValues[3].toIntOrNull() ?: 0
                seconds = hours * 3600 + minutes * 60 + secs
            }
            return seconds
        }
    }
}

class TidalApiException(message: String) : Exception(message)
