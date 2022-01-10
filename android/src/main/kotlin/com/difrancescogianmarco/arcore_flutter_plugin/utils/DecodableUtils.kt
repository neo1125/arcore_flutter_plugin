package com.difrancescogianmarco.arcore_flutter_plugin.utils

import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3

class DecodableUtils {

    companion object {
        fun parseVector3(vector: HashMap<String, *>?): Vector3? {
            if (vector != null) {
                val x: Float = (vector["x"] as Double).toFloat()
                val y: Float = (vector["y"] as Double).toFloat()
                val z: Float = (vector["z"] as Double).toFloat()
                return Vector3(x, y, z)
            }
            return null
        }

        fun parseQuaternion(vector: HashMap<String, Double>?): Quaternion? {
            var vec3 = parseVector3(vector);
            if (vec3 != null) {
                return Quaternion(vec3)
            }
            return null
        }
    }
}