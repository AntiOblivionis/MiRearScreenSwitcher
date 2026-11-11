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

import android.graphics.Rect;
import android.util.Log;

/**
 * 显示屏信息缓存
 * 在应用启动时获取一次，之后直接使用缓存数据
 */
public class DisplayInfoCache {
    private static final String TAG = "DisplayInfoCache";
    
    // 单例
    private static volatile DisplayInfoCache instance;
    
    // 缓存的背屏信息
    private volatile RearDisplayHelper.RearDisplayInfo cachedInfo;
    private volatile boolean initialized = false;
    private final java.util.concurrent.atomic.AtomicBoolean loading = new java.util.concurrent.atomic.AtomicBoolean();

    public void initializeAsync(ITaskService service) {
        if (initialized || !loading.compareAndSet(false, true)) return;
        new Thread(() -> {
            try { initialize(service); } finally { loading.set(false); }
        }, "MRSS-display-info").start();
    }
    
    private DisplayInfoCache() {}

    /** Public display APIs reflect current DPI/rotation without a shell query or a stale default. */
    public RearDisplayHelper.RearDisplayInfo getCachedInfo(android.content.Context context) {
        android.hardware.display.DisplayManager manager = context.getSystemService(android.hardware.display.DisplayManager.class);
        android.view.Display display = manager == null ? null : manager.getDisplay(1);
        if (display == null) return getCachedInfo();
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);
        RearDisplayHelper.RearDisplayInfo info = new RearDisplayHelper.RearDisplayInfo();
        info.width = metrics.widthPixels;
        info.height = metrics.heightPixels;
        info.densityDpi = metrics.densityDpi;
        info.verified = info.width > 0 && info.height > 0 && info.densityDpi > 0;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.view.DisplayCutout cutout = display.getCutout();
            if (cutout != null) info.cutout = new Rect(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                    cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
        }
        return info;
    }
    
    public static DisplayInfoCache getInstance() {
        if (instance == null) {
            synchronized (DisplayInfoCache.class) {
                if (instance == null) {
                    instance = new DisplayInfoCache();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化缓存（在应用启动时调用一次）
     */
    public synchronized void initialize(ITaskService taskService) {
        if (initialized) {
            Log.d(TAG, "ℹ️ 已初始化，跳过");
            return;
        }
        
        try {
            Log.d(TAG, "🔄 开始获取背屏信息...");
            cachedInfo = RearDisplayHelper.getRearDisplayInfo(taskService);
            initialized = cachedInfo.verified;
            
            Log.d(TAG, String.format("✅ 背屏信息已缓存: %dx%d, DPI=%d, Cutout=%s",
                cachedInfo.width, cachedInfo.height, cachedInfo.densityDpi,
                cachedInfo.hasCutout() ? cachedInfo.cutout.toString() : "无"));
                
        } catch (Exception e) {
            Log.e(TAG, "❌ 初始化失败", e);
            // 设置默认值
            cachedInfo = new RearDisplayHelper.RearDisplayInfo();
            cachedInfo.width = 904;
            cachedInfo.height = 572;
            cachedInfo.densityDpi = 450;
            cachedInfo.cutout = new Rect(0, 0, 0, 0);
            initialized = false;
            Log.w(TAG, "⚠️ 使用默认背屏信息");
        }
    }
    
    /**
     * 获取缓存的背屏信息
     */
    public RearDisplayHelper.RearDisplayInfo getCachedInfo() {
        if (!initialized) {
            Log.w(TAG, "⚠️ 缓存未初始化，返回默认值");
            RearDisplayHelper.RearDisplayInfo defaultInfo = new RearDisplayHelper.RearDisplayInfo();
            defaultInfo.width = 904;
            defaultInfo.height = 572;
            defaultInfo.densityDpi = 450;
            defaultInfo.cutout = new Rect(0, 0, 0, 0);
            return defaultInfo;
        }
        RearDisplayHelper.RearDisplayInfo value = cachedInfo;
        return value != null ? value : new RearDisplayHelper.RearDisplayInfo();
    }
    
    /**
     * 强制重新获取（用于刷新缓存）
     */
    public synchronized void refresh(ITaskService taskService) {
        initialized = false;
        initialize(taskService);
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 清除缓存（用于测试或重置）
     */
    public synchronized void clear() {
        cachedInfo = null;
        initialized = false;
        Log.d(TAG, "🗑️ 缓存已清除");
    }
}

