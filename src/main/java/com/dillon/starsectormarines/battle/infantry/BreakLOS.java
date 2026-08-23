package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Squad posture: duck around a corner.</b> Story A re-trigger — when a
 * squadmate is taking fire from an enemy with LOS back to the shot's origin
 * ({@link Predicate#UNDER_FIRE_AT_LOS}), exposed fireteams break the firing
 * lane via {@link TacticalScoring#findFallbackPosition} while unexposed
 * sibling teams plant and cover through the normal opportunity-fire seam.
 *
 * <p>Cost {@code 2.0} — cheaper than {@link EngagePosture}'s 1.0 in the
 * planner's regression math when the goal predicate is {@link Predicate#UNDER_FIRE_AT_LOS},
 * since this action's effect ({@code UNDER_FIRE_AT_LOS=false}) directly
 * satisfies the desired-state slot and Engage doesn't. Keeping it modest
 * stops the planner from prepending BreakLOS in unrelated plans.
 *
 * <p>Destination caching reuses the AI_STATE fall-back cell
 * ({@code world.fallbackCellX(id)}/{@code world.fallbackCellY(id)}) — the same
 * columns {@link BreakContact} uses. Re-rolls via the shared
 * {@link TacticalScoring#fallbackDestinationNeedsRefresh} when the cached
 * cell is unset or has become visible to an enemy; otherwise holds the cell
 * and walks toward it. Because the plan step is squad-shared, one member's
 * arrival is not enough to finish it: the action returns
 * {@link ActionStatus#SUCCESS} only after every living displacer has reached
 * its individually selected hidden or least-exposed fallback cell. Cover-team
 * members are deliberately excluded from that completion gate.
 *
 * <p>Emitted from the planner only — uses backward-chaining preconditions/effects
 * (not a customPlan action like BreakContact).
 */
public final class BreakLOS implements Action {

    public static final BreakLOS INSTANCE = new BreakLOS();

    private static final float COST = 2.0f;
    static final String DISPLACE = "displace:";
    static final String COVER = "cover:";

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.UNDER_FIRE_AT_LOS, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.UNDER_FIRE_AT_LOS, false);

    private BreakLOS() {}

    @Override public String name() { return "BreakLOS"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return COST; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public Map<String, List<Long>> assignRoles(Squad squad, BattleView sim,
                                                List<Long> candidates) {
        List<FireTeamGroups.Team> teams = FireTeamGroups.organize(candidates, sim.squad());
        Set<Integer> exposed = exposedTeams(teams, squad, sim);
        if (exposed.isEmpty() && !teams.isEmpty()) {
            exposed.add(fallbackExposedTeam(teams, squad, sim));
        }

        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (FireTeamGroups.Team team : teams) {
            String role = exposed.contains(team.index()) ? DISPLACE : COVER;
            result.put(role + team.index(), team.members());
        }
        return result;
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        SmokeTactics.coverWithdrawal(squad, sim);
        SquadPlan.Step step = squad.currentPlan != null
                ? squad.currentPlan.currentStep() : null;
        String role = step != null ? step.slotOf(member) : null;
        if (role != null && role.startsWith(COVER)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return allDisplacersAtFallback(step, sim)
                    ? ActionStatus.SUCCESS
                    : ActionStatus.RUNNING;
        }

        if (sim.getTacticalScoring().fallbackDestinationNeedsRefresh(member)) {
            int[] dest = sim.getTacticalScoring().findFallbackPosition(member);
            sim.world().setFallbackCell(member, dest[0], dest[1]);
        }

        boolean atDest = sim.movement().atCell(member, sim.world().fallbackCellX(member), sim.world().fallbackCellY(member));
        if (atDest) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            // Arrived at the member's best available fallback (hidden when the
            // map provides one, least-exposed otherwise). Do not advance the
            // shared step until the rest of the squad reaches its picks too.
            boolean complete = step != null
                    ? allDisplacersAtFallback(step, sim)
                    : allSquadmatesAtFallback(squad, sim);
            return complete ? ActionStatus.SUCCESS : ActionStatus.RUNNING;
        }
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    sim.world().fallbackCellX(member), sim.world().fallbackCellY(member),
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    private static Set<Integer> exposedTeams(List<FireTeamGroups.Team> teams,
                                              Squad squad, BattleView sim) {
        Set<Integer> exposed = new HashSet<>();
        for (ShotEvent shot : sim.snapshotActiveShots()) {
            if (shot.shooterFaction == squad.faction) continue;
            int fromX = (int) Math.floor(shot.fromX);
            int fromY = (int) Math.floor(shot.fromY);
            for (FireTeamGroups.Team team : teams) {
                if (exposed.contains(team.index())) continue;
                for (long member : team.members()) {
                    if (sim.resolveUnit(member) == 0L) continue;
                    float dx = shot.toX - sim.world().x(member);
                    float dy = shot.toY - sim.world().y(member);
                    if (dx * dx + dy * dy > 4f) continue;
                    if (!sim.getGrid().hasLineOfSight(sim.world().cellX(member),
                            sim.world().cellY(member), fromX, fromY)) continue;
                    exposed.add(team.index());
                    break;
                }
            }
        }
        return exposed;
    }

    private static int fallbackExposedTeam(List<FireTeamGroups.Team> teams,
                                           Squad squad, BattleView sim) {
        BelievedContact threat = null;
        for (BelievedContact contact : squad.believedContacts()) {
            if (threat == null
                    || contact.lastSeenTick() > threat.lastSeenTick()
                    || contact.lastSeenTick() == threat.lastSeenTick()
                    && contact.confidence() > threat.confidence()) {
                threat = contact;
            }
        }
        if (threat == null) return teams.get(0).index();

        int best = teams.get(0).index();
        float bestDistance = Float.MAX_VALUE;
        for (FireTeamGroups.Team team : teams) {
            float x = 0f;
            float y = 0f;
            int live = 0;
            for (long member : team.members()) {
                if (sim.resolveUnit(member) == 0L) continue;
                x += sim.world().x(member);
                y += sim.world().y(member);
                live++;
            }
            if (live == 0) continue;
            float distance = TacticalScoring.cellDistance(x / live, y / live,
                    threat.lastSeenCellX() + 0.5f,
                    threat.lastSeenCellY() + 0.5f);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = team.index();
            }
        }
        return best;
    }

    private static boolean allDisplacersAtFallback(SquadPlan.Step step,
                                                    BattleView sim) {
        boolean foundDisplacer = false;
        for (Map.Entry<String, List<Long>> entry : step.assignments.entrySet()) {
            if (!entry.getKey().startsWith(DISPLACE)) continue;
            for (long member : entry.getValue()) {
                if (sim.resolveUnit(member) == 0L) continue;
                foundDisplacer = true;
                int fx = sim.world().fallbackCellX(member);
                int fy = sim.world().fallbackCellY(member);
                if (fx < 0 || fy < 0 || !sim.movement().atCell(member, fx, fy)) {
                    return false;
                }
            }
        }
        return foundDisplacer;
    }

    /** True when every living member has a valid fallback and has reached it. */
    private static boolean allSquadmatesAtFallback(Squad squad, BattleControl sim) {
        boolean foundMember = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long other = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(other) || sim.squad().squadId(other) != squad.id) continue;
            foundMember = true;
            int fx = sim.world().fallbackCellX(other);
            int fy = sim.world().fallbackCellY(other);
            if (fx < 0 || fy < 0 || !sim.movement().atCell(other, fx, fy)) return false;
        }
        return foundMember;
    }
}
