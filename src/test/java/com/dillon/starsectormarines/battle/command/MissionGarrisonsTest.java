package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mission defender's reactive manning ({@link MissionGarrisons}) on a small map: a barracks (x 40..55, y 20..30)
 * with four posts on its north wall, a parapet three cells off that wall (its apron), and a second building far east.
 * The attack mostly comes from the south, where only the barracks is within reach of it.
 * Pressure is a field around one believed contact; reach is free. Each test is a handful of pulses.
 */
class MissionGarrisonsTest {

    private static final int W = 120, H = 60, BLOCK = 8;
    private static final String ISSUER = "test-defender";
    private static final TacticalNode BARRACKS = building(40, 20, 55, 30);
    private static final TacticalNode FAR = building(95, 40, 110, 50);

    private final MissionGarrisons garrisons = new MissionGarrisons();
    private final GarrisonableRegistry places = registry();
    private final int barracks = places.envelopeOf(BARRACKS);
    private final int apron = places.idAt(46, 17);

    private static TacticalNode building(int left, int top, int right, int bottom) {
        return new TacticalNode(TacticalNode.Kind.BARRACKS, (left + right) / 2, (top + bottom) / 2,
                left, top, right, bottom, Faction.DEFENDER, 60, 0);
    }

    private static GarrisonableRegistry registry() {
        DoodadDef bag = new DoodadDef("doodad.test.bag", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f, 1, 1, null, 0, null,
                FightingPositionRole.PARAPET);
        List<Doodad> props = new ArrayList<>();
        for (int x = 44; x < 50; x++) props.add(new Doodad(x, 17, bag));
        return GarrisonableRegistry.build(FightingPositionIndex.build(W, H, props, (x, y) -> true), W, H, BLOCK,
                new int[0], List.of(new GarrisonableRegistry.Envelope(BARRACKS, new int[]{47, 20, 48, 20, 42, 20, 53, 20}),
                        new GarrisonableRegistry.Envelope(FAR, new int[]{100, 40, 104, 40})));
    }

    /** A believed contact and the pressure it raises: {@code weight} on its own block and every block beside it. */
    private record Picture(int x, int y, float weight) {
        GarrisonDirector.Pressure field() {
            return (bx, by) -> Math.max(Math.abs(bx - x / BLOCK), Math.abs(by - y / BLOCK)) <= 1 ? weight : 0f;
        }
    }

    private static final Picture QUIET = new Picture(-100, -100, 0f);

    private static CommandSquadState squad(int id, float x, float y, UnitRole role, boolean contact,
                                           ObjectiveAssignment order) {
        CommandDirective directive = order == null ? null : new CommandDirective(id, Faction.DEFENDER, ISSUER,
                CommandAuthority.MISSION_COMMAND, "TEST", order, 0, 0, -1, CommandDirective.Status.ACTIVE, "");
        return new CommandSquadState(id, Faction.DEFENDER, 6, x, y, (int) x, (int) y, 0, role, contact, null,
                order, directive, 0, new int[0]);
    }

    private static CommandSquadState patrol(int id, float x, float y) {
        return squad(id, x, y, UnitRole.PATROL, false, null);
    }

    private Map<Integer, MissionGarrisons.Manning> pulse(Picture picture, List<CommandSquadState> squads,
                                                        Map<Integer, MissionGarrisons.Offer> offers) {
        int[] contact = {picture.x, picture.y};
        return garrisons.plan(squads, places, picture.field(), contact, picture.weight > 0 ? 1 : 0,
                (sx, sy, x, y) -> true, offers, ISSUER);
    }

    private static Map<Integer, MissionGarrisons.Offer> reserve(int... ids) {
        Map<Integer, MissionGarrisons.Offer> offers = new TreeMap<>();
        for (int id : ids) offers.put(id, MissionGarrisons.Offer.reserve());
        return offers;
    }

    @Test
    void aReserveSquadMansThePressedBuildingAndLeavesTheQuietOneEmpty() {
        Picture pressed = new Picture(48, 38, 5f);
        Map<Integer, MissionGarrisons.Manning> out = pulse(pressed,
                List.of(patrol(1, 20, 25), patrol(2, 80, 50)), reserve(1, 2));

        assertEquals(1, out.size(), "one squad sent; the other is the reserve");
        MissionGarrisons.Manning man = out.get(1);
        assertNotNull(man, "the nearer squad is sent");
        assertEquals(MissionGarrisons.Kind.MAN, man.kind());
        assertEquals(barracks, man.place());
        assertTrue(man.order().isEnvelope(), "manned by holding the building");
        assertSame(BARRACKS, man.order().targetNode());
    }

    @Test
    void theLastRovingSquadIsTheReserveButADutySquadMansBesideItsPost() {
        Picture pressed = new Picture(48, 38, 5f);
        assertTrue(pulse(pressed, List.of(patrol(1, 20, 25)), reserve(1)).isEmpty(), "the reserve is never emptied");

        Map<Integer, MissionGarrisons.Offer> duty = new TreeMap<>();
        duty.put(3, MissionGarrisons.Offer.duty(47, 34));
        Map<Integer, MissionGarrisons.Manning> out = pulse(pressed, List.of(patrol(3, 47, 34)), duty);
        assertEquals(MissionGarrisons.Kind.MAN, out.get(3).kind(), "a post beside the building is held from it");

        MissionGarrisons distant = new MissionGarrisons();
        Map<Integer, MissionGarrisons.Offer> far = new TreeMap<>();
        far.put(4, MissionGarrisons.Offer.duty(80, 25));
        assertTrue(distant.plan(List.of(patrol(4, 80, 25)), places, pressed.field(), new int[]{48, 38}, 1,
                (sx, sy, x, y) -> true, far, ISSUER).isEmpty(), "a post past the duty radius stays a post");
    }

    @Test
    void aHolderCapBoundsNewSendsButTurnsNobodyOut() {
        // Both buildings pressed, a duty squad beside each: uncapped both go, capped at one only the first does.
        GarrisonDirector.Pressure everywhere = (bx, by) -> 5f;
        int[] contacts = {48, 38, 102, 54};
        Map<Integer, MissionGarrisons.Offer> duty = new TreeMap<>();
        duty.put(1, MissionGarrisons.Offer.duty(47, 34));
        duty.put(2, MissionGarrisons.Offer.duty(102, 54));
        List<CommandSquadState> squads = List.of(patrol(1, 47, 34), patrol(2, 102, 54));
        assertEquals(2, new MissionGarrisons().plan(squads, places, everywhere, contacts, 2,
                (sx, sy, x, y) -> true, duty, ISSUER).size(), "uncapped, every pressed place gets a picket");

        Map<Integer, MissionGarrisons.Manning> capped = garrisons.plan(squads, places, everywhere, contacts, 2,
                (sx, sy, x, y) -> true, duty, ISSUER, 1);
        assertEquals(1, capped.size(), "capped at one holder, one squad is sent");
        int sent = capped.keySet().iterator().next();
        ObjectiveAssignment order = capped.get(sent).order();
        int other = sent == 1 ? 2 : 1;
        CommandSquadState holder = squad(sent, places.centreX(capped.get(sent).place()),
                places.centreY(capped.get(sent).place()), UnitRole.PATROL, false, order);
        CommandSquadState free = sent == 1 ? patrol(2, 102, 54) : patrol(1, 47, 34);
        Map<Integer, MissionGarrisons.Manning> next = garrisons.plan(List.of(holder, free), places, everywhere,
                contacts, 2, (sx, sy, x, y) -> true, duty, ISSUER, 1);
        assertEquals(MissionGarrisons.Kind.STAND, next.get(sent).kind(), "the holder keeps its place");
        assertFalse(next.containsKey(other), "and the cap keeps the other on its post in the open");
    }

    @Test
    void aHolderStandsWhilePressedAndIsGivenBackAfterTheDwell() {
        Picture pressed = new Picture(48, 38, 5f);
        ObjectiveAssignment order = pulse(pressed, List.of(patrol(1, 20, 25), patrol(2, 80, 50)), reserve(1, 2))
                .get(1).order();
        int centreX = places.centreX(barracks);
        int centreY = places.centreY(barracks);
        List<CommandSquadState> manned = List.of(squad(1, centreX, centreY, UnitRole.PATROL, false, order),
                patrol(2, 80, 50));

        assertEquals(MissionGarrisons.Kind.STAND, pulse(pressed, manned, reserve(1, 2)).get(1).kind());
        for (int i = 1; i < GarrisonDirector.DWELL_PULSES; i++) {
            assertEquals(MissionGarrisons.Kind.STAND, pulse(QUIET, manned, reserve(1, 2)).get(1).kind(),
                    "cool pulse " + i + " of the dwell");
        }
        assertFalse(pulse(QUIET, manned, reserve(1, 2)).containsKey(1), "given back to the mission");
        assertEquals(1, garrisons.stats().releases);
    }

    @Test
    void aSquadTheMissionTakesBackIsForgotten() {
        Picture pressed = new Picture(48, 38, 5f);
        ObjectiveAssignment order = pulse(pressed, List.of(patrol(1, 20, 25), patrol(2, 80, 50)), reserve(1, 2))
                .get(1).order();
        List<CommandSquadState> manned = List.of(squad(1, 47, 25, UnitRole.PATROL, false, order), patrol(2, 80, 50));
        // The alarm took squad 1 for a mission duty far from the building.
        Map<Integer, MissionGarrisons.Offer> offers = reserve(2);
        offers.put(1, MissionGarrisons.Offer.duty(10, 50));
        Map<Integer, MissionGarrisons.Manning> out = pulse(pressed, manned, offers);
        assertFalse(out.containsKey(1), "the mission's duty outranks the place");
        assertTrue(pulse(pressed, manned, reserve(2)).isEmpty(), "and the reserve is not emptied to replace it");
    }

    @Test
    void anEnemyInsideTheBuildingDrivesItsGarrisonIntoTheRooms() {
        Picture pressed = new Picture(48, 38, 5f);
        ObjectiveAssignment order = pulse(pressed, List.of(patrol(1, 20, 25), patrol(2, 80, 50)), reserve(1, 2))
                .get(1).order();
        List<CommandSquadState> manned = List.of(squad(1, 47, 24, UnitRole.PATROL, false, order), patrol(2, 80, 50));
        MissionGarrisons.Manning rooms = pulse(new Picture(48, 26, 5f), manned, reserve(1, 2)).get(1);

        assertEquals(MissionGarrisons.Kind.ROOMS, rooms.kind());
        assertEquals(AssignmentKind.DEFEND_AREA, rooms.order().kind());
        assertEquals(BARRACKS.anchorX, rooms.order().targetCellX());
        List<CommandSquadState> inside = List.of(squad(1, 47, 24, UnitRole.PATROL, true, rooms.order()),
                patrol(2, 80, 50));
        assertEquals(MissionGarrisons.Kind.ROOMS, pulse(new Picture(48, 26, 5f), inside, reserve(1, 2)).get(1).kind(),
                "the rooms are held while the building is pressed");
        List<CommandSquadState> quiet = List.of(squad(1, 47, 24, UnitRole.PATROL, false, rooms.order()),
                patrol(2, 80, 50));
        for (int i = 1; i < MissionGarrisons.INSIDE_COOL_PLANS; i++) pulse(QUIET, quiet, reserve(1, 2));
        assertFalse(pulse(QUIET, quiet, reserve(1, 2)).containsKey(1), "and given back once it has been quiet");
    }

    @Test
    void aGarrisonDrivenOffTheApronFallsBackIntoTheBuildingItFronts() {
        assertTrue(apron >= 0 && !places.isEnvelope(apron));
        assertEquals(barracks, MissionGarrisons.frontedEnvelope(apron, places));
        ObjectiveAssignment onApron = GarrisonDirector.orderFor(1, apron, places);
        List<CommandSquadState> manned = List.of(squad(1, 46, 17, UnitRole.PATROL, false, onApron), patrol(2, 80, 50));
        MissionGarrisons.Manning back = pulse(new Picture(47, 17, 5f), manned, reserve(1, 2)).get(1);

        assertEquals(MissionGarrisons.Kind.FALL_BACK, back.kind());
        assertEquals(barracks, back.place());
        assertTrue(back.order().isEnvelope());
    }

    @Test
    void aBuildingItsBornGarrisonStandsInIsNotSentASecondSquad() {
        Picture pressed = new Picture(48, 38, 5f);
        List<CommandSquadState> squads = List.of(squad(9, 47, 25, UnitRole.GARRISON, false, null),
                patrol(1, 20, 25), patrol(2, 80, 50));
        assertTrue(pulse(pressed, squads, reserve(1, 2)).isEmpty(), "declared holds stay held, and count as manned");
    }

    @Test
    void aSquadInAFightTakesOnlyTheCoverBesideIt() {
        Picture pressed = new Picture(48, 38, 5f);
        assertTrue(pulse(pressed, List.of(squad(1, 20, 25, UnitRole.PATROL, true, null)), reserve(1)).isEmpty(),
                "a fight twenty cells off is not abandoned for a building");
        Map<Integer, MissionGarrisons.Manning> out = pulse(pressed,
                List.of(squad(1, 47, 35, UnitRole.PATROL, true, null)), reserve(1));
        assertEquals(MissionGarrisons.Kind.MAN, out.get(1).kind(),
                "a squad fighting beside the building takes it, reserve floor or not");
    }

    @Test
    void theRewriteReplacesOnlyTheNamedSquadsProposals() {
        List<CommandProposal> proposals = List.of(
                CommandProposal.claim(1, CommandAuthority.MISSION_COMMAND, "MOBILE_RESERVE_HELD",
                        CommandStabilityBreak.CONTEXT_INVALIDATED),
                CommandProposal.claim(2, CommandAuthority.MISSION_COMMAND, "MOBILE_RESERVE_HELD",
                        CommandStabilityBreak.CONTEXT_INVALIDATED));
        ObjectiveAssignment order = GarrisonDirector.orderFor(1, barracks, places);
        Map<Integer, MissionGarrisons.Manning> manning = Map.of(1,
                new MissionGarrisons.Manning(1, MissionGarrisons.Kind.MAN, order, barracks));
        List<CommandProposal> out = MissionGarrisons.rewrite(proposals, manning,
                List.of(patrol(1, 20, 25), patrol(2, 80, 50)));

        assertEquals(2, out.size());
        assertEquals(CommandProposal.Action.ASSIGN, out.get(0).action());
        assertEquals(order, out.get(0).assignment());
        assertEquals("GARRISONABLE_MAN", out.get(0).reason());
        assertSame(proposals.get(1), out.get(1));
    }

    @Test
    void aWarmAreaStagesAReserveWhichIsThenTheOneSentToMan() {
        List<CommandSquadState> pool = List.of(patrol(1, 100, 10), patrol(2, 110, 55), patrol(3, 115, 55));
        Map<Integer, MissionGarrisons.Manning> warm = pulse(new Picture(48, 38, 2.5f), pool, reserve(1, 2, 3));
        assertEquals(1, warm.size(), "warm, not hot: one squad staged, nobody manning");
        MissionGarrisons.Manning staged = warm.values().iterator().next();
        assertEquals(MissionGarrisons.Kind.STAGE, staged.kind());
        assertEquals(-1, staged.place(), "staged for an area, not holding a place");
        assertEquals(AssignmentKind.DEFEND_AREA, staged.order().kind());
        int id = staged.squadId();

        // It walks up to its staging cell and the pressure rises: it, not the nearer reserve, is the one sent.
        List<CommandSquadState> arrived = new ArrayList<>();
        for (CommandSquadState squad : pool) {
            arrived.add(squad.squadId() == id
                    ? squad(id, staged.cellX(), staged.cellY(), UnitRole.PATROL, false, staged.order()) : squad);
        }
        Map<Integer, MissionGarrisons.Manning> hot = pulse(new Picture(48, 38, 5f), arrived, reserve(1, 2, 3));
        assertEquals(MissionGarrisons.Kind.MAN, hot.get(id).kind(), "the staged squad mans its area's place");
        assertEquals(barracks, hot.get(id).place());
        assertEquals(1, garrisons.stats().manFromStage);
    }

    @Test
    void aStagedSquadTheMissionTakesBackIsForgotten() {
        List<CommandSquadState> pool = List.of(patrol(1, 100, 10), patrol(2, 110, 55), patrol(3, 115, 55));
        Map<Integer, MissionGarrisons.Manning> warm = pulse(new Picture(48, 38, 2.5f), pool, reserve(1, 2, 3));
        int id = warm.keySet().iterator().next();
        Map<Integer, MissionGarrisons.Offer> without = reserve(1, 2, 3);
        without.remove(id);
        assertFalse(pulse(new Picture(48, 38, 2.5f), pool, without).containsKey(id), "the mission outranks the flow");
        assertTrue(garrisons.flow().posts().isEmpty() || !garrisons.flow().posts().containsKey(id));
    }
}
