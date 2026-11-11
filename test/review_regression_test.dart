import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:mrss/main.dart';
import 'package:mrss/l10n/app_localizations.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.display.switcher/task');
  Future<void> openHome(
    WidgetTester tester,
    Future<Object?> Function(MethodCall) handler,
  ) async {
    SharedPreferences.setMockInitialValues({
      'charging_animation_enabled': false,
    });
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      handler,
    );
    await tester.pumpWidget(const DisplaySwitcherApp());
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    await tester.ensureVisible(find.text('Set DPI'));
    await tester.pumpAndSettle();
  }

  Object? normal(MethodCall call) {
    if (call.method == 'getCurrentRearDpi') return 450;
    if (call.method == 'getDisplayRotation') return 0;
    if (call.method == 'getShizukuInfo') return 'ready';
    return true;
  }

  Widget page(Widget home) => MaterialApp(
    locale: const Locale('en'),
    localizationsDelegates: const [AppLocalizations.delegate],
    home: home,
  );
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  testWidgets('native DPI rejection never shows success', (tester) async {
    await openHome(
      tester,
      (call) async => call.method == 'setRearDpi' ? false : normal(call),
    );
    await tester.tap(find.text('Set DPI'));
    await tester.pumpAndSettle();
    expect(find.textContaining('REJECTED'), findsOneWidget);
    expect(find.textContaining('Rear screen DPI set to'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('closing during connection wait cancels DPI mutation', (
    tester,
  ) async {
    final connection = Completer<bool>();
    final calls = <String>[];
    await openHome(tester, (call) async {
      calls.add(call.method);
      if (call.method == 'ensureTaskServiceConnected') return connection.future;
      return normal(call);
    });
    await tester.tap(find.text('Set DPI'));
    await tester.pump();
    expect(calls, contains('ensureTaskServiceConnected'));
    await tester.pumpWidget(const SizedBox());
    connection.complete(true);
    await tester.pump();
    expect(calls, isNot(contains('setRearDpi')));
    expect(tester.takeException(), isNull);
  });

  testWidgets('closing during DPI write prevents follow-up refresh', (
    tester,
  ) async {
    final write = Completer<bool>();
    int reads = 0;
    await openHome(tester, (call) async {
      if (call.method == 'getCurrentRearDpi') reads++;
      if (call.method == 'setRearDpi') return write.future;
      return normal(call);
    });
    await tester.tap(find.text('Set DPI'));
    await tester.pump();
    final beforeClose = reads;
    await tester.pumpWidget(const SizedBox());
    write.complete(true);
    await tester.pump();
    expect(reads, beforeClose);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'late installed-app reply does not update a closed selection page',
    (tester) async {
      final apps = Completer<List<Object?>>();
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
        call,
      ) async {
        if (call.method == 'checkQueryAllPackagesPermission') return true;
        if (call.method == 'getSelectedNotificationApps') return <String>[];
        if (call.method == 'getInstalledApps') return apps.future;
        return true;
      });
      await tester.pumpWidget(page(const AppSelectionPage()));
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      apps.complete([]);
      await tester.pump();
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'package permission is rechecked on resume without a polling loop',
    (tester) async {
      bool granted = false;
      int checks = 0, loads = 0;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
        call,
      ) async {
        if (call.method == 'checkQueryAllPackagesPermission') {
          checks++;
          return granted;
        }
        if (call.method == 'getSelectedNotificationApps') return <String>[];
        if (call.method == 'getInstalledApps') {
          loads++;
          return <Object?>[];
        }
        return true;
      });
      await tester.pumpWidget(page(const AppSelectionPage()));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Go to Settings'));
      await tester.pumpAndSettle();
      final before = checks;
      await tester.pump(const Duration(seconds: 35));
      expect(
        checks,
        before,
        reason: 'No periodic permission queries while waiting',
      );
      granted = true;
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(checks, before + 1);
      expect(loads, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'late notification-setting completion after closing is harmless',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final save = Completer<bool>();
      bool writeStarted = false;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
        call,
      ) async {
        if (call.method == 'setNotificationPrivacyHideTitle') {
          writeStarted = true;
          return save.future;
        }
        return true;
      });
      await tester.pumpWidget(page(const NotificationSettingsPage()));
      await tester.pumpAndSettle();
      final titleRow = find
          .ancestor(
            of: find.text('Hide Notification Title'),
            matching: find.byType(Row),
          )
          .first;
      await tester.tap(
        find.descendant(of: titleRow, matching: find.byType(InkWell)),
      );
      await tester.pump();
      expect(writeStarted, isTrue);
      await tester.pumpWidget(const SizedBox());
      save.complete(true);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    },
  );
}
