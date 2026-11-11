package com.tgwgroup.MiRearScreenSwitcher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact identities, shared by the framework backend and the shell compatibility fallback. */
final class TaskSnapshot {
    static final class Task {
        final int id, rootId, displayId;
        final String packageName;
        String componentName;
        final boolean visible;
        Task(int id, int rootId, int displayId, String packageName, boolean visible) {
            this.id = id;
            this.rootId = rootId;
            this.displayId = displayId;
            this.packageName = packageName;
            this.visible = visible;
        }
        String identity() { return packageName + ":" + id; }
    }
    final List<Task> tasks = new ArrayList<>();
    private static final Pattern ROOT = Pattern.compile("^\\s*(?:RootTask|Stack) id=(\\d+).*?\\bdisplayId=(\\d+)\\b");
    private static final Pattern TASK = Pattern.compile("^\\s*taskId=(\\d+):\\s+([A-Za-z0-9_.$]+)/([^\\s]+).*");

    static TaskSnapshot parse(String output) {
        TaskSnapshot result = new TaskSnapshot();
        int display = -1, root = -1;
        int rootStart = 0;
        boolean visible = true, recognized = false;
        for (String line : output.split("\\r?\\n")) {
            Matcher header = ROOT.matcher(line);
            if (header.find()) {
                java.util.Collections.reverse(result.tasks.subList(rootStart, result.tasks.size()));
                rootStart = result.tasks.size();
                root = Integer.parseInt(header.group(1));
                display = Integer.parseInt(header.group(2));
                visible = !line.contains("visible=false");
                recognized = true;
            } else if (line.trim().startsWith("RootTask") || line.trim().startsWith("Stack")) {
                throw new IllegalArgumentException("Unrecognized task header");
            } else {
                Matcher task = TASK.matcher(line);
                if (task.matches() && display >= 0) {
                    Task item = new Task(Integer.parseInt(task.group(1)), root, display,
                            task.group(2), visible && !line.contains("visible=false"));
                    item.componentName = normalizeComponent(task.group(2) + "/" + task.group(3));
                    result.tasks.add(item);
                } else if (line.trim().startsWith("taskId=")) {
                    throw new IllegalArgumentException("Unrecognized task entry");
                }
            }
        }
        java.util.Collections.reverse(result.tasks.subList(rootStart, result.tasks.size()));
        if (!recognized) throw new IllegalArgumentException("No task snapshot available");
        return result;
    }
    Task find(int id) {
        for (Task task : tasks) if (task.id == id) return task;
        return null;
    }
    Task foreground(int displayId) {
        for (Task task : tasks) if (task.displayId == displayId && task.visible) return task;
        return null;
    }
    boolean isSingleTaskRoot(Task target) {
        int count = 0;
        for (Task task : tasks) if (task.rootId == target.rootId) count++;
        return count == 1;
    }
    Task findComponent(String component) {
        String normalized = normalizeComponent(component);
        for (Task task : tasks) if (normalized.equals(task.componentName)) return task;
        return null;
    }
    static String normalizeComponent(String component) {
        int slash = component.indexOf('/');
        if (slash < 1) throw new IllegalArgumentException("Invalid component");
        String pkg = component.substring(0, slash), activity = component.substring(slash + 1);
        return pkg + "/" + (activity.startsWith(".") ? pkg + activity : activity);
    }
}
