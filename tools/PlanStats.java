package io.github.pbanddev.tatamihome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Layout numbers: the plan as it shows on screen, read straight from the model. It runs the same
 * {@link Scene} as the TV with every effect on, at FRAMES times 30 s apart, so walls and camera differ.
 *
 * <p>Sizes are a share of the screen height H, after the camera's zoom. "Complete" rooms have all 4
 * corners on screen; room sizes, shapes and windmills are counted on those only.
 *
 * <p>Usage (via tools/shot.sh --stats): PlanStats [W H [FRAMES [T0]]], default 1920 1080 120 1793500000.
 */
public final class PlanStats {
    private static final int GRID = 6;
    private static final float STEP = 2f;
    private static final int LINES = 30;
    private static final double SPACING = 30.0;

    private final Scene scene = new Scene(Look.DEFAULT);
    private final int w, h;
    private float[] m;

    private final List<Double> touching = new ArrayList<>(), complete = new ArrayList<>(),
            windmills = new ArrayList<>(), shortSide = new ArrayList<>(), longSide = new ArrayList<>(),
            aspect = new ArrayList<>(), longestWall = new ArrayList<>(), fixtureShare = new ArrayList<>(),
            doorsPerRoom = new ArrayList<>();
    private int wider, rooms, crossingFrames, shut, open, doors;
    private double chordLengthX, chordLengthY;
    private int crossingsX, crossingsY;
    private final double[] lstarBand = new double[5];
    private final List<List<float[]>> fixtureSizes = new ArrayList<>();

    private PlanStats(int w, int h) {
        this.w = w;
        this.h = h;
        for (int k = 0; k < 3; k++) fixtureSizes.add(new ArrayList<>());
    }

    public static void main(String[] args) {
        int w = args.length > 1 ? Integer.parseInt(args[0]) : 1920;
        int h = args.length > 1 ? Integer.parseInt(args[1]) : 1080;
        int frames = args.length > 2 ? Integer.parseInt(args[2]) : 120;
        double t0 = args.length > 3 ? Double.parseDouble(args[3]) : 1_793_500_000.0;
        PlanStats stats = new PlanStats(w, h);
        for (int i = 0; i < frames; i++) stats.frame(t0 + i * SPACING);
        stats.print(frames, t0);
    }

    private void frame(double t) {
        scene.build(t, w, h, true, true, true);
        Frame f = scene.frame;
        m = scene.matrix;
        float zoom = Camera.zoom(t, true), toH = zoom / h;

        // rooms on screen, their area by paint lightness
        boolean[] seen = new boolean[f.roomCount];
        for (int sy = GRID / 2; sy < h; sy += GRID) {
            for (int sx = GRID / 2; sx < w; sx += GRID) {
                float[] p = toPlan(sx, sy);
                int k = PlanGeometry.roomAt(f, p[0], p[1]);
                if (k < 0) continue;
                seen[k] = true;
                double l = lstar(scene.look.colours.deck[f.roomColor[k]]);
                lstarBand[Math.min(4, (int) (l / 20))]++;
            }
        }
        int touched = 0, done = 0, mills = 0, hosts = 0;
        boolean[] hosting = new boolean[f.roomCount];
        for (int k = 0; k < f.fixtureCount; k++) hosting[f.fixtureRoom[k]] = true;
        for (int k = 0; k < f.roomCount; k++) {
            if (!seen[k]) continue;
            touched++;
            if (hosting[k]) hosts++;
            float x0 = f.roomX[k], y0 = f.roomY[k], x1 = x0 + f.roomW[k], y1 = y0 + f.roomH[k];
            if (!(onScreen(x0, y0) && onScreen(x1, y0) && onScreen(x0, y1) && onScreen(x1, y1))) continue;
            done++;
            if (PlanGeometry.isWindmillCentre(f, k, 0.5f)) mills++;
            double a = f.roomW[k] * toH, b = f.roomH[k] * toH;
            shortSide.add(Math.min(a, b));
            longSide.add(Math.max(a, b));
            aspect.add(Math.max(a, b) / Math.min(a, b));
            if (a > b) wider++;
            rooms++;
        }
        touching.add((double) touched);
        complete.add((double) done);
        windmills.add((double) mills);
        fixtureShare.add(touched == 0 ? 0 : (double) hosts / touched);

        // doors with their hinge on screen
        int doorsHere = 0;
        for (int d = 0; d < f.doorCount; d++) {
            if (!onScreen(f.doorX[d], f.doorY[d])) continue;
            doorsHere++;
            double deg = Math.toDegrees(f.doorOpening[d]);
            if (deg <= 5) shut++;
            if (deg >= 75) open++;
        }
        doors += doorsHere;
        doorsPerRoom.add(touched == 0 ? 0 : (double) doorsHere / touched);
        for (int k = 0; k < f.fixtureCount; k++) {
            if (seen[f.fixtureRoom[k]]) {
                float px = 1080f / h * zoom;
                fixtureSizes.get(Math.min(2, f.fixtureKind[k])).add(new float[] {f.fixtureW[k] * px, f.fixtureH[k] * px});
            }
        }

        // chords: walls crossed along lines of plan x and of plan y
        float[] box = screenBox();
        for (int axis = 0; axis < 2; axis++) {
            float lo = axis == 0 ? box[1] : box[0], hi = axis == 0 ? box[3] : box[2];
            float from = axis == 0 ? box[0] : box[1], to = axis == 0 ? box[2] : box[3];
            for (int n = 0; n < LINES; n++) {
                float c = lo + (hi - lo) * (n + 0.5f) / LINES;
                int last = -2;
                for (float s = from; s <= to; s += STEP) {
                    float x = axis == 0 ? s : c, y = axis == 0 ? c : s;
                    if (!onScreen(x, y)) {
                        last = -2;
                        continue;
                    }
                    int k = PlanGeometry.roomAt(f, x, y);
                    if (last != -2) {
                        if (axis == 0) chordLengthX += STEP * toH; else chordLengthY += STEP * toH;
                        if (k != last) {
                            if (axis == 0) crossingsX++; else crossingsY++;
                        }
                    }
                    last = k;
                }
            }
        }

        // the longest straight wall on screen, collinear wall pieces joined
        double longest = 0;
        boolean crossing = false;
        for (float[] line : straightWalls(f)) {
            boolean vertical = line[0] == 1;
            float run = 0, runStart = line[2];
            boolean startsOutside = false, wasOn = false;
            for (float s = line[2]; s <= line[3] + STEP / 2; s += STEP) {
                float x = vertical ? line[1] : s, y = vertical ? s : line[1];
                boolean on = onScreen(x, y);
                if (on && !wasOn) {
                    runStart = s;
                    startsOutside = s > line[2];
                }
                if (!on && wasOn) {
                    run = Math.max(run, s - runStart);
                    if (startsOutside && opposite(vertical, line[1], runStart, s)) crossing = true;
                }
                wasOn = on;
            }
            if (wasOn) run = Math.max(run, line[3] - runStart);
            longest = Math.max(longest, run * toH);
        }
        longestWall.add(longest);
        if (crossing) crossingFrames++;
    }

    /** Joins collinear walls that touch: {vertical 1 or 0, coordinate, from, to}. */
    private static List<float[]> straightWalls(Frame f) {
        List<float[]> pieces = new ArrayList<>();
        for (int k = 0; k < f.wallCount; k++) {
            boolean v = f.wallVertical[k];
            pieces.add(new float[] {v ? 1 : 0, v ? f.wallX0[k] : f.wallY0[k], v ? f.wallY0[k] : f.wallX0[k], v ? f.wallY1[k] : f.wallX1[k]});
        }
        pieces.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : a[1] != b[1] ? Float.compare(a[1], b[1]) : Float.compare(a[2], b[2]));
        List<float[]> lines = new ArrayList<>();
        for (float[] p : pieces) {
            float[] last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            if (last != null && last[0] == p[0] && Math.abs(last[1] - p[1]) < 0.01f && p[2] <= last[3] + 0.01f) {
                last[3] = Math.max(last[3], p[3]);
            } else {
                lines.add(p.clone());
            }
        }
        return lines;
    }

    /** True when a wall's on-screen run, from plan position a to b along it, enters and leaves on opposite screen edges. */
    private boolean opposite(boolean vertical, float coordinate, float a, float b) {
        float[] p = toScreen(vertical ? coordinate : a, vertical ? a : coordinate);
        float[] q = toScreen(vertical ? coordinate : b, vertical ? b : coordinate);
        int ea = edge(p), eb = edge(q);
        return (ea ^ eb) == 2;
    }

    /** The screen edge nearest a point: 0 left, 1 top, 2 right, 3 bottom. */
    private int edge(float[] s) {
        float[] d = {s[0], s[1], w - s[0], h - s[1]};
        int best = 0;
        for (int i = 1; i < 4; i++) if (d[i] < d[best]) best = i;
        return best;
    }

    private float[] screenBox() {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] c : new float[][] {{0, 0}, {w, 0}, {0, h}, {w, h}}) {
            float[] p = toPlan(c[0], c[1]);
            box[0] = Math.min(box[0], p[0]);
            box[1] = Math.min(box[1], p[1]);
            box[2] = Math.max(box[2], p[0]);
            box[3] = Math.max(box[3], p[1]);
        }
        return box;
    }

    private float[] toScreen(float px, float py) {
        float cx = m[0] * px + m[4] * py + m[12], cy = m[1] * px + m[5] * py + m[13];
        return new float[] {(cx + 1) / 2 * w, (1 - cy) / 2 * h};
    }

    private boolean onScreen(float px, float py) {
        float[] s = toScreen(px, py);
        return s[0] >= 0 && s[0] <= w && s[1] >= 0 && s[1] <= h;
    }

    private float[] toPlan(float sx, float sy) {
        float cx = 2 * sx / w - 1 - m[12], cy = 1 - 2 * sy / h - m[13];
        float det = m[0] * m[5] - m[4] * m[1];
        return new float[] {(m[5] * cx - m[4] * cy) / det, (-m[1] * cx + m[0] * cy) / det};
    }

    static double lstar(int argb) {
        double y = 0.2126 * linear((argb >> 16) & 0xFF) + 0.7152 * linear((argb >> 8) & 0xFF) + 0.0722 * linear(argb & 0xFF);
        double f = y > 216.0 / 24389 ? Math.cbrt(y) : (24389.0 / 27 * y + 16) / 116;
        return 116 * f - 16;
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double pct(List<Double> v, double p) {
        if (v.isEmpty()) return Double.NaN;
        double[] a = v.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(a);
        return a[(int) Math.min(a.length - 1, Math.round(p / 100 * (a.length - 1)))];
    }

    private static double mean(List<Double> v) {
        return v.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private static String spread(List<Double> v) {
        return String.format("%.2f (%.2f..%.2f)", pct(v, 50), pct(v, 10), pct(v, 90));
    }

    private static void row(String metric, String value) {
        System.out.printf("%-34s %s%n", metric, value);
    }

    private void print(int frames, double t0) {
        int planMills = 0;
        Frame f = scene.frame;
        for (int k = 0; k < f.roomCount; k++) if (PlanGeometry.isWindmillCentre(f, k, 0.5f)) planMills++;
        double aspect3 = aspect.stream().filter(a -> a >= 3).count() / (double) Math.max(1, aspect.size());
        double lstarTotal = Arrays.stream(lstarBand).sum();
        System.out.printf("Tatami layout numbers: %d frames, %dx%d, every %.0f s from t=%.0f. Median (p10..p90).%n%n",
                frames, w, h, SPACING, t0);
        row("rooms in the plan", String.valueOf(scene.plan.rooms));
        row("windmill centres in the plan", String.valueOf(planMills));
        row("rooms touching the screen", String.format("%.1f mean", mean(touching)));
        row("complete rooms", String.format("%.1f mean", mean(complete)));
        row("windmill centres, complete", String.format("%.2f mean", mean(windmills)));
        row("short side (H)", spread(shortSide));
        row("short side, smallest (H)", String.format("%.3f", pct(shortSide, 0)));
        row("long side (H)", spread(longSide));
        row("aspect", spread(aspect));
        row("aspect, largest", String.format("%.2f", pct(aspect, 100)));
        row("rooms 3:1 or more", String.format("%.0f %%", 100 * aspect3));
        row("wider than tall (plan x)", String.format("%.0f %%", 100.0 * wider / Math.max(1, rooms)));
        row("mean chord along plan x (H)", String.format("%.3f", chordLengthX / Math.max(1, crossingsX)));
        row("mean chord along plan y (H)", String.format("%.3f", chordLengthY / Math.max(1, crossingsY)));
        row("longest straight wall (H)", spread(longestWall));
        row("frames with a wall across", String.format("%.0f %%", 100.0 * crossingFrames / frames));
        row("doors per room on screen", String.format("%.2f mean", mean(doorsPerRoom)));
        row("doors shut (<= 5 deg)", String.format("%.0f %%", 100.0 * shut / Math.max(1, doors)));
        row("doors open (>= 75 deg)", String.format("%.0f %%", 100.0 * open / Math.max(1, doors)));
        row("rooms with a fixture", String.format("%.0f %%", 100 * mean(fixtureShare)));
        String[] kinds = {"tub", "toilet", "stairs"};
        for (int k = 0; k < 3; k++) {
            List<float[]> s = fixtureSizes.get(k);
            String ours = s.isEmpty() ? "none" : String.format("%.0f x %.0f (n=%d)", medianOf(s, 0), medianOf(s, 1), s.size());
            row(kinds[k] + " box at 1080p (px)", ours);
        }
        String[] bands = {"< 20", "20-40", "40-60", "60-80", "> 80"};
        for (int b = 0; b < 5; b++) {
            row("room area, paint L* " + bands[b], String.format("%.0f %%", 100 * lstarBand[b] / lstarTotal));
        }
    }

    private static double medianOf(List<float[]> v, int i) {
        List<Double> d = new ArrayList<>();
        for (float[] a : v) d.add((double) a[i]);
        return pct(d, 50);
    }
}
