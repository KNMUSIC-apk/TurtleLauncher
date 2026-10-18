package com.cyplixlauncher.mc.feature.download.item

import com.cyplixlauncher.mc.feature.download.enums.Category
import com.cyplixlauncher.mc.feature.download.enums.Classify
import com.cyplixlauncher.mc.feature.download.enums.ModLoader
import com.cyplixlauncher.mc.feature.download.enums.Platform
import java.util.Date

/**
 * @param modloaders mod loader info
 */
open class ModInfoItem(
    classify: Classify,
    platform: Platform,
    projectId: String,
    slug: String,
    author: Array<String>?,
    title: String,
    description: String,
    downloadCount: Long,
    uploadDate: Date,
    iconUrl: String?,
    category: List<Category>,
    val modloaders: List<ModLoader>
) : InfoItem(
    classify, platform, projectId, slug, author, title, description, downloadCount, uploadDate, iconUrl, category
) {
    override fun toString(): String {
        return "ModInfoItem(" +
                "classify='$classify', " +
                "platform='$platform', " +
                "projectId='$projectId', " +
                "slug='$slug', " +
                "author=${author.contentToString()}, " +
                "title='$title', " +
                "description='$description', " +
                "downloadCount=$downloadCount, " +
                "uploadDate=$uploadDate, " +
                "iconUrl='$iconUrl', " +
                "category=$category, " +
                "modloaders=$modloaders" +
                ")"
    }
}