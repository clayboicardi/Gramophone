package org.akanework.gramophone.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R

/**
 * Tiny adapter that shows a single section header row (e.g. "Artists (3)").
 * Returns itemCount = 0 when there are no results, which makes ConcatAdapter
 * hide the section automatically.
 */
class SearchSectionHeaderAdapter(
    private val title: String
) : RecyclerView.Adapter<SearchSectionHeaderAdapter.ViewHolder>() {

    var resultCount: Int = 0
        set(value) {
            val old = field
            field = value
            when {
                old == 0 && value > 0 -> notifyItemInserted(0)
                old > 0 && value == 0 -> notifyItemRemoved(0)
                old > 0 && value > 0 -> notifyItemChanged(0)
            }
        }

    override fun getItemCount(): Int = if (resultCount > 0) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_section_header, parent, false)
        return ViewHolder(view as TextView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = "$title ($resultCount)"
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
}
