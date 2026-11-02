package io.github.pbanddev.tatamihome;

/**
 * Turns a {@link Frame} into coloured triangles for OpenGL: x, y in plan units, then r, g, b, a.
 * Rooms, then walls, then doors ({@link DoorShapes}), then fixtures ({@link FixtureShapes}), so later
 * shapes cover earlier ones. Sizes and colours come from the {@link Look}.
 */
public final class SceneMesh {
    public static final int FLOATS_PER_VERTEX = 6;

    /** A room's fill and its 4 wall quads. */
    private static final int VERTICES_PER_ROOM = 6 + 4 * 6;

    public final float[] data;
    public int floats;
    private final Look look;
    private float r, g, b;

    public SceneMesh(int rooms, Look look) {
        this.look = look;
        int vertices = rooms * VERTICES_PER_ROOM + Math.max(0, rooms - 1) * DoorShapes.MAX_VERTICES
                + rooms * FixtureShapes.MAX_VERTICES;
        data = new float[FLOATS_PER_VERTEX * vertices];
    }

    public void build(Frame f, float screenHeight) {
        floats = 0;
        int[] deck = look.colours.deck;
        for (int i = 0; i < f.roomCount; i++) {
            color(deck[f.roomColor[i]]);
            rect(f.roomX[i], f.roomY[i], f.roomW[i], f.roomH[i]);
        }
        float wall = screenHeight * look.walls.widthH;
        color(look.walls.color);
        for (int i = 0; i < f.roomCount; i++) outline(f.roomX[i], f.roomY[i], f.roomW[i], f.roomH[i], wall);
        for (int d = 0; d < f.doorCount; d++) DoorShapes.add(this, f, d, screenHeight, look);
        for (int k = 0; k < f.fixtureCount; k++) FixtureShapes.add(this, f, k, screenHeight, look);
    }

    /** The colour of the triangles that follow. */
    void color(int argb) {
        r = ((argb >> 16) & 0xFF) / 255f;
        g = ((argb >> 8) & 0xFF) / 255f;
        b = (argb & 0xFF) / 255f;
    }

    /** A box outline of the given width, centred on the box's edges. */
    void outline(float x, float y, float w, float h, float width) {
        float half = width / 2;
        rect(x - half, y - half, w + width, width);
        rect(x - half, y + h - half, w + width, width);
        rect(x - half, y - half, width, h + width);
        rect(x + w - half, y - half, width, h + width);
    }

    /** A straight line of the given width with flat ends at the end points. Always 2 triangles. */
    void line(float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.hypot(dx, dy);
        if (length == 0) {
            triangle(x0, y0, x0, y0, x0, y0);
            triangle(x0, y0, x0, y0, x0, y0);
            return;
        }
        float nx = -dy / length * width / 2, ny = dx / length * width / 2;
        triangle(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny);
        triangle(x0 + nx, y0 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny);
    }

    void rect(float x, float y, float w, float h) {
        triangle(x, y, x + w, y, x + w, y + h);
        triangle(x, y, x + w, y + h, x, y + h);
    }

    void triangle(float x0, float y0, float x1, float y1, float x2, float y2) {
        vertex(x0, y0);
        vertex(x1, y1);
        vertex(x2, y2);
    }

    private void vertex(float x, float y) {
        float[] d = data;
        int i = floats;
        d[i] = x;
        d[i + 1] = y;
        d[i + 2] = r;
        d[i + 3] = g;
        d[i + 4] = b;
        d[i + 5] = 1f;
        floats = i + FLOATS_PER_VERTEX;
    }
}
