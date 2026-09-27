package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
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
    private final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();
    private final String previousCpuProperty = System.getProperty(CPU_PROPERTY);

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) TickInnerProfile.releaseCurrentThread();
        else TickInnerProfile.setCurrent(previousProfile);
        if (previousCpuProperty == null) System.clearProperty(CPU_PROPERTY);
        else System.setProperty(CPU_PROPERTY, previousCpuProperty);
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

        Fixture() {
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 7; x++) grid.setWalkableFloor(x, y);
            }
            graph.rebuild();
            compounds.register(node);
            sim = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> graph;
                        case "getNavigationGridRevision" -> grid.topologyRevision();
                        case "getNavigationTopologyRevision" -> 0L;
                        case "getSquads" -> List.of();
                        case "getSimTickIndex" -> 75;
                        case "getCommanderInfluence" -> null;
                        case "getCompoundService" -> compounds;
                        default -> throw new AssertionError("Unexpected battle read: " + method.getName());
                    });
        }
    }
}
