package com.tgwgroup.MiRearScreenSwitcher;

import java.io.File;
import java.util.concurrent.TimeoutException;

/** Dependency-free executable tests against the production parser and subprocess runner. */
public final class CoreRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(String input) {
        try { TaskSnapshot.parse(input); throw new AssertionError("Accepted invalid task snapshot: " + input); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static String[] child(String mode) {
        return new String[]{new File(System.getProperty("java.home"), "bin/java").getPath(), "-cp",
                System.getProperty("java.class.path"), CoreRegressionTest.class.getName(), mode};
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            switch (args[0]) {
                case "pipes":
                    for (int i = 0; i < 200000; i++) { System.err.print('e'); System.out.print('o'); }
                    return;
                case "fail": System.err.print("intentional failure"); System.exit(7); return;
                case "sleep": Thread.sleep(30000); return;
                default: System.out.print("complete"); return;
            }
        }
        TaskSnapshot tasks = TaskSnapshot.parse("RootTask id=8 bounds=[0,0][1,1] displayId=10 visible=true\n"
                + "  taskId=123: com.example.other/.Main\n"
                + "RootTask id=7 bounds=[0,0][1,1] displayId=1 visible=true\n"
                + "  taskId=12: com.example.app/.Main\n"
                + "RootTask id=9 displayId=1 visible=false\n  taskId=88: com.example.hidden/.Main\n");
        check(tasks.find(1) == null, "task IDs must not match prefixes");
        check(tasks.find(12).displayId == 1, "exact display ID");
        check(tasks.find(123).displayId == 10, "display 10 must not match display 1");
        check(tasks.foreground(1).identity().equals("com.example.app:12"), "rear foreground identity");
        check(tasks.foreground(2) == null, "empty display");
        check(tasks.isSingleTaskRoot(tasks.find(12)), "one task root movable");
        check(tasks.findComponent("com.example.app/com.example.app.Main").id == 12, "normalize activity component");
        check(tasks.findComponent("com.example.app/.Other") == null, "another activity in the same package is not the target");
        TaskSnapshot stacked = TaskSnapshot.parse("  RootTask id=3 displayId=1 visible=true\n"
                + " taskId=1: com.example.bottom/.Main\n taskId=2: com.example.top/.Main\n");
        check(stacked.foreground(1).id == 2, "AOSP child arrays are bottom-to-top");
        check(!stacked.isSingleTaskRoot(stacked.find(1)), "do not move other apps in organized roots");
        TaskSnapshot invisible = TaskSnapshot.parse("RootTask id=4 displayId=1 visible=false\n taskId=4: com.example.app/.Main\n");
        check(invisible.foreground(1) == null && invisible.find(4) != null, "existence is not foreground visibility");
        rejects(""); rejects("Permission Denial: getAllRootTaskInfos");
        rejects("RootTask malformed\n taskId=1: com.example/.Main");
        rejects("RootTask id=1 displayId=1\n taskId=broken: com.example/.Main");
        String dump = "DisplayViewport{type=INTERNAL, displayId=10, uniqueId='local:111', deviceWidth=100}\n"
                + "DisplayViewport{type=INTERNAL, displayId=1, uniqueId='local:18446744073709551615', deviceWidth=900}";
        check("18446744073709551615".equals(PhysicalDisplayId.fromDump(dump, 1)), "physical ID keeps unsigned 64 bits");
        check(PhysicalDisplayId.fromDump(dump, 2) == null, "no guessed physical ID");
        check(PhysicalDisplayId.fromDump(dump + "DisplayViewport{displayId=1, uniqueId='local:222'}", 1) == null, "ambiguous display fails closed");
        check(PhysicalDisplayId.fromDump("DisplayViewport{displayId=1, uniqueId='virtual:1'}", 1) == null, "virtual display is not a physical panel");
        check(CommandRunner.quote("a'b").equals("'a'\\''b'"), "shell quoting");
        CommandRunner.Result success = CommandRunner.run(5000, child("ok"));
        check(success.succeeded() && success.output.contains("complete"), "successful child output");
        CommandRunner.Result fail = CommandRunner.run(5000, child("fail"));
        check(!fail.succeeded() && fail.exitCode == 7 && fail.output.contains("intentional failure"), "nonzero exit is not success");
        CommandRunner.Result pressure = CommandRunner.run(10000, child("pipes"));
        check(pressure.truncated && !pressure.succeeded() && pressure.output.length() == CommandRunner.OUTPUT_LIMIT,
                "both pipes drain without deadlock; bounded output is explicit");
        long start = System.nanoTime();
        try { CommandRunner.run(200, child("sleep")); throw new AssertionError("Missing timeout"); }
        catch (TimeoutException expected) { assertions++; }
        check((System.nanoTime() - start) < 3_000_000_000L, "timeout is bounded");
        Thread.currentThread().interrupt();
        try { CommandRunner.run(5000, child("sleep")); throw new AssertionError("Missing interrupt"); }
        catch (InterruptedException expected) { check(Thread.currentThread().isInterrupted(), "interrupt preserved"); }
        finally { Thread.interrupted(); }
        RearAnimationManager.startAnimation(RearAnimationManager.AnimationType.NOTIFICATION);
        long oldNotification = RearAnimationManager.getGeneration();
        RearAnimationManager.startAnimation(RearAnimationManager.AnimationType.NOTIFICATION);
        long newNotification = RearAnimationManager.getGeneration();
        check(!RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.NOTIFICATION, oldNotification),
                "old same-type activity cannot finish its replacement");
        check(RearAnimationManager.isCurrent(RearAnimationManager.AnimationType.NOTIFICATION, newNotification), "replacement remains current");
        RearAnimationManager.startAnimation(RearAnimationManager.AnimationType.CHARGING);
        long charging = RearAnimationManager.getGeneration();
        check(!RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.NOTIFICATION, newNotification), "old notification cannot end charging");
        check(RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.CHARGING, charging), "current owner can finish");
        check(!RearAnimationManager.endAnimation(RearAnimationManager.AnimationType.CHARGING, charging), "finish is consumed once");
        File sh = new File(System.getProperty("os.name").startsWith("Windows") ? "C:/Program Files/Git/bin/bash.exe" : "/bin/sh");
        if (sh.isFile()) {
            String notification = "notice $(printf injected) `printf injected`; \"'\\ end";
            // A script file avoids Windows/MSYS command-line quoting, which Android does not use.
            java.nio.file.Path script = java.nio.file.Files.createTempFile("mrss-quote-", ".sh");
            try {
                java.nio.file.Files.writeString(script, "printf %s " + CommandRunner.quote(notification));
                CommandRunner.Result quoted = CommandRunner.run(5000, sh.getPath(), script.toString().replace('\\', '/'));
                check(quoted.succeeded() && quoted.output.equals(notification), "notification content cannot become shell syntax: exit=" + quoted.exitCode + " output=[" + quoted.output + "]");
            } finally { java.nio.file.Files.deleteIfExists(script); }
        }
        System.out.println("PASS: " + assertions + " core regression assertions");
    }
}
