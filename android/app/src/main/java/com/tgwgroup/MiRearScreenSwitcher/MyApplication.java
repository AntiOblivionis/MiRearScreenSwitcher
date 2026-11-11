/*
 * Author: AntiOblivionis
 * QQ: 319641317
 * Github: https://github.com/GoldenglowSusie/
 * Bilibili: 罗德岛T0驭械术师澄闪
 * 
 * Co-developed with AI assistants:
 * - Cursor
 * - Claude-4.5-Sonnet
 * - GPT-5
 * - Gemini-2.5-Pro
 */

package com.tgwgroup.MiRearScreenSwitcher;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.app.Application;
import rikka.sui.Sui;

/**
 * 自定义Application - 初始化Shizuku
 */
public class MyApplication extends Application {
    
    private static final String TAG = "MyApplication";
    private static boolean isSui = false;
    
    static {
        // 关键！在静态块中初始化Sui
        try {
            isSui = Sui.init("com.tgwgroup.MiRearScreenSwitcher");
        } catch (Throwable e) {
            Log.e(TAG, "Sui init failed", e);
        }
    }
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // HiddenAPI豁免（Android 9+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Class<?> hiddenApiBypass = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass");
                hiddenApiBypass.getMethod("addHiddenApiExemptions", String.class)
                    .invoke(null, "L");
            } catch (Exception e) {
            }
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
    }
    
    public static boolean isSui() {
        return isSui;
    }
}

