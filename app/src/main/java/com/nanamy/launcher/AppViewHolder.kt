package com.nanamy.launcher

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.databinding.ItemAppBinding

class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val binding: ItemAppBinding = ItemAppBinding.bind(view)
}
