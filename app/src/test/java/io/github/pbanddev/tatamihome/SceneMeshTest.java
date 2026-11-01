package io.github.pbanddev.tatamihome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneMeshTest {

    @Test
    public void meshNeverOverflowsAndHoldsWholeTriangles() {
        float w = 1920, h = 1080;
        float pw = Camera.planWidth(w, h), ph = Camera.planHeight(w, h);
        FloorPlan plan = new FloorPlan(7L, 6);
        Frame f = new Frame(plan.rooms);
        SceneMesh mesh = new SceneMesh(plan.rooms);
        int most = 0;
        for (int i = 0; i < 200; i++) {
            double t = i * 17.7 + (i % 2 == 0 ? 0 : 1_793_500_000.0);
            plan.layout(t, -pw / 2, -ph / 2, pw, ph, f);
            Fixtures.place(f, h * 0.018f);
            mesh.build(f, t, h, true);
            assertTrue(mesh.floats <= mesh.data.length);
            assertEquals(0, mesh.floats % (3 * SceneMesh.FLOATS_PER_VERTEX));
            most = Math.max(most, mesh.floats);
        }
        assertTrue("mesh is empty", most > 0);
    }

    @Test
    public void doorsCanBeLeftOut() {
        FloorPlan plan = new FloorPlan(7L, 6);
        Frame f = new Frame(plan.rooms);
        plan.layout(0, -1000, -800, 2000, 1600, f);
        f.fixtureCount = 0;
        SceneMesh mesh = new SceneMesh(plan.rooms);
        mesh.build(f, 0, 1080, false);
        int without = mesh.floats;
        mesh.build(f, 0, 1080, true);
        assertTrue(mesh.floats > without);
    }
}
