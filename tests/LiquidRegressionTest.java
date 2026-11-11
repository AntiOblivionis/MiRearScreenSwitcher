package com.tgwgroup.MiRearScreenSwitcher;

import java.io.PrintWriter;
import java.util.Locale;

public final class LiquidRegressionTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++; if (!ok) throw new AssertionError(message);
    }
    private static void run(LiquidSurface s, int frames) { for (int i = 0; i < frames; i++) s.advance(1f / 60); }
    private static LiquidSurface create(float fill) {
        LiquidSurface s = new LiquidSurface(); s.resize(904, 572); s.setFill(fill); return s;
    }
    private static boolean bounded(LiquidSurface s, float w, float h, float fill) {
        if (!Float.isFinite(s.areaFraction()) || Math.abs(s.areaFraction() - fill) > .00003f) return false;
        for (int i = 0; i < s.count(); i++) {
            if (!Float.isFinite(s.x[i]) || !Float.isFinite(s.y[i]) || s.x[i] < -.001f
                    || s.y[i] < -.001f || s.x[i] > w + .001f || s.y[i] > h + .001f) return false;
        }
        return true;
    }
    private static void state(LiquidSurface s, PrintWriter out, float seconds) {
        out.printf(Locale.ROOT, "{\"angle\":%.6f,\"area\":%.6f,\"phase\":%.6f,\"points\":[", s.angle(), s.areaFraction(), s.wavePhase());
        for (int i = 0; i < s.count(); i++) {
            if (i > 0) out.print(','); out.printf(Locale.ROOT, "[%.4f,%.4f]", s.x[i], s.y[i]);
        }
        out.print("],\"bubbles\":[");
        float[] p = new float[2]; float[] along = {.2f, .4f, .6f, .8f, .3f, .7f};
        for (int i = 0; i < 6; i++) {
            float fraction = (.16f * i + .12f - seconds * .12f) % 1; if (fraction < 0) fraction++;
            s.bubble(along[i], fraction, p);
            if (i > 0) out.print(','); out.printf(Locale.ROOT, "[%.4f,%.4f]", p[0], p[1]);
        }
        out.print("]}");
    }
    public static void main(String[] args) throws Exception {
        float[] out = new float[3];
        for (int rotation = 0; rotation < 4; rotation++) {
            LiquidMotion.map(2, 3, 4, rotation, true, out);
            check(Math.abs(out[0]*out[0]+out[1]*out[1]+out[2]*out[2]-29) < .001, "Mapping preserves force");
        }
        LiquidMotion.map(1, 2, 3, 0, true, out);
        check(out[0] == -1 && out[1] == 2 && out[2] == -3, "Back-facing sensor signs");
        LiquidSurface s = create(.42f); run(s, 90);
        check(bounded(s, 904, 572, .42f) && Math.abs(s.angle()) < .001, "Original upright fill");
        float initialAngle = s.angle(); s.setGravity(9.81f, 0); s.advance(1f / 60);
        check(s.angle() > initialAngle && s.angle() < .1f, "Tilt has inertia instead of snapping");
        run(s, 240);
        check(Math.abs(s.angle() - Math.PI / 2) < .005, "Gravity to the right settles on right wall");
        check(bounded(s, 904, 572, .42f), "Sideways area conservation");
        s.setGravity(0, -9.81f); run(s, 240);
        check(Math.abs(Math.abs(s.angle()) - Math.PI) < .005, "Inversion moves liquid to top");
        check(bounded(s, 904, 572, .42f), "Inverted area conservation");
        s.setGravity(0, 0); run(s, 300); float flatAngle = s.angle(); run(s, 300);
        check(Math.abs(s.angle() - flatAngle) < .0001, "Flat phone does not invent a down direction");
        s.setGravity(Float.NaN, Float.POSITIVE_INFINITY); run(s, 60);
        check(bounded(s, 904, 572, .42f), "Invalid sensor input stays finite");
        check(s.advance(30) <= 8, "Bounded catch-up after a stalled frame");
        check(s.advance(Float.NaN) == 0 && s.advance(-1) == 0, "Invalid frame times ignored");
        s.resetClock(); check(s.advance(0) == 0, "Resume does not advance hidden time");
        s.setFill(0); check(s.count() == 0 && s.areaFraction() == 0, "Empty means no fluid");
        s.setFill(1); check(s.count() == 4 && s.areaFraction() == 1, "Full means no wave-shaped empty corners");
        s.setFill(Float.NaN); check(s.areaFraction() == 1, "Invalid fill preserves last good value");
        s.setFill(.42f); s.resize(572, 904);
        check(bounded(s, 572, 904, .42f), "Resize conserves fill fraction");
        boolean stable = true;
        float[] levels = {.001f, .01f, .1f, .42f, .5f, .9f, .99f, .999f};
        for (float level : levels) {
            s.setFill(level);
            for (int i = 0; i < 360; i++) {
                float a = i * .05f;
                s.setGravity((float)Math.sin(a)*20, (float)Math.cos(a)*20); s.advance(1f / 60);
                stable &= bounded(s, 572, 904, level);
            }
        }
        check(stable, "Rapid repeated full rotations retain area/bounds at near-empty and near-full levels");
        LiquidSurface a = create(.42f), b = create(.42f);
        a.setGravity(5, 8); b.setGravity(5, 8);
        for (int i = 0; i < 180; i++) a.advance(1f / 30);
        for (int i = 0; i < 360; i++) b.advance(1f / 60);
        check(Math.abs(a.angle()-b.angle()) < .0001 && Math.abs(a.wavePhase()-b.wavePhase()) < .001,
                "Dynamics independent of 30/60 fps display cadence");
        a.setGravity(0, 9.81f); run(a, 600);
        check(Math.abs(a.angle()) < .0001, "Sloshing damps back to rest");
        float[] bubble = new float[2]; a.bubble(.2f, .5f, bubble);
        check(Math.abs(bubble[0] - 904 * .2f) < .1, "Original upright bubble horizontal placement");
        if (args.length > 0) {
            try (PrintWriter writer = new PrintWriter(args[0])) {
                LiquidSurface visual = create(.42f); run(visual, 60);
                writer.print("{\"width\":904,\"height\":572,\"states\":{\"upright\":"); state(visual, writer, 0);
                visual.setGravity(7, 7); run(visual, 240); writer.print(",\"tilted\":"); state(visual, writer, 0);
                visual.setGravity(9.81f, 0); run(visual, 240); writer.print(",\"sideways\":"); state(visual, writer, 0);
                visual.setGravity(0, -9.81f); run(visual, 240); writer.print(",\"inverted\":"); state(visual, writer, 0);
                writer.print("},\"frames\":["); visual = create(.42f);
                for (int i = 0; i < 240; i++) {
                    float t = i / 30f;
                    float angle = t < 1 ? 0 : t < 6 ? (t - 1) / 5 * (float) Math.PI * 2 : (float) Math.PI * 2;
                    visual.setGravity((float)Math.sin(angle)*9.81f, (float)Math.cos(angle)*9.81f);
                    visual.advance(1f / 30); if (i > 0) writer.print(','); state(visual, writer, t);
                }
                writer.print("]}");
            }
        }
        System.out.println("Liquid surface regression checks passed: " + checks);
    }
}
