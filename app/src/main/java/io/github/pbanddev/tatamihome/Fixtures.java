package io.github.pbanddev.tatamihome;

/**
 * The small plan symbols: a tub, a toilet, a flight of stairs. Each sits in the
 * top-left corner of the same room every frame, so it travels with its room. When the room gets
 * too small the symbol shrinks with it instead of crossing a wall, and below a quarter of its
 * size it is left out.
 */
public final class Fixtures {
    public static final int TUB = 0;
    public static final int TOILET = 1;
    public static final int STAIRS = 2;
    /** Gap between a fixture and the walls, in units. */
    public static final float INSET = 0.6f;

    /** Width and height of each kind, in units. */
    private static final float[][] SIZE = {{2.0f, 1.0f}, {0.8f, 1.2f}, {1.2f, 2.2f}};
    private static final float SMALLEST = 0.25f;

    private Fixtures() {}

    /** Fills the fixture arrays of {@code f} from its rooms. {@code unit} is the fixture scale in plan units. */
    public static void place(Frame f, float unit) {
        f.fixtureCount = 0;
        float inset = INSET * unit;
        for (int i = 0; i < f.roomCount; i++) {
            if ((i * 5 + 3) % 7 != 0) continue;   // 1 room in 7, spread over the plan
            int kind = (i / 7) % 3;
            float needW = SIZE[kind][0] * unit, needH = SIZE[kind][1] * unit;
            float scale = Math.min(1f, Math.min((f.roomW[i] - 2 * inset) / needW, (f.roomH[i] - 2 * inset) / needH));
            if (scale < SMALLEST) continue;
            int k = f.fixtureCount++;
            f.fixtureKind[k] = kind;
            f.fixtureRoom[k] = i;
            f.fixtureX[k] = f.roomX[i] + inset;
            f.fixtureY[k] = f.roomY[i] + inset;
            f.fixtureW[k] = needW * scale;
            f.fixtureH[k] = needH * scale;
        }
    }
}
