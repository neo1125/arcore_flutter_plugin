package com.difrancescogianmarco.arcore_flutter_plugin.flutter_models

import android.graphics.Color

class FlutterArCoreText3d(map: HashMap<String, *>) {
    val text = map["text"] as String
    val align = map["align"] as String
    val color: Int
    val shadowOn = map["shadowOn"] as Boolean
    private val shadow = map["shadow"] as HashMap<String, *>

    init {
        var colorStr = (map["color"] ?: "#ffffffff") as String
        if (!colorStr.startsWith("#")) {
            colorStr = "#$colorStr"
        }
        color = Color.parseColor(colorStr)
    }

    val shadowDx get() = (shadow["dx"] as Double).toFloat()
    val shadowDy get() = (shadow["dy"] as Double).toFloat()
    val shadowRadius get() = (shadow["radius"] as Double).toFloat()
    val shadowColor get() = parseColor((shadow["color"] as String))

    private fun parseColor(colorStr: String): Int {
        val str = if (!colorStr.startsWith("#")) {
            "#$colorStr"
        } else {
            colorStr
        }
        return Color.parseColor(str)
    }
}
