package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens when a parked airframe is destroyed where it stands.
 *
 * <p>A flat, open 40x40 grid: the question is about the blast, so nothing
 * should be in the way of it but distance.
 */
class AirframeCookOffSystemTest {

    private static final int W = 40;
    private static final int H = 40;
    private static final int PAD_X = 20;
    private static final int PAD_Y = 20;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Whether a unit is still on the live roster. */
    private static boolean onMap(BattleSimulation sim, long unit) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (sim.liveUnitAt(i) == unit) return true;
        }
        return false;
    }

    /** An aircraft standing on a hardstand, placed the way a field places one. */
    private static long parkedAircraft(BattleSimulation sim, int x, int y) {
        return parkedAircraft(sim, x, y, ShuttleType.AEROSHUTTLE);
    }

    private static long parkedAircraft(BattleSimulation sim, int x, int y, Airframe airframe) {
        sim.getAirfieldService().addBerth(
                LandingPad.garrison(x, y, LandingPad.Approach.SOUTH), airframe, 0f);
        new AirfieldSystem()
                .tick(BattleSimulation.TICK_DT, sim, sim.getAirfieldService());
        List<AirfieldService.Berth> berths = sim.getAirfieldService().berths();
        return berths.get(berths.size() - 1).airframeId;
    }

    private static long marine(BattleSimulation sim, String id, int x, int y) {
        return sim.spawn(new EntitySpec(id, Faction.MARINE, UnitType.MARINE, x, y));
    }

    /** The people who walked onto the apron to burn it are standing next to a full tank. */
    @Test
    void theBlastCatchesWhoeverIsBesideTheStand() {
        BattleSimulation sim = openSim();
        long airframe = parkedAircraft(sim, PAD_X, PAD_Y);
        long beside = marine(sim, "beside", PAD_X + 2, PAD_Y);
        long acrossTheField = marine(sim, "across", PAD_X + 12, PAD_Y);

        sim.applyDamage(airframe, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(onMap(sim, beside),
                "two cells from a burning aircraft is inside the fire");
        assertTrue(onMap(sim, acrossTheField),
                "twelve cells away is not");
    }

    /**
     * Exactly how far the fire reaches a person, which is the one number in
     * here tuned by watching it: burning three aircraft from four cells away
     * cost about half a six-man fire team, so riflemen out-range the apron
     * comfortably and the price is for standing on it.
     *
     * <p>Pinned at the boundary rather than loosely, because the cook-off's
     * reach against infantry is what any future move of the blast radius has
     * to leave alone, and a test that only looks at two cells and twelve would
     * not notice.
     */
    @Test
    void theFireReachesARiflemanFourCellsOutAndNoFurther() {
        BattleSimulation sim = openSim();
        long airframe = parkedAircraft(sim, PAD_X, PAD_Y);
        long onTheApron = marine(sim, "apron", PAD_X + 4, PAD_Y);
        long clearOfIt = marine(sim, "clear", PAD_X + 5, PAD_Y);

        sim.applyDamage(airframe, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(onMap(sim, onTheApron), "four cells out is on the apron and in the fire");
        assertTrue(onMap(sim, clearOfIt), "five cells out is clear of it");
    }

    /** The explosion is an event both presentation hosts already draw and play. */
    @Test
    void theExplosionIsSeenAndHeardWhereTheAircraftStood() {
        BattleSimulation sim = openSim();
        long airframe = parkedAircraft(sim, PAD_X, PAD_Y);

        sim.applyDamage(airframe, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        List<float[]> impacts = sim.getHeavyImpactsThisFrame();
        assertEquals(1, impacts.size(), "one heavy impact, where it stood");
        assertEquals(PAD_X + 0.5f, impacts.get(0)[0], 0.6f);
        assertEquals(PAD_Y + 0.5f, impacts.get(0)[1], 0.6f);
        assertTrue(impacts.get(0)[2] > 0f, "with a radius the FX can size itself to");
    }

    /**
     * The fire is sized to the stand and the apron round it, so a person
     * standing at the next berth is out of it.
     *
     * <p>The aircraft over there is spared by the exclusion below and not by
     * this arithmetic; everything else on the apron is spared by exactly this.
     * Widening the blast or narrowing the lot is therefore a decision about
     * both, and this is what says so.
     */
    @Test
    void theFireIsSizedToTheStandAndItsApron() {
        float reachToARifleman =
                AirframeCookOffSystem.BLAST_RADIUS_CELLS + UnitType.MARINE.radius;
        assertTrue(reachToARifleman < AirbaseLot.BERTH_PITCH,
                "a cook-off reaches " + reachToARifleman + " cells and the next berth is "
                        + AirbaseLot.BERTH_PITCH + " away");
    }

    /**
     * One fire does not take the whole field, for any aircraft the game ships.
     *
     * <p>Asked of every airframe rather than of one, because the area sweep
     * catches a body at blast radius <em>plus that body's own radius</em>, so
     * how much of the aircraft on the next stand there is decides whether the
     * fire reaches it. The version of this test that only ever parked an
     * Aeroshuttle was asking about the smallest hull on the list.
     */
    @Test
    void noShippedAirframeTakesTheNextStandWithIt() {
        for (Airframe frame : everyShippedAirframe()) {
            BattleSimulation sim = openSim();
            long first = parkedAircraft(sim, PAD_X, PAD_Y, frame);
            long neighbour = parkedAircraft(sim, PAD_X + AirbaseLot.BERTH_PITCH, PAD_Y, frame);

            sim.applyDamage(first, 100_000f, 100_000f);
            sim.advance(BattleSimulation.TICK_DT);

            assertTrue(onMap(sim, neighbour),
                    frame + " on the next stand went up with its neighbour");
            assertEquals(frame.maxHp(), sim.world().hp(neighbour), 0.01f,
                    frame + " on the next stand was scorched by its neighbour");
        }
    }

    /**
     * And for any aircraft it might ship later.
     *
     * <p>The shipped list happens to fit, mostly with room to spare, and that
     * is worth nothing: the largest transport already reached across the gap
     * once a parked hull started reporting its real drawn size, and the next
     * hull anybody bases is a number nobody can enumerate at build time. So
     * the law is asked of an aircraft as wide as the gap itself, where no
     * arithmetic can save it.
     */
    @Test
    void noAirframeTakesTheNextStandWithItHoweverLargeItIs() {
        Airframe wider = new DrawnToSize(ShuttleType.VALKYRIE, AirbaseLot.BERTH_PITCH);
        BattleSimulation sim = openSim();
        long first = parkedAircraft(sim, PAD_X, PAD_Y, wider);
        long neighbour = parkedAircraft(sim, PAD_X + AirbaseLot.BERTH_PITCH, PAD_Y, wider);

        sim.applyDamage(first, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(onMap(sim, neighbour),
                "an aircraft too big to miss is still not a chain reaction");
        assertEquals(wider.maxHp(), sim.world().hp(neighbour), 0.01f,
                "and is not even scorched");
    }

    /** Everything else that dies on the field dies quietly. */
    @Test
    void anOrdinaryDeathDoesNotGoUp() {
        BattleSimulation sim = openSim();
        long standing = marine(sim, "standing", PAD_X, PAD_Y);
        long beside = marine(sim, "beside", PAD_X + 2, PAD_Y);

        sim.applyDamage(standing, 100_000f, 100_000f);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.getHeavyImpactsThisFrame().isEmpty(),
                "a rifleman is not a fuel tank");
        assertTrue(onMap(sim, beside));
    }

    /**
     * Every airframe the game ships, each reporting the size it really is.
     *
     * <p>Asserted rather than assumed. {@link Airframe#targetRadiusCells} goes
     * through {@code HullFootprintResolver}, which falls back to one flat
     * length for any hull it cannot read — so an install this run cannot see
     * would hand back sixteen identical aircraft, and every assertion below
     * would pass while measuring none of them.
     */
    private static List<Airframe> everyShippedAirframe() {
        List<Airframe> all = new ArrayList<>();
        for (ShuttleType type : ShuttleType.values()) all.add(type);
        for (FighterProfile fighter : FighterProfile.values()) all.add(fighter);
        for (Airframe frame : all) {
            assertTrue(HullFootprintResolver.isMeasured(frame.renderHullId()),
                    frame + " is standing in at a fallback size rather than its own");
        }
        return all;
    }

    /**
     * A shipped airframe standing at a chosen size — everything else about it
     * is the real thing.
     */
    private record DrawnToSize(Airframe hull, float radiusCells) implements Airframe {
        @Override public String spritePath()        { return hull.spritePath(); }
        @Override public String renderHullId()      { return hull.renderHullId(); }
        @Override public AirOrdnance ordnance()     { return hull.ordnance(); }
        @Override public int hardpoints()           { return hull.hardpoints(); }
        @Override public AirHandling flight()       { return hull.flight(); }
        @Override public float maxHp()              { return hull.maxHp(); }
        @Override public float targetRadiusCells()  { return radiusCells; }
        @Override public String toString()          { return hull.toString(); }
    }
}
