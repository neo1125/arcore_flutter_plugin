import 'dart:typed_data';

import 'package:arcore_flutter_plugin/src/arcore_augmented_image.dart';
import 'package:arcore_flutter_plugin/src/arcore_rotating_node.dart';
import 'package:arcore_flutter_plugin/src/utils/vector_utils.dart';
import 'package:flutter/services.dart';
import 'package:meta/meta.dart';

import 'arcore_hit_test_result.dart';
import 'arcore_node.dart';
import 'arcore_plane.dart';

typedef StringResultHandler = void Function(String text);
typedef UnsupportedHandler = void Function(String text);
typedef ArCoreHitResultHandler = void Function(List<ArCoreHitTestResult> hits);
typedef ArCorePlaneHandler = void Function(ArCorePlane plane);
typedef ArCoreAugmentedImageTrackingHandler = void Function(
    ArCoreAugmentedImage);

const UTILS_CHANNEL_NAME = 'arcore_flutter_plugin/utils';

class ArCoreController {
  static Future<bool> checkArCoreAvailability() async {
    return await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkArCoreApkAvailability');
  }

  static Future<bool> checkIsArCoreInstalled() async {
    return await MethodChannel(UTILS_CHANNEL_NAME)
        .invokeMethod('checkIfARCoreServicesInstalled');
  }

  ArCoreController({required this.id,
    this.enableTapRecognizer,
    this.enablePlaneRenderer,
    this.enableUpdateListener,
    this.debug = false
  }) {
    _channel = MethodChannel('arcore_flutter_plugin_$id');
    _channel.setMethodCallHandler(_handleMethodCalls);
    init();
  }

  final int id;
  final bool? enableUpdateListener;
  final bool? enableTapRecognizer;
  final bool? enablePlaneRenderer;
  final bool debug;
  late MethodChannel _channel;
  StringResultHandler? onError;
  StringResultHandler? onNodeTap;

//  UnsupportedHandler onUnsupported;
  ArCoreHitResultHandler? onPlaneTap;
  ArCorePlaneHandler? onPlaneDetected;
  String trackingState = '';
  ArCoreAugmentedImageTrackingHandler? onTrackingImage;

  Function(String nodeName, String state, double percent)? onAnimChanged;

  init() async {
    try {
      await _channel.invokeMethod<void>('init', {
        'enableTapRecognizer': enableTapRecognizer,
        'enablePlaneRenderer': enablePlaneRenderer,
        'enableUpdateListener': enableUpdateListener,
      });
    } on PlatformException catch (ex) {
      print(ex.message);
    }
  }

  Future<dynamic> _handleMethodCalls(MethodCall call) async {
    if (debug) {
      print('_platformCallHandler call ${call.method} ${call.arguments}');
    }

    switch (call.method) {
      case 'onError':
        if (onError != null) {
          onError!(call.arguments);
        }
        break;
      case 'onNodeTap':
        if (onNodeTap != null) {
          onNodeTap!(call.arguments);
        }
        break;
      case 'onPlaneTap':
        if (onPlaneTap != null) {
          final List<dynamic> input = call.arguments;
          final objects = input
              .cast<Map<dynamic, dynamic>>()
              .map<ArCoreHitTestResult>(
                  (Map<dynamic, dynamic> h) => ArCoreHitTestResult.fromMap(h))
              .toList();
          onPlaneTap!(objects);
        }
        break;
      case 'onPlaneDetected':
        if (enableUpdateListener ?? true && onPlaneDetected != null) {
          final plane = ArCorePlane.fromMap(call.arguments);
          onPlaneDetected!(plane);
        }
        break;
      case 'getTrackingState':
      // TRACKING, PAUSED or STOPPED
        trackingState = call.arguments;
        if (debug) {
          print('Latest tracking state received is: $trackingState');
        }
        break;
      case 'onTrackingImage':
        if (debug) {
          print('flutter onTrackingImage');
        }
        final arCoreAugmentedImage =
            ArCoreAugmentedImage.fromMap(call.arguments);
        onTrackingImage!(arCoreAugmentedImage);
        break;
      case 'togglePlaneRenderer':
        if (debug) {
          print('Toggling Plane Renderer Visibility');
        }
        togglePlaneRenderer();
        break;
      case 'onAnimChanged':
        if (debug) {
          print('onAnimChanged.plugin: $onAnimChanged');
        }
        if (onAnimChanged != null) {
          final String nodeName = call.arguments['nodeName'];
          final String state = call.arguments['state'];
          final double percent = call.arguments['percent'];

          onAnimChanged?.call(nodeName, state, percent);
        }
        break;

      default:
        if (debug) {
          print('Unknown method ${call.method}');
        }
    }
    return Future.value();
  }

  Future<void> addArCoreNode(ArCoreNode node, {String? parentNodeName}) {
    final params = _addParentNodeNameToParams(node.toMap(), parentNodeName);
    if (debug) {
      print(params.toString());
    }
    _addListeners(node);
    return _channel.invokeMethod('addArCoreNode', params);
  }

  Future<dynamic> togglePlaneRenderer() async {
    return _channel.invokeMethod('togglePlaneRenderer');
  }

  Future<dynamic> getTrackingState() async {
    return _channel.invokeMethod('getTrackingState');
  }

  Future<void> pause() async {
    return _channel.invokeMethod('pause');
  }

  Future<void> resume() async {
    return _channel.invokeMethod('resume');
  }

  addArCoreNodeToAugmentedImage(ArCoreNode node, int index,
      {String? parentNodeName}) {
    final params = _addParentNodeNameToParams(node.toMap(), parentNodeName);
    return _channel.invokeMethod(
        'attachObjectToAugmentedImage', {'index': index, 'node': params});
  }

  Future<void> addArCoreNodeWithAnchor(ArCoreNode node,
      {String? parentNodeName}) {
    final params = _addParentNodeNameToParams(node.toMap(), parentNodeName);
    if (debug) {
      print(params.toString());
    }
    _addListeners(node);
    if (debug) {
      print('---------_CALLING addArCoreNodeWithAnchor : $params');
    }
    return _channel.invokeMethod('addArCoreNodeWithAnchor', params);
  }

  Future<void> removeNode({@required String? nodeName}) {
    assert(nodeName != null);
    return _channel.invokeMethod('removeARCoreNode', {'nodeName': nodeName});
  }

  Map<String, dynamic>? _addParentNodeNameToParams(Map<String, dynamic> geometryMap,
      String? parentNodeName) {
    if (parentNodeName != null && parentNodeName.isNotEmpty)
      geometryMap['parentNodeName'] = parentNodeName;
    return geometryMap;
  }

  void _addListeners(ArCoreNode node) {
    node.position?.addListener(() => _handlePositionChanged(node));
    node.rotation?.addListener(() => _handleRotationChanged(node));
    node.scale?.addListener(() => _handleScaleChanged(node));
    node.shape?.materials.addListener(() => _updateMaterials(node));
    if (node is ArCoreRotatingNode) {
      node.degreesPerSecond.addListener(() => _handleAutoRotationChanged(node));
    }
  }

  void _handlePositionChanged(ArCoreNode node) {
    _channel.invokeMethod<void>('positionChanged',
        _getHandlerParams(
            node, {'position': convertVector3ToMap(node.position?.value)}));
  }

  void _handleRotationChanged(ArCoreNode node) {
    _channel.invokeMethod<void>('rotationChanged',
        _getHandlerParams(
            node, {'rotation': convertVector3ToMap(node.rotation?.value)}));
  }

  void _handleScaleChanged(ArCoreNode node) {
    _channel.invokeMethod<void>('scaleChanged',
        _getHandlerParams(
            node, {'scale': convertVector3ToMap(node.scale?.value)}));
  }

  void _handleAutoRotationChanged(ArCoreRotatingNode node) {
    _channel.invokeMethod<void>('rotationChanged',
        {'name': node.name, 'degreesPerSecond': node.degreesPerSecond.value});
  }

  void _updateMaterials(ArCoreNode node) {
    _channel.invokeMethod<void>(
        'updateMaterials', _getHandlerParams(node, node.shape!.toMap()));
  }

  Map<String, dynamic> _getHandlerParams(ArCoreNode node,
      Map<String, dynamic>? params) {
    final Map<String, dynamic> values = <String, dynamic>{'name': node.name}
      ..addAll(params!);
    return values;
  }

  Future<void> loadSingleAugmentedImage({required Uint8List bytes}) {
    return _channel.invokeMethod('load_single_image_on_db', {
      'bytes': bytes,
    });
  }

  Future<void> loadMultipleAugmentedImage(
      {@required Map<String, Uint8List>? bytesMap}) {
    assert(bytesMap != null);
    return _channel.invokeMethod('load_multiple_images_on_db', {
      'bytesMap': bytesMap,
    });
  }

  Future<void> loadAugmentedImagesDatabase({@required Uint8List? bytes}) {
    assert(bytes != null);
    return _channel.invokeMethod('load_augmented_images_database', {
      'bytes': bytes,
    });
  }

  Future<void> dispose() {
    return _channel.invokeMethod<void>('dispose');
  }

  Future<void> cleanup() {
    return _channel.invokeMethod<void>('cleanup');
  }

  Future<void> runGC() {
    return _channel.invokeMethod<void>('runGC');
  }

  Future<void> removeNodeWithIndex(int index) async {
    try {
      return await _channel.invokeMethod('removeARCoreNodeWithIndex', {
        'index': index,
      });
    } catch (ex) {
      print(ex);
    }
  }

  Future<void> animate(
      {required String nodeName, List<double>? interval, double? progress}) async {
    return _channel.invokeMethod<void>(
        'animate', {
      'nodeName': nodeName, 'interval': interval, 'progress': progress,
    });
  }
}
