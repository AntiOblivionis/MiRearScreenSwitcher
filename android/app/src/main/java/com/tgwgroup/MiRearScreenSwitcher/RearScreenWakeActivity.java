/*
 * Author: AntiOblivionis
 * QQ: 319641317
 * Github: https://github.com/AntiOblivionis/
 * Bilibili: AntiOblivionis
 * 
 * Co-developed with AI assistants:
 * - Cursor
 * - Claude-4.5-Sonnet
 * - GPT-5
 * - Gemini-2.5-Pro
 */

package com.tgwgroup.MiRearScreenSwitcher;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * 持久化的背屏常亮Activity
 * 修改策略：
 * 1. 不再自动关闭，保持存活以维持FLAG_KEEP_SCREEN_ON
 * 2. 使用TYPE_APPLICATION_OVERLAY保持可见，同时FLAG_NOT_TOUCHABLE不影响交互
 * 3. 全屏尺寸 + 完全透明（alpha=0），用户完全看不到但系统认为可见
 * 
 * 关键发现（3次迭代）：
 * V1: FLAG_NOT_FOCUSABLE → 立即onPause/onStop（18ms）
 * V2: 移除FLAG_NOT_FOCUSABLE + 屏幕外(-1000,-1000) → 依然onStop（109ms）
 * V3: 窗口必须在屏幕内(0,0) + alpha=0透明 → 最终方案✅
 */
public class RearScreenWakeActivity extends Activity {
    private static final String TAG = "RearScreenWakeActivity";
    private static RearScreenWakeActivity instance = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 保存实例引用
        instance = this;
        
        // 获取当前display
        int displayId = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayId = getDisplay().getDisplayId();
        }
        // V6更新：区分主屏和背屏的不同策略
        boolean isMainDisplay = (displayId == 0);
        
        if (isMainDisplay) {
        } else {
        }
        
        // --- 通用设置（主屏和背屏都需要） ---
        
        // 设置纯黑透明内容（OLED优化）
        View rootView = new View(this);
        rootView.setBackgroundColor(0x00000000); // 完全透明
        setContentView(rootView);
        
        // 关键修改：使用TYPE_APPLICATION_OVERLAY保持窗口一直可见
        // 这种类型的窗口不会被系统自动隐藏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        
        // 主屏和背屏使用不同的窗口配置
        WindowManager.LayoutParams params = getWindow().getAttributes();
        
        if (isMainDisplay) {
            // 主屏：小窗口 + 无焦点，完全不影响用户操作
            params.width = 1;   // 1像素宽度
            params.height = 1;  // 1像素高度
            params.x = 0;
            params.y = 0;
            params.alpha = 0.0f;  // 完全透明
        } else {
            // 背屏：全屏 + 可获取焦点，保持常亮
            params.screenBrightness = 0.01f;  // 1% 亮度
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.x = 0;
            params.y = 0;
            params.alpha = 0.0f;  // 完全透明
        }
        getWindow().setAttributes(params);
        
        // Flags配置：主屏和背屏不同
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        
        if (isMainDisplay) {
            // 主屏：添加FLAG_NOT_FOCUSABLE，避免影响用户操作
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            // 背屏：添加FLAG_KEEP_SCREEN_ON，保持屏幕常亮
            // 移除FLAG_SHOW_WHEN_LOCKED以避免锁屏时干扰触摸手势
            flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        
        getWindow().addFlags(flags);
        
        // 移除 setShowWhenLocked，避免锁屏时干扰触摸
        // 现在锁屏时Activity会被隐藏，但Service继续保持Launcher禁用
        // **不再自动关闭** - 保持Activity存活以维持常亮
        // 移除原来的 postDelayed(finish()) 代码
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // 获取当前display
        int displayId = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayId = getDisplay().getDisplayId();
        }
        boolean isMainDisplay = (displayId == 0);
        // 确保flags持续生效
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        }
        
        // 重新应用flags（根据display不同）
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        
        if (isMainDisplay) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        
        getWindow().addFlags(flags);
        
        // 重新设置窗口参数
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (isMainDisplay) {
            params.width = 1;
            params.height = 1;
            params.x = 0;
            params.y = 0;
            params.alpha = 0.0f;
        } else {
            params.screenBrightness = 0.01f;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.x = 0;
            params.y = 0;
            params.alpha = 0.0f;
        }
        getWindow().setAttributes(params);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 立即将焦点转回（尝试让投射的应用重新获得焦点）
            // 使用moveTaskToBack而不是finish，保持Activity存活
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    moveTaskToBack(true);
                }
            }, 100); // 100ms延迟
        } else {
            // 尝试获取当前前台任务信息
            logCurrentTaskStack();
        }
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
    
    /**
     * 尝试记录当前任务栈信息（调试用）
     */
    private void logCurrentTaskStack() {
        try {
            // 简单记录，不使用需要权限的API
        } catch (Exception e) {
            Log.e(TAG, "Error logging task stack", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }
    
    /**
     * 提供静态方法用于外部关闭Activity
     * 可以通过 RearScreenWakeActivity.closeIfExists() 来停止常亮
     */
    public static void closeIfExists() {
        if (instance != null) {
            instance.finish();
        }
    }
    
    /**
     * 检查Activity是否存活
     */
    public static boolean isAlive() {
        return instance != null;
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0); // 禁用转场动画
    }
}

