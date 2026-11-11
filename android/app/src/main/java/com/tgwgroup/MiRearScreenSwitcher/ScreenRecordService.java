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
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import rikka.shizuku.Shizuku;

/**
 * 背屏录屏服务
 * 功能：
 * 1. 显示悬浮窗（录制/停止按钮+关闭按钮）
 * 2. 录制背屏画面（screenrecord --display-id 1）
 * 3. 前台Service保活
 */
public class ScreenRecordService extends Service {
    private volatile boolean destroyed;

    private static final String TAG = "ScreenRecordService";
    private static final String CHANNEL_ID = "rear_screen_keeper"; // 使用MRSS内核服务通道
    private static final int NOTIFICATION_ID = 10004; // 避免与KeeperService冲突
    
    private static ScreenRecordService instance = null;
    private WindowManager windowManager;
    private View floatingView;
    private boolean isRecording = false;
    private boolean recordStarting, recordStopping, recordFinished, recordingConnectionLost;
    private long recordGeneration;
    private View recordControl;
    private android.widget.TextView closeControl;
    private final java.util.concurrent.ExecutorService recordWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private String currentVideoPath;
    private Handler wakeupHandler = new Handler(android.os.Looper.getMainLooper());
    
    // TaskService
    private volatile ITaskService taskService;
    private volatile java.util.concurrent.CountDownLatch serviceReady = new java.util.concurrent.CountDownLatch(1);
    private final Shizuku.UserServiceArgs serviceArgs = 
        new Shizuku.UserServiceArgs(new ComponentName("com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName()))
            .daemon(false)
            .processNameSuffix("task_service")
            .debuggable(false)
            .version(1);
    
    private final ServiceConnection taskServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            taskService = ITaskService.Stub.asInterface(binder);
            serviceReady.countDown();
            Log.d(TAG, "✓ TaskService connected");
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceReady = new java.util.concurrent.CountDownLatch(1);
            taskService = null;
            if (recordStarting || isRecording || recordStopping) {
                recordingConnectionLost = true;
                stopRearScreenWakeup();
                if (destroyed) TaskServiceClient.unbind(null, taskServiceConnection, false);
                else {
                    renderRecordingState();
                    Toast.makeText(ScreenRecordService.this, "录屏连接中断，保存状态未知；可关闭悬浮窗后重新连接", Toast.LENGTH_LONG).show();
                }
            }
        }
    };
    
    public static boolean isRunning() {
        return instance != null;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📹 ScreenRecordService onCreate");
        
        // 创建通知渠道
        createNotificationChannel();
        
        // 绑定TaskService
        bindTaskService();
        
        // 启动前台通知
        startForeground(NOTIFICATION_ID, buildNotification());
        Log.d(TAG, "✓ 前台Service已启动");
        
        // 显示悬浮窗
        try {
            showFloatingWindow();
        } catch (Exception e) {
            Log.e(TAG, "❌ 显示悬浮窗失败", e);
            e.printStackTrace();
            Toast.makeText(this, "显示悬浮窗失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        
        Log.d(TAG, "═══════════════════════════════════════");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 被杀后自动重启
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void bindTaskService() {
        if (!destroyed) TaskServiceClient.bind(null, taskServiceConnection);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "MRSS内核服务", NotificationManager.IMPORTANCE_LOW));
        }
    }
    
    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        
        // 统一使用MRSS内核服务的通知样式
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MRSS内核服务")
            .setContentText(recordingConnectionLost ? "录屏连接中断，保存状态未知" : "MRSS目前正在运行")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    /**
     * 显示悬浮窗
     */
    private void showFloatingWindow() {
        Log.d(TAG, "→ 准备显示悬浮窗");
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            Log.e(TAG, "❌ 无法获取WindowManager");
            return;
        }
        Log.d(TAG, "✓ WindowManager已获取");
        
        // 创建悬浮窗布局
        Log.d(TAG, "→ 创建悬浮窗视图");
        floatingView = createFloatingView();
        if (floatingView == null) {
            Log.e(TAG, "❌ 创建视图失败");
            return;
        }
        Log.d(TAG, "✓ 视图已创建");
        
        // 设置悬浮窗参数
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 200;
        
        Log.d(TAG, "→ 参数设置完成，准备添加视图");
        Log.d(TAG, "  TYPE: " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? "TYPE_APPLICATION_OVERLAY" : "TYPE_PHONE"));
        
        try {
            windowManager.addView(floatingView, params);
            Log.d(TAG, "✅ 悬浮窗已成功添加到WindowManager");
        } catch (Exception e) {
            Log.e(TAG, "❌ 添加悬浮窗失败", e);
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 创建悬浮窗视图
     */
    private View createFloatingView() {
        Log.d(TAG, "→ 开始创建悬浮窗布局");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(16, 16, 16, 16);
        layout.setGravity(android.view.Gravity.CENTER); // 上下左右居中
        
        Log.d(TAG, "✓ LinearLayout已创建");
        
        // 背景 - 四色渐变（与其他UI一致）
        GradientDrawable background = new GradientDrawable();
        background.setOrientation(GradientDrawable.Orientation.TL_BR);
        background.setColors(new int[]{
            0xE0FF9D88,  // 珊瑚橙（88%不透明）
            0xE0FFB5C5,  // 粉红（88%不透明）
            0xE0E0B5DC,  // 紫色（88%不透明）
            0xE0A8C5E5   // 蓝色（88%不透明）
        });
        background.setCornerRadius(60);
        layout.setBackground(background);
        
        // 关闭按钮（×）- 先声明
        final android.widget.TextView closeButton = new android.widget.TextView(this);
        closeControl = closeButton;
        closeButton.setText("×");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(32);
        closeButton.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.gravity = android.view.Gravity.CENTER; // 上下居中
        closeParams.leftMargin = 24;
        closeButton.setLayoutParams(closeParams);
        
        closeButton.setOnClickListener(v -> {
            // 录制中不允许关闭
            if (!recordingConnectionLost && (isRecording || recordStarting || recordStopping)) {
                Toast.makeText(this, "请先停止录制", Toast.LENGTH_SHORT).show();
                return;
            }
            // 停止服务（关闭悬浮窗）
            stopSelf();
        });
        
        // 录制/停止按钮（圆形，红色）
        final View recordButton = new View(this);
        recordControl = recordButton;
        int buttonSize = 120;
        LinearLayout.LayoutParams recordParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        recordParams.gravity = android.view.Gravity.CENTER; // 上下居中
        recordButton.setLayoutParams(recordParams);
        
        // 初始状态：录制按钮（实心圆）
        updateRecordButtonState(recordButton, false);
        
        // 点击事件
        recordButton.setOnClickListener(v -> {
            if (recordStarting || recordStopping) return;
            if (isRecording) stopRecordingInternal(recordButton, closeButton); else startRecording();
        });
        
        layout.addView(recordButton);
        layout.addView(closeButton);
        
        Log.d(TAG, "✓ 按钮已添加到布局");
        
        // 拖动功能
        final WindowManager.LayoutParams[] params = new WindowManager.LayoutParams[1];
        layout.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (params[0] == null) {
                    params[0] = (WindowManager.LayoutParams) floatingView.getLayoutParams();
                }
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params[0].x;
                        initialY = params[0].y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        params[0].x = initialX + (int) (initialTouchX - event.getRawX());
                        params[0].y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params[0]);
                        return true;
                }
                return false;
            }
        });
        
        Log.d(TAG, "✓ 悬浮窗布局创建完成");
        return layout;
    }
    
    /**
     * 更新录制按钮状态
     */
    private void updateRecordButtonState(View button, boolean recording) {
        GradientDrawable drawable = new GradientDrawable();
        
        if (recording) {
            // 停止状态：方形
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(20);
            drawable.setColor(Color.RED);
            drawable.setSize(60, 60); // 方形内部稍小
        } else {
            // 录制状态：圆形
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.RED);
        }
        
        drawable.setStroke(6, Color.WHITE); // 白色边框
        button.setBackground(drawable);
    }
    
    /**
     * 确保TaskService连接
     */
    private boolean ensureTaskServiceConnected() {
        if (taskService != null) return true;
        java.util.concurrent.CountDownLatch ready = serviceReady;
        bindTaskService();
        try {
            ready.await(3, java.util.concurrent.TimeUnit.SECONDS);
            return taskService != null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * 持续唤醒背屏任务 - 录制期间防止背屏熄屏
     */

    
    /**
     * 启动背屏持续唤醒
     */
    private void startRearScreenWakeup() {
        RearWakeController.acquire(this, this, () -> true);
    }
    
    /**
     * 停止背屏持续唤醒
     */
    private void stopRearScreenWakeup() {
        RearWakeController.release(this);
    }
    
    /**
     * 开始录制
     */
    private void startRecording() {
        if (destroyed || recordStarting || isRecording) return;
        recordStarting = true;
        recordFinished = false;
        final long epoch = ++recordGeneration;
        renderRecordingState();
        IRecordingObserver observer = new IRecordingObserver.Stub() {
            @Override public void onRecordingStopped(String path, boolean saved) {
                wakeupHandler.post(() -> {
                    if (epoch != recordGeneration) return;
                    recordFinished = true;
                    recordingConnectionLost = false;
                    recordStarting = recordStopping = isRecording = false;
                    currentVideoPath = null;
                    stopRearScreenWakeup();
                    if (destroyed) {
                        TaskServiceClient.unbind(null, taskServiceConnection, false);
                    } else {
                        renderRecordingState();
                        Toast.makeText(ScreenRecordService.this, saved ? "录屏已保存到Movies文件夹" : "录屏未生成有效视频", Toast.LENGTH_LONG).show();
                    }
                });
            }
        };
        recordWorker.execute(() -> {
            String path = null;
            try {
                if (ensureTaskServiceConnected()) path = taskService.startRearRecording(observer);
            } catch (Exception e) { Log.w(TAG, "Recorder start failed", e); }
            final String startedPath = path;
            wakeupHandler.post(() -> {
                if (epoch != recordGeneration || recordFinished) return;
                recordStarting = false;
                currentVideoPath = startedPath;
                isRecording = startedPath != null;
                if (destroyed) {
                    if (isRecording && taskService != null) {
                        final ITaskService service = taskService;
                        new Thread(() -> {
                            try { service.stopRearRecording(startedPath); } catch (Exception ignored) { }
                        }, "MRSS-record-cleanup").start();
                    } else TaskServiceClient.unbind(null, taskServiceConnection, false);
                    return;
                }
                if (isRecording) startRearScreenWakeup();
                else Toast.makeText(this, "录屏启动失败，请检查 Shizuku 和系统录屏支持", Toast.LENGTH_LONG).show();
                renderRecordingState();
            });
        });
    }

    private void renderRecordingState() {
        if (destroyed) return;
        if (recordControl != null) {
            recordControl.setEnabled(!recordStarting && !recordStopping && !recordingConnectionLost);
            updateRecordButtonState(recordControl, isRecording);
        }
        if (closeControl != null) closeControl.setVisibility(!recordingConnectionLost && (isRecording || recordStarting || recordStopping) ? View.GONE : View.VISIBLE);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }
    
    /**
     * 停止录制（带按钮引用，用于更新状态）
     */
    private void stopRecordingInternal(final View recordButton, final android.widget.TextView closeButton) {
        if (!isRecording || recordStopping || currentVideoPath == null) return;
        recordStopping = true;
        renderRecordingState();
        String path = currentVideoPath;
        ITaskService service = taskService;
        recordWorker.execute(() -> {
            boolean accepted = false;
            try { if (service != null) accepted = service.stopRearRecording(path); }
            catch (Exception e) { Log.w(TAG, "Recorder stop failed", e); }
            final boolean stopped = accepted;
            wakeupHandler.post(() -> {
                if (!stopped && path.equals(currentVideoPath)) {
                    recordStopping = false;
                    renderRecordingState();
                    if (!destroyed) Toast.makeText(this, "无法停止录屏，请检查 Shizuku 连接", Toast.LENGTH_LONG).show();
                }
            });
        });
        // Completion is delivered by the exact child's exit callback, never by sleeping for a second.
    }
    
    /**
     * 停止录制（兼容方法）
     */
    private void stopRecording() {
        stopRecordingInternal(null, null);
    }
    
    @Override
    public void onDestroy() {
        destroyed = true;
        stopRearScreenWakeup();
        if (isRecording) stopRecording();
        if (recordingConnectionLost || (!isRecording && !recordStarting)) TaskServiceClient.unbind(null, taskServiceConnection, false);
        recordWorker.shutdown();
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) { }
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }
}

