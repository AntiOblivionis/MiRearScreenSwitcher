package com.tgwgroup.MiRearScreenSwitcher;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.Map;
import rikka.shizuku.Shizuku;

/** One UserService per app, with main-thread serialized ownership and bounded reconnects. */
final class TaskServiceClient {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ComponentName NAME = new ComponentName(
            "com.tgwgroup.MiRearScreenSwitcher", TaskService.class.getName());
    private static final Shizuku.UserServiceArgs ARGS = new Shizuku.UserServiceArgs(NAME)
            .tag("mrss-task-service").daemon(false).processNameSuffix("task_service").debuggable(false).version(8);
    private static final Map<ServiceConnection, IBinder> CLIENTS = new IdentityHashMap<>();
    private static IBinder binder;
    private static ServiceConnection connection;
    private static int failures;
    private static boolean listening;
    private static final Shizuku.OnBinderReceivedListener RECEIVED = () -> MAIN.post(() -> {
        failures = 0;
        connect();
    });
    private static final Shizuku.OnBinderDeadListener DEAD = () -> MAIN.post(TaskServiceClient::disconnected);
    private static final Shizuku.OnRequestPermissionResultListener PERMISSION = (code, result) -> MAIN.post(() -> {
        if (result == PackageManager.PERMISSION_GRANTED) { failures = 0; connect(); }
    });
    private static final Runnable RETRY = TaskServiceClient::connect;
    private static final Runnable TIMEOUT = () -> {
        if (binder == null) {
            detach(false);
            retry();
        }
    };

    static void bind(Shizuku.UserServiceArgs ignored, ServiceConnection client) {
        MAIN.post(() -> {
            // An explicit caller request may retry after an exhausted automatic retry budget.
            if (connection == null) failures = 0;
            if (!CLIENTS.containsKey(client)) CLIENTS.put(client, null);
            if (binder != null && !binder.isBinderAlive()) disconnected();
            if (!listening) {
                listening = true;
                Shizuku.addBinderReceivedListenerSticky(RECEIVED);
                Shizuku.addBinderDeadListener(DEAD);
                Shizuku.addRequestPermissionResultListener(PERMISSION);
            }
            if (binder != null && binder.isBinderAlive()) deliver(client);
            else connect();
        });
    }

    static void unbind(Shizuku.UserServiceArgs ignored, ServiceConnection client, boolean ignoredRemove) {
        MAIN.post(() -> {
            CLIENTS.remove(client);
            if (!CLIENTS.isEmpty()) return;
            MAIN.removeCallbacks(RETRY);
            MAIN.removeCallbacks(TIMEOUT);
            detach(true);
            binder = null;
            failures = 0;
            if (listening) {
                listening = false;
                Shizuku.removeBinderReceivedListener(RECEIVED);
                Shizuku.removeBinderDeadListener(DEAD);
                Shizuku.removeRequestPermissionResultListener(PERMISSION);
            }
        });
    }

    private static void deliver(ServiceConnection client) {
        if (CLIENTS.containsKey(client) && CLIENTS.get(client) != binder) {
            CLIENTS.put(client, binder);
            client.onServiceConnected(NAME, binder);
        }
    }

    private static void connect() {
        if (CLIENTS.isEmpty() || connection != null) return;
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
            MAIN.removeCallbacks(RETRY);
            ServiceConnection attempt = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    MAIN.post(() -> {
                        if (connection != this || CLIENTS.isEmpty()) return;
                        MAIN.removeCallbacks(TIMEOUT);
                        if (service == null || !service.isBinderAlive()) { detach(false); retry(); return; }
                        binder = service;
                        failures = 0;
                        for (ServiceConnection client : new ArrayList<>(CLIENTS.keySet())) deliver(client);
                    });
                }
                @Override public void onServiceDisconnected(ComponentName name) {
                    MAIN.post(() -> { if (connection == this) disconnected(); });
                }
            };
            connection = attempt;
            Shizuku.bindUserService(ARGS, attempt);
            MAIN.postDelayed(TIMEOUT, 5000);
        } catch (Exception e) {
            Log.w("TaskServiceClient", "Bind failed", e);
            detach(false);
            retry();
        }
    }

    private static void disconnected() {
        binder = null;
        MAIN.removeCallbacks(TIMEOUT);
        detach(false);
        for (ServiceConnection client : new ArrayList<>(CLIENTS.keySet())) {
            CLIENTS.put(client, null);
            client.onServiceDisconnected(NAME);
        }
        retry();
    }
    private static void retry() {
        MAIN.removeCallbacks(RETRY);
        if (!CLIENTS.isEmpty() && failures < 5) MAIN.postDelayed(RETRY, 1000L << failures++);
    }
    private static void detach(boolean remove) {
        ServiceConnection old = connection;
        connection = null;
        if (old != null) try { Shizuku.unbindUserService(ARGS, old, remove); }
        catch (Exception e) { Log.w("TaskServiceClient", "Unbind failed", e); }
    }
}
