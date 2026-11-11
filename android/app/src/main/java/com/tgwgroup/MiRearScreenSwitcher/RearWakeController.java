package com.tgwgroup.MiRearScreenSwitcher;

import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Shared, display-scoped wake requests and optional screen-on leases. */
final class RearWakeController implements DisplayManager.DisplayListener {
    private static final class Lease {
        final BooleanSupplier enabled;
        final boolean keepBright;
        Lease(BooleanSupplier enabled, boolean keepBright) {
            this.enabled = enabled;
            this.keepBright = keepBright;
        }
    }
    private static RearWakeController instance;
    private final Context appContext;
    private final DisplayManager displays;
    private final Handler worker;
    private final Map<Object, Lease> owners = new IdentityHashMap<>();
    private final IBinder wakeLockOwner = new Binder();
    private ITaskService service;
    private boolean active, wakeLockHeld, receiverRegistered;
    private long nextWake;
    private long nextLockAttempt;
    private int failures;
    private final BroadcastReceiver sleepReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!active || !hasBrightOwner()) return;
            Log.d("RearWakeController", "Sleep event received; checking rear power");
            worker.postDelayed(RearWakeController.this::check, 50);
            // On some firmware the broadcast arrives before the power group changes state.
            worker.postDelayed(RearWakeController.this::check, 250);
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            worker.post(() -> {
                service = ITaskService.Stub.asInterface(binder);
                wakeLockHeld = false;
                nextLockAttempt = 0;
                check();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            worker.post(() -> { service = null; wakeLockHeld = false; nextLockAttempt = 0; });
        }
    };

    private RearWakeController(Context context) {
        appContext = context.getApplicationContext();
        displays = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        HandlerThread thread = new HandlerThread("MRSS-rear-wake");
        thread.start();
        worker = new Handler(thread.getLooper());
    }
    private static synchronized RearWakeController get(Context context) {
        if (instance == null) instance = new RearWakeController(context);
        return instance;
    }
    static void acquire(Context context, Object owner, BooleanSupplier enabled) {
        acquire(context, owner, enabled, false);
    }
    static void acquireKeepingBright(Context context, Object owner, BooleanSupplier enabled) {
        acquire(context, owner, enabled, true);
    }
    private static void acquire(Context context, Object owner, BooleanSupplier enabled, boolean keepBright) {
        RearWakeController controller = get(context);
        controller.worker.post(() -> {
            controller.owners.put(owner, new Lease(enabled, keepBright));
            if (!controller.active) {
                controller.active = true;
                controller.failures = 0;
                controller.nextWake = 0;
                controller.displays.registerDisplayListener(controller, controller.worker);
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_DREAMING_STARTED);
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        controller.appContext.registerReceiver(controller.sleepReceiver, filter, null,
                                controller.worker, Context.RECEIVER_NOT_EXPORTED);
                    } else {
                        controller.appContext.registerReceiver(controller.sleepReceiver, filter, null, controller.worker);
                    }
                    controller.receiverRegistered = true;
                } catch (RuntimeException e) {
                    Log.w("RearWakeController", "Sleep broadcast registration unavailable", e);
                }
                TaskServiceClient.bind(null, controller.connection);
            }
            controller.check();
        });
    }
    static synchronized void release(Object owner) {
        if (instance == null) return;
        RearWakeController controller = instance;
        controller.worker.post(() -> { controller.owners.remove(owner); controller.check(); });
    }
    private final Runnable reconcile = this::check;
    private boolean hasBrightOwner() {
        for (Lease lease : owners.values()) if (lease.keepBright && lease.enabled.getAsBoolean()) return true;
        return false;
    }
    private void check() {
        worker.removeCallbacks(reconcile);
        owners.entrySet().removeIf(entry -> !entry.getValue().enabled.getAsBoolean());
        boolean wantBright = false;
        for (Lease lease : owners.values()) wantBright |= lease.keepBright;
        long now = SystemClock.elapsedRealtime();
        if (service != null && wakeLockHeld && !wantBright) {
            try {
                if (!service.setRearScreenWakeLock(wakeLockOwner, false)) {
                    worker.postDelayed(reconcile, 1000);
                    return;
                }
                wakeLockHeld = false;
                nextLockAttempt = 0;
            } catch (Exception e) {
                Log.w("RearWakeController", "Rear screen wake lock release failed", e);
                worker.postDelayed(reconcile, 1000);
                return;
            }
        }
        if (owners.isEmpty()) {
            if (active) {
                active = false;
                displays.unregisterDisplayListener(this);
                if (receiverRegistered) {
                    appContext.unregisterReceiver(sleepReceiver);
                    receiverRegistered = false;
                }
                TaskServiceClient.unbind(null, connection, false);
                service = null;
            }
            return;
        }
        if (service != null && wantBright && !wakeLockHeld && now >= nextLockAttempt) {
            try {
                wakeLockHeld = service.setRearScreenWakeLock(wakeLockOwner, true);
                if (wakeLockHeld) {
                    // A bright lock prevents timeout but does not wake a sleeping display.
                    service.wakeDisplay(1);
                    nextWake = SystemClock.elapsedRealtime() + 1000;
                } else Log.w("RearWakeController", "Rear-only screen wake lock unavailable");
            } catch (Exception e) {
                Log.w("RearWakeController", "Rear-only screen wake lock request failed", e);
            }
            nextLockAttempt = SystemClock.elapsedRealtime() + 15000;
        }
        Display rear = displays.getDisplay(1);
        int interactive = -1;
        if (wantBright && wakeLockHeld && service != null) {
            try { interactive = service.getRearInteractiveState(); }
            catch (Exception e) { Log.w("RearWakeController", "Rear power state query failed", e); }
        }
        boolean rearAsleep = interactive == 0
                || (interactive < 0 && rear != null && rear.getState() != Display.STATE_ON);
        now = SystemClock.elapsedRealtime();
        if (rearAsleep && service != null && now >= nextWake) {
            try {
                boolean accepted = service.wakeDisplay(1);
                failures = accepted ? 0 : Math.min(failures + 1, 5);
            } catch (Exception e) {
                failures = Math.min(failures + 1, 5);
                Log.w("RearWakeController", "Rear wake rejected", e);
            }
            nextWake = SystemClock.elapsedRealtime() + Math.min(30000L, 1000L << failures);
        }
        // Reconcile missing OEM display callbacks and preference changes without shell polling.
        long delay = rearAsleep && now < nextWake ? Math.min(15000, nextWake - now) : 15000;
        worker.postDelayed(reconcile, delay);
    }
    @Override public void onDisplayAdded(int id) { if (id == 1) check(); }
    @Override public void onDisplayRemoved(int id) { if (id == 1) check(); }
    @Override public void onDisplayChanged(int id) {
        if (id == 1) {
            worker.removeCallbacks(reconcile);
            worker.postDelayed(reconcile, Math.max(0, nextWake - SystemClock.elapsedRealtime()));
        }
    }
}
