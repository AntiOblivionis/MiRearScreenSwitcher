package com.tgwgroup.MiRearScreenSwitcher;

import com.tgwgroup.MiRearScreenSwitcher.ITaskObserver;
import com.tgwgroup.MiRearScreenSwitcher.IRecordingObserver;
import android.os.Bundle;

interface ITaskService {
    void destroy() = 16777114;  // Shizuku required
    
    /**
     * 获取当前前台应用的包名和taskId
     * @return "package:taskId" 格式
     */
    String getCurrentForegroundApp() = 1;
    
    /**
     * 通过包名获取taskId
     * @param packageName 包名
     * @return taskId，失败返回-1
     */
    int getTaskIdByPackage(String packageName) = 2;
    
    /**
     * 移动任务到指定显示屏
     * @param taskId 任务ID
     * @param displayId 显示屏ID (0=主屏, 1=背屏)
     * @return 是否成功
     */
    boolean moveTaskToDisplay(int taskId, int displayId) = 3;
    
    /**
     * 在指定显示屏启动Activity（尝试保持常亮）
     * 注意：已移除主动点亮功能，仅尝试设置FLAG_KEEP_SCREEN_ON
     * @param displayId 显示屏ID (0=主屏, 1=背屏)
     * @return 是否成功
     */
    boolean launchWakeActivity(int displayId) = 4;
    
    /**
     * 强制将SystemUI（状态栏）固定在主屏幕
     * @return 是否成功
     */
    boolean forceStatusBarToMainDisplay() = 5;
    
    /**
     * 禁用小米背屏Launcher（防止挤占应用）
     * @return 是否成功
     */
    boolean disableSubScreenLauncher() = 6;
    
    /**
     * 启用小米背屏Launcher（恢复系统功能）
     * @return 是否成功
     */
    boolean enableSubScreenLauncher() = 7;
    
    /**
     * V9新增：检查Launcher进程是否在运行
     * @return true=进程在运行, false=进程不存在
     */
    boolean isLauncherProcessRunning() = 8;
    
    /**
     * V9新增：杀掉Launcher进程（轻量级操作）
     * @return 是否成功
     */
    boolean killLauncherProcess() = 9;
    
    // 删除未使用的wakeUpDisplay方法声明
    
    /**
     * V14.4新增：收回状态栏/控制中心
     * @return 是否成功
     */
    boolean collapseStatusBar() = 11;
    
    /**
     * V15新增：获取当前背屏DPI
     * @return DPI值
     */
    int getCurrentRearDpi() = 12;
    
    /**
     * V15新增：设置背屏DPI
     * @param dpi DPI值
     * @return 是否成功
     */
    boolean setRearDpi(int dpi) = 13;
    
    /**
     * V15新增：还原背屏DPI到默认值
     * @return 是否成功
     */
    boolean resetRearDpi() = 14;
    
    /**
     * V15新增：截取背屏画面
     * @return 是否成功
     */
    boolean takeRearScreenshot() = 15;
    
    /**
     * V15.1新增：检查任务是否在指定显示屏上运行
     * @param taskId 任务ID
     * @param displayId 显示屏ID (0=主屏, 1=背屏)
     * @return true=任务在指定屏幕运行, false=任务不存在或在其他屏幕
     */
    boolean isTaskOnDisplay(int taskId, int displayId) = 16;
    
    /**
     * V15.2新增：获取指定显示屏的前台应用
     * @param displayId 显示屏ID (0=主屏, 1=背屏)
     * @return "package:taskId" 格式，失败返回null
     */
    String getForegroundAppOnDisplay(int displayId) = 17;
    
    /**
     * V2.1新增：设置显示器旋转方向
     * @param displayId 显示器ID (0=主屏, 1=背屏)
     * @param rotation 旋转角度 (0=0°, 1=90°, 2=180°, 3=270°)
     * @return 是否成功
     */
    boolean setDisplayRotation(int displayId, int rotation) = 18;
    
    /**
     * V2.1新增：获取显示器当前旋转方向
     * @param displayId 显示器ID (0=主屏, 1=背屏)
     * @return 旋转角度 (0-3)，-1表示失败
     */
    int getDisplayRotation(int displayId) = 19;
    
    /**
     * V2.3新增：执行shell命令
     * @param cmd 要执行的命令
     * @return 是否成功
     */
    boolean executeShellCommand(String cmd) = 20;
    String executeShellCommandWithResult(String cmd) = 21;
    boolean registerTaskObserver(ITaskObserver observer) = 22;
    void unregisterTaskObserver(ITaskObserver observer) = 23;
    boolean wakeDisplay(int displayId) = 24;
    String getRearPhysicalDisplayId() = 25;
    String startRearRecording(IRecordingObserver observer) = 26;
    boolean stopRearRecording(String path) = 27;
    boolean launchRearActivity(String component, in Bundle extras) = 28;
    boolean setRearScreenWakeLock(IBinder owner, boolean enabled) = 29;
    int getRearInteractiveState() = 30;
}

