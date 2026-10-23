package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.LoadingProgress;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Render-free state of the screen a battle shows while its textures load
 * ({@code battle-textures.md}, Loading screen): a card with the operation's name, where it is
 * fought, a bar, the loader's stage and an activity spinner.
 *
 * <p>The spinner runs on frame time rather than on progress, so it keeps moving through a long
 * step while the bar stands still; if it stops, the GL thread has stopped yielding.
 */
final class BattleLoadingOverlayModel {

    /** Dots in the spinner; one is lit at a time. */
    static final int DOTS = 3;
    /** Seconds for the lit dot to walk the row once. */
    static final float SPIN_PERIOD_SECONDS = 0.9f;

    private static final String DOT = "spin-dot";
    private static final String DOT_LIT = "spin-dot spin-lit";

    private final MutableSignal<String> title;
    private final MutableSignal<String> area;
    private final MutableSignal<String> stage;
    private final MutableSignal<String> progressStyle;
    private final MutableSignal<String>[] dots;
    private float spinSeconds;

    @SuppressWarnings("unchecked")
    BattleLoadingOverlayModel(Reactor reactor) {
        title = reactor.signal("");
        area = reactor.signal("");
        stage = reactor.signal("");
        progressStyle = reactor.signal("width: 0%;");
        dots = new MutableSignal[DOTS];
        for (int i = 0; i < DOTS; i++) dots[i] = reactor.signal(i == 0 ? DOT_LIT : DOT);
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", title);
        props.put("area", area);
        props.put("stage", stage);
        props.put("progressStyle", progressStyle);
        for (int i = 0; i < DOTS; i++) props.put("dot" + i + "Classes", dots[i]);
        return props;
    }

    /** The operation's name and where it is fought. */
    void heading(String operation, String where) {
        title.set(operation == null ? "" : operation);
        area.set(where == null ? "" : where);
    }

    /** Moves the spinner on by {@code realDt} and shows {@code progress}. */
    void update(float realDt, LoadingProgress progress) {
        spinSeconds = (spinSeconds + Math.max(0f, realDt)) % SPIN_PERIOD_SECONDS;
        int lit = litDot(spinSeconds);
        for (int i = 0; i < DOTS; i++) dots[i].set(i == lit ? DOT_LIT : DOT);
        int percent = Math.round(100f * progress.fraction());
        progressStyle.set("width: " + percent + "%;");
        stage.set(progress.stage());
    }

    /** Which dot is lit {@code seconds} into a spin. */
    static int litDot(float seconds) {
        float phase = (seconds % SPIN_PERIOD_SECONDS) / SPIN_PERIOD_SECONDS;
        return Math.min(DOTS - 1, (int) (phase * DOTS));
    }

    String stage() {
        return stage.get();
    }

    String progressStyle() {
        return progressStyle.get();
    }
}
