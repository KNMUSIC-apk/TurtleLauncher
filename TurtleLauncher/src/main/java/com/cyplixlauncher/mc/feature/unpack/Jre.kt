package com.cyplixlauncher.mc.feature.unpack

import com.cyplixlauncher.mc.R

enum class Jre(val jreName: String, val jrePath: String, val summary: Int) {
    JAVA_8("Internal-8", "components/jre8", R.string.jre_8_summary),
    JAVA_21("Internal-21", "components/jre21", R.string.jre_21_summary)
}
