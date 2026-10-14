package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.SquadService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import java.util.function.Supplier;

/**
 * Stateless tick consumer that decides which infantry marines are <em>dug in</em>: settled on a
 * {@link FightingPositionIndex fighting-position} cell for {@link #DIG_IN_SECONDS} of simulated
 * time. The per-marine clock lives in the {@code COMBAT} component
 * ({@code CombatService.digInSeconds}), so it is per simulation, deterministic, and cleared with
 * the combat component when the marine dies. Moving, leaving the cell, or the cell leaving the
 * index resets it to zero on that tick.
 *
 * <p>Only infantry of non-mech squads qualify: a marine needs a squad and fire team, a combat
 * body and a mover. Mechs, vehicles, drones, aircraft and non-soldiers are never dug in.
 *
 * <p>The benefit is morale only (see {@link #DUG_IN_MORALE_DRAIN_MULT}); damage, cover, accuracy
 * and {@code FireStance} are untouched. Runs once per sim tick, ahead of {@link SquadMoraleSystem}
 * whose drains read the result.
 *
 * <p>See {@code fighting-positions.md}.
 */
public final class DigInSystem {

    /**
     * Turns fighting positions off for a control run:
     * {@code -Dbattle.infantry.fightingPositions=false}. On by default.
     */
    public static final String PROPERTY = "battle.infantry.fightingPositions";

    /** Simulated seconds a marine must stand settled on a fighting-position cell to be dug in. */
    public static final float DIG_IN_SECONDS = 2f;

    /**
     * Multiplier on the morale drained by a hit or near miss on a dug-in marine. The bonus is
     * composure under fire, not protection: cover already reduces damage and stops rounds, and a
     * dug-in bonus on those would count the same ground twice. The death drain and recovery are
     * not scaled.
     */
    public static final float DUG_IN_MORALE_DRAIN_MULT = 0.5f;

    /**
     * Read once at class load and settable for evidence. Volatile because a scene's control loop
     * flips it between loops on one thread while the unit dispatch reads it from several.
     */
    private static volatile boolean enabled =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    private final UnitRosterService roster;
    private final Supplier<FightingPositionIndex> positions;

    /**
     * @param positions asked afresh every tick, because the index is rebuilt when prepared-ground
     *                  props are destroyed
     */
    public DigInSystem(UnitRosterService roster, Supplier<FightingPositionIndex> positions) {
        this.roster = roster;
        this.positions = positions;
    }

    /** Whether fighting positions are in effect. */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Evidence seam: turns fighting positions on or off for the rest of this JVM, the same way
     * {@code LaneSidestep.setEnabledForEvidence} does. Restore the previous value in a
     * {@code finally}.
     */
    public static void setEnabledForEvidence(boolean value) {
        enabled = value;
    }

    /** Whether a dig-in clock reading counts as dug in. */
    public static boolean isDugIn(float seconds) {
        return seconds >= DIG_IN_SECONDS;
    }

    /** The clock after one tick: advanced and clamped while {@code eligible}, zero otherwise. */
    public static float advance(float seconds, boolean eligible, float dt) {
        if (!eligible) return 0f;
        return Math.min(DIG_IN_SECONDS, seconds + dt);
    }

    /** Factor applied to a hit or near-miss morale drain on a marine. */
    public static float moraleDrainScale(boolean dugIn) {
        return dugIn ? DUG_IN_MORALE_DRAIN_MULT : 1f;
    }

    public void tick(float dt) {
        World world = roster.world();
        SquadService squads = roster.squad();
        CombatService combat = roster.combat();
        MovementService movement = roster.movement();
        boolean on = enabled;
        FightingPositionIndex index = on ? positions.get() : null;
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long id = dense[i];
            if (!combat.has(id)) continue;
            float before = combat.digInSeconds(id);
            boolean eligible = on
                    && squads.hasSquad(id)
                    && isInfantry(squads, id)
                    && movement.has(id)
                    && movement.settled(id)
                    && index.isFightingPositionCell(world.cellX(id), world.cellY(id));
            float after = advance(before, eligible, dt);
            if (after != before) combat.setDigInSeconds(id, after);
        }
    }

    private boolean isInfantry(SquadService squads, long id) {
        Squad squad = roster.getSquad(squads.squadId(id));
        return squad != null && !squad.isMechSquad();
    }
}
