/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.SearchSectionHeaderAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter

/**
 * SearchFragment:
 *   Sectioned search across Artists, Albums, and Songs.
 *   Each section auto-hides when there are no matches.
 *
 * @author AkaneTan, modified by Clayboi
 */
class SearchFragment : BaseFragment(true) {
    // TODO this class leaks InsetSourceControl
    private lateinit var editText: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_search, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        editText = rootView.findViewById(R.id.edit_text)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val searchTextFlow = MutableStateFlow(arguments?.getString("query", "") ?: "")
        val trimmedQuery = searchTextFlow.map { it.trim() }

        // ── Filtered flows ──────────────────────────────────────────────

        val filteredArtistFlow = mainActivity.reader.artistListFlow
            .combine(trimmedQuery) { artists, query ->
                if (query.isBlank()) emptyList()
                else artists.filter { artist ->
                    artist.title?.contains(query, true) == true
                }
            }

        val filteredAlbumFlow = mainActivity.reader.albumListFlow
            .combine(trimmedQuery) { albums, query ->
                if (query.isBlank()) emptyList()
                else albums.filter { album ->
                    album.title?.contains(query, true) == true ||
                        album.albumArtist?.contains(query, true) == true
                }
            }

        val filteredSongFlow = mainActivity.reader.songListFlow
            .combine(trimmedQuery) { list, query ->
                if (query.isBlank()) emptyList()
                else list.filter {
                    val isMatchingTitle =
                        it.mediaMetadata.title?.contains(query, true) == true
                    val isMatchingAlbum =
                        it.mediaMetadata.albumTitle?.contains(query, true) == true
                    val isMatchingArtist =
                        it.mediaMetadata.artist?.contains(query, true) == true
                    isMatchingTitle || isMatchingAlbum || isMatchingArtist
                }
            }

        // ── Section headers ─────────────────────────────────────────────

        val artistHeader = SearchSectionHeaderAdapter(
            getString(R.string.category_artists)
        )
        val albumHeader = SearchSectionHeaderAdapter(
            getString(R.string.category_albums)
        )
        val songHeader = SearchSectionHeaderAdapter(
            getString(R.string.category_songs)
        )

        // ── Adapters ────────────────────────────────────────────────────

        val maxPerSection = 5

        val artistAdapter = ArtistAdapter(
            fragment = this,
            liveData = filteredArtistFlow.map { it.take(maxPerSection) },
            isSubFragment = R.id.search
        )

        val albumAdapter = AlbumAdapter(
            fragment = this,
            liveData = filteredAlbumFlow.map { it.take(maxPerSection) },
            isSubFragment = R.id.search
        )

        val songAdapter = SongAdapter(
            this,
            filteredSongFlow.map { it.take(maxPerSection) },
            isSubFragment = R.id.search,
            allowDiffUtils = true,
            rawOrderExposed = Sorter.Type.ByTitleAscending
        )

        // ── Observe result counts → update section headers ──────────

        viewLifecycleOwner.lifecycleScope.launch {
            filteredArtistFlow.collect { artists ->
                artistHeader.resultCount = artists.size
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            filteredAlbumFlow.collect { albums ->
                albumHeader.resultCount = albums.size
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            filteredSongFlow.collect { songs ->
                songHeader.resultCount = songs.size
            }
        }

        // ── ConcatAdapter: chain sections ───────────────────────────

        recyclerView.enableEdgeToEdgePaddingListener(ime = true)
        recyclerView.setAppBar(appBarLayout)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true)
                .build(),
            artistHeader,
            artistAdapter,
            albumHeader,
            albumAdapter,
            songHeader,
            songAdapter
        )

        val returnButton = rootView.findViewById<Button>(R.id.return_button)

        editText.addTextChangedListener { rawText ->
            searchTextFlow.value = rawText?.toString() ?: ""
        }

        returnButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }

    override fun onPause() {
        if (!isHidden) {
            requireActivity().closeKeyboard(editText)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden && !mainActivity.playerBottomSheet.visibleAndExpanded) {
            requireActivity().showKeyboard(editText)
        } else {
            editText.clearFocus()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            requireActivity().closeKeyboard(editText)
            super.onHiddenChanged(true)
        } else {
            super.onHiddenChanged(false)
            requireActivity().showKeyboard(editText)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycleScope.cancel() // TODO: why?
    }

}
