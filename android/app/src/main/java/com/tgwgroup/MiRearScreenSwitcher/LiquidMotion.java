package com.tgwgroup.MiRearScreenSwitcher;

/** Sensor/device coordinates -> the rear display's right-handed, Y-up container frame. */
final class LiquidMotion {
    private LiquidMotion() { }

    static void map(float x, float y, float z, int rotation, boolean rear, float[] out) {
        // Looking at the back of the phone reverses X and Z, not Y (a proper 180-degree rotation).
        if (rear) { x = -x; z = -z; }
        switch (rotation & 3) {
            case 1: out[0] = y; out[1] = -x; break;
            case 2: out[0] = -x; out[1] = -y; break;
            case 3: out[0] = -y; out[1] = x; break;
            default: out[0] = x; out[1] = y;
        }
        out[2] = z;
    }

    static float finiteClamp(float value, float limit) {
        return Float.isFinite(value) ? Math.max(-limit, Math.min(limit, value)) : 0;
    }
}
