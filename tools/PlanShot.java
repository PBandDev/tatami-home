package io.github.pbanddev.tatamihome;

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
 * <p>Usage (tools/shot.sh compiles it): PlanShot OUT_DIR W H T [T...]. T is in seconds, like the wall
 * clock the TV uses (System.currentTimeMillis() / 1000.0). Writes OUT_DIR/shot_t{T}.png.
 */
public final class PlanShot {
    private static final int SS = 2;

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: PlanShot OUT_DIR W H T [T...]");
            System.exit(2);
        }
        File dir = new File(args[0]);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot make " + dir);
        int w = Integer.parseInt(args[1]), h = Integer.parseInt(args[2]);
        Scene scene = new Scene(Look.DEFAULT);
        for (int a = 3; a < args.length; a++) {
            long start = System.nanoTime();
            double t = Double.parseDouble(args[a]);
            scene.build(t, w, h, true, true, true);
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
        g.setColor(new Color(scene.look.colours.floor & 0xFFFFFF));
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
}
