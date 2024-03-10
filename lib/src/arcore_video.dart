class ArCoreVideo {
  ArCoreVideo({
    required this.url,
    required this.width,
    required this.height,
    this.volume = 0.7,
    this.repeat = -1,
    this.chromaKeyColor = null,
  });

  final String url;
  final int width, height;
  final int repeat;
  final double volume;
  final String? chromaKeyColor;

  Map<String, dynamic> toMap() => <String, dynamic>{
        'url': url,
        'width': width,
        'height': height,
        'volume': volume,
        'repeat': repeat,
        'chromaKeyColor': chromaKeyColor,
      }..removeWhere((String k, dynamic v) => v == null);
}
