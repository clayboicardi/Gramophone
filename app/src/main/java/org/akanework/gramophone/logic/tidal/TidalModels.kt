package org.akanework.gramophone.logic.tidal

data class TidalSearchResult(
    val albums: List<TidalAlbum>,
    val tracks: List<TidalTrack>
)

data class TidalAlbum(
    val id: String,
    val title: String,
    val artistName: String,
    val coverUrl: String?,
    val trackCount: Int,
    val releaseDate: String?
)

data class TidalTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String?,
    val duration: Int,
    val trackNumber: Int,
    val coverUrl: String?
)
