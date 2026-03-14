package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.closeKeyboard
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.showKeyboard
import org.akanework.gramophone.logic.tidal.TermuxDownloadBridge
import org.akanework.gramophone.ui.adapters.TidalSearchAdapter
import org.akanework.gramophone.ui.viewmodels.TidalSearchViewModel

class TidalSearchFragment : BaseFragment(true) {

    private val viewModel: TidalSearchViewModel by viewModels()
    private lateinit var editText: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_tidal_search, container, false)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()
        editText = rootView.findViewById(R.id.edit_text)
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerview)
        val progressBar = rootView.findViewById<ProgressBar>(R.id.progress_bar)
        val emptyText = rootView.findViewById<TextView>(R.id.empty_text)
        val errorText = rootView.findViewById<TextView>(R.id.error_text)

        val adapter = TidalSearchAdapter(
            onDownloadAlbum = { album ->
                TermuxDownloadBridge.downloadAlbum(requireContext(), album.id, album.title)
            },
            onDownloadTrack = { track ->
                TermuxDownloadBridge.downloadTrack(requireContext(), track.id, track.title)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        editText.addTextChangedListener { rawText ->
            viewModel.setQuery(rawText?.toString() ?: "")
        }

        rootView.findViewById<View>(R.id.return_button).setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchResults.collect { results ->
                if (results != null) {
                    adapter.submitList(results.albums, results.tracks)
                    emptyText.visibility =
                        if (results.albums.isEmpty() && results.tracks.isEmpty())
                            View.VISIBLE else View.GONE
                    if (results.albums.isEmpty() && results.tracks.isEmpty()) {
                        emptyText.text = "No results found"
                    }
                } else {
                    adapter.clear()
                    if (viewModel.searchQuery.value.isBlank()) {
                        emptyText.text = "Search Tidal to find music to download"
                        emptyText.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                if (loading) {
                    emptyText.visibility = View.GONE
                    errorText.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { err ->
                if (err != null) {
                    errorText.text = err
                    errorText.visibility = View.VISIBLE
                    emptyText.visibility = View.GONE
                } else {
                    errorText.visibility = View.GONE
                }
            }
        }

        // Restore query if present
        val currentQuery = viewModel.searchQuery.value
        if (currentQuery.isNotEmpty()) {
            editText.setText(currentQuery)
            editText.setSelection(currentQuery.length)
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
}
