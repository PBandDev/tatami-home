package io.github.pbanddev.tatamihome;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FixturesTest {

    private static final float W = 2300f;
    private static final float H = 1800f;
    private static final float UNIT = 19f;
    private static final float EPS = 0.05f;

    @Test
    public void everyFixtureStaysInsideItsRoomAtAnyTime() {
        FloorPlan plan = new FloorPlan(7L, 6);
        Frame f = new Frame(plan.rooms);
        for (int i = 0; i < 80; i++) {
            double t = i < 60 ? i * 23.1 : 1_793_500_000.0 + i * 401.3;
            plan.layout(t, -W / 2, -H / 2, W, H, f);
            Fixtures.place(f, UNIT);
            for (int k = 0; k < f.fixtureCount; k++) {
                int r = f.fixtureRoom[k];
                float inset = Fixtures.INSET * UNIT;
                assertTrue("fixture " + k + " leaves its room at t=" + t,
                        f.fixtureX[k] >= f.roomX[r] + inset - EPS
                                && f.fixtureY[k] >= f.roomY[r] + inset - EPS
                                && f.fixtureX[k] + f.fixtureW[k] <= f.roomX[r] + f.roomW[r] - inset + EPS
                                && f.fixtureY[k] + f.fixtureH[k] <= f.roomY[r] + f.roomH[r] - inset + EPS);
            }
        }
    }

    @Test
    public void tubToiletAndStairsAllShowUp() {
        FloorPlan plan = new FloorPlan(7L, 6);
        Frame f = new Frame(plan.rooms);
        plan.layout(0, -W / 2, -H / 2, W, H, f);
        Fixtures.place(f, UNIT);
        boolean[] seen = new boolean[3];
        for (int k = 0; k < f.fixtureCount; k++) seen[f.fixtureKind[k]] = true;
        assertTrue(seen[Fixtures.TUB] && seen[Fixtures.TOILET] && seen[Fixtures.STAIRS]);
    }
}
