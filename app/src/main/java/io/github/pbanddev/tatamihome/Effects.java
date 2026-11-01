package io.github.pbanddev.tatamihome;

import android.content.Context;
import android.content.SharedPreferences;

/** Which floor plan effects are on. All on by default; the choices are kept on the TV. */
public final class Effects {
    public static final String GRAIN = "grain";
    public static final String VIGNETTE = "vignette";
    public static final String TILT_SHIFT = "tilt_shift";
    public static final String DOORS = "doors";
    public static final String FIXTURES = "fixtures";
    public static final String CAMERA = "camera";

    /** Every effect in the order the settings list shows them. */
    public static final String[] ALL = {GRAIN, VIGNETTE, TILT_SHIFT, DOORS, FIXTURES, CAMERA};

    private static final String FILE = "effects";

    public final boolean grain, vignette, tiltShift, doors, fixtures, camera;

    private Effects(SharedPreferences p) {
        grain = p.getBoolean(GRAIN, true);
        vignette = p.getBoolean(VIGNETTE, true);
        tiltShift = p.getBoolean(TILT_SHIFT, true);
        doors = p.getBoolean(DOORS, true);
        fixtures = p.getBoolean(FIXTURES, true);
        camera = p.getBoolean(CAMERA, true);
    }

    public boolean isOn(String key) {
        switch (key) {
            case GRAIN: return grain;
            case VIGNETTE: return vignette;
            case TILT_SHIFT: return tiltShift;
            case DOORS: return doors;
            case FIXTURES: return fixtures;
            case CAMERA: return camera;
            default: throw new IllegalArgumentException(key);
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static Effects load(Context context) {
        return new Effects(prefs(context));
    }

    public static void set(Context context, String key, boolean on) {
        prefs(context).edit().putBoolean(key, on).apply();
    }
}
