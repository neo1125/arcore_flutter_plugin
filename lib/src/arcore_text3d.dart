import 'dart:convert';

class ArCoreText3d {
  ArCoreText3d({
    this.text,
    this.color,
    this.align = "left",
    this.shadowOn = true,
    this.shadow = const {},
  })
      : assert(text != null),
        assert(color != null && color.length >= 3);

  final String text;
  final String color;
  final String align;
  final bool shadowOn;
  final Map shadow;

  Map<String, dynamic> toMap() =>
      <String, dynamic>{
        'text': text,
        'color': color,
        'align': align,
        'shadowOn': shadowOn,
        'shadow': shadow,
      }
        ..removeWhere((String k, dynamic v) => v == null);
}
