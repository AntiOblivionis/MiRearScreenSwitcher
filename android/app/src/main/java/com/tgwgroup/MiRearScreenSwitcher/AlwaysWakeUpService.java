package com.tgwgroup.MiRearScreenSwitcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import rikka.shizuku.Shizuku;

/**
 * V3.5: 未投放应用时常亮服务
 * 根据背屏状态变化请求唤醒
 * ⚠️ 警告：可能导致烧屏和额外耗电
 */
public class AlwaysWakeUpService extends Service {
    private volatile boolean destroyed;

    private static final String TAG = "AlwaysWakeUpService";
    private static final int NOTIFICATION_ID = 1001; // 与其他Service共用ID
    
    private volatile ITaskService taskService;
    private Handler wakeupHandler;
    private Runnable wakeupRunnable;
    private boolean isRunning = false;
    private SharedPreferences prefs;
    
    private final Shizuku.UserServiceArgs serviceArgs = 
        new Shizuku.UserServiceArgs(new ComponentName("com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName()))
            .daemon(false)
            .processNameSuffix("always_wakeup_task_service")
            .debuggable(false)
            .version(1);
    
    private final ServiceConnection taskServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            taskService = ITaskService.Stub.asInterface(service);
            Log.d(TAG, "✓ TaskService connected");
            
            // TaskService连接后开始发送wakeup
            startWakeupLoop();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "⚠️ TaskService disconnected");
            taskService = null;
            
            // 断开后尝试重连
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "🔄 尝试重新绑定TaskService...");
                bindTaskService();
            }, 1000);
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "📱 onCreate");
        
        prefs = getSharedPreferences("mrss_settings", MODE_PRIVATE);
        wakeupHandler = new Handler(Looper.getMainLooper());
        
        // 创建前台通知
        createForegroundNotification();
        
        // 绑定TaskService
        bindTaskService();
    }
    
    private void bindTaskService() {
        if (!destroyed) TaskServiceClient.bind(null, taskServiceConnection);
    }
    
    private void createForegroundNotification() {
        String channelId = "mrss_core_service";
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "MRSS内核服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("MRSS目前正在运行");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }
        
        Notification notification = builder
            .setContentTitle("MRSS内核服务")
            .setContentText("MRSS目前正在运行")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
        
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "✓ 前台服务已启动");
    }
    
    private void startWakeupLoop() {
        if (destroyed || !prefs.getBoolean("always_wakeup_enabled", false)) { stopSelf(); return; }
        isRunning = true;
        RearWakeController.acquireKeepingBright(this, this, () -> prefs.getBoolean("always_wakeup_enabled", false));
    }
    
    private void stopWakeupLoop() {
        isRunning = false;
        RearWakeController.release(this);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        startWakeupLoop();
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        destroyed = true;
        TaskServiceClient.unbind(null, taskServiceConnection, false);

        Log.d(TAG, "🔴 onDestroy");
        
        stopWakeupLoop();
        
        // 解绑TaskService
        try {
            if (taskService != null) {
                TaskServiceClient.unbind(serviceArgs, taskServiceConnection, true);
                Log.d(TAG, "✓ TaskService unbound");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unbind TaskService: " + e.getMessage());
        }
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

