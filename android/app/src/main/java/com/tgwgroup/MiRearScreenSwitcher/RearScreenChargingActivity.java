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

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 背屏充电动画Activity
 * 显示充电图标、电量百分比和进度条，5秒后自动关闭并恢复投送app或官方Launcher
 */
public class RearScreenChargingActivity extends Activity {
    private long animationSession;
    private boolean contentInitialized;
    private android.animation.ValueAnimator fillAnimator;
    private GestureDetector doubleTapDetector;
    private final android.os.Handler recoveryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicLong recoveryGeneration = new java.util.concurrent.atomic.AtomicLong();
    private Runnable pendingRecovery;
    private long pendingRecoveryDeadline;
    private DisplayManager displays;
    private boolean displayListenerRegistered;
    private boolean chargingPageResumed;
    private boolean primaryWasUnlockedWhileResumed;
    private int previousPrimaryState = Display.STATE_UNKNOWN;
    private long lastPauseTime;
    private final java.util.concurrent.atomic.AtomicLong lockRecoveryGeneration = new java.util.concurrent.atomic.AtomicLong();
    private Runnable pendingLockRecovery;
    private long pendingLockRecoveryDeadline;

    private final DisplayManager.DisplayListener primaryDisplayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { }
        @Override public void onDisplayRemoved(int displayId) { }
        @Override public void onDisplayChanged(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY) return;
            int state = primaryDisplayState();
            boolean turnedOff = previousPrimaryState == Display.STATE_ON
                    && state != Display.STATE_ON && state != Display.STATE_UNKNOWN;
            previousPrimaryState = state;
            if (chargingPageResumed && state == Display.STATE_ON && isPrimaryUnlocked()) {
                primaryWasUnlockedWhileResumed = true;
            }
            if (turnedOff && primaryWasUnlockedWhileResumed && (chargingPageResumed
                    || SystemClock.elapsedRealtime() - lastPauseTime <= 2000)) {
                scheduleLockRecovery();
            }
        }
    };

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (!RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, intent.getLongExtra("animationSession", -1))) return;
        if (intent.getLongExtra("animationSession", -1) != animationSession) {
            setIntent(intent);
            recreate();
        }
    }

    private static final String TAG = "RearScreenChargingActivity";
    private int rearTaskId = -1;  // 背屏投送的app的taskId，-1表示没有投送app
    private boolean autoFinishScheduled = false; // 是否已安排自动销毁
    
    // 静态实例追踪，防止旧实例干扰新实例
    private static volatile RearScreenChargingActivity currentInstance = null;
    private static volatile long currentInstanceCreateTime = 0;
    
    // 静态电量更新方法，供ChargingService直接调用
    public static void updateBatteryLevelStatic(int newLevel) {
        if (currentInstance != null) {
            currentInstance.updateBatteryLevel(newLevel);
        }
    }

    static boolean hasCurrentSession(long session) {
        RearScreenChargingActivity activity = currentInstance;
        return activity != null && activity.animationSession == session && activity.contentInitialized
                && !activity.isFinishing() && !activity.isDestroyed();
    }
    
    // 广播接收器：接收立即结束的命令和电量更新
    private android.content.BroadcastReceiver finishReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                if (chargingPageResumed) primaryWasUnlockedWhileResumed = true;
            } else if ("com.tgwgroup.MiRearScreenSwitcher.FINISH_CHARGING_ANIMATION".equals(action)) {
                if (intent.getLongExtra("animationSession", -1) != animationSession) return;
                Log.d(TAG, "🔌 收到拔电广播，立即销毁");
                finish();
            } else if ("com.tgwgroup.MiRearScreenSwitcher.INTERRUPT_CHARGING_ANIMATION".equals(action)) {
                if (RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, animationSession)) return;
                Log.d(TAG, "🔄 收到打断广播（新动画来了），立即销毁但不恢复Launcher");
                // 标记为被打断，onDestroy不恢复Launcher
                finish();
            } else if ("com.tgwgroup.MiRearScreenSwitcher.UPDATE_CHARGING_BATTERY".equals(action)) {
                // V3.5: 接收电量更新
                int newLevel = intent.getIntExtra("batteryLevel", -1);
                Log.d(TAG, "📡 收到电量更新广播: " + newLevel + "%");
                if (newLevel >= 0) {
                    updateBatteryLevel(newLevel);
                }
            }
        }
    };
    
    public RearScreenChargingActivity() {
        super();
        long time = System.currentTimeMillis();
        Log.d(TAG, String.format("[%tT.%tL] 🟢 构造函数被调用", time, time));
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long onCreateStartTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%tT.%tL] 🟡 onCreate开始", onCreateStartTime, onCreateStartTime));
        
        super.onCreate(savedInstanceState);
        animationSession = getIntent().getLongExtra("animationSession", -1);
        if (!RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.CHARGING, animationSession)) { finish(); return; }
        // 判断当前所在的屏幕
        int displayId = getWindowManager().getDefaultDisplay().getDisplayId();
        Log.d(TAG, String.format("[%tT.%tL] 📍 当前displayId=%d", onCreateStartTime, onCreateStartTime, displayId));
        
        int level = getIntent().getIntExtra("batteryLevel", 0);
        rearTaskId = getIntent().getIntExtra("rearTaskId", -1);
        
        // Never render or remain as a show-when-locked window outside the rear display.
        if (displayId != 1) {
            Log.w(TAG, "Charging activity launched outside the rear display; closing");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) setShowWhenLocked(false);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            finish();
            return;
        }
        currentInstance = this;
        contentInitialized = true;
        // --- 以下代码只在背屏(displayId == 1)执行 ---
        Log.d(TAG, String.format("[%tT.%tL] 🎯 在背屏执行，开始设置内容", onCreateStartTime, onCreateStartTime));
        
        // V3.3: 保持常亮 + 锁屏显示
        getWindow().addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );
        
        // 适配新API：锁屏时显示
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        }
        
        // V3.5: 优化渲染性能（解决DequeueBuffer超时）
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        // V3.16: 移除120Hz重新设置，系统自动管理刷新率
        
        // ⚠️ 关键：在 setContentView 之前强制使用背屏DPI！
        forceRearScreenDensityBeforeInflate();
        
        setContentView(R.layout.activity_rear_screen_charging);

        displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displays != null) {
            previousPrimaryState = primaryDisplayState();
            displays.registerDisplayListener(primaryDisplayListener, recoveryHandler);
            displayListenerRegistered = true;
        }

        // Keep this screen's touch stream together so the second tap can be associated with
        // this charging session even when the vendor's global gesture monitor also sees it.
        doubleTapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(MotionEvent event) {
                scheduleDoubleTapRecovery();
                return true;
            }
        });
        
        long afterSetContentViewTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%tT.%tL] 🟠 setContentView完成", 
            afterSetContentViewTime, afterSetContentViewTime));
        
        
        long afterGetIntentTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%tT.%tL] ⚡ Intent数据: Battery=%d%%, rearTaskId=%d", 
            afterGetIntentTime, afterGetIntentTime, level, rearTaskId));
        
        // V3.5: 获取全屏液体视图
        LightningShapeView fullScreenLiquid = findViewById(R.id.full_screen_liquid);
        fullScreenLiquid.setFullScreenMode(true);
        fullScreenLiquid.setHostResumed(false);
        TextView batteryText = findViewById(R.id.battery_text);
        View chargingContainer = findViewById(R.id.charging_container);
        
        // 应用安全区域margin到电量数字
        applySafeAreaToText(batteryText);
        
        // 设置电量文字
        batteryText.setText(level + "%");
        
        // 启动全屏液体填充动画（非线性，从0到电量百分比）
        startFullScreenLiquidAnimation(fullScreenLiquid, level);
        
        // 启动电量数字淡入动画
        startCenterTextAnimation(batteryText);
        
        long animationStartTime = System.currentTimeMillis();
        
        // V3.5: 检查充电常亮开关
        boolean chargingAlwaysOn = getSharedPreferences("mrss_settings", MODE_PRIVATE)
            .getBoolean("charging_always_on_enabled", false);
        
        if (chargingAlwaysOn) {
            Log.d(TAG, String.format("[%tT.%tL] 🎬 动画已启动，充电常亮模式，不自动关闭", 
                animationStartTime, animationStartTime));
        } else {
            Log.d(TAG, String.format("[%tT.%tL] 🎬 动画已启动，8秒后自动关闭", 
                animationStartTime, animationStartTime));
            // 8秒后自动关闭
            chargingContainer.postDelayed(this::finish, 8000);
        }
        autoFinishScheduled = true;
        
        long onCreateEndTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%tT.%tL] ✅ onCreate完成 (总耗时%dms)", 
            onCreateEndTime, onCreateEndTime, onCreateEndTime - onCreateStartTime));
        
        // 注册广播接收器（监听拔电、打断和电量更新事件）
        android.content.IntentFilter finishFilter = new android.content.IntentFilter();
        finishFilter.addAction("com.tgwgroup.MiRearScreenSwitcher.FINISH_CHARGING_ANIMATION");
        finishFilter.addAction("com.tgwgroup.MiRearScreenSwitcher.INTERRUPT_CHARGING_ANIMATION");
        finishFilter.addAction("com.tgwgroup.MiRearScreenSwitcher.UPDATE_CHARGING_BATTERY");  // V3.5: 监听电量更新
        finishFilter.addAction(Intent.ACTION_USER_PRESENT);
        androidx.core.content.ContextCompat.registerReceiver(this, finishReceiver, finishFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        
        // 注册LocalBroadcastManager接收器（监听电量更新）
        // androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(finishReceiver, finishFilter);
        Log.d(TAG, String.format("[%tT.%tL] ✅ 已注册充电动画广播接收器", onCreateEndTime, onCreateEndTime));
        Log.d(TAG, "📡 广播接收器已注册，监听: FINISH_CHARGING_ANIMATION, INTERRUPT_CHARGING_ANIMATION, UPDATE_CHARGING_BATTERY");
        
        // 设置为当前实例
        currentInstance = this;
        currentInstanceCreateTime = onCreateEndTime;
        
        // 测试代码已移除
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (!ensureRearDisplay()) return;
        chargingPageResumed = true;
        previousPrimaryState = primaryDisplayState();
        primaryWasUnlockedWhileResumed = isPrimaryUnlocked();
        View container = findViewById(R.id.charging_container);
        if (container != null) container.setVisibility(View.VISIBLE);
        LightningShapeView liquid = findViewById(R.id.full_screen_liquid);
        if (liquid != null) liquid.setHostResumed(true);
    }

    private boolean ensureRearDisplay() {
        android.view.Display display = getWindowManager().getDefaultDisplay();
        if (display != null && display.getDisplayId() == 1) return true;
        View container = findViewById(R.id.charging_container);
        if (container != null) container.setVisibility(View.INVISIBLE);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) setShowWhenLocked(false);
        Log.w(TAG, "Charging activity left the rear display; hiding and closing");
        if (!isFinishing()) finish();
        return false;
    }

    private int primaryDisplayState() {
        Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        return display == null ? Display.STATE_UNKNOWN : display.getState();
    }

    private boolean isPrimaryUnlocked() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguard != null && !keyguard.isKeyguardLocked();
    }

    private boolean isLockRecoveryCurrent(long session, long generation, long deadline) {
        boolean instanceCurrent = currentInstance == this && !isFinishing() && !isDestroyed();
        boolean sessionCurrent = ChargingService.mayRecoverCharging(session);
        boolean withinDeadline = lockRecoveryGeneration.get() == generation && SystemClock.elapsedRealtime() <= deadline;
        int primaryState = primaryDisplayState();
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean locked = keyguard != null && keyguard.isKeyguardLocked();
        // Some dual-screen firmware reports the primary Display as ON while it is in AOD.
        // A lock transition while this page was visible is established before scheduling.
        boolean eligible = instanceCurrent && sessionCurrent && withinDeadline && locked;
        if (!eligible) Log.d(TAG, "Lock check: instance=" + instanceCurrent + ", session=" + sessionCurrent
                + ", inTime=" + withinDeadline + ", primaryState=" + primaryState + ", locked=" + locked);
        return eligible;
    }

    private void scheduleLockRecovery() {
        if (!ChargingService.mayRecoverCharging(animationSession)) return;
        long now = SystemClock.elapsedRealtime();
        if (pendingLockRecovery != null && now <= pendingLockRecoveryDeadline) {
            // The display transition can follow onPause; check promptly when it arrives.
            recoveryHandler.postDelayed(pendingLockRecovery, 80);
            return;
        }
        final long session = animationSession;
        final long generation = lockRecoveryGeneration.incrementAndGet();
        final long deadline = now + 2500;
        pendingLockRecoveryDeadline = deadline;
        final int task = rearTaskId;
        final int level = getIntent().getIntExtra("batteryLevel", 0);
        final java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        Log.i(TAG, "Charging page saw a possible primary-screen lock; checking rear display");
        pendingLockRecovery = () -> RearAnimationManager.LAUNCHES.execute(() -> {
            if (restored.get() || !isLockRecoveryCurrent(session, generation, deadline)) return;
            int attempt = attempts.incrementAndGet();
            if (attempt > 2) return;
            ITaskService service = ChargingService.getTaskService();
            if (service == null) return;
            try {
                // Display.getState() can lag physical doze on this firmware. Wake only the rear
                // once for this verified lock transition before checking which task is in front.
                if (attempt == 1 && isLockRecoveryCurrent(session, generation, deadline)) service.wakeDisplay(1);
                String foreground = service.getForegroundAppOnDisplay(1);
                if (foreground == null || !foreground.startsWith("com.xiaomi.subscreencenter:")) {
                    Log.i(TAG, "Lock recovery skipped: rear home is not foreground");
                    return;
                }
                if (!isLockRecoveryCurrent(session, generation, deadline)) return;
                Bundle extras = new Bundle();
                extras.putInt("batteryLevel", level);
                extras.putInt("rearTaskId", task);
                extras.putLong("animationSession", session);
                boolean success = service.launchRearActivity(getPackageName() + "/" + RearScreenChargingActivity.class.getName(), extras);
                if (success) restored.set(true);
                Log.i(TAG, "Locked-screen charging recovery requested: " + success);
            } catch (Exception e) {
                Log.w(TAG, "Locked-screen charging recovery failed", e);
            }
        });
        // These are the only task checks for this lock transition, never a watchdog.
        recoveryHandler.postDelayed(pendingLockRecovery, 150);
        recoveryHandler.postDelayed(pendingLockRecovery, 850);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (doubleTapDetector != null && ChargingService.mayRecoverCharging(animationSession)) {
            doubleTapDetector.onTouchEvent(event);
            // This page has no interactive controls. Other screens retain normal touch handling.
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isRecoveryCurrent(long session, long gesture, long deadline) {
        return currentInstance == this && !isFinishing() && !isDestroyed()
                && recoveryGeneration.get() == gesture && android.os.SystemClock.elapsedRealtime() <= deadline
                && ChargingService.mayRecoverCharging(session);
    }

    private void scheduleDoubleTapRecovery() {
        if (!ChargingService.mayRecoverCharging(animationSession)) return;
        if (pendingRecovery != null) recoveryHandler.removeCallbacks(pendingRecovery);
        final long gesture = recoveryGeneration.incrementAndGet();
        final long session = animationSession;
        final long deadline = android.os.SystemClock.elapsedRealtime() + 2500;
        pendingRecoveryDeadline = deadline;
        final int task = rearTaskId;
        final int level = getIntent().getIntExtra("batteryLevel", 0);
        final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        Log.i(TAG, "Charging double tap observed; checking for a system sleep transition");
        pendingRecovery = () -> RearAnimationManager.LAUNCHES.execute(() -> {
            if (started.get() || attempts.incrementAndGet() > 2 || !isRecoveryCurrent(session, gesture, deadline)) return;
            ITaskService service = ChargingService.getTaskService();
            if (service == null) return;
            try {
                String foreground = service.getForegroundAppOnDisplay(1);
                // Do not cover a notification, a projected app, or an unknown task.
                if (foreground == null || !foreground.startsWith("com.xiaomi.subscreencenter:")) {
                    Log.i(TAG, "Double-tap recovery skipped: rear home is not foreground");
                    return;
                }
                if (!isRecoveryCurrent(session, gesture, deadline)) return;
                if (!started.compareAndSet(false, true)) return;
                android.os.Bundle extras = new android.os.Bundle();
                extras.putInt("batteryLevel", level);
                extras.putInt("rearTaskId", task);
                extras.putLong("animationSession", session);
                boolean restored = service.launchRearActivity(getPackageName() + "/" + RearScreenChargingActivity.class.getName(), extras);
                if (!restored) started.set(false);
                Log.i(TAG, "Double-tap charging recovery requested: " + restored);
            } catch (Exception e) {
                started.set(false);
                Log.w(TAG, "Double-tap charging recovery failed", e);
            }
        });
        // At most two checks for this gesture, never a watchdog or repeated relaunch.
        recoveryHandler.postDelayed(pendingRecovery, 350);
    }

    @Override protected void onPause() {
        chargingPageResumed = false;
        lastPauseTime = SystemClock.elapsedRealtime();
        if (primaryWasUnlockedWhileResumed) scheduleLockRecovery();
        View container = findViewById(R.id.charging_container);
        if (container != null) container.setVisibility(View.INVISIBLE);
        LightningShapeView liquid = findViewById(R.id.full_screen_liquid);
        if (liquid != null) liquid.setHostResumed(false);
        super.onPause();
        if (pendingRecovery != null && android.os.SystemClock.elapsedRealtime() <= pendingRecoveryDeadline) {
            // The OEM has moved the charging page away. Try promptly; the original delayed
            // check remains as a bounded fallback if task visibility has not settled yet.
            recoveryHandler.postDelayed(pendingRecovery, 30);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (contentInitialized) ensureRearDisplay();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (contentInitialized && !ensureRearDisplay()) return;
    }

    @Override
    protected void onDestroy() {
        recoveryGeneration.incrementAndGet();
        if (pendingRecovery != null) recoveryHandler.removeCallbacks(pendingRecovery);
        lockRecoveryGeneration.incrementAndGet();
        if (pendingLockRecovery != null) recoveryHandler.removeCallbacks(pendingLockRecovery);
        if (displayListenerRegistered) {
            displays.unregisterDisplayListener(primaryDisplayListener);
            displayListenerRegistered = false;
        }
        if (fillAnimator != null) { fillAnimator.cancel(); fillAnimator = null; }
        LightningShapeView liquid = findViewById(R.id.full_screen_liquid);
        if (liquid != null) liquid.release();
        long destroyTime = System.currentTimeMillis();
        Log.d(TAG, String.format("[%tT.%tL] 🔴 onDestroy被调用", destroyTime, destroyTime));
        
        // 注销广播接收器
        try {
            unregisterReceiver(finishReceiver);
            Log.d(TAG, String.format("[%tT.%tL] ✅ 已注销充电动画广播接收器", destroyTime, destroyTime));
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister finish receiver: " + e.getMessage());
        }
        
        // 注销LocalBroadcastManager接收器
        // try {
        //     androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(finishReceiver);
        //     Log.d(TAG, String.format("[%tT.%tL] ✅ 已注销LocalBroadcastManager接收器", destroyTime, destroyTime));
        // } catch (Exception e) {
        //     Log.w(TAG, "Failed to unregister LocalBroadcastManager receiver: " + e.getMessage());
        // }
        
        super.onDestroy();
        
        // 检查是否是当前实例，防止旧实例干扰新实例
        if (this != currentInstance) {
            Log.w(TAG, String.format("[%tT.%tL] ⚠️ 这是旧实例，跳过恢复操作", destroyTime, destroyTime));
            return;
        }
        
        // 通知动画管理器：充电动画结束
        currentInstance = null;
        if (isChangingConfigurations()) return;
        boolean shouldRestore = RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.CHARGING, animationSession);
        
        // 只有正常结束时才恢复Launcher，被打断时不恢复
        if (!shouldRestore) {
            Log.d(TAG, String.format("[%tT.%tL] 🔄 充电动画被打断，跳过恢复Launcher", destroyTime, destroyTime));
            return;
        }
        
        // 在背屏恢复投送app或官方Launcher（仅当在背屏时）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            int currentDisplayId = getDisplay() != null ? getDisplay().getDisplayId() : 0;
            Log.d(TAG, String.format("[%tT.%tL] 📍 当前displayId=%d", destroyTime, destroyTime, currentDisplayId));
            
            if (currentDisplayId == 1) {
                final int finalTaskId = rearTaskId;
                
                // 在后台线程执行恢复操作，不阻塞onDestroy
                RearAnimationManager.LAUNCHES.execute(() -> {
                    try {
                        if (RearAnimationManager.getGeneration() != animationSession) return;
                        
                        if (finalTaskId > 0) {
                            Log.d(TAG, "⚡ 恢复投送app (taskId=" + finalTaskId + ")");
                            restoreProjectedApp(finalTaskId);
                        } else {
                            Log.d(TAG, "⚡ 恢复官方Launcher");
                            restoreOfficialLauncher();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in restore thread", e);
                    }
                });
            }
        }
    }
    
    private void restoreProjectedApp(int taskId) {
        // The existing keeper owns its persisted identity. Do not recreate it from stale tile state.
        RearScreenKeeperService.resumeMonitoring();
    }

    private void restoreOfficialLauncher() {
        try {
            ITaskService service = ChargingService.getTaskService();
            if (service != null) service.enableSubScreenLauncher();
        } catch (Exception e) { Log.w(TAG, "Launcher restore failed", e); }
        finally { RearScreenKeeperService.resumeMonitoring(); }
    }
    
    /**
     * 在inflate布局之前强制使用背屏DPI
     */
    private void forceRearScreenDensityBeforeInflate() {
        try {
            // 从缓存获取背屏DPI（适配所有小米双屏设备）
            RearDisplayHelper.RearDisplayInfo info = DisplayInfoCache.getInstance().getCachedInfo(this);
            int rearScreenDpi = info.densityDpi;
            
            // DisplayManager provides live metrics; never sleep or query a shell on the UI thread.
            if (rearScreenDpi <= 0) return;

            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            int currentDpi = metrics.densityDpi;
            
            Log.d(TAG, String.format("🔧 inflate前 - 当前DPI=%d, 背屏DPI=%d", currentDpi, rearScreenDpi));
            
            metrics.densityDpi = rearScreenDpi;
            metrics.density = rearScreenDpi / 160f;
            metrics.scaledDensity = metrics.density;
            
            android.content.res.Configuration config = new android.content.res.Configuration(getResources().getConfiguration());
            config.densityDpi = rearScreenDpi;
            
            getResources().updateConfiguration(config, metrics);
            
            Log.d(TAG, String.format("✅ inflate前已强制应用背屏DPI: %d", metrics.densityDpi));
                
        } catch (Exception e) {
            Log.e(TAG, "❌ inflate前应用DPI失败", e);
        }
    }
    
    /**
     * V3.5: 全屏液体填充动画（非线性，从0到目标电量）
     */
    private void startFullScreenLiquidAnimation(LightningShapeView liquidView, int targetLevel) {
        // 目标填充比例
        float targetFillLevel = targetLevel / 100f;
        
        // 创建非线性填充动画（DecelerateInterpolator - 减速效果）
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, targetFillLevel);
        fillAnimator = animator;
        animator.setDuration(2000); // 2秒填充动画
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator(2.5f));
        
        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            liquidView.setFillLevel(animatedValue);
        });
        
        animator.start();
        Log.d(TAG, String.format("🌊 全屏液体填充动画已启动: 0%% → %d%%", targetLevel));
    }
    
    /**
     * V3.5: 中央电量数字淡入动画
     */
    private void startCenterTextAnimation(TextView textView) {
        textView.setAlpha(0f);
        textView.setScaleX(0.8f);
        textView.setScaleY(0.8f);
        
        textView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setStartDelay(600) // 液体填充开始后显示
            .setInterpolator(new android.view.animation.DecelerateInterpolator(2.0f))
            .start();
    }
    
    /**
     * V3.5: 更新电量显示（充电常亮模式下实时更新）
     */
    private void updateBatteryLevel(int newLevel) {
        try {
            Log.d(TAG, "🔋 开始更新电量: " + newLevel + "%");
            LightningShapeView liquidView = findViewById(R.id.full_screen_liquid);
            TextView batteryText = findViewById(R.id.battery_text);
            
            if (liquidView != null && batteryText != null) {
                if (fillAnimator != null) { fillAnimator.cancel(); fillAnimator = null; }
                // 平滑更新液体填充
                liquidView.setFillLevel(newLevel / 100f);
                // 更新数字
                batteryText.setText(newLevel + "%");
                Log.d(TAG, "🔋 电量已更新: " + newLevel + "%");
            } else {
                Log.w(TAG, "⚠️ 视图未找到，无法更新电量 - liquidView=" + (liquidView != null) + ", batteryText=" + (batteryText != null));
            }
        } catch (Exception e) {
            Log.w(TAG, "更新电量失败: " + e.getMessage());
        }
    }
    
    /**
     * V3.5: 应用安全区域到电量数字（确保数字显示在安全区域中央）
     */
    private void applySafeAreaToText(TextView textView) {
        try {
            // 从缓存获取背屏信息
            RearDisplayHelper.RearDisplayInfo info = DisplayInfoCache.getInstance().getCachedInfo(this);
            
            if (info == null) {
                Log.w(TAG, "⚠️ 背屏信息缓存为空");
                return;
            }
            
            if (!info.hasCutout()) {
                Log.d(TAG, "ℹ️ 背屏无Cutout，数字自动居中");
                return;
            }
            
            // 设置margin让数字居中在安全区域
            if (textView.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams params = 
                    (android.widget.FrameLayout.LayoutParams) textView.getLayoutParams();
                
                params.leftMargin = info.cutout.left;
                params.topMargin = info.cutout.top;
                params.rightMargin = info.cutout.right;
                params.bottomMargin = info.cutout.bottom;
                textView.setLayoutParams(params);
                
                Log.d(TAG, String.format("✅ 电量数字已应用安全区域: left=%d, top=%d, right=%d, bottom=%d",
                    info.cutout.left, info.cutout.top, info.cutout.right, info.cutout.bottom));
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 应用安全区域失败", e);
        }
    }
}

