package io.github.pbanddev.tatamihome;

/** One drawn moment of the floor plan, in plan units. Allocated once and refilled every frame. */
public final class Frame {
    public final float[] roomX, roomY, roomW, roomH;
    public final int[] roomColor;
    public final float[] doorX, doorY, doorPhase;
    public final boolean[] doorOnVerticalWall;
    public final float[] fixtureX, fixtureY, fixtureW, fixtureH;
    public final int[] fixtureKind, fixtureRoom;
    public int roomCount, doorCount, fixtureCount;

    public Frame(int rooms) {
        roomX = new float[rooms];
        roomY = new float[rooms];
        roomW = new float[rooms];
        roomH = new float[rooms];
        roomColor = new int[rooms];
        int doors = Math.max(0, rooms - 1);
        doorX = new float[doors];
        doorY = new float[doors];
        doorPhase = new float[doors];
        doorOnVerticalWall = new boolean[doors];
        fixtureX = new float[rooms];
        fixtureY = new float[rooms];
        fixtureW = new float[rooms];
        fixtureH = new float[rooms];
        fixtureKind = new int[rooms];
        fixtureRoom = new int[rooms];
    }
}
