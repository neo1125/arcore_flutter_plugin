class ArCoreVideo {
  ArCoreVideo({
    required this.url,
    required this.width,
    required this.height,
    this.volume = 0.7,
    this.repeat = -1,
  });

  final String url;
  final int width, height;
  final int repeat;
  final double volume;

  Map<String, dynamic> toMap() => <String, dynamic>{
        'url': url,
        'width': width,
        'height': height,
        'volume': volume,
        'repeat': repeat,
      }..removeWhere((String k, dynamic v) => v == null);
}
