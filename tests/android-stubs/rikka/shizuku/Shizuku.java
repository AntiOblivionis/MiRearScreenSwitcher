package rikka.shizuku;
import java.util.*;
import android.content.*;
public final class Shizuku {
    public interface OnBinderReceivedListener { void onBinderReceived(); }
    public interface OnBinderDeadListener { void onBinderDead(); }
    public interface OnRequestPermissionResultListener { void onRequestPermissionResult(int code,int result); }
    public static final List<ServiceConnection> attempts=new ArrayList<>();
    public static int removes, detaches;
    public static boolean running=true, permitted=true;
    public static OnRequestPermissionResultListener permission;
    public static final class UserServiceArgs {
        public UserServiceArgs(ComponentName name) { }
        public UserServiceArgs tag(String x) { return this; }
        public UserServiceArgs daemon(boolean x) { return this; }
        public UserServiceArgs processNameSuffix(String x) { return this; }
        public UserServiceArgs debuggable(boolean x) { return this; }
        public UserServiceArgs version(int x) { return this; }
    }
    public static void addBinderReceivedListenerSticky(OnBinderReceivedListener x) { }
    public static void addBinderDeadListener(OnBinderDeadListener x) { }
    public static void addRequestPermissionResultListener(OnRequestPermissionResultListener x) { permission=x; }
    public static void removeBinderReceivedListener(OnBinderReceivedListener x) { }
    public static void removeBinderDeadListener(OnBinderDeadListener x) { }
    public static void removeRequestPermissionResultListener(OnRequestPermissionResultListener x) { permission=null; }
    public static boolean pingBinder() { return running; }
    public static int checkSelfPermission() { return permitted?0:-1; }
    public static void bindUserService(UserServiceArgs args,ServiceConnection connection) { attempts.add(connection); }
    public static void unbindUserService(UserServiceArgs args,ServiceConnection connection,boolean remove) { if(remove) removes++; else detaches++; }
}
