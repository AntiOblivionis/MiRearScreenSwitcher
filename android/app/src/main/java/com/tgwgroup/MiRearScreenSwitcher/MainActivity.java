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

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

import rikka.shizuku.Shizuku;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "com.display.switcher/task";
    private static final String TAG = "MainActivity";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
    
    // 静态实例，供其他类访问
    private static MainActivity currentInstance;
    
    public static MainActivity getCurrentInstance() {
        return currentInstance;
    }
    
    private ITaskService taskService;
    private MethodChannel methodChannel;
    private final Shizuku.UserServiceArgs serviceArgs = 
        new Shizuku.UserServiceArgs(new ComponentName("com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName()))
            .daemon(false)
            .processNameSuffix("task_service")
            .debuggable(false)
            .version(1);
    
    // Shizuku监听器（关键！）
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = 
        () -> {
            bindTaskService();
        };
    
    private final Shizuku.OnBinderDeadListener binderDeadListener = 
        () -> {
            taskService = null;
            
            // 启动重连任务
            scheduleReconnectTaskService();
        };
    
    /**
     * TaskService重连任务
     */
    private final Runnable reconnectTaskServiceRunnable = new Runnable() {
        @Override
        public void run() {
            if (taskService == null) {
                bindTaskService();
                
                // 如果重连失败，2秒后再次尝试
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 30);
            } else {
            }
        }
    };
    
    /**
     * 安排TaskService重连
     */
    private void scheduleReconnectTaskService() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(reconnectTaskServiceRunnable, 30);
    };
    
    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = 
        (requestCode, grantResult) -> {
            boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                bindTaskService();
            }
            // 通知Flutter刷新状态
            if (methodChannel != null) {
                runOnUiThread(() -> {
                    methodChannel.invokeMethod("onShizukuPermissionChanged", granted);
                });
            }
        };
    
    private final ServiceConnection taskServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            taskService = ITaskService.Stub.asInterface(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            taskService = null;
        }
    };
    
    private void bindTaskService() {
        if (taskService != null) {
            return;
        }
        
        try {
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Shizuku not available");
                return;
            }
            
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No Shizuku permission");
                return;
            }
            Shizuku.bindUserService(serviceArgs, taskServiceConnection);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind TaskService", e);
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 保存实例
        currentInstance = this;
        
        // 添加Shizuku监听器（关键！使用Sticky版本）
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);
        
        // 自动检查并请求Shizuku权限
        checkAndRequestShizukuPermission();
        
        // 处理通知Intent
        handleIncomingIntent(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }
    
    /**
     * 处理来自Service的通知Intent
     */
    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        
        // 处理通知Intent
        if ("SHOW_NOTIFICATION_ON_REAR_SCREEN".equals(action)) {
            String packageName = intent.getStringExtra("packageName");
            String title = intent.getStringExtra("title");
            String text = intent.getStringExtra("text");
            long when = intent.getLongExtra("when", System.currentTimeMillis());
            
            Log.d(TAG, "Received notification intent for: " + packageName);
            startNotificationOnRearScreen(packageName, title, text, when);
        }
    }
    
    /**
     * 在背屏启动通知显示Activity
     */
    private void startNotificationOnRearScreen(String packageName, String title, String text, long when) {
        if (taskService == null) {
            Log.w(TAG, "TaskService not available for notification");
            return;
        }
        
        new Thread(() -> {
            try {
                // 步骤1: 禁用官方Launcher
                taskService.disableSubScreenLauncher();
                
                // 步骤2: 唤醒背屏
                taskService.executeShellCommand("input -d 1 keyevent KEYCODE_WAKEUP");
                Thread.sleep(50);
                
                // 步骤3: 在主屏启动Activity
                String componentName = getPackageName() + "/" + RearScreenNotificationActivity.class.getName();
                String mainCmd = String.format(
                    "am start -n %s --es packageName \"%s\" --es title \"%s\" --es text \"%s\" --el when %d",
                    componentName, packageName,
                    title != null ? title.replace("\"", "'") : "",
                    text != null ? text.replace("\"", "'") : "",
                    when
                );
                taskService.executeShellCommand(mainCmd);
                
                // 步骤4: 轮询获取taskId
                String notifTaskId = null;
                int attempts = 0;
                int maxAttempts = 20;
                
                while (notifTaskId == null && attempts < maxAttempts) {
                    Thread.sleep(30);
                    String result = taskService.executeShellCommandWithResult("am stack list | grep RearScreenNotificationActivity");
                    if (result != null && !result.trim().isEmpty()) {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("taskId=(\\d+)");
                        java.util.regex.Matcher matcher = pattern.matcher(result);
                        if (matcher.find()) {
                            notifTaskId = matcher.group(1);
                            Log.d(TAG, "Found notification taskId=" + notifTaskId);
                            break;
                        }
                    }
                    attempts++;
                }
                
                if (notifTaskId != null) {
                    // 步骤5: 移动到背屏
                    String moveCmd = "service call activity_task 50 i32 " + notifTaskId + " i32 1";
                    taskService.executeShellCommand(moveCmd);
                    Thread.sleep(40);
                    
                    // 步骤6: 检查是否锁屏，决定是否关闭主屏
                    android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        // 主屏休眠功能已移除
                        Log.d(TAG, "🔒 锁屏状态，主屏已关闭");
                    }
                    
                    Log.d(TAG, "✅ Notification animation started on rear screen");
                } else {
                    Log.e(TAG, "❌ Failed to find notification taskId");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to show notification on rear screen", e);
            }
        }).start();
    }
    
    /**
     * 执行Shell命令（供RearScreenChargingActivity调用）
     */
    public void executeShellCommand(String cmd) {
        if (taskService != null) {
            try {
                taskService.executeShellCommand(cmd);
            } catch (Exception e) {
                Log.e(TAG, "Failed to execute command: " + cmd, e);
            }
        }
    }
    
    private void checkAndRequestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(0);
                } else {
                    bindTaskService();
                }
            } else {
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check Shizuku permission", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 清除静态实例
        currentInstance = null;
        
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
    }
    
    @Override
    public void configureFlutterEngine(FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        
        methodChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL);
        methodChannel.setMethodCallHandler((call, result) -> {
                switch (call.method) {
                    case "checkShizuku": {
                        try {
                            boolean isRunning = Shizuku.pingBinder();
                            boolean hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                            result.success(isRunning && hasPermission);
                        } catch (Exception e) {
                            result.success(false);
                        }
                        break;
                    }
                    
                    case "requestShizukuPermission": {
                        try {
                            Shizuku.requestPermission(0);
                            result.success(null);
                        } catch (Exception e) {
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "getShizukuInfo": {
                        try {
                            boolean isRunning = Shizuku.pingBinder();
                            boolean hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                            int uid = Shizuku.getUid();
                            int version = Shizuku.getVersion();
                            String info = "Running: " + isRunning + "\n" +
                                         "Permission: " + hasPermission + "\n" +
                                         "UID: " + uid + "\n" +
                                         "Version: " + version;
                            result.success(info);
                        } catch (Exception e) {
                            result.success("Error: " + e.getMessage());
                        }
                        break;
                    }
                    
                    case "getCurrentApp": {
                        if (taskService != null) {
                            try {
                                String currentApp = taskService.getCurrentForegroundApp();
                                result.success(currentApp);
                            } catch (Exception e) {
                                Log.e(TAG, "TaskService error: " + e.getMessage(), e);
                                result.success(null);
                            }
                        } else {
                            result.success(null);
                        }
                        break;
                    }
                    
                    case "requestNotificationPermission": {
                        // Android 13+ 需要请求通知权限
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                                != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this, 
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                                    NOTIFICATION_PERMISSION_REQUEST_CODE);
                                result.success(null);
                            } else {
                                result.success(null);
                            }
                        } else {
                            // Android 12及以下不需要请求通知权限
                            result.success(null);
                        }
                        break;
                    }
                    
                    case "getCurrentRearDpi": {
                        if (taskService != null) {
                            try {
                                int dpi = taskService.getCurrentRearDpi();
                                result.success(dpi);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to get rear DPI", e);
                                result.error("ERROR", e.getMessage(), null);
                            }
                        } else {
                            result.error("ERROR", "TaskService not available", null);
                        }
                        break;
                    }
                    
                    case "setRearDpi": {
                        if (taskService != null) {
                            try {
                                int dpi = (int) call.argument("dpi");
                                boolean success = taskService.setRearDpi(dpi);
                                result.success(success);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to set rear DPI", e);
                                result.error("ERROR", e.getMessage(), null);
                            }
                        } else {
                            result.error("ERROR", "TaskService not available", null);
                        }
                        break;
                    }
                    
                    case "resetRearDpi": {
                        if (taskService != null) {
                            try {
                                boolean success = taskService.resetRearDpi();
                                result.success(success);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to reset rear DPI", e);
                                result.error("ERROR", e.getMessage(), null);
                            }
                        } else {
                            result.error("ERROR", "TaskService not available", null);
                        }
                        break;
                    }
                    
                    case "openCoolApkProfile": {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("com.coolapk.market", "com.coolapk.market.view.AppLinkActivity");
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("coolmarket://u/8158212"));
                            startActivity(intent);
                            result.success(null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open CoolApk profile", e);
                            result.error("ERROR", "请先安装酷安应用", null);
                        }
                        break;
                    }
                    
                    case "openCoolApkProfileXmz": {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("com.coolapk.market", "com.coolapk.market.view.AppLinkActivity");
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("coolmarket://u/4279097"));
                            startActivity(intent);
                            result.success(null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open CoolApk profile", e);
                            result.error("ERROR", "请先安装酷安应用", null);
                        }
                        break;
                    }
                    
                    case "openTutorial": {
                        // 打开腾讯文档使用教程
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("https://docs.qq.com/doc/DVWxpT3hQdHNPR3Zy?dver="));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            result.success(null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open tutorial", e);
                            result.error("ERROR", "打开失败: " + e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "openDonationPage": {
                        // 打开打赏页面
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("https://tgwgroup.ltd/2025/10/19/%e5%85%b3%e4%ba%8e%e6%89%93%e8%b5%8f/"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            result.success(null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open donation page", e);
                            result.error("ERROR", "打开失败: " + e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "openQQGroup": {
                        // 打开MRSS交流群页面
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("https://tgwgroup.ltd/2025/10/21/%e5%85%b3%e4%ba%8emrss%e4%ba%a4%e6%b5%81%e7%be%a4/"));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            result.success(null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open QQ group page", e);
                            result.error("ERROR", "打开失败: " + e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "ensureTaskServiceConnected": {
                        // 确保TaskService连接正常
                        try {
                            if (taskService == null) {
                                // 尝试重新绑定
                                bindTaskService();
                            }
                            result.success(taskService != null);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to ensure TaskService connection", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setDisplayRotation": {
                        if (taskService != null) {
                            try {
                                int displayId = (int) call.argument("displayId");
                                int rotation = (int) call.argument("rotation");
                                boolean success = taskService.setDisplayRotation(displayId, rotation);
                                result.success(success);
                            } catch (Exception e) {
                                result.error("ERROR", e.getMessage(), null);
                            }
                        } else {
                            result.error("ERROR", "TaskService not available", null);
                        }
                        break;
                    }
                    
                    case "getDisplayRotation": {
                        if (taskService != null) {
                            try {
                                int displayId = (int) call.argument("displayId");
                                int rotation = taskService.getDisplayRotation(displayId);
                                result.success(rotation);
                            } catch (Exception e) {
                                result.error("ERROR", e.getMessage(), null);
                            }
                        } else {
                            result.error("ERROR", "TaskService not available", null);
                        }
                        break;
                    }
                    
                    case "returnRearAppAndRestart": {
                        // 重启前先拉回背屏应用
                        if (taskService != null) {
                            try {
                                // 获取最后移动的任务信息
                                String lastTask = SwitchToRearTileService.getLastMovedTask();
                                
                                if (lastTask != null && lastTask.contains(":")) {
                                    String[] parts = lastTask.split(":");
                                    int taskId = Integer.parseInt(parts[1]);
                                    
                                    // 检查任务是否还在背屏
                                    boolean onRear = taskService.isTaskOnDisplay(taskId, 1);
                                    
                                    if (onRear) {
                                        // 拉回主屏
                                        taskService.moveTaskToDisplay(taskId, 0);
                                        
                                        // 恢复官方Launcher
                                        taskService.enableSubScreenLauncher();
                                        
                                        result.success(true);
                                    } else {
                                        // 没有应用在背屏
                                        result.success(false);
                                    }
                                } else {
                                    // 没有记录
                                    result.success(false);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to return rear app", e);
                                result.error("ERROR", e.getMessage(), null);
                            }
                        } else {
                            result.error("ERROR", "TaskService not available", null);
                        }
                        break;
                    }
                    
                    case "setProximitySensorEnabled": {
                        // V2.2: 设置接近传感器开关
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        // 通知RearScreenKeeperService更新状态
                        Intent intent = new Intent(this, RearScreenKeeperService.class);
                        intent.setAction("ACTION_SET_PROXIMITY_ENABLED");
                        intent.putExtra("enabled", enabled);
                        startService(intent);
                        
                        result.success(true);
                        break;
                    }
                    
                    case "setKeepScreenOnEnabled": {
                        // V2.5: 设置背屏常亮开关
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        // 通知RearScreenKeeperService更新状态
                        Intent intent = new Intent(this, RearScreenKeeperService.class);
                        intent.setAction("ACTION_SET_KEEP_SCREEN_ON_ENABLED");
                        intent.putExtra("enabled", enabled);
                        startService(intent);
                        
                        result.success(true);
                        break;
                    }
                    
                    case "setAlwaysWakeUpEnabled": {
                        // V3.5: 设置未投放应用时常亮开关
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                        prefs.edit().putBoolean("always_wakeup_enabled", enabled).apply();
                        
                        Intent intent = new Intent(this, AlwaysWakeUpService.class);
                        if (enabled) {
                            startService(intent);
                            Log.d(TAG, "AlwaysWakeUpService started");
                        } else {
                            stopService(intent);
                            Log.d(TAG, "AlwaysWakeUpService stopped");
                        }
                        
                        result.success(true);
                        break;
                    }
                    
                    case "setSmartMediaEnabled": {
                        // V3.6: Smart Media Rear Controller toggle
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                        prefs.edit().putBoolean("smart_media_enabled", enabled).apply();
                        
                        // Notify NotificationService to reload settings
                        sendBroadcast(new Intent("com.tgwgroup.MiRearScreenSwitcher.RELOAD_NOTIFICATION_SETTINGS"));
                        
                        Log.d(TAG, "Smart Media Controller set to: " + enabled);
                        result.success(true);
                        break;
                    }
                    
                    case "setChargingAlwaysOnEnabled": {
                        // V3.5: 设置充电动画常亮开关
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                        prefs.edit().putBoolean("charging_always_on_enabled", enabled).apply();
                        
                        // 通知ChargingService重新加载设置
                        sendBroadcast(new Intent("com.tgwgroup.MiRearScreenSwitcher.RELOAD_CHARGING_SETTINGS"));
                        
                        Log.d(TAG, "Charging always on set to: " + enabled);
                        result.success(true);
                        break;
                    }
                    
                    case "toggleChargingService": {
                        // V2.3: 切换充电动画服务
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        Intent intent = new Intent(this, ChargingService.class);
                        if (enabled) {
                            startService(intent);
                            Log.d(TAG, "ChargingService started");
                        } else {
                            stopService(intent);
                            Log.d(TAG, "ChargingService stopped");
                        }
                        
                        result.success(true);
                        break;
                    }
                    
                    case "startNotificationService": {
                        // V2.4: 启动通知服务
                        Intent intent = new Intent(this, NotificationService.class);
                        startService(intent);
                        Log.d(TAG, "NotificationService started");
                        result.success(true);
                        break;
                    }
                    
                    case "toggleNotificationService": {
                        // V2.4: 切换通知服务
                        boolean enabled = (boolean) call.argument("enabled");
                        
                        SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                        prefs.edit()
                            .putBoolean("notification_service_enabled", enabled)
                            .apply();
                        
                        if (enabled) {
                            // 开启时启动服务
                            Intent intent = new Intent(this, NotificationService.class);
                            startService(intent);
                            Log.d(TAG, "NotificationService started");
                        } else {
                            // 关闭时停止服务
                            Intent intent = new Intent(this, NotificationService.class);
                            stopService(intent);
                            Log.d(TAG, "NotificationService stopped");
                        }
                        
                        Log.d(TAG, "Notification service enabled: " + enabled);
                        result.success(true);
                        break;
                    }
                    
                    case "checkNotificationListenerPermission": {
                        // V2.4: 检查通知监听权限
                        boolean hasPermission = isNotificationListenerEnabled();
                        result.success(hasPermission);
                        break;
                    }
                    
                    case "openNotificationListenerSettings": {
                        // V2.4: 打开通知监听设置
                        try {
                            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open notification settings", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "checkQueryAllPackagesPermission": {
                        // V2.4: 检查QUERY_ALL_PACKAGES权限
                        boolean hasPermission = checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED;
                        Log.d(TAG, "🔍 QUERY_ALL_PACKAGES permission check: " + hasPermission);
                        result.success(hasPermission);
                        break;
                    }
                    
                    case "requestQueryAllPackagesPermission": {
                        // V2.4: 请求QUERY_ALL_PACKAGES权限（跳转到应用详情页）
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open app settings", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "getInstalledApps": {
                        // V2.4: 获取已安装应用列表（异步）
                        new Thread(() -> {
                            try {
                                // 先检查权限
                                boolean hasPermission = checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED;
                                if (!hasPermission) {
                                    Log.w(TAG, "⚠️ 没有QUERY_ALL_PACKAGES权限，应用列表可能不完整");
                                }
                                
                                java.util.List<java.util.Map<String, Object>> apps = getInstalledApps();
                                runOnUiThread(() -> result.success(apps));
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to get installed apps", e);
                                runOnUiThread(() -> result.error("ERROR", e.getMessage(), null));
                            }
                        }).start();
                        break;
                    }
                    
                    case "getSelectedNotificationApps": {
                        // V2.4: 获取已选择的通知应用
                        try {
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            java.util.Set<String> selectedApps = prefs.getStringSet("notification_selected_apps", new java.util.HashSet<>());
                            result.success(new java.util.ArrayList<>(selectedApps));
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to get selected apps", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setSelectedNotificationApps": {
                        // V2.4: 保存选择的通知应用
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.List<String> selectedApps = (java.util.List<String>) call.arguments;
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putStringSet("notification_selected_apps", new java.util.HashSet<>(selectedApps))
                                .apply();
                            Log.d(TAG, "Saved " + selectedApps.size() + " selected apps");
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to save selected apps", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setNotificationPrivacyHideTitle": {
                        // V3.2: 设置隐藏通知标题
                        try {
                            boolean enabled = (boolean) call.argument("enabled");
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putBoolean("notification_privacy_hide_title", enabled)
                                .apply();
                            
                            // 通知NotificationService重新加载设置
                            sendBroadcast(new Intent("com.tgwgroup.MiRearScreenSwitcher.RELOAD_NOTIFICATION_SETTINGS"));
                            
                            Log.d(TAG, "Privacy hide title set to: " + enabled);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set privacy hide title", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setNotificationPrivacyHideContent": {
                        // V3.2: 设置隐藏通知内容
                        try {
                            boolean enabled = (boolean) call.argument("enabled");
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putBoolean("notification_privacy_hide_content", enabled)
                                .apply();
                            
                            // 通知NotificationService重新加载设置
                            sendBroadcast(new Intent("com.tgwgroup.MiRearScreenSwitcher.RELOAD_NOTIFICATION_SETTINGS"));
                            
                            Log.d(TAG, "Privacy hide content set to: " + enabled);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set privacy hide content", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setFollowDndMode": {
                        // V3.0: 设置跟随系统勿扰模式
                        try {
                            boolean enabled = (boolean) call.argument("enabled");
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putBoolean("notification_follow_dnd_mode", enabled)
                                .apply();
                            Log.d(TAG, "Follow DND mode set to: " + enabled);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set follow DND mode", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setOnlyWhenLocked": {
                        // V3.0: 设置仅倒扣手机时通知
                        try {
                            boolean enabled = (boolean) call.argument("enabled");
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putBoolean("notification_only_when_locked", enabled)
                                .apply();
                            Log.d(TAG, "Only when upside down mode set to: " + enabled);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set only when upside down mode", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setNotificationDarkMode": {
                        // V3.1: 设置通知暗夜模式
                        try {
                            boolean enabled = (boolean) call.argument("enabled");
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putBoolean("notification_dark_mode", enabled)
                                .apply();
                            Log.d(TAG, "Notification dark mode set to: " + enabled);
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set notification dark mode", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    case "setNotificationDuration": {
                        // V3.4: 设置通知自动销毁时间
                        try {
                            int duration = (int) call.argument("duration");
                            SharedPreferences prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
                            prefs.edit()
                                .putInt("notification_duration", duration)
                                .apply();
                            Log.d(TAG, "Notification duration set to: " + duration + " seconds");
                            result.success(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to set notification duration", e);
                            result.error("ERROR", e.getMessage(), null);
                        }
                        break;
                    }
                    
                    default:
                        result.notImplemented();
                }
            });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
            }
        }
    }
    
    /**
     * V2.4: 检查通知监听服务是否已启用
     */
    private boolean isNotificationListenerEnabled() {
        String packageName = getPackageName();
        String flat = android.provider.Settings.Secure.getString(
            getContentResolver(),
            "enabled_notification_listeners"
        );
        
        if (flat == null || flat.isEmpty()) {
            return false;
        }
        
        String[] names = flat.split(":");
        for (String name : names) {
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
            if (cn != null && packageName.equals(cn.getPackageName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * V2.4: 获取已安装应用列表
     */
    private java.util.List<java.util.Map<String, Object>> getInstalledApps() {
        java.util.List<java.util.Map<String, Object>> apps = new java.util.ArrayList<>();
        
        try {
            PackageManager pm = getPackageManager();
            java.util.List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            Log.d(TAG, "Total packages found: " + packages.size());
            
            // 使用白名单策略（用户应用 + 重要系统应用）
            java.util.Set<String> importantSystemApps = new java.util.HashSet<>();
            importantSystemApps.add("com.tencent.mm"); // 微信
            importantSystemApps.add("com.tencent.mobileqq"); // QQ
            importantSystemApps.add("com.coolapk.market"); // 酷安
            importantSystemApps.add("com.sina.weibo"); // 微博
            importantSystemApps.add("com.taobao.taobao"); // 淘宝
            importantSystemApps.add("com.eg.android.AlipayGphone"); // 支付宝
            importantSystemApps.add("com.netease.cloudmusic"); // 网易云
            importantSystemApps.add("com.ss.android.ugc.aweme"); // 抖音
            importantSystemApps.add("com.bilibili.app.in"); // 哔哩哔哩
            importantSystemApps.add("com.android.mms"); // 短信
            importantSystemApps.add("com.android.contacts"); // 联系人
            
            for (android.content.pm.ApplicationInfo appInfo : packages) {
                // 跳过自己
                if (appInfo.packageName.equals(getPackageName())) {
                    continue;
                }
                
                boolean isSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isUserApp = !isSystemApp;
                boolean isImportantSystemApp = importantSystemApps.contains(appInfo.packageName);
                
                // 只包含用户应用或重要系统应用
                if (!isUserApp && !isImportantSystemApp) {
                    continue;
                }
                
                java.util.Map<String, Object> app = new java.util.HashMap<>();
                app.put("appName", pm.getApplicationLabel(appInfo).toString());
                app.put("packageName", appInfo.packageName);
                app.put("isSystemApp", isSystemApp);  // V3.3: 添加系统应用标志
                
                // 获取应用图标（全分辨率，不压缩不受损）
                try {
                    Drawable icon = pm.getApplicationIcon(appInfo);
                    // 使用原始图标尺寸，不限制大小
                    int iconSize = Math.max(icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                    if (iconSize <= 0) iconSize = 192; // 如果无法获取，使用默认高分辨率
                    
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888
                    );
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    icon.setBounds(0, 0, iconSize, iconSize);
                    icon.draw(canvas);
                    
                    java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream); // 100%质量，无损压缩
                    app.put("icon", stream.toByteArray());
                    
                    bitmap.recycle();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get icon for " + appInfo.packageName);
                }
                
                apps.add(app);
            }
            
            // 按应用名排序
            apps.sort((a, b) -> {
                String nameA = (String) a.get("appName");
                String nameB = (String) b.get("appName");
                return nameA.compareToIgnoreCase(nameB);
            });
            
            Log.d(TAG, "Found " + apps.size() + " user apps");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get installed apps", e);
        }
        
        return apps;
    }
}
