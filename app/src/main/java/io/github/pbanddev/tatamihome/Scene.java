package io.github.pbanddev.tatamihome;

/**
 * One frame of the floor plan, from the time to triangles and a camera matrix. The renderer on the TV
 * and the desktop tools (tools/PlanShot.java, tools/PlanStats.java) all run this, so they draw the same
 * frame. Pure Java.
 *
 * <p>The steps, in order: {@link FloorPlan} lays out rooms and walls, {@link RoomColours} paints them,
 * {@link Doors} and {@link Fixtures} add theirs, {@link SceneMesh} makes the triangles, and
 * {@link CameraMatrix} tilts, pans and zooms.
 */
public final class Scene {
    public final Look look;
    public final FloorPlan plan;
    public final Frame frame;
    public final SceneMesh mesh;
    /** Plan units to clip space, column-major 4x4. */
    public final float[] matrix = new float[16];
    private final int[] paints;
    private final Doors doors;
    private final Fixtures fixtures;

    public Scene(Look look) {
        this.look = look;
        plan = new FloorPlan(look);
        frame = new Frame(plan.rooms);
        mesh = new SceneMesh(plan.rooms, look);
        paints = RoomColours.deal(plan, look);
        doors = new Doors(look);
        fixtures = new Fixtures(look);
    }

    /** Fills {@link #frame}, {@link #mesh} and {@link #matrix} for time {@code t} on a w by h screen. */
    public void build(double t, int w, int h, boolean showDoors, boolean showFixtures, boolean moveCamera) {
        float angle = look.camera.angleDeg;
        float pw = Camera.planWidth(w, h, angle), ph = Camera.planHeight(w, h, angle);
        plan.layout(t, -pw / 2, -ph / 2, pw, ph, frame);
        for (int k = 0; k < frame.roomCount; k++) frame.roomColor[k] = paints[k];
        if (showDoors) doors.place(frame, t, h); else frame.doorCount = 0;
        if (showFixtures) fixtures.place(frame, h); else frame.fixtureCount = 0;
        mesh.build(frame, h);
        CameraMatrix.build(t, w, h, moveCamera, angle, matrix);
    }
}
