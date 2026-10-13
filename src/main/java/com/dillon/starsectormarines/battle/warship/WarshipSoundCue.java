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
        IMPACT,
        /** A carrier let its warheads go (the game's own split sound), once for the whole sheaf. */
        SPLIT,
        /** A ship broke through the cloud deck: a deep report, once. Played low and slow. */
        ARRIVAL_REPORT,
        /** A ship's descent settled onto its hover: the pressure wave arriving on the ground. */
        ARRIVAL_SETTLE,
        /** A ship began to climb away: the lift-off roar. */
        DEPARTURE_ROAR,
        /** A ship climbing into the deck: the roar again, higher, as it goes. */
        DEPARTURE_CLIMB,
        /** A ship was sent on a low pass: the burn, once. */
        LOW_PASS_ROAR,
        /** A ship came onto its low-pass line at speed: the report that rolls across the ground, once. */
        LOW_PASS_REPORT,
        /** A planetary shield dome took ship-grade ordnance as flux (rate-limited per dome). */
        DOME_ABSORB,
        /** A dome filled and dropped, or its generator was destroyed: the shield-burnout report. */
        DOME_DROP,
        /** A dome came back after an overload or a reboot. */
        DOME_RISE
    }
}
