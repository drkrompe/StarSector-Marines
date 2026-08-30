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

    private static final int SUPPORT_SIZE = 3;
    private static final float REMOVAL_DAMAGE = 1_000_000f;

    /** State retained by the recorder for captions and midpoint removal. */
    record Scene(BattleSimulation sim, long mechId, long threatId,
                 long[] supportIds, int mechSquadId, MechRole requestedRole,
                 int supportRemovalTick) {}

    /** One point-in-time reading used both on-frame and in the printed report. */
    record Sample(float threatDistance, float supportAxisPosition,
                  boolean supportAlive, String goal) {}

    private MechDoctrineScene() {}

    static Scene build(MechRole requestedRole, int supportRemovalTick) {
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
        EntitySpec mechSpec = MechVariant.BULWARK.applyTo(new EntitySpec(
                "doctrine-bulwark", Faction.MARINE, UnitType.HEAVY_MECH,
                MECH_X, LANE_Y).squad(mechSquadId));
        long mech = sim.spawn(mechSpec);
        // Deployment provenance stays identical in every loop. Only the queued
        // battle override below varies.
        sim.world().attachMechLoadout(
                mech, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
        initializeSquad(sim.getSquad(mechSquadId), mech, 1,
                MECH_X + 0.5f, LANE_Y + 0.5f);

        int supportSquadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE_BLUE);
        long[] support = new long[SUPPORT_SIZE];
        for (int index = 0; index < SUPPORT_SIZE; index++) {
            int y = LANE_Y - 1 + index;
            support[index] = sim.spawn(new EntitySpec(
                    "screen-" + index, Faction.MARINE, UnitType.MARINE_BLUE,
                    SUPPORT_X, y)
                    .squad(supportSquadId)
                    .role(UnitRole.GARRISON)
                    .home(SUPPORT_X, y)
                    .moveSpeed(0f)
                    .attackDamage(0f)
                    .attackRange(0f)
                    .accuracy(0f)
                    .visionRange(0f));
        }
        Squad supportSquad = sim.getSquad(supportSquadId);
        initializeSquad(supportSquad, support[1], SUPPORT_SIZE,
                SUPPORT_X + 0.5f, LANE_Y + 0.5f);

        long threat = sim.spawn(new EntitySpec(
                "fixed-contact", Faction.DEFENDER, UnitType.MARINE_RED,
                THREAT_X, LANE_Y)
                .health(100_000f)
                .moveSpeed(0f)
                .attackDamage(0f)
                .attackRange(0f)
                .accuracy(0f)
                .visionRange(0f));

        Squad mechSquad = sim.getSquad(mechSquadId);
        mechSquad.assignedObjective = ObjectiveAssignment.attackMove(
                mechSquadId, ASSIGNMENT_X, LANE_Y);
        // The stationary screen shares the authored mission. This makes it a
        // legal Tank anchor while keeping the physical fixture identical for
        // all four loops; removing it then exercises the Tank's hold-alone law.
        supportSquad.assignedObjective = ObjectiveAssignment.attackMove(
                supportSquadId, ASSIGNMENT_X, LANE_Y);
        SquadBeliefTestAccess.observeDirect(mechSquad, threat,
                THREAT_X, LANE_Y, sim.getSimTickIndex());

        sim.getMechDoctrineService().requestOverride(mech, requestedRole);
        // The production command phase drains the request before the same
        // tick's replan. Recording starts only after that boundary has run.
        sim.advance(BattleSimulation.TICK_DT);
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        if (loadout == null || loadout.effectiveRole() != requestedRole) {
            sim.close();
            throw new IllegalStateException(
                    "queued doctrine was not effective before recording");
        }

        return new Scene(sim, mech, threat, support, mechSquadId,
                requestedRole, supportRemovalTick);
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
        float supportX = SUPPORT_X + 0.5f;
        float supportY = LANE_Y + 0.5f;

        float toThreatX = threatX - supportX;
        float toThreatY = threatY - supportY;
        float axisLength = (float) Math.sqrt(
                toThreatX * toThreatX + toThreatY * toThreatY);
        float supportAxisPosition = axisLength > 0f
                ? ((mechX - supportX) * toThreatX
                + (mechY - supportY) * toThreatY) / axisLength
                : 0f;
        float dx = threatX - mechX;
        float dy = threatY - mechY;

        Squad squad = sim.getSquad(scene.mechSquadId());
        String goal = squad != null && squad.currentGoal != null
                ? squad.currentGoal.name() : "none";
        return new Sample((float) Math.sqrt(dx * dx + dy * dy),
                supportAxisPosition, supportAlive(scene), goal);
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
