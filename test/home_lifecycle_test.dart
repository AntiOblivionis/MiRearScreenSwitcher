import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mrss/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.display.switcher/task');

  testWidgets('closing home cancels delayed display queries', (tester) async {
    SharedPreferences.setMockInitialValues({'charging_animation_enabled': false});
    final calls = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'getShizukuInfo') return 'unavailable';
      if (call.method == 'getCurrentRearDpi') return 450;
      if (call.method == 'getDisplayRotation') return 0;
      return false;
    });
    await tester.pumpWidget(const DisplaySwitcherApp());
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 3));
    expect(calls, isNot(contains('getCurrentRearDpi')));
    expect(calls, isNot(contains('getDisplayRotation')));
    expect(tester.takeException(), isNull);
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  testWidgets('late DPI response cannot update a disposed home', (tester) async {
    SharedPreferences.setMockInitialValues({'charging_animation_enabled': false});
    final dpi = Completer<int>();
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'getShizukuInfo') return 'unavailable';
      if (call.method == 'getCurrentRearDpi') return dpi.future;
      if (call.method == 'getDisplayRotation') return 0;
      return false;
    });
    await tester.pumpWidget(const DisplaySwitcherApp());
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpWidget(const SizedBox());
    dpi.complete(450);
    await tester.pump();
    expect(tester.takeException(), isNull);
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });
}
