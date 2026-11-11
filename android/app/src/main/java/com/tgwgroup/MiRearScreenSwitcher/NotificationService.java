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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import rikka.shizuku.Shizuku;

/**
 * 通知监听服务
 * 监听系统通知，将选中应用的通知显示到背屏
 */
public class NotificationService extends NotificationListenerService {
    private volatile boolean destroyed;

    private static final String TAG = "NotificationService";
    private static final int NOTIFICATION_ID = 1001; // 与其他Service共用ID
    
    private Set<String> selectedApps = new HashSet<>();
    private boolean privacyHideTitle = false; // V3.2: 隐私模式 - 隐藏标题
    private boolean privacyHideContent = false; // V3.2: 隐私模式 - 隐藏内容
    private boolean followDndMode = true; // 跟随系统勿扰模式（默认开启）
    private boolean onlyWhenLocked = false; // 仅倒扣手机时通知（默认关闭）
    private boolean notificationDarkMode = false; // 通知暗夜模式（默认关闭）
    private boolean serviceEnabled = false; // 服务是否启用
    private volatile ITaskService taskService; // 自己的TaskService实例
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    
    // 主屏接近传感器相关
    private SensorManager sensorManager;
    private Sensor mainProximitySensor; // 主屏接近传感器
    private boolean isMainScreenCovered = false; // 主屏是否被遮盖
    
    // 静态实例，供外部访问
    private static NotificationService instance;
    
    public static ITaskService getTaskService() {
        return instance != null ? instance.taskService : null;
    }
    
    // 广播接收器：监听设置重新加载
    private BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.tgwgroup.MiRearScreenSwitcher.RELOAD_NOTIFICATION_SETTINGS".equals(intent.getAction())) {
                Log.d(TAG, "🔄 收到重新加载设置的广播");
                loadNotificationServiceSettings(); // 重新加载开关状态
                loadSettings(); // 重新加载其他设置
            }
        }
    };
    
    // Shizuku服务配置
    private final Shizuku.UserServiceArgs serviceArgs = 
        new Shizuku.UserServiceArgs(new ComponentName("com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName()))
            .daemon(false)
            .processNameSuffix("notification_task_service")
            .debuggable(false)
            .version(1);
    
    // TaskService连接
    private final ServiceConnection taskServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "✓ TaskService connected");
            taskService = ITaskService.Stub.asInterface(binder);
            
            // 初始化显示屏信息缓存
            try {
                DisplayInfoCache.getInstance().initializeAsync(taskService);
            } catch (Exception e) {
                Log.w(TAG, "初始化显示屏缓存失败: " + e.getMessage());
            }
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "✗ TaskService disconnected");
            taskService = null;
            // 自动重连
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (taskService == null) {
                    bindTaskService();
                }
            }, 1000);
        }
    };
    
    // Shizuku监听器
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = 
        () -> {
            Log.d(TAG, "Shizuku binder received");
            bindTaskService();
        };
    
    private final Shizuku.OnBinderDeadListener binderDeadListener = 
        () -> {
            Log.d(TAG, "Shizuku binder dead");
            taskService = null;
            // 尝试重连
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                bindTaskService();
            }, 1000);
        };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🟢 NotificationService created");
        
        // 保存实例
        instance = this;
        
        // 初始化SharedPreferences
        prefs = getSharedPreferences("mrss_settings", Context.MODE_PRIVATE);
        
        // 注册广播接收器（监听设置变化）
        IntentFilter filter = new IntentFilter("com.tgwgroup.MiRearScreenSwitcher.RELOAD_NOTIFICATION_SETTINGS");
        androidx.core.content.ContextCompat.registerReceiver(this, settingsReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "✓ 广播接收器已注册");
        
        // 添加Shizuku监听器
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        
        // 绑定TaskService
        bindTaskService();
        
        // V2.4: 加载通知服务开关状态
        Log.d(TAG, "🔧 开始加载通知服务开关状态...");
        loadNotificationServiceSettings();
        Log.d(TAG, "🔧 通知服务开关状态加载完成: " + serviceEnabled);
        
        // 初始化主屏接近传感器
        initMainProximitySensor();
        
        // 启动为前台服务，防止被系统杀死
        startForeground(NOTIFICATION_ID, RearScreenKeeperService.createServiceNotification(this));
        Log.d(TAG, "✓ 前台服务已启动");
        
        loadSettings();
    }
    
    private void bindTaskService() {
        if (!destroyed) TaskServiceClient.bind(null, taskServiceConnection);
    }
    
    /**
     * 加载通知服务开关状态
     */
    private void loadNotificationServiceSettings() {
        try {
            Log.d(TAG, "🔧 开始读取FlutterSharedPreferences...");
            // 从FlutterSharedPreferences读取开关状态
            SharedPreferences flutterPrefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE);
            Log.d(TAG, "🔧 FlutterSharedPreferences读取成功");
            
            serviceEnabled = flutterPrefs.getBoolean("flutter.notification_service_enabled", false);
            Log.d(TAG, "🔧 通知服务开关状态已恢复: " + serviceEnabled);
            
            // NotificationListenerService由系统管理，不能手动停止
            // 如果开关关闭，服务仍会运行但不处理通知
            if (!serviceEnabled) {
                Log.d(TAG, "⏸️ 通知服务已禁用，将忽略所有通知");
            } else {
                Log.d(TAG, "✅ 通知服务已启用，将处理通知");
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ 加载通知服务设置失败", e);
            serviceEnabled = false; // 默认关闭
        }
    }
    
    private void loadSettings() {
        try {
            selectedApps = prefs.getStringSet("notification_selected_apps", new HashSet<>());
            privacyHideTitle = prefs.getBoolean("notification_privacy_hide_title", false);
            privacyHideContent = prefs.getBoolean("notification_privacy_hide_content", false);
            followDndMode = prefs.getBoolean("notification_follow_dnd_mode", true);
            onlyWhenLocked = prefs.getBoolean("notification_only_when_locked", false);
            notificationDarkMode = prefs.getBoolean("notification_dark_mode", false);
            // 注意：不在这里重新设置 serviceEnabled，保持 loadNotificationServiceSettings() 的值
            
            Log.d(TAG, "⚙️ 已加载设置");
            Log.d(TAG, "   - 启用状态: " + serviceEnabled + " (由loadNotificationServiceSettings设置)");
            Log.d(TAG, "   - 选中应用: " + selectedApps.size() + " 个");
            Log.d(TAG, "   - 隐藏标题: " + privacyHideTitle);
            Log.d(TAG, "   - 隐藏内容: " + privacyHideContent);
            
            if (!selectedApps.isEmpty()) {
                Log.d(TAG, "📋 选中应用列表: " + selectedApps.toString());
            } else {
                Log.w(TAG, "⚠️ 没有选中任何应用");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载设置失败", e);
            selectedApps = new HashSet<>();
            // 不在这里重置 serviceEnabled
        }
    }
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        
        // V2.4: 每次收到通知时重新加载开关状态
        loadNotificationServiceSettings();
        
        // V2.4: 如果通知服务开关关闭，不处理通知
        if (!serviceEnabled) {
            Log.d(TAG, "⏸️ 通知服务已禁用，忽略通知");
            return;
        }
        
        try {
            String packageName = sbn.getPackageName();
            Notification notification = sbn.getNotification();
            
            Log.d(TAG, "📢 收到通知: " + packageName);
            
            // 忽略常驻通知
            if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
                Log.d(TAG, "⏭️ 忽略常驻通知: " + packageName);
                return;
            }
            
            // 忽略自己的通知
            if (packageName.equals(getPackageName())) {
                Log.d(TAG, "⏭️ 忽略自己的通知");
                return;
            }
            
            // 每次都重新加载设置（确保实时生效）
            loadSettings();
            
            // 检查服务是否启用
            if (!serviceEnabled) {
                Log.d(TAG, "⏭️ 通知服务未启用，跳过");
                return;
            }
            
            // 检查系统勿扰模式
            if (followDndMode) {
                try {
                    android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null && nm.getCurrentInterruptionFilter() != android.app.NotificationManager.INTERRUPTION_FILTER_ALL) {
                        Log.d(TAG, "⏭️ 系统勿扰模式已开启，跳过通知动画");
                        return;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "检查勿扰模式失败: " + e.getMessage());
                }
            }
            
            // 检查是否仅倒扣手机时通知（检测主屏接近传感器）
            if (onlyWhenLocked) {
                if (!isMainScreenCovered) {
                    Log.d(TAG, "⏭️ 主屏未被遮盖，仅倒扣手机通知模式已开启，跳过");
                    return;
                }
            }
            
            Log.d(TAG, "📋 当前选中应用数量: " + selectedApps.size());
            Log.d(TAG, "📋 选中应用列表: " + selectedApps.toString());
            
            // 检查是否在选中列表中
            if (!selectedApps.contains(packageName)) {
                Log.d(TAG, "⏭️ 应用不在选中列表中: " + packageName);
                return;
            }
            
            Log.d(TAG, "✓ 应用在选中列表中: " + packageName);
            
            // 提取通知内容
            CharSequence rawTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence rawText = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            String title = rawTitle == null ? "" : rawTitle.toString();
            String text = rawText == null ? "" : rawText.toString();
            long when = notification.when;
            
            
            // V3.2: 隐私模式处理（区分标题和内容）
            if (privacyHideTitle) {
                Log.d(TAG, "🔒 隐藏通知标题");
                title = getString(R.string.privacy_mode_enabled);
            }
            if (privacyHideContent) {
                Log.d(TAG, "🔒 隐藏通知内容");
                text = getString(R.string.new_message_placeholder);
            }
            
            Log.d(TAG, "🚀 开始显示背屏通知: " + packageName);
            
            // 通知动画管理器：开始通知动画（返回被打断的旧动画）
            RearAnimationManager.AnimationType oldAnim = RearAnimationManager.startAnimation(RearAnimationManager.AnimationType.NOTIFICATION);
            final long session = RearAnimationManager.getGeneration();
            final String displayTitle = title;
            final String displayText = text;
            if (oldAnim == RearAnimationManager.AnimationType.CHARGING) {
                RearAnimationManager.markInterruptedChargingAsAlwaysOn(prefs.getBoolean("charging_always_on_enabled", false));
            }
            if (oldAnim != RearAnimationManager.AnimationType.NONE) RearAnimationManager.sendInterruptBroadcast(this, oldAnim);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!destroyed && RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.NOTIFICATION, session)) {
                    showNotificationOnRearScreen(packageName, displayTitle, displayText, when);
                }
            }, oldAnim == RearAnimationManager.AnimationType.NONE ? 0 : 150);

        } catch (Exception e) {
            Log.e(TAG, "❌ 处理通知时出错", e);
        }
    }
    
    private void showNotificationOnRearScreen(String packageName, String title, String text, long when) {
        final long session = RearAnimationManager.getGeneration();
        RearAnimationManager.LAUNCHES.execute(() -> {
            if (destroyed || !RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.NOTIFICATION, session)) return;
            showNotificationOnRearScreenDirect(packageName, title, text, when, session);
        });
    }
    
    private void showNotificationOnRearScreenDirect(String packageName, String title, String text, long when, long session) {
        boolean launched = false;
        try {
            ITaskService service = taskService;
            if (service == null) return;
            acquireWakeLock(8000);
            service.disableSubScreenLauncher();
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("packageName", packageName);
            extras.putString("title", title);
            extras.putString("text", text);
            extras.putLong("when", when);
            extras.putLong("animationSession", session);
            extras.putBoolean("darkMode", prefs.getBoolean("notification_dark_mode", false));
            if (destroyed || !RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.NOTIFICATION, session)) return;
            launched = service.launchRearActivity(getPackageName() + "/" + RearScreenNotificationActivity.class.getName(), extras);
        } catch (Exception e) { Log.w(TAG, "Rear notification launch failed", e); }
        finally {
            releaseWakeLock();
            if (!launched && RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.NOTIFICATION, session)) {
                RearScreenKeeperService.resumeMonitoring();
            }
        }
    }

    private void acquireWakeLock(long timeoutMs) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                if (wakeLock == null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MRSS:NotificationWake");
                    wakeLock.setReferenceCounted(false);
                }
                if (!wakeLock.isHeld()) {
                    wakeLock.acquire(timeoutMs);
                    Log.d(TAG, "🔒 PARTIAL_WAKE_LOCK acquired for " + timeoutMs + "ms");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to acquire wakelock: " + t.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "🔓 PARTIAL_WAKE_LOCK released");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release wakelock: " + t.getMessage());
        }
    }
    
    /**
     * 初始化主屏接近传感器（用于检测倒扣手机）
     */
    private void initMainProximitySensor() {
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            
            if (sensorManager != null) {
                // 获取所有传感器列表
                java.util.List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
                
                // 查找主屏接近传感器（不包含"Back"的接近传感器）
                // 优先选择 Wakeup 版本，如果没有则选择 Non-wakeup 版本
                Sensor wakeupSensor = null;
                Sensor nonWakeupSensor = null;
                
                for (Sensor sensor : allSensors) {
                    String name = sensor.getName();
                    if (name.contains("Proximity") && !name.contains("Back")) {
                        // 主屏接近传感器（不包含Back）
                        if (name.contains("Wakeup")) {
                            wakeupSensor = sensor;
                        } else {
                            nonWakeupSensor = sensor;
                        }
                    }
                }
                
                // 优先使用 Wakeup 版本
                if (wakeupSensor != null) {
                    mainProximitySensor = wakeupSensor;
                } else if (nonWakeupSensor != null) {
                    mainProximitySensor = nonWakeupSensor;
                    Log.w(TAG, "→ Using NON-WAKEUP main proximity sensor");
                }
                
                // 如果找不到主屏传感器，回退到默认传感器
                if (mainProximitySensor == null) {
                    Log.w(TAG, "⚠ Main proximity sensor not found, using default");
                    mainProximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                }
                
                if (mainProximitySensor != null) {
                    // 注册传感器监听器
                    boolean registered = sensorManager.registerListener(
                            proximitySensorListener,
                            mainProximitySensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                    
                    if (registered) {
                        Log.d(TAG, "✅ 主屏接近传感器已注册");
                    } else {
                        Log.w(TAG, "⚠ Failed to register main proximity sensor");
                    }
                } else {
                    Log.w(TAG, "⚠ No main proximity sensor available");
                }
            } else {
                Log.w(TAG, "⚠ SensorManager not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Error initializing main proximity sensor", e);
        }
    }
    
    /**
     * 注销主屏接近传感器
     */
    private void unregisterMainProximitySensor() {
        try {
            if (sensorManager != null && proximitySensorListener != null) {
                sensorManager.unregisterListener(proximitySensorListener);
                Log.d(TAG, "✓ 主屏接近传感器已注销");
            }
        } catch (Exception e) {
            Log.e(TAG, "✗ Error unregistering main proximity sensor", e);
        }
    }
    
    /**
     * 主屏接近传感器监听器
     */
    private final SensorEventListener proximitySensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == mainProximitySensor) {
                float distance = event.values[0];
                float maxRange = mainProximitySensor.getMaximumRange();
                
                // 当距离接近0（被覆盖）时触发
                // 小于最大距离的20%视为覆盖
                boolean isCovered = (distance < maxRange * 0.2f);
                
                isMainScreenCovered = isCovered;
                
                if (isCovered) {
                    Log.d(TAG, "📱 主屏接近传感器：被遮盖 (距离: " + distance + " cm)");
                } else {
                    Log.d(TAG, "📱 主屏接近传感器：未遮盖 (距离: " + distance + " cm)");
                }
            }
        }
        
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // 不需要处理
        }
    };
    
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "🔗 NotificationListener connected");
        loadSettings();
        Log.d(TAG, "✓ 通知监听器已就绪");
    }
    
    @Override
    public void onDestroy() {
        destroyed = true;
        TaskServiceClient.unbind(null, taskServiceConnection, false);

        super.onDestroy();
        Log.d(TAG, "🔴 NotificationService destroyed");
        
        // 注销广播接收器
        try {
            unregisterReceiver(settingsReceiver);
            Log.d(TAG, "✓ 广播接收器已注销");
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister receiver", e);
        }
        
        // 移除Shizuku监听器
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
        } catch (Exception e) {
            Log.w(TAG, "Failed to remove Shizuku listeners", e);
        }
        
        // 解绑TaskService
        try {
            if (taskService != null) {
                TaskServiceClient.unbind(serviceArgs, taskServiceConnection, true);
                taskService = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unbind TaskService", e);
        }
        
        // 注销主屏接近传感器
        unregisterMainProximitySensor();
        
        // 清除实例
        instance = null;
        
        stopForeground(true);
    }
}

