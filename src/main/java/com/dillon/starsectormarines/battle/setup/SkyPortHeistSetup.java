package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.GroundMarineArrival;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.RaidCommand;
import com.dillon.starsectormarines.battle.command.RaidCommandDisclosure;
import com.dillon.starsectormarines.battle.command.RaidDefenderCommand;
import com.dillon.starsectormarines.battle.command.RaidDefenderCommandDisclosure;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The freighter heist on the sky port station (`sky-port-heist.md`; owner, 2026-11-09): a Raid whose target can be
 * worked only while a freighter stands at its berth. The company lands on the landing deck, fights up a viaduct
 * and across the concourse to the platform the freighter is due at, seizes the cargo from its hold while it is
 * berthed, and brings the cargo back to the landing deck. If the freighter leaves first, the raid has failed.
 *
 * <p>One schedule, {@link #ARRIVE_SECONDS} to {@link #DEPART_SECONDS} of the battle's clock, is both the raid's
 * {@link RaidObjective.ServiceWindow} and the freighter the backdrop draws in its slip ({@link VoidBackdrop.Call}),
 * so the hold opens exactly while the hull is seen at the berth. Port security holds the viaduct heads and the
 * berth; a mobile reserve on the concourse answers the alarm under {@link RaidDefenderCommand}.
 *
 * <p>Every time, place and strength here is a first placement (UNTUNED): balance is the owner's playtest.
 */
public final class SkyPortHeistSetup {

    /** The station's owner, whose roster port security is drawn from (an open question to the owner). */
    static final String OWNER = "tritachyon";
    /** The slip the freighter berths in: the canyon between the third and fourth platforms. */
    public static final int SLIP = 2;
    /** The freighter's hull, one of the backdrop's craft. */
    public static final String HULL = "graphics/ships/tarsus/tarsus_base.png";
    /** When the freighter stands at its berth, in seconds of the battle (UNTUNED). */
    public static final float ARRIVE_SECONDS = 240f, DEPART_SECONDS = 420f;
    /** The freighter's hold: the platform edge beside its berth, on the third platform. */
    public static final int TARGET_X = SkyPortClusterSetup.PLATFORM_X0 + 12,
            TARGET_Y = SkyPortClusterSetup.PLATFORMS[SLIP][3] - 1;
    /** Where the cargo goes back to: the landing deck, where the company landed. */
    public static final int EGRESS_X = SkyPortClusterSetup.ARRIVAL_X, EGRESS_Y = SkyPortClusterSetup.ARRIVAL_Y;

    /** A post port security holds: its name, where, and how many. */
    record Post(String id, int x, int y, int size) { }

    /** Garrisons: the two viaduct heads on the concourse, and the berth (UNTUNED). */
    static final List<Post> GARRISONS = List.of(
            new Post("north-viaduct-head", 80, 140, 6),
            new Post("south-viaduct-head", 80, 97, 4),
            new Post("berth", TARGET_X + 8, TARGET_Y - 6, 4));
    /** The mobile reserve on the concourse, which the alarm sends (UNTUNED). */
    static final List<Post> RESERVE = List.of(
            new Post("concourse-north", 104, 156, 6),
            new Post("concourse-south", 104, 84, 6));

    private SkyPortHeistSetup() { }

    public static BattleSimulation create(long seed) {
        SkyPortClusterSetup.Ground ground = SkyPortClusterSetup.lay(true);
        ground.topology().setVoidBackdrop(ground.topology().voidBackdrop()
                .withCalls(List.of(new VoidBackdrop.Call(SLIP, HULL, ARRIVE_SECONDS, DEPART_SECONDS))));
        BattleSimulation sim = new BattleSimulation(ground.grid(), ground.topology(), seed);
        sim.setSurface(SurfacePalette.ARID);
        sim.setGroundMarineArrival(new GroundMarineArrival(SkyPortClusterSetup.ARRIVAL_X, SkyPortClusterSetup.ARRIVAL_Y,
                SquadCommandClaim.mission("sky-port-heist", "Company raids the freighter")));
        sim.setMarineLandingPads(List.of(
                new LandingPad(24, 89, 15, 15, LandingPad.Approach.WEST),
                new LandingPad(24, 150, 15, 15, LandingPad.Approach.WEST)));
        sim.playerCommandsCompany();

        int targetZone = sim.getZoneGraph().zoneIdAt(TARGET_X, TARGET_Y);
        sim.addObjective(new RaidObjective("sky-port-freighter", "Freighter's hold", TARGET_X, TARGET_Y,
                Math.max(0, targetZone), EGRESS_X, EGRESS_Y).withServiceWindow(window()));
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));

        Random rng = new Random(seed);
        GroundRosterProfile roster = GroundRosterRegistry.resolve(OWNER);
        for (Post post : GARRISONS) {
            int squadId = squad(sim, roster, post, UnitRole.GARRISON, rng);
            Squad squad = sim.getSquad(squadId);
            squad.assignedNode = BattleSetup.openingDefenseNode(post.x(), post.y(), Faction.DEFENDER, sim.getGrid());
            squad.patrolRadius = 4;
            sim.assignSquadCommand(ObjectiveAssignment.holdNode(squadId, squad.assignedNode), CommandAuthority.GARRISON,
                    "sky-port-heist-garrison", "Port security holds " + post.id());
        }
        Set<Integer> mobile = new LinkedHashSet<>();
        for (Post post : RESERVE) {
            int squadId = squad(sim, roster, post, UnitRole.PATROL, rng);
            mobile.add(squadId);
            sim.claimSquadCommand(squadId, CommandAuthority.MISSION_COMMAND, "sky-port-heist-reserve",
                    "Port security reserve on the concourse");
        }
        sim.setAutonomousCommander(Faction.MARINE, new RaidCommand(), RaidCommandDisclosure.INSTANCE);
        sim.setAutonomousCommander(Faction.DEFENDER, new RaidDefenderCommand(mobile),
                RaidDefenderCommandDisclosure.INSTANCE);
        return sim;
    }

    /** The freighter's time at its berth as the raid's window, in sim ticks. */
    public static RaidObjective.ServiceWindow window() {
        return new RaidObjective.ServiceWindow(Math.round(ARRIVE_SECONDS / BattleSimulation.TICK_DT),
                Math.round(DEPART_SECONDS / BattleSimulation.TICK_DT));
    }

    /** One squad of port security spawned round its post. */
    private static int squad(BattleSimulation sim, GroundRosterProfile roster, Post post, UnitRole role, Random rng) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
        List<int[]> cells = BattleSetup.pickSpawnCluster(sim.getGrid(), post.x(), post.y(), post.size());
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            sim.spawn(BattleSetup.makeDefender(post.id() + "-" + i, Faction.DEFENDER, UnitType.MARINE_RED, cell[0],
                            cell[1], RiskLevel.MEDIUM, roster, GroundRosterProfile.ForceTier.ELITE, rng, null)
                    .role(role).home(cell[0], cell[1]).squad(squadId));
        }
        sim.getSquad(squadId).originalSize = cells.size();
        return squadId;
    }
}
