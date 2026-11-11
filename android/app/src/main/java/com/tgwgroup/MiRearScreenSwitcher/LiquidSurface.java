package com.tgwgroup.MiRearScreenSwitcher;

/** Bounded 2D free-surface dynamics for the original Canvas liquid. Coordinates use Y down. */
final class LiquidSurface {
    static final int POINTS = 65;
    static final float STEP = 1f / 120f;
    private static final int CAPACITY = POINTS * 2 + 16;
    private final float[] displacement = new float[POINTS], velocity = new float[POINTS];
    private final float[] acceleration = new float[POINTS], wave = new float[POINTS];
    private final float[] ax = new float[CAPACITY], ay = new float[CAPACITY];
    private final float[] bx = new float[CAPACITY], by = new float[CAPACITY];
    final float[] x = new float[CAPACITY], y = new float[CAPACITY];
    private float width = 1, height = 1, fill, angle, angularVelocity, accumulator, phase;
    private float inputX, inputY = 9.81f, gravityX, gravityY = 9.81f, previousGravity = 9.81f;
    private float nx, ny = 1, tx = 1, ty, span, depth, offset;
    private int count;
    private float area;

    void resize(float w, float h) {
        if (!Float.isFinite(w) || !Float.isFinite(h) || w <= 0 || h <= 0) return;
        width = w; height = h;
        rebuild();
    }

    void setFill(float value) {
        if (!Float.isFinite(value)) return;
        fill = Math.max(0, Math.min(1, value));
        rebuild();
    }

    void setGravity(float gx, float gy) {
        // Bad sensor samples retain the last good direction, never poison the integrator.
        if (!Float.isFinite(gx) || !Float.isFinite(gy)) return;
        inputX = LiquidMotion.finiteClamp(gx, 40);
        inputY = LiquidMotion.finiteClamp(gy, 40);
    }

    void resetClock() { accumulator = 0; }
    int count() { return count; }
    float areaFraction() { return area / (width * height); }
    float angle() { return angle; }
    float wavePhase() { return phase; }

    int advance(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0) return 0;
        accumulator = Math.min(accumulator + seconds, STEP * 8);
        int steps = 0;
        while (accumulator >= STEP && steps < 8) {
            step(); accumulator -= STEP; steps++;
        }
        rebuild();
        return steps;
    }

    private void step() {
        float blend = STEP / (.035f + STEP);
        gravityX += (inputX - gravityX) * blend;
        gravityY += (inputY - gravityY) * blend;
        float gravity = (float) Math.hypot(gravityX, gravityY);
        float strength = Math.min(2, gravity / 9.81f);
        // When the phone lies flat there is no reliable in-plane down direction. Hold it,
        // damp residual motion, and resume naturally when an in-plane force returns.
        float error = gravity < .6f ? 0 : wrap((float) Math.atan2(gravityX, gravityY) - angle);
        float angularAcceleration = 38 * strength * error - 7.5f * angularVelocity;
        angularVelocity = LiquidMotion.finiteClamp(angularVelocity + angularAcceleration * STEP, 7);
        angle = wrap(angle + angularVelocity * STEP);
        float impulse = LiquidMotion.finiteClamp((gravity - previousGravity) / STEP, 60);
        previousGravity = gravity;
        float limit = Math.min(45, Math.min(width, height) * .12f);
        for (int i = 0; i < POINTS; i++) {
            // Reflecting ends and coupled springs produce traveling waves with viscous decay.
            float left = displacement[i == 0 ? 1 : i - 1];
            float right = displacement[i == POINTS - 1 ? POINTS - 2 : i + 1];
            float u = (2f * i / (POINTS - 1)) - 1;
            acceleration[i] = 360 * (left + right - 2 * displacement[i])
                    - 24 * displacement[i] - 4.6f * velocity[i]
                    - angularAcceleration * u * 9 - impulse * (1 - 3 * u * u) * .45f;
        }
        for (int i = 0; i < POINTS; i++) {
            velocity[i] = LiquidMotion.finiteClamp(velocity[i] + acceleration[i] * STEP, 350);
            displacement[i] += velocity[i] * STEP;
            if (Math.abs(displacement[i]) > limit) {
                displacement[i] = Math.copySign(limit, displacement[i]); velocity[i] *= -.2f;
            }
        }
        phase = (phase + STEP * (float) Math.PI * 1.5f) % ((float) Math.PI * 2);
    }

    private static float wrap(float value) {
        return (float) Math.atan2(Math.sin(value), Math.cos(value));
    }

    private void rebuild() {
        nx = (float) Math.sin(angle); ny = (float) Math.cos(angle); tx = ny; ty = -nx;
        span = (Math.abs(tx) * width + Math.abs(ty) * height) * .5f;
        depth = (Math.abs(nx) * width + Math.abs(ny) * height) * .5f;
        if (fill <= 0) { count = 0; area = 0; return; }
        if (fill >= 1) {
            count = 4; area = width * height;
            x[0] = 0; y[0] = 0; x[1] = width; y[1] = 0;
            x[2] = width; y[2] = height; x[3] = 0; y[3] = height;
            offset = -depth; return;
        }
        float fade = Math.min(1, Math.min(fill, 1 - fill) * 16);
        for (int i = 0; i < POINTS; i++) {
            // Keep the old 8 px, two-period idle ripple; only its motion gains physics.
            wave[i] = fade * (displacement[i] + Math.min(8, Math.min(width, height) * .025f)
                    * (float) Math.sin(4 * Math.PI * i / (POINTS - 1) + phase));
        }
        // Solve the clipped polygon's actual area, including ripples and every corner.
        // This avoids a fill-level jump when turning through 90/180 degrees.
        float low = -depth - 64, high = depth + 64, target = width * height * fill;
        for (int i = 0; i < 19; i++) {
            offset = (low + high) * .5f;
            makePolygon();
            if (area > target) low = offset; else high = offset;
        }
        offset = (low + high) * .5f;
        makePolygon();
    }

    private void makePolygon() {
        for (int i = 0; i < POINTS; i++) {
            float u = span * (2f * i / (POINTS - 1) - 1);
            float v = offset + wave[i];
            ax[i] = width * .5f + tx * u + nx * v;
            ay[i] = height * .5f + ty * u + ny * v;
        }
        // Extend past the vessel then clip: the fill direction may be any of its four sides.
        float bottom = depth + 128;
        ax[POINTS] = width * .5f + tx * span + nx * bottom;
        ay[POINTS] = height * .5f + ty * span + ny * bottom;
        ax[POINTS + 1] = width * .5f - tx * span + nx * bottom;
        ay[POINTS + 1] = height * .5f - ty * span + ny * bottom;
        int n = POINTS + 2;
        n = clip(ax, ay, n, bx, by, 0, 0);
        n = clip(bx, by, n, ax, ay, 1, width);
        n = clip(ax, ay, n, bx, by, 2, 0);
        n = clip(bx, by, n, x, y, 3, height);
        count = n;
        double sum = 0;
        for (int i = 0, j = n - 1; i < n; j = i++) sum += (double) x[j] * y[i] - (double) x[i] * y[j];
        area = (float) Math.abs(sum * .5);
    }

    private static int clip(float[] sx, float[] sy, int n, float[] dx, float[] dy, int edge, float bound) {
        if (n == 0) return 0;
        int out = 0, prev = n - 1;
        float a = distance(sx[prev], sy[prev], edge, bound);
        for (int i = 0; i < n; i++) {
            float b = distance(sx[i], sy[i], edge, bound);
            if ((a >= 0) != (b >= 0)) {
                float t = a / (a - b);
                dx[out] = sx[prev] + t * (sx[i] - sx[prev]);
                dy[out++] = sy[prev] + t * (sy[i] - sy[prev]);
            }
            if (b >= 0) { dx[out] = sx[i]; dy[out++] = sy[i]; }
            prev = i; a = b;
        }
        return out;
    }

    private static float distance(float x, float y, int edge, float bound) {
        switch (edge) {
            case 0: return x;
            case 1: return bound - x;
            case 2: return y;
            default: return bound - y;
        }
    }

    // Bubbles rise against gravity inside the same surface; caller clips to the liquid path.
    void bubble(float along, float fraction, float[] out) {
        float u = (along * 2 - 1) * span;
        int index = Math.min(POINTS - 1, Math.max(0, Math.round((u / span + 1) * .5f * (POINTS - 1))));
        float top = fill >= 1 ? -depth : offset + wave[index];
        float bottom = depth;
        if (Math.abs(nx) > .0001f) bottom = Math.min(bottom, (width * .5f - Math.signum(nx) * tx * u) / Math.abs(nx));
        if (Math.abs(ny) > .0001f) bottom = Math.min(bottom, (height * .5f - Math.signum(ny) * ty * u) / Math.abs(ny));
        float v = top + Math.max(0, bottom - top) * fraction;
        out[0] = width * .5f + tx * u + nx * v;
        out[1] = height * .5f + ty * u + ny * v;
    }
}
