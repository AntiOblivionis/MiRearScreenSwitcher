package com.tgwgroup.MiRearScreenSwitcher;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import androidx.annotation.Keep;
import java.io.File;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shell/root process. Framework signatures come from the running ROM, not Binder ordinals. */
public class TaskService extends ITaskService.Stub {
    private static final String TAG = "TaskService";
    private static final String PACKAGE = "com.tgwgroup.MiRearScreenSwitcher";
    private static final String LAUNCHER = "com.xiaomi.subscreencenter";
    private final FrameworkTasks framework;
    private final HandlerThread events = new HandlerThread("MRSS-task-events");
    private final Handler eventHandler;
    private final RemoteCallbackList<ITaskObserver> observers = new RemoteCallbackList<>();
    private long lastLauncherStop;
    private RecordingSession recording;
    private final RearPowerControl rearPower;
    private final java.util.concurrent.atomic.AtomicBoolean eventPending = new java.util.concurrent.atomic.AtomicBoolean();

    @Keep public TaskService() {
        Log.i(TAG, "TaskService v8: rear recording duration handling");
        RearPowerControl power = null;
        try { power = new RearPowerControl(); }
        catch (Exception e) { Log.w(TAG, "Rear-only power control unavailable", e); }
        rearPower = power;
        FrameworkTasks candidate = null;
        try { candidate = new FrameworkTasks(); }
        catch (Exception e) { Log.w(TAG, "Framework task API unavailable; shell queries only", e); }
        framework = candidate;
        events.start();
        eventHandler = new Handler(events.getLooper());
    }

    @Override public void destroy() {
        synchronized (this) { if (recording != null) recording.stop(); }
        if (rearPower != null) rearPower.close();
        observers.kill();
        if (framework != null) try { framework.stopObserving(); } catch (Exception ignored) { }
        eventHandler.removeCallbacksAndMessages(null);
        events.quitSafely();
        System.exit(0);
    }

    private TaskSnapshot snapshot() throws RemoteException {
        if (framework != null) {
            try { return framework.snapshot(); }
            catch (Exception e) { Log.w(TAG, "Framework query failed; trying shell snapshot", e); }
        }
        try {
            CommandRunner.Result result = CommandRunner.run(5000, "am", "stack", "list");
            if (!result.succeeded()) throw new IllegalStateException("Task query failed");
            return TaskSnapshot.parse(result.output);
        } catch (Exception e) {
            // Unknown is an error, never evidence that the monitored app has exited.
            throw new RemoteException("Task snapshot unavailable: " + e.getClass().getSimpleName());
        }
    }

    @Override public String getCurrentForegroundApp() throws RemoteException {
        for (TaskSnapshot.Task task : snapshot().tasks) {
            if (task.displayId == 0 && task.visible && !task.packageName.equals(PACKAGE)
                    && !task.packageName.equals("com.miui.home")
                    && !task.packageName.equals("com.android.launcher3")) return task.identity();
        }
        return null;
    }

    @Override public int getTaskIdByPackage(String packageName) throws RemoteException {
        for (TaskSnapshot.Task task : snapshot().tasks) {
            if (task.packageName.equals(packageName)) return task.id;
        }
        return -1;
    }

    @Override public boolean moveTaskToDisplay(int taskId, int displayId) throws RemoteException {
        if (taskId < 0 || displayId < 0 || framework == null) return false;
        TaskSnapshot before = snapshot();
        TaskSnapshot.Task task = before.find(taskId);
        if (task == null) return false;
        if (task.displayId == displayId) return true;
        // An organized split-screen root may contain other apps; moving it would move them too.
        if (!before.isSingleTaskRoot(task)) return false;
        try {
            framework.moveRoot(task.rootId, displayId);
            TaskSnapshot.Task moved = snapshot().find(taskId);
            return moved != null && moved.displayId == displayId && moved.packageName.equals(task.packageName);
        } catch (Exception e) {
            Log.w(TAG, "Task move unsupported or rejected by this ROM", e);
            return false;
        }
    }

    @Override public boolean isTaskOnDisplay(int taskId, int displayId) throws RemoteException {
        TaskSnapshot.Task task = snapshot().find(taskId);
        return task != null && task.displayId == displayId;
    }

    @Override public String getForegroundAppOnDisplay(int displayId) throws RemoteException {
        TaskSnapshot.Task task = snapshot().foreground(displayId);
        return task == null ? null : task.identity();
    }

    @Override public boolean launchWakeActivity(int displayId) {
        return displayId >= 0 && command("am", "start", "--display", String.valueOf(displayId),
                "-n", PACKAGE + "/.RearScreenWakeupActivity");
    }

    /** Compatibility workaround at an explicit transition only; never used as a watchdog. */
    @Override public synchronized boolean disableSubScreenLauncher() throws RemoteException {
        TaskSnapshot.Task top = snapshot().foreground(1);
        if (top == null || !LAUNCHER.equals(top.packageName)) return true;
        long now = SystemClock.elapsedRealtime();
        if (lastLauncherStop != 0 && now - lastLauncherStop < 2000) return false;
        lastLauncherStop = now;
        return command("am", "force-stop", LAUNCHER);
    }

    @Override public boolean killLauncherProcess() throws RemoteException { return disableSubScreenLauncher(); }
    @Override public boolean isLauncherProcessRunning() { return command("pidof", LAUNCHER); }
    @Override public boolean enableSubScreenLauncher() throws RemoteException {
        TaskSnapshot.Task top = snapshot().foreground(1);
        // An old activity's onDestroy must not cover a newer projected app.
        if (top != null && !top.packageName.equals(LAUNCHER)) return true;
        return command("am", "start", "--display", "1", "-n", LAUNCHER + "/.SubScreenLauncher");
    }
    @Override public boolean forceStatusBarToMainDisplay() { return false; }
    @Override public boolean collapseStatusBar() { return command("cmd", "statusbar", "collapse"); }

    @Override public int getCurrentRearDpi() {
        try {
            CommandRunner.Result result = CommandRunner.run(5000, "wm", "density", "-d", "1");
            if (!result.succeeded()) return 0;
            Matcher matcher = Pattern.compile("(?:Physical|Override) density:\\s*(\\d+)").matcher(result.output);
            int density = 0;
            while (matcher.find()) density = Integer.parseInt(matcher.group(1));
            return density;
        } catch (Exception e) { return 0; }
    }
    @Override public boolean setRearDpi(int dpi) {
        return dpi >= 72 && dpi <= 1000 && command("wm", "density", String.valueOf(dpi), "-d", "1");
    }
    @Override public boolean resetRearDpi() { return command("wm", "density", "reset", "-d", "1"); }
    @Override public boolean setDisplayRotation(int displayId, int rotation) {
        return displayId >= 0 && rotation >= 0 && rotation <= 3 && command("wm", "user-rotation",
                "-d", String.valueOf(displayId), "lock", String.valueOf(rotation));
    }
    @Override public int getDisplayRotation(int displayId) {
        if (displayId < 0) return -1;
        try {
            CommandRunner.Result result = CommandRunner.run(5000, "wm", "user-rotation", "-d", String.valueOf(displayId));
            if (!result.succeeded()) return -1;
            Matcher matcher = Pattern.compile("\\block\\s+([0-3])\\b").matcher(result.output);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (Exception e) { return -1; }
    }

    @Override public String getRearPhysicalDisplayId() throws RemoteException {
        // The full OEM dump can exceed the bounded command-output limit. The viewport list
        // contains the logical-to-physical mapping; keep pipe failure and ambiguity explicit.
        String viewports = executeShellCommandWithResult(
                "set -o pipefail; dumpsys display | grep -F 'mViewports='");
        String physicalId = PhysicalDisplayId.fromDump(viewports, 1);
        if (physicalId == null) Log.w(TAG, "No unique physical ID for rear display");
        return physicalId;
    }
    @Override public boolean takeRearScreenshot() {
        try {
            String displayId = getRearPhysicalDisplayId();
            if (displayId == null) return false;
            wakeDisplay(1);
            File directory = new File("/storage/emulated/0/Pictures/RearDisplay");
            if (!directory.isDirectory() && !directory.mkdirs()) return false;
            File file = new File(directory, "RD_" + java.util.UUID.randomUUID() + ".png");
            if (!command("screencap", "-p", "-d", displayId, file.getAbsolutePath())
                    || !file.isFile() || file.length() == 0) return false;
            command("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file://" + file);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Screenshot failed", e);
            return false;
        }
    }

    @Override public boolean wakeDisplay(int displayId) {
        if (displayId < 0) return false;
        try {
            Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Method inject = managerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
            Method setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_WAKEUP, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_WAKEUP, 0);
            setDisplay.invoke(down, displayId);
            setDisplay.invoke(up, displayId);
            boolean accepted = (Boolean) inject.invoke(manager, down, 2);
            accepted &= (Boolean) inject.invoke(manager, up, 2);
            return accepted;
        } catch (Exception e) {
            // InputManager can change across ROMs. This fallback runs on state changes, not 10 Hz.
            return command("input", "-d", String.valueOf(displayId), "keyevent", "KEYCODE_WAKEUP");
        }
    }

    @Override public boolean setRearScreenWakeLock(android.os.IBinder owner, boolean enabled) {
        return rearPower != null && rearPower.setHeld(owner, enabled);
    }

    @Override public int getRearInteractiveState() {
        return rearPower == null ? -1 : rearPower.rearInteractiveState();
    }

    private boolean command(String... args) {
        try {
            CommandRunner.Result result = CommandRunner.run(10000, args);
            return result.succeeded() && !result.output.contains("Error:") && !result.output.contains("Exception");
        } catch (Exception e) {
            Log.w(TAG, "Command failed: " + args[0], e);
            return false;
        }
    }
    @Override public boolean executeShellCommand(String cmd) {
        return cmd != null && command("sh", "-c", cmd);
    }
    @Override public String executeShellCommandWithResult(String cmd) throws RemoteException {
        try {
            CommandRunner.Result result = CommandRunner.run(10000, "sh", "-c", cmd);
            if (!result.succeeded()) throw new IllegalStateException("Command failed or output truncated");
            return result.output;
        } catch (Exception e) {
            throw new RemoteException("Shell query failed: " + e.getClass().getSimpleName());
        }
    }

    private final Runnable notifyObservers = () -> {
        int count = observers.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                try { observers.getBroadcastItem(i).onTasksChanged(); } catch (RemoteException ignored) { }
            }
        } finally { observers.finishBroadcast(); eventPending.set(false); }
    };
    private synchronized void tasksChanged() {
        if (eventPending.compareAndSet(false, true)) eventHandler.postDelayed(notifyObservers, 150);
    }
    @Override public synchronized boolean registerTaskObserver(ITaskObserver observer) {
        if (observer == null || framework == null) return false;
        try {
            framework.observe(this::tasksChanged);
            return observers.register(observer);
        } catch (Exception e) {
            Log.w(TAG, "Task events unavailable; client may use bounded polling", e);
            return false;
        }
    }
    @Override public synchronized void unregisterTaskObserver(ITaskObserver observer) {
        observers.unregister(observer);
        if (observers.getRegisteredCallbackCount() == 0 && framework != null) {
            try { framework.stopObserving(); } catch (Exception e) { Log.w(TAG, "Task observer cleanup failed", e); }
            eventHandler.removeCallbacks(notifyObservers);
            eventPending.set(false);
        }
    }

    @Override public synchronized String startRearRecording(IRecordingObserver observer) {
        if (recording != null) return null;
        try {
            wakeDisplay(1);
            RecordingSession session = new RecordingSession(getRearPhysicalDisplayId(), observer);
            recording = session;
            session.watch(() -> { synchronized (TaskService.this) { if (recording == session) recording = null; } });
            return session.path;
        } catch (Exception e) { Log.w(TAG, "Recorder could not start", e); return null; }
    }
    @Override public synchronized boolean stopRearRecording(String path) {
        return recording != null && recording.path.equals(path) && recording.stop();
    }
    @Override public boolean launchRearActivity(String component, android.os.Bundle extras) {
        if (component == null || extras == null) return false;
        boolean charging = component.equals(PACKAGE + "/" + PACKAGE + ".RearScreenChargingActivity");
        if (!charging
                && !component.equals(PACKAGE + "/" + PACKAGE + ".RearScreenNotificationActivity")) return false;
        StringBuilder arguments = new StringBuilder(" -n ").append(CommandRunner.quote(component));
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            String flag = value instanceof String ? " --es " : value instanceof Boolean ? " --ez "
                    : value instanceof Integer ? " --ei " : value instanceof Long ? " --el " : null;
            if (flag == null) return false;
            arguments.append(flag).append(CommandRunner.quote(key)).append(' ').append(CommandRunner.quote(String.valueOf(value)));
        }
        try {
            wakeDisplay(1);
            // -W waits for the actual launch result. Never poll dumpsys 60 times on the UI thread.
            boolean direct = executeShellCommand("am start -W --display 1" + arguments);
            TaskSnapshot.Task task = snapshot().findComponent(component);
            if (direct && task != null && task.displayId == 1) return true;
            if (charging) {
                // A stale task restored after a process restart can finish instead of accepting
                // the new session. Retry once on the rear, never via the main display.
                Log.i(TAG, "Retrying unconfirmed charging launch on rear display");
                boolean retried = executeShellCommand("am start -W --display 1" + arguments);
                TaskSnapshot.Task retriedTask = snapshot().findComponent(component);
                if (retried && retriedTask != null && retriedTask.displayId == 1) return true;
                Log.w(TAG, "Rear-only charging launch not confirmed; refusing main-display fallback");
                return false;
            }
            if (!direct && task != null && task.displayId == 1) return false;
            // Some OEM builds reject a direct rear launch. Preserve the main-then-move workaround.
            if (task == null || !direct) {
                if (!executeShellCommand("am start -W" + arguments)) return false;
                task = snapshot().findComponent(component);
            }
            return task != null && moveTaskToDisplay(task.id, 1);
        } catch (Exception e) { Log.w(TAG, "Rear activity launch failed", e); return false; }
    }
}
