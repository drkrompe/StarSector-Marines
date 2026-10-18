package com.dillon.starsectormarines.ui.retained;

import java.util.function.LongSupplier;

/**
 * The retained UI's sound cues: a press on a control, a press on a disabled one, and the
 * pointer arriving over a control. Every {@link UiDocument} plays them through the one
 * installed sink, so every page sounds alike without per-screen code. The default sink is
 * silent, which is what headless tests and snapshots get; the game installs its sound player
 * at application load.
 *
 * <p>The ids are the base game's own (Starsector's {@code sounds.json}), so the mod ships no
 * audio for them. A button may name its own press sound with the markup attribute
 * {@code sound}.
 */
public final class UiSounds {

    public static final String PRESSED = "ui_button_pressed";
    public static final String DISABLED_PRESSED = "ui_button_disabled_pressed";
    public static final String HOVER = "ui_button_mouseover";

    /**
     * Shortest gap between two hover sounds. Sweeping the pointer down a list crosses a row
     * every few frames; without a floor that is a machine gun.
     */
    public static final long HOVER_GAP_NANOS = 90_000_000L;

    /** Where a cue is played. */
    @FunctionalInterface
    public interface Sink {
        void play(String soundId);
    }

    public static final Sink SILENT = soundId -> { };

    private static volatile UiSounds installed = new UiSounds(SILENT, System::nanoTime);

    private final Sink sink;
    private final LongSupplier clock;
    private long lastHover = Long.MIN_VALUE;

    public UiSounds(Sink sink, LongSupplier clock) {
        if (sink == null || clock == null) throw new IllegalArgumentException("sink and clock are required");
        this.sink = sink;
        this.clock = clock;
    }

    /** Installs the process-wide cues, e.g. the game's sound player at application load. */
    public static void install(UiSounds sounds) {
        if (sounds == null) throw new IllegalArgumentException("sounds must not be null");
        installed = sounds;
    }

    public static UiSounds installed() {
        return installed;
    }

    /** A control was activated: its own sound if it names one, otherwise the ordinary press. */
    void pressed(UiElement control) {
        String own = control == null ? null : control.sound();
        sink.play(own != null && !own.isEmpty() ? own : PRESSED);
    }

    void disabledPressed() {
        sink.play(DISABLED_PRESSED);
    }

    /** The pointer arrived over a control; dropped when the last hover sound was too recent. */
    void hovered() {
        long now = clock.getAsLong();
        if (lastHover != Long.MIN_VALUE && now - lastHover < HOVER_GAP_NANOS) return;
        lastHover = now;
        sink.play(HOVER);
    }
}
