package com.cyplixlauncher.mc.ui.subassembly.modlist

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.ItemModDownloadBinding
import com.cyplixlauncher.mc.feature.download.enums.DependencyType
import com.cyplixlauncher.mc.feature.download.enums.ModLoader
import com.cyplixlauncher.mc.feature.version.utils.VersionIconUtils
import com.cyplixlauncher.mc.feature.version.VersionsManager

class ModListAdapter(
    private val fragment: ModListFragment,
    private val mData: MutableList<ModListItemBean>?
) : RecyclerView.Adapter<ModListAdapter.InnerHolder>() {
    private val mCurrentVersion = VersionsManager.getCurrentVersion()
    private val mIconUtils = lazy { mCurrentVersion?.run { VersionIconUtils(mCurrentVersion) } }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        return InnerHolder(ItemModDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        holder.setData(mData?.getOrNull(position) ?: return)
    }

    override fun getItemCount(): Int {
        return mData?.size ?: 0
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newData: List<ModListItemBean>?) {
        mData?.clear()
        if (newData != null) mData?.addAll(newData)
        super.notifyDataSetChanged()
    }

    val data: List<ModListItemBean>?
        get() = mData

    inner class InnerHolder(private val binding: ItemModDownloadBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        private val mContext = binding.root.context

        fun setData(item: ModListItemBean) {
            itemView.setOnClickListener {
                fragment.switchToChild(
                    item.getAdapter(),
                    item.title
                )
            }
            binding.apply {
                modVersionId.text = item.title
                val modLoaderInfo = getModLoaderInfo(mContext, item.modloader)
                modloaderIcon.setImageDrawable(modLoaderInfo.first)
                modLoaderInfo.second?.let { name ->
                    modloaderName.visibility = View.VISIBLE
                    modloaderName.text = name
                } ?: run {
                    modloaderName.visibility = View.GONE
                }

                // Every row shares one background drawable, so a tint set on it leaks into the other
                // rows and the next row to bind clears it. Mutate it so this row owns its highlight.
                val rowBackground = root.background?.mutate()
                if (rowBackground !== root.background) root.background = rowBackground

                val visibility = if (item.isAdapt) run {
                    mIconUtils.value?.start(currentVersionIcon)
                    rowBackground?.setTint(DependencyType.OPTIONAL.color)
                    View.VISIBLE
                } else run {
                    rowBackground?.setTintList(null)
                    View.GONE
                }

                isAdapt.visibility = visibility
                currentVersionIcon.visibility = visibility

                if (item.isAdapt) {
                    val versionName = mCurrentVersion?.getVersionName()
                    isAdapt.apply {
                        text = versionName?.run {
                            mContext.getString(R.string.download_install_is_adapt, versionName)
                        }
                        isSelected = true
                    }
                }
            }
        }

        /**
         * @return icon and name of the mod loader, or the vanilla grass block and an empty string
         */
        private fun getModLoaderInfo(context: Context, modLoader: ModLoader?): Pair<Drawable?, String?> {
            val loaderInfo: Pair<Int, String?> = when (modLoader) {
                ModLoader.FORGE -> Pair(R.drawable.ic_anvil, ModLoader.FORGE.loaderName)
                ModLoader.NEOFORGE -> Pair(R.drawable.ic_neoforge, ModLoader.NEOFORGE.loaderName)
                ModLoader.FABRIC -> Pair(R.drawable.ic_fabric, ModLoader.FABRIC.loaderName)
                ModLoader.QUILT -> Pair(R.drawable.ic_quilt, ModLoader.QUILT.loaderName)
                else -> Pair(R.drawable.ic_minecraft, null)
            }

            return Pair(ContextCompat.getDrawable(context, loaderInfo.first), loaderInfo.second)
        }
    }
}
