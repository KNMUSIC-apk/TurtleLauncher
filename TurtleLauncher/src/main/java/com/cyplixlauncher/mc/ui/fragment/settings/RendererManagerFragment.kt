package com.cyplixlauncher.mc.ui.fragment.settings
import com.cyplixlauncher.mc.utils.anim.TurtleTransitions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.databinding.FragmentRendererManagerBinding
import com.cyplixlauncher.mc.feature.download.RendererCardAdapter
import com.cyplixlauncher.mc.feature.pluginupdate.PluginAssetInfo
import com.cyplixlauncher.mc.feature.pluginupdate.PluginKind
import com.cyplixlauncher.mc.feature.pluginupdate.PluginUpdateManager
import com.cyplixlauncher.mc.renderer.RendererCatalog
import com.cyplixlauncher.mc.renderer.Renderers
import com.cyplixlauncher.mc.renderer.renderers.HolyGL4ESRenderer
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.ui.fragment.FragmentWithAnim
import com.cyplixlauncher.mc.utils.ZHTools
import net.endiq.launcher.Tools
import java.io.File

class RendererManagerFragment : FragmentWithAnim(R.layout.fragment_renderer_manager) {
    companion object {
        const val TAG: String = "RendererManagerFragment"
    }

    private lateinit var binding: FragmentRendererManagerBinding

    /** Cached once per screen-open so re-rendering after picking a renderer doesn't
     *  re-hit GitHub every time; refreshed after a successful install. */
    private var downloadableCards: List<RendererCardAdapter.CardEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRendererManagerBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rendererGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        render()
        fetchAlwaysOfferedRenderers()

        binding.rendererManagerReset.setOnClickListener {
            val defaultUuid = HolyGL4ESRenderer().getUniqueIdentifier()
            AllSettings.renderer.put(defaultUuid).save()
            Renderers.setCurrentRenderer(requireContext(), defaultUuid)
            render()
        }

        binding.rendererManagerReturn.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
    }

    private fun fetchAlwaysOfferedRenderers() {
        val context = requireContext()
        PluginUpdateManager.checkForUpdates(context, force = true) { updates, _ ->
            if (!isAdded) return@checkForUpdates

            val alreadyLoadedNames = Renderers.getCompatibleRenderers(context).second
                .map { it.getRendererName().lowercase() }

            val ltw = updates.firstOrNull {
                it.kind == PluginKind.RENDERER && it.assetName.contains("ltw", ignoreCase = true)
            }
            val mobileGlues = updates.firstOrNull {
                it.kind == PluginKind.RENDERER && it.assetName.contains("mobileglues", ignoreCase = true)
            }

            downloadableCards = listOfNotNull(
                ltw?.takeIf { "ltw" !in alreadyLoadedNames.joinToString() }?.let { toDownloadCard(it, "LTW") },
                mobileGlues?.takeIf { "mobileglues" !in alreadyLoadedNames.joinToString().replace(" ", "") }
                    ?.let { toDownloadCard(it, "MobileGlues") }
            )
            if (downloadableCards.isNotEmpty()) render()
        }
    }

    private fun toDownloadCard(asset: PluginAssetInfo, displayName: String) = RendererCardAdapter.CardEntry(
        uniqueIdentifier = "download:${asset.assetName}",
        name = displayName,
        badge = RendererCatalog.Badge.EXPERIMENTAL,
        compatNote = getString(R.string.renderer_tap_to_download),
        isSelected = false,
        downloadAsset = asset
    )

    private fun installRendererAsset(asset: PluginAssetInfo) {
        val context = requireContext()
        Toast.makeText(context, getString(R.string.renderer_get_more_installing, asset.assetName), Toast.LENGTH_SHORT).show()
        PluginUpdateManager.downloadAndInstall(context, asset) { success, message ->
            if (!isAdded) return@downloadAndInstall
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (success) {
                downloadableCards = downloadableCards.filterNot { it.downloadAsset == asset }
                render()
            }
        }
    }

    private fun render() {
        val context = requireContext()
        val compatibleRenderers = Renderers.getCompatibleRenderers(context).second
        val currentUuid = AllSettings.renderer.getValue()
        val hasVulkan = Tools.checkVulkanSupport(context.packageManager)

        val installedCards = compatibleRenderers.map { renderer ->
            val catalogEntry = RendererCatalog.get(renderer.getRendererId())
            val plugin = com.cyplixlauncher.mc.plugins.renderer.RendererPluginManager.getRendererList()
                .find { it.uniqueIdentifier == renderer.getUniqueIdentifier() }
            val compatNote = buildCompatNote(renderer, catalogEntry, plugin, hasVulkan)
            RendererCardAdapter.CardEntry(
                uniqueIdentifier = renderer.getUniqueIdentifier(),
                name = renderer.getRendererName(),
                badge = catalogEntry?.badge ?: RendererCatalog.Badge.EXPERIMENTAL,
                compatNote = compatNote,
                isSelected = renderer.getUniqueIdentifier() == currentUuid
            )
        }

        binding.rendererGrid.adapter = RendererCardAdapter(installedCards + downloadableCards) { card ->
            if (card.downloadAsset != null) {
                installRendererAsset(card.downloadAsset)
            } else {
                AllSettings.renderer.put(card.uniqueIdentifier).save()
                Renderers.setCurrentRenderer(context, card.uniqueIdentifier)
                render()
            }
        }
    }

    /** Missing-library warning takes priority over version-range notes - a renderer that
     *  will crash on load is more urgent to know about than its recommended version range. */
    private fun buildCompatNote(
        renderer: com.cyplixlauncher.mc.renderer.RendererInterface,
        catalogEntry: RendererCatalog.Entry?,
        plugin: com.cyplixlauncher.mc.plugins.renderer.RendererPlugin?,
        hasVulkan: Boolean
    ): String? {
        // Same existence rule as Renderers.hasRequiredLibrary: built-in renderers name a
        // bare library inside the launcher's own jniLibs folder, plugin renderers bring
        // their own library and reference it by absolute path inside the plugin.
        val library = renderer.getRendererLibrary()
        val libMissing = if (library.startsWith("/")) !File(library).exists()
        else !File(com.cyplixlauncher.mc.utils.path.PathManager.DIR_NATIVE_LIB, library).exists()
        if (libMissing) return getString(R.string.renderer_compat_missing_library)

        if (renderer.getRendererId() == com.cyplixlauncher.mc.renderer.renderers.ZinkRenderer.ID && !hasVulkan) {
            return getString(R.string.renderer_compat_requires_vulkan)
        }

        val maxVersion = catalogEntry?.maxMinecraftVersion ?: plugin?.maxMinecraftVersion
        val minVersion = catalogEntry?.minMinecraftVersion ?: plugin?.minMinecraftVersion
        maxVersion?.let { return getString(R.string.renderer_compat_max_version, it) }
        minVersion?.let { return getString(R.string.renderer_compat_min_version, it) }
        return null
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.enter()))
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        animPlayer.apply(AnimPlayer.Entry(binding.root, TurtleTransitions.exit()))
    }
}
