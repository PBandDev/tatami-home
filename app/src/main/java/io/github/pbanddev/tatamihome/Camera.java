package io.github.pbanddev.tatamihome;

/**
 * The view onto the plan: tilted by {@link #ANGLE_DEG}, drifting slowly and pulling in and back out
 * over a few minutes. The plan is sized so that, tilted and panned as far as the drift goes, it
 * still covers every corner of the screen; the zoom only ever pulls in, so it never shows an edge.
 */
public final class Camera {
    public static final float ANGLE_DEG = -20f;

    private static final float PAN = 0.03f;
    private static final double PAN_SPEED_X = 0.05;
    private static final double PAN_SPEED_Y = 0.037;
    private static final float ZOOM_RANGE = 0.15f;
    private static final double ZOOM_SPEED = 0.021;
    private static final float MARGIN = 1.02f;
    private static final double COS = Math.cos(Math.toRadians(ANGLE_DEG));
    private static final double SIN = Math.abs(Math.sin(Math.toRadians(ANGLE_DEG)));

    private Camera() {}

    public static float panX(double t, float w, boolean moving) {
        return moving ? (float) (PAN * w * Math.sin(t * PAN_SPEED_X)) : 0f;
    }

    public static float panY(double t, float h, boolean moving) {
        return moving ? (float) (PAN * h * Math.cos(t * PAN_SPEED_Y)) : 0f;
    }

    /** 1 is the widest view; the camera pulls in to 1.15 and back. */
    public static float zoom(double t, boolean moving) {
        return moving ? 1f + ZOOM_RANGE * 0.5f * (1f - (float) Math.cos(t * ZOOM_SPEED)) : 1f;
    }

    public static float planWidth(float w, float h) {
        return (float) (2 * MARGIN * (reachX(w) * COS + reachY(h) * SIN));
    }

    public static float planHeight(float w, float h) {
        return (float) (2 * MARGIN * (reachX(w) * SIN + reachY(h) * COS));
    }

    private static double reachX(float w) {
        return w / 2.0 + PAN * w;
    }

    private static double reachY(float h) {
        return h / 2.0 + PAN * h;
    }
}
