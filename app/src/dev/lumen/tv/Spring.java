package dev.lumen.tv;

/**
 * A damped spring for motion. Unlike a fixed ease curve it carries velocity, so a focus that changes mid-move bends
 * smoothly instead of restarting. Slightly under-damped by default, for the gentle settle a clean interface has.
 */
final class Spring {
    float x, v, target;
    private final float freq, damping;

    Spring(float freq, float damping) { this.freq = freq; this.damping = damping; }

    Spring at(float value) { x = target = value; v = 0; return this; }

    /** Advances by dtMs; returns true while still moving. Sub-steps keep it stable on long frames. */
    boolean step(long dtMs) {
        if (x == target && v == 0) return false;
        float w = (float) (2 * Math.PI * freq), remaining = dtMs / 1000f;
        while (remaining > 0) {
            float dt = Math.min(remaining, 1 / 120f);
            v += (-w * w * (x - target) - 2 * damping * w * v) * dt;
            x += v * dt;
            remaining -= dt;
        }
        if (Math.abs(x - target) < .0015f * Math.max(1, Math.abs(target)) && Math.abs(v) < .02f * Math.max(1, Math.abs(target))) {
            x = target;
            v = 0;
            return false;
        }
        return true;
    }
}
