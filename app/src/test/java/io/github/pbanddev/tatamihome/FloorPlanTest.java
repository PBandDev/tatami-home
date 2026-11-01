package io.github.pbanddev.tatamihome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FloorPlanTest {

    private static final long SEED = 7L;
    private static final int DEPTH = 5;
    private static final float W = 2300f;
    private static final float H = 1800f;
    private static final float EPS = 0.05f;
    /** Epoch seconds in November 2026: the clock Home and the screensaver both read. */
    private static final double NOW = 1_793_500_000.0;

    private static Frame layout(FloorPlan plan, double t) {
        Frame frame = new Frame(plan.rooms);
        plan.layout(t, -W / 2, -H / 2, W, H, frame);
        return frame;
    }

    private static double[] times() {
        double[] out = new double[60];
        for (int i = 0; i < 50; i++) out[i] = i * 37.3;
        for (int i = 50; i < 60; i++) out[i] = NOW + (i - 50) * 911.7;
        return out;
    }

    @Test
    public void hasTwoToTheDepthRoomsAndOneDoorPerCut() {
        FloorPlan plan = new FloorPlan(SEED, DEPTH);
        Frame frame = layout(plan, 0);
        assertEquals(32, plan.rooms);
        assertEquals(32, frame.roomCount);
        assertEquals(31, frame.doorCount);
    }

    @Test
    public void roomsFillThePlanExactlyAtAnyTime() {
        FloorPlan plan = new FloorPlan(SEED, DEPTH);
        for (double t : times()) {
            Frame f = layout(plan, t);
            double area = 0;
            for (int i = 0; i < f.roomCount; i++) area += (double) f.roomW[i] * f.roomH[i];
            assertEquals("t=" + t, (double) W * H, area, W * H * 1e-4);
        }
    }

    @Test
    public void roomsNeverOverlapAndStayInsideThePlan() {
        FloorPlan plan = new FloorPlan(SEED, DEPTH);
        for (double t : times()) {
            Frame f = layout(plan, t);
            for (int i = 0; i < f.roomCount; i++) {
                assertTrue(f.roomW[i] > 0 && f.roomH[i] > 0);
                assertTrue(f.roomX[i] >= -W / 2 - EPS && f.roomX[i] + f.roomW[i] <= W / 2 + EPS);
                assertTrue(f.roomY[i] >= -H / 2 - EPS && f.roomY[i] + f.roomH[i] <= H / 2 + EPS);
                for (int j = i + 1; j < f.roomCount; j++) {
                    float ox = Math.min(f.roomX[i] + f.roomW[i], f.roomX[j] + f.roomW[j]) - Math.max(f.roomX[i], f.roomX[j]);
                    float oy = Math.min(f.roomY[i] + f.roomH[i], f.roomY[j] + f.roomH[j]) - Math.max(f.roomY[i], f.roomY[j]);
                    assertTrue("rooms " + i + " and " + j + " overlap at t=" + t, ox <= EPS || oy <= EPS);
                }
            }
        }
    }

    @Test
    public void everyCutStaysInItsSafeRange() {
        FloorPlan plan = new FloorPlan(SEED, DEPTH);
        for (double t : times()) {
            for (int s = 0; s < plan.rooms - 1; s++) {
                float r = plan.ratio(s, t);
                assertTrue("cut " + s + " = " + r, r >= FloorPlan.MIN_RATIO && r <= FloorPlan.MAX_RATIO);
            }
        }
    }

    @Test
    public void sameSeedAndTimeGiveTheSameFrame() {
        Frame a = layout(new FloorPlan(SEED, DEPTH), NOW);
        Frame b = layout(new FloorPlan(SEED, DEPTH), NOW);
        assertArrayEquals(a.roomX, b.roomX, 0f);
        assertArrayEquals(a.roomY, b.roomY, 0f);
        assertArrayEquals(a.roomW, b.roomW, 0f);
        assertArrayEquals(a.roomH, b.roomH, 0f);
        assertArrayEquals(a.roomColor, b.roomColor);
        assertArrayEquals(a.doorX, b.doorX, 0f);
        assertArrayEquals(a.doorY, b.doorY, 0f);
    }

    @Test
    public void wallsMoveOverTime() {
        FloorPlan plan = new FloorPlan(SEED, DEPTH);
        Frame a = layout(plan, NOW);
        Frame b = layout(plan, NOW + 2);
        assertNotEquals(a.roomW[0], b.roomW[0], 0.5f);
    }

    @Test
    public void everyDoorSitsOnAWallBetweenTwoRooms() {
        FloorPlan plan = new FloorPlan(SEED, DEPTH);
        for (double t : times()) {
            Frame f = layout(plan, t);
            for (int d = 0; d < f.doorCount; d++) {
                float x = f.doorX[d], y = f.doorY[d];
                boolean before = false, after = false;
                for (int i = 0; i < f.roomCount; i++) {
                    float left = f.roomX[i], right = left + f.roomW[i], top = f.roomY[i], bottom = top + f.roomH[i];
                    if (f.doorOnVerticalWall[d]) {
                        boolean spans = y >= top - EPS && y <= bottom + EPS;
                        before |= spans && Math.abs(right - x) < EPS;
                        after |= spans && Math.abs(left - x) < EPS;
                    } else {
                        boolean spans = x >= left - EPS && x <= right + EPS;
                        before |= spans && Math.abs(bottom - y) < EPS;
                        after |= spans && Math.abs(top - y) < EPS;
                    }
                }
                assertTrue("door " + d + " at t=" + t + " is not on a wall", before && after);
            }
        }
    }

    @Test
    public void neighbouringRoomsFromOneCutNeverShareAColour() {
        Frame f = layout(new FloorPlan(SEED, DEPTH), 0);
        for (int i = 0; i + 1 < f.roomCount; i += 2) {
            assertNotEquals(f.roomColor[i], f.roomColor[i + 1]);
        }
        for (int c : f.roomColor) assertTrue(c >= 0 && c < FloorPlan.PALETTE_SIZE);
    }
}
