package com.myiptv.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myiptv.app.data.CategoryRow
import com.myiptv.app.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (CategoryRow) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private val items = ArrayList<CategoryRow>()

    var selectedKey: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
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
