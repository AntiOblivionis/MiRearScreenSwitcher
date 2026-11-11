package com.tgwgroup.MiRearScreenSwitcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Event-driven lifetime of one projected task. All blocking IPC is off the main thread. */
public class RearScreenKeeperService extends Service implements SensorEventListener {
    private static final String TAG = "RearScreenKeeperService";
    private static final String CHANNEL_ID = "rear_screen_keeper";
    private static final int NOTIFICATION_ID = 10001;
    private static volatile RearScreenKeeperService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ITaskService taskService;
    private String monitoredTaskInfo;
    private boolean destroyed, paused, observing, checking, returning, checkAgain;
    private volatile boolean keepScreenOnEnabled;
    private boolean proximityEnabled, covered;
    private int generation, mismatches;
    private long scheduledCheckAt;
    private SensorManager sensors;
    private Sensor rearProximity;

    public static void pauseMonitoring() {
        RearScreenKeeperService service = instance;
        if (service != null) service.main.post(() -> {
            service.paused = true;
            service.generation++;
            service.main.removeCallbacks(service.checkTask);
            service.scheduledCheckAt = 0;
        });
    }
    public static void resumeMonitoring() {
        RearScreenKeeperService service = instance;
        if (service != null) service.main.post(() -> {
            service.paused = false;
            service.generation++;
            service.mismatches = 0;
            service.scheduleCheck(750);
        });
    }
    public static boolean isRunning() { return instance != null; }
    public static void stop() {
        RearScreenKeeperService service = instance;
        if (service != null) service.main.post(service::stopSelf);
    }

    private final ITaskObserver observer = new ITaskObserver.Stub() {
        @Override public void onTasksChanged() { main.post(() -> scheduleCheck(150)); }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (destroyed) return;
            ITaskService connected = ITaskService.Stub.asInterface(binder);
            taskService = connected;
            generation++;
            worker.execute(() -> {
                boolean registered;
                try { registered = connected.registerTaskObserver(observer); }
                catch (Exception e) { registered = false; }
                final boolean result = registered;
                main.post(() -> {
                    if (destroyed || taskService != connected) return;
                    observing = result;
                    scheduleCheck(0);
                });
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            taskService = null;
            observing = false;
            generation++;
            main.removeCallbacks(checkTask);
            scheduledCheckAt = 0;
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel(this);
        proximityEnabled = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getBoolean("flutter.proximity_sensor_enabled", true);
        keepScreenOnEnabled = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                .getBoolean("flutter.keep_screen_on_enabled", true);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensors != null) {
            for (Sensor sensor : sensors.getSensorList(Sensor.TYPE_ALL)) {
                String name = sensor.getName().toLowerCase(java.util.Locale.ROOT);
                boolean rear = name.contains("back") || name.contains("rear");
                boolean proximity = sensor.getType() == Sensor.TYPE_PROXIMITY || name.contains("proximity");
                boolean eventSensor = sensor.getReportingMode() == Sensor.REPORTING_MODE_ON_CHANGE;
                if (rear && proximity && eventSensor
                        && (rearProximity == null || (!rearProximity.isWakeUpSensor() && sensor.isWakeUpSensor()))) {
                    rearProximity = sensor;
                }
            }
            if (rearProximity != null) Log.i(TAG, "Rear proximity sensor: " + rearProximity.getName());
            else Log.w(TAG, "Rear proximity sensor unavailable");
        }
        TaskServiceClient.bind(null, connection);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String saved = getSharedPreferences("mrss_keeper", MODE_PRIVATE).getString("task", null);
        if (intent != null && intent.hasExtra("lastMovedTask")) saved = intent.getStringExtra("lastMovedTask");
        if (saved != null && saved.matches("[A-Za-z0-9_.$]+:[0-9]+")) monitoredTaskInfo = saved;
        // Even a settings-only foreground-service start must promote immediately.
        startForeground(NOTIFICATION_ID, buildNotification());
        if (monitoredTaskInfo == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        getSharedPreferences("mrss_keeper", MODE_PRIVATE).edit().putString("task", monitoredTaskInfo).apply();
        generation++;
        mismatches = 0;
        String action = intent == null ? null : intent.getAction();
        if ("ACTION_RETURN_TO_MAIN".equals(action)) {
            returnToMain();
            return START_STICKY;
        }
        if ("ACTION_SET_PROXIMITY_ENABLED".equals(action)) {
            proximityEnabled = intent.getBooleanExtra("enabled", true);
        }
        if (intent != null && intent.hasExtra("keepScreenOnEnabled")) {
            keepScreenOnEnabled = intent.getBooleanExtra("keepScreenOnEnabled", true);
        }
        if ("ACTION_SET_KEEP_SCREEN_ON_ENABLED".equals(action)) {
            keepScreenOnEnabled = intent.getBooleanExtra("enabled", true);
        }
        if (sensors != null) {
            sensors.unregisterListener(this);
            covered = false;
            main.removeCallbacks(proximityReturn);
            if (proximityEnabled && rearProximity != null) {
                boolean registered = sensors.registerListener(this, rearProximity, SensorManager.SENSOR_DELAY_NORMAL);
                Log.i(TAG, "Rear proximity listener registered: " + registered + ", max=" + rearProximity.getMaximumRange());
            }
        }
        if (keepScreenOnEnabled) RearWakeController.acquireKeepingBright(this, this, () -> keepScreenOnEnabled);
        else RearWakeController.release(this);
        scheduleCheck(0);
        return START_STICKY;
    }

    private void scheduleCheck(long delay) {
        if (destroyed || paused || monitoredTaskInfo == null || taskService == null) return;
        long deadline = android.os.SystemClock.uptimeMillis() + delay;
        // A stream of callbacks must not keep postponing the same pending check forever.
        if (scheduledCheckAt != 0 && scheduledCheckAt <= deadline) return;
        main.removeCallbacks(checkTask);
        scheduledCheckAt = deadline;
        main.postAtTime(checkTask, deadline);
    }
    private final Runnable checkTask = () -> {
        scheduledCheckAt = 0;
        if (destroyed || paused || taskService == null) return;
        if (checking) { checkAgain = true; return; }
        checking = true;
        int epoch = generation;
        ITaskService service = taskService;
        String identity = monitoredTaskInfo;
        worker.execute(() -> {
            String foreground = null;
            boolean known = false;
            try { foreground = service.getForegroundAppOnDisplay(1); known = true; }
            catch (Exception e) { Log.w(TAG, "Task state unknown; retaining session", e); }
            String current = foreground;
            boolean valid = known;
            main.post(() -> {
                checking = false;
                boolean dirty = checkAgain;
                checkAgain = false;
                if (destroyed) return;
                if (paused || epoch != generation || service != taskService) { scheduleCheck(0); return; }
                if (!valid) mismatches = 0;
                if (valid) {
                    boolean temporary = current != null && current.startsWith(getPackageName() + ":");
                    mismatches = identity.equals(current) || temporary ? 0 : mismatches + 1;
                    if (mismatches >= 2) {
                        getSharedPreferences("mrss_keeper", MODE_PRIVATE).edit().remove("task").apply();
                        stopSelf();
                        return;
                    }
                }
                // Confirm a transition once. Only unsupported task events use periodic polling.
                if (mismatches > 0) scheduleCheck(750);
                else if (dirty && valid) scheduleCheck(150);
                else if (!observing || !valid) scheduleCheck(15000);
            });
        });
    };

    private void returnToMain() {
        if (taskService == null || monitoredTaskInfo == null || returning) return;
        returning = true;
        int epoch = generation;
        String identity = monitoredTaskInfo;
        ITaskService service = taskService;
        worker.execute(() -> {
            boolean success = false;
            try {
                int id = Integer.parseInt(identity.substring(identity.lastIndexOf(':') + 1));
                // Do not pull an old task after the user has switched to another rear app.
                if (identity.equals(service.getForegroundAppOnDisplay(1))) success = service.moveTaskToDisplay(id, 0);
            } catch (Exception e) { Log.w(TAG, "Return failed", e); }
            final boolean moved = success;
            main.post(() -> {
                returning = false;
                if (destroyed || epoch != generation) return;
                if (moved) {
                    Toast.makeText(this, "已返回主屏", Toast.LENGTH_SHORT).show();
                    getSharedPreferences("mrss_keeper", MODE_PRIVATE).edit().remove("task").apply();
                    stopSelf();
                } else Toast.makeText(this, "返回失败，请检查 Shizuku 和背屏任务状态", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private final Runnable proximityReturn = () -> {
        if (!destroyed && proximityEnabled && covered) returnToMain();
    };
    @Override public void onSensorChanged(SensorEvent event) {
        if (!proximityEnabled || event.sensor != rearProximity || event.values.length == 0) return;
        boolean nowCovered = event.values[0] < rearProximity.getMaximumRange() * 0.2f;
        if (nowCovered != covered) {
            covered = nowCovered;
            Log.i(TAG, "Rear proximity covered=" + covered + ", value=" + event.values[0]);
            main.removeCallbacks(proximityReturn);
            if (covered) main.postDelayed(proximityReturn, 1500);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override public void onDestroy() {
        destroyed = true;
        generation++;
        main.removeCallbacksAndMessages(null);
        RearWakeController.release(this);
        if (sensors != null) sensors.unregisterListener(this);
        ITaskService service = taskService;
        worker.execute(() -> {
            if (service != null) {
                try { service.unregisterTaskObserver(observer); } catch (Exception ignored) { }
                try { service.enableSubScreenLauncher(); } catch (Exception ignored) { }
            }
            TaskServiceClient.unbind(null, connection, false);
        });
        worker.shutdown();
        taskService = null;
        if (instance == this) instance = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.notif_kernel_service), NotificationManager.IMPORTANCE_LOW);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    public static Notification createServiceNotification(Context context) {
        createChannel(context);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_kernel_service))
                .setContentText(context.getString(R.string.notif_mrss_running))
                .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true).setShowWhen(false).build();
    }
    private Notification buildNotification() {
        Intent intent = new Intent(this, RearScreenKeeperService.class).setAction("ACTION_RETURN_TO_MAIN");
        PendingIntent pending = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String app = monitoredTaskInfo == null ? "MRSS" : monitoredTaskInfo.split(":")[0];
        try { app = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app, 0)).toString(); }
        catch (Exception ignored) { }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(app + " " + getString(R.string.notif_running_on_rear))
                .setContentText(getString(R.string.notif_click_to_return, app))
                .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pending).setOngoing(true).setShowWhen(false).build();
    }
}
