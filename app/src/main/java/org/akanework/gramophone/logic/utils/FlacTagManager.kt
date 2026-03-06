package org.akanework.gramophone.logic.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import ealvatag.audio.AudioFileIO
import ealvatag.tag.FieldKey
import ealvatag.tag.TagOptionSingleton
import ealvatag.tag.vorbiscomment.VorbisCommentTag
import java.io.File

/**
 * Utility for reading and writing FLAC Vorbis comments via ealvatag.
 * Optimized for Clay's music library (36 canonical genres, MusicBrainz IDs).
 */
object FlacTagManager {

    private const val TAG = "FlacTagManager"

    // ── Clay's 36 Canonical Genres ──────────────────────────────────────

    val HIP_HOP_GENRES = listOf(
        "Abstract/Experimental",
        "Alternative Hip-Hop",
        "Boom Bap",
        "Cloud Rap",
        "Conscious Hip-Hop",
        "Crunk",
        "Drill",
        "East Coast Hip-Hop",
        "Gangsta Rap",
        "Griselda/Hardcore",
        "Hip-Hop",
        "Horrorcore",
        "Jazz Rap",
        "Melodic Rap",
        "Pop Rap",
        "Southern Hip-Hop",
        "Trap",
        "West Coast Hip-Hop",
    )

    val OTHER_GENRES = listOf(
        "Alternative",
        "Blues",
        "Classical",
        "Dancehall",
        "Electronic",
        "Folk",
        "Funk",
        "Gospel",
        "Grime",
        "Jazz",
        "Metal",
        "Pop",
        "Punk",
        "R&B",
        "Reggae",
        "Rock",
        "Soul",
        "Soundtrack",
    )

    val CANONICAL_GENRES: List<String> = (HIP_HOP_GENRES + OTHER_GENRES).sorted()

    // ── Data Classes ────────────────────────────────────────────────────

    data class AudioProperties(
        val sampleRate: Int,      // e.g. 44100, 96000
        val bitDepth: Int,        // e.g. 16, 24
        val channels: Int,        // e.g. 2
        val durationSeconds: Int, // total seconds
        val bitRate: Int,         // kbps
        val format: String,       // e.g. "FLAC"
    )

    data class ArtInfo(
        val width: Int,
        val height: Int,
        val mimeType: String,  // e.g. "image/jpeg"
        val sizeBytes: Int,
    )

    // ── Initialization ──────────────────────────────────────────────────

    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            TagOptionSingleton.getInstance().isAndroid = true
            initialized = true
        }
    }

    // ── Read Operations ─────────────────────────────────────────────────

    /**
     * Read all Vorbis comments from a FLAC file as key-value pairs.
     * Keys are uppercased for consistency (FLAC tags are case-insensitive).
     */
    fun readAllTags(filePath: String): Map<String, String> {
        ensureInitialized()
        return try {
            val audioFile = AudioFileIO.read(File(filePath))
            val tagOpt = audioFile.tag
            val tag = if (tagOpt.isPresent) tagOpt.get() else return emptyMap()
            val result = linkedMapOf<String, String>()

            // Standard fields via FieldKey for reliable access
            for (fieldKey in STANDARD_FIELDS) {
                val value = try {
                    tag.getFirst(fieldKey)
                } catch (_: Exception) {
                    null
                }
                if (!value.isNullOrBlank()) {
                    result[fieldKey.name] = value
                }
            }

            // Raw iteration to catch non-standard fields (MBIDs, custom tags)
            try {
                for (field in tag.fields) {
                    val key = field.id.uppercase()
                    if (key !in result && field.toString().isNotBlank()) {
                        val value = field.toString()
                        if (value.isNotBlank()) {
                            result[key] = value
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to iterate raw fields for $filePath", e)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read tags from $filePath", e)
            emptyMap()
        }
    }

    /**
     * Read audio properties (sample rate, bit depth, channels, duration).
     */
    fun readAudioProperties(filePath: String): AudioProperties? {
        ensureInitialized()
        return try {
            val audioFile = AudioFileIO.read(File(filePath))
            val header = audioFile.audioHeader
            AudioProperties(
                sampleRate = header.sampleRate,
                bitDepth = header.bitsPerSample,
                channels = header.channelCount,
                durationSeconds = header.durationAsDouble.toInt(),
                bitRate = header.bitRate,
                format = header.format,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audio properties from $filePath", e)
            null
        }
    }

    /**
     * Read embedded album art info (dimensions, format, size) without decoding pixels.
     */
    fun readArtInfo(filePath: String): ArtInfo? {
        ensureInitialized()
        return try {
            val audioFile = AudioFileIO.read(File(filePath))
            val tagOpt = audioFile.tag
            val tag = if (tagOpt.isPresent) tagOpt.get() else return null
            val artOpt = tag.firstArtwork
            val artwork = if (artOpt.isPresent) artOpt.get() else return null
            val imageData = artwork.binaryData ?: return null
            ArtInfo(
                width = artwork.width,
                height = artwork.height,
                mimeType = artwork.mimeType ?: "unknown",
                sizeBytes = imageData.size,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read art info from $filePath", e)
            null
        }
    }

    /**
     * Get raw album art bytes (for display). Returns null if no art embedded.
     */
    fun readArtBytes(filePath: String): ByteArray? {
        ensureInitialized()
        return try {
            val audioFile = AudioFileIO.read(File(filePath))
            val tagOpt = audioFile.tag
            val tag = if (tagOpt.isPresent) tagOpt.get() else return null
            val artOpt = tag.firstArtwork
            if (artOpt.isPresent) artOpt.get().binaryData else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read art bytes from $filePath", e)
            null
        }
    }

    // ── Write Operations ────────────────────────────────────────────────

    /**
     * Write multiple tags to a FLAC file. Keys should be FieldKey names
     * (TITLE, ARTIST, ALBUM, etc.) or raw Vorbis comment names.
     *
     * @return Result.success if written, Result.failure with exception otherwise.
     */
    fun writeTags(filePath: String, tags: Map<String, String>): Result<Unit> {
        ensureInitialized()
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("File not found: $filePath"))
            }
            if (!file.canWrite()) {
                return Result.failure(SecurityException("No write permission for: $filePath"))
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrSetNewDefault

            for ((key, value) in tags) {
                // Try to map the key to a FieldKey first (standard fields)
                val fieldKey = FIELD_KEY_MAP[key.uppercase()]
                if (fieldKey != null) {
                    tag.setField(fieldKey, value)
                } else if (tag is VorbisCommentTag) {
                    // Write as raw Vorbis comment for non-standard keys
                    tag.setField(key.uppercase(), value)
                }
            }

            audioFile.save()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write tags to $filePath", e)
            Result.failure(e)
        }
    }

    // ── MediaStore Refresh ──────────────────────────────────────────────

    /**
     * Trigger a MediaStore rescan for the given file so the system
     * picks up updated tags.
     */
    fun triggerMediaScan(context: Context, filePath: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            arrayOf("audio/flac"),
            null
        )
    }

    // ── Genre Utilities ─────────────────────────────────────────────────

    /**
     * Check if a genre is in the canonical 36-value set (case-insensitive).
     */
    fun isCanonicalGenre(genre: String): Boolean {
        return CANONICAL_GENRES.any { it.equals(genre, ignoreCase = true) }
    }

    /**
     * Check if a genre is a hip-hop subgenre.
     */
    fun isHipHopGenre(genre: String): Boolean {
        return HIP_HOP_GENRES.any { it.equals(genre, ignoreCase = true) }
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    /** Standard fields we always try to read via FieldKey for reliable access. */
    private val STANDARD_FIELDS = listOf(
        FieldKey.TITLE,
        FieldKey.ARTIST,
        FieldKey.ALBUM,
        FieldKey.ALBUM_ARTIST,
        FieldKey.GENRE,
        FieldKey.YEAR,
        FieldKey.TRACK,
        FieldKey.DISC_NO,
        FieldKey.MUSICBRAINZ_ARTISTID,
        FieldKey.MUSICBRAINZ_RELEASEID,
        FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID,
        FieldKey.MUSICBRAINZ_TRACK_ID,
        FieldKey.ENCODER,
        FieldKey.COMMENT,
    )

    /** Maps uppercase string keys to FieldKey enum values for write operations. */
    private val FIELD_KEY_MAP: Map<String, FieldKey> = buildMap {
        put("TITLE", FieldKey.TITLE)
        put("ARTIST", FieldKey.ARTIST)
        put("ALBUM", FieldKey.ALBUM)
        put("ALBUMARTIST", FieldKey.ALBUM_ARTIST)
        put("ALBUM_ARTIST", FieldKey.ALBUM_ARTIST)
        put("ALBUM ARTIST", FieldKey.ALBUM_ARTIST)
        put("GENRE", FieldKey.GENRE)
        put("YEAR", FieldKey.YEAR)
        put("DATE", FieldKey.YEAR)
        put("TRACK", FieldKey.TRACK)
        put("TRACKNUMBER", FieldKey.TRACK)
        put("DISC_NO", FieldKey.DISC_NO)
        put("DISCNUMBER", FieldKey.DISC_NO)
        put("MUSICBRAINZ_ARTISTID", FieldKey.MUSICBRAINZ_ARTISTID)
        put("MUSICBRAINZ_ALBUMID", FieldKey.MUSICBRAINZ_RELEASEID)
        put("MUSICBRAINZ_RELEASEID", FieldKey.MUSICBRAINZ_RELEASEID)
        put("MUSICBRAINZ_RELEASE_GROUP_ID", FieldKey.MUSICBRAINZ_RELEASE_GROUP_ID)
        put("MUSICBRAINZ_TRACKID", FieldKey.MUSICBRAINZ_TRACK_ID)
        put("ENCODER", FieldKey.ENCODER)
        put("COMMENT", FieldKey.COMMENT)
    }
}
