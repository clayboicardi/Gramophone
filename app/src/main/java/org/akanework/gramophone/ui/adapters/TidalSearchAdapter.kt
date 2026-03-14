package org.akanework.gramophone.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.tidal.TidalAlbum
import org.akanework.gramophone.logic.tidal.TidalTrack

sealed class TidalSearchItem {
    data class AlbumItem(val album: TidalAlbum) : TidalSearchItem()
    data class TrackItem(val track: TidalTrack) : TidalSearchItem()
    data class Header(val title: String, val count: Int) : TidalSearchItem()
}

class TidalSearchAdapter(
    private val onDownloadAlbum: (TidalAlbum) -> Unit,
    private val onDownloadTrack: (TidalTrack) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<TidalSearchItem> = emptyList()

    fun submitList(albums: List<TidalAlbum>, tracks: List<TidalTrack>) {
        val newItems = mutableListOf<TidalSearchItem>()
        if (albums.isNotEmpty()) {
            newItems.add(TidalSearchItem.Header("Albums", albums.size))
            albums.forEach { newItems.add(TidalSearchItem.AlbumItem(it)) }
        }
        if (tracks.isNotEmpty()) {
            newItems.add(TidalSearchItem.Header("Tracks", tracks.size))
            tracks.forEach { newItems.add(TidalSearchItem.TrackItem(it)) }
        }
        items = newItems
        notifyDataSetChanged()
    }

    fun clear() {
        items = emptyList()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is TidalSearchItem.Header -> VIEW_TYPE_HEADER
        is TidalSearchItem.AlbumItem, is TidalSearchItem.TrackItem -> VIEW_TYPE_RESULT
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.search_section_header, parent, false)
                HeaderViewHolder(view as TextView)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_tidal_result, parent, false)
                ResultViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TidalSearchItem.Header -> {
                (holder as HeaderViewHolder).textView.text = "${item.title} (${item.count})"
            }
            is TidalSearchItem.AlbumItem -> {
                val h = holder as ResultViewHolder
                val album = item.album
                h.title.text = album.title
                val subtitle = buildString {
                    if (album.artistName.isNotBlank()) append(album.artistName)
                    if (album.trackCount > 0) {
                        if (isNotEmpty()) append(" \u00b7 ")
                        append("${album.trackCount} tracks")
                    }
                }
                h.subtitle.text = subtitle
                h.coverArt.load(album.coverUrl) {
                    crossfade(true)
                    error(R.drawable.ic_default_cover)
                }
                h.downloadButton.setOnClickListener { onDownloadAlbum(album) }
            }
            is TidalSearchItem.TrackItem -> {
                val h = holder as ResultViewHolder
                val track = item.track
                h.title.text = track.title
                val subtitle = buildString {
                    if (track.artistName.isNotBlank()) append(track.artistName)
                    if (track.duration > 0) {
                        if (isNotEmpty()) append(" \u00b7 ")
                        val mins = track.duration / 60
                        val secs = track.duration % 60
                        append("$mins:%02d".format(secs))
                    }
                }
                h.subtitle.text = subtitle
                h.coverArt.load(track.coverUrl) {
                    crossfade(true)
                    error(R.drawable.ic_default_cover)
                }
                h.downloadButton.setOnClickListener { onDownloadTrack(track) }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ResultViewHolder) {
            holder.coverArt.setImageDrawable(null)
        }
        super.onViewRecycled(holder)
    }

    class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coverArt: ImageView = view.findViewById(R.id.cover_art)
        val title: TextView = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val downloadButton: MaterialButton = view.findViewById(R.id.download_button)
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_RESULT = 1
    }
}
