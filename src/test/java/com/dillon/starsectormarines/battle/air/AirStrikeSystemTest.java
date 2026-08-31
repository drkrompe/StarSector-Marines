package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a field decides to fly.
 *
 * <p>Everything under this — the lot, the strip, the berths, the ground
 * procedure — worked without anything ever asking for a sortie, so a station's
 * fighters sat in their hangars for whole battles. These pin the asking.
 */
class AirStrikeSystemTest {

    private static final int W = 70;
    private static final int H = 60;
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);

    private static BattleSimulation openSim(boolean withStrip) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        if (withStrip) sim.getAirfieldService().installRunway(STRIP);
        return sim;
    }

    private static AirfieldService.Berth shed(BattleSimulation sim, int x, int y) {
        return sim.getAirfieldService().addShelterBerth(
                new Gantry(x, y, 2, 2, Gantry.Facing.SOUTH), FighterProfile.BROADSWORD);
    }

    /**
     * A field with people on it, as far as these tests care.
     *
     * <p>A turnaround is hand-work now: an unmanned field flies its aircraft
     * once and never sees them again, which is the point of the law and is a
     * different subject from dispatch. These tests are about which berths a
     * field commits and how many at a time, so the field is simply declared to
     * be staffed rather than given a crew, an apron and a hangar to stand them
     * in.
     */
    private static void manned(BattleSimulation sim) {
        for (AirfieldService.Berth berth : sim.getAirfieldService().berths()) {
            berth.refitWork = 0f;
        }
    }

    /** A concentration worth attacking: a squad's worth of marines together. */
    private static void massMarines(BattleSimulation sim, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    x + (i % 3), y + (i / 3)));
        }
    }

    private static ShuttleMission strikeOut(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = sim.world().mission(id);
            if (m != null && m.strikeSortie) return m;
        }
        return null;
    }

    private static void advance(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) {
            manned(sim);
            sim.advance(BattleSimulation.TICK_DT);
        }
    }

    /** A massed enemy on a field's doorstep gets an aircraft sent at it. */
    @Test
    void aFieldFliesAStrikeAtAMassedEnemy() {
        BattleSimulation sim = openSim(true);
        AirfieldService.Berth shed = shed(sim, 25, 20);
        massMarines(sim, 45, 30, 6);

        // Past the opening interval.
        advance(sim, 60 * 30);

        ShuttleMission strike = strikeOut(sim);
        assertNotNull(strike, "the field never flew anything");
        assertTrue(strike.usesRunway, "a strike that did not use the strip");
        assertEquals(shed, strike.homeBerth, "the sortie does not know its own shed");
        assertEquals(AirfieldService.BerthState.AWAY, shed.state,
                "flew and the shed still says it has the aircraft");
        assertTrue(strike.lzX > 40f, "sent somewhere other than the concentration");
    }

    /**
     * Several at once, not one — but not everything the field has.
     *
     * <p>The complaint this answers was a playtester's: a station with five
     * airframes putting exactly one over the battle. Five sheds commit three,
     * which is half of them rounded up, and the other two are always either in
     * the shop or standing ready for the push the field exists to answer.
     *
     * <p>Watched across the whole window rather than sampled at the end: a
     * sortie completes and goes home, so the instant a test happens to look
     * says nothing about whether three were ever up together.
     */
    @Test
    void aFieldWithFiveShedsFliesThreeAtOnce() {
        BattleSimulation sim = openSim(true);
        for (int i = 0; i < 5; i++) shed(sim, 20 + i * 4, 20);
        aBattlesWorthOfEnemy(sim);

        Watch watch = watch(sim, 180);

        assertEquals(3, watch.mostAirborne,
                "half of five sheds, rounded up, is what the field should commit");
        assertEquals(3, watch.mostBerthsAway,
                "berths committed disagrees with aircraft in the air");
        assertTrue(watch.launches >= 5,
                "a field of five flew only " + watch.launches + " sorties in three minutes");
    }

    /**
     * A field of one commits its one. Half of a single shed rounded down is
     * nothing, and a garrison that held its only aircraft in reserve forever
     * would be an airfield that never flies.
     */
    @Test
    void aFieldWithOneShedStillFliesIt() {
        BattleSimulation sim = openSim(true);
        shed(sim, 25, 20);
        aBattlesWorthOfEnemy(sim);

        Watch watch = watch(sim, 180);

        assertEquals(1, watch.mostAirborne, "a one-shed field flies exactly one at a time");
        assertTrue(watch.launches >= 2, "the one aircraft flew once and stopped");
    }

    /**
     * Every sortie came out of a berth it actually took, and no two took the
     * same one.
     *
     * <p>The failure this guards against is not hypothetical. A dispatch path
     * that could not find a berth once fell back to the nearest bare landing
     * pad, so aircraft were conjured onto ground nobody had assigned them,
     * several onto the same pad, and deleted on arrival. Concurrency is exactly
     * the condition that made that visible, so it is exactly the condition to
     * check under.
     */
    @Test
    void noTwoSortiesShareABerthAndNoneIsConjured() {
        BattleSimulation sim = openSim(true);
        for (int i = 0; i < 5; i++) shed(sim, 20 + i * 4, 20);
        aBattlesWorthOfEnemy(sim);

        Watch watch = watch(sim, 180);

        assertEquals(0, watch.berthlessSorties,
                "a strike flew from this field without holding a berth");
        assertEquals(0, watch.sharedBerthTicks,
                "two sorties were flying the same shed's aircraft");
        assertEquals(0, watch.berthNotAwayTicks,
                "a sortie was up while its shed still claimed to have the aircraft");
        assertTrue(watch.launches >= 5, "nothing flew, so nothing was checked");
    }

    /**
     * One strip, one aircraft on it — and the queue behind it drains.
     *
     * <p>Single occupancy is the interesting half only because several
     * aircraft now want the strip at once. The other half is that waiting for
     * it does not deadlock: an aircraft that held short got its turn, and the
     * field kept launching.
     */
    @Test
    void oneAircraftHasTheStripAtATime() {
        BattleSimulation sim = openSim(true);
        for (int i = 0; i < 5; i++) shed(sim, 20 + i * 4, 20);
        aBattlesWorthOfEnemy(sim);

        Watch watch = watch(sim, 180);

        assertEquals(1, watch.mostOnTheStrip, "two aircraft were on the runway together");
        assertEquals(0, watch.rollingWithoutTheStripTicks,
                "an aircraft rolled without holding the strip");
        assertTrue(watch.heldShort, "nobody ever had to wait, so the queue was never exercised");
        assertTrue(watch.tookOff >= 5,
                "only " + watch.tookOff + " aircraft got off the strip — the queue stalled");
    }

    /**
     * Two aircraft up, two concentrations on the map, one each.
     *
     * <p>Sending both at the same platoon is a legitimate tactic and stays
     * available when the map holds nothing else; sending both there because the
     * dispatcher cannot express anything else is a bug.
     */
    @Test
    void concurrentSortiesWorkSeparateConcentrations() {
        BattleSimulation sim = openSim(true);
        // Three sheds commits two, which is the smallest field that can ask
        // the question at all.
        shed(sim, 25, 20);
        shed(sim, 29, 20);
        shed(sim, 33, 20);
        massMarines(sim, 50, 30, 12);
        massMarines(sim, 4, 30, 12);

        float separation = -1f;
        for (int t = 0; t < 180 * 30 && separation < 0f; t++) {
            sim.advance(BattleSimulation.TICK_DT);
            List<ShuttleMission> out = strikesOut(sim);
            if (out.size() < 2) continue;
            float dx = out.get(0).lzX - out.get(1).lzX;
            float dy = out.get(0).lzY - out.get(1).lzY;
            separation = (float) Math.sqrt(dx * dx + dy * dy);
        }

        assertTrue(separation >= EnemyConcentration.SEPARATE_TARGET_DIST,
                "two aircraft were sent at the same concentration with another on the map"
                        + " (they were " + separation + " cells apart)");
    }

    /** Scattered enemies are not worth a sortie. */
    @Test
    void aScatteredEnemyIsNotWorthASortie() {
        BattleSimulation sim = openSim(true);
        shed(sim, 25, 20);
        // Far enough apart that nobody has friends within the cluster radius.
        for (int i = 0; i < 6; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE,
                    4 + i * 9, 30));
        }

        advance(sim, 120 * 30);

        assertNull(strikeOut(sim), "flew a sortie at nothing in particular");
    }

    /**
     * A field with no strip flies nothing, however many aircraft are in its
     * sheds — they cannot get off the ground.
     */
    @Test
    void aFieldWithNoStripFliesNothing() {
        BattleSimulation sim = openSim(false);
        shed(sim, 25, 20);
        massMarines(sim, 45, 30, 6);

        advance(sim, 120 * 30);

        assertNull(strikeOut(sim), "flew off a field with no runway");
    }

    /** A field whose sheds are empty flies nothing either. */
    @Test
    void aFieldWithNothingAirworthyFliesNothing() {
        BattleSimulation sim = openSim(true);
        AirfieldService.Berth shed = shed(sim, 25, 20);
        sim.getAirfieldService().destroyed(shed);
        massMarines(sim, 45, 30, 6);

        advance(sim, 120 * 30);

        assertNull(strikeOut(sim), "flew an aircraft that had been destroyed");
    }

    /**
     * Enough of an enemy to keep a field busy for three minutes.
     *
     * <p>Four separate concentrations rather than one big one. A strike is
     * genuinely lethal against massed infantry, so a single huddle is gone
     * inside two sorties and everything measured after that is a field with
     * nothing to attack — which reads exactly like a field that has stopped
     * flying.
     *
     * <p>And all of it well down the map, away from the strip. An aircraft on
     * its wheels is shootable, so a concentration placed beside the runway is
     * not an enemy for the field to attack — it is a fire team astride the
     * taxiway, and it burns the whole establishment down inside two minutes
     * without a shot being fired at it. That is a real behaviour and a
     * different question; it belongs to the runway scene, not here.
     */
    private static void aBattlesWorthOfEnemy(BattleSimulation sim) {
        for (int i = 0; i < 4; i++) massMarines(sim, 4 + i * 18, 48, 16);
    }

    /** Every strike this field has in the air right now. */
    private static List<ShuttleMission> strikesOut(BattleSimulation sim) {
        List<ShuttleMission> out = new ArrayList<>();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = sim.world().mission(id);
            if (m != null && m.strikeSortie) out.add(m);
        }
        return out;
    }

    /**
     * What a battle's worth of ticks saw.
     *
     * <p>Peaks and counts rather than a sample, because every question here is
     * about what was ever true and not about what happens to be true when the
     * clock stops.
     */
    private static final class Watch {
        int mostAirborne;
        int mostBerthsAway;
        int mostOnTheStrip;
        int launches;
        int tookOff;
        int berthlessSorties;
        int sharedBerthTicks;
        int berthNotAwayTicks;
        int rollingWithoutTheStripTicks;
        boolean heldShort;
    }

    /** Plays {@code seconds} of battle and reports everything it saw. */
    private static Watch watch(BattleSimulation sim, int seconds) {
        Watch watch = new Watch();
        AirfieldService field = sim.getAirfieldService();
        Set<Long> seenCraft = new HashSet<>();
        Set<Long> rolled = new HashSet<>();
        Set<AirfieldService.Berth> berthsThisTick = new HashSet<>();
        for (int t = 0; t < seconds * 30; t++) {
            manned(sim);
            sim.advance(BattleSimulation.TICK_DT);
            berthsThisTick.clear();
            int airborne = 0;
            int onStrip = 0;
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission m = sim.world().mission(id);
                if (m == null || !m.strikeSortie) continue;
                airborne++;
                if (seenCraft.add(id)) watch.launches++;
                if (m.homeBerth == null) {
                    watch.berthlessSorties++;
                } else {
                    if (!berthsThisTick.add(m.homeBerth)) watch.sharedBerthTicks++;
                    if (m.homeBerth.state != AirfieldService.BerthState.AWAY) {
                        watch.berthNotAwayTicks++;
                    }
                }
                if (m.state == ShuttleState.HOLDING_SHORT) watch.heldShort = true;
                if (m.state == ShuttleState.TAKEOFF_ROLL || m.state == ShuttleState.LANDING_ROLL) {
                    onStrip++;
                    if (field.runwayOccupant() != id) watch.rollingWithoutTheStripTicks++;
                }
                if (m.state == ShuttleState.TAKEOFF_ROLL && rolled.add(id)) watch.tookOff++;
            }
            int away = 0;
            for (AirfieldService.Berth berth : field.berths()) {
                if (berth.state == AirfieldService.BerthState.AWAY) away++;
            }
            watch.mostAirborne = Math.max(watch.mostAirborne, airborne);
            watch.mostBerthsAway = Math.max(watch.mostBerthsAway, away);
            watch.mostOnTheStrip = Math.max(watch.mostOnTheStrip, onStrip);
        }
        return watch;
    }
}
