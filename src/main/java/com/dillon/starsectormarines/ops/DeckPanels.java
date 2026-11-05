package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.CanvasContext;

/**
 * The bridge's physical buttons and indicator lamps, drawn over the painted consoles. The
 * painter is not trusted with hundreds of tiny keys, so the plate leaves plain boards and each
 * button's corners come from the Blender model through the pack.
 *
 * <p>A board answers to the company where it can: the comms boards ripple as a report arrives,
 * the quartermaster's indicator row shows the payroll runway's tone, and the cowl's buttons
 * count the open actions on the board. Everything else is the watch at work. As with
 * {@link DeckDisplays} none of it is the only place a fact appears, and everything is a pure
 * function of the time and {@link Signals} given, so snapshots repeat.
 */
final class DeckPanels {
    /** How the payroll runway stands, as the command strip tones it. */
    enum Tone {
        NONE, GOOD, ACCENT, DANGER;

        /** The tone named in the command strip's runway classes. */
        static Tone of(String classes) {
            if (classes == null) return NONE;
            if (classes.contains("tone-danger")) return DANGER;
            if (classes.contains("tone-accent")) return ACCENT;
            if (classes.contains("tone-good")) return GOOD;
            return NONE;
        }
    }

    /** What the boards answer to: how strongly a report is arriving (0..1), open actions, the runway. */
    record Signals(float arrival, int actions, Tone runway) { }

    /** What a button shows at one instant: its colour, and 0 for dark up to 1 for full. */
    record Light(int rgb, float level) { }

    private static final int BODY = rgb(52, 54, 58);
    private static final int AMBER = rgb(255, 176, 60);
    private static final int GREEN = rgb(110, 225, 120);
    private static final int PALE = rgb(235, 232, 205);
    private static final int RED = rgb(235, 70, 45);
    private static final Light DARK = new Light(BODY, 0f);

    private final DeckDisplays.Ink bodies = new DeckDisplays.Ink();
    private final DeckDisplays.Ink halos = new DeckDisplays.Ink();
    private final DeckDisplays.Ink lamps = new DeckDisplays.Ink();

    void draw(CanvasContext context, CommandDeckLayout layout, CommandDeckLayout.Fit fit, float time, Signals signals) {
        float now = Float.isFinite(time) ? time : 0f;
        for (CommandDeckLayout.Panel panel : layout.panels()) {
            int seed = panel.id().hashCode();
            int count = panel.count();
            for (int i = 0; i < count; i++) {
                float[] q = corners(panel, i, fit);
                bodies.quad(BODY, 255, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7]);
                Light light = light(panel.role(), seed, i, count, now, signals);
                if (light.level() <= 0.02f) continue;
                halo(q, light);
                lamps.quad(light.rgb(), Math.round(255 * light.level()), q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7]);
            }
        }
        bodies.flush(context);
        halos.flush(context);
        lamps.flush(context);
    }

    /** A soft-edged square of the lamp's colour round it, so a lit key lifts the panel beside it. */
    private void halo(float[] q, Light light) {
        float cx = (q[0] + q[2] + q[4] + q[6]) * 0.25f, cy = (q[1] + q[3] + q[5] + q[7]) * 0.25f;
        float[] e = new float[8];
        for (int k = 0; k < 8; k += 2) {
            e[k] = cx + (q[k] - cx) * 1.9f;
            e[k + 1] = cy + (q[k + 1] - cy) * 1.9f;
        }
        halos.quad(light.rgb(), Math.round(46 * light.level()), e[0], e[1], e[2], e[3], e[4], e[5], e[6], e[7]);
    }

    private static float[] corners(CommandDeckLayout.Panel panel, int button, CommandDeckLayout.Fit fit) {
        float[] q = new float[8];
        for (int k = 0; k < 4; k++) {
            q[k * 2] = fit.x(panel.x(button, k));
            q[k * 2 + 1] = fit.y(panel.y(button, k));
        }
        return q;
    }

    /** What one button of a board shows now. */
    static Light light(String role, int seed, int index, int count, float time, Signals signals) {
        switch (role == null ? "" : role) {
            case "comms" -> {
                if (signals.arrival() > 0.02f) {
                    float wave = frac(index / (float) Math.max(1, count) - time * 1.6f);
                    return wave < 0.35f ? new Light(PALE, Math.min(1f, 0.4f + signals.arrival())) : idle(seed, index, time);
                }
                return idle(seed, index, time);
            }
            case "finance" -> {
                return switch (signals.runway()) {
                    case GOOD -> new Light(GREEN, 0.9f);
                    case ACCENT -> new Light(AMBER, 0.9f);
                    case DANGER -> new Light(RED, frac(time * 1.1f + index * 0.04f) < 0.55f ? 1f : 0.2f);
                    case NONE -> DARK;
                };
            }
            case "actions" -> {
                return index < signals.actions() ? new Light(AMBER, 0.95f) : DARK;
            }
            case "systems" -> {
                float h = beat(seed, index, time, 7f, 19f);
                if (h < 0.05f) return new Light(AMBER, 0.95f);
                return h < 0.7f ? new Light(GREEN, 0.85f) : DARK;
            }
            case "flight" -> {
                float h = DeckDisplays.hash(index, seed);
                return h < 0.5f ? new Light(h < 0.2f ? AMBER : GREEN, 0.85f) : DARK;
            }
            default -> {
                return idle(seed, index, time);
            }
        }
    }

    /** The watch at work: a few keys on, changing every few seconds, each on its own beat. */
    private static Light idle(int seed, int index, float time) {
        float h = beat(seed, index, time, 2.5f, 9f);
        if (h >= 0.42f) return DARK;
        return new Light(h < 0.2f ? AMBER : h < 0.36f ? GREEN : PALE, 0.9f);
    }

    /** A value in [0, 1) that holds for this button's own period, between the two bounds in seconds. */
    private static float beat(int seed, int index, float time, float shortest, float longest) {
        float period = shortest + (longest - shortest) * DeckDisplays.hash(index, seed ^ 31);
        float phase = 40f * DeckDisplays.hash(index, seed ^ 57);
        return DeckDisplays.hash((int) Math.floor((time + phase) / period), seed + index * 131);
    }

    private static float frac(float value) {
        return value - (float) Math.floor(value);
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
