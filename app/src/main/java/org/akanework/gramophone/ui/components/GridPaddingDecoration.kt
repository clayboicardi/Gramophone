package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.BaseDecorAdapter
import org.akanework.gramophone.ui.adapters.DetailedFolderAdapter
import org.akanework.gramophone.ui.adapters.SearchSectionHeaderAdapter

class GridPaddingDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private var mPadding = context.resources.getDimensionPixelSize(R.dimen.grid_card_side_padding)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        var itemPosition = parent.getChildAdapterPosition(view)
        if (itemPosition == RecyclerView.NO_POSITION) {
            return
        }
        if (itemPosition >= (parent.adapter?.itemCount ?: 0)) {
            return
        }
        var adapter = parent.adapter
        loop@ while (adapter is ConcatAdapter) {
            for (it in adapter.adapters) {
                val c = it.itemCount
                if (itemPosition < c) {
                    adapter = it
                    continue@loop
                }
                itemPosition -= c
            }
            throw IllegalStateException("can't find desired adapter?")
        }
        if (adapter is BaseDecorAdapter<*> || adapter is DetailedFolderAdapter.FolderCardAdapter
            || adapter is SearchSectionHeaderAdapter) {
            return
        }
        if (adapter !is BaseAdapter<*>) {
            throw IllegalStateException("Cannot find desired adapter! ${adapter?.javaClass?.name}")
        }
        if (adapter.layoutType != BaseAdapter.LayoutType.GRID &&
            adapter.layoutType != BaseAdapter.LayoutType.COMPACT_GRID
        ) {
            return
        }
        val columnSize = CustomGridLayoutManager.FULL_SPAN_COUNT / adapter.getSpanSize()
        if (itemPosition % columnSize == columnSize - 1) {
            outRect.right = mPadding
        } else if (itemPosition % columnSize == 0) {
            outRect.left = mPadding
        }
    }
}