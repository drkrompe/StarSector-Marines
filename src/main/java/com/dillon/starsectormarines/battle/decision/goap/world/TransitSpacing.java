package com.dillon.starsectormarines.battle.decision.goap.world;

import com.dillon.starsectormarines.battle.decision.goap.action.EnterZone;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.EntryGate;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

/**
 * Keeps squads on the same approach apart, so a force sent to one target
 * arrives as a procession rather than a mob.
 *
 * <p>Measured on the Conquest fixtures, the marine clumps are not at the door:
 * most squads standing within a few cells of another were mid-walk, more than
 * eighteen cells from the room they were sent to. Every squad leaves the
 * landing together and takes the same route, so they arrive as one body. A
 * squad that finds another squad already on its way, a little ahead of it
 * toward the same destination and closer than {@link #SEPARATION_CELLS},
 * waits where it stands until that gap opens.
 *
 * <p>It is a presentation lever and is bounded accordingly. A squad in contact
 * never waits, none waits longer than {@link #MAX_HOLD_TICKS} on one hop, and
 * only a squad that is strictly ahead can hold another, so two squads cannot
 * hold each other. The answer is cached on the squad for
 * {@link #CHECK_EVERY_TICKS}, so the cost is one pass over the squad list per
 * squad per second or so, spread by the squads' own first-check ticks.
 * {@code -Dbattle.squad.transitSpacing=true} turns it on; the other two
 * settings are the gap in cells and the longest wait in ticks.
 */
public final class TransitSpacing {

    public static final String PROPERTY = "battle.squad.transitSpacing";

    /** Read once, like the other measured levers. */
    static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty(PROPERTY, "false"));

    /** Closest a squad may follow another on the same approach, in cells. */
    static final float SEPARATION_CELLS = Float.parseFloat(
            System.getProperty(PROPERTY + ".cells", "9"));
    /** The longest a squad waits on one hop, in ticks (30 s at 30 Hz). */
    static final int MAX_HOLD_TICKS = Integer.getInteger(PROPERTY + ".maxHold", 900);
    /** Ticks a decision is reused for. */
    static final int CHECK_EVERY_TICKS = 30;

    private TransitSpacing() { }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Whether {@code squad} should wait where it stands rather than close up. */
    public static boolean holds(Squad squad, int zoneId, int destX, int destY,
                                BattleView sim) {
        if (!ENABLED) return false;
        // A squad in contact is fighting, not following.
        if (squad.contactPicture.hasContacts()) return false;
        EntryGate gate = squad.entryGate;
        int tick = sim.getSimTickIndex();
        if (gate.isFresh(zoneId, tick, CHECK_EVERY_TICKS)) return gate.holding();
        int ahead = squadsAheadWithin(squad, destX, destY, sim);
        return gate.decide(zoneId, tick, true, ahead, 1, MAX_HOLD_TICKS);
    }

    private static int squadsAheadWithin(Squad self, int destX, int destY,
                                         BattleView sim) {
        int count = 0;
        for (Squad other : sim.getSquads()) {
            if (other == self || other.faction != self.faction) continue;
            if (sim.squadMemberCount(other.id) <= 0) continue;
            if (!isInTransit(other)) continue;
            if (isAheadWithin(self.centroidX, self.centroidY,
                    other.centroidX, other.centroidY, destX + 0.5f,
                    destY + 0.5f, SEPARATION_CELLS)) count++;
        }
        return count;
    }

    private static boolean isInTransit(Squad squad) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null ? plan.currentStep() : null;
        return step != null && step.action instanceof EnterZone;
    }

    /**
     * Whether the other squad stands within {@code gap} cells of this one and
     * strictly nearer the destination. Strictness is what stops two squads
     * holding each other.
     */
    static boolean isAheadWithin(float x, float y, float otherX, float otherY,
                                 float destX, float destY, float gap) {
        float dx = otherX - x;
        float dy = otherY - y;
        if (dx * dx + dy * dy > gap * gap) return false;
        float mine = (destX - x) * (destX - x) + (destY - y) * (destY - y);
        float theirs = (destX - otherX) * (destX - otherX)
                + (destY - otherY) * (destY - otherY);
        return theirs < mine;
    }
}
