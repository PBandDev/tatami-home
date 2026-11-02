package io.github.pbanddev.tatamihome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SceneMeshTest {

    @Test
    public void meshNeverOverflowsAndHoldsWholeTriangles() {
        Scene scene = new Scene(Look.DEFAULT);
        int most = 0;
        for (int i = 0; i < 200; i++) {
            double t = i * 17.7 + (i % 2 == 0 ? 0 : 1_793_500_000.0);
            scene.build(t, i % 3 == 0 ? 1280 : 1920, i % 3 == 0 ? 720 : 1080, true, true, true);
            assertTrue(scene.mesh.floats <= scene.mesh.data.length);
            assertEquals(0, scene.mesh.floats % (3 * SceneMesh.FLOATS_PER_VERTEX));
            most = Math.max(most, scene.mesh.floats);
        }
        assertTrue("mesh is empty", most > 0);
    }

    @Test
    public void doorsAndFixturesCanBeLeftOut() {
        Scene scene = new Scene(Look.DEFAULT);
        scene.build(0, 1920, 1080, false, false, true);
        int without = scene.mesh.floats;
        scene.build(0, 1920, 1080, true, false, true);
        int doors = scene.mesh.floats;
        scene.build(0, 1920, 1080, true, true, true);
        assertTrue(doors > without);
        assertTrue(scene.mesh.floats > doors);
    }
}
