package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandFrameProfileTest {
    private static final String CPU_PROPERTY = "battle.profile.commandTopologyCpu";
    private static final String DETAIL_PROPERTY = "battle.profile.commandFrameDetail";
    private final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();
    private final String previousCpuProperty = System.getProperty(CPU_PROPERTY);
    private final String previousDetailProperty = System.getProperty(DETAIL_PROPERTY);

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) TickInnerProfile.releaseCurrentThread();
        else TickInnerProfile.setCurrent(previousProfile);
        if (previousCpuProperty == null) System.clearProperty(CPU_PROPERTY);
        else System.setProperty(CPU_PROPERTY, previousCpuProperty);
        if (previousDetailProperty == null) System.clearProperty(DETAIL_PROPERTY);
        else System.setProperty(DETAIL_PROPERTY, previousDetailProperty);
    }

    @Test
    void detailedSlicesAreOptInAndPreserveEverySquadRowField() throws ReflectiveOperationException {
        Fixture fixture = new Fixture(true);
        CommandTopology topology = CommandTopology.freeze(fixture.sim);
        CommandAssignmentSnapshot assignments = new CommandAssignmentSnapshot(Map.of());
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        System.setProperty(DETAIL_PROPERTY, "false");
        CommandFrame control = CommandFrame.freeze(fixture.sim, Faction.MARINE, topology, assignments);
        assertDetailStages(profile, 0);
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_ROWS));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_MEMBER_INPUTS));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_BELIEF_INPUTS));

        System.setProperty(DETAIL_PROPERTY, "true");
        CommandFrame measured = CommandFrame.freeze(fixture.sim, Faction.MARINE, topology, assignments);
        assertDetailStages(profile, 1);
        assertEquals(2, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUADS));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_ROWS));
        assertEquals(2, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_MEMBER_INPUTS));
        assertEquals(2, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_BELIEF_INPUTS));
        assertEquals(control.squads().size(), measured.squads().size());
        for (int i = 0; i < control.squads().size(); i++) {
            for (var component : CommandSquadState.class.getRecordComponents()) {
                Object expected = component.getAccessor().invoke(control.squads().get(i));
                Object actual = component.getAccessor().invoke(measured.squads().get(i));
                if (expected instanceof int[] array) assertArrayEquals(array, (int[]) actual);
                else assertEquals(expected, actual, component.getName());
            }
        }
        TickInnerProfile.releaseCurrentThread();
        CommandFrame.freeze(fixture.sim, Faction.MARINE, topology, assignments);
        assertNull(TickInnerProfile.currentIfBound(), "detail does not bind a diagnostic profile");
    }

    private static void assertDetailStages(TickInnerProfile profile, int count) {
        for (TickInnerProfile.Bucket bucket : new TickInnerProfile.Bucket[]{
                TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_ROSTER,
                TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_MEMBERS,
                TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_CONTACT,
                TickInnerProfile.Bucket.COMMANDER_FRAME_SQUAD_PUBLICATION}) {
            assertEquals(count, profile.countOf(bucket), bucket.name());
        }
    }

    @Test
    void perspectiveFreezesRecordStagesWithoutChangingDisclosedFacts() {
        Fixture fixture = new Fixture();
        TickInnerProfile.releaseCurrentThread();
        CommandTopology topology = CommandTopology.freeze(fixture.sim);
        CommandAssignmentSnapshot assignments = new CommandAssignmentSnapshot(Map.of());
        ConquestCommandFrame control = ConquestCommandFrame.disclose(fixture.sim,
                Faction.MARINE, topology, assignments);
        assertNull(TickInnerProfile.currentIfBound());

        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        for (Faction faction : List.of(Faction.MARINE, Faction.DEFENDER)) {
            ConquestCommandFrame frame = ConquestCommandFrame.disclose(fixture.sim,
                    faction, topology, assignments);
            assertEquals(faction, frame.perspective());
            assertEquals(control.tick(), frame.tick());
            assertEquals(control.squads(), frame.squads());
            ConquestCommandFacts.Compound expected = control.facts().compounds().get(0);
            ConquestCommandFacts.Compound actual = frame.facts().compounds().get(0);
            assertEquals(expected.state(), actual.state());
            assertEquals(expected.captureZoneId(), actual.captureZoneId());
            assertArrayEquals(expected.garrisonZoneIds(), actual.garrisonZoneIds());
            assertNotSame(fixture.node, actual.node());
        }
        assertStages(profile, 2);
    }

    @Test
    void serviceMeasuresOneCompleteFrameCpuEnvelopeOnlyWhenEnabled() {
        Fixture fixture = new Fixture();
        CommanderService service = new CommanderService();
        for (Faction faction : List.of(Faction.MARINE, Faction.DEFENDER)) {
            service.setAutonomousCommander(faction, new EmptyCommand(faction),
                    ConquestCommandDisclosure.INSTANCE);
        }
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        System.setProperty(CPU_PROPERTY, "false");
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, fixture.sim);
        assertStages(profile, 2);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_CPU));

        System.setProperty(CPU_PROPERTY, "true");
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, fixture.sim);
        assertStages(profile, 4);
        assertEquals(2, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME));
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        int expected = bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled() ? 1 : 0;
        assertEquals(expected, profile.countOf(TickInnerProfile.Bucket.COMMANDER_FRAME_CPU));
    }

    private static void assertStages(TickInnerProfile profile, int count) {
        for (TickInnerProfile.Bucket bucket : new TickInnerProfile.Bucket[]{
                TickInnerProfile.Bucket.COMMANDER_FRAME_ASSIGNMENTS,
                TickInnerProfile.Bucket.COMMANDER_FRAME_SQUADS,
                TickInnerProfile.Bucket.COMMANDER_FRAME_INFLUENCE,
                TickInnerProfile.Bucket.COMMANDER_FRAME_FACTS}) {
            assertEquals(count, profile.countOf(bucket), bucket.name());
        }
    }

    private record EmptyCommand(Faction faction)
            implements AutonomousMissionCommand<ConquestCommandFrame, String> {
        @Override public String strategyId() { return "profile-test"; }
        @Override public CommandPlan<String> plan(ConquestCommandFrame frame) {
            return new CommandPlan<>(faction, strategyId(), "idle", frame.tick(),
                    -1, 0, 0, List.of(), List.of(), "unchanged");
        }
        @Override public void publish(CommanderSnapshot<String> snapshot) { }
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(7, 5);
        final ZoneGraph graph = new ZoneGraph(grid);
        final CompoundService compounds = new CompoundService();
        final TacticalNode node = new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                2, 2, 0, 0, 6, 4, Faction.DEFENDER, 1, 0);
        final BattleView sim;

        Fixture() { this(false); }

        Fixture(boolean withMembers) {
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 7; x++) grid.setWalkableFloor(x, y);
            }
            graph.rebuild();
            compounds.register(node);
            UnitRosterService roster = withMembers
                    ? new UnitRosterService(new UnitSpatialIndex(7, 5), null) : null;
            List<Squad> squads;
            if (withMembers) {
                int ownId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
                long leader = roster.spawn(new EntitySpec("leader", Faction.MARINE,
                        UnitType.MARINE, 1, 1).squad(ownId));
                roster.spawn(new EntitySpec("member", Faction.MARINE,
                        UnitType.MARINE, 2, 1).squad(ownId));
                int enemyId = roster.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
                long enemy = roster.spawn(new EntitySpec("enemy", Faction.DEFENDER,
                        UnitType.MILITIA, 5, 3).squad(enemyId));
                Squad own = roster.getSquad(ownId);
                own.leaderId = leader;
                own.aliveMembers = 2;
                own.centroidX = 2f;
                own.centroidY = 1.5f;
                SquadBeliefTestAccess.observeDirect(own, enemy, 5, 3, 75);
                SquadBeliefTestAccess.observeDirect(own, Long.MAX_VALUE, 6, 3, 75);
                squads = List.of(own, roster.getSquad(enemyId));
            } else squads = List.of();
            sim = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> graph;
                        case "getNavigationGridRevision" -> grid.topologyRevision();
                        case "getNavigationTopologyRevision" -> 0L;
                        case "getSquads" -> squads;
                        case "squadMemberCount" -> roster.squadMemberCount((int) args[0]);
                        case "squadMemberAt" -> roster.squadMemberArray((int) args[0])[(int) args[1]];
                        case "resolveUnit" -> roster.bodies().isTargetable((long) args[0]) ? args[0] : 0L;
                        case "world" -> roster.world();
                        case "role" -> roster.role();
                        case "movement" -> roster.movement();
                        case "combat" -> roster.combat();
                        case "identity" -> roster.identity();
                        case "squad" -> roster.squad();
                        case "isRiding" -> roster.isRiding((long) args[0]);
                        case "getDoodadCoverAt" -> 0;
                        case "getSimTickIndex" -> 75;
                        case "getCommanderInfluence" -> null;
                        case "getCompoundService" -> compounds;
                        default -> throw new AssertionError("Unexpected battle read: " + method.getName());
                    });
        }
    }
}
