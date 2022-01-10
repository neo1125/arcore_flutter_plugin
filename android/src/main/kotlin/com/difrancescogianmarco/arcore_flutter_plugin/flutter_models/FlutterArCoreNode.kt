package com.difrancescogianmarco.arcore_flutter_plugin.flutter_models

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.difrancescogianmarco.arcore_flutter_plugin.models.RotatingNode
import com.difrancescogianmarco.arcore_flutter_plugin.utils.DecodableUtils.Companion.parseQuaternion
import com.difrancescogianmarco.arcore_flutter_plugin.utils.DecodableUtils.Companion.parseVector3
import com.google.android.filament.gltfio.Animator
import com.google.ar.core.Pose
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.VideoNode
import java.io.File

class FlutterArCoreNode(map: HashMap<String, *>) {

    val dartType: String = map["dartType"] as String
    val name: String = map["name"] as String
    val text3d: FlutterArCoreText3d? = createArCoreText3d(map["text3d"] as? HashMap<String, *>)
    val video: FlutterArCoreVideo? = createArCoreVideo(map["video"] as? HashMap<String, *>)
    val image: FlutterArCoreImage? = createArCoreImage(map["image"] as? HashMap<String, *>)
    val objectUrl: String? = map["objectUrl"] as? String
    val object3DFileName: String? = map["object3DFileName"] as? String
    val shape: FlutterArCoreShape? = getShape(map["shape"] as? HashMap<String, *>)
    var position: Vector3 = parseVector3(map["position"] as? HashMap<String, *>) ?: Vector3()
    val scale: Vector3 = parseVector3(map["scale"] as? HashMap<String, *>)
        ?: Vector3(1.0F, 1.0F, 1.0F)
    var rotation: Quaternion = parseQuaternion(map["rotation"] as? HashMap<String, Double>)
        ?: Quaternion()
    val degreesPerSecond: Float? = getDegreesPerSecond((map["degreesPerSecond"] as? Double))
    var parentNodeName: String? = map["parentNodeName"] as? String
    val children: ArrayList<FlutterArCoreNode> =
        getChildrenFromMap(map["children"] as ArrayList<HashMap<String, *>>)
    private val animationAutoPlay: Boolean = map["animationAutoPlay"] as? Boolean ?: true

    //    private val animationProgressPercent = map["animationProgressPercent"] as Double?
    private var animationHandler = AnimationHandler()
    private val mediaPlayers = mutableListOf<MediaPlayer>()

    private fun getChildrenFromMap(list: ArrayList<HashMap<String, *>>): ArrayList<FlutterArCoreNode> {
        return ArrayList(list.map { map -> FlutterArCoreNode(map) })
    }

    fun buildNode(context: Context): Node {
        val node = if (video != null) {
            buildVideoNode(context, video)
        } else if (degreesPerSecond != null) {
            RotatingNode(degreesPerSecond, true, 0.0f)
        } else {
            Node()
        }

        node.name = name
        node.localPosition = position
        node.localScale = scale
        node.localRotation = rotation

        return node
    }

    private fun buildVideoNode(context: Context, video: FlutterArCoreVideo): VideoNode {
        val uri = if (video.url.startsWith("http")) {
            Uri.parse(video.url)
        } else {
            Uri.fromFile(File(video.url))
        }
        val player: MediaPlayer = MediaPlayer.create(context, uri)
        val repeatCount = video.repeat
        var repeatTimes = 0
        if (repeatCount == -1) {
            player.isLooping = true
        } else {
            player.setOnCompletionListener {
                ++repeatTimes
                if (repeatCount == -1 || repeatTimes < repeatCount) {
                    player.seekTo(0)
                    player.start()
                }
            }
        }
        player.setVolume(video.volume.toFloat(), video.volume.toFloat())
        player.setOnPreparedListener {
            it.start()
        }
        mediaPlayers.add(player)
        return VideoNode(context, player, null)
    }

    fun getPosition(): FloatArray {
        return floatArrayOf(position.x, position.y, position.z)
    }

    fun getRotation(): FloatArray {
        return floatArrayOf(rotation.x, rotation.y, rotation.z, rotation.w)
    }

    fun getPose(): Pose {
        return Pose(getPosition(), getRotation())
    }

    private fun getDegreesPerSecond(degreesPerSecond: Double?): Float? {
        if (dartType == "ArCoreRotatingNode" && degreesPerSecond != null) {
            return degreesPerSecond.toFloat()
        }
        return null
    }

    private fun createArCoreImage(map: HashMap<String, *>?): FlutterArCoreImage? {
        if (map != null) {
            return FlutterArCoreImage(map);
        }
        return null
    }

    private fun createArCoreText3d(map: HashMap<String, *>?): FlutterArCoreText3d? {
        if (map != null) {
            return FlutterArCoreText3d(map)
        }
        return null
    }

    private fun createArCoreVideo(map: HashMap<String, *>?): FlutterArCoreVideo? {
        if (map != null) {
            return FlutterArCoreVideo(map)
        }
        return null
    }

    private fun getShape(map: HashMap<String, *>?): FlutterArCoreShape? {
        if (map != null) {
            return FlutterArCoreShape(map)
        }
        return null
    }

    override fun toString(): String {
        return "dartType: $dartType\n" +
                "name: $name\n" +
                "shape: ${shape.toString()}\n" +
                "object3DFileName: $object3DFileName \n" +
                "objectUrl: $objectUrl \n" +
                "position: $position\n" +
                "scale: $scale\n" +
                "rotation: $rotation\n" +
                "parentNodeName: $parentNodeName"
    }

    fun tryAnimation(node: Node) {
        Log.d("#anim", "try")

        if (node.renderableInstance == null) {
            return
        }
        val animator = node.renderableInstance?.filamentAsset?.animator
        if (animator != null && animator.animationCount > 0) {
            Log.d("#anim", "count > 0")
            animationHandler.add(animator)
            if (animationAutoPlay) {
                Log.d("#anim", "play")
                animationHandler.play()
            }
//            if (animationProgressPercent != null) {
//                animationHandler.setAnimationProgress(animationProgressPercent.toFloat())
//            }
        } else {
            Log.d("#anim", "count = 0")
        }
    }

    fun updateAnimation(deltaSeconds: Float) {
        animationHandler.update(deltaSeconds)
    }

//    fun playAnimation(interval: List<Float>) {
//        if (interval.isNotEmpty()) {
//            animationHandler.startInterval(interval)
//        } else {
//            animationHandler.play()
//        }
//    }
//
//    fun setAnimationProgress(progress: Float) {
//        animationHandler.setAnimationProgress(progress)
//    }

    fun dispose() {
        println("dispose flutter node: $name")
        animationHandler.dispose()
        mediaPlayers.forEach {
            it.stop()
            it.reset()
        }
        mediaPlayers.clear()
        children.forEach {
            it.dispose()
        }
        children.clear()
    }

    fun pause() {
        mediaPlayers.forEach {
            Log.d("NODE", "pause player, isPlay: ${it.isPlaying}")
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    fun resume() {
        mediaPlayers.forEach {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }
}

class AnimationHandler {
    private val animations = mutableListOf<AnimationInstance>()
    private var anim = true
    var animStateListener: ((percent: Float) -> Unit)? = null
    var speed = 1.0f;

    fun add(animator: Animator): AnimationInstance {
        val instance = AnimationInstance(animator, 0)
        animations.add(instance)
        return instance
    }

    fun update(deltaSeconds: Float) {
        if (!anim) {
            return
        }
        if (animations.isEmpty()) {
            return
        }

        val wasAnim = animations[0].isAnim
        for (anim in animations) {
            anim.update(deltaSeconds * speed)
        }
        val nowAnim = animations[0].isAnim
        if (wasAnim && !nowAnim) {
            onAnimEnded(animations[0].progressPercent)
        }
    }

    private fun onAnimEnded(progressPercent: Float) {
        animStateListener?.invoke(progressPercent)
    }

    fun play() {
        speed = 1f
        for (anim in animations) {
            anim.startEndless()
        }
        resume()
    }

    private fun pause() {
        anim = false
    }

    private fun resume() {
        anim = true
    }

//    fun startInterval(pair: List<Float>) {
//        speed = 5f; // todo 5x for test
//        for (anim in animations) {
//            anim.startInterval(pair)
//        }
//        resume()
//    }
//
//    fun setAnimationProgress(progress: Float) {
//        for (anim in animations) {
//            anim.setAnimationProgress(progress)
//        }
//    }

    fun dispose() {
        pause()
        animStateListener = null
        for (anim in animations) {
            anim.dispose()
        }
        animations.clear()
    }
}

class AnimationInstance(var animator: Animator?, var index: Int) {
    private var progress = 0f
    private var duration = animator?.getAnimationDuration(index)!!
    private var target = 0f
    private var dir = 0f
    private var anim = false

    val isAnim
        get() = anim

    val progressPercent
        get() = progress * 100.0f / duration

    fun update(deltaSeconds: Float): Float {
        progress += deltaSeconds * dir
        if (dir > 0 && progress >= target) {
            progress = target
            onAnimEnded()
        } else if (dir < 0 && progress <= target) {
            progress = target
            onAnimEnded()
        }
        refreshProgress()
        return progress
    }

    private fun refreshProgress() {
        animator?.applyAnimation(
            index,
            progress % duration
        )
        animator?.updateBoneMatrices()
    }

    private fun onAnimEnded() {
        anim = false
    }

    fun startEndless() {
        progress = 0f
        target = Float.MAX_VALUE
        dir = 1f
        anim = true
    }

    fun startInterval(pair: List<Float>) {

        if (anim) {
            return
        }

        anim = true

        val from = pair[0] * duration
        val to = pair[1] * duration

        dir = if (from < to) 1f else -1f

        progress = from
        target = to
    }
//
//    private fun checkTheSame(pair: List<Float>): Boolean {
//        if (stored.size != pair.size) {
//            return false
//        }
//        for (i in pair.indices) {
//            if (pair[i] != stored[i]) {
//                return false
//            }
//        }
//        return true
//    }

    fun setAnimationProgress(progress: Float) {
        this.progress = progress * duration
        refreshProgress()
    }

    fun dispose() {
        animator = null
    }
}
