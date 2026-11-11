/*
 * Author: AntiOblivionis
 * QQ: 319641317
 * Github: https://github.com/GoldenglowSusie/
 * Bilibili: 罗德岛T0驭械术师澄闪
 * 
 * Chief Tester: 汐木泽
 * 
 * Co-developed with AI assistants:
 * - Cursor
 * - Claude-4.5-Sonnet
 * - GPT-5
 * - Gemini-2.5-Pro
 */

package com.tgwgroup.MiRearScreenSwitcher;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

/**
 * Quick Settings Tile - 切换至背屏
 * 点击后将当前前台应用切换到背屏
 */
public class SwitchToRearTileService extends TileService {
    private volatile boolean destroyed;
    private boolean switching, listening;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService switchWorker = java.util.concurrent.Executors.newSingleThreadExecutor();

    private static final String TAG = "SwitchToRearTile";

    // 静态变量：保存最后移动到背屏的任务信息（用于接近传感器恢复）
    private static volatile String lastMovedTask = null; // 格式: "packageName:taskId"

    private volatile ITaskService taskService;
    private final Shizuku.UserServiceArgs serviceArgs = new Shizuku.UserServiceArgs(
            new ComponentName("com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName()))
            .daemon(false)
            .processNameSuffix("task_service")
            .debuggable(false)
            .version(1);

    private final ServiceConnection taskServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            taskService = ITaskService.Stub.asInterface(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            taskService = null;
            scheduleReconnectTaskService();
        }
    };

    /**
     * TaskService重连任务
     */


    /**
     * 安排TaskService重连
     */
    private void scheduleReconnectTaskService() {
        // Shared client handles Binder/permission events and bounded backoff.
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        listening = true;

        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            if (android.os.Build.VERSION.SDK_INT >= 29) tile.setSubtitle(null);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                tile.setStateDescription("");
            }
            tile.updateTile();
        }

        bindTaskService();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        listening = false;
        if (!switching) unbindTaskService();
    }

    /**
     * 获取最后移动到背屏的任务信息
     * 
     * @return 格式: "packageName:taskId"，如果没有则返回null
     */
    public static String getLastMovedTask() {
        return lastMovedTask;
    }

    @Override
    public void onClick() {
        super.onClick();
        switchCurrentAppToRearDisplay();
    }

    private void bindTaskService() {
        if (!destroyed) TaskServiceClient.bind(null, taskServiceConnection);
    }

    private void unbindTaskService() {
        // Also cancels an in-flight bind that has not delivered a Binder yet.
        TaskServiceClient.unbind(null, taskServiceConnection, false);
        taskService = null;
    }

    private void switchCurrentAppToRearDisplay() {
        if (taskService == null) {
            Log.w(TAG, "TaskService not available!");
            showTemporaryFeedback("服务未就绪");

            // 尝试重新绑定
            bindTaskService();

            // 延迟重试
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (taskService != null) {
                    performSwitch();
                } else {
                    showTemporaryFeedback("请先打开应用授权");
                }
            }, 1000);
            return;
        }

        performSwitch();
    }

    private void performSwitch() {
        if (destroyed || switching || taskService == null) return;
        switching = true;
        ITaskService service = taskService;
        showTemporaryFeedback("切换中...");
        switchWorker.execute(() -> {
            String moved = null;
            String message = getString(R.string.toast_switch_failed);
            try {
                String rear = service.getForegroundAppOnDisplay(1);
                if (rear != null && !rear.startsWith(getPackageName() + ":") && !rear.startsWith("com.xiaomi.subscreencenter:")) {
                    message = getString(R.string.toast_please_switch_back, getAppName(rear.split(":")[0]));
                } else {
                    String current = service.getCurrentForegroundApp();
                    if (current != null && !destroyed) {
                        int taskId = Integer.parseInt(current.substring(current.lastIndexOf(':') + 1));
                        // HyperOS restarts a force-stopped rear home and can then put the
                        // display back to sleep. Moving the task does not require killing it.
                        if (service.moveTaskToDisplay(taskId, 1)) {
                            moved = current;
                            service.wakeDisplay(1);
                            message = getAppName(current.split(":")[0]) + " " + getString(R.string.toast_cast_to_rear);
                        }
                    }
                }
                service.collapseStatusBar();
            } catch (Exception e) { Log.w(TAG, "Projection failed", e); }
            String result = moved;
            String feedback = message;
            mainHandler.post(() -> {
                switching = false;
                if (!listening) unbindTaskService();
                if (result != null) {
                    lastMovedTask = result;
                    Intent keeper = new Intent(this, RearScreenKeeperService.class).putExtra("lastMovedTask", result);
                    keeper.putExtra("keepScreenOnEnabled", getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                            .getBoolean("flutter.keep_screen_on_enabled", true));
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(keeper); else startService(keeper);
                }
                if (!destroyed) {
                    Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show();
                    showTemporaryFeedback(result == null ? "切换失败" : "已切换");
                }
            });
        });
    }

    private void showTemporaryFeedback(String message) {
        if (destroyed) return;
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            if (android.os.Build.VERSION.SDK_INT >= 29) tile.setSubtitle(message);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                tile.setStateDescription("");
            }
            tile.updateTile();
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (destroyed) return;
            Tile resetTile = getQsTile();
            if (resetTile != null) {
                resetTile.setState(Tile.STATE_INACTIVE);
                if (android.os.Build.VERSION.SDK_INT >= 29) resetTile.setSubtitle(null);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    resetTile.setStateDescription("");
                }
                resetTile.updateTile();
            }
        }, 1500);
    }

    /**
     * 获取应用名称
     */
    private String getAppName(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(appInfo);
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get app name: " + e.getMessage());
        }
        return packageName; // 失败时返回包名
    }
    @Override public void onDestroy() {
        destroyed = true;
        listening = false;
        switchWorker.shutdown();
        if (!switching) unbindTaskService();
        super.onDestroy();
    }
}
