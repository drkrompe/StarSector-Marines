package com.dillon.starsectormarines.battle.warship;

/**
 * One sound a warship asks to be heard this frame, at a place on the ground.
 *
 * <p>The simulation only records the request; whoever presents the battle
 * plays it, so a headless run stays silent and a test can read exactly what
 * was asked for. {@code soundId} is one of the game's own sound ids, taken from
 * the weapon's {@code .wpn} or its projectile spec. Position is in cells.
 */
public record WarshipSoundCue(Kind kind, String soundId, float x, float y, float volume) {

    /** What the sound is for. */
    public enum Kind {
        /** A mount fired a round (thinned for rapid bursts). */
        FIRE,
        /** A lance sweep went from telegraphing to burning. */
        BEAM_LIT,
        /**
         * A lance beam is burning this tick. Emitted every tick it burns, at the
         * beam's tip, because the game's loop sounds play only for frames they are
         * re-requested; the presenter calls {@code playLoop} on each.
         */
        BEAM_BURNING,
        /** A round landed. */
        IMPACT
    }
}
