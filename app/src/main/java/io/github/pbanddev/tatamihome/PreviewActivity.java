package io.github.pbanddev.tatamihome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * Shows the floor plan full screen from its own tile, without changing the TV's screensaver.
 * OK or Menu opens the effects; Back closes.
 */
public final class PreviewActivity extends Activity {
    private FloorPlanView plan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        plan = new FloorPlanView(this);
        setContentView(plan);
    }

    @Override
    protected void onResume() {
        super.onResume();
        plan.setEffects(Effects.load(this));   // picks up changes made in the effects list
        plan.onResume();
    }

    @Override
    protected void onPause() {
        plan.onPause();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (opensEffects(keyCode)) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Only a press that began here: a launcher that opens apps on the press leaves its release to this screen, which
     * would otherwise open the effects at once.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (opensEffects(keyCode)) {
            if (event.isTracking() && !event.isCanceled()) startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private static boolean opensEffects(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MENU;
    }
}
