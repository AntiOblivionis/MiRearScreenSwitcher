package android.content;
import android.os.IBinder;
public interface ServiceConnection { void onServiceConnected(ComponentName name,IBinder binder); void onServiceDisconnected(ComponentName name); }
