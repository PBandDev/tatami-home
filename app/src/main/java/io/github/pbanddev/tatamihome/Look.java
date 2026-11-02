package io.github.pbanddev.tatamihome;

/**
 * Every look value in one place: the cut of the plan, paints, walls, tilt, doors, fixtures and the lens.
 * One instance per look ({@link #DEFAULT} now).
 *
 * <p>Motion is not here. The wall speeds, the door speed and the camera's pan and zoom stay in their
 * classes, so a new look never changes the pace.
 *
 * <p>Sizes whose names end in {@code H} are a share of the screen height and become plan units as
 * {@code screenHeight * size}. The camera's zoom then scales them with the plan, like the walls.
 *
 * <p>One block per part of the scene. Each block keeps its own {@code DEFAULT} values, so a change to one
 * block never touches another block's lines.
 */
public final class Look {
    public static final Look DEFAULT = new Look(Plan.DEFAULT, Colours.DEFAULT, Walls.DEFAULT, Camera.DEFAULT,
            Doors.DEFAULT, Fixtures.DEFAULT, Post.DEFAULT);

    public final Plan plan;
    public final Colours colours;
    public final Walls walls;
    public final Camera camera;
    public final Doors doors;
    public final Fixtures fixtures;
    public final Post post;

    private Look(Plan plan, Colours colours, Walls walls, Camera camera, Doors doors, Fixtures fixtures, Post post) {
        this.plan = plan;
        this.colours = colours;
        this.walls = walls;
        this.camera = camera;
        this.doors = doors;
        this.fixtures = fixtures;
        this.post = post;
    }

    // ------------------------------------------------------------------------------------------ plan

    /**
     * How the plan is cut into rooms: straight cuts and windmills (4 rooms round a centre room). Areas are
     * in H² on screen at the camera's middle zoom (1.075).
     */
    public static final class Plan {
        static final Plan DEFAULT = new Plan(5L, 0.25f, 0.07f, 0.05f, 5f, 2.1f, 0.8f, 0.3f, 0.6f);

        /** Picks the layout; every seeded choice in the scene starts from it. */
        public final long seed;
        /** A part bigger than this is always cut again. */
        public final float splitAboveH2;
        /** A part smaller than this is always a room. In between, a coin decides. */
        public final float stopBelowH2;
        /** No room's short side is ever below this. */
        public final float minSideH;
        /** No room is ever longer than this many times its width. */
        public final float maxAspect;
        /** A part stays a room only if, with every wall at its middle, it is at most this long for its width. */
        public final float roomAspect;
        /** The chance that a part that can be a windmill is one. */
        public final float windmillOdds;
        /** Only parts at least this big become windmills, so the centre room is not too small. */
        public final float windmillMinH2;
        /** The share of windmills that turn clockwise. */
        public final float clockwiseShare;

        Plan(long seed, float splitAboveH2, float stopBelowH2, float minSideH, float maxAspect, float roomAspect,
                float windmillOdds, float windmillMinH2, float clockwiseShare) {
            this.seed = seed;
            this.splitAboveH2 = splitAboveH2;
            this.stopBelowH2 = stopBelowH2;
            this.minSideH = minSideH;
            this.maxAspect = maxAspect;
            this.roomAspect = roomAspect;
            this.windmillOdds = windmillOdds;
            this.windmillMinH2 = windmillMinH2;
            this.clockwiseShare = clockwiseShare;
        }
    }

    // --------------------------------------------------------------------------------------- colours

    /** Room paints and the colours of everything drawn on top of the rooms. ARGB. */
    public static final class Colours {
        /** 22 paints, dark to light, as they look in the middle of the screen, before the lens's colour ramp. */
        static final Colours DEFAULT = new Colours(
                new int[] {
                        0xFF1F120C, 0xFF1B1D0F, 0xFF251E16, 0xFF2F2913, 0xFF344234, 0xFF645535,
                        0xFFA03E1C, 0xFF577048, 0xFF776924, 0xFF6F7420, 0xFF82723D, 0xFF97804A,
                        0xFF669469, 0xFF909156, 0xFFA29240, 0xFF8D987B, 0xFFBA9535, 0xFFC39449,
                        0xFFB3A66C, 0xFFBDB65E, 0xFFCABA82, 0xFFF4ECCA},
                new int[] {
                        3, 5, 5, 5, 2, 4,
                        1, 4, 2, 3, 4, 3,
                        1, 4, 2, 2, 3, 2,
                        3, 2, 2, 2},
                0xFFC4BF74, 0xFF6E9F86, 0xFFAEB099, 0xFF040404, 0xFF1B1D0F);

        /** The room paints. {@link Frame#roomColor} holds an index into this. */
        public final int[] deck;
        /**
         * How often each paint of {@link #deck} comes up: its cards in a deck of 64, one card per room.
         * {@link RoomColours} deals in proportion for other room counts.
         */
        public final int[] cards;
        /** Door bar and swing. */
        public final int door;
        /** Toilet and tub. */
        public final int fixture;
        public final int stairs;
        /** Outlines of doors and fixtures. */
        public final int line;
        /** Shown only if the plan cannot draw. */
        public final int floor;

        Colours(int[] deck, int[] cards, int door, int fixture, int stairs, int line, int floor) {
            this.deck = deck;
            this.cards = cards;
            this.door = door;
            this.fixture = fixture;
            this.stairs = stairs;
            this.line = line;
            this.floor = floor;
        }
    }

    // ----------------------------------------------------------------------------------------- walls

    public static final class Walls {
        /** 0.0094 H on screen at the middle zoom (1.075): 10.2 px at 1080p. Near-black. */
        static final Walls DEFAULT = new Walls(0.0094f / 1.075f, 0xFF040404);

        public final float widthH;
        public final int color;

        Walls(float widthH, int color) {
            this.widthH = widthH;
            this.color = color;
        }
    }

    // ---------------------------------------------------------------------------------------- camera

    public static final class Camera {
        static final Camera DEFAULT = new Camera(11f);

        /** The plan's tilt on screen, in degrees. Positive: walls fall to the right (y points down). */
        public final float angleDeg;

        Camera(float angleDeg) {
            this.angleDeg = angleDeg;
        }
    }

    // ----------------------------------------------------------------------------------------- doors

    /**
     * Doors: a pale bar in the wall, always drawn, and a swing that opens into one room. Sizes are given on
     * screen at the camera's middle zoom, 1.075, so each one is divided by it.
     */
    public static final class Doors {
        static final Doors DEFAULT = new Doors(0.8f, 0.28f, 0.73f, 0.017f / 1.075f,
                0.0361f / 1.075f, 0.00315f / 1.075f, 0.0364f / 1.075f, 0.0020f / 1.075f,
                45f, 56f, 90f, 1.6f);

        /**
         * The share of walls that get a door, tuned so about 0.6 doors per room show on screen
         * ({@code tools/shot.sh --stats}).
         */
        public final float chance;
        /** Where along its wall a door wants its middle, as a share of the wall: each wall draws one in this range. */
        public final float nearAt, farAt;
        /** The least gap between a bar's end and its wall's ends. */
        public final float joinGapH;
        /** The bar in the wall: its length from the hinge, and half its thickness. */
        public final float lengthH, barHalfH;
        /** The swing, from the hinge to the middle of its arc's stroke. */
        public final float radiusH;
        /** The arc and the leaf. */
        public final float lineH;
        /** The opening, in degrees: clamp(middle + amplitude * sin(speed * t + phase), 0, max). */
        public final float swingMiddleDeg, swingAmplitudeDeg, swingMaxDeg;
        /** Each door's phase is one shared phase plus up to this much either way, in radians. 0: all in step. */
        public final float phaseSpread;

        Doors(float chance, float nearAt, float farAt, float joinGapH, float lengthH, float barHalfH, float radiusH,
                float lineH, float swingMiddleDeg, float swingAmplitudeDeg, float swingMaxDeg, float phaseSpread) {
            this.chance = chance;
            this.nearAt = nearAt;
            this.farAt = farAt;
            this.joinGapH = joinGapH;
            this.lengthH = lengthH;
            this.barHalfH = barHalfH;
            this.radiusH = radiusH;
            this.lineH = lineH;
            this.swingMiddleDeg = swingMiddleDeg;
            this.swingAmplitudeDeg = swingAmplitudeDeg;
            this.swingMaxDeg = swingMaxDeg;
            this.phaseSpread = phaseSpread;
        }
    }

    // -------------------------------------------------------------------------------------- fixtures

    /**
     * Toilets, tubs and stairs. Their shapes and sizes are in {@link FixtureShapes}. Those sizes and the ones
     * below are in units of {@link #unitH}, so they never change with the room.
     */
    public static final class Fixtures {
        static final Fixtures DEFAULT = new Fixtures(1f / (1080f * 1.075f), 2.2f, 0.18f,
                new float[] {0.17f, 0.5f, 0.33f}, 31f, 28.5f, 20f);

        /** One unit: 1 px at 1080p on screen at the camera's middle zoom (1.075), as a share of the screen height. */
        public final float unitH;
        /** The outline of the toilet and the tub. */
        public final float line;
        /** The share of rooms that hold a fixture. 0 for none. */
        public final float share;
        /** How often each kind shows up, by kind: tub, toilet, stairs ({@code Fixtures.TUB} and so on). */
        public final float[] weights;
        /** From the toilet's back to the face of the wall it backs onto. */
        public final float toiletGap;
        /** From the tub to the faces of the 2 walls of its corner. */
        public final float tubGap;
        /** Floor to leave between a fixture's open sides and the walls they face, in rooms that allow it. */
        public final float clearance;

        Fixtures(float unitH, float line, float share, float[] weights, float toiletGap, float tubGap, float clearance) {
            this.unitH = unitH;
            this.line = line;
            this.share = share;
            this.weights = weights;
            this.toiletGap = toiletGap;
            this.tubGap = tubGap;
            this.clearance = clearance;
        }
    }

    // ------------------------------------------------------------------------------------------ post

    /**
     * The lens: what the GPU does to the drawn plan, all of it fixed to the screen. A screen position p is
     * measured from the screen centre in half screen heights, x to the right and y up. Colours stay
     * gamma-encoded (0 to 1, as drawn), and so do the blur and the blend.
     */
    public static final class Post {
        static final Post DEFAULT = new Post(
                new Oval(-0.071f, 0.054f, 0.574f, 0.206f, 1.64f), 0.42f,
                new float[] {0.045f, 0f, -0.05f}, new float[] {0f, 0f, 0.03f},
                1.3f, 1.75f, 23.976,
                new Oval(0.030f, 0f, 0.611f, 0.837f, 1.157f), 4,
                new float[] {0.2719f, 0.3235f, 0.0406f}, new float[] {0f, 1.3333f, 3.1650f});

        /** Where the vignette darkens: k = 0 inside the oval, 1 outside it. */
        public final Oval vignetteOval;
        /** Gain {@code 1 - vignette * k^3}: edge middles ~0.90-0.94, corners ~0.6-0.78. */
        public final float vignette;
        /**
         * The colour ramp: r, g and b times {@code exp(tintX * p.x + tintY * p.y)}, bluer to the left and
         * warmer to the right. The vignette switch turns it off too.
         */
        public final float[] tintX, tintY;
        /**
         * Grain std in 8-bit levels: {@code grainBase + grainSlope * luma^2}, luma 0 to 1.
         */
        public final float grainBase, grainSlope;
        /** A new grain pattern this many times a second: film's frame rate. */
        public final double grainFps;
        /** Where the blurred copy shows: its weight is 0 inside the oval, 1 outside it. */
        public final Oval blurOval;
        /** The blurred copy is this many times smaller than the screen. */
        public final int blurDivisor;
        /**
         * One separable Gaussian round on the small copy: {@code blurWeights[i]} at {@code +-blurOffsets[i]}
         * of its texels (each tap between 2 texels, read bilinear). Sigma ~6 px at 1080p, 0.0056 H.
         */
        public final float[] blurWeights, blurOffsets;

        Post(Oval vignetteOval, float vignette, float[] tintX, float[] tintY,
                float grainBase, float grainSlope, double grainFps,
                Oval blurOval, int blurDivisor, float[] blurWeights, float[] blurOffsets) {
            this.vignetteOval = vignetteOval;
            this.vignette = vignette;
            this.tintX = tintX;
            this.tintY = tintY;
            this.grainBase = grainBase;
            this.grainSlope = grainSlope;
            this.grainFps = grainFps;
            this.blurOval = blurOval;
            this.blurDivisor = blurDivisor;
            this.blurWeights = blurWeights;
            this.blurOffsets = blurOffsets;
        }

        /** {@code smoothstep(inner, outer, length(vec2((p.x - x) * scaleX, p.y - y)))}: 0 in the middle, 1 out. */
        public static final class Oval {
            public final float x, y, scaleX, inner, outer;

            Oval(float x, float y, float scaleX, float inner, float outer) {
                this.x = x;
                this.y = y;
                this.scaleX = scaleX;
                this.inner = inner;
                this.outer = outer;
            }
        }
    }
}
