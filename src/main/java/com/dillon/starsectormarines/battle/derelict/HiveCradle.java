package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockCradle;
import com.dillon.starsectormarines.battle.drone.FlockHatch;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

/**
 * The hive cradle, derelict security's layer guardian (`ruins-guardians.md`): an armoured launch housing in an interior
 * light well that keeps putting up drones for its flock until it is destroyed. Its fight is a rhythm the company can
 * read. It is a hardened cradle with a fixed cadence and keeps every cradle's rules: it stands shut and heavily plated, building drones into its bay; once a cycle, when its flock is short, its
 * lamps warn for {@link #WARNING_SECONDS} and its hatch opens for {@link #OPEN_SECONDS} to launch, and while the hatch
 * is open its plate is no heavier than an ordinary cradle's. Fed through its layer's junction like the zone it
 * stands in, it goes shut and cold when that branch is thrown away from it ({@link SecurityZone#powerDown}) or its
 * zone's node is destroyed ({@link FlockCommandPort#nodeStands}): it builds and launches nothing, but its plate stays
 * shut.
 *
 * <p>This class holds the rules (where it stands, its hatch cycle and its plate); the battle seam spawns it and its
 * flock. Numbers are UNTUNED: they wait on the owner playing a derelict battle, never on autopilot runs.
 */
public final class HiveCradle {

    /** A cycle between launches: the hive puts its flock back up about once a minute (owner card). UNTUNED. */
    public static final float CYCLE_SECONDS = 60f;
    /** Lamps and tone before the hatch opens: the company's cue to bring its anti-armour up. UNTUNED. */
    public static final float WARNING_SECONDS = 3f;
    /** How long the hatch stays open, launching; the window in which the hive is soft. UNTUNED. */
    public static final float OPEN_SECONDS = 8f;
    /** Drones launched per second while the hatch is open, while the bay holds any. UNTUNED. */
    public static final float LAUNCH_PER_SECOND = 3f;
    /** Drones the bay holds: one flock's worth. */
    public static final int BAY = DerelictSecurity.FLOCK_SIZE;
    /** Seconds to build one drone into the bay: a full bay in about one cycle. UNTUNED. */
    public static final float BUILD_SECONDS = CYCLE_SECONDS / BAY;

    public static final float MAX_STRUCTURE = 900f;
    public static final float ARMOR_CAPACITY = 600f;
    /** Plate rating with the hatch shut: small arms do almost nothing; rockets and charges still tell. UNTUNED. */
    public static final float SHUT_ARMOR_RATING = 40f;
    /** Plate rating with the hatch open: an ordinary cradle's. */
    public static final float OPEN_ARMOR_RATING = FlockCradle.ARMOR_RATING;

    private HiveCradle() {}

    /**
     * The hive's housing at {@code (cellX, cellY)}: a cradle's structure, targeted, armoured and demolished as one is,
     * heavier and plated shut until its hatch opens.
     */
    public static EntitySpec create(String id, Faction side, int cellX, int cellY) {
        return FlockCradle.create(id, side, cellX, cellY)
                .health(MAX_STRUCTURE)
                .armor(ARMOR_CAPACITY, SHUT_ARMOR_RATING);
    }

    /**
     * The court the hive stands in, or null for a site without one. A layer guardian stands at an extensive or vast
     * site (`ruins-guardian-placement` a), on the deepest interior layer before the vault's, in that layer's court:
     * inside the megastructure a court is a light well, open sky ringed by roofed halls, so the hive's flock is kept
     * in its well and the court, the inner lift point, is held by it. Searches back toward the entrance for the
     * deepest such layer with a court.
     */
    public static RuinsDelve.Court site(RuinsDelve delve, RuinsGrade grade) {
        if (grade.ordinal() < RuinsGrade.EXTENSIVE.ordinal()) return null;
        RuinsDelve.Interior interior = delve.interior();
        if (interior == null) return null;
        for (int i = delve.layerCount() - 2; i >= 0 && interior.contains(i); i--) {
            RuinsDelve.Court court = delve.layers().get(i).court();
            if (court != null) return court;
        }
        return null;
    }

    /** Where the hatch is in its cycle. */
    public enum HatchState {
        /** Plated and building. */
        SHUT,
        /** Lamps lit and the tone sounding; still plated. */
        WARNING,
        /** Open and launching; soft. */
        OPEN
    }

    /**
     * One hive's hatch and bay. Each tick takes whether its branch is powered and how many of its flock are up, and
     * returns how many drones it launches that tick.
     */
    public static final class Hatch implements FlockHatch {
        private HatchState state = HatchState.SHUT;
        private int bay = BAY;
        private float cycle;
        private float build;
        private float phase;
        private float launch;

        /**
         * Advances the hatch by {@code dt} seconds.
         *
         * @param powered    whether the hive's junction feeds it
         * @param nodeStands whether its zone's control node stands
         * @param airborne   the flock's drones now up
         * @return the drones launched this tick
         */
        @Override
        public int tick(float dt, boolean powered, boolean nodeStands, int airborne) {
            if (!powered || !nodeStands) {
                // Cold: the hatch shuts and the clocks stop; the bay keeps what it built.
                state = HatchState.SHUT;
                cycle = 0f;
                build = 0f;
                phase = 0f;
                launch = 0f;
                return 0;
            }
            if (bay < BAY) {
                build += dt;
                while (build >= BUILD_SECONDS && bay < BAY) {
                    build -= BUILD_SECONDS;
                    bay++;
                }
            } else {
                build = 0f;
            }
            switch (state) {
                case SHUT -> {
                    cycle = Math.min(cycle + dt, CYCLE_SECONDS);
                    // Ready once the cycle is up; it opens only for a flock that is short and a bay that holds any.
                    if (cycle >= CYCLE_SECONDS && airborne < BAY && bay > 0) {
                        state = HatchState.WARNING;
                        phase = 0f;
                    }
                    return 0;
                }
                case WARNING -> {
                    phase += dt;
                    if (phase >= WARNING_SECONDS) {
                        state = HatchState.OPEN;
                        phase = 0f;
                        launch = 0f;
                    }
                    return 0;
                }
                default -> {
                    phase += dt;
                    launch += dt * LAUNCH_PER_SECOND;
                    int want = Math.min((int) launch, Math.min(bay, Math.max(0, BAY - airborne)));
                    launch -= (int) launch;
                    bay -= want;
                    // The window is fixed, so it reads the same however few drones it had to put up.
                    if (phase >= OPEN_SECONDS) {
                        state = HatchState.SHUT;
                        cycle = 0f;
                        phase = 0f;
                    }
                    return want;
                }
            }
        }

        public HatchState state() { return state; }

        /** Drones in the bay, built and waiting. */
        public int bay() { return bay; }

        /** The plate rating the hive takes hits on now. */
        @Override
        public float armorRating() {
            return state == HatchState.OPEN ? OPEN_ARMOR_RATING : SHUT_ARMOR_RATING;
        }
    }
}
