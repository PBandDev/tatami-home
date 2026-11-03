package io.github.pbanddev.tatami;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The floor plan behind Home's UI, and Home's idle fade. The plan goes under the UI's view, in the same parent,
 * so the UI (with a dark layer at its bottom) can fade as one while the plan keeps moving. After the idle time
 * with no key the UI fades out and only the plan shows; the first key brings it back ({@link IdleFade}).
 *
 * <p>The host calls {@link #resume()}, {@link #pause()}, {@link #onUserInteraction()} and
 * {@link #onWindowFocusChanged(boolean)} from the activity's own methods, {@link #consumeKey(KeyEvent)} first thing
 * in dispatchKeyEvent, and {@link #consumeMotion(MotionEvent)} first thing in dispatchTouchEvent and
 * dispatchGenericMotionEvent.
 */
public final class FloorPlanBackdrop {
    private static final long FADE_OUT_MS = 1500;
    private static final long FADE_IN_MS = 250;

    private final Activity activity;
    private final View ui;
    private final FloorPlanView plan;
    private final long idleMs;
    private final IdleFade fade = new IdleFade();
    private final Runnable fadeOut = this::fadeOut;
    private boolean resumed;
    /**
     * A screensaver is up. Before Android 11 it is a window over Home, which stays resumed under it, so without this
     * two plans would draw at 60 fps for as long as the screensaver runs.
     */
    private boolean dreaming;
    private boolean drawing;
    private boolean listening;
    private final BroadcastReceiver dreams = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dreaming = Intent.ACTION_DREAMING_STARTED.equals(intent.getAction());
            updatePlan();
        }
    };

    /**
     * @param ui          the activity's full-screen UI; it must be a child of a FrameLayout, and its own background
     *                    is cleared so the plan shows through
     * @param dimPercent  how dark the plan is behind the UI, 0 to 90
     * @param idleMinutes minutes without a key before the UI fades; 0 never fades it
     */
    public FloorPlanBackdrop(Activity activity, FrameLayout ui, int dimPercent, int idleMinutes) {
        this.activity = activity;
        this.ui = ui;
        this.idleMs = idleMinutes * 60_000L;
        plan = new FloorPlanView(activity);
        ((ViewGroup) ui.getParent()).addView(plan, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ui.setBackground(null);
        if (dimPercent > 0) {
            View dim = new View(activity);
            dim.setBackgroundColor((dimPercent * 255 / 100) << 24);
            dim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ui.addView(dim, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    public void resume() {
        resumed = true;
        if (!listening) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_DREAMING_STARTED);
            filter.addAction(Intent.ACTION_DREAMING_STOPPED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(dreams, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(dreams, filter);
            }
            listening = true;
        }
        dreaming = false;
        updatePlan();
        restartIdle();
    }

    /** Home left the screen: stop drawing, and come back with the UI showing. */
    public void pause() {
        resumed = false;
        if (listening) {
            activity.unregisterReceiver(dreams);
            listening = false;
        }
        updatePlan();
        ui.removeCallbacks(fadeOut);
        fade.reset();
        ui.animate().cancel();
        ui.setAlpha(1f);
    }

    public void onUserInteraction() {
        restartIdle();
    }

    /** Keys inside a popup or dialog never reach Home, so the idle time starts over once Home has the keys again. */
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) restartIdle();
    }

    /** @return true when Home must not see this key: it only woke the UI. */
    public boolean consumeKey(KeyEvent event) {
        boolean wasFaded = fade.isFaded();
        boolean eaten = fade.consume(event.getAction(), event.getKeyCode(), event.getRepeatCount());
        if (wasFaded && !fade.isFaded()) {
            show();
        } else if (!eaten && event.getAction() == KeyEvent.ACTION_DOWN) {
            // Some of Home's key paths (carrying a tile, vendor keys) return before onUserInteraction runs.
            restartIdle();
        }
        return eaten;
    }

    /**
     * A touch, a mouse or an air-mouse pointer. The faded UI is invisible but still takes clicks, so the first
     * pointer event only brings it back.
     *
     * @return true when Home must not see this event
     */
    public boolean consumeMotion(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false;
        if (!fade.isFaded()) {
            restartIdle();
            return false;
        }
        fade.reset();
        show();
        return true;
    }

    private void updatePlan() {
        boolean draw = resumed && !dreaming;
        if (draw == drawing) return;
        drawing = draw;
        if (draw) {
            plan.setEffects(Effects.load(activity));   // picks up changes made in the effects list
            plan.onResume();
        } else {
            plan.onPause();
        }
    }

    private void show() {
        ui.animate().cancel();
        ui.animate().alpha(1f).setDuration(FADE_IN_MS).withLayer().start();
        restartIdle();
    }

    private void restartIdle() {
        ui.removeCallbacks(fadeOut);
        if (resumed && idleMs > 0 && !fade.isFaded()) ui.postDelayed(fadeOut, idleMs);
    }

    private void fadeOut() {
        // A popup or dialog over Home has the keys, and Home would never hear the key that should wake it.
        if (!activity.hasWindowFocus()) {
            restartIdle();
            return;
        }
        fade.fade();
        ui.animate().cancel();
        ui.animate().alpha(0f).setDuration(FADE_OUT_MS).withLayer().start();
    }
}
