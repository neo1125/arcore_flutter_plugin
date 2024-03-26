package com.difrancescogianmarco.arcore_flutter_plugin

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Pair
import android.os.Handler
import android.os.HandlerThread
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.PixelCopy
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreHitTestResult
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCoreNode
import com.difrancescogianmarco.arcore_flutter_plugin.flutter_models.FlutterArCorePose
import com.difrancescogianmarco.arcore_flutter_plugin.utils.ArCoreUtils
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*
import kotlin.coroutines.CoroutineContext

class ArCoreAugmentedImagesView(
    activity: Activity,
    context: Context,
    messenger: BinaryMessenger,
    id: Int,
    val useSingleImage: Boolean,
    debug: Boolean
) : BaseArCoreView(activity, context, messenger, id, debug), CoroutineScope {

    private val TAG: String = ArCoreAugmentedImagesView::class.java.name
    private var sceneUpdateListener: Scene.OnUpdateListener

    // Augmented image and its associated center pose anchor,
    // keyed by index of the augmented image in the database.
    private val augmentedImageMap = HashMap<Int, Pair<AugmentedImage, AnchorNode>>()
    private val gestureDetector: GestureDetector

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    init {

        debugLog("augmented init")

        sceneUpdateListener = Scene.OnUpdateListener { frameTime ->

            updateFlutterNodes(frameTime.deltaSeconds)

            val frame = arSceneView?.arFrame ?: return@OnUpdateListener

            // If there is no frame or ARCore is not tracking yet, just return.
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return@OnUpdateListener
            }

            // todo: iterate in image-detection mode only
            val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

            for (augmentedImage in updatedAugmentedImages) {
                when (augmentedImage.trackingState) {
                    TrackingState.PAUSED -> {
                        val text = String.format("Detected Image %d", augmentedImage.index)
                        debugLog(text)
                    }

                    TrackingState.TRACKING -> {
                        debugLog("${augmentedImage.name} ${augmentedImage.trackingMethod}")
                        if (!augmentedImageMap.containsKey(augmentedImage.index)) {
                            debugLog("${augmentedImage.name} ASSENTE")
                            val centerPoseAnchor =
                                augmentedImage.createAnchor(augmentedImage.centerPose)
                            val anchorNode = AnchorNode()
                            anchorNode.anchor = centerPoseAnchor
                            augmentedImageMap[augmentedImage.index] =
                                Pair.create(augmentedImage, anchorNode)
                        }

                        sendAugmentedImageToFlutter(augmentedImage)
                    }

                    TrackingState.STOPPED -> {
                        debugLog("STOPPED: ${augmentedImage.name}")
                        removeAugmentedImage(augmentedImage.index)
                    }

                    else -> {
                    }
                }
            }

            // todo: iterate in plane-detection mode only
            for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                if (plane.trackingState == TrackingState.TRACKING) {
                    val pose = plane.centerPose
                    val map: HashMap<String, Any> = HashMap<String, Any>()
                    map["type"] = plane.type.ordinal
                    map["centerPose"] =
                        FlutterArCorePose(pose.translation, pose.rotationQuaternion).toHashMap()
                    map["extentX"] = plane.extentX
                    map["extentZ"] = plane.extentZ

                    methodChannel.invokeMethod("onPlaneDetected", map)
                }
            }
        }

        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    onSingleTap(e)
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }
            })
    }

    private fun removeAllAugmentedImages() {
        while (augmentedImageMap.isNotEmpty()) {
            removeAugmentedImage(augmentedImageMap.keys.first())
        }
    }

    private fun removeAugmentedImage(index: Int) {
        val anchorNode = augmentedImageMap[index]!!.second
        augmentedImageMap.remove(index)
        arSceneView?.scene?.removeChild(anchorNode)
        val text = String.format("Removed Image %d", index)
        debugLog(text)
    }

    private fun updateFlutterNodes(deltaSeconds: Float) {
        allFlutterNodesSet.forEach {
            updateAnimation(it, deltaSeconds)
        }
    }

    private fun updateAnimation(node: FlutterArCoreNode, deltaSeconds: Float) {
        node.updateAnimation(deltaSeconds)
    }

    private fun onSingleTap(tap: MotionEvent?) {
        val frame = arSceneView?.arFrame ?: return
        if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
            val hitList = frame.hitTest(tap)
            val list = ArrayList<HashMap<String, Any>>()
            for (hit in hitList) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    hit.hitPose
                    val distance: Float = hit.distance
                    val translation = hit.hitPose.translation
                    val rotation = hit.hitPose.rotationQuaternion
                    val flutterArCoreHitTestResult =
                        FlutterArCoreHitTestResult(distance, translation, rotation)
                    val arguments = flutterArCoreHitTestResult.toHashMap()
                    list.add(arguments)
                }
            }
            methodChannel.invokeMethod("onPlaneTap", list)
        }
    }

    private fun sendAugmentedImageToFlutter(augmentedImage: AugmentedImage) {
        val map: HashMap<String, Any> = HashMap<String, Any>()
        map["name"] = augmentedImage.name
        map["index"] = augmentedImage.index
        map["extentX"] = augmentedImage.extentX
        map["extentZ"] = augmentedImage.extentZ
        map["centerPose"] = FlutterArCorePose.fromPose(augmentedImage.centerPose).toHashMap()
        map["trackingMethod"] = augmentedImage.trackingMethod.ordinal
        activity.runOnUiThread {
            methodChannel.invokeMethod("onTrackingImage", map)
        }
    }

    private var paused = false
    private fun pauseSession() {
        arSceneView?.pause()
        paused = true
    }

    private fun resumeSession() {
        if (paused) {
            try {
                arSceneView?.resume()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            paused = false
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (isSupportedDevice) {
            debugLog(call.method + " called on supported device")
            when (call.method) {
                "init" -> {
                    debugLog("INIT AUGMENTED IMAGES")
                    arSceneViewInit(call, result)
                }

                "pause" -> {
                    debugLog("pause session")
                    pauseSession()
                    result.success(null)
                }

                "resume" -> {
                    debugLog("resume session")
                    resumeSession()
                    result.success(null)
                }

                "load_single_image_on_db" -> {
                    debugLog("load_single_image_on_db")
                    val map = call.arguments as HashMap<String, Any>
                    val singleImageBytes = map["bytes"] as? ByteArray
                    setupSession(singleImageBytes, true)
                    result.success(null)
                }

                "load_multiple_images_on_db" -> {
                    debugLog("load_multiple_image_on_db")
                    val map = call.arguments as HashMap<String, Any>
                    val dbByteMap = map["bytesMap"] as? Map<String, ByteArray>
                    setupSession(dbByteMap, result)
                }

                "load_augmented_images_database" -> {
                    debugLog("LOAD DB")
                    val map = call.arguments as HashMap<String, Any>
                    val dbByteArray = map["bytes"] as? ByteArray
                    setupSession(dbByteArray, false)
                    result.success(null)
                }

                "attachObjectToAugmentedImage" -> {
                    debugLog("attachObjectToAugmentedImage")
                    val map = call.arguments as HashMap<String, Any>
                    val flutterArCoreNode = FlutterArCoreNode(map["node"] as HashMap<String, Any>)
                    val index = map["index"] as Int
                    if (augmentedImageMap.containsKey(index)) {
                        val anchorNode = augmentedImageMap[index]!!.second
                        NodeFactory.makeNode(
                            activity.applicationContext,
                            flutterArCoreNode,
                            debug
                        ) { node, throwable ->
                            debugLog("inserted ${node?.name}")
                            if (node != null) {
                                node.parent = anchorNode
                                arSceneView?.scene?.addChild(anchorNode)
                                result.success(null)
                            } else if (throwable != null) {
                                result.error(
                                    "attachObjectToAugmentedImage error",
                                    throwable.localizedMessage,
                                    null
                                )
                            }
                        }
                    } else {
                        result.error(
                            "attachObjectToAugmentedImage error",
                            "Augmented image there isn't ona hashmap",
                            null
                        )
                    }
                }

                "removeARCoreNodeWithIndex" -> {
                    debugLog("removeObject")
                    try {
                        val map = call.arguments as HashMap<String, Any>
                        val index = map["index"] as Int
                        removeAugmentedImage(index)
                        result.success(null)
                    } catch (ex: Exception) {
                        result.error("removeARCoreNodeWithIndex", ex.localizedMessage, null)
                    }
                }

                "addArCoreNodeWithAnchor" -> {
                    debugLog("addArCoreNodeWithAnchor")
                    val map = call.arguments as HashMap<String, Any>
                    val flutterNode = FlutterArCoreNode(map)
                    addNodeWithAnchor(flutterNode, result)
                }

                "removeARCoreNode" -> {
                    debugLog("removeARCoreNode")
                    val map = call.arguments as HashMap<String, Any>
                    removeNode(map["nodeName"] as String, result)
                }

                "addArCoreNode" -> {
                    debugLog("addArCoreNode")
                    val map = call.arguments as HashMap<String, Any>
                    val flutterNode = FlutterArCoreNode(map);
                    onAddNode(flutterNode, result)
                }

                "dispose" -> {
                    debugLog(" dispose")
                    dispose()
                    result.success(null)
                }

                "cleanup" -> {
                    debugLog("cleanup")
                    cleanup()
                    result.success(null)
                }

                "runGC" -> {
                    debugLog("runGC")
                    System.gc()
                    result.success(null)
                }

                "positionChanged" -> {
                    val map = call.arguments as HashMap<String, *>
                    moveNodeTo(
                        map["name"] as String,
                        map["position"] as HashMap<String, *>
                    )
                    result.success(null)
                }

                "rotationChanged" -> {
                    val map = call.arguments as HashMap<String, *>
                    rotateNodeTo(
                        map["name"] as String,
                        map["rotation"] as HashMap<String, *>
                    )
                    result.success(null)
                }

                "scaleChanged" -> {
                    val map = call.arguments as HashMap<String, *>
                    scaleNodeTo(
                        map["name"] as String,
                        map["scale"] as HashMap<String, *>
                    )
                    result.success(null)
                }

                "takeSnapshot" -> {
                    debugLog("takeSnapshot")
                    try {
                        val bitmap: Bitmap = Bitmap.createBitmap(arSceneView!!.getWidth(), arSceneView!!.getHeight(),
                            Bitmap.Config.ARGB_8888)
                        val handlerThread = HandlerThread("PixelCopier")
                        handlerThread.start()
                        PixelCopy.request(arSceneView!!, bitmap, { copyResult ->
                            if (copyResult === PixelCopy.SUCCESS) {
                                val mainHandler = Handler(activity.applicationContext.mainLooper)
                                val runnable = Runnable {
                                    val stream = ByteArrayOutputStream()
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                                    val data = stream.toByteArray()
                                    result.success(data)
                                }
                                mainHandler.post(runnable)
                            }
                            handlerThread.quitSafely()
                        }, Handler(handlerThread.getLooper()))

                    } catch (e: Throwable) {
                        e.printStackTrace()
                        result.error("fail take snapshot", e.message, e);
                    }
                }
                "getCameraPose" -> {
                    debugLog("getCameraPose")
                    val cameraPose = arSceneView?.arFrame?.camera?.displayOrientedPose
                    if (cameraPose != null) {
                        val width = arSceneView?.width?.toFloat() ?: 0.0f
                        val height = arSceneView?.height?.toFloat() ?: 0.0f
                        val ray = arSceneView?.scene?.camera?.screenPointToRay(width / 2.0f, height / 2.0f)
                        val vector = ray?.origin
                        if (vector != null) {
                            val map: HashMap<String, Any> = HashMap<String, Any>()
                            map["x"] = vector.x
                            map["y"] = vector.y
                            map["z"] = vector.z
                            result.success(map)
                        } else {
                            result.error("fail camera position", "", null)
                        }

                    } else {
                        result.error("not found camera pose", "", null)
                    }
                }
//                "animate" -> {
//                    debugLog(" animate")
//                    val map = call.arguments as HashMap<String, Any>
//                    onAnimate(map)
//                    result.success(null)
//                }
                else -> {
                    result.notImplemented()
                }
            }
        } else {
            debugLog("Impossible call " + call.method + " method on unsupported device")
            job.cancel()
            result.error("Unsupported Device", "", null)
        }
    }

//    private fun onAnimate(params: HashMap<String, Any>) {
//        val name = params["nodeName"] as String
//        val node = allFlutterNodesSet.find { it.name == name }
//        if (node == null) {
//            debugLog("anim. node not found: $name")
//            return
//        }
//        if (node.animationHandler.animStateListener == null) {
//            node.animationHandler.animStateListener = {
//                val map = hashMapOf<String, Any>(
//                    "nodeName" to name,
//                    "state" to "ended",
//                    "percent" to it,
//                )
//                methodChannel.invokeMethod("onAnimChanged", map)
//            }
//        }
//        val progress = params["progress"] as Float?
//        if (progress != null) {
//            debugLog("apply progress: $progress")
//            node.setAnimationProgress(progress)
//            return
//        }
//        val interval = params["interval"] as ArrayList<Float>?
//        if (interval != null) {
//            node.playAnimation(interval)
//            return
//        }
//    }

    override fun cleanup() {
        arSceneView?.scene?.removeOnUpdateListener(sceneUpdateListener)
        removeAllAugmentedImages()
        super.cleanup()
    }

    private fun arSceneViewInit(call: MethodCall, result: MethodChannel.Result) {
        val scene = arSceneView?.scene
        val enableTapRecognizer = call.argument("enableTapRecognizer") ?: false
        if (enableTapRecognizer) {
            scene?.setOnTouchListener { hitTestResult: HitTestResult, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_UP) {
                    if (hitTestResult.node != null) {
                        debugLog(" onNodeTap " + hitTestResult.node?.name)
                        debugLog(hitTestResult.node?.localPosition.toString())
                        debugLog(hitTestResult.node?.worldPosition.toString())
                        methodChannel.invokeMethod("onNodeTap", hitTestResult.node?.name)
                        return@setOnTouchListener true
                    }
                }
                return@setOnTouchListener gestureDetector.onTouchEvent(event)
            }
        }
        scene?.addOnUpdateListener(sceneUpdateListener)
        onResume()
        result.success(null)
    }

    override fun onResume() {
        debugLog("onResume")
        if (arSceneView == null) {
            debugLog("arSceneView NULL")
            return
        }
        debugLog("arSceneView NOT null")

        if (arSceneView?.session == null) {
            debugLog("session NULL")
            if (!ArCoreUtils.hasCameraPermission(activity)) {
                ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
                return
            }

            debugLog("Camera has permission")
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = ArCoreUtils.createArSession(activity, installRequested, false)
                if (session == null) {
                    installRequested = false
                    return
                } else {
                    debugLog("setup session in onResume")

                    applyCameraConfig(session)
                    applySessionConfig(session)
                }
            } catch (e: UnavailableException) {
                ArCoreUtils.handleSessionException(activity, e)
            }
        }

        try {
            arSceneView?.resume()
            allFlutterNodesSet.forEach {
                it.resume()
            }
            debugLog("arSceneView.resume()")
        } catch (ex: CameraNotAvailableException) {
            ArCoreUtils.displayError(activity, "Unable to get camera", ex)
            debugLog("CameraNotAvailableException")
            activity.finish()
            return
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    private fun applySessionConfig(session: Session) {
        //todo: set current config if exists (not new)
        val config = Config(session)
        config.focusMode = Config.FocusMode.AUTO
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        config.augmentedFaceMode = Config.AugmentedFaceMode.DISABLED
        config.cloudAnchorMode = Config.CloudAnchorMode.DISABLED
        config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

        session.configure(config)

        arSceneView?.session = session
//        session.configure(config)
//        arSceneView?.setSessionConfig(config, true)

        arSceneView?.planeRenderer?.isVisible = false
        arSceneView?.planeRenderer?.isShadowReceiver = false
    }

    private fun applyCameraConfig(session: Session) {
        val filter = CameraConfigFilter(session)
        filter.targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)
//        filter.depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE)
        val cameraConfigList = session.getSupportedCameraConfigs(filter)
        session.cameraConfig = cameraConfigList[0]
    }

    fun setupSession(bytes: ByteArray?, useSingleImage: Boolean) {
        debugLog("setupSession()")
        try {
            val session = arSceneView?.session ?: return
            val config = session.config

//            val config = Config(session)
//            config.focusMode = Config.FocusMode.AUTO
//            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            bytes?.let {
                if (useSingleImage) {
                    if (!addImageToAugmentedImageDatabase(config, bytes)) {
                        throw Exception("Could not setup augmented image database")
                    }
                } else {
                    if (!useExistingAugmentedImageDatabase(config, bytes)) {
                        throw Exception("Could not setup augmented image database")
                    }
                }
            }
//            session.configure(config)
//            arSceneView?.setSession(session)
        } catch (ex: Exception) {
            debugLog(ex.localizedMessage)
        }
    }

    fun setupSession(bytesMap: Map<String, ByteArray>?, result: MethodChannel.Result) {
        debugLog("setupSession()")
        try {
            val session = arSceneView?.session
            if (session == null) {
                result.error("setupSession", "Session is not ready!", null)
                return//@runAsync
            }
//-            val config = session.config
            bytesMap?.let {
                val config = Config(session)
                config.focusMode = Config.FocusMode.AUTO
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                addMultipleImagesToAugmentedImageDatabase(config, bytesMap, session)
            }
            result.success(null)
        } catch (ex: Exception) {
            val error = ex.localizedMessage ?: "Can't setup session with images!"
            debugLog(error)
            result.error("setupSession", error, null)
        }
    }

    private fun addMultipleImagesToAugmentedImageDatabase(
        config: Config,
        bytesMap: Map<String, ByteArray>, session: Session
    ) {
        debugLog("addImageToAugmentedImageDatabase")
        val augmentedImageDatabase = arSceneView?.session?.config?.augmentedImageDatabase
            ?: AugmentedImageDatabase(arSceneView?.session)

        launch {
            val operation = async(Dispatchers.Default) {
                for ((key, value) in bytesMap) {
                    val augmentedImageBitmap = loadAugmentedImageBitmap(value)
                    try {
                        augmentedImageDatabase.addImage(key, augmentedImageBitmap)
                    } catch (ex: Exception) {
                        debugLog(
                            "Image with the title $key cannot be added to the database. " +
                                    "The exception was thrown: " + ex.toString()
                        )
                    }
                }
                if (augmentedImageDatabase.numImages == 0) {
                    throw Exception("Could not setup augmented image database")
                }
                config.augmentedImageDatabase = augmentedImageDatabase
                arSceneView?.setSessionConfig(config, true)
            }
            operation.await()
        }
    }

    private fun addImageToAugmentedImageDatabase(config: Config, bytes: ByteArray): Boolean {
        debugLog("addImageToAugmentedImageDatabase")
        try {
            val augmentedImageBitmap = loadAugmentedImageBitmap(bytes) ?: return false
            val augmentedImageDatabase = AugmentedImageDatabase(arSceneView?.session)
            augmentedImageDatabase.addImage("image_name", augmentedImageBitmap)
            config.augmentedImageDatabase = augmentedImageDatabase
            return true
        } catch (ex: Exception) {
            debugLog(ex.localizedMessage)
            return false
        }
    }

    private fun useExistingAugmentedImageDatabase(config: Config, bytes: ByteArray): Boolean {
        debugLog("useExistingAugmentedImageDatabase")
        return try {
            val inputStream = ByteArrayInputStream(bytes)
            val augmentedImageDatabase =
                AugmentedImageDatabase.deserialize(arSceneView?.session, inputStream)
            config.augmentedImageDatabase = augmentedImageDatabase
            true
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image database.", e)
            false
        }
    }

    private fun loadAugmentedImageBitmap(bitmapdata: ByteArray): Bitmap? {
        debugLog("loadAugmentedImageBitmap, size: ${bitmapdata.size}")
        return try {
            BitmapFactory.decodeByteArray(bitmapdata, 0, bitmapdata.size)
        } catch (e: Exception) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e)
            null
        }
    }

    private fun addNodeWithAnchor(
        flutterArCoreNode: FlutterArCoreNode,
        result: MethodChannel.Result
    ) {

        if (arSceneView == null) {
            return
        }

        val anchor = arSceneView?.session?.createAnchor(
            Pose(
                flutterArCoreNode.getPosition(),
                flutterArCoreNode.getRotation()
            )
        )
        if (anchor != null) {
            val anchorNode = AnchorNode(anchor)
            anchorNode.name = "#anchorOf" + flutterArCoreNode.name
            attachNodeToParent(anchorNode, flutterArCoreNode.parentNodeName)

            // we set node's position and rotation to anchorNode
            // so reset them of node
            flutterArCoreNode.position = Vector3.zero()
            flutterArCoreNode.rotation = Quaternion.eulerAngles(Vector3.zero())
            flutterArCoreNode.parentNodeName = anchorNode.name
            onAddNode(flutterArCoreNode, null)

            for (node in flutterArCoreNode.children) {
                node.parentNodeName = flutterArCoreNode.name
                onAddNode(node, null)
            }
        }
        result.success(null)
    }

    override fun onDestroy() {
        debugLog("onDestroy")
        cleanup()
        super.onDestroy()
    }
}