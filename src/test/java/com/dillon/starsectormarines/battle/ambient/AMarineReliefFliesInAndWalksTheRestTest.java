package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.StructureWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The marines' relief crosses the map by air and walks the last of it; the
 * defender's walks the whole way.
 *
 * <p>Not a fairness knob — it is what each side has. A garrison's rear is the
 * map edge, so its people simply come from it. An attacker has no rear on this
 * planet at all: everybody they field arrived by air, and a technician posted to
 * a shed they captured arrives the same way as everyone else did.
 *
 * <p>What the two arrivals have in common is the part that matters, and it is
 * asserted on both: the person is put on the map somewhere they have to walk
 * from, holding the billet's own rotation, and nothing carries them the rest of
 * the way. A lift that set its passenger down at the bench would be a spawner
 * with an aircraft drawn around it, and would make a facility deep inside held
 * ground exactly as cheap to re-crew as one on the perimeter.
 *
 * <p>The map is deliberately tall. On a short one the stand-off pad and the
 * marine rear edge are within a few cells of each other, and a test that could
 * not tell a flight from a walk would pass whichever one happened.
 */
class AMarineReliefFliesInAndWalksTheRestTest {

    private static final int W = 40;
    private static final int H = 90;
    private static final int BAY_X = 10;
    private static final int BAY_Y = 45;
    private static final int BAY_W = 20;
    private static final int BAY_H = 12;

    /** Room centre, which is what a relief is aimed at. */
    private static final int WORK_Y = BAY_Y + BAY_H / 2;

    /**
     * A marine relief comes in by air, and sets down short of the work.
     *
     * <p>Two claims and both are load-bearing. It is a flight — a carrier is on
     * the way before anybody is on the ground, so there is something an
     * interceptor could reach. And it stops short — the technician lands well
     * away from the shed and is left to walk, so taking a building deep in your
     * own ground does not make re-crewing it free.
     */
    @Test
    void theMarineReliefIsAFlightThatStopsShortOfTheWork() {
        Works works = works();
        works.sim.applyDamage(works.crew.get(0), 1000f, 1f);
        works.record.state = CompoundService.CompoundState.MARINE_HELD;

        long carrier = runUntilFlight(works, WorksCrewService.REPLACEMENT_SECONDS + 5f);
        assertTrue(carrier != 0L, "no lift ever went; the marines walked or sent nobody");
        assertEquals(0, technicians(works, Faction.MARINE),
                "somebody was already on the ground while their lift was still flying");

        long arrival = runUntilTechnician(works, Faction.MARINE, 0, 60f);
        assertTrue(arrival != 0L, "the lift never put anybody down");

        int landedY = works.sim.world().cellY(arrival);
        assertTrue(landedY < BAY_Y,
                "the relief landed at " + works.sim.world().cellX(arrival) + "," + landedY
                        + ", which is inside the shed rather than short of it");
        assertTrue(WORK_Y - landedY >= 10,
                "the relief landed " + (WORK_Y - landedY) + " cells from the work,"
                        + " which is close enough that there is no walk");
    }

    /**
     * The control: the same billet, the same map, held by the defender.
     *
     * <p>Without it the test above says only that somebody arrived somewhere
     * south of the shed, which is where the marines' end of the map is and where
     * a walk-on would start too. The defender's relief walks on at the far edge
     * and no lift goes at all, which is the difference the flight is supposed to
     * make.
     */
    @Test
    void theDefendersReliefWalksTheWholeWay() {
        Works works = works();
        works.sim.applyDamage(works.crew.get(0), 1000f, 1f);
        run(works, 1f);
        // The survivors, so "somebody arrived" is not satisfied by the crew that
        // was already standing there.
        int standing = technicians(works, Faction.DEFENDER);

        long arrival = runUntilTechnician(works, Faction.DEFENDER, standing,
                WorksCrewService.REPLACEMENT_SECONDS + 20f);
        assertTrue(arrival != 0L, "nobody was sent");
        assertEquals(0L, anyInboundFlight(works),
                "the defender's relief was flown in");
        // SOUTH_TO_NORTH puts the defender's rear at the top of the map.
        assertTrue(works.sim.world().cellY(arrival) > BAY_Y + BAY_H,
                "the defender's replacement did not walk on at their own edge");
    }

    /**
     * A relief shot down delivers nobody, and the billet reopens.
     *
     * <p>The whole reason the crossing is a flight rather than a teleport. It
     * also pins the reading that empties the seat: an aircraft is world-resident
     * and never appears in the roster walk that empties a billet whose
     * technician died, so a lift lost has to be noticed on its own terms or the
     * seat stays answered-for forever and the shed is never re-crewed.
     */
    @Test
    void aReliefShotDownDeliversNobodyAndTheSeatReopens() {
        Works works = works();
        works.sim.applyDamage(works.crew.get(0), 1000f, 1f);
        works.record.state = CompoundService.CompoundState.MARINE_HELD;

        long carrier = runUntilFlight(works, WorksCrewService.REPLACEMENT_SECONDS + 5f);
        assertTrue(carrier != 0L, "no lift went, so there was nothing to shoot down");
        // A craft still fading in off-map is not shootable yet, by the same rule
        // that keeps one on its ramp out of the target set.
        assertTrue(runUntilShootable(works, carrier, 30f),
                "the lift never became something anybody could shoot at");
        // Taken down the way anything takes an aircraft down: the same call the
        // damage pipeline makes once a craft is out of structure. Not
        // applyDamage, which is defined not to reach an airborne body — a
        // shooter that can reach up goes through the engagement relation, and
        // that relation is not what this test is about.
        works.sim.getRoster().airTargets().destroy(carrier);
        run(works, 1f);

        assertEquals(0L, anyInboundFlight(works),
                "the destroyed lift is still holding the seat it never filled");
        assertEquals(0, technicians(works, Faction.MARINE),
                "a technician got off an aircraft that was shot down");

        long second = runUntilFlight(works, WorksCrewService.REPLACEMENT_SECONDS + 5f);
        assertTrue(second != 0L, "the seat was never answered again");
        assertNotEquals(carrier, second, "the destroyed lift came back");
    }

    /** One shed, its watch, the compound over it, and a battle to tick them in. */
    private record Works(BattleSimulation sim, WorksCrewService crews,
                         CompoundService.Record record, List<Long> crew) { }

    private static Works works() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        CellTopology topology = new CellTopology(W, H);
        for (int dx = 0; dx < BAY_W; dx++) {
            for (int dy = 0; dy < BAY_H; dy++) {
                topology.setRoomPurpose(BAY_X + dx, BAY_Y + dy, RoomPurpose.VEHICLE_BAY);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid, topology);
        sim.setMissionCompletionEnabled(false);

        // The compound whose capture state says whose shed this is; its anchor
        // is inside the room, which is how a room finds its owner.
        TacticalNode node = new TacticalNode(TacticalNode.Kind.ARMORY,
                BAY_X + 2, BAY_Y + 2,
                BAY_X, BAY_Y + BAY_H, BAY_X + BAY_W, BAY_Y,
                Faction.DEFENDER, 70, 3);
        CompoundService.Record record = sim.getCompoundService().register(node);

        List<Gantry> berths = List.of(
                new Gantry(BAY_X + 4, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH),
                new Gantry(BAY_X + 12, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH));
        List<FixtureTask> authored = work(berths);
        List<RoomSite> rooms = RoomSite.findAll(topology, W, H);
        FabricationService fabrication = new FabricationService(berths, authored, rooms);
        sim.setFabrication(fabrication);

        WorksCrewService crews = new WorksCrewService(TraversalAxis.SOUTH_TO_NORTH);
        sim.setWorksCrews(crews);
        List<Long> crew = StructureWatch.man(sim, Faction.DEFENDER, rooms, authored,
                fabrication.berthed(), EnumSet.of(RoomPurpose.VEHICLE_BAY), 2, crews);
        assertFalse(crew.isEmpty(), "nobody was taken on");
        return new Works(sim, crews, record, crew);
    }

    private static List<FixtureTask> work(List<Gantry> berths) {
        List<FixtureTask> authored = new ArrayList<>();
        for (int berth = 0; berth < berths.size(); berth++) {
            Gantry gantry = berths.get(berth);
            authored.add(FixtureTask.servingBerth(gantry.left() - 1, gantry.centerY,
                    berth, gantry.centerX, gantry.centerY));
            authored.add(FixtureTask.servingBerth(gantry.right() + 1, gantry.centerY,
                    berth, gantry.centerX, gantry.centerY));
            authored.add(FixtureTask.at(gantry.centerX, gantry.top() + 2,
                    Affordance.STOW, gantry.centerX, gantry.top() + 3));
            authored.add(FixtureTask.at(gantry.centerX + 1, gantry.top() + 2,
                    Affordance.READOUT, gantry.centerX + 1, gantry.top() + 3));
        }
        return List.copyOf(authored);
    }

    /** The carrier on its way to any billet on this map, or 0 while none is. */
    private static long anyInboundFlight(Works works) {
        for (WorksCrewService.Posting posting : works.crews.postings()) {
            for (int billet = 0; billet < posting.billets(); billet++) {
                long carrier = posting.inbound(billet);
                if (carrier != 0L) return carrier;
            }
        }
        return 0L;
    }

    private static int technicians(Works works, Faction side) {
        int found = 0;
        for (int i = 0; i < works.sim.getRoster().liveCount(); i++) {
            long id = works.sim.getRoster().get(i);
            if (works.sim.identity().type(id) == UnitType.TECHNICIAN
                    && works.sim.identity().faction(id) == side) found++;
        }
        return found;
    }

    private static long newestTechnician(Works works, Faction side) {
        long newest = 0L;
        for (int i = 0; i < works.sim.getRoster().liveCount(); i++) {
            long id = works.sim.getRoster().get(i);
            if (works.sim.identity().type(id) != UnitType.TECHNICIAN) continue;
            if (works.sim.identity().faction(id) != side) continue;
            if (id > newest) newest = id;
        }
        return newest;
    }

    /** Tick until this craft is over the map and shootable. */
    private static boolean runUntilShootable(Works works, long carrier, float within) {
        int ticks = Math.max(1, Math.round(within / BattleSimulation.TICK_DT));
        for (int tick = 0; tick < ticks; tick++) {
            works.sim.advance(BattleSimulation.TICK_DT);
            if (works.sim.getRoster().airTargets().isPresent(carrier)) return true;
        }
        return false;
    }

    /** Tick until a lift is on its way, and answer with it the moment it is. */
    private static long runUntilFlight(Works works, float within) {
        int ticks = Math.max(1, Math.round(within / BattleSimulation.TICK_DT));
        for (int tick = 0; tick < ticks; tick++) {
            works.sim.advance(BattleSimulation.TICK_DT);
            long carrier = anyInboundFlight(works);
            if (carrier != 0L) return carrier;
        }
        return 0L;
    }

    /**
     * Tick until this side has more technicians on the ground than
     * {@code standing}, and answer with the newest of them.
     */
    private static long runUntilTechnician(Works works, Faction side, int standing,
                                           float within) {
        int ticks = Math.max(1, Math.round(within / BattleSimulation.TICK_DT));
        for (int tick = 0; tick < ticks; tick++) {
            works.sim.advance(BattleSimulation.TICK_DT);
            if (technicians(works, side) > standing) return newestTechnician(works, side);
        }
        return 0L;
    }

    private static void run(Works works, float seconds) {
        int ticks = Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
        for (int tick = 0; tick < ticks; tick++) {
            works.sim.advance(BattleSimulation.TICK_DT);
        }
    }
}
