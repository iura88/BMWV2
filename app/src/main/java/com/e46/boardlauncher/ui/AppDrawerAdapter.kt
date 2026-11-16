package com.e46.boardlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.e46.boardlauncher.data.AppInfo
import com.e46.boardlauncher.databinding.ItemAppDrawerBinding

class AppDrawerAdapter(
    private val animationsEnabledProvider: () -> Boolean,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.AppViewHolder>() {

    private var items: List<AppInfo> = emptyList()
    private var pinnedPackages: Set<String> = emptySet()
    private var accentColor: Int = 0xFFFF6A1A.toInt()

    fun submitData(
        apps: List<AppInfo>,
        pinned: Set<String>,
        accent: Int
    ) {
        items = apps
        pinnedPackages = pinned
        accentColor = accent
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppDrawerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        ThemeHelper.attachTactilePressAnimation(binding.root, animationsEnabledProvider)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(
        private val binding: ItemAppDrawerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            val density = binding.root.resources.displayMetrics.density
            binding.root.background = ThemeHelper.createCarPlayCardBackground(
                accentColor = accentColor,
                cornerRadiusPx = 14f * density,
                strokeWidthPx = (1f * density).toInt().coerceAtLeast(1)
            )
            binding.ivAppIcon.setImageDrawable(app.icon)
            binding.tvAppLabel.text = app.label

            val isPinned = pinnedPackages.contains(app.packageName)
            binding.viewPinIndicator.visibility = if (isPinned) View.VISIBLE else View.INVISIBLE
            binding.viewPinIndicator.setBackgroundColor(accentColor)

            binding.root.setOnClickListener { onAppClick(app) }
            binding.root.setOnLongClickListener {
                onAppLongClick(app)
                true
            }
        }
    }
}
