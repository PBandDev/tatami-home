package io.github.pbanddev.tatamihome;

/**
 * Turns a {@link Frame} into coloured triangles for OpenGL: x, y in plan units, then r, g, b, a.
 * Rooms, then walls, then doors, then fixtures, so later shapes cover earlier ones. Sizes are
 * set from the screen height so a wall is about 4 px at 1080p.
 */
public final class SceneMesh {
    public static final int FLOATS_PER_VERTEX = 6;

    private static final int ARC_SEGMENTS = 10;
    private static final int OVAL_SEGMENTS = 16;
    private static final int[] PALETTE = {
            0xFF8A8F3A, 0xFFC9A43B, 0xFF86A0A6, 0xFFE0A05A, 0xFF5D7F5F, 0xFF6A5A3E,
            0xFFC0B98A, 0xFFB24A2E, 0xFF3D3A2C, 0xFFA9B56B, 0xFFD9D3B8, 0xFF7F6A4A
    };
    private static final int WALL = 0xFF15130E;
    private static final int DOOR = 0xFFE8D87A;
    private static final int PORCELAIN = 0xFFE9E4D0;

    public final float[] data;
    public int floats;
    private float r, g, b;

    public SceneMesh(int rooms) {
        int perRoom = 6 + 4 * 6;                                         // fill and 4 wall quads
        int perDoor = 3 * ARC_SEGMENTS + 6 * (ARC_SEGMENTS + 2);         // fan and its outline
        int perFixture = 6 + 4 * 6 + 3 * OVAL_SEGMENTS + 6 * OVAL_SEGMENTS; // the toilet is the largest
        data = new float[FLOATS_PER_VERTEX * (rooms * perRoom + Math.max(0, rooms - 1) * perDoor + rooms * perFixture)];
    }

    public void build(Frame f, double t, float screenHeight, boolean doors) {
        floats = 0;
        float wall = screenHeight * 0.0037f, line = screenHeight * 0.0012f, radius = screenHeight * 0.016f;
        for (int i = 0; i < f.roomCount; i++) {
            color(PALETTE[f.roomColor[i]]);
            rect(f.roomX[i], f.roomY[i], f.roomW[i], f.roomH[i]);
        }
        color(WALL);
        for (int i = 0; i < f.roomCount; i++) outline(f.roomX[i], f.roomY[i], f.roomW[i], f.roomH[i], wall);
        if (doors) {
            for (int d = 0; d < f.doorCount; d++) door(f, d, t, radius, line);
        }
        for (int k = 0; k < f.fixtureCount; k++) fixture(f, k, line);
    }

    private void door(Frame f, int d, double t, float radius, float line) {
        float x = f.doorX[d], y = f.doorY[d];
        double sweep = Math.toRadians(90 * (0.55 + 0.45 * Math.sin(1.3 * t + f.doorPhase[d])));
        double start = Math.toRadians(f.doorOnVerticalWall[d] ? -90 : 0);
        color(DOOR);
        for (int s = 0; s < ARC_SEGMENTS; s++) {
            double a0 = start + sweep * s / ARC_SEGMENTS, a1 = start + sweep * (s + 1) / ARC_SEGMENTS;
            triangle(x, y, x + radius * (float) Math.cos(a0), y + radius * (float) Math.sin(a0),
                    x + radius * (float) Math.cos(a1), y + radius * (float) Math.sin(a1));
        }
        color(WALL);
        line(x, y, x + radius * (float) Math.cos(start), y + radius * (float) Math.sin(start), line);
        line(x, y, x + radius * (float) Math.cos(start + sweep), y + radius * (float) Math.sin(start + sweep), line);
        for (int s = 0; s < ARC_SEGMENTS; s++) {
            double a0 = start + sweep * s / ARC_SEGMENTS, a1 = start + sweep * (s + 1) / ARC_SEGMENTS;
            line(x + radius * (float) Math.cos(a0), y + radius * (float) Math.sin(a0),
                    x + radius * (float) Math.cos(a1), y + radius * (float) Math.sin(a1), line);
        }
    }

    private void fixture(Frame f, int k, float line) {
        float x = f.fixtureX[k], y = f.fixtureY[k], w = f.fixtureW[k], h = f.fixtureH[k];
        switch (f.fixtureKind[k]) {
            case Fixtures.TUB: {
                color(PORCELAIN);
                rect(x, y, w, h);
                color(WALL);
                outline(x, y, w, h, line);
                float in = h * 0.18f;
                outline(x + in, y + in, w - 2 * in, h - 2 * in, line);
                break;
            }
            case Fixtures.TOILET: {
                float tank = h * 0.28f;
                color(PORCELAIN);
                rect(x, y, w, tank);
                oval(x + w * 0.08f, y + h * 0.24f, w * 0.84f, h * 0.76f, line);
                color(WALL);
                outline(x, y, w, tank, line);
                break;
            }
            default: {
                color(PORCELAIN);
                rect(x, y, w, h);
                color(WALL);
                outline(x, y, w, h, line);
                for (int s = 1; s < 7; s++) {
                    float yy = y + h * s / 7f;
                    line(x, yy, x + w, yy, line);
                }
            }
        }
    }

    /** A filled oval in porcelain with a wall-coloured rim. */
    private void oval(float x, float y, float w, float h, float line) {
        float cx = x + w / 2, cy = y + h / 2, rx = w / 2, ry = h / 2;
        for (int s = 0; s < OVAL_SEGMENTS; s++) {
            double a0 = 2 * Math.PI * s / OVAL_SEGMENTS, a1 = 2 * Math.PI * (s + 1) / OVAL_SEGMENTS;
            triangle(cx, cy, cx + rx * (float) Math.cos(a0), cy + ry * (float) Math.sin(a0),
                    cx + rx * (float) Math.cos(a1), cy + ry * (float) Math.sin(a1));
        }
        color(WALL);
        for (int s = 0; s < OVAL_SEGMENTS; s++) {
            double a0 = 2 * Math.PI * s / OVAL_SEGMENTS, a1 = 2 * Math.PI * (s + 1) / OVAL_SEGMENTS;
            line(cx + rx * (float) Math.cos(a0), cy + ry * (float) Math.sin(a0),
                    cx + rx * (float) Math.cos(a1), cy + ry * (float) Math.sin(a1), line);
        }
    }

    private void outline(float x, float y, float w, float h, float width) {
        float half = width / 2;
        rect(x - half, y - half, w + width, width);
        rect(x - half, y + h - half, w + width, width);
        rect(x - half, y - half, width, h + width);
        rect(x + w - half, y - half, width, h + width);
    }

    private void line(float x0, float y0, float x1, float y1, float width) {
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

    private void rect(float x, float y, float w, float h) {
        triangle(x, y, x + w, y, x + w, y + h);
        triangle(x, y, x + w, y + h, x, y + h);
    }

    private void triangle(float x0, float y0, float x1, float y1, float x2, float y2) {
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

    private void color(int argb) {
        r = ((argb >> 16) & 0xFF) / 255f;
        g = ((argb >> 8) & 0xFF) / 255f;
        b = (argb & 0xFF) / 255f;
    }
}
