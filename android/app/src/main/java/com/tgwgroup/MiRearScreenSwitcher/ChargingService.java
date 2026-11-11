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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.IBinder;
import android.os.Handler;
import android.util.Log;

import rikka.shizuku.Shizuku;

/**
 * 充电状态监听服务
 * 监听电源连接事件，插电后在背屏显示充电电量动画
 */
public class ChargingService extends Service {
    private volatile boolean destroyed;
    private volatile boolean chargingConnected;

    private static final String TAG = "ChargingService";
    private SharedPreferences prefs;
    private volatile ITaskService taskService;
    private PowerManager.WakeLock wakeLock;
    
    // 静态实例，供RearScreenChargingActivity访问
    private static volatile ChargingService instance;
    
    // 防止重复触发动画（冷却时间）
    private long lastChargingAnimationTime = 0;
    private static final long CHARGING_ANIMATION_COOLDOWN_MS = 6000; // 6秒冷却时间
    
    // V3.5: 充电动画常亮模式
    private boolean chargingAlwaysOnEnabled = false;
    private Handler wakeupHandler;
    private Runnable wakeupRunnable;
    private boolean isWakeupRunning = false;
    
    public static ITaskService getTaskService() {
        return instance != null ? instance.taskService : null;
    }

    static boolean mayRecoverCharging(long session) {
        ChargingService owner = instance;
        return owner != null && !owner.destroyed && owner.chargingConnected
                && owner.prefs != null && owner.prefs.getBoolean("charging_always_on_enabled", false)
                && RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, session);
    }
    
    private final Shizuku.UserServiceArgs serviceArgs = 
        new Shizuku.UserServiceArgs(new ComponentName("com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName()))
            .daemon(false)
            .processNameSuffix("charging_task_service")
            .debuggable(false)
            .version(1);
    
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
    
    // V3.5: 设置变化广播接收器
    private BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "收到设置变化广播");
            chargingAlwaysOnEnabled = prefs.getBoolean("charging_always_on_enabled", false);
            Log.d(TAG, "充电动画常亮: " + chargingAlwaysOnEnabled);
            if (!chargingAlwaysOnEnabled) {
                stopWakeupLoop();
                RearAnimationManager.clearChargingAlwaysOnFlag();
                finishChargingAnimation();
            }
        }
    };
    
    // V3.5: 恢复充电动画广播接收器
    private BroadcastReceiver resumeChargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.tgwgroup.MiRearScreenSwitcher.RESUME_CHARGING_ANIMATION".equals(intent.getAction())) {
                Log.d(TAG, "🔋 收到恢复充电动画广播，准备恢复");
                
                // 获取当前电量
                int batteryLevel = getBatteryLevel(context);
                final long finishedSession = RearAnimationManager.getGeneration();
                
                // 延迟后重新启动充电动画
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (destroyed || !chargingConnected || !chargingAlwaysOnEnabled || !prefs.getBoolean("charging_animation_enabled", true)
                                || RearAnimationManager.getGeneration() != finishedSession || RearAnimationManager.isAnimationPlaying()) return;
                        // 通知动画管理器：开始充电动画
                        RearAnimationManager.startAnimation(RearAnimationManager.AnimationType.CHARGING);
                        
                        // 启动充电动画
                        showChargingOnRearScreen(batteryLevel, false);
                        
                        // 如果常亮模式开启，启动唤醒循环
                        if (chargingAlwaysOnEnabled) {
                            Log.d(TAG, "💡 常亮模式开启，启动wakeup循环");
                            startWakeupAndUpdateLoop();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "恢复充电动画失败", e);
                    }
                }, 300);  // 300ms延迟，确保通知Activity完全销毁
            }
        }
    };
    
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                chargingConnected = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
                int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) RearScreenChargingActivity.updateBatteryLevelStatic(level * 100 / scale);
                return;
            }
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                chargingConnected = true;
                // 检查开关状态
                boolean enabled = prefs.getBoolean("charging_animation_enabled", true);
                if (!enabled) {
                    Log.d(TAG, "Charging animation disabled");
                    return;
                }
                
                // 检查冷却时间（防止重复触发）
                long currentTime = android.os.SystemClock.elapsedRealtime();
                if (lastChargingAnimationTime != 0 && currentTime - lastChargingAnimationTime < CHARGING_ANIMATION_COOLDOWN_MS) {
                    Log.d(TAG, "⏸ Charging animation in cooldown, skipping");
                    return;
                }
                
                // 检查屏幕锁定状态
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                boolean isLocked = km != null && km.isKeyguardLocked();
                
                if (isLocked) {
                    Log.d(TAG, "🔓 Screen is locked, will show charging animation with screen sleep");
                } else {
                    Log.d(TAG, "🔓 Screen is unlocked, will show charging animation without screen sleep");
                }
                
                int batteryLevel = getBatteryLevel(context);
                Log.d(TAG, "🔌 Power connected, battery: " + batteryLevel + "%");
                
                // 记录触发时间
                lastChargingAnimationTime = currentTime;
                
                // 通知动画管理器：开始充电动画（返回被打断的旧动画）
                RearAnimationManager.AnimationType oldAnim = RearAnimationManager.startAnimation(RearAnimationManager.AnimationType.CHARGING);
                
                // 如果有旧动画需要打断，发送打断广播
                if (oldAnim == RearAnimationManager.AnimationType.NOTIFICATION) {
                    Log.d(TAG, "检测到通知动画正在播放，发送打断广播");
                    RearAnimationManager.sendInterruptBroadcast(ChargingService.this, RearAnimationManager.AnimationType.NOTIFICATION);
                }
                
                showChargingOnRearScreen(batteryLevel, isLocked);
                
                // V3.5: 如果开启了充电动画常亮，启动唤醒和更新循环
                if (chargingAlwaysOnEnabled) {
                    Log.d(TAG, "充电动画常亮已开启，启动wakeup循环");
                    startWakeupAndUpdateLoop();
                }
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                chargingConnected = false;
                RearAnimationManager.clearChargingAlwaysOnFlag();
                RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.CHARGING, RearAnimationManager.getGeneration());
                // 拔掉充电器，立即销毁充电动画
                Log.d(TAG, "🔌 Power disconnected, finishing charging animation");
                
                // V3.5: 停止唤醒循环
                stopWakeupLoop();
                
                finishChargingAnimation();
            }
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ChargingService created");
        
        // 保存实例
        instance = this;
        
        prefs = getSharedPreferences("mrss_settings", Context.MODE_PRIVATE);
        
        // 添加Shizuku监听器
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);  // 监听拔电事件
        registerReceiver(batteryReceiver, filter);
        
        // V3.5: 注册设置变化广播接收器
        IntentFilter settingsFilter = new IntentFilter("com.tgwgroup.MiRearScreenSwitcher.RELOAD_CHARGING_SETTINGS");
        androidx.core.content.ContextCompat.registerReceiver(this, settingsReceiver, settingsFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        
        // V3.5: 注册恢复充电动画广播接收器
        IntentFilter resumeFilter = new IntentFilter("com.tgwgroup.MiRearScreenSwitcher.RESUME_CHARGING_ANIMATION");
        androidx.core.content.ContextCompat.registerReceiver(this, resumeChargingReceiver, resumeFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        
        // V3.5: 加载充电动画常亮设置
        chargingAlwaysOnEnabled = prefs.getBoolean("charging_always_on_enabled", false);
        wakeupHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        // 绑定TaskService
        bindTaskService();
        
        // 启动前台服务保活（使用统一的内核服务通知）
        startForeground(NOTIFICATION_ID, RearScreenKeeperService.createServiceNotification(this));
        Log.d(TAG, "✓ 前台服务已启动（使用内核服务通知）");
    }
    
    private static final int NOTIFICATION_ID = 1001; // 与其他Service共用ID

    private void acquireWakeLock(long timeoutMs) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                if (wakeLock == null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MRSS:ChargingWake");
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
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ChargingService started");
        
        // 确保TaskService已绑定
        if (taskService == null) {
            bindTaskService();
        }
        
        return START_STICKY;
    }
    
    private void bindTaskService() {
        if (!destroyed) TaskServiceClient.bind(null, taskServiceConnection);
    }
    
    private int getBatteryLevel(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
    
    /**
     * 立即结束充电动画
     */
    private void finishChargingAnimation() {
        try {
            // 通过广播通知RearScreenChargingActivity立即结束
            Intent finishIntent = new Intent("com.tgwgroup.MiRearScreenSwitcher.FINISH_CHARGING_ANIMATION");
            finishIntent.setPackage(getPackageName());
            finishIntent.putExtra("animationSession", RearAnimationManager.getGeneration());
            sendBroadcast(finishIntent);
                Log.d(TAG, "已发送结束充电动画的广播");
        } catch (Exception e) {
            Log.e(TAG, "Failed to finish charging animation", e);
        }
    }
    
    private void showChargingOnRearScreen(int level, boolean isLocked) {
        showChargingOnRearScreenWithRetry(level, isLocked, 0);
    }
    
    private void showChargingOnRearScreenWithRetry(int level, boolean isLocked, int retryCount) {
        final long session = RearAnimationManager.getGeneration();
        RearAnimationManager.LAUNCHES.execute(() -> {
            try {
                if (destroyed || !chargingConnected || !RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, session)) return;
                ITaskService service = taskService;
                if (service == null) return;
                android.os.Bundle extras = new android.os.Bundle();
                extras.putInt("batteryLevel", level);
                extras.putLong("animationSession", session);
                String front = service.getForegroundAppOnDisplay(1);
                int task = -1;
                if (front != null && !front.startsWith(getPackageName() + ":") && !front.startsWith("com.xiaomi.subscreencenter:")) {
                    task = Integer.parseInt(front.substring(front.lastIndexOf(':') + 1));
                }
                extras.putInt("rearTaskId", task);
                // Starting a charging page must not kill the rear home: on HyperOS the home
                // immediately restarts and its restart/doze handler can turn the rear display off.
                if (!RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, session)) return;
                acquireWakeLock(8000);
                if (!service.launchRearActivity(getPackageName() + "/" + RearScreenChargingActivity.class.getName(), extras)) {
                    Log.w(TAG, "Charging launch not yet confirmed on the rear display");
                }
            } catch (Exception e) { Log.w(TAG, "Charging animation launch failed", e); }
            finally {
                releaseWakeLock();
                // A task can briefly exist even though Android restored it with an old intent
                // and the activity immediately finished. Confirm this session reached the page.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (destroyed || !chargingConnected
                            || !RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, session)
                            || RearScreenChargingActivity.hasCurrentSession(session)) return;
                    if (retryCount == 0) {
                        Log.i(TAG, "Charging page did not receive this session; retrying on rear display");
                        showChargingOnRearScreenWithRetry(level, isLocked, 1);
                    } else if (RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.CHARGING, session)) {
                        stopWakeupLoop();
                        RearScreenKeeperService.resumeMonitoring();
                    }
                }, 500);
            }
        });
    }
    
    @Override
    public void onDestroy() {
        destroyed = true;
        RearAnimationManager.clearChargingAlwaysOnFlag();
        finishChargingAnimation();
        TaskServiceClient.unbind(null, taskServiceConnection, false);

        super.onDestroy();
        
        // 停止前台服务
        stopForeground(true);
        
        // 清除静态实例
        instance = null;
        
        // 移除Shizuku监听器
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
        } catch (Exception e) {
            Log.e(TAG, "Error removing Shizuku listeners", e);
        }
        
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering battery receiver", e);
        }
        
        // V3.5: 注销设置变化接收器
        try {
            unregisterReceiver(settingsReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering settings receiver", e);
        }
        
        // V3.5: 注销恢复充电动画接收器
        try {
            unregisterReceiver(resumeChargingReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering resume charging receiver", e);
        }
        
        // V3.5: 停止唤醒循环
        stopWakeupLoop();
        
        // 解绑TaskService
        try {
            if (taskService != null) {
                TaskServiceClient.unbind(serviceArgs, taskServiceConnection, true);
                taskService = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unbinding TaskService", e);
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    // V3.5: 启动唤醒和更新电量循环
    private void startWakeupAndUpdateLoop() {
        if (destroyed) return;
        isWakeupRunning = true;
        RearWakeController.acquire(this, this, () -> prefs.getBoolean("charging_always_on_enabled", false));
    }
    
    // V3.5: 停止唤醒循环
    private void stopWakeupLoop() {
        isWakeupRunning = false;
        RearWakeController.release(this);
    }
}

