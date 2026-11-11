package android.os;
import java.util.*;
/** Deterministic event queue for the actual connection manager; no real Android timing claims. */
public final class Handler {
    private static long now, sequence;
    private static final class Entry { final long time, order; final Runnable task;
        Entry(long time, Runnable task) { this.time=time; this.task=task; this.order=sequence++; } }
    private static final PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingLong((Entry e)->e.time).thenComparingLong(e->e.order));
    public Handler(Looper ignored) { }
    public boolean post(Runnable task) { return postDelayed(task,0); }
    public boolean postDelayed(Runnable task,long delay) { queue.add(new Entry(now+delay,task)); return true; }
    public void removeCallbacks(Runnable task) { queue.removeIf(e->e.task==task); }
    public static void drain() { advance(0); }
    public static void advance(long duration) {
        long target=now+duration; int runs=0;
        while(!queue.isEmpty() && queue.peek().time<=target) {
            if (++runs>1000) throw new AssertionError("Unbounded event loop");
            Entry entry=queue.remove(); now=entry.time; entry.task.run();
        }
        now=target;
    }
}
