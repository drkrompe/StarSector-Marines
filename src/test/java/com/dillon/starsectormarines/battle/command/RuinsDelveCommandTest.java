package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RuinsDelveSnapshot.Role;
import com.dillon.starsectormarines.battle.command.RuinsDelveSnapshot.Stage;
import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsDelveCommandTest {

    private static final int W = 40;
    private static final int H = 12;
    /** The approach runs along y = 5 from the landing area at x = 0 to the vault at x = 30. */
    private static final int VAULT_X = 30;
    private static final int ROW = 5;
    private static final CommandTopology TOPOLOGY = topology();

    @Test
    void theLeadTakesOneLayerAtATimeAndTheBodyFollows() {
        RuinsDelveCommand command = new RuinsDelveCommand(3);
        RuinsObjectiveFacts facts = facts("AT_SOURCE", List.of());

        RuinsDelveSnapshot start = plan(command, facts, squad(1, 1, false), squad(2, 0, false), squad(3, 0, false));
        assertEquals(Stage.DELVE, start.stage());
        assertEquals(1, start.leadSquadId(), "the squad nearest the first layer leads");
        assertEquals(10, start.intentFor(1).targetCellX(), "the lead moves to the end of the first layer");
        assertEquals(Role.DELVE_BODY, start.intentFor(2).role());
        assertTrue(start.intentFor(2).targetCellX() <= 5, "the body holds the landing area");

        RuinsDelveSnapshot cleared = plan(command, facts, squad(1, 10, false), squad(2, 0, false), squad(3, 0, false));
        assertEquals(1, cleared.layer(), "a quiet lead on the layer's end clears it");
        assertEquals(10, cleared.intentFor(1).targetCellX(), "the lead waits for the body");
        assertTrue(Math.abs(cleared.intentFor(2).targetCellX() - 10) <= 5, "the body moves up to the cleared layer");

        RuinsDelveSnapshot onward = plan(command, facts, squad(1, 10, false), squad(2, 9, false), squad(3, 11, false));
        assertEquals(20, onward.intentFor(1).targetCellX(), "with the body settled, the lead takes the next layer");
    }

    @Test
    void aLeadInContactDrawsTheBodyUpAndClearsNothing() {
        RuinsDelveCommand command = new RuinsDelveCommand(3);
        RuinsObjectiveFacts facts = facts("AT_SOURCE", List.of());
        plan(command, facts, squad(1, 1, false), squad(2, 0, false));

        RuinsDelveSnapshot contact = plan(command, facts, squad(1, 10, true), squad(2, 0, false));
        assertEquals(0, contact.layer(), "a layer in contact is not cleared");
        assertTrue(Math.abs(contact.intentFor(2).targetCellX() - 10) <= 5, "the body comes up to the lead");
    }

    @Test
    void aQuietLayerSendsOneBodySquadToAFoundCacheAndTheLeadWaits() {
        RuinsDelveCommand command = new RuinsDelveCommand(3);
        RuinsObjectiveFacts withCache = facts("AT_SOURCE", List.of(new RuinsObjectiveFacts.Cache("c1", 3, 9)));

        RuinsDelveSnapshot detour = plan(command, withCache, squad(1, 2, false), squad(2, 1, false),
                squad(3, 0, false));
        assertEquals("c1", detour.detourCacheId());
        assertEquals(Role.CACHE_DETOUR, detour.intentFor(2).role(), "the body squad nearest the cache goes");
        assertEquals(0, detour.intentFor(1).targetCellX() / 5, "the lead holds while the detour is out");

        RuinsDelveSnapshot contact = plan(command, withCache, squad(1, 2, true), squad(2, 2, false),
                squad(3, 0, false));
        assertNull(contact.detourCacheId(), "contact calls the detour back");
        assertEquals(Role.DELVE_BODY, contact.intentFor(2).role());
    }

    @Test
    void atTheVaultOneSquadHoldsTheBreachAndANearerCourtIsHeldOpen() {
        RuinsDelveCommand command = new RuinsDelveCommand(1);
        RuinsObjectiveFacts.LiftPoint landing = new RuinsObjectiveFacts.LiftPoint("Landing area", true, 0, ROW, 31,
                true);
        RuinsObjectiveFacts.LiftPoint court = new RuinsObjectiveFacts.LiftPoint("East court", false, 36, ROW, 7,
                false);
        RuinsObjectiveFacts facts = facts("SECURING", List.of(landing, court), List.of());

        RuinsDelveSnapshot breach = plan(command, facts, squad(1, 29, false), squad(2, 27, false),
                squad(3, 33, false));
        assertEquals(Stage.BREACH, breach.stage());
        assertEquals(Role.VAULT_HOLD, breach.intentFor(1).role());
        assertEquals(Role.COURT_HOLD, breach.intentFor(3).role(), "the squad nearest the court holds it open");
        assertEquals(36, breach.intentFor(3).targetCellX());
        assertEquals(Role.VAULT_SCREEN, breach.intentFor(2).role());

        RuinsDelveSnapshot pair = plan(new RuinsDelveCommand(1), facts, squad(1, 29, false), squad(2, 27, false));
        assertTrue(pair.squadIntents().stream().noneMatch(i -> i.role() == Role.COURT_HOLD),
                "two squads keep together at the vault");
    }

    @Test
    void theHaulMovesUnderExtractionsEscortAndThePlayersSquadIsLeftAlone() {
        RuinsDelveCommand command = new RuinsDelveCommand(1);
        RuinsObjectiveFacts facts = facts("IN_TRANSIT", List.of());
        ObjectiveAssignment hold = ObjectiveAssignment.defendArea(4, 2, 2, 3);
        CommandDirective order = new CommandDirective(4, Faction.MARINE, "player", CommandAuthority.PLAYER_INTERVENTION,
                "fixture", hold, 0, -1, CommandDirective.Status.ACTIVE, "");
        CommandSquadState held = new CommandSquadState(4, Faction.MARINE, 4, 2.5f, 2.5f, 2, 2, 0, UnitRole.PATROL,
                false, null, hold, order, 0, new int[0]);

        RuinsDelveSnapshot haul = plan(command, facts, squad(1, 24, false), squad(2, 26, false), held);
        assertEquals(Stage.HAUL, haul.stage());
        assertEquals(Role.PAYLOAD_ELEMENT, haul.intentFor(1).role(), "the squad nearest the haul carries it");
        assertEquals(Role.CLOSE_ESCORT, haul.intentFor(2).role());
        assertEquals(Role.EXTERNAL, haul.intentFor(4).role());
    }

    private static RuinsDelveSnapshot plan(RuinsDelveCommand command, RuinsObjectiveFacts facts,
                                           CommandSquadState... squads) {
        CommandFrame common = new CommandFrame(30, Faction.MARINE, List.of(squads), null, TOPOLOGY,
                new CommandAssignmentSnapshot(Map.of()));
        return command.plan(RuinsDelveCommandFrame.of(common, facts)).detail();
    }

    private static CommandSquadState squad(int id, int x, boolean contact) {
        return new CommandSquadState(id, Faction.MARINE, 4, x + .5f, ROW + .5f, x, ROW, 0, UnitRole.PATROL, contact,
                null, null, null, 0, new int[0]);
    }

    private static RuinsObjectiveFacts facts(String phase, List<RuinsObjectiveFacts.Cache> caches) {
        return facts(phase, List.of(new RuinsObjectiveFacts.LiftPoint("Landing area", true, 0, ROW, VAULT_X + 1,
                true)), caches);
    }

    private static RuinsObjectiveFacts facts(String phase, List<RuinsObjectiveFacts.LiftPoint> lifts,
                                             List<RuinsObjectiveFacts.Cache> caches) {
        List<Integer> route = new ArrayList<>();
        for (int x = 0; x <= VAULT_X; x++) { route.add(x); route.add(ROW); }
        int[] approach = route.stream().mapToInt(Integer::intValue).toArray();
        boolean moving = phase.equals("IN_TRANSIT");
        return new RuinsObjectiveFacts("RUINS-01", "vault haul", "The vault", phase, VAULT_X, ROW, approach,
                moving ? 25 : VAULT_X, ROW, moving ? 19 : -1, ROW, phase.equals("SECURING") ? 5f : 0f, 30f, false,
                lifts, 0, phase.equals("SECURING"), -1, -1, phase.equals("SECURING"), false, false, false,
                ExtractionPayloadObjective.Failure.NONE, caches);
    }

    private static CommandTopology topology() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        ZoneGraph graph = new ZoneGraph(grid);
        BattleView view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "getZoneGraph" -> graph;
                    default -> throw new AssertionError("Unexpected topology read: " + method.getName());
                });
        return CommandTopology.freeze(view);
    }
}
