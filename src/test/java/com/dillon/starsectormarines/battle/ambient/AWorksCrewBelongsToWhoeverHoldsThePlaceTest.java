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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A billet a technician died in is filled again, by whoever holds the building.
 *
 * <p>Without this a manning pass is a one-off: kill the crew and the shed is
 * finished for the battle whoever ends up standing in it, which makes a facility
 * a prize nobody who takes it can use and makes clearing one a permanent result
 * for the price of a fire team. With it, killing a crew buys the minutes it
 * takes somebody to walk across the map — and taking the building means the next
 * technicians in it are yours.
 *
 * <p>That second half is the one worth guarding, because it is invisible until
 * a compound actually changes hands. A repopulation that always sent the
 * original side's people would look completely correct for the whole of an
 * ordinary battle and would quietly give a captured motor pool back to the enemy
 * who lost it.
 */
class AWorksCrewBelongsToWhoeverHoldsThePlaceTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final int BAY_X = 20;
    private static final int BAY_Y = 14;
    private static final int BAY_W = 20;
    private static final int BAY_H = 12;

    /** Nobody is sent while the watch is full. */
    @Test
    void afullWatchIsSentNobody() {
        Works works = works();
        int hired = works.crew.size();

        run(works, WorksCrewService.REPLACEMENT_SECONDS * 2f);

        assertEquals(hired, technicians(works, Faction.DEFENDER),
                "the shed took on more people without anybody having been lost");
    }

    /**
     * A billet stands empty for the whole cooldown and is then filled.
     *
     * <p>Both ends. Refilled promptly, killing a crew is a tax rather than a
     * result; never refilled, a facility is switched off permanently by one fire
     * team and whoever takes it inherits a building that does nothing.
     */
    @Test
    void anEmptyBilletIsFilledAfterTheCooldownAndNotBefore() {
        Works works = works();
        long lost = works.crew.get(0);
        works.sim.applyDamage(lost, 1000f, 1f);
        run(works, 1f);
        int short_ = technicians(works, Faction.DEFENDER);
        assertEquals(works.crew.size() - 1, short_, "the test did not kill anybody");

        run(works, WorksCrewService.REPLACEMENT_SECONDS * 0.5f);
        assertEquals(short_, technicians(works, Faction.DEFENDER),
                "somebody was sent before the shed had been short-handed for long");

        run(works, WorksCrewService.REPLACEMENT_SECONDS);
        assertEquals(works.crew.size(), technicians(works, Faction.DEFENDER),
                "the billet was never filled again");
    }

    /**
     * A replacement walks on at their own side's edge, not at the building.
     *
     * <p>The whole cost of the thing. Somebody who appeared at the bench would
     * make a crew unkillable in effect; somebody who has to cross the map is a
     * body in the open for a minute, and the shed is not working while they do
     * it.
     */
    @Test
    void aReplacementWalksOnAtTheirOwnEdge() {
        Works works = works();
        works.sim.applyDamage(works.crew.get(0), 1000f, 1f);

        run(works, WorksCrewService.REPLACEMENT_SECONDS + 1f);

        long arrival = newestTechnician(works, Faction.DEFENDER);
        assertTrue(arrival != 0L, "nobody was sent");
        // SOUTH_TO_NORTH puts the defender's rear at the top of the map.
        assertTrue(works.sim.world().cellY(arrival) > BAY_Y + BAY_H,
                "the replacement started at " + works.sim.world().cellX(arrival) + ","
                        + works.sim.world().cellY(arrival) + ", which is not off the"
                        + " defender's own edge");
    }

    /**
     * A shed taken from the defender turns out marine technicians.
     *
     * <p>The point of the whole feature, and the half that a battle where
     * nothing changes hands can never show.
     *
     * <p>Run past the cooldown by rather more than a tick, because a marine
     * relief flies the long part of the journey and is still in the air when the
     * cooldown expires. How it travels is
     * {@code AMarineReliefFliesInAndWalksTheRestTest}'s question; this one is
     * only about whose it is.
     */
    @Test
    void aCapturedShedIsCrewedByWhoeverTookIt() {
        Works works = works();
        works.sim.applyDamage(works.crew.get(0), 1000f, 1f);
        works.record.state = CompoundService.CompoundState.MARINE_HELD;

        run(works, WorksCrewService.REPLACEMENT_SECONDS + 20f);

        assertEquals(1, technicians(works, Faction.MARINE),
                "the marines hold the shed and it is still turning out defenders");
        long arrival = newestTechnician(works, Faction.MARINE);
        assertNotNull(works.sim.squadOf(arrival),
                "the new crew is in no squad, so they will never shoot at anything");
    }

    /**
     * A worked room that no compound stands over is never refilled.
     *
     * <p>A hangar in a city block is not a garrison's facility, and handing it
     * to whichever compound happened to be nearest would give a side a building
     * a hundred cells away that it has no claim on at all.
     */
    @Test
    void aRoomNobodyHoldsIsNeverRefilled() {
        Works works = works(false);
        long lost = works.crew.get(0);
        works.sim.applyDamage(lost, 1000f, 1f);
        int short_ = technicians(works, Faction.DEFENDER);

        run(works, WorksCrewService.REPLACEMENT_SECONDS * 3f);

        assertEquals(short_, technicians(works, Faction.DEFENDER),
                "a room in nobody's hands was crewed anyway");
        for (WorksCrewService.Posting posting : works.crews.postings()) {
            assertEquals(null, posting.owner, "the room resolved an owner it does not have");
        }
    }

    /** One shed, its watch, the compound over it, and a battle to tick them in. */
    private record Works(BattleSimulation sim, WorksCrewService crews,
                         CompoundService.Record record, List<Long> crew) { }

    private static Works works() {
        return works(true);
    }

    private static Works works(boolean held) {
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

        CompoundService.Record record = null;
        if (held) {
            // The compound whose capture state says whose shed this is. Its
            // anchor is inside the room, which is how a room finds its owner.
            TacticalNode node = new TacticalNode(TacticalNode.Kind.ARMORY,
                    BAY_X + 2, BAY_Y + 2,
                    BAY_X, BAY_Y + BAY_H, BAY_X + BAY_W, BAY_Y,
                    Faction.DEFENDER, 70, 3);
            record = sim.getCompoundService().register(node);
        }

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

    private static int technicians(Works works, Faction side) {
        int found = 0;
        for (int i = 0; i < works.sim.getRoster().liveCount(); i++) {
            long id = works.sim.getRoster().get(i);
            if (works.sim.identity().type(id) == UnitType.TECHNICIAN
                    && works.sim.identity().faction(id) == side) found++;
        }
        return found;
    }

    /** The latest-minted technician on a side, which is the one just sent. */
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

    private static void run(Works works, float seconds) {
        int ticks = Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
        for (int tick = 0; tick < ticks; tick++) {
            works.sim.advance(BattleSimulation.TICK_DT);
        }
    }
}
