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

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

/**
 * V2.6: URI命令处理服务
 * 在后台静默执行URI命令，不显示UI
 * 复用现有的TileService切换逻辑
 */
public class UriCommandService extends IntentService {
    private volatile boolean destroyed;

    private static final String TAG = "UriCommandService";

    private volatile ITaskService taskService;
    private volatile java.util.concurrent.CountDownLatch serviceReady = new java.util.concurrent.CountDownLatch(1);
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
            serviceReady.countDown();
            Log.d(TAG, "✓ TaskService已连接");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceReady = new java.util.concurrent.CountDownLatch(1);
            taskService = null;
        }
    };

    public UriCommandService() {
        super("UriCommandService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bindTaskService();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null)
            return;

        Uri uri = intent.getData();
        if (uri == null || !"mrss".equals(uri.getScheme())) {
            return;
        }

        Log.d(TAG, "🔗 处理URI: " + uri.toString());

        // 确保TaskService已连接
        if (!ensureTaskServiceConnected()) {
            Log.e(TAG, "❌ TaskService未连接");
            return;
        }

        String host = uri.getHost();
        if (host == null)
            return;

        switch (host) {
            case "switch":
                handleSwitch(uri);
                break;
            case "return":
                handleReturn(uri);
                break;
            case "screenshot":
                handleScreenshot();
                break;
            case "config":
                handleConfig(uri);
                break;
        }
    }

    private boolean ensureTaskServiceConnected() {
        if (taskService != null) return true;
        java.util.concurrent.CountDownLatch ready = serviceReady;
        bindTaskService();
        try {
            ready.await(3, java.util.concurrent.TimeUnit.SECONDS);
            return taskService != null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void bindTaskService() {
        if (!destroyed) TaskServiceClient.bind(null, taskServiceConnection);
    }

    /**
     * 处理切换命令 - 复用TileService逻辑
     */
    private void handleSwitch(Uri uri) {
        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "🔄 处理SWITCH命令");
        Log.d(TAG, "URI: " + uri.toString());

        try {
            // 0. 检查背屏是否已有应用在运行（拒绝重复投放）
            try {
                String rearForegroundApp = taskService.getForegroundAppOnDisplay(1);
                Log.d(TAG, "背屏前台应用: " + rearForegroundApp);

                if (rearForegroundApp != null && !rearForegroundApp.isEmpty()) {
                    // 排除允许的进程：
                    // 1. MRSS自己的Activity（充电动画、通知动画、唤醒等）
                    // 2. 小米官方Launcher（com.xiaomi.subscreencenter.SubScreenLauncher）
                    if (!rearForegroundApp.startsWith(getPackageName() + ":") &&
                            !rearForegroundApp.startsWith("com.xiaomi.subscreencenter:")) {
                        Log.w(TAG, "❌ 背屏已有应用在运行: " + rearForegroundApp);
                        Log.d(TAG, "════════════════════════════════════════");
                        return;
                    } else {
                        Log.d(TAG, "✓ 背屏空闲或仅有官方Launcher/MRSS临时Activity");
                    }
                } else {
                    Log.d(TAG, "✓ 背屏空闲");
                }
            } catch (Exception e) {
                Log.w(TAG, "检查背屏占用失败: " + e.getMessage());
                showToast(getString(R.string.toast_switch_failed));
                return;
            }

            // 1. 确定目标
            String currentParam = uri.getQueryParameter("current");
            String packageName = uri.getQueryParameter("packageName");
            String activity = uri.getQueryParameter("activity");

            Log.d(TAG, "参数 - current: " + currentParam + ", packageName: " + packageName + ", activity: " + activity);

            if ("true".equalsIgnoreCase(currentParam) || "1".equals(currentParam)) {
                // 切换当前应用 - 完全复用TileService逻辑
                Log.d(TAG, "→ 模式：切换当前应用");
                // 先应用配置参数，再切换
                applyConfigParams(uri);
                switchCurrentAppToRear();
            } else if (activity != null) {
                // 启动指定Activity到背屏
                Log.d(TAG, "→ 模式：启动指定Activity");
                switchSpecificAppToRear(activity, null, uri);
            } else if (packageName != null) {
                // 启动指定包名到背屏
                Log.d(TAG, "→ 模式：启动指定包名");
                switchSpecificAppToRear(null, packageName, uri);
            } else {
                Log.w(TAG, "⚠ 未指定切换目标");
            }

            Log.d(TAG, "════════════════════════════════════════");
        } catch (Exception e) {
            Log.e(TAG, "❌ 切换命令失败", e);
            e.printStackTrace();
            Log.d(TAG, "════════════════════════════════════════");
        }
    }

    /**
     * 切换当前应用到背屏 - 完全复用TileService的逻辑
     */
    private void switchCurrentAppToRear() {
        try {
            String rear = taskService.getForegroundAppOnDisplay(1);
            if (rear != null && !rear.startsWith(getPackageName() + ":") && !rear.startsWith("com.xiaomi.subscreencenter:")) return;
            String current = taskService.getCurrentForegroundApp();
            if (current == null) { showToast(getString(R.string.toast_app_not_found)); return; }
            int id = Integer.parseInt(current.substring(current.lastIndexOf(':') + 1));
            taskService.disableSubScreenLauncher();
            if (!taskService.moveTaskToDisplay(id, 1)) throw new IllegalStateException("Task move failed");
            Intent keeper = new Intent(this, RearScreenKeeperService.class).putExtra("lastMovedTask", current);
            keeper.putExtra("keepScreenOnEnabled", getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                    .getBoolean("flutter.keep_screen_on_enabled", true));
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(keeper); else startService(keeper);
            taskService.wakeDisplay(1);
            showToast(getAppName(current.substring(0, current.lastIndexOf(':'))) + " " + getString(R.string.toast_cast_to_rear));
        } catch (Exception e) {
            Log.w(TAG, "URI projection failed", e);
            showToast(getString(R.string.toast_switch_failed));
        }
    }

    /**
     * 切换指定应用到背屏（packageName或activity）
     */
    private void switchSpecificAppToRear(String activity, String packageName, Uri uri) {
        try {
            String target = packageName;
            if (activity != null) {
                if (!activity.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) throw new IllegalArgumentException("Invalid component");
                target = activity.substring(0, activity.indexOf('/'));
            }
            if (target == null || !target.matches("[A-Za-z0-9_]+(?:[.][A-Za-z0-9_]+)+")) throw new IllegalArgumentException("Invalid package");
            applyConfigParams(uri);
            int taskId = taskService.getTaskIdByPackage(target);
            taskService.disableSubScreenLauncher();
            if (taskId < 0 || activity != null) {
                String command = "am start -W --user current --display 1 " + (activity != null
                        ? "-n " + CommandRunner.quote(activity)
                        : "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p " + CommandRunner.quote(target));
                if (!taskService.executeShellCommand(command)) throw new IllegalStateException("Launch rejected");
                taskId = taskService.getTaskIdByPackage(target);
            }
            if (taskId < 0 || !taskService.moveTaskToDisplay(taskId, 1)) throw new IllegalStateException("Task move failed");
            String identity = target + ":" + taskId;
            if (!identity.equals(taskService.getForegroundAppOnDisplay(1))) throw new IllegalStateException("Target is not foreground");
            Intent keeper = new Intent(this, RearScreenKeeperService.class).putExtra("lastMovedTask", identity);
            keeper.putExtra("keepScreenOnEnabled", getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                    .getBoolean("flutter.keep_screen_on_enabled", true));
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(keeper); else startService(keeper);
            taskService.wakeDisplay(1);
            showToast(getAppName(target) + " " + getString(R.string.toast_cast_to_rear));
        } catch (Exception e) {
            Log.w(TAG, "Requested app could not be projected", e);
            showToast(getString(R.string.toast_switch_failed));
        }
    }

    /**
     * 处理拉回命令 - 完全复用现有逻辑
     */
    private void handleReturn(Uri uri) {
        try {
            String currentParam = uri.getQueryParameter("current");
            String taskIdStr = uri.getQueryParameter("taskId");
            String packageName = uri.getQueryParameter("packageName");

            int targetTaskId = -1;
            String targetPackage = null;

            if ("true".equalsIgnoreCase(currentParam) || "1".equals(currentParam)) {
                String rearApp = taskService.getForegroundAppOnDisplay(1);
                if (rearApp != null && rearApp.contains(":")) {
                    String[] parts = rearApp.split(":");
                    targetPackage = parts[0];
                    targetTaskId = Integer.parseInt(parts[1]);
                }
            } else if (taskIdStr != null) {
                targetTaskId = Integer.parseInt(taskIdStr);
                // 尝试从背屏前台应用获取包名
                String rearApp = taskService.getForegroundAppOnDisplay(1);
                if (rearApp != null && rearApp.contains(":")) {
                    targetPackage = rearApp.split(":")[0];
                }
            } else if (packageName != null) {
                String rearApp = taskService.getForegroundAppOnDisplay(1);
                if (rearApp != null && rearApp.startsWith(packageName + ":")) {
                    targetPackage = packageName;
                    targetTaskId = Integer.parseInt(rearApp.split(":")[1]);
                }
            }

            if (targetTaskId != -1) {
                // 检查任务是否真的在背屏
                boolean onRear = taskService.isTaskOnDisplay(targetTaskId, 1);

                if (onRear) {
                    String appName = getAppName(targetPackage != null ? targetPackage : String.valueOf(targetTaskId));

                    // 步骤1: 拉回主屏
                    if (!taskService.moveTaskToDisplay(targetTaskId, 0)) {
                        showToast(getString(R.string.toast_switch_failed));
                        return;
                    }
                    Log.d(TAG, "✅ 已拉回主屏 (taskId=" + targetTaskId + ")");

                    // 步骤2: 恢复官方Launcher（关键！）
                    try {
                        taskService.enableSubScreenLauncher();
                        Log.d(TAG, "✓ Launcher已恢复");
                    } catch (Exception e) {
                        Log.w(TAG, "恢复Launcher失败: " + e.getMessage());
                    }

                    // 步骤3: 停止RearScreenKeeperService（如果在运行）
                    try {
                        stopService(new Intent(this, RearScreenKeeperService.class));
                        Log.d(TAG, "✓ KeeperService已停止");
                    } catch (Exception e) {
                        Log.w(TAG, "停止KeeperService失败: " + e.getMessage());
                    }

                    // Toast提示
                    showToast(appName + " " + getString(R.string.toast_return_to_main));
                } else {
                    Log.w(TAG, "⚠ 任务不在背屏");
                    showToast(getString(R.string.toast_not_on_rear));
                }
            } else {
                Log.w(TAG, "⚠ 未找到要拉回的任务");
                showToast(getString(R.string.toast_app_not_found));
            }
        } catch (Exception e) {
            Log.e(TAG, "拉回命令失败", e);
        }
    }

    /**
     * 处理截图命令
     */
    private void handleScreenshot() {
        try {
            boolean success = taskService.takeRearScreenshot();

            showToast(getString(success ? R.string.toast_screenshot_saved : R.string.toast_screenshot_failed));
        } catch (Exception e) {
            Log.e(TAG, "截图命令失败", e);
            showToast(getString(R.string.toast_screenshot_failed));
        }
    }

    /**
     * 处理配置命令
     */
    private void handleConfig(Uri uri) {
        try {
            applyConfigParams(uri);
        } catch (Exception e) {
            Log.e(TAG, "配置命令失败", e);
        }
    }

    /**
     * 应用配置参数 - 直接调用TaskService方法（照抄MainActivity逻辑）
     * DPI和旋转都直接设置，TaskService内部会处理等待和复活
     */
    private void applyConfigParams(Uri uri) {
        Log.d(TAG, "────────────────────────────");
        Log.d(TAG, "🔧 开始应用配置参数");
        Log.d(TAG, "URI: " + uri.toString());

        try {
            String dpiStr = uri.getQueryParameter("dpi");
            Log.d(TAG, "DPI参数: " + dpiStr);

            if (dpiStr != null) {
                int dpi = Integer.parseInt(dpiStr);
                Log.d(TAG, "→ 调用 taskService.setRearDpi(" + dpi + ")");

                // 直接调用TaskService.setRearDpi - 完全照抄MainActivity逻辑
                boolean success = taskService.setRearDpi(dpi);

                if (success) {
                    Log.d(TAG, "✅ DPI设置成功: " + dpi);
                } else {
                    Log.e(TAG, "❌ DPI设置失败（TaskService返回false）");
                }
            } else {
                Log.d(TAG, "→ 跳过DPI设置（无参数）");
            }

            String rotationStr = uri.getQueryParameter("rotation");
            Log.d(TAG, "旋转参数: " + rotationStr);

            if (rotationStr != null) {
                int rotation = Integer.parseInt(rotationStr);
                Log.d(TAG, "→ 调用 taskService.setDisplayRotation(1, " + rotation + ")");

                // 直接调用TaskService.setDisplayRotation - 完全照抄MainActivity逻辑
                // TaskService内部会自动处理：等待500ms + 检查应用 + 复活
                boolean success = taskService.setDisplayRotation(1, rotation);

                if (success) {
                    Log.d(TAG, "✅ 旋转设置成功: " + rotation);
                } else {
                    Log.e(TAG, "❌ 旋转设置失败（TaskService返回false）");
                }
            } else {
                Log.d(TAG, "→ 跳过旋转设置（无参数）");
            }

            Log.d(TAG, "🔧 配置参数应用完成");
            Log.d(TAG, "────────────────────────────");
        } catch (Exception e) {
            Log.e(TAG, "❌ 应用配置参数异常", e);
            e.printStackTrace();
        }
    }

    /**
     * 获取应用名称
     */
    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    /**
     * 显示Toast提示（主线程）
     */
    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        TaskServiceClient.unbind(null, taskServiceConnection, false);

        super.onDestroy();

        if (taskService != null) {
            try {
                TaskServiceClient.unbind(serviceArgs, taskServiceConnection, true);
            } catch (Exception e) {
                Log.e(TAG, "解绑TaskService失败", e);
            }
            taskService = null;
        }
    }
}
