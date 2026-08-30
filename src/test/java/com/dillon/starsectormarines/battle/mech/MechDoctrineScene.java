package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * A controlled firing lane for comparing the four player-selectable mech
 * doctrines under one physical and tactical fixture.
 *
 * <p>Every variant is the same Bulwark, deployed with the same Balanced
 * doctrine, on the same open lane with the same attack-move assignment and
 * known contact. The only changed input is a request sent through the live
 * {@link MechDoctrineService} before the first recorded frame. That makes the
 * recordings evidence of the command seam and the downstream production
 * planner, rather than four hand-configured action demonstrations.
 *
 * <p>A stationary marine fire team begins twelve cells ahead of the mech. It
 * is close enough to be the Brawler's cohesion anchor and the Tank's backed
 * squad, and lies on the Long Range Support firing axis. The team is killed at
 * one shared midpoint. The second half therefore shows how each doctrine
 * responds when exactly the same support geometry disappears.
 *
 * <p>The paired lance-order builder leaves those four loops unchanged and
 * instead adds a zero-locomotion Sirocco as the selected Bulwark's real squad
 * leader. Its offset firing pose stays stable without test-time position
 * writes while remaining inside the production Brawler support-acquisition
 * radius.
 *
 * <p>The defender is deliberately immobile, harmless, and extremely durable.
 * It is a real hostile contact, so target acquisition and firing remain live,
 * but it cannot turn a position comparison into four different casualty
 * races. Mission completion is disabled and the unit-update threshold is
 * forced above the scene population so seeded runs remain serial.
 */
final class MechDoctrineScene {

    static final long SEED = 20260830L;
    static final int WIDTH = 80;
    static final int HEIGHT = 32;

    static final int MECH_X = 24;
    static final int LANE_Y = 16;
    static final int SUPPORT_X = 36;
    static final int THREAT_X = 68;
    static final int ASSIGNMENT_X = 72;
    /** Command cell used by the paired lance-order evidence. */
    static final int LANCE_ASSIGNMENT_X = 55;
    static final int LANCE_MECH_X = 39;
    static final int LANCE_LEADER_X = 49;
    static final int LANCE_LEADER_Y = 10;

    private static final int SUPPORT_SIZE = 3;
    private static final float REMOVAL_DAMAGE = 1_000_000f;

    /** State retained by the recorder for captions and midpoint removal. */
    record Scene(BattleSimulation sim, long mechId, long threatId,
                 long[] supportIds, int mechSquadId, MechRole requestedRole,
                 MechLanceOrder lanceOrder, long lanceLeaderId, int assignmentX,
                 int supportRemovalTick) {}

    /** One point-in-time reading used both on-frame and in the printed report. */
    record Sample(float threatDistance, float anchorAxisPosition,
                  float missionOvershoot, float lanceLeaderDrift,
                  boolean supportAlive, String goal) {}

    private MechDoctrineScene() {}

    static Scene build(MechRole requestedRole, int supportRemovalTick) {
        return build(requestedRole, MechLanceOrder.FORM_ON_LEAD,
                ASSIGNMENT_X, supportRemovalTick, false);
    }

    /**
     * Builds one half of the paired Brawler lance-order comparison. Both mech
     * chassis/loadouts, squad membership, contact, assignment, geometry, and
     * seed are held fixed; the queued lance order is the only varied input.
     */
    static Scene buildBrawlerLanceOrder(MechLanceOrder lanceOrder) {
        return build(MechRole.ASSAULT, lanceOrder,
                LANCE_ASSIGNMENT_X, Integer.MAX_VALUE, true);
    }

    private static Scene build(MechRole requestedRole,
                               MechLanceOrder lanceOrder,
                               int assignmentX,
                               int supportRemovalTick,
                               boolean sameLanceLeader) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y,
                        Math.abs(y - LANE_Y) <= 4 ? GroundKind.STREET : GroundKind.DIRT);
                topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }

        BattleSimulation sim = serialSimulation(grid, topology);
        sim.setMissionCompletionEnabled(false);

        int mechSquadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        int mechStartX = sameLanceLeader ? LANCE_MECH_X : MECH_X;
        EntitySpec mechSpec = MechVariant.BULWARK.applyTo(new EntitySpec(
                "doctrine-bulwark", Faction.MARINE, UnitType.HEAVY_MECH,
                mechStartX, LANE_Y).squad(mechSquadId));
        long mech = sim.spawn(mechSpec);
        // The selected Bulwark is always deployed Balanced. The queued inputs
        // below create the compared battle-local effective state.
        sim.world().attachMechLoadout(
                mech, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
        Squad mechSquad = sim.getSquad(mechSquadId);
        long lanceLeader = 0L;
        long[] support;
        Squad supportSquad;
        if (sameLanceLeader) {
            EntitySpec leaderSpec = MechVariant.SIROCCO.applyTo(new EntitySpec(
                    "lance-lead-sirocco", Faction.MARINE, UnitType.HEAVY_MECH,
                    LANCE_LEADER_X, LANCE_LEADER_Y).squad(mechSquadId));
            // The leader is a real same-squad mech and a different chassis, so
            // Brawler cohesion can acquire it. Zero locomotion keeps the
            // controlled comparison anchored without replacing production AI.
            leaderSpec.moveSpeed(0f);
            lanceLeader = sim.spawn(leaderSpec);
            sim.world().attachMechLoadout(lanceLeader,
                    MechVariant.SIROCCO.createLoadout(MechRole.BALANCED));
            support = new long[]{lanceLeader};
            supportSquad = mechSquad;
            initializeSquad(mechSquad, lanceLeader, 2,
                    (LANCE_MECH_X + LANCE_LEADER_X + 1f) * 0.5f,
                    (LANE_Y + LANCE_LEADER_Y + 1f) * 0.5f);
        } else {
            initializeSquad(mechSquad, mech, 1,
                    MECH_X + 0.5f, LANE_Y + 0.5f);
            int supportSquadId = sim.mintSquad(
                    Faction.MARINE, UnitType.MARINE_BLUE);
            support = new long[SUPPORT_SIZE];
            for (int index = 0; index < SUPPORT_SIZE; index++) {
                int y = LANE_Y - 1 + index;
                support[index] = sim.spawn(new EntitySpec(
                        "screen-" + index, Faction.MARINE,
                        UnitType.MARINE_BLUE, SUPPORT_X, y)
                        .squad(supportSquadId)
                        .role(UnitRole.GARRISON)
                        .home(SUPPORT_X, y)
                        .moveSpeed(0f)
                        .attackDamage(0f)
                        .attackRange(0f)
                        .accuracy(0f)
                        .visionRange(0f));
            }
            supportSquad = sim.getSquad(supportSquadId);
            initializeSquad(supportSquad, support[1], SUPPORT_SIZE,
                    SUPPORT_X + 0.5f, LANE_Y + 0.5f);
        }

        long threat = sim.spawn(new EntitySpec(
                "fixed-contact", Faction.DEFENDER, UnitType.MARINE_RED,
                THREAT_X, LANE_Y)
                .health(100_000f)
                .moveSpeed(0f)
                .attackDamage(0f)
                .attackRange(0f)
                .accuracy(0f)
                .visionRange(0f));

        mechSquad.assignedObjective = ObjectiveAssignment.attackMove(
                mechSquadId, assignmentX, LANE_Y);
        // The stationary screen shares the authored mission. This makes it a
        // legal Tank anchor while keeping the physical fixture identical for
        // all four loops; removing it then exercises the Tank's hold-alone law.
        if (supportSquad != mechSquad) {
            supportSquad.assignedObjective = ObjectiveAssignment.attackMove(
                    supportSquad.id, assignmentX, LANE_Y);
        }
        SquadBeliefTestAccess.observeDirect(mechSquad, threat,
                THREAT_X, LANE_Y, sim.getSimTickIndex());

        sim.getMechDoctrineService().requestOverride(mech, requestedRole);
        sim.getMechDoctrineService().requestLanceOrder(mech, lanceOrder);
        // The production command phase drains the request before the same
        // tick's replan. Recording starts only after that boundary has run.
        sim.advance(BattleSimulation.TICK_DT);
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        if (loadout == null || loadout.effectiveRole() != requestedRole) {
            sim.close();
            throw new IllegalStateException(
                    "queued doctrine was not effective before recording");
        }
        if (mechSquad.lanceOrder() != lanceOrder) {
            sim.close();
            throw new IllegalStateException(
                    "queued lance order was not effective before recording");
        }
        if (sameLanceLeader && (lanceLeader == 0L
                || mechSquad.leaderId != lanceLeader
                || sim.squadOf(lanceLeader) != mechSquad
                || sim.squadOf(mech) != mechSquad
                || BreachAndAssault.nearestSupport(
                        mech, mechSquad, sim) != lanceLeader)) {
            sim.close();
            throw new IllegalStateException(
                    "paired evidence did not build one shared two-mech lance");
        }

        return new Scene(sim, mech, threat, support, mechSquadId,
                requestedRole, lanceOrder, lanceLeader, assignmentX,
                supportRemovalTick);
    }

    /** Removes the shared screen at the same boundary in every recording. */
    static void advance(Scene scene, int completedTick) {
        if (completedTick == scene.supportRemovalTick()) {
            for (long support : scene.supportIds()) {
                if (scene.sim().resolveUnit(support) != 0L) {
                    scene.sim().applyDamage(
                            support, REMOVAL_DAMAGE, REMOVAL_DAMAGE, 0f);
                }
            }
        }
        scene.sim().advance(BattleSimulation.TICK_DT);
    }

    static Sample sample(Scene scene) {
        BattleSimulation sim = scene.sim();
        float mechX = sim.world().x(scene.mechId());
        float mechY = sim.world().y(scene.mechId());
        float threatX = sim.world().x(scene.threatId());
        float threatY = sim.world().y(scene.threatId());
        Squad squad = sim.getSquad(scene.mechSquadId());
        float supportX = scene.lanceLeaderId() != 0L
                ? LANCE_LEADER_X + 0.5f : SUPPORT_X + 0.5f;
        float supportY = scene.lanceLeaderId() != 0L
                ? LANCE_LEADER_Y + 0.5f : LANE_Y + 0.5f;
        long liveSupport = scene.lanceLeaderId() != 0L
                ? scene.lanceLeaderId()
                : squad != null
                ? BreachAndAssault.nearestSupport(scene.mechId(), squad, sim)
                : 0L;
        if (liveSupport != 0L) {
            supportX = sim.world().x(liveSupport);
            supportY = sim.world().y(liveSupport);
        }

        float toThreatX = threatX - supportX;
        float toThreatY = threatY - supportY;
        float axisLength = (float) Math.sqrt(
                toThreatX * toThreatX + toThreatY * toThreatY);
        float anchorAxisPosition = axisLength > 0f
                ? ((mechX - supportX) * toThreatX
                + (mechY - supportY) * toThreatY) / axisLength
                : 0f;
        float dx = threatX - mechX;
        float dy = threatY - mechY;

        float assignmentX = scene.assignmentX() + 0.5f;
        float assignmentY = LANE_Y + 0.5f;
        float missionAxisX = threatX - assignmentX;
        float missionAxisY = threatY - assignmentY;
        float missionAxisLength = (float) Math.sqrt(
                missionAxisX * missionAxisX + missionAxisY * missionAxisY);
        float missionOvershoot = missionAxisLength > 0f
                ? Math.max(0f, ((mechX - assignmentX) * missionAxisX
                + (mechY - assignmentY) * missionAxisY) / missionAxisLength)
                : 0f;

        String goal = squad != null && squad.currentGoal != null
                ? squad.currentGoal.name() : "none";
        float leaderDriftX = supportX - (LANCE_LEADER_X + 0.5f);
        float leaderDriftY = supportY - (LANCE_LEADER_Y + 0.5f);
        float lanceLeaderDrift = scene.lanceLeaderId() != 0L
                ? (float) Math.sqrt(leaderDriftX * leaderDriftX
                + leaderDriftY * leaderDriftY) : 0f;
        return new Sample((float) Math.sqrt(dx * dx + dy * dy),
                anchorAxisPosition, missionOvershoot, lanceLeaderDrift,
                supportAlive(scene), goal);
    }

    static MechLoadoutComponent loadout(Scene scene) {
        return scene.sim().world().mechLoadout(scene.mechId());
    }

    private static boolean supportAlive(Scene scene) {
        for (long support : scene.supportIds()) {
            if (scene.sim().resolveUnit(support) != 0L) return true;
        }
        return false;
    }

    private static void initializeSquad(Squad squad, long leader, int size,
                                        float centroidX, float centroidY) {
        if (squad == null) throw new IllegalStateException("scene squad was not minted");
        squad.leaderId = leader;
        squad.aliveMembers = size;
        squad.originalSize = size;
        squad.centroidX = centroidX;
        squad.centroidY = centroidY;
    }

    private static BattleSimulation serialSimulation(
            NavigationGrid grid, CellTopology topology) {
        String property = UnitUpdateSystem.MINIMUM_PARALLEL_UNITS_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try {
            return new BattleSimulation(grid, topology, SEED);
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
}
