package io.github.pbanddev.tatamihome;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CameraTest {

    /** Maps a screen point into plan space the way the renderer's canvas transform does, in reverse. */
    private static float[] toPlan(float sx, float sy, double t, float w, float h, boolean moving) {
        float dx = sx - (w / 2 + Camera.panX(t, w, moving));
        float dy = sy - (h / 2 + Camera.panY(t, h, moving));
        double a = Math.toRadians(-Camera.ANGLE_DEG);
        float zoom = Camera.zoom(t, moving);
        return new float[] {
                (float) ((dx * Math.cos(a) - dy * Math.sin(a)) / zoom),
                (float) ((dx * Math.sin(a) + dy * Math.cos(a)) / zoom)
        };
    }

    private static void assertCovers(float w, float h, boolean moving) {
        float pw = Camera.planWidth(w, h), ph = Camera.planHeight(w, h);
        for (int i = 0; i < 2000; i++) {
            double t = i * 7.9 + (i % 2 == 0 ? 0 : 1_793_500_000.0);
            for (float[] corner : new float[][] {{0, 0}, {w, 0}, {0, h}, {w, h}}) {
                float[] p = toPlan(corner[0], corner[1], t, w, h, moving);
                assertTrue("corner " + corner[0] + "," + corner[1] + " leaves the plan at t=" + t,
                        Math.abs(p[0]) <= pw / 2 && Math.abs(p[1]) <= ph / 2);
            }
        }
    }

    @Test
    public void tiltedPlanAlwaysCoversTheWholeScreenAt1080p() {
        assertCovers(1920, 1080, true);
        assertCovers(1920, 1080, false);
    }

    @Test
    public void tiltedPlanAlwaysCoversTheWholeScreenAt720p() {
        assertCovers(1280, 720, true);
        assertCovers(1280, 720, false);
    }

    @Test
    public void zoomOnlyPullsInAndStaysGentle() {
        for (int i = 0; i < 5000; i++) {
            float z = Camera.zoom(i * 3.7, true);
            assertTrue("zoom " + z, z >= 1f && z <= 1.15f);
        }
        assertTrue(Camera.zoom(123.0, false) == 1f);
    }
}
