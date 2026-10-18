package com.cyplixlauncher.mc.feature.download

import com.cyplixlauncher.mc.feature.download.enums.Category
import com.cyplixlauncher.mc.feature.download.enums.ModLoader
import com.cyplixlauncher.mc.feature.download.enums.Sort


/**
 * Provides the filter info used for platform searches.
 */
class Filters {
    var name: String = ""
    var mcVersion: String? = null
    var modloader: ModLoader? = null
    var sort: Sort = Sort.RELEVANT
    var category: Category = Category.ALL

    override fun toString(): String {
        return "Filters(name='$name', mcVersion=$mcVersion, modloader=$modloader, sort=$sort, category=$category)"
    }
}