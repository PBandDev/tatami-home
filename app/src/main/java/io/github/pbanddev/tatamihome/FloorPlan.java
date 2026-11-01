package io.github.pbanddev.tatamihome;

import java.util.Random;

/**
 * A floor plan made by cutting one rectangle in two, then each half in two, {@code depth} times
 * (a split tree). Every cut is stored as a ratio that slides on its own slow sine, so the room on
 * one side of a wall gains exactly the space its neighbour loses: no gaps, no overlaps.
 *
 * <p>Pure: the same seed and time give the same plan, so Home and the screensaver, both driven by
 * the wall clock, draw the same frame and the hand-off between them does not jump.
 */
public final class FloorPlan {
    public static final float MIN_RATIO = 0.25f;
    public static final float MAX_RATIO = 0.75f;
    public static final int PALETTE_SIZE = 12;

    private static final float AMPLITUDE = 0.12f;

    public final int rooms;
    private final int cuts;
    private final boolean[] vertical;
    private final float[] base, speed, phase;
    private final int[] leafColor;

    public FloorPlan(long seed, int depth) {
        rooms = 1 << depth;
        cuts = rooms - 1;
        vertical = new boolean[cuts];
        base = new float[cuts];
        speed = new float[cuts];
        phase = new float[cuts];
        Random random = new Random(seed);
        for (int n = 0; n < cuts; n++) {
            int level = 31 - Integer.numberOfLeadingZeros(n + 1);
            vertical[n] = level % 2 == 0;
            base[n] = 0.38f + 0.24f * random.nextFloat();
            speed[n] = 0.15f + 0.20f * random.nextFloat();
            phase[n] = (float) (2 * Math.PI * random.nextFloat());
        }
        // Leaves come out left to right; a step of 7 in a palette of 12 never repeats for two neighbours.
        int offset = random.nextInt(PALETTE_SIZE);
        leafColor = new int[rooms];
        for (int k = 0; k < rooms; k++) leafColor[k] = (offset + 7 * k) % PALETTE_SIZE;
    }

    /** Where cut {@code n} sits at time {@code t} (seconds), as a share of its rectangle. */
    public float ratio(int n, double t) {
        float r = base[n] + AMPLITUDE * (float) Math.sin(speed[n] * t + phase[n]);
        return Math.max(MIN_RATIO, Math.min(MAX_RATIO, r));
    }

    /** Fills {@code out} with the rooms and doors of the plan at time {@code t}, inside x, y, w, h. */
    public void layout(double t, float x, float y, float w, float h, Frame out) {
        out.roomCount = 0;
        out.doorCount = 0;
        place(0, t, x, y, w, h, out);
    }

    private void place(int n, double t, float x, float y, float w, float h, Frame out) {
        if (n >= cuts) {
            int k = out.roomCount++;
            out.roomX[k] = x;
            out.roomY[k] = y;
            out.roomW[k] = w;
            out.roomH[k] = h;
            out.roomColor[k] = leafColor[n - cuts];
            return;
        }
        float r = ratio(n, t);
        int d = out.doorCount++;
        out.doorPhase[d] = phase[n];
        out.doorOnVerticalWall[d] = vertical[n];
        if (vertical[n]) {
            float cut = w * r;
            place(2 * n + 1, t, x, y, cut, h, out);
            place(2 * n + 2, t, x + cut, y, w - cut, h, out);
            out.doorX[d] = x + cut;
            out.doorY[d] = y + h * 0.5f;
        } else {
            float cut = h * r;
            place(2 * n + 1, t, x, y, w, cut, out);
            place(2 * n + 2, t, x, y + cut, w, h - cut, out);
            out.doorX[d] = x + w * 0.5f;
            out.doorY[d] = y + cut;
        }
    }
}
