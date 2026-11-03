package io.github.pbanddev.tatami;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import org.junit.Test;

public class IdleFadeTest {
    private static final int DOWN = KeyEvent.ACTION_DOWN;
    private static final int UP = KeyEvent.ACTION_UP;

    @Test
    public void keysReachHomeWhileTheUiShows() {
        IdleFade fade = new IdleFade();
        assertFalse(fade.consume(DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assertFalse(fade.consume(UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
    }

    @Test
    public void aNavigationKeyOnlyWakesTheUiAndHomeNeverSeesItsRepeatsOrRelease() {
        IdleFade fade = new IdleFade();
        fade.fade();
        assertTrue(fade.consume(DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        assertFalse(fade.isFaded());
        assertTrue(fade.consume(DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1));
        assertTrue(fade.consume(UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        assertFalse(fade.consume(DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
    }

    @Test
    public void volumeWakesTheUiAndStillChangesTheVolume() {
        IdleFade fade = new IdleFade();
        fade.fade();
        assertFalse(fade.consume(DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0));
        assertFalse(fade.isFaded());
        assertFalse(fade.consume(DOWN, KeyEvent.KEYCODE_VOLUME_UP, 1));
        assertFalse(fade.consume(UP, KeyEvent.KEYCODE_VOLUME_UP, 0));
    }

    @Test
    public void aNewPressReachesHomeWhenTheWakingKeysReleaseWasLost() {
        IdleFade fade = new IdleFade();
        fade.fade();
        assertTrue(fade.consume(DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        assertFalse(fade.consume(DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0));
    }

    @Test
    public void aReleaseFromBeforeTheFadeDoesNotWakeTheUi() {
        IdleFade fade = new IdleFade();
        fade.fade();
        assertFalse(fade.consume(UP, KeyEvent.KEYCODE_DPAD_LEFT, 0));
        assertTrue(fade.isFaded());
    }
}
