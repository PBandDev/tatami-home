package io.github.pbanddev.tatamihome;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws the floor plan with OpenGL ES 2, all on the GPU, through a film-like lens ({@link Look.Post}):
 * <ol>
 *   <li>the copy to blur: the bare scene (the cheapest shader there is) into a texture {@link Lens#margin} bigger
 *       than the screen on every side, so walls just off screen blur in at the edges as they should;</li>
 *   <li>that copy averaged down to a quarter size as a 4x4 box;</li>
 *   <li>one separable Gaussian round there, across and then down (2 passes);</li>
 *   <li>the scene into the window (the window asks for 4x MSAA, so tilted walls stay smooth), with the
 *       vignette, the colour ramp and the grain;</li>
 *   <li>the blurred copy, given the same vignette and colour ramp, blended over it everywhere but a sharp
 *       oval in the middle. Both are smooth over hundreds of pixels, so giving them after the blur looks the
 *       same as before it, and the blurred copy's scene pass stays cheap.</li>
 * </ol>
 * The vignette, the colour ramp and the blur's oval come from a small picture of the screen made once per
 * size ({@link Lens}), so each pixel reads them instead of working them out.
 * Six passes; with tilt-shift off, only the scene into the window. Nothing is copied from the CPU to the GPU
 * each frame except the scene's triangles. The grain tile goes up once per GL context.
 */
final class FloorPlanGlRenderer implements GLSurfaceView.Renderer {

    private static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    /**
     * Mediump maths, about twice as fast as highp on many TV GPUs, with HIGHP for the texture coordinates that must
     * land on the right pixel: the grain tile's (in mediump, gl_FragCoord / 256 cannot hit texel centres across
     * a 1080p screen) and the blurred copy's (mediump is about 1 px off near the right edge).
     */
    private static final String LENS_PRECISION = "precision mediump float;\n"
            + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n#define HIGHP highp\n#else\n#define HIGHP mediump\n#endif\n";
    /** The scene's colours as they are, for the copy to blur. */
    private static final String PLAIN_FRAGMENT = "precision mediump float;\nvarying vec4 vColor;\n"
            + "void main() { gl_FragColor = vColor; }\n";
    /** Attribute slots, bound before every program links. */
    private static final int POS = 0, COLOR = 1;
    private static final long GRAIN_SEED = 1L;
    private static final String SCENE_VERTEX =
            "uniform mat4 uMatrix;\nattribute vec2 aPos;\nattribute vec4 aColor;\nvarying vec4 vColor;\n"
                    + "void main() { vColor = aColor; gl_Position = uMatrix * vec4(aPos, 0.0, 1.0); }\n";
    private static final String QUAD_VERTEX =
            "attribute vec2 aPos;\nvarying vec2 vUv;\n"
                    + "void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    /**
     * Full size down to 1/4 as a 4x4 box: 4 bilinear taps, each the average of 2x2 texels. A wall
     * thinner than one small pixel then fades in and out smoothly instead of popping, which is
     * what made the blurred edges flicker when the scene was drawn straight at 1/4 size.
     */
    private static final String DOWNSAMPLE_FRAGMENT = PRECISION
            + "uniform sampler2D uTex;\nuniform vec2 uTexel;\nvarying vec2 vUv;\n"
            + "void main() {\n"
            + "  gl_FragColor = 0.25 * (texture2D(uTex, vUv + uTexel * vec2(-1.0, -1.0))\n"
            + "      + texture2D(uTex, vUv + uTexel * vec2(1.0, -1.0))\n"
            + "      + texture2D(uTex, vUv + uTexel * vec2(-1.0, 1.0))\n"
            + "      + texture2D(uTex, vUv + uTexel * vec2(1.0, 1.0)));\n}\n";

    private final Look look = Look.DEFAULT;
    private final Scene scene = new Scene(look);
    private final float floorR, floorG, floorB;
    private final FloatBuffer meshBuffer;
    private final FloatBuffer quadBuffer;
    /** The grain tile, made once on the GL thread, off Home's main thread. Its GPU copy dies with each GL context. */
    private ByteBuffer grainPixels;
    private final float[] grainOffset = new float[2];
    /**
     * Index 0 is the full-size copy of the scene, a margin bigger than the screen on every side; 1 and 2
     * are the 1/4-size blur targets of the same area; 3 is the lens map.
     */
    private final int[] texture = new int[4];
    private final int[] framebuffer = new int[3];
    /**
     * The copy to blur reaches this far past the screen's left and right, and top and bottom, edges, so walls
     * just off screen blur in too: {@link Lens#margin}, padded so the copy is whole GPU tiles.
     */
    private int marginX, marginY;
    /** The camera matrix for the copy to blur: the screen's, shrunk to fit the wider target. */
    private final float[] wideMatrix = new float[16];
    private volatile Effects effects;
    private int width, height, wideWidth, wideHeight, blurWidth, blurHeight;
    private int sceneProgram, plainProgram, downsample, blur, blend, meshVbo, quadVbo, grainTexture;
    /** Uniform locations, named as in the shaders. uLens reads texture unit 1; every other sampler unit 0. */
    private int uMatrix, uPlainMatrix, uInvSize, uVig, uGrainAmp, uGrainOffset, uTexel, uStep, uBlendVig, uBlurArea;

    FloorPlanGlRenderer(Effects effects) {
        this.effects = effects;
        floorR = ((look.colours.floor >> 16) & 0xFF) / 255f;
        floorG = ((look.colours.floor >> 8) & 0xFF) / 255f;
        floorB = (look.colours.floor & 0xFF) / 255f;
        meshBuffer = floats(scene.mesh.data.length);
        quadBuffer = floats(8);
        quadBuffer.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
    }

    void setEffects(Effects effects) {
        this.effects = effects;
    }

    /**
     * A GL call failed (a shader or a render target the driver refused, or memory ran out, say right after a 4K app):
     * clear to the floor colour instead. The GL thread must keep drawing, because the window that holds this view
     * waits for its first frame before Android shows it; a dead GL thread left Home black and not responding.
     * A new context (the next time the surface is created) tries again.
     */
    private boolean broken;

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        broken = false;
        try {
            setUp();
        } catch (RuntimeException | OutOfMemoryError failure) {
            fail(failure);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = w;
        height = h;
        if (broken) return;
        try {
            size(w, h);
        } catch (RuntimeException | OutOfMemoryError failure) {
            fail(failure);
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (!broken) {
            try {
                draw();
                return;
            } catch (RuntimeException | OutOfMemoryError failure) {
                fail(failure);
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(floorR, floorG, floorB, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void fail(Throwable failure) {
        broken = true;
        Log.e("Tatami", "the floor plan cannot draw on this context", failure);
    }

    /** A new context: everything from the old one is gone, its render targets and grain tile too. */
    private void setUp() {
        texture[0] = 0;   // so size() does not delete the old names, which may be live ones in this context
        Look.Post post = look.post;
        sceneProgram = program(SCENE_VERTEX, sceneFragment(post));
        plainProgram = program(SCENE_VERTEX, PLAIN_FRAGMENT);
        downsample = program(QUAD_VERTEX, DOWNSAMPLE_FRAGMENT);
        blur = program(QUAD_VERTEX, blurFragment(post));
        blend = program(QUAD_VERTEX, BLEND_FRAGMENT);
        uMatrix = GLES20.glGetUniformLocation(sceneProgram, "uMatrix");
        uPlainMatrix = GLES20.glGetUniformLocation(plainProgram, "uMatrix");
        uInvSize = GLES20.glGetUniformLocation(sceneProgram, "uInvSize");
        uVig = GLES20.glGetUniformLocation(sceneProgram, "uVig");
        uGrainAmp = GLES20.glGetUniformLocation(sceneProgram, "uGrainAmp");
        uGrainOffset = GLES20.glGetUniformLocation(sceneProgram, "uGrainOffset");
        uTexel = GLES20.glGetUniformLocation(downsample, "uTexel");
        uStep = GLES20.glGetUniformLocation(blur, "uStep");
        uBlendVig = GLES20.glGetUniformLocation(blend, "uVig");
        uBlurArea = GLES20.glGetUniformLocation(blend, "uBlurArea");
        lensOnUnit1(sceneProgram);
        lensOnUnit1(blend);
        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        meshVbo = buffers[0];
        quadVbo = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, quadBuffer, GLES20.GL_STATIC_DRAW);
        if (grainPixels == null) grainPixels = ByteBuffer.wrap(Grain.texture(Grain.field(GRAIN_SEED)));
        int[] grain = new int[1];
        GLES20.glGenTextures(1, grain, 0);
        grainTexture = grain[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, grainTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, Grain.SIZE, Grain.SIZE, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, grainPixels);
        sampling(GLES20.GL_NEAREST, GLES20.GL_REPEAT);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(floorR, floorG, floorB, 1f);
    }

    private void size(int w, int h) {
        int least = Lens.margin(look.post);
        marginX = Lens.padded(w, least, look.post.blurDivisor);
        marginY = Lens.padded(h, least, look.post.blurDivisor);
        wideWidth = w + 2 * marginX;
        wideHeight = h + 2 * marginY;
        blurWidth = Math.max(1, wideWidth / look.post.blurDivisor);
        blurHeight = Math.max(1, wideHeight / look.post.blurDivisor);
        if (texture[0] != 0) {
            GLES20.glDeleteTextures(4, texture, 0);
            GLES20.glDeleteFramebuffers(3, framebuffer, 0);
        }
        GLES20.glGenTextures(4, texture, 0);
        GLES20.glGenFramebuffers(3, framebuffer, 0);
        for (int i = 0; i < 3; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, i == 0 ? wideWidth : blurWidth, i == 0 ? wideHeight : blurHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            sampling(GLES20.GL_LINEAR, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, texture[i], 0);
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("render target " + i + " incomplete: " + status);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[3]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, Lens.width(w), Lens.height(h), 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(Lens.map(w, h, look.post)));
        sampling(GLES20.GL_LINEAR, GLES20.GL_CLAMP_TO_EDGE);
    }

    /** Points the program's uLens sampler at texture unit 1. */
    private static void lensOnUnit1(int program) {
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLens"), 1);
    }

    private void draw() {
        Effects fx = effects;
        double t = FloorPlanView.now();
        scene.build(t, width, height, fx.doors, fx.fixtures, fx.camera);
        SceneMesh mesh = scene.mesh;
        meshBuffer.clear();
        meshBuffer.put(mesh.data, 0, mesh.floats).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.floats * 4, meshBuffer, GLES20.GL_STREAM_DRAW);
        Grain.offset(t, look.post.grainFps, grainOffset);
        float vignette = fx.vignette ? 1f : 0f;
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[3]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        if (fx.tiltShift) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
            GLES20.glViewport(0, 0, wideWidth, wideHeight);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            drawPlainScene();   // no lens yet, and no grain: grain belongs to the sharp layer only
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[1]);
            GLES20.glViewport(0, 0, blurWidth, blurHeight);
            GLES20.glUseProgram(downsample);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
            GLES20.glUniform2f(uTexel, 1f / wideWidth, 1f / wideHeight);
            drawQuad();
            blurInto(2, texture[1], 1f / blurWidth, 0f);
            blurInto(1, texture[2], 0f, 1f / blurHeight);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawScene(vignette, fx.grain ? 1f : 0f);

        if (fx.tiltShift) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(blend);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[1]);
            GLES20.glUniform1f(uBlendVig, vignette);
            // where the screen sits inside the wider blurred copy: scale x, y, then offset x, y
            GLES20.glUniform4f(uBlurArea, (float) width / wideWidth, (float) height / wideHeight,
                    (float) marginX / wideWidth, (float) marginY / wideHeight);
            drawQuad();
            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    /** The scene's triangles through the lens into the bound target. {@code vignette} and {@code grain} are 1 (on) or 0 (off). */
    private void drawScene(float vignette, float grain) {
        GLES20.glUseProgram(sceneProgram);
        GLES20.glUniformMatrix4fv(uMatrix, 1, false, scene.matrix, 0);
        GLES20.glUniform2f(uInvSize, 1f / width, 1f / height);
        GLES20.glUniform1f(uVig, vignette);
        GLES20.glUniform1f(uGrainAmp, grain);
        GLES20.glUniform2f(uGrainOffset, grainOffset[0], grainOffset[1]);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, grainTexture);
        drawTriangles();
    }

    /** The scene's triangles as they are into the wider copy to blur: the screen's view, shrunk to fit it. */
    private void drawPlainScene() {
        float[] m = scene.matrix, wide = wideMatrix;
        float sx = (float) width / wideWidth, sy = (float) height / wideHeight;
        System.arraycopy(m, 0, wide, 0, 16);
        wide[0] = m[0] * sx;
        wide[4] = m[4] * sx;
        wide[12] = m[12] * sx;
        wide[1] = m[1] * sy;
        wide[5] = m[5] * sy;
        wide[13] = m[13] * sy;
        GLES20.glUseProgram(plainProgram);
        GLES20.glUniformMatrix4fv(uPlainMatrix, 1, false, wide, 0);
        drawTriangles();
    }

    private void drawTriangles() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo);
        int stride = SceneMesh.FLOATS_PER_VERTEX * 4;
        GLES20.glEnableVertexAttribArray(POS);
        GLES20.glVertexAttribPointer(POS, 2, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(COLOR);
        GLES20.glVertexAttribPointer(COLOR, 4, GLES20.GL_FLOAT, false, stride, 8);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, scene.mesh.floats / SceneMesh.FLOATS_PER_VERTEX);
        GLES20.glDisableVertexAttribArray(COLOR);
    }

    private void blurInto(int target, int source, float stepX, float stepY) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[target]);
        GLES20.glUseProgram(blur);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source);
        GLES20.glUniform2f(uStep, stepX, stepY);
        drawQuad();
    }

    private void drawQuad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glEnableVertexAttribArray(POS);
        GLES20.glVertexAttribPointer(POS, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    /** Filtering and wrapping of the bound texture. */
    private static void sampling(int filter, int wrap) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
    }

    // ------------------------------------------------------------------------------- shaders

    /**
     * The plan's triangles through the lens, into the window. The vignette and colour ramp (uVig 1 or 0)
     * come from the lens map. Grain is zero-mean and added last (uGrainAmp 1 or 0); only this sharp layer
     * has it, so it fades where the blurred copy covers it.
     */
    private static String sceneFragment(Look.Post post) {
        return LENS_PRECISION
                + "uniform vec2 uInvSize;\nuniform float uVig;\nuniform float uGrainAmp;\nuniform HIGHP vec2 uGrainOffset;\n"
                + "uniform sampler2D uGrain;\nuniform sampler2D uLens;\nvarying vec4 vColor;\n"
                + "void main() {\n"
                + "  vec3 c = vColor.rgb * mix(vec3(1.0), texture2D(uLens, gl_FragCoord.xy * uInvSize).rgb * 2.0, uVig);\n"
                + "  float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));\n"
                + "  HIGHP vec2 g = gl_FragCoord.xy / " + glsl(Grain.SIZE) + " + uGrainOffset;\n"
                + "  float n = (texture2D(uGrain, g).r - 0.5) * 8.0;\n"
                + "  c += n * (" + glsl(post.grainBase / 255f) + " + " + glsl(post.grainSlope / 255f)
                + " * luma * luma) * uGrainAmp;\n"
                + "  gl_FragColor = vec4(c, 1.0);\n}\n";
    }

    /** One separable Gaussian round, along uStep (one texel across or down). */
    private static String blurFragment(Look.Post post) {
        StringBuilder s = new StringBuilder(PRECISION)
                .append("uniform sampler2D uTex;\nuniform vec2 uStep;\nvarying vec2 vUv;\n")
                .append("void main() {\n  vec4 c = vec4(0.0);\n");
        for (int i = 0; i < post.blurWeights.length; i++) {
            String w = glsl(post.blurWeights[i]), o = glsl(post.blurOffsets[i]);
            if (post.blurOffsets[i] == 0f) {
                s.append("  c += texture2D(uTex, vUv) * ").append(w).append(";\n");
            } else {
                s.append("  c += (texture2D(uTex, vUv + uStep * ").append(o).append(") + texture2D(uTex, vUv - uStep * ")
                        .append(o).append(")) * ").append(w).append(";\n");
            }
        }
        return s.append("  gl_FragColor = c;\n}\n").toString();
    }

    /** The blurred copy through the same lens, see-through in the sharp oval and opaque outside it. */
    private static final String BLEND_FRAGMENT = LENS_PRECISION
            + "uniform sampler2D uTex;\nuniform sampler2D uLens;\nuniform float uVig;\nuniform HIGHP vec4 uBlurArea;\n"
            + "varying HIGHP vec2 vUv;\n"
            + "void main() {\n"
            + "  vec4 lens = texture2D(uLens, vUv);\n"
            + "  HIGHP vec2 uv = vUv * uBlurArea.xy + uBlurArea.zw;\n"
            + "  vec3 blurred = texture2D(uTex, uv).rgb;\n"
            + "  gl_FragColor = vec4(blurred * mix(vec3(1.0), lens.rgb * 2.0, uVig), lens.a);\n}\n";

    /** A GLSL ES 1.00 float literal. Float.toString always writes a decimal point ("2.0", "0.42", "1.0E-5"). */
    private static String glsl(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) throw new IllegalArgumentException("not a number: " + v);
        return Float.toString(v);
    }

    private static int program(String vertex, String fragment) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vertex));
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fragment));
        GLES20.glBindAttribLocation(program, POS, "aPos");
        GLES20.glBindAttribLocation(program, COLOR, "aColor");
        GLES20.glLinkProgram(program);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("link: " + GLES20.glGetProgramInfoLog(program));
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("compile: " + GLES20.glGetShaderInfoLog(shader));
        return shader;
    }

    private static FloatBuffer floats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
}
