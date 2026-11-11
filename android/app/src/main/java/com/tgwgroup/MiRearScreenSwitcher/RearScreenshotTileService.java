package com.tgwgroup.MiRearScreenSwitcher;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Screenshot work retains its service connection until the result is delivered. */
public class RearScreenshotTileService extends TileService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile ITaskService taskService;
    private boolean destroyed, listening, capturing;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            taskService = ITaskService.Stub.asInterface(binder);
        }
        @Override public void onServiceDisconnected(ComponentName name) { taskService = null; }
    };
    @Override public void onStartListening() {
        super.onStartListening();
        listening = true;
        setFeedback(null);
        TaskServiceClient.bind(null, connection);
    }
    @Override public void onStopListening() {
        listening = false;
        if (!capturing) TaskServiceClient.unbind(null, connection, false);
        super.onStopListening();
    }
    @Override public void onClick() {
        super.onClick();
        unlockAndRun(() -> {
            if (destroyed || capturing) return;
            final ITaskService service = taskService;
            if (service == null) {
                Toast.makeText(this, R.string.toast_service_not_ready, Toast.LENGTH_SHORT).show();
                TaskServiceClient.bind(null, connection);
                return;
            }
            capturing = true;
            worker.execute(() -> {
                boolean saved = false;
                try {
                    service.collapseStatusBar();
                    saved = service.takeRearScreenshot();
                } catch (Exception e) { Log.w("RearScreenshotTile", "Screenshot failed", e); }
                final boolean success = saved;
                main.post(() -> {
                    capturing = false;
                    if (!destroyed) {
                        setFeedback(success ? "✓ 已保存" : "✗ 截图失败");
                        Toast.makeText(this, success ? R.string.toast_screenshot_saved : R.string.toast_screenshot_failed,
                                Toast.LENGTH_SHORT).show();
                        main.postDelayed(() -> { if (!destroyed) setFeedback(null); }, 1000);
                    }
                    if (!listening) TaskServiceClient.unbind(null, connection, false);
                });
            });
        });
    }
    private void setFeedback(String message) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.setSubtitle(message);
        if (android.os.Build.VERSION.SDK_INT >= 30) tile.setStateDescription("");
        tile.updateTile();
    }
    @Override public void onDestroy() {
        destroyed = true;
        listening = false;
        main.removeCallbacksAndMessages(null);
        worker.shutdown();
        if (!capturing) TaskServiceClient.unbind(null, connection, false);
        super.onDestroy();
    }
}
