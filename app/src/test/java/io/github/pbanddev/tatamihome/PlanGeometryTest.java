package io.github.pbanddev.tatamihome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlanGeometryTest {
    private static final float DELTA = 1f;

    /** A frame holding the given rooms, each as x0, y0, x1, y1. */
    private static Frame rooms(float[]... boxes) {
        Frame f = new Frame(boxes.length);
        for (float[] b : boxes) {
            int k = f.roomCount++;
            f.roomX[k] = b[0];
            f.roomY[k] = b[1];
            f.roomW[k] = b[2] - b[0];
            f.roomH[k] = b[3] - b[1];
        }
        return f;
    }

    /** The clockwise windmill of a 100 x 100 square: top, right, bottom, left, then the centre. */
    private static final float[][] CLOCKWISE = {
            {0, 0, 70, 30}, {70, 0, 100, 70}, {30, 70, 100, 100}, {0, 30, 30, 100}, {30, 30, 70, 70}};

    private static float[][] mirrored(float[][] boxes) {
        float[][] out = new float[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            float[] b = boxes[i];
            out[i] = new float[] {100 - b[2], b[1], 100 - b[0], b[3]};
        }
        return out;
    }

    @Test
    public void findsTheRoomThatHoldsAPoint() {
        Frame f = rooms(CLOCKWISE);
        assertEquals(4, PlanGeometry.roomAt(f, 50, 50));
        assertEquals(0, PlanGeometry.roomAt(f, 10, 10));
        assertEquals(-1, PlanGeometry.roomAt(f, 150, 50));
    }

    @Test
    public void sharedEdgeIsTheOverlapOfTwoTouchingSides() {
        Frame f = rooms(new float[] {0, 0, 50, 100}, new float[] {50, 20, 100, 60}, new float[] {50, 60, 100, 100});
        float[] out = new float[4];
        assertEquals(40f, PlanGeometry.sharedEdge(f, 0, 1, 0.01f, out), 1e-4f);
        assertArrayEquals(new float[] {50, 20, 50, 60}, out, 1e-4f);
        assertEquals(50f, PlanGeometry.sharedEdge(f, 2, 1, 0.01f, out), 1e-4f);
        assertArrayEquals(new float[] {50, 60, 100, 60}, out, 1e-4f);
    }

    @Test
    public void roomsThatMeetOnlyAtACornerShareNoEdge() {
        Frame f = rooms(new float[] {0, 0, 50, 50}, new float[] {50, 50, 100, 100});
        assertEquals(0f, PlanGeometry.sharedEdge(f, 0, 1, 0.01f, new float[4]), 0f);
    }

    @Test
    public void theCentreOfAWindmillIsAWindmillCentreEitherWayRound() {
        assertTrue(PlanGeometry.isWindmillCentre(rooms(CLOCKWISE), 4, DELTA));
        assertTrue(PlanGeometry.isWindmillCentre(rooms(mirrored(CLOCKWISE)), 4, DELTA));
    }

    @Test
    public void roomsOnThePlanEdgeAreNeverWindmillCentres() {
        Frame f = rooms(CLOCKWISE);
        for (int k = 0; k < 4; k++) assertFalse(PlanGeometry.isWindmillCentre(f, k, DELTA));
    }

    @Test
    public void aRoomInAGridOrBetweenTwoBandsIsNoWindmillCentre() {
        float[][] grid = new float[9][];
        for (int i = 0; i < 9; i++) {
            float x = (i % 3) * 30, y = (i / 3) * 30;
            grid[i] = new float[] {x, y, x + 30, y + 30};
        }
        assertFalse("a + join at every corner", PlanGeometry.isWindmillCentre(rooms(grid), 4, DELTA));
        Frame bands = rooms(new float[] {0, 0, 100, 30}, new float[] {0, 30, 30, 70}, new float[] {30, 30, 70, 70},
                new float[] {70, 30, 100, 70}, new float[] {0, 70, 100, 100});
        assertFalse("T joins that do not take turns", PlanGeometry.isWindmillCentre(bands, 2, DELTA));
    }
}
