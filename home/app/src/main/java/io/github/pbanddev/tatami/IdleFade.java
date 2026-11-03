package io.github.pbanddev.tatami;

import android.view.KeyEvent;

/**
 * Which keys Home sees around its idle fade, when the UI has faded and only the floor plan shows. The first key
 * brings the UI back. A navigation key that does so does nothing else: Home never sees its repeats or its release,
 * so OK opens no app and an arrow moves no focus. Any other key (volume, mute, media) wakes the UI and still does
 * its own job.
 */
public final class IdleFade {
    private static final int NONE = KeyEvent.KEYCODE_UNKNOWN;

    private boolean faded;
    /** The navigation key that woke the UI, until its release. */
    private int waking = NONE;

    public void fade() {
        faded = true;
    }

    public boolean isFaded() {
        return faded;
    }

    /** The UI shows again with nothing pending, as when Home leaves the screen. */
    public void reset() {
        faded = false;
        waking = NONE;
    }

    /**
     * @return true when Home must not see this key. A key pressed while the UI has faded wakes it, so
     *     {@link #isFaded()} is false afterwards.
     */
    public boolean consume(int action, int keyCode, int repeatCount) {
        if (waking != NONE && keyCode == waking) {
            if (action == KeyEvent.ACTION_UP) {
                waking = NONE;
                return true;
            }
            if (repeatCount > 0) return true;
            waking = NONE;   // a new press: the waking key's release was lost (focus moved, a dialog took it)
        }
        if (!faded || action != KeyEvent.ACTION_DOWN) return false;
        faded = false;
        if (!navigation(keyCode)) return false;
        waking = keyCode;
        return true;
    }

    private static boolean navigation(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
                return true;
            default:
                return false;
        }
    }
}
