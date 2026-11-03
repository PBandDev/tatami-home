package io.github.pbanddev.tatamihome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layout numbers: the plan as it shows on screen, read straight from the model. It runs the same
 * {@link Scene} as the TV with every effect on, at FRAMES times 30 s apart, so walls and camera differ.
 *
 * <p>Sizes are a share of the screen height H, after the camera's zoom. "Complete" rooms have all 4
 * corners on screen; room sizes, shapes and windmills are counted on those only.
 *
 * <p>Nagaya also gets its tile's numbers at home, in H at zoom 1 (rooms, steps, joins, lines, wall density and door
 * hosts), and its motion over {@code --slots} slots (default 1000): the share of wall length that moves in a slot,
 * how many slots move nothing, how far apart a slot's glides start, how far lines go, and how often they turn back.
 *
 * <p>Usage (via tools/shot.sh --stats): PlanStats [OPTIONS] [W H [FRAMES [T0]]], default 1920 1080 120 1793500000.
 * OPTIONS are {@link PlanShot}'s, and {@code --slots N}.
 */
public final class PlanStats {
    private static final int GRID = 6;
    private static final float STEP = 2f;
    private static final int LINES = 30;
    private static final double SPACING = 30.0;
    /** Nagaya's frames are 30.7 s apart, so they land at every point of its slots, not always the same one. */
    private static final double NAGAYA_SPACING = 30.7;

    private final Scene scene;
    private final boolean tree;
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

    private PlanStats(Scene scene, int w, int h) {
        this.scene = scene;
        tree = scene.look.plan.kind == Look.Plan.Kind.TREE;
        this.w = w;
        this.h = h;
        for (int k = 0; k < 3; k++) fixtureSizes.add(new ArrayList<>());
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        PlanShot.Options o = new PlanShot.Options(args);
        args = o.rest;
        int w = args.length > 1 ? Integer.parseInt(args[0]) : 1920;
        int h = args.length > 1 ? Integer.parseInt(args[1]) : 1080;
        int frames = args.length > 2 ? Integer.parseInt(args[2]) : 120;
        double t0 = args.length > 3 ? Double.parseDouble(args[3]) : 1_793_500_000.0;
        PlanStats stats = new PlanStats(o.scene(), w, h);
        double spacing = stats.scene.look.rhythm == null ? SPACING : NAGAYA_SPACING;
        for (int i = 0; i < frames; i++) stats.frame(t0 + i * spacing);
        stats.print(frames, t0, spacing);
        if (stats.scene.layout instanceof TilePlan) {
            TilePlan plan = (TilePlan) stats.scene.layout;
            tile(plan, stats.scene.look, o.builder);
            motion(plan, o.slots, t0);
        }
    }

    private void frame(double t) {
        scene.build(t, w, h, true, true, true);
        Frame f = scene.frame;
        m = scene.matrix;
        float zoom = scene.view.zoom, toH = zoom / h;

        // rooms on screen, their area by paint lightness
        boolean[] seen = new boolean[f.roomCount];
        for (int sy = GRID / 2; sy < h; sy += GRID) {
            for (int sx = GRID / 2; sx < w; sx += GRID) {
                float[] p = toPlan(sx, sy);
                int k = PlanGeometry.roomAt(f, p[0], p[1]);
                if (k < 0) continue;
                seen[k] = true;
                double l = lstar(scene.look.palette.deck[f.roomColor[k]]);
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
            if (tree && PlanGeometry.isWindmillCentre(f, k, 0.5f)) mills++;
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

    private void print(int frames, double t0, double spacing) {
        int planMills = 0;
        Frame f = scene.frame;
        for (int k = 0; k < f.roomCount && tree; k++) if (PlanGeometry.isWindmillCentre(f, k, 0.5f)) planMills++;
        double aspect3 = aspect.stream().filter(a -> a >= 3).count() / (double) Math.max(1, aspect.size());
        double lstarTotal = Arrays.stream(lstarBand).sum();
        System.out.printf("Tatami layout numbers, %s: %d frames, %dx%d, every %.1f s from t=%.0f. Median (p10..p90).%n%n",
                tree ? "Yojohan" : "Nagaya", frames, w, h, spacing, t0);
        row("rooms in the plan", String.valueOf(scene.layout.rooms()));
        if (tree) row("windmill centres in the plan", String.valueOf(planMills));
        row("rooms touching the screen", String.format("%.1f mean", mean(touching)));
        row("complete rooms", String.format("%.1f mean", mean(complete)));
        if (tree) row("windmill centres, complete", String.format("%.2f mean", mean(windmills)));
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
        if (tree) row("rooms with a fixture", String.format("%.0f %%", 100 * mean(fixtureShare)));
        String[] kinds = {"tub", "toilet", "stairs"};
        for (int k = 0; k < 3 && tree; k++) {
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

    // ------------------------------------------------------------------------------------------ Nagaya

    /** Nagaya's tile at home, read from its model, in H at zoom 1. */
    private static void tile(TilePlan plan, Look look, String builder) {
        TileGraph g = plan.graph;
        double unit = look.plan.unitH;
        int least = (int) Math.ceil(0.015 / unit - 1e-9);
        System.out.printf("%nNagaya's tile at home (builder %s, seed %d), in H at zoom 1.%n%n", builder, look.plan.seed);
        row("tile (H)", String.format("%.2f x %.2f: %d rooms, %d cells", g.width * unit, g.height * unit, g.rooms, g.cells));
        row("rooms per H^2", String.format("%.1f", g.rooms / (g.width * g.height * unit * unit)));

        // steps: each room's inside corners, counting legs of at least 0.015 H
        int[] mix = new int[4];
        int[][] byWidth = new int[3][4];
        List<Double> legs = new ArrayList<>();
        int inRows = 0, runOn = 0, steps = 0;
        int[] stepsOf = new int[g.rooms];
        for (int r = 0; r < g.rooms; r++) {
            int n = 0, x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE;
            for (int[] st : steps(g, r)) {
                if (st[0] < least) continue;
                n++;
                legs.add(st[0] * unit);
                inRows += st[1];
                runOn += st[2];
            }
            for (int i = g.roomCellStart[r]; i < g.roomCellStart[r + 1]; i++) {
                x0 = Math.min(x0, g.cellX0[g.roomCellList[i]]);
                x1 = Math.max(x1, g.cellX1[g.roomCellList[i]]);
            }
            stepsOf[r] = n;
            steps += n;
            mix[Math.min(3, n)]++;
            double width = (x1 - x0) * unit;
            byWidth[width < 0.3 ? 0 : width <= 0.5 ? 1 : 2][Math.min(3, n)]++;
        }
        row("class: box / L / two steps / 3+", shares(mix));
        String[] bins = {"under 0.3 H wide", "0.3 to 0.5 H wide", "over 0.5 H wide"};
        for (int b = 0; b < 3; b++) row("  " + bins[b] + " (" + Arrays.stream(byWidth[b]).sum() + ")", shares(byWidth[b]));
        row("step, short leg (H)", spread(legs));
        row("steps in row lines", String.format("%.0f %%", 100.0 * inRows / Math.max(1, steps)));
        row("steps a wall runs on past", String.format("%.0f %%", 100.0 * runOn / Math.max(1, steps)));

        // interlocks: 2 rooms whose shared wall turns a corner (its pieces lie on 2 lines or more)
        Map<Integer, Set<Integer>> linesOfPair = new HashMap<>();
        Map<Integer, int[]> roomsOfPair = new HashMap<>();
        for (int p = 0; p < g.pieces; p++) {
            linesOfPair.computeIfAbsent(g.piecePair[p], k -> new HashSet<>()).add(g.pieceLine[p]);
            roomsOfPair.put(g.piecePair[p], new int[] {g.pieceRoomA[p], g.pieceRoomB[p]});
        }
        boolean[] locked = new boolean[g.rooms];
        for (Map.Entry<Integer, Set<Integer>> e : linesOfPair.entrySet()) {
            if (e.getValue().size() < 2) continue;
            for (int r : roomsOfPair.get(e.getKey())) locked[r] = true;
        }
        int stepped = 0, interlocked = 0;
        for (int r = 0; r < g.rooms; r++) {
            if (stepsOf[r] == 0) continue;
            stepped++;
            if (locked[r]) interlocked++;
        }
        row("rooms with steps in an interlock", String.format("%.0f %%", 100.0 * interlocked / Math.max(1, stepped)));

        // joins at every point of the tile
        int tees = 0, ells = 0, crosses = 0, stems = 0;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                boolean up = wall(g, x - 1, y - 1, x, y - 1), down = wall(g, x - 1, y, x, y);
                boolean left = wall(g, x - 1, y - 1, x - 1, y), right = wall(g, x, y - 1, x, y);
                int arms = (up ? 1 : 0) + (down ? 1 : 0) + (left ? 1 : 0) + (right ? 1 : 0);
                if (arms == 4) crosses++;
                if (arms == 3) tees++;
                if (arms == 3 && left && right) stems++;
                if (arms == 2 && (up || down) && (left || right)) ells++;
            }
        }
        double joins = Math.max(1, tees + ells + crosses);
        row("joins: T / L / +", String.format("%.0f / %.0f / %.1f %%", 100 * tees / joins, 100 * ells / joins, 100 * crosses / joins));
        row("joints running on across a row", String.format("%.1f %%", 100.0 * crosses / Math.max(1, stems + crosses)));

        // walls crossed down columns of plan y (2 tile rows repeat) and along rows of plan x
        List<Double> courses = new ArrayList<>(), straight = new ArrayList<>();
        int columns = 0, alongY = 0, alongX = 0, round = 0;
        for (int x = 0; x < g.width; x += 3, columns++) {
            int first = -1, last = -1;
            for (int y = 0; y < 2 * g.height; y++) {
                if (!wall(g, x, y - 1, x, y)) continue;
                alongY++;
                if (last >= 0) courses.add((y - last) * unit); else first = y;
                last = y;
            }
            if (first >= 0) courses.add((first + 2 * g.height - last) * unit);
        }
        for (int y = 0; y < g.height; y++) {
            boolean[] on = new boolean[g.width];
            int count = 0, start = -1, run = 0;
            for (int x = 0; x < g.width; x++) {
                if (wall(g, x - 1, y, x, y)) alongX++;
                on[x] = wall(g, x, y - 1, x, y);
                if (on[x]) count++; else start = x;
            }
            if (count == g.width) {
                round++;
                continue;
            }
            // straight row walls, joined across the tile's seam
            for (int i = 1; i <= g.width; i++) {
                int x = (start + i) % g.width;
                if (on[x]) {
                    run++;
                } else if (run > 0) {
                    straight.add(run * unit);
                    run = 0;
                }
            }
        }
        row("course spacing (H)", String.format("%.3f (quartiles %.3f..%.3f)", pct(courses, 50), pct(courses, 25), pct(courses, 75)));
        row("straight row walls (H)", String.format("%.3f, p90 %.2f, longest %.2f", pct(straight, 50), pct(straight, 90), pct(straight, 100)));
        row("row walls all round the tile", String.valueOf(round));
        double px = unit * 1080;
        row("joints per 1000 px along x", String.format("%.2f", 1000.0 * alongX / (g.height * g.width * px)));
        row("row walls per 1000 px along y", String.format("%.2f", 1000.0 * alongY / (columns * 2 * g.height * px)));
        int hosts = 0;
        for (boolean host : g.pieceHost) if (host) hosts++;
        row("door hosts per room", String.format("%.2f", hosts / (double) g.rooms));
        row("touching room pairs with a host", String.format("%.0f %% of %d", 100.0 * hosts / g.pairs, g.pairs));
        int moving = 0;
        for (int length : g.groupLength) if (length > 0) moving++;
        row("lines; groups that move", g.lines + "; " + moving);
    }

    /** Nagaya's motion over many slots. */
    private static void motion(TilePlan plan, int slots, double t0) {
        TileGraph g = plan.graph;
        TileMotion m = plan.motion;
        int n = g.groups;
        long total = 0;
        for (int length : g.groupLength) total += length;
        int[] before = new int[n], after = new int[n], lastWay = new int[n];
        List<Double> shares = new ArrayList<>(), busy = new ArrayList<>(), starts = new ArrayList<>(), travel = new ArrayList<>();
        int turns = 0, pairs = 0, tried = 0, rejected = 0, shortened = 0, aligned = 0, quiet = 0;
        long k0 = m.clock.slot(t0);
        for (long k = k0; k < k0 + slots; k++) {
            m.glide(k, before, after);
            tried += m.tried;
            rejected += m.rejected;
            shortened += m.shortened;
            aligned += m.aligned;
            long length = 0;
            double first = Double.MAX_VALUE, last = 0;
            for (int i = 0; i < n; i++) {
                int d = after[i] - before[i];
                if (d == 0) continue;
                length += g.groupLength[i];
                travel.add((double) Math.abs(d));
                first = Math.min(first, m.clock.delay(k, i));
                last = Math.max(last, m.clock.delay(k, i));
                if (lastWay[i] != 0) {
                    pairs++;
                    if (Integer.signum(d) != lastWay[i]) turns++;
                }
                lastWay[i] = Integer.signum(d);
            }
            shares.add(length / (double) total);
            if (length == 0) quiet++; else busy.add(length / (double) total);
            if (length > 0) starts.add(last - first);
        }
        System.out.printf("%nNagaya's motion: %d slots of %.1f s from t=%.0f.%n%n", slots, m.clock.slotSeconds, t0);
        row("wall length moving per slot, all", spread(shares));
        row("wall length moving per busy slot", spread(busy));
        row("slots that move nothing", String.format("%.0f %%", 100.0 * quiet / slots));
        row("first to last glide start (s)", spread(starts));
        row("travel (steps)", String.format("%.0f, p90 %.0f, longest %.0f", pct(travel, 50), pct(travel, 90), pct(travel, 100)));
        row("moves that turn back", String.format("%.0f %%", 100.0 * turns / Math.max(1, pairs)));
        row("moves into line with another line", String.format("%.0f %%", 100.0 * aligned / Math.max(1, travel.size())));
        row("picks a rule cut short", String.format("%.0f %%", 100.0 * shortened / Math.max(1, tried)));
        row("picks a rule turned down", String.format("%.0f %%", 100.0 * rejected / Math.max(1, tried)));
    }

    /** True when the unit squares (ax, ay) and (bx, by) of the plane lie in different rooms. */
    private static boolean wall(TileGraph g, int ax, int ay, int bx, int by) {
        return g.roomAt(ax, ay) != g.roomAt(bx, by);
    }

    /** A room's inside corners at home: {short leg, 1 when that leg is vertical, 1 when a wall runs on past its end}. */
    private static List<int[]> steps(TileGraph g, int r) {
        int from = g.roomCellStart[r], to = g.roomCellStart[r + 1];
        int[] xs = new int[2 * (to - from)], ys = new int[xs.length];
        for (int i = from; i < to; i++) {
            int c = g.roomCellList[i];
            xs[2 * (i - from)] = g.cellX0[c];
            xs[2 * (i - from) + 1] = g.cellX1[c];
            ys[2 * (i - from)] = g.cellY0[c];
            ys[2 * (i - from) + 1] = g.cellY1[c];
        }
        int[] x = Arrays.stream(xs).sorted().distinct().toArray(), y = Arrays.stream(ys).sorted().distinct().toArray();
        // in[i][j]: the block left of and above grid point (i, j) is in the room; a ring of empty blocks round it
        boolean[][] in = new boolean[x.length + 1][y.length + 1];
        for (int i = 0; i + 1 < x.length; i++) {
            for (int j = 0; j + 1 < y.length; j++) {
                for (int k = g.roomCellStart[r]; k < g.roomCellStart[r + 1]; k++) {
                    int c = g.roomCellList[k];
                    if (x[i] >= g.cellX0[c] && x[i + 1] <= g.cellX1[c] && y[j] >= g.cellY0[c] && y[j + 1] <= g.cellY1[c]) {
                        in[i + 1][j + 1] = true;
                    }
                }
            }
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < y.length; j++) {
                if (around(in, i, j) != 3) continue;
                // the 2 edges run away from the corner along the sides of the empty block
                int sx = in[i][j] && in[i][j + 1] ? 1 : -1, sy = in[i][j] && in[i + 1][j] ? 1 : -1;
                int ii = i + sx, jj = j + sy;
                while (!corner(in, ii, j)) ii += sx;
                while (!corner(in, i, jj)) jj += sy;
                int across = Math.abs(x[ii] - x[i]), down = Math.abs(y[jj] - y[j]);
                boolean vertical = down <= across;
                int ex = vertical ? x[i] : x[ii], ey = vertical ? y[jj] : y[j];
                boolean runsOn = vertical
                        ? wall(g, ex - 1, sy > 0 ? ey : ey - 1, ex, sy > 0 ? ey : ey - 1)
                        : wall(g, sx > 0 ? ex : ex - 1, ey - 1, sx > 0 ? ex : ex - 1, ey);
                out.add(new int[] {Math.min(across, down), vertical ? 1 : 0, runsOn ? 1 : 0});
            }
        }
        return out;
    }

    /** How many of the 4 blocks round grid point (i, j) are in the room. */
    private static int around(boolean[][] in, int i, int j) {
        return (in[i][j] ? 1 : 0) + (in[i + 1][j] ? 1 : 0) + (in[i][j + 1] ? 1 : 0) + (in[i + 1][j + 1] ? 1 : 0);
    }

    private static boolean corner(boolean[][] in, int i, int j) {
        int n = around(in, i, j);
        return n == 1 || n == 3 || (n == 2 && in[i][j] == in[i + 1][j + 1]);
    }

    private static String shares(int[] counts) {
        double total = Math.max(1, Arrays.stream(counts).sum());
        return String.format("%.0f / %.0f / %.0f / %.0f %%", 100 * counts[0] / total, 100 * counts[1] / total,
                100 * counts[2] / total, 100 * counts[3] / total);
    }
}
