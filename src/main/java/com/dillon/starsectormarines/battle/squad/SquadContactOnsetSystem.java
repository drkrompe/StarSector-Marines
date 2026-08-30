package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Stateless tick consumer that turns one marine's contact into the squad's.
 *
 * <p><b>An onset is a squad event that happens to one person.</b> A marine who
 * comes round a corner into somebody, or who is taken by a shooter down a lane,
 * is the squad's first indication that the ground ahead is not what it thought.
 * Left personal, it is answered personally — that marine screens or goes to
 * ground and the other five keep walking past the bearing one at a time, which
 * is the failure the whole cooperation layer exists to remove. Published here,
 * the squad answers it together.
 *
 * <p>Runs before {@link AssaultCoordinationSystem} and before the replan, so a
 * contact made this tick is available to the squad that made it, to the squads
 * cooperating with it, and to the plan all of them build — rather than arriving
 * a tick late through some other reading.
 *
 * <p><b>What a squad already believes is not the same as what one of its people
 * is looking at.</b> The contact picture is an aggregate over believed contacts
 * and moves at its own pace; this is the instant a member's own eyes changed
 * the situation. The two are kept separate rather than merged because the
 * picture's slowness is deliberate and useful everywhere else.
 *
 * <p>The close-quarters opening is preferred over the singling-out when a squad
 * has both, because a hostile inside ten cells is the more pressing thing to
 * put people and screens on than a shooter at range, and a squad can only face
 * one way at a time. Ties break on the nearest contact and then on id, so a
 * replay reaches the same bearing every time.
 *
 * <p>This system never writes an assignment, a path, or a target. It publishes
 * a reading. Sibling System to {@link SquadAlertSystem} /
 * {@link SquadMoraleSystem} / {@link AssaultCoordinationSystem}.
 */
public final class SquadContactOnsetSystem {

    /**
     * {@code -Dbattle.squad.contactOnset=false} stops publishing, which leaves
     * every squad advancing exactly as it did before this existed. Here because
     * the only honest control for a behaviour change is the same battle with
     * the behaviour absent, and reaching that by checking out an older commit
     * measures every other difference between the two trees at once.
     */
    public static final String ENABLED_PROPERTY = "battle.squad.contactOnset";

    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty(ENABLED_PROPERTY, "true"));

    private final UnitRosterService roster;
    private final TacticalScoring scoring;

    public SquadContactOnsetSystem(UnitRosterService roster, TacticalScoring scoring) {
        this.roster = roster;
        this.scoring = scoring;
    }

    /** Publishes at most one onset contact per live squad. */
    public void tick(int currentTick) {
        if (!ENABLED) return;
        for (Squad squad : roster.getSquads()) {
            publishFor(squad, currentTick);
        }
    }

    private void publishFor(Squad squad, int currentTick) {
        if (squad == null || squad.aliveMembers <= 0) {
            if (squad != null) clear(squad);
            return;
        }
        long[] members = roster.squadMemberArray(squad.id);
        long bestContact = 0L;
        long bestSeenBy = 0L;
        float bestDistance = Float.MAX_VALUE;
        boolean bestIsClose = false;
        for (int i = 0, n = roster.squadMemberCount(squad.id); i < n; i++) {
            long member = members[i];
            if (!roster.isAliveById(member)) continue;
            long contact = scoring.closeContactOpening(
                    member, TacticalScoring.CLOSE_QUARTERS_CELLS);
            boolean close = contact != 0L;
            if (contact == 0L) contact = scoring.takenAsATarget(member);
            if (contact == 0L) continue;
            // A close-quarters opening outranks a singling-out outright, so a
            // squad with both faces the thing that is already on top of it.
            if (bestIsClose && !close) continue;
            float distance = TacticalScoring.cellDistance(
                    roster.world().x(member), roster.world().y(member),
                    roster.world().x(contact), roster.world().y(contact));
            boolean better = (close && !bestIsClose)
                    || distance < bestDistance
                    || (distance == bestDistance
                    && (bestContact == 0L || contact < bestContact));
            if (!better) continue;
            bestIsClose = close;
            bestDistance = distance;
            bestContact = contact;
            bestSeenBy = member;
        }
        if (bestContact == 0L) {
            clear(squad);
            return;
        }
        squad.onsetContactId = bestContact;
        squad.onsetSeenBy = bestSeenBy;
        squad.onsetAtCloseQuarters = bestIsClose;
        squad.onsetDistance = bestDistance;
        squad.onsetTick = currentTick;
    }

    private static void clear(Squad squad) {
        if (squad.onsetContactId == 0L) return;
        squad.onsetContactId = 0L;
        squad.onsetSeenBy = 0L;
        squad.onsetAtCloseQuarters = false;
        squad.onsetDistance = Float.MAX_VALUE;
        squad.onsetTick = -1;
    }
}
