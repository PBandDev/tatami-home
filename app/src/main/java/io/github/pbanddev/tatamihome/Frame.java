package io.github.pbanddev.tatamihome;

/**
 * One drawn moment of the floor plan, in plan units (screen pixels at zoom 1, before the tilt; y points
 * down). Allocated once and refilled every frame. The contract between the stages of {@link Scene}:
 *
 * <ul>
 *   <li><b>Rooms</b> (filled by {@link FloorPlan}): axis-aligned boxes that fill the plan. A room keeps its
 *       index in every frame. {@code roomMinW} and {@code roomMinH} are the smallest the room ever gets, at
 *       any time. {@code roomColor} is an index into {@link Look.Colours#deck} (set by {@link RoomColours}).</li>
 *   <li><b>Walls</b> (filled by {@link FloorPlan}): the wall pieces between rooms, on the wall's centre line,
 *       with {@code x0 <= x1} and {@code y0 <= y1}. A wall keeps its index in every frame, so the index is its
 *       id. There is always one wall fewer than rooms (a cut adds 1 room and 1 wall; a windmill adds 4 and 4).
 *       The plan's outer edge is not a wall.</li>
 *   <li><b>Doors</b> (filled by {@link Doors}): each hinges at {@code doorX, doorY} on the centre line of wall
 *       {@code doorWall}. {@code doorAlong} is +1 when the bar runs from the hinge toward +x (or +y on a
 *       vertical wall), -1 the other way. {@code doorSide} is +1 when the leaf swings toward +y (or +x on a
 *       vertical wall), -1 the other way. {@code doorOpening} is in radians: 0 shut, pi/2 open.</li>
 *   <li><b>Fixtures</b> (filled by {@link Fixtures}): a kind in room {@code fixtureRoom}, turned
 *       {@code fixtureTurn} quarter turns (0 to 3), filling the box {@code x, y, w, h} after the turn.</li>
 * </ul>
 */
public final class Frame {
    public final float[] roomX, roomY, roomW, roomH, roomMinW, roomMinH;
    public final int[] roomColor;

    public final float[] wallX0, wallY0, wallX1, wallY1;
    public final boolean[] wallVertical;

    public final int[] doorWall, doorAlong, doorSide;
    public final float[] doorX, doorY;
    public final double[] doorOpening;

    public final int[] fixtureKind, fixtureRoom, fixtureTurn;
    public final float[] fixtureX, fixtureY, fixtureW, fixtureH;

    public int roomCount, wallCount, doorCount, fixtureCount;

    public Frame(int rooms) {
        roomX = new float[rooms];
        roomY = new float[rooms];
        roomW = new float[rooms];
        roomH = new float[rooms];
        roomMinW = new float[rooms];
        roomMinH = new float[rooms];
        roomColor = new int[rooms];
        int walls = Math.max(0, rooms - 1);
        wallX0 = new float[walls];
        wallY0 = new float[walls];
        wallX1 = new float[walls];
        wallY1 = new float[walls];
        wallVertical = new boolean[walls];
        doorWall = new int[walls];
        doorAlong = new int[walls];
        doorSide = new int[walls];
        doorX = new float[walls];
        doorY = new float[walls];
        doorOpening = new double[walls];
        fixtureKind = new int[rooms];
        fixtureRoom = new int[rooms];
        fixtureTurn = new int[rooms];
        fixtureX = new float[rooms];
        fixtureY = new float[rooms];
        fixtureW = new float[rooms];
        fixtureH = new float[rooms];
    }
}
