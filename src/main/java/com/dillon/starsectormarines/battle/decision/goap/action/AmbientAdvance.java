package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;
import java.util.Map;

/**
 * <b>Squad posture: ambient advance.</b> Close on the best local cue the squad
 * has — a remembered hostile, or the bearing of something it heard — and fight
 * what is met on the way.
 *
 * <p>This is the floor of the goal ladder, and it exists because the ladder had
 * none. {@code ai-nouns.md} states that a squad without workable orders "falls
 * through to ambient engagement rather than inventing a mission"; before this
 * action there was nothing at {@code IDLE} to fall through to, so a squad whose
 * mission goal declined and whose engagement goals could not chain held no plan
 * at all. A planless member drops its path by design, so the squad did not
 * drift to a halt — it was stopped, and stayed stopped until its commander's
 * next pulse re-assigned it.
 *
 * <p><b>A cue is not an objective.</b> The destination here is the squad's own
 * evidence, never a mission target: closing on a bearing is engagement, while
 * deciding to go take a zone nobody assigned would be the squad inventing a
 * mission for itself. {@link AmbientAdvance} therefore carries no
 * {@code ObjectiveAssignment} and validates none — unlike {@link AttackMove},
 * which is an order and fails when its order is withdrawn. This one is what the
 * squad does when there is no order to check.
 *
 * <p>Otherwise it is an ordinary member of the push family: the same
 * {@link AbstractZoneAction#advanceIntoZone} with {@code haltOnContact}, so the
 * squad commits when the route threat earns it, fights from bounded firing
 * positions, and resumes when the threat releases. The zone is {@code -1}
 * context for the same reason {@link AttackMove}'s is — arrival is reaching a
 * cell rather than crossing a portal.
 */
public final class AmbientAdvance extends AbstractZoneAction {

    /** Cells from the cue within which the squad has arrived and the step is done. */
    public static final float ARRIVAL_RADIUS = 2f;

    private final int destX;
    private final int destY;

    public AmbientAdvance(int destX, int destY) {
        super(-1);
        this.destX = destX;
        this.destY = destY;
    }

    public int destX() { return destX; }
    public int destY() { return destY; }

    @Override public String name() { return "AmbientAdvance"; }

    @Override
    public Map<String, List<Long>> assignRoles(Squad squad, BattleView sim,
                                               List<Long> candidates) {
        return FireTeamGroups.assignments(FIRE_TEAM, candidates, sim.squad());
    }

    /** See {@link AttackMove#permitsOpportunityFire()} — the advance authors its own fire. */
    @Override public boolean permitsOpportunityFire() { return false; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        if (TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                destX + 0.5f, destY + 0.5f) <= ARRIVAL_RADIUS) {
            return ActionStatus.SUCCESS;
        }
        clearBounding(squad);
        advanceIntoZone(member, squad, sim, destX, destY, true);
        return ActionStatus.RUNNING;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{destX, destY});
    }
}
