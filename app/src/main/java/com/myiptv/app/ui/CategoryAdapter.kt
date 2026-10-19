package com.myiptv.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myiptv.app.data.CategoryRow
import com.myiptv.app.databinding.ItemCategoryBinding

/**
 * Category rail / chip row.
 *
 * Category lists are small, but they are rebuilt whenever the tab, the search
 * text or the favorites change, so selection changes rebind the two affected
 * rows only instead of the whole rail.
 */
class CategoryAdapter(
    private val onClick: (CategoryRow) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private val items = ArrayList<CategoryRow>()

    var selectedKey: String? = null
        set(value) {
            if (field == value) return
            val previous = items.indexOfFirst { it.key == field }
            field = value
            val next = items.indexOfFirst { it.key == value }
            if (previous >= 0) notifyItemChanged(previous)
            if (next >= 0) notifyItemChanged(next)
        }

    fun submit(list: List<CategoryRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding.root)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.title
        holder.label.isSelected = item.key == selectedKey
        holder.label.setOnClickListener { onClick(item) }
    }

    class ViewHolder(val label: TextView) : RecyclerView.ViewHolder(label)
}
