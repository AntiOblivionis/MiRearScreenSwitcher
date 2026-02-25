import 'dart:ui';
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class RearMediaWidget extends StatefulWidget {
  const RearMediaWidget({super.key});

  @override
  State<RearMediaWidget> createState() => _RearMediaWidgetState();
}

class _RearMediaWidgetState extends State<RearMediaWidget> {
  static const MethodChannel _channel = MethodChannel('com.display.switcher/media');

  // Media State
  String _songName = "Waiting for media...";
  String _artistName = "";
  Uint8List? _albumArt;
  bool _isPlaying = false;
  String _appName = "Music";
  Uint8List? _appIcon;
  
  // Real-time Progress State
  int _positionMs = 0;
  int _durationMs = 0;
  double _playbackSpeed = 1.0;
  int _lastReceivedTimeMs = 0;
  Timer? _progressTimer;

  @override
  void initState() {
    super.initState();
    _channel.setMethodCallHandler(_handleMethodCall);
    // Request initial state on launch
    _channel.invokeMethod('requestInitialState');
    
    // Smart real-time progress update without battery drain
    // Interpolates position locally based on speed, only when playing
    _progressTimer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
      if (_isPlaying && mounted && _durationMs > 0) {
        setState(() {}); // Trigger redraw to interpolate current position smoothly
      }
    });
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'updateMediaState':
        if (mounted) {
          setState(() {
            _songName = call.arguments['title'] ?? 'Unknown Title';
            _artistName = call.arguments['artist'] ?? 'Unknown Artist';
            _albumArt = call.arguments['albumArt'];
            _isPlaying = call.arguments['isPlaying'] ?? false;
            _appName = call.arguments['appName'] ?? 'Music';
            _appIcon = call.arguments['appIcon'];
            
            // Sync progressive tracking
            _positionMs = call.arguments['position'] ?? 0;
            _durationMs = call.arguments['duration'] ?? 0;
            _playbackSpeed = call.arguments['speed'] ?? 1.0;
            _lastReceivedTimeMs = DateTime.now().millisecondsSinceEpoch;
          });
        }
        break;
    }
  }

  // --- Handlers for playback controls ---
  void _playPause() {
    _channel.invokeMethod('playPause');
    setState(() {
      _isPlaying = !_isPlaying;
      _lastReceivedTimeMs = DateTime.now().millisecondsSinceEpoch;
    });
  }
  
  void _skipNext() {
    _channel.invokeMethod('skipNext');
    setState(() {
      _positionMs = 0;
      _lastReceivedTimeMs = DateTime.now().millisecondsSinceEpoch;
    });
  }
  
  void _skipPrev() {
    _channel.invokeMethod('skipPrev');
    setState(() {
      _positionMs = 0;
      _lastReceivedTimeMs = DateTime.now().millisecondsSinceEpoch;
    });
  }
  
  String _formatDuration(int ms) {
    if (ms <= 0) return "0:00";
    int seconds = (ms / 1000).truncate();
    int minutes = (seconds / 60).truncate();
    int remainingSeconds = seconds % 60;
    return '$minutes:${remainingSeconds.toString().padLeft(2, '0')}';
  }

  @override
  void dispose() {
    _progressTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;

    // Calculate smart real-time position
    int currentPos = _positionMs;
    if (_isPlaying) {
      int timeDelta = DateTime.now().millisecondsSinceEpoch - _lastReceivedTimeMs;
      currentPos += (timeDelta * _playbackSpeed).toInt();
    }
    if (currentPos > _durationMs) currentPos = _durationMs;
    if (currentPos < 0) currentPos = 0;

    double progressValue = 0.0;
    if (_durationMs > 0) {
      progressValue = currentPos / _durationMs;
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: GestureDetector(
        onVerticalDragEnd: (details) {
          // Detect downward swipe (velocity > 0 indicates moving down)
          if (details.primaryVelocity != null && details.primaryVelocity! > 300) {
            // User swiped down significantly, hide the widget
            _channel.invokeMethod('hideMediaWidget');
          }
        },
        child: ClipRRect(
          borderRadius: BorderRadius.circular(16.0), // Rounded corners matching the frame
          child: Stack(
            children: [
              // 1. Background Layer: Full screen album art
              Positioned.fill(
                child: _albumArt != null
                    ? Image.memory(_albumArt!, fit: BoxFit.cover)
                    : Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [Colors.grey.shade900, Colors.black],
                        ),
                      ),
                    ),
            ),
            
            // 2. Immersive dark vignette overlay for text legibility
            Positioned.fill(
              child: Container(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.centerLeft,
                    end: Alignment.centerRight,
                    colors: [
                      Colors.black87, // Darker near camera
                      Colors.black54, // Lighter towards the right
                    ],
                  ),
                ),
              ),
            ),

            // 3. Content Layer Framework (30% Left Dead Zone, 70% Right Content)
            Positioned.fill(
              child: Row(
                children: [
                  // Camera Cutout "Dead Zone"
                  Expanded(flex: 3, child: const SizedBox()),
                  
                  // Interactive UI Area
                  Expanded(
                    flex: 7,
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(8.0, 16.0, 20.0, 16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Top Right: App Icon & Label
                          Row(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: [
                              if (_appIcon != null) ...[
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(4.0),
                                  child: Image.memory(_appIcon!, width: 14, height: 14, fit: BoxFit.cover),
                                ),
                                const SizedBox(width: 6),
                              ],
                              Text(
                                _appName.toUpperCase(),
                                style: const TextStyle(
                                  color: Colors.white70,
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  letterSpacing: 1.2,
                                ),
                              ),
                            ],
                          ),
                          
                          const Spacer(),
                          
                          // Center Right Text: Song Title & Artist
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                _songName,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 20,
                                  fontWeight: FontWeight.w800, // Bold
                                  fontFamily: 'sans-serif',
                                  letterSpacing: 0.5,
                                ),
                                maxLines: 2,
                                textAlign: TextAlign.right,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 2),
                              Text(
                                _artistName,
                                style: const TextStyle(
                                  color: Colors.white70, // Lighter white
                                  fontSize: 16,
                                  fontWeight: FontWeight.w600,
                                  fontFamily: 'sans-serif',
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                          
                          const SizedBox(height: 12),
                          
                          // Bottom Right: Playback Controls
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.skip_previous_rounded, color: Colors.white, size: 36),
                                onPressed: _skipPrev,
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(),
                                splashRadius: 24,
                              ),
                              IconButton(
                                icon: Icon(
                                  _isPlaying ? Icons.pause_circle_filled_rounded : Icons.play_circle_fill_rounded,
                                  color: Colors.white,
                                  size: 52,
                                ),
                                onPressed: _playPause,
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(),
                                splashRadius: 32,
                              ),
                              IconButton(
                                icon: const Icon(Icons.skip_next_rounded, color: Colors.white, size: 36),
                                onPressed: _skipNext,
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(),
                                splashRadius: 24,
                              ),
                            ],
                          ),
                          
                          const SizedBox(height: 4),
                          
                          // Time markers & Progress Bar container
                          Column(
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    _formatDuration(currentPos),
                                    style: const TextStyle(color: Colors.white54, fontSize: 11, fontWeight: FontWeight.w500),
                                  ),
                                  Text(
                                    _formatDuration(_durationMs),
                                    style: const TextStyle(color: Colors.white54, fontSize: 11, fontWeight: FontWeight.w500),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 4),
                              // Slim, subtle light red progress bar
                              ClipRRect(
                                borderRadius: BorderRadius.circular(2.0),
                                child: Container(
                                  height: 3,
                                  width: double.infinity,
                                  color: Colors.white.withOpacity(0.2), // Track background
                                  child: Align(
                                    alignment: Alignment.centerLeft,
                                    child: FractionallySizedBox(
                                      widthFactor: progressValue,
                                      child: Container(
                                        color: Colors.redAccent.shade100, // Light red
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ]
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ));
  }
}
