package com.tgwgroup.MiRearScreenSwitcher;

import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.WorkSource;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Privileged, display-group-scoped screen lock. Both processes own its lifetime. */
final class RearPowerControl {
    private static final String TAG = "RearPowerControl";
    private final Object power;
    private final Method acquire;
    private final Method release;
    private final Method interactive;
    private final Object displays;
    private final Method getDisplayInfo;
    private final Field displayGroupId;
    private IBinder owner;
    private IBinder token;
    private IBinder.DeathRecipient ownerDeath;

    RearPowerControl() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        IBinder powerBinder = (IBinder) getService.invoke(null, "power");
        IBinder displayBinder = (IBinder) getService.invoke(null, "display");
        if (powerBinder == null || displayBinder == null) throw new IllegalStateException("Power or display service unavailable");

        Class<?> powerApi = Class.forName("android.os.IPowerManager");
        Class<?> powerStub = Class.forName("android.os.IPowerManager$Stub");
        power = powerStub.getMethod("asInterface", IBinder.class).invoke(null, powerBinder);
        Class<?> callback = Class.forName("android.os.IWakeLockCallback");
        acquire = powerApi.getMethod("acquireWakeLock", IBinder.class, int.class, String.class,
                String.class, WorkSource.class, String.class, int.class, callback);
        release = powerApi.getMethod("releaseWakeLock", IBinder.class, int.class);
        Method query;
        try { query = powerApi.getMethod("isDisplayInteractive", int.class); }
        catch (NoSuchMethodException e) { query = null; }
        interactive = query;

        Class<?> displayApi = Class.forName("android.hardware.display.IDisplayManager");
        Class<?> displayStub = Class.forName("android.hardware.display.IDisplayManager$Stub");
        displays = displayStub.getMethod("asInterface", IBinder.class).invoke(null, displayBinder);
        getDisplayInfo = displayApi.getMethod("getDisplayInfo", int.class);
        displayGroupId = Class.forName("android.view.DisplayInfo").getField("displayGroupId");
    }

    private boolean hasIndependentRearGroup() throws Exception {
        Object primary = getDisplayInfo.invoke(displays, 0);
        Object rear = getDisplayInfo.invoke(displays, 1);
        if (primary == null || rear == null) return false;
        int primaryGroup = displayGroupId.getInt(primary);
        int rearGroup = displayGroupId.getInt(rear);
        return primaryGroup >= 0 && rearGroup >= 0 && primaryGroup != rearGroup;
    }

    synchronized boolean setHeld(IBinder newOwner, boolean hold) {
        if (newOwner == null) return false;
        if (!hold) {
            if (owner != null && !owner.equals(newOwner)) return false;
            return releaseLocked();
        }
        if (token != null && newOwner.equals(owner)) return true;
        if (!releaseLocked()) return false;
        try {
            if (!hasIndependentRearGroup()) {
                Log.w(TAG, "Rear display does not have a verified independent power group");
                return false;
            }
            IBinder newToken = new Binder();
            IBinder.DeathRecipient death = () -> {
                synchronized (RearPowerControl.this) {
                    if (newOwner.equals(owner)) releaseLocked();
                }
            };
            newOwner.linkToDeath(death, 0);
            try {
                acquire.invoke(power, newToken, PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                        "MRSS::rear-screen-on", "com.android.shell", null, null, 1, null);
            } catch (Exception e) {
                try { release.invoke(power, newToken, 0); } catch (Exception ignored) { }
                newOwner.unlinkToDeath(death, 0);
                throw e;
            }
            owner = newOwner;
            token = newToken;
            ownerDeath = death;
            Log.i(TAG, "Rear-only screen wake lock acquired");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Cannot acquire rear-only screen wake lock", e);
            return false;
        }
    }

    private boolean releaseLocked() {
        if (token == null) return true;
        try {
            release.invoke(power, token, 0);
            if (owner != null && ownerDeath != null) owner.unlinkToDeath(ownerDeath, 0);
            owner = null;
            token = null;
            ownerDeath = null;
            Log.i(TAG, "Rear-only screen wake lock released");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Cannot release rear-only screen wake lock", e);
            return false;
        }
    }

    synchronized void close() { releaseLocked(); }

    int rearInteractiveState() {
        if (interactive == null) return -1;
        try { return Boolean.TRUE.equals(interactive.invoke(power, 1)) ? 1 : 0; }
        catch (Exception e) { Log.w(TAG, "Rear power state unavailable", e); return -1; }
    }
}
