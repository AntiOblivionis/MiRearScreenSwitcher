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

import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

/**
 * Quick Settings Tile - 背屏录屏
 * 点击后显示/隐藏录屏悬浮窗
 */
public class RearScreenRecordTileService extends TileService {
    private static final String TAG = "RearScreenRecordTile";
    
    @Override
    public void onStartListening() {
        super.onStartListening();
        
        Tile tile = getQsTile();
        if (tile != null) {
            // 检查悬浮窗是否正在显示
            boolean isRecording = ScreenRecordService.isRunning();
            tile.setState(isRecording ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }
    
    @Override
    public void onClick() {
        super.onClick();
        
        unlockAndRun(() -> {
            // 检查悬浮窗权限
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "无悬浮窗权限");
                
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
                
                // 跳转到权限设置页面
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                
                return;
            }
            
            // 检查是否已在运行
            if (ScreenRecordService.isRunning()) {
                // 已有悬浮窗，收起悬浮窗（停止服务）
                stopService(new Intent(this, ScreenRecordService.class));
                Log.d(TAG, "✓ 录屏悬浮窗已关闭");
                
                // 更新Tile状态
                Tile tile = getQsTile();
                if (tile != null) {
                    tile.setState(Tile.STATE_INACTIVE);
                    tile.updateTile();
                }
            } else {
                // 启动录屏服务（显示悬浮窗）
                Intent intent = new Intent(this, ScreenRecordService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                
                Log.d(TAG, "✓ 录屏悬浮窗已启动");
                
                // 更新Tile状态
                Tile tile = getQsTile();
                if (tile != null) {
                    tile.setState(Tile.STATE_ACTIVE);
                    tile.updateTile();
                }
            }
        });
    }
}






