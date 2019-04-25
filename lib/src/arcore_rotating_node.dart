import 'package:arcore_flutter_plugin/src/arcore_node.dart';
import 'package:flutter/widgets.dart';
import 'package:vector_math/vector_math_64.dart';

import 'arcore_geometry.dart';

class ArCoreRotatingNode extends ArCoreNode {
  ArCoreRotatingNode({
    this.geometry,
    double degreesPerSecond,
    Vector3 position,
    Vector3 scale,
    Vector4 rotation,
    String name,
  })  : degreesPerSecond = ValueNotifier(90.0),
        super(
          geometry: geometry,
          name: name,
          position: position,
          scale: scale,
        );

  final ArCoreGeometry geometry;

  final ValueNotifier<double> degreesPerSecond;

  Map<String, dynamic> toMap() => <String, dynamic>{
        'degreesPerSecond': this.degreesPerSecond.value,
      }
        ..addAll(super.toMap())
        ..removeWhere((String k, dynamic v) => v == null);
}
