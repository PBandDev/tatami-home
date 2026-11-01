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
 * Draws the floor plan with OpenGL ES 2, all on the GPU:
 * <ol>
 *   <li>the scene at full size (the window asks for 4x MSAA, so tilted walls stay smooth);</li>
 *   <li>tilt-shift: the scene again at full size, averaged down to 1/{@value #BLUR_DIVISOR} size,
 *       a real separable Gaussian blur there, blended over the top and bottom of the screen;</li>
 *   <li>one last pass for film grain and the vignette.</li>
 * </ol>
 * Nothing is copied from the CPU to the GPU each frame except the scene's triangles.
 */
final class FloorPlanGlRenderer implements GLSurfaceView.Renderer {
    private static final long SEED = 7L;
    private static final int DEPTH = 6;
    private static final int BLUR_DIVISOR = 4;
    private static final int BLUR_PASSES = 2;
    private static final float FLOOR_R = 0x1C / 255f, FLOOR_G = 0x1A / 255f, FLOOR_B = 0x14 / 255f;

    private static final String PRECISION =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n";
    private static final String SCENE_VERTEX =
            "uniform mat4 uMatrix;\nattribute vec2 aPos;\nattribute vec4 aColor;\nvarying vec4 vColor;\n"
                    + "void main() { vColor = aColor; gl_Position = uMatrix * vec4(aPos, 0.0, 1.0); }\n";
    private static final String SCENE_FRAGMENT = PRECISION
            + "varying vec4 vColor;\nvoid main() { gl_FragColor = vColor; }\n";
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
    /** 9-tap Gaussian in 5 fetches (linear sampling between texels). */
    private static final String BLUR_FRAGMENT = PRECISION
            + "uniform sampler2D uTex;\nuniform vec2 uStep;\nvarying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec4 c = texture2D(uTex, vUv) * 0.2270270270;\n"
            + "  c += texture2D(uTex, vUv + uStep * 1.3846153846) * 0.3162162162;\n"
            + "  c += texture2D(uTex, vUv - uStep * 1.3846153846) * 0.3162162162;\n"
            + "  c += texture2D(uTex, vUv + uStep * 3.2307692308) * 0.0702702703;\n"
            + "  c += texture2D(uTex, vUv - uStep * 3.2307692308) * 0.0702702703;\n"
            + "  gl_FragColor = c;\n}\n";
    /** The blurred scene, opaque at the top and bottom edges, gone in the middle third. */
    private static final String BAND_FRAGMENT = PRECISION
            + "uniform sampler2D uTex;\nvarying vec2 vUv;\n"
            + "void main() {\n"
            + "  float a = max(1.0 - smoothstep(0.0, 0.36, vUv.y), smoothstep(0.64, 1.0, vUv.y));\n"
            + "  gl_FragColor = vec4(texture2D(uTex, vUv).rgb, a);\n}\n";
    /** Grain and vignette in one pass, premultiplied: out = src + dst * (1 - src.a). */
    private static final String FINISH_FRAGMENT = PRECISION
            + "uniform vec2 uSize;\nuniform float uSeed;\nuniform float uGrain;\nuniform float uVignette;\nvarying vec2 vUv;\n"
            + "float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }\n"
            + "void main() {\n"
            + "  vec2 px = floor(vUv * uSize);\n"
            + "  float n = hash(px + uSeed);\n"
            + "  float g = uGrain * (0.025 + 0.05 * hash(px * 1.7 + uSeed + 3.1));\n"
            + "  float r = length((vUv - 0.5) * uSize) / (0.5 * length(uSize));\n"
            + "  float v = uVignette * 0.69 * smoothstep(0.55, 1.0, r);\n"
            + "  gl_FragColor = vec4(vec3(n) * g * (1.0 - v), 1.0 - (1.0 - g) * (1.0 - v));\n}\n";

    private final FloorPlan plan = new FloorPlan(SEED, DEPTH);
    private final Frame frame = new Frame(plan.rooms);
    private final SceneMesh mesh = new SceneMesh(plan.rooms);
    private final FloatBuffer meshBuffer;
    private final FloatBuffer quadBuffer;
    private final float[] matrix = new float[16];
    /** Index 0 is the full-size copy of the scene; 1 and 2 are the 1/4-size blur targets. */
    private final int[] texture = new int[3];
    private final int[] framebuffer = new int[3];
    private volatile Effects effects;
    private int width, height, blurWidth, blurHeight, frameNumber;
    private int scene, downsample, blur, band, finish, meshVbo, quadVbo;

    FloorPlanGlRenderer(Effects effects) {
        this.effects = effects;
        meshBuffer = floats(mesh.data.length);
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
        GLES20.glClearColor(FLOOR_R, FLOOR_G, FLOOR_B, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void fail(Throwable failure) {
        broken = true;
        Log.e("Tatami", "the floor plan cannot draw on this context", failure);
    }

    private void setUp() {
        scene = program(SCENE_VERTEX, SCENE_FRAGMENT);
        downsample = program(QUAD_VERTEX, DOWNSAMPLE_FRAGMENT);
        blur = program(QUAD_VERTEX, BLUR_FRAGMENT);
        band = program(QUAD_VERTEX, BAND_FRAGMENT);
        finish = program(QUAD_VERTEX, FINISH_FRAGMENT);
        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        meshVbo = buffers[0];
        quadVbo = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, quadBuffer, GLES20.GL_STATIC_DRAW);
        texture[0] = 0;   // the old context and its targets are gone
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(FLOOR_R, FLOOR_G, FLOOR_B, 1f);
    }

    private void size(int w, int h) {
        blurWidth = Math.max(1, w / BLUR_DIVISOR);
        blurHeight = Math.max(1, h / BLUR_DIVISOR);
        if (texture[0] != 0) {
            GLES20.glDeleteTextures(3, texture, 0);
            GLES20.glDeleteFramebuffers(3, framebuffer, 0);
        }
        GLES20.glGenTextures(3, texture, 0);
        GLES20.glGenFramebuffers(3, framebuffer, 0);
        for (int i = 0; i < 3; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, i == 0 ? w : blurWidth, i == 0 ? h : blurHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, texture[i], 0);
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("render target " + i + " incomplete: " + status);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void draw() {
        Effects fx = effects;
        double t = FloorPlanView.now();
        float pw = Camera.planWidth(width, height), ph = Camera.planHeight(width, height);
        plan.layout(t, -pw / 2, -ph / 2, pw, ph, frame);
        if (fx.fixtures) Fixtures.place(frame, height * 0.018f); else frame.fixtureCount = 0;
        mesh.build(frame, t, height, fx.doors);
        meshBuffer.clear();
        meshBuffer.put(mesh.data, 0, mesh.floats).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.floats * 4, meshBuffer, GLES20.GL_STREAM_DRAW);
        CameraMatrix.build(t, width, height, fx.camera, matrix);

        if (fx.tiltShift) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            drawScene();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[1]);
            GLES20.glViewport(0, 0, blurWidth, blurHeight);
            GLES20.glUseProgram(downsample);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(downsample, "uTex"), 0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(downsample, "uTexel"), 1f / width, 1f / height);
            drawQuad(downsample);
            for (int pass = 0; pass < BLUR_PASSES; pass++) {
                blurInto(2, texture[1], 1f / blurWidth, 0f);
                blurInto(1, texture[2], 0f, 1f / blurHeight);
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawScene();

        if (fx.tiltShift) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(band);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[1]);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(band, "uTex"), 0);
            drawQuad(band);
        }
        if (fx.grain || fx.vignette) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(finish);
            frameNumber = (frameNumber + 1) % 997;
            GLES20.glUniform2f(GLES20.glGetUniformLocation(finish, "uSize"), width, height);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(finish, "uSeed"), frameNumber * 1.37f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(finish, "uGrain"), fx.grain ? 1f : 0f);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(finish, "uVignette"), fx.vignette ? 1f : 0f);
            drawQuad(finish);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawScene() {
        GLES20.glUseProgram(scene);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(scene, "uMatrix"), 1, false, matrix, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshVbo);
        int stride = SceneMesh.FLOATS_PER_VERTEX * 4;
        int pos = GLES20.glGetAttribLocation(scene, "aPos");
        int color = GLES20.glGetAttribLocation(scene, "aColor");
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(color);
        GLES20.glVertexAttribPointer(color, 4, GLES20.GL_FLOAT, false, stride, 8);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.floats / SceneMesh.FLOATS_PER_VERTEX);
        GLES20.glDisableVertexAttribArray(color);
    }

    private void blurInto(int target, int source, float stepX, float stepY) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[target]);
        GLES20.glUseProgram(blur);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(blur, "uTex"), 0);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(blur, "uStep"), stepX, stepY);
        drawQuad(blur);
    }

    private void drawQuad(int program) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        int pos = GLES20.glGetAttribLocation(program, "aPos");
        GLES20.glEnableVertexAttribArray(pos);
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private static int program(String vertex, String fragment) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vertex));
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fragment));
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
