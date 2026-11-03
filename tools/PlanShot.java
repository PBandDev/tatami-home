package io.github.pbanddev.tatami;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the floor plan to PNG on the PC with the same {@link Scene} the TV runs, before the lens
 * (no grain, vignette or blur). Java2D fills the triangles at 2x without anti-aliasing, then a 2x2
 * average gives one pixel: close to the TV's 4x MSAA. About 0.12 s per 1080p frame.
 *
 * <p>Usage (tools/shot.sh compiles it): PlanShot [OPTIONS] OUT_DIR W H T [T...]. T is in seconds, like the wall
 * clock the TV uses (System.currentTimeMillis() / 1000.0). Writes OUT_DIR/shot_t{T}.png. Options:
 * {@code --look yojohan|nagaya} (default yojohan), {@code --flat} (no tilt, zoom 1, the plan's origin in the middle:
 * the layout as built), and for Nagaya {@code --seed N}, {@code --tile WxH} (in steps) and {@code --builder NAME}
 * (a {@link TileBuilder} class in this package).
 */
public final class PlanShot {
    private static final int SS = 2;

    public static void main(String[] args) throws Exception {
        Options o = new Options(args);
        if (o.rest.length < 4) {
            System.err.println("usage: PlanShot [--look yojohan|nagaya] [--flat] [--seed N] [--tile WxH] [--builder NAME] OUT_DIR W H T [T...]");
            System.exit(2);
        }
        args = o.rest;
        File dir = new File(args[0]);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot make " + dir);
        int w = Integer.parseInt(args[1]), h = Integer.parseInt(args[2]);
        Scene scene = o.scene();
        for (int a = 3; a < args.length; a++) {
            long start = System.nanoTime();
            double t = Double.parseDouble(args[a]);
            if (o.flat) {
                scene.view.set(w, h, 0f, 1f, 0.0, 0.0, Scene.margin(scene.look));
                scene.view.matrix(scene.matrix);
                scene.fill(t, true, true, scene.look.post.wash ? Wash.NAGAYA : null);
            } else {
                scene.build(t, w, h, true, true, true);
            }
            File file = new File(dir, "shot_t" + args[a] + ".png");
            ImageIO.write(render(scene, w, h), "png", file);
            System.out.printf("%s  %d triangles  %.0f ms%n", file.getName(),
                    scene.mesh.floats / (3 * SceneMesh.FLOATS_PER_VERTEX), (System.nanoTime() - start) / 1e6);
        }
    }

    static BufferedImage render(Scene scene, int w, int h) {
        BufferedImage big = new BufferedImage(w * SS, h * SS, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(scene.look.palette.floor & 0xFFFFFF));
        g.fillRect(0, 0, w * SS, h * SS);
        Path2D.Float p = new Path2D.Float();
        float[] d = scene.mesh.data, m = scene.matrix;
        int stride = SceneMesh.FLOATS_PER_VERTEX;
        for (int i = 0; i < scene.mesh.floats; i += 3 * stride) {
            p.reset();
            for (int v = 0; v < 3; v++) {
                int k = i + v * stride;
                float cx = m[0] * d[k] + m[4] * d[k + 1] + m[12];
                float cy = m[1] * d[k] + m[5] * d[k + 1] + m[13];
                float sx = (cx + 1) / 2 * w * SS, sy = (1 - cy) / 2 * h * SS;
                if (v == 0) p.moveTo(sx, sy); else p.lineTo(sx, sy);
            }
            p.closePath();
            g.setColor(new Color(d[i + 2], d[i + 3], d[i + 4]));
            g.fill(p);
        }
        g.dispose();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, gg = 0, b = 0;
                for (int dy = 0; dy < SS; dy++) {
                    for (int dx = 0; dx < SS; dx++) {
                        int c = big.getRGB(x * SS + dx, y * SS + dy);
                        r += (c >> 16) & 0xFF;
                        gg += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                int n = SS * SS;
                out.setRGB(x, y, ((r / n) << 16) | ((gg / n) << 8) | (b / n));
            }
        }
        return out;
    }

    /** The command line's options, shared with {@link PlanStats}; {@link #rest} is what follows them. */
    static final class Options {
        String look = "yojohan", builder = "SkylineBuilder";
        long seed = -1;
        int tileW = -1, tileH = -1, slots = 1000;
        boolean flat;
        String[] rest;

        Options(String[] args) {
            int a = 0;
            for (; a < args.length && args[a].startsWith("--"); a++) {
                switch (args[a]) {
                    case "--look": look = args[++a]; break;
                    case "--flat": flat = true; break;
                    case "--seed": seed = Long.parseLong(args[++a]); break;
                    case "--builder": builder = args[++a]; break;
                    case "--slots": slots = Integer.parseInt(args[++a]); break;
                    case "--tile": {
                        String[] wh = args[++a].split("x");
                        tileW = Integer.parseInt(wh[0]);
                        tileH = Integer.parseInt(wh[1]);
                        break;
                    }
                    default: throw new IllegalArgumentException("unknown option " + args[a]);
                }
            }
            rest = java.util.Arrays.copyOfRange(args, a, args.length);
        }

        Look look() {
            if (look.equals("yojohan")) return Look.YOJOHAN;
            if (!look.equals("nagaya")) throw new IllegalArgumentException("no look " + look);
            Look n = Look.NAGAYA;
            Look.Plan p = n.plan;
            return new Look(p.withTile(seed >= 0 ? seed : p.seed, tileW > 0 ? tileW : p.tileWUnits,
                    tileH > 0 ? tileH : p.tileHUnits),
                    n.rhythm, n.motion, n.palette, n.walls, n.camera, n.doors, n.fixtures, n.post);
        }

        TileBuilder builder() throws ReflectiveOperationException {
            return (TileBuilder) Class.forName("io.github.pbanddev.tatami." + builder).getDeclaredConstructor().newInstance();
        }

        Scene scene() throws ReflectiveOperationException {
            Look l = look();
            return l == Look.YOJOHAN ? new Scene(l) : new Scene(l, builder());
        }
    }
}
