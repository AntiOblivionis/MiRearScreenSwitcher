/*
 * Author: AntiOblivionis
 * QQ: 319641317
 * Github: https://github.com/AntiOblivionis/
 * Bilibili: AntiOblivionis
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
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

/**
 * 专门用于点亮背屏的透明Activity
 * 参考 MiRearScreenNotification 的实现
 * V2.1: 支持动态旋转控制
 */
public class RearScreenWakeupActivity extends Activity {
    private static final String TAG = "RearScreenWakeup";
    
    // 静态变量存储背屏旋转方向
    private static int sRearDisplayRotation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    
    /**
     * V2.1: 设置背屏旋转方向（从外部调用）
     * @param rotation 旋转方向 (0=0°, 1=90°, 2=180°, 3=270°)
     */
    public static void setRearDisplayRotation(int rotation) {
        // 将rotation值转换为ActivityInfo常量
        switch (rotation) {
            case 0:
                sRearDisplayRotation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case 1:
                sRearDisplayRotation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case 2:
                sRearDisplayRotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            case 3:
                sRearDisplayRotation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
            default:
                sRearDisplayRotation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                break;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 应用旋转设置
        if (sRearDisplayRotation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            setRequestedOrientation(sRearDisplayRotation);
        }
        
        // 获取当前display
        int displayId = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayId = getDisplay().getDisplayId();
        }
        // 如果在主屏，什么都不做
        if (displayId == 0) {
            return;
        }
        
        // --- 以下代码只在背屏 (displayId == 1) 执行 ---
        
        // 关键：在背屏时点亮屏幕并保持常亮
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );
        
        // 适配新API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        // 延迟关闭（给予足够时间点亮屏幕）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            finish();
        }, 1000); // 1秒后关闭
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 再次确保点亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );
    }
    
    @Override
    public void finish() {
        super.finish();
        // 禁用转场动画
        overridePendingTransition(0, 0);
    }
}

