package com.tgwgroup.MiRearScreenSwitcher;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class RearMediaActivity extends FlutterActivity {
    private static final String MEDIA_CHANNEL = "com.display.switcher/media";
    public static RearMediaActivity activeInstance = null;
    private MethodChannel channel;

    @NonNull
    @Override
    public String getInitialRoute() {
        return "/rearMedia";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeInstance = this;

        // Apply Persistence/Hijacking Flags
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
    }

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        channel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), MEDIA_CHANNEL);
        
        channel.setMethodCallHandler((call, result) -> {
            if (call.method.equals("playPause")) {
                if (NotificationService.getInstance() != null) {
                   NotificationService.getInstance().toggleMediaPlayback();
                }
                result.success(null);
            } else if (call.method.equals("skipNext")) {
                if (NotificationService.getInstance() != null) {
                   NotificationService.getInstance().skipNextMedia();
                }
                result.success(null);
            } else if (call.method.equals("skipPrev")) {
                if (NotificationService.getInstance() != null) {
                   NotificationService.getInstance().skipPrevMedia();
                }
                result.success(null);
            } else if (call.method.equals("hideMediaWidget")) {
                if (NotificationService.getInstance() != null) {
                    NotificationService.getInstance().hideMediaWidget();
                }
                result.success(null);
            } else if (call.method.equals("requestInitialState")) {
                if (NotificationService.getInstance() != null) {
                    NotificationService.getInstance().pushCurrentMediaStateToFlutter();
                }
                result.success(null);
            } else {
                result.notImplemented();
            }
        });
    }


    
    // Updates Flutter with current media
    public void updateMediaState(String title, String artist, byte[] albumArt, boolean isPlaying, String appName, byte[] appIcon, long position, long duration, double speed) {
        if (channel != null) {
            runOnUiThread(() -> {
                channel.invokeMethod("updateMediaState", new java.util.HashMap<String, Object>() {{
                    put("title", title);
                    put("artist", artist);
                    put("albumArt", albumArt);
                    put("isPlaying", isPlaying);
                    put("appName", appName);
                    put("appIcon", appIcon);
                    put("position", position);
                    put("duration", duration);
                    put("speed", speed);
                }});
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activeInstance = null;
    }
}
