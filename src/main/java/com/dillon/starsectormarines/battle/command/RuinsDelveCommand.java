package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RuinsDelveSnapshot.Role;
import com.dillon.starsectormarines.battle.command.RuinsDelveSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.command.RuinsDelveSnapshot.Stage;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The company's headless strategy for the Ruins family ({@code ruins-expedition.md}, The Ruins mission family).
 *
 * <ol>
 *   <li><b>Delve.</b> The approach from the landing area to the vault is cut into the grade's layers. A lead element
 *       moves up to the next layer while the body holds the last one; once the lead is there and out of contact, the
 *       layer is cleared and the body moves up behind it. A lead in contact draws the body up to it. When every
 *       squad is quiet, one body squad detours to a field cache the company has found near the layer, and the lead
 *       waits for it.</li>
 *   <li><b>Breach.</b> One squad holds the vault, the rest screen it towards the lift point. A landing court whose
 *       route is shorter than the haul's bound lift point gets a squad to hold it open, while three squads or more
 *       remain.</li>
 *   <li><b>Haul.</b> Extraction's moving escort ({@link EscortFormation}) takes the haul to the lift point the
 *       objective bound it for.</li>
 * </ol>
 *
 * <p>Only squads under mission command or nothing are in the pool: a squad under the player's order is left alone,
 * as every mission's commander leaves it.
 */
public final class RuinsDelveCommand implements AutonomousMissionCommand<RuinsDelveCommandFrame, RuinsDelveSnapshot> {

    static final String STRATEGY_ID = "ruins-delve";
    /** How near its waypoint a squad's anchor counts as there. */
    static final int ARRIVE_RADIUS = 4;
    /** How near the layer's waypoint the body must be before the lead moves on. */
    static final int SETTLE_RADIUS = 8;
    /** How far, by route from the layer's waypoint, a cache may lie and still be worth the detour. */
    static final int DETOUR_ROUTE_CELLS = 24;

    private final int layers;
    private final EscortFormation formation = new EscortFormation();
    private int cleared;
    private boolean atVault;
    private int leadSquadId = -1;
    private int detourSquadId = -1;
    private String detourCacheId;
    private int vaultHoldSquadId = -1;
    private int courtHoldSquadId = -1;
    private volatile RuinsDelveSnapshot delveSnapshot;

    /** @param layers the grade's layers ({@code RuinsDelve.layers}) */
    public RuinsDelveCommand(int layers) {
        if (layers < 1) throw new IllegalArgumentException("a delve has a layer");
        this.layers = layers;
    }

    public RuinsDelveSnapshot delveSnapshot() { return delveSnapshot; }

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return STRATEGY_ID; }

    @Override
    public CommandPlan<RuinsDelveSnapshot> plan(RuinsDelveCommandFrame frame) {
        RuinsObjectiveFacts facts = frame.facts();
        CommandTopology topology = frame.topology();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> squad.directive() == null
                        || squad.directive().authority().priority() <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        Set<Integer> poolIds = new HashSet<>();
        for (CommandSquadState squad : pool) poolIds.add(squad.squadId());
        formation.retain(poolIds);
        if (!poolIds.contains(leadSquadId)) leadSquadId = -1;
        if (!poolIds.contains(detourSquadId)) clearDetour();
        if (!poolIds.contains(vaultHoldSquadId)) vaultHoldSquadId = -1;
        if (!poolIds.contains(courtHoldSquadId)) courtHoldSquadId = -1;

        Stage stage = stage(facts);
        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() > 0 && !poolIds.contains(squad.squadId())) {
                intents.add(new SquadIntent(squad.squadId(), Role.EXTERNAL, "EXTERNAL_OWNERSHIP_PRESERVED", null,
                        -1, -1, squad.localContact()));
            }
        }
        int waypointX = -1;
        int waypointY = -1;
        switch (stage) {
            case DONE -> {
                for (CommandSquadState squad : pool) {
                    proposals.add(releaseOrRetain(squad, "OBJECTIVE_TERMINAL"));
                    intents.add(new SquadIntent(squad.squadId(), Role.RELEASED, "OBJECTIVE_TERMINAL", null, -1, -1,
                            squad.localContact()));
                }
            }
            case DELVE -> {
                int[] waypoint = delve(pool, facts, topology, proposals, intents);
                waypointX = waypoint[0];
                waypointY = waypoint[1];
            }
            case BREACH -> {
                breach(pool, facts, topology, proposals, intents);
                waypointX = facts.vaultCellX();
                waypointY = facts.vaultCellY();
            }
            case HAUL -> haul(pool, facts, topology, proposals, intents);
        }
        if (stage == Stage.DELVE && atVault) stage = Stage.BREACH;
        intents.sort(Comparator.comparingInt(SquadIntent::squadId));
        RuinsDelveSnapshot detail = new RuinsDelveSnapshot(frame.tick(), faction(), stage, facts.phase(), cleared,
                layers, waypointX, waypointY, leadSquadId, detourCacheId, intents);
        return new CommandPlan<>(faction(), strategyId(), stage.name(), frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1, pool.size(), 0,
                List.of(summary(facts, stage)), proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<RuinsDelveSnapshot> snapshot) {
        delveSnapshot = snapshot.detail();
    }

    private Stage stage(RuinsObjectiveFacts facts) {
        if (facts.terminal()) return Stage.DONE;
        if ("IN_TRANSIT".equals(facts.phase()) || "BOARDING".equals(facts.phase())) return Stage.HAUL;
        if (facts.breachProgress() > 0f || facts.escortPresent()) atVault = true;
        if (cleared >= layers) atVault = true;
        return atVault ? Stage.BREACH : Stage.DELVE;
    }

    /** The cell on the approach where layer {@code k} ends: 0 is the landing area, {@code layers} the vault. */
    private int[] layerCell(RuinsObjectiveFacts facts, int k) {
        int cells = facts.approachCellCount();
        if (cells == 0) return new int[]{facts.vaultCellX(), facts.vaultCellY()};
        int index = Math.round((float) Math.min(k, layers) * (cells - 1) / layers);
        return new int[]{facts.approachCellX(index), facts.approachCellY(index)};
    }

    private int[] delve(List<CommandSquadState> pool, RuinsObjectiveFacts facts, CommandTopology topology,
                        List<CommandProposal> proposals, List<SquadIntent> intents) {
        int[] held = layerCell(facts, cleared);
        int[] next = layerCell(facts, cleared + 1);
        CommandSquadState lead = leadSquadId >= 0 ? find(pool, leadSquadId) : null;
        if (lead == null) {
            lead = nearest(pool, next[0], next[1], topology, Set.of(detourSquadId));
            leadSquadId = lead != null ? lead.squadId() : -1;
        }
        boolean quiet = pool.stream().noneMatch(CommandSquadState::localContact);
        if (detourSquadId >= 0 && (!quiet || cacheFor(facts, detourCacheId) == null)) clearDetour();

        // The layer is cleared once the lead stands on it, quiet, with no detour still out.
        if (lead != null && quiet && detourSquadId < 0 && near(lead, next, ARRIVE_RADIUS)) {
            cleared++;
            if (cleared >= layers) {
                atVault = true;
                breach(pool, facts, topology, proposals, intents);
                return new int[]{facts.vaultCellX(), facts.vaultCellY()};
            }
            held = next;
            next = layerCell(facts, cleared + 1);
        }

        if (quiet && detourSquadId < 0 && pool.size() >= 3) startDetour(pool, facts, held, topology);

        boolean leadInContact = lead != null && lead.localContact();
        int[] bodyTarget = leadInContact ? next : held;
        boolean settled = true;
        for (CommandSquadState squad : pool) {
            if (squad.squadId() == leadSquadId || squad.squadId() == detourSquadId) continue;
            if (!near(squad, held, SETTLE_RADIUS)) settled = false;
        }
        boolean leadMoves = settled && detourSquadId < 0 || leadInContact;
        int[] leadTarget = leadMoves ? next : held;

        Set<Long> reserved = new HashSet<>();
        for (CommandSquadState squad : pool) {
            if (squad.squadId() == leadSquadId) {
                assign(squad, Role.DELVE_LEAD, ObjectiveAssignment.attackMove(squad.squadId(), leadTarget[0],
                        leadTarget[1]), "DELVE_LEAD_LAYER_" + (cleared + 1), leadTarget, proposals, intents);
            } else if (squad.squadId() == detourSquadId) {
                RuinsObjectiveFacts.Cache cache = cacheFor(facts, detourCacheId);
                int[] cell = EscortFormation.nearestReachable(squad, cache.cellX(), cache.cellY(),
                        cache.cellX(), cache.cellY(), topology, reserved);
                if (cell == null) { stranded(squad, proposals, intents); clearDetour(); continue; }
                reserved.add(EscortFormation.cellKey(cell[0], cell[1]));
                assign(squad, Role.CACHE_DETOUR, ObjectiveAssignment.defendSite(squad.squadId(), cell[0], cell[1]),
                        "CACHE_DETOUR_" + detourCacheId, cell, proposals, intents);
            } else {
                int[] cell = EscortFormation.nearestReachable(squad, bodyTarget[0], bodyTarget[1],
                        bodyTarget[0], bodyTarget[1], topology, reserved);
                if (cell == null) { stranded(squad, proposals, intents); continue; }
                reserved.add(EscortFormation.cellKey(cell[0], cell[1]));
                assign(squad, Role.DELVE_BODY, ObjectiveAssignment.defendSite(squad.squadId(), cell[0], cell[1]),
                        leadInContact ? "DELVE_BODY_SUPPORT_LEAD" : "DELVE_BODY_HOLD_LAYER_" + cleared, cell,
                        proposals, intents);
            }
        }
        return bodyTarget;
    }

    /** Sends the body squad nearest a found cache close by the held layer to recover it. */
    private void startDetour(List<CommandSquadState> pool, RuinsObjectiveFacts facts, int[] held,
                             CommandTopology topology) {
        RuinsObjectiveFacts.Cache best = null;
        int bestRoute = Integer.MAX_VALUE;
        for (RuinsObjectiveFacts.Cache cache : facts.caches()) {
            if (!topology.reachable(held[0], held[1], cache.cellX(), cache.cellY())) continue;
            int route = topology.routeLength(held[0], held[1], cache.cellX(), cache.cellY());
            if (route <= DETOUR_ROUTE_CELLS && route < bestRoute) {
                best = cache;
                bestRoute = route;
            }
        }
        if (best == null) return;
        CommandSquadState squad = nearest(pool, best.cellX(), best.cellY(), topology, Set.of(leadSquadId));
        if (squad == null) return;
        detourSquadId = squad.squadId();
        detourCacheId = best.id();
    }

    private void breach(List<CommandSquadState> pool, RuinsObjectiveFacts facts, CommandTopology topology,
                        List<CommandProposal> proposals, List<SquadIntent> intents) {
        clearDetour();
        leadSquadId = -1;
        int vaultX = facts.vaultCellX();
        int vaultY = facts.vaultCellY();
        if (vaultHoldSquadId < 0) {
            CommandSquadState hold = nearest(pool, vaultX, vaultY, topology, Set.of(courtHoldSquadId));
            vaultHoldSquadId = hold != null ? hold.squadId() : -1;
        }
        RuinsObjectiveFacts.LiftPoint court = courtWorthHolding(facts);
        if (court == null || pool.size() < 3) courtHoldSquadId = -1;
        else if (courtHoldSquadId < 0) {
            CommandSquadState holder = nearest(pool, court.cellX(), court.cellY(), topology, Set.of(vaultHoldSquadId));
            courtHoldSquadId = holder != null ? holder.squadId() : -1;
        }
        RuinsObjectiveFacts.LiftPoint bound = facts.boundLiftPoint();
        int egressX = bound != null ? bound.cellX() : vaultX;
        int egressY = bound != null ? bound.cellY() : vaultY;

        Set<Long> reserved = new HashSet<>();
        int screen = 0;
        for (CommandSquadState squad : pool) {
            if (squad.squadId() == vaultHoldSquadId) {
                int[] cell = EscortFormation.nearestReachable(squad, vaultX, vaultY, vaultX, vaultY, topology, reserved);
                if (cell == null) { stranded(squad, proposals, intents); continue; }
                reserved.add(EscortFormation.cellKey(cell[0], cell[1]));
                assign(squad, Role.VAULT_HOLD, ObjectiveAssignment.defendSite(squad.squadId(), cell[0], cell[1]),
                        "VAULT_BREACH_HOLD", cell, proposals, intents);
            } else if (squad.squadId() == courtHoldSquadId) {
                int[] cell = EscortFormation.nearestReachable(squad, court.cellX(), court.cellY(), court.cellX(),
                        court.cellY(), topology, reserved);
                if (cell == null) { stranded(squad, proposals, intents); courtHoldSquadId = -1; continue; }
                reserved.add(EscortFormation.cellKey(cell[0], cell[1]));
                assign(squad, Role.COURT_HOLD, ObjectiveAssignment.defendSite(squad.squadId(), cell[0], cell[1]),
                        "COURT_HOLD_" + court.name(), cell, proposals, intents);
            } else {
                ExtractionCommandSnapshot.Role facing = SCREENS.get(screen++ % SCREENS.size());
                int[] desired = EscortFormation.desiredCell(facing, vaultX, vaultY, -1, -1, egressX, egressY,
                        vaultX, vaultY);
                int[] cell = EscortFormation.nearestReachable(squad, desired[0], desired[1], vaultX, vaultY,
                        topology, reserved);
                if (cell == null) { stranded(squad, proposals, intents); continue; }
                reserved.add(EscortFormation.cellKey(cell[0], cell[1]));
                assign(squad, Role.VAULT_SCREEN, ObjectiveAssignment.defendSite(squad.squadId(), cell[0], cell[1]),
                        "VAULT_SCREEN_" + facing.name(), cell, proposals, intents);
            }
        }
    }

    private static final List<ExtractionCommandSnapshot.Role> SCREENS = List.of(
            ExtractionCommandSnapshot.Role.LEAD_SCREEN, ExtractionCommandSnapshot.Role.LEFT_SCREEN,
            ExtractionCommandSnapshot.Role.RIGHT_SCREEN, ExtractionCommandSnapshot.Role.REAR_SCREEN);

    /** The unopened landing court with the shortest route, when it is shorter than the haul's bound lift point's. */
    static RuinsObjectiveFacts.LiftPoint courtWorthHolding(RuinsObjectiveFacts facts) {
        RuinsObjectiveFacts.LiftPoint bound = facts.boundLiftPoint();
        int boundRoute = bound != null ? bound.routeCells() : Integer.MAX_VALUE;
        RuinsObjectiveFacts.LiftPoint best = null;
        for (RuinsObjectiveFacts.LiftPoint point : facts.liftPoints()) {
            if (point.available() || point.routeCells() >= boundRoute) continue;
            if (best == null || point.routeCells() < best.routeCells()) best = point;
        }
        return best;
    }

    private void haul(List<CommandSquadState> pool, RuinsObjectiveFacts facts, CommandTopology topology,
                      List<CommandProposal> proposals, List<SquadIntent> intents) {
        vaultHoldSquadId = -1;
        courtHoldSquadId = -1;
        RuinsObjectiveFacts.LiftPoint bound = facts.boundLiftPoint();
        boolean boarding = "BOARDING".equals(facts.phase());
        int anchorX = boarding && bound != null ? bound.cellX() : facts.haulCellX();
        int anchorY = boarding && bound != null ? bound.cellY() : facts.haulCellY();
        int egressX = bound != null ? bound.cellX() : anchorX;
        int egressY = bound != null ? bound.cellY() : anchorY;
        formation.assign(pool, anchorX, anchorY, topology);
        Set<Long> reserved = new HashSet<>();
        for (CommandSquadState squad : pool) {
            ExtractionCommandSnapshot.Role role = formation.role(squad.squadId());
            int[] desired = EscortFormation.desiredCell(role, anchorX, anchorY, facts.guideCellX(),
                    facts.guideCellY(), egressX, egressY, facts.vaultCellX(), facts.vaultCellY());
            int[] cell = EscortFormation.nearestReachable(squad, desired[0], desired[1], anchorX, anchorY,
                    topology, reserved);
            if (cell == null) { stranded(squad, proposals, intents); continue; }
            reserved.add(EscortFormation.cellKey(cell[0], cell[1]));
            ObjectiveAssignment assignment = EscortFormation.escortRole(role)
                    ? ObjectiveAssignment.escort(squad.squadId(), cell[0], cell[1])
                    : ObjectiveAssignment.defendSite(squad.squadId(), cell[0], cell[1]);
            assign(squad, Role.valueOf(role.name()), assignment, "HAUL_" + role.name() + "_" + facts.phase(), cell,
                    proposals, intents);
        }
    }

    private void clearDetour() {
        detourSquadId = -1;
        detourCacheId = null;
    }

    private static RuinsObjectiveFacts.Cache cacheFor(RuinsObjectiveFacts facts, String id) {
        if (id == null) return null;
        for (RuinsObjectiveFacts.Cache cache : facts.caches()) if (cache.id().equals(id)) return cache;
        return null;
    }

    private static CommandSquadState find(List<CommandSquadState> pool, int squadId) {
        for (CommandSquadState squad : pool) if (squad.squadId() == squadId) return squad;
        return null;
    }

    /** The pool squad with the shortest route to a cell, skipping the excluded; the lowest id on a tie. */
    private static CommandSquadState nearest(List<CommandSquadState> pool, int x, int y, CommandTopology topology,
                                             Set<Integer> excluded) {
        return pool.stream()
                .filter(squad -> !excluded.contains(squad.squadId()))
                .filter(squad -> topology.reachable(squad.anchorCellX(), squad.anchorCellY(), x, y))
                .min(Comparator.comparingInt((CommandSquadState squad) ->
                                topology.routeLength(squad.anchorCellX(), squad.anchorCellY(), x, y))
                        .thenComparingInt(CommandSquadState::squadId))
                .orElse(null);
    }

    private static boolean near(CommandSquadState squad, int[] cell, int radius) {
        return Math.abs(squad.anchorCellX() - cell[0]) <= radius && Math.abs(squad.anchorCellY() - cell[1]) <= radius;
    }

    private static void assign(CommandSquadState squad, Role role, ObjectiveAssignment assignment, String reason,
                               int[] cell, List<CommandProposal> proposals, List<SquadIntent> intents) {
        proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                stabilityBreak(squad, assignment)));
        intents.add(new SquadIntent(squad.squadId(), role, reason, assignment.kind(), cell[0], cell[1],
                squad.localContact()));
    }

    private static void stranded(CommandSquadState squad, List<CommandProposal> proposals,
                                 List<SquadIntent> intents) {
        proposals.add(releaseOrRetain(squad, "DELVE_TARGET_UNREACHABLE"));
        intents.add(new SquadIntent(squad.squadId(), Role.STRANDED, "DELVE_TARGET_UNREACHABLE", null, -1, -1,
                squad.localContact()));
    }

    private static CommandProposal releaseOrRetain(CommandSquadState squad, String reason) {
        return squad.directive() != null && STRATEGY_ID.equals(squad.directive().issuer())
                ? CommandProposal.release(squad.squadId(), CommandAuthority.MISSION_COMMAND, reason,
                CommandStabilityBreak.OBJECTIVE_COMPLETED)
                : CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, reason);
    }

    private static CommandStabilityBreak stabilityBreak(CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }

    private String summary(RuinsObjectiveFacts facts, Stage stage) {
        return switch (stage) {
            case DELVE -> facts.haulName() + "=delve layer " + cleared + "/" + layers;
            case BREACH -> facts.haulName() + "=breach "
                    + Math.round(100f * facts.breachProgress() / Math.max(0.001f, facts.breachDuration())) + "%";
            case HAUL -> facts.haulName() + "=" + facts.phase().toLowerCase();
            case DONE -> facts.haulName() + "=" + (facts.complete() ? "boarded" : "lost");
        };
    }
}
