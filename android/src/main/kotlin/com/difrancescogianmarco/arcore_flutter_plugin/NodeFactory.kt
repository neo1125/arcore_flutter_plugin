package com.difrancescogianmarco.arcore_flutter_plugin

import android.content.Context
import android.util.Log
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.google.ar.sceneform.Node

typealias NodeHandler = (Node?, Throwable?) -> Unit

class NodeFactory {

    companion object {
        val TAG: String = NodeFactory::class.java.name

        fun makeNode(
            context: Context,
            flutterNode: FlutterArCoreNode,
            debug: Boolean,
            handler: NodeHandler
        ) {
            if (debug) {
                Log.i(TAG, flutterNode.toString())
            }
            val node = flutterNode.buildNode(context)
            if (flutterNode.video != null) {
                // ready to use
                setupShadows(node)
                handler(node, null)
                return
            }
            RenderableCustomFactory.makeRenderable(context, flutterNode) { renderable, throwable ->
                if (renderable != null) {
                    node.renderable = renderable
                    setupShadows(node)
                    flutterNode.tryAnimation(node)
                    handler(node, null)
                } else if (throwable != null) {
                    handler(null, throwable)
                } else {
                    handler(node, null)
                }
            }
        }

        private fun setupShadows(node: Node?) {
            node?.renderable?.isShadowCaster = false
            node?.renderable?.isShadowReceiver = false
            node?.renderableInstance?.isShadowCaster = false
            node?.renderableInstance?.isShadowReceiver = false
        }
    }
}