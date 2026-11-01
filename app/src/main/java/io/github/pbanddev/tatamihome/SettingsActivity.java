package io.github.pbanddev.tatamihome;

import android.app.Activity;
import com.gathin.tvhome.R;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The effects list over the live floor plan, so every switch shows its result at once.
 * Up and Down move one row, OK switches, Back closes. Opened from the preview with OK, and from
 * the TV's screensaver settings where the firmware offers it.
 */
public final class SettingsActivity extends Activity {
    private static final int TEXT = 0xFFEDE6D3;
    private static final int ACCENT = 0xFFE8D87A;

    private FloorPlanView plan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        plan = new FloorPlanView(this);
        root.addView(plan, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(40), dp(32), dp(32), dp(32));
        panel.setBackgroundColor(0xE6151310);

        TextView title = new TextView(this);
        title.setText(R.string.effects_title);
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setPadding(dp(12), 0, 0, dp(16));
        panel.addView(title);

        Effects current = Effects.load(this);
        CheckBox first = null;
        for (String key : Effects.ALL) {
            CheckBox box = new CheckBox(this);
            box.setText(label(key));
            box.setTextColor(TEXT);
            box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            box.setPadding(dp(12), dp(10), dp(12), dp(10));
            box.setBackground(focusBackground());
            box.setChecked(current.isOn(key));
            box.setOnCheckedChangeListener((view, on) -> {
                Effects.set(this, key, on);
                plan.setEffects(Effects.load(this));
            });
            panel.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (first == null) first = box;
        }
        root.addView(panel, new FrameLayout.LayoutParams(dp(460), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
        setContentView(root);
        first.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        plan.onResume();
    }

    @Override
    protected void onPause() {
        plan.onPause();
        super.onPause();
    }

    private int label(String key) {
        switch (key) {
            case Effects.GRAIN: return R.string.effect_grain;
            case Effects.VIGNETTE: return R.string.effect_vignette;
            case Effects.TILT_SHIFT: return R.string.effect_tilt_shift;
            case Effects.DOORS: return R.string.effect_doors;
            case Effects.FIXTURES: return R.string.effect_fixtures;
            case Effects.CAMERA: return R.string.effect_camera;
            default: throw new IllegalArgumentException(key);
        }
    }

    private static StateListDrawable focusBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, new ColorDrawable(0x40E8D87A));
        background.addState(new int[] {}, new ColorDrawable(0x00000000));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
