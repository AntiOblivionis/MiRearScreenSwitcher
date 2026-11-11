package com.tgwgroup.MiRearScreenSwitcher;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/** Only used in the shell/root UserService, never in the ordinary app process. */
final class FrameworkTasks {
    private final Object service;
    private final Class<?> api;
    private Object listener;

    FrameworkTasks() throws Exception {
        api = Class.forName("android.app.IActivityTaskManager");
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "activity_task");
        service = Class.forName("android.app.IActivityTaskManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        if (service == null) throw new IllegalStateException("Activity task manager unavailable");
    }

    TaskSnapshot snapshot() throws Exception {
        List<?> roots = (List<?>) api.getMethod("getAllRootTaskInfos").invoke(service);
        if (roots == null) throw new IllegalStateException("No task snapshot");
        TaskSnapshot snapshot = new TaskSnapshot();
        for (Object root : roots) {
            Class<?> type = root.getClass();
            int rootId = type.getField("taskId").getInt(root);
            int displayId = type.getField("displayId").getInt(root);
            boolean visible = type.getField("visible").getBoolean(root);
            int[] ids = (int[]) type.getField("childTaskIds").get(root);
            String[] names = (String[]) type.getField("childTaskNames").get(root);
            ComponentName top = (ComponentName) type.getField("topActivity").get(root);
            if (ids == null || names == null || ids.length != names.length) {
                throw new IllegalStateException("Unsupported root task layout");
            }
            // RootTaskInfo child arrays are bottom-to-top; roots themselves are top-to-bottom.
            for (int i = ids.length - 1; i >= 0; --i) {
                ComponentName component = names[i] == null ? null : ComponentName.unflattenFromString(names[i]);
                String pkg = i == ids.length - 1 && top != null ? top.getPackageName()
                        : component == null ? names[i] : component.getPackageName();
                if (pkg == null || !pkg.matches("[A-Za-z0-9_.$]+")) {
                    throw new IllegalStateException("Unrecognized child task; cannot safely move its root");
                }
                {
                    TaskSnapshot.Task item = new TaskSnapshot.Task(ids[i], rootId, displayId, pkg, visible);
                    ComponentName activity = i == ids.length - 1 && top != null ? top : component;
                    item.componentName = activity == null ? null : activity.flattenToString();
                    snapshot.tasks.add(item);
                }
            }
        }
        return snapshot;
    }

    void moveRoot(int rootId, int displayId) throws Exception {
        api.getMethod("moveRootTaskToDisplay", int.class, int.class).invoke(service, rootId, displayId);
    }

    synchronized void observe(Runnable changed) throws Exception {
        if (listener != null) return;
        Class<?> listenerApi = Class.forName("android.app.ITaskStackListener");
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code >= FIRST_CALL_TRANSACTION && code <= LAST_CALL_TRANSACTION) {
                    data.enforceInterface("android.app.ITaskStackListener");
                    // Treat callbacks as invalidations. No ROM-specific parcel layout or transaction ID.
                    changed.run();
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        Object proxy = Proxy.newProxyInstance(listenerApi.getClassLoader(), new Class<?>[]{listenerApi},
                (object, method, args) -> {
                    if (method.getName().equals("asBinder")) return callback;
                    if (method.getName().equals("toString")) return "MRSS task observer";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(object);
                    if (method.getName().equals("equals")) return object == args[0];
                    return null;
                });
        api.getMethod("registerTaskStackListener", listenerApi).invoke(service, proxy);
        listener = proxy;
    }

    synchronized void stopObserving() throws Exception {
        if (listener == null) return;
        api.getMethod("unregisterTaskStackListener", Class.forName("android.app.ITaskStackListener"))
                .invoke(service, listener);
        listener = null;
    }
}
