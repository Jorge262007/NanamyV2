package com.nanamy.launcher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nanamy.launcher.databinding.ItemAppBinding

/**
 * Adaptador para el grid de aplicaciones (tanto curadas como lista completa).
 */
class AppsAdapter(
    private val apps: List<AppModel>,
    private val onAppClick: (AppModel) -> Unit,
    private val onAppLongClick: ((AppModel) -> Unit)? = null
) : RecyclerView.Adapter<AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.binding.tvAppName.text = app.label
        
        if (app.icon != null) {
            holder.binding.ivAppIcon.setImageDrawable(app.icon)
            holder.binding.ivAppIcon.alpha = 1.0f
        } else {
            // Placeholder for ADD button or missing icon
            if (app.type == AppItemType.ADD_BUTTON) {
                holder.binding.ivAppIcon.setImageResource(android.R.drawable.ic_input_add)
                holder.binding.ivAppIcon.alpha = 0.7f
            } else {
                holder.binding.ivAppIcon.setImageResource(android.R.drawable.ic_menu_help)
                holder.binding.ivAppIcon.alpha = 0.5f
            }
        }

        holder.binding.root.setOnClickListener { onAppClick(app) }
        
        holder.binding.root.setOnLongClickListener {
            onAppLongClick?.invoke(app)
            onAppLongClick != null
        }
    }

    override fun getItemCount(): Int = apps.size
}
