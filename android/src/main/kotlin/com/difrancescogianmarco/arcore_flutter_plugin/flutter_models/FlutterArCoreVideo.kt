package com.difrancescogianmarco.arcore_flutter_plugin.flutter_models

class FlutterArCoreVideo(map: HashMap<String, *>) {
    val url = map["url"] as String
    val width = map["width"] as Int
    val height = map["height"] as Int
    val repeat = map["repeat"] as Int
    val volume = map["volume"] as Double
    val chromaKeyColor = map["chromaKeyColor"] as String?
}
