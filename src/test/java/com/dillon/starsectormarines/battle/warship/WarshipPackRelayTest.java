package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.WarshipManoeuvre.Role;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack relay's pure pieces ({@link WarshipTactics}): how much of a threat a hull is to the one choosing, who holds
 * the target and who strikes, the swap when the tank runs high, and the venting ship holding its fire.
 */
class WarshipPackRelayTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    /** A Dormer Wolf at {@code (x, y)} facing {@code facing}, fighting hulls, its pool on. */
    private static Warship wolf(float x, float y, float facing) throws Exception {
        Warship w = Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, x, y, facing);
        w.shipFire(ShipFireOrder.ALL);
        w.limitFlux();
        return w;
    }

    @Test
    void aHullIsAThreatWhenItsGunsAreOnTheChooserFromInsideItsReach() throws Exception {
        // Facing 0 the nose is along +y. A Venture due north of the Wolf, inside its reach.
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        Warship on = wolf(0f, 0f, 0f);
        Warship away = wolf(0f, 0f, 180f);
        assertTrue(WarshipTactics.menace(on, venture) > 0.5f, "nose and guns on it: " + WarshipTactics.menace(on, venture));
        assertEquals(0f, WarshipTactics.menace(away, venture), 1e-4f, "turned away, nothing bears");
        Warship far = wolf(0f, 120f - 3f * WarshipShipFire.longestReach(on), 0f);
        assertEquals(0f, WarshipTactics.menace(far, venture), 1e-4f, "out of its own reach, no threat yet");
    }

    @Test
    void oneWolfHoldsTheTargetAndTheOtherStrikesThenTheySwapWhenTheTankRunsHigh() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.shipFire(ShipFireOrder.ALL);
        Warship a = wolf(-20f, 0f, 0f);
        Warship b = wolf(20f, 0f, 0f);
        List<Warship> ships = List.of(venture, a, b);
        WarshipTactics tactics = new WarshipTactics();
        tactics.plan(0f, ships, false);
        Role ra = a.manoeuvre().role();
        Role rb = b.manoeuvre().role();
        assertTrue((ra == Role.TANK && rb == Role.STRIKER) || (ra == Role.STRIKER && rb == Role.TANK), ra + "/" + rb);
        Warship tank = ra == Role.TANK ? a : b;
        Warship striker = tank == a ? b : a;

        // The tank's pool runs high after it has held the role its minimum time: the fresh striker takes over.
        tank.fluxPool().spend(0.7f * tank.fluxPool().capacity());
        tactics.plan(WarshipTactics.RELAY_MIN_SECONDS + 1f, ships, false);
        assertSame(Role.TANK, striker.manoeuvre().role(), "the fresh partner takes the target");
        assertSame(Role.STRIKER, tank.manoeuvre().role());
        assertTrue(tank.manoeuvre().relayVenting(), "the old tank breaks off to vent");
        assertEquals(1, striker.manoeuvre().relaySwaps());
        // Venting, it holds its fire however little a salvo costs.
        assertFalse(WarshipShipFire.mayStart(tank, venture, 1f));
    }

    /** A Hegemony Enforcer at {@code (x, y)} facing {@code facing}, fighting hulls, its pool on. */
    private static Warship enforcer(float x, float y, float facing) throws Exception {
        Warship e = Warship.fit("enforcer", "enforcer_Balanced", Faction.ALLY, x, y, facing);
        e.shipFire(ShipFireOrder.ALL);
        e.limitFlux();
        return e;
    }

    @Test
    void aDestroyerIsDecidedlySturdierThanAFrigateAndAWornOneNeedNotBe() throws Exception {
        Warship heavy = enforcer(0f, 0f, 0f);
        Warship light = wolf(20f, 0f, 0f);
        assertTrue(WarshipTactics.sturdierBy(heavy, light), WarshipTactics.sturdiness(heavy) + " against "
                + WarshipTactics.sturdiness(light));
        assertFalse(WarshipTactics.sturdierBy(light, heavy));
        assertSame(heavy, WarshipTactics.sturdiest(List.of(light, heavy)));
        assertEquals(null, WarshipTactics.sturdiest(List.of(light, wolf(-20f, 0f, 0f))), "two of a kind: none stands out");
        heavy.takeDamage(0.9f * heavy.hitPoints(), 1000f);
        assertFalse(WarshipTactics.sturdierBy(heavy, light), "worn down, it no longer outranks a fresh frigate");
    }

    @Test
    void inAMismatchedPairTheSturdierHullTanksAndTheLighterOneStrikes() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.shipFire(ShipFireOrder.ALL);
        Warship heavy = enforcer(-20f, 0f, 0f);
        Warship light = wolf(20f, 0f, 0f);
        // The target has the frigate in its sights, and the frigate is nearer: the destroyer holds it all the same.
        venture.hullTarget(light);
        List<Warship> ships = List.of(venture, light, heavy);
        WarshipTactics tactics = new WarshipTactics();
        tactics.plan(0f, ships, false);
        assertSame(Role.TANK, heavy.manoeuvre().role());
        assertSame(Role.STRIKER, light.manoeuvre().role());

        // Past the swap level the fresh frigate does not relieve it: a sturdy tank holds until its shield is going.
        heavy.fluxPool().spend(0.6f * heavy.fluxPool().capacity());
        tactics.plan(WarshipTactics.RELAY_MIN_SECONDS + 1f, ships, false);
        assertSame(Role.TANK, heavy.manoeuvre().role(), "a lighter partner does not take over at the swap level");
        heavy.fluxPool().spend(0.3f * heavy.fluxPool().capacity());
        tactics.plan(WarshipTactics.RELAY_MIN_SECONDS + 2f, ships, false);
        assertSame(Role.TANK, light.manoeuvre().role(), "with its shield going it hands over all the same");
        assertTrue(heavy.manoeuvre().relayVenting());
    }

    @Test
    void aSturdyPartnerTakesTheTargetBackFromALighterTankOnceFresh() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.shipFire(ShipFireOrder.ALL);
        Warship heavy = enforcer(-20f, 0f, 0f);
        Warship light = wolf(20f, 0f, 0f);
        List<Warship> ships = List.of(venture, light, heavy);
        WarshipTactics tactics = new WarshipTactics();
        tactics.plan(0f, ships, false);
        // The frigate holds the target, as it does while the destroyer vents, and its own pool is low.
        light.manoeuvre().role = Role.TANK;
        light.manoeuvre().relaySince = 0f;
        heavy.manoeuvre().role = Role.STRIKER;
        tactics.plan(1f, ships, false);
        assertSame(Role.TANK, light.manoeuvre().role(), "a role is held its minimum time");
        tactics.plan(WarshipTactics.RELAY_MIN_SECONDS + 1f, ships, false);
        assertSame(Role.TANK, heavy.manoeuvre().role(), "fresh again, the destroyer takes the target back");
        assertSame(Role.STRIKER, light.manoeuvre().role());
    }

    @Test
    void aHunterBetweenTwoHullsGoesForTheOneItsGunsCanHurt() throws Exception {
        // The Venture's Heavy Autocannons glance off a destroyer's plate and bite a frigate's.
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 0f, 0f);
        venture.shipFire(ShipFireOrder.ALL);
        venture.limitFlux();
        Warship heavy = enforcer(-10f, 110f, 180f);
        Warship light = wolf(10f, 125f, 180f);
        float onHeavy = WarshipTactics.bite(venture, heavy), onLight = WarshipTactics.bite(venture, light);
        assertTrue(onLight > onHeavy + 0.3f, "frigate " + onLight + " against destroyer " + onHeavy);
        WarshipTactics tactics = new WarshipTactics();
        tactics.plan(0f, List.of(venture, heavy, light), false);
        assertSame(light, venture.manoeuvre().target(), "the nearer destroyer is passed over for the frigate");
    }

    @Test
    void withTheRelayOffTheyShareTheHullAsBefore() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.shipFire(ShipFireOrder.ALL);
        Warship a = wolf(-20f, 0f, 0f);
        Warship b = wolf(20f, 0f, 0f);
        WarshipTactics tactics = new WarshipTactics();
        tactics.packRelay(false);
        tactics.plan(0f, List.of(venture, a, b), false);
        assertSame(Role.FREE, a.manoeuvre().role());
        assertSame(Role.FREE, b.manoeuvre().role());
    }

    @Test
    void aTanksCeilingCountsTheHardFluxItsShieldHoldsNotItsGuns() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.limitFlux();
        Warship tank = wolf(0f, 0f, 0f);
        tank.manoeuvre().role = Role.TANK;
        float cap = tank.fluxPool().capacity();
        assertTrue(WarshipShipFire.mayStart(tank, venture, 0.1f * cap), "plenty of headroom");
        // Its own guns' flux is soft and vents behind the shield: it does not stop the tank firing.
        tank.fluxPool().spend(0.45f * cap);
        assertTrue(WarshipShipFire.mayStart(tank, venture, 0.1f * cap), "soft flux leaves the shield its headroom");
        // What its shield has absorbed is hard: past half the pool the tank holds its fire.
        tank.fluxPool().spendHard(0.5f * cap);
        assertFalse(WarshipShipFire.mayStart(tank, venture, 0.01f * cap), "past its share of hard flux it holds fire");
    }

    @Test
    void aVentingShipTurnsAlongAnEdgeRatherThanIntoIt() throws Exception {
        WarshipEdgeHelm edges = new WarshipEdgeHelm(460f, 232f);
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 230f, 100f, 0f);
        Warship ship = wolf(230f, 70f, 0f);
        float out = WarshipShipFire.longestReach(venture) * WarshipTactics.VENT_HOLD_SHARE;
        // Straight away from the target is due south, into the south edge's margin long before the hold.
        float away = WarshipFacing.bearing(venture.centerX(), venture.centerY(), ship.centerX(), ship.centerY());
        float straight = WarshipTactics.ventRoom(ship, venture, away, out, edges);
        assertTrue(straight < WarshipTactics.VENT_ROOM_SHARE * out, "the edge blocks the straight course: " + straight);
        float course = WarshipTactics.ventCourse(ship, venture, away, out, edges);
        float room = WarshipTactics.ventRoom(ship, venture, course, out, edges);
        assertTrue(room > straight, "the course it takes has more room: " + room + " against " + straight);
        assertTrue(Math.abs(WarshipFacing.wrap(course - away)) <= WarshipTactics.VENT_COURSE_SWING_DEGREES + 1e-3f);
        // With the map open, it runs straight away.
        WarshipEdgeHelm open = new WarshipEdgeHelm(4000f, 4000f);
        Warship farTarget = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 2000f, 2000f, 0f);
        Warship farShip = wolf(2000f, 1970f, 0f);
        float farAway = WarshipFacing.bearing(2000f, 2000f, farShip.centerX(), farShip.centerY());
        assertEquals(0f, WarshipFacing.wrap(WarshipTactics.ventCourse(farShip, farTarget, farAway, out, open) - farAway),
                1e-3f);
    }
}
