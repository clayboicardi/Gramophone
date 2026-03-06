package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.toMediaStoreId
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.utils.FlacTagManager

/**
 * Full-screen fragment for viewing FLAC tag metadata.
 * Shows: Title, Artist, Album, Genre, Year, Format, Sample Rate, Bit Rate, BPM.
 */
class TagDetailFragment : BaseFragment(false) {

    private var filePath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_tag_detail, container, false)

        // Edge-to-edge
        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()
        rootView.findViewById<View>(R.id.scrollView).enableEdgeToEdgePaddingListener()

        // Back navigation
        rootView.findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Look up the MediaItem
        val id = requireArguments().getString("Id")?.toMediaStoreId()
        val mediaItem = runBlocking { mainActivity.reader.idMapFlow.map { it[id] }.first() }
        if (mediaItem == null) {
            parentFragmentManager.popBackStack()
            return null
        }

        val mediaMetadata = mediaItem.mediaMetadata
        filePath = mediaItem.getFile()?.path

        // -- Album Art --
        val albumCover = rootView.findViewById<ImageView>(R.id.album_cover)
        albumCover.load(mediaMetadata.artworkUri) {
            placeholderScaleToFit(R.drawable.ic_default_cover)
            crossfade(true)
            error(R.drawable.ic_default_cover)
        }

        // -- Set title in toolbar --
        rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
            .title = mediaMetadata.title ?: getString(R.string.tag_detail_title)

        // -- Core info rows --
        val rowTitle = rootView.findViewById<View>(R.id.row_title)
        val rowArtist = rootView.findViewById<View>(R.id.row_artist)
        val rowAlbum = rootView.findViewById<View>(R.id.row_album)
        val rowGenre = rootView.findViewById<View>(R.id.row_genre)
        val rowYear = rootView.findViewById<View>(R.id.row_year)

        // Set labels
        setRowLabel(rowTitle, getString(R.string.tag_label_title))
        setRowLabel(rowArtist, getString(R.string.tag_label_artist))
        setRowLabel(rowAlbum, getString(R.string.tag_label_album))
        setRowLabel(rowGenre, getString(R.string.tag_label_genre))
        setRowLabel(rowYear, getString(R.string.tag_label_year))

        // Set initial values from MediaStore (instant, no disk I/O)
        setRowValue(rowTitle, mediaMetadata.title?.toString() ?: "")
        setRowValue(rowArtist, mediaMetadata.artist?.toString() ?: "")
        setRowValue(rowAlbum, mediaMetadata.albumTitle?.toString() ?: "")
        setRowValue(rowGenre, mediaMetadata.genre?.toString() ?: "")
        setRowValue(rowYear,
            (mediaMetadata.releaseYear ?: mediaMetadata.recordingYear)?.toString() ?: "")

        // Technical section container
        val techContainer = rootView.findViewById<LinearLayout>(R.id.technical_rows_container)

        // -- Load tags from actual file on background thread --
        val path = filePath
        if (path != null) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val tags = FlacTagManager.readAllTags(path)
                val audioProps = FlacTagManager.readAudioProperties(path)

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext

                    // Override core fields with ealvatag values (more accurate than MediaStore)
                    tags["TITLE"]?.let { setRowValue(rowTitle, it) }
                    tags["ARTIST"]?.let { setRowValue(rowArtist, it) }
                    tags["ALBUM"]?.let { setRowValue(rowAlbum, it) }
                    tags["GENRE"]?.let { setRowValue(rowGenre, it) }
                    tags["YEAR"]?.let { setRowValue(rowYear, it) }

                    // -- Technical section --
                    if (audioProps != null) {
                        addDynamicRow(inflater, techContainer,
                            getString(R.string.tag_label_format), audioProps.format)
                        addDynamicRow(inflater, techContainer,
                            getString(R.string.tag_label_sample_rate),
                            getString(R.string.tag_sample_rate_format, audioProps.sampleRate))
                        addDynamicRow(inflater, techContainer,
                            getString(R.string.tag_label_bitrate),
                            "${audioProps.bitRate} kbps")
                    }

                    // BPM from Vorbis comments — only show if present
                    val bpm = tags["BPM"] ?: tags["TMPO"] ?: tags["TEMPO"]
                    if (!bpm.isNullOrBlank()) {
                        addDynamicRow(inflater, techContainer,
                            getString(R.string.tag_label_bpm), bpm)
                    }
                }
            }
        }

        // -- FAB: Edit (placeholder for Phase 3) --
        rootView.findViewById<FloatingActionButton>(R.id.fab_edit).setOnClickListener {
            if (!mainActivity.hasAllFilesPermission()) {
                Snackbar.make(rootView,
                    R.string.tag_permission_needed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.tag_grant_permission) {
                        mainActivity.requestAllFilesPermission()
                    }
                    .show()
            } else {
                Snackbar.make(rootView,
                    "Tag editing coming soon!", Snackbar.LENGTH_SHORT).show()
            }
        }

        return rootView
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun setRowLabel(rowView: View, label: String) {
        rowView.findViewById<TextView>(R.id.row_label)?.text = label
    }

    private fun setRowValue(rowView: View, value: String) {
        rowView.findViewById<TextView>(R.id.row_value)?.text = value
    }

    private fun addDynamicRow(
        inflater: LayoutInflater,
        container: LinearLayout,
        label: String,
        value: String
    ) {
        val row = inflater.inflate(R.layout.tag_detail_row, container, false)
        row.findViewById<TextView>(R.id.row_label).text = label
        row.findViewById<TextView>(R.id.row_value).text = value
        container.addView(row)
    }
}
