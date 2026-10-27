package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.grenade.FragGrenadeService;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryUnitPrep;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.BreacherAssistSpec;
import com.dillon.starsectormarines.marine.ExposedUnderFireSpec;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.IntegralSystemEffect;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.SpecialResourceMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Marine's right button with a hand grenade on it: its list, the committed throw and the pick's lifetime.
 * The release itself is the battle's {@code throwFragmentationGrenade}, recorded here, spending one grenade and
 * dropping the reservation as the real one does; the flight, burst and friendly fire are that path's own.
 */
class DirectControlGrenadeTest {
    private static final SpecialEquipmentDef FRAG = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID);
    private static final IntegralSystemDef SHIELD = new IntegralSystemDef(
            "system.test-screen", "Screen", EquipmentGrade.SERVICE, "",
            IntegralSystemEffect.BREACHER_ASSIST, SpecialResourceMode.COOLDOWN,
            3f, 22f, 0, new BreacherAssistSpec(1.45f, 20f, 120f),
            null, new ExposedUnderFireSpec(2f, 1f));

    private final NavigationGrid grid = new NavigationGrid(30, 12);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(30, 12), null);
    private final FragGrenadeService grenades = new FragGrenadeService();
    private final SmokeFieldService smokeFields = new SmokeFieldService(grid);
    private final List<float[]> releases = new ArrayList<>();
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "world" -> roster.world();
                case "combat" -> roster.combat();
                case "movement" -> roster.movement();
                case "squad" -> roster.squad();
                case "identity" -> roster.identity();
                case "fragGrenades" -> grenades;
                case "smokeFields" -> smokeFields;
                case "physicalRadius" -> UnitType.MARINE.radius;
                case "squadOf", "getShelvedSquadDirective" -> null;
                case "resolveUnit" -> roster.isAliveById((long) args[0]) ? args[0] : 0L;
                case "clearPath" -> {
                    roster.movement().setPathRef((long) args[0], new int[0]);
                    roster.movement().setPathIdx((long) args[0], 0);
                    yield null;
                }
                case "throwFragmentationGrenade" -> {
                    long carrier = (long) args[0];
                    releases.add(new float[]{(float) args[1], (float) args[2]});
                    roster.world().setSecondaryAmmo(carrier, roster.world().secondaryAmmo(carrier) - 1);
                    grenades.release(carrier);
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster, id -> false, () -> false);

    DirectControlGrenadeTest() {
        for (int y = 0; y < 12; y++) for (int x = 0; x < 30; x++) grid.setWalkableFloor(x, y);
    }

    @Test
    void theListIsSmokeThenAGrenadeThenTheShieldAndHoldsOnlyWhatIsCarried() {
        var smoke = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        var incendiary = SpecialEquipmentRegistry.require("special.incendiary-grenade");
        assertEquals(List.of(MarineRightTrigger.Item.SMOKE, MarineRightTrigger.Item.SHIELD),
                MarineRightTrigger.members(smoke, SHIELD));
        assertEquals(List.of(MarineRightTrigger.Item.GRENADE, MarineRightTrigger.Item.SHIELD),
                MarineRightTrigger.members(FRAG, SHIELD));
        assertEquals(List.of(MarineRightTrigger.Item.GRENADE), MarineRightTrigger.members(incendiary, null));
        assertEquals(List.of(), MarineRightTrigger.members(
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FLAMER_ID), null),
                "the flamer sprays a direct jet; it is not a throw");
        assertEquals(List.of(), MarineRightTrigger.members(null, null));
    }

    @Test
    void eCyclesTheRightButtonAndThePressUsesThePick() {
        long marine = spawn(FRAG, SHIELD);
        assertTrue(session.enter(marine));
        assertEquals(MarineRightTrigger.Item.GRENADE, session.marineRightPick(), "the first item is the default");
        int before = session.selectionKey();
        assertTrue(session.cycle(FireTrigger.RIGHT));
        assertEquals(MarineRightTrigger.Item.SHIELD, session.marineRightPick());
        assertNotEquals(before, session.selectionKey(), "the host releases held fire on a new pick");
        press(12, 5.5f);
        assertTrue(roster.mitigations().isActive(marine), "the shield pick raises the shield");
        assertNull(grenades.reservationFor(marine));
        assertTrue(session.cycle(FireTrigger.RIGHT));
        assertEquals(MarineRightTrigger.Item.GRENADE, session.marineRightPick(), "the list wraps");
        assertTrue(session.pick(FireTrigger.RIGHT, 1));
        assertEquals(MarineRightTrigger.Item.SHIELD, session.marineRightPick(), "a row click picks by index");
        assertFalse(session.pick(FireTrigger.RIGHT, 2));
    }

    @Test
    void theThrowBlocksMoveAndFireReleasesOnceAtItsMidpointAndSpendsOne() {
        long marine = spawn(FRAG, null);
        session.enter(marine);
        float startX = roster.world().renderX(marine);
        session.submit(new ManualIntent(1, 0, 8, 5.5f, true, false, true));
        session.tick();
        var reservation = grenades.reservationFor(marine);
        assertNotNull(reservation);
        assertTrue(session.manualThrowActive());
        assertEquals(8f, reservation.targetX(), 1e-6f);
        assertEquals(FRAG.aoeRadius(), reservation.blastRadius());
        assertFalse(session.canUseAbility(DirectControlAbility.GRENADE));
        int ammo = FRAG.startingAmmo();
        session.submit(new ManualIntent(1, 0, 2, 9, true, false, true));
        for (int ticks = 0; roster.world().secondaryActionTimer(marine) > 0f; ticks++) {
            assertTrue(ticks < 60);
            assertTrue(session.active(), "the follow-through after the release keeps control too");
            assertTrue(session.manualThrowActive());
            assertEquals(startX, roster.world().renderX(marine), "the channel holds the Marine still");
            assertNull(roster.combat().pointFireAim(marine), "and holds the primary");
            if (roster.world().secondaryActionTimer(marine) > FRAG.aimDuration() * .5f) {
                assertEquals(0, releases.size());
                assertEquals(ammo, roster.world().secondaryAmmo(marine), "nothing is spent before the release");
            }
            session.tick();
        }
        assertEquals(1, releases.size(), "one press, one grenade, however long the button is held");
        assertEquals(8f, releases.get(0)[0], 1e-6f, "the cursor moving does not redirect the throw");
        assertEquals(ammo - 1, roster.world().secondaryAmmo(marine));
        assertNull(grenades.reservationFor(marine));
        assertFalse(session.manualThrowActive());
        for (int i = 0; i < 40; i++) session.tick();
        assertEquals(1, releases.size(), "a held button does not throw again");
    }

    @Test
    void aThrowPastItsReachIsPulledInAndKeptOnTheMap() {
        long marine = spawn(FRAG, null);
        session.enter(marine);
        float x = roster.world().renderX(marine);
        float y = roster.world().renderY(marine);
        press(x + 40f, y);
        var reservation = grenades.reservationFor(marine);
        assertEquals(x + FRAG.range(), reservation.targetX(), 1e-4f);
        assertEquals(y, reservation.targetY(), 1e-4f);
        session.exit();
        long other = spawn(FRAG, null);
        session.enter(other);
        press(roster.world().renderX(other), -10f);
        assertEquals(.5f, grenades.reservationFor(other).targetY(), 1e-6f);
    }

    @Test
    void anAcceptedThrowSurvivesHandbackAndDeathBeforeReleaseThrowsNothing() {
        long marine = spawn(FRAG, null);
        session.enter(marine);
        press(9, 5.5f);
        session.exit();
        assertNotNull(grenades.reservationFor(marine));
        for (int i = 0; i < 60 && roster.world().secondaryActionTimer(marine) > 0f; i++) {
            assertTrue(InfantryUnitPrep.tickAimAndShortCircuit(marine, battle));
        }
        assertEquals(1, releases.size(), "ordinary prep finishes the player's throw");
        assertEquals(9f, releases.get(0)[0], 1e-6f);

        long doomed = spawn(FRAG, null);
        session.enter(doomed);
        press(9, 5.5f);
        roster.world().setHp(doomed, 0f);
        session.validate();
        assertFalse(session.active(), "death ends control");
        assertEquals(1, releases.size(), "a body that is no longer ticked never releases");
    }

    @Test
    void thePickResetsOnHandbackAndAnEmptyGrenadeStaysListed() {
        long marine = spawn(FRAG, SHIELD);
        session.enter(marine);
        session.cycle(FireTrigger.RIGHT);
        assertEquals(MarineRightTrigger.Item.SHIELD, session.marineRightPick());
        session.exit();
        session.enter(marine);
        assertEquals(MarineRightTrigger.Item.GRENADE, session.marineRightPick(), "takeover starts on the first item");
        roster.world().setSecondaryAmmo(marine, 0);
        assertEquals(List.of(MarineRightTrigger.Item.GRENADE, MarineRightTrigger.Item.SHIELD),
                session.marineRightMembers(), "an empty grenade reads empty rather than leaving the list");
        assertFalse(session.canUseAbility(DirectControlAbility.GRENADE));
        press(9, 5.5f);
        assertNull(grenades.reservationFor(marine));
        assertFalse(roster.mitigations().isActive(marine), "an empty pick does not fall back to the shield");
    }

    @Test
    void smokeAndAGrenadeOnOneMarineAreBothOnTheListAndTheBeltThrowGoesBackToItsSlot() {
        var smoke = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID);
        assertEquals(List.of(MarineRightTrigger.Item.SMOKE, MarineRightTrigger.Item.GRENADE, MarineRightTrigger.Item.SHIELD),
                MarineRightTrigger.members(FRAG, smoke, SHIELD), "the order is by item, not by slot");
        long marine = roster.spawn(new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID))
                .specialEquipment(smoke, smoke.startingAmmo())
                .beltEquipment(FRAG, FRAG.startingAmmo())
                .integralSystem(SHIELD));
        session.enter(marine);
        assertEquals(List.of(MarineRightTrigger.Item.SMOKE, MarineRightTrigger.Item.GRENADE, MarineRightTrigger.Item.SHIELD),
                session.marineRightMembers());
        assertTrue(session.canUseAbility(DirectControlAbility.GRENADE), "a belt grenade is ready while the hand is free");
        assertTrue(session.cycle(FireTrigger.RIGHT));
        assertEquals(MarineRightTrigger.Item.GRENADE, session.marineRightPick());
        press(9, 5.5f);
        assertNotNull(grenades.reservationFor(marine), "the press throws the belt's grenade");
        assertSame(FRAG, roster.world().specialEquipment(marine), "drawn into the hand for its throw");
        assertSame(smoke, roster.world().beltEquipment(marine), "the smoke rides the belt meanwhile");
        assertEquals(List.of(MarineRightTrigger.Item.SMOKE, MarineRightTrigger.Item.GRENADE, MarineRightTrigger.Item.SHIELD),
                session.marineRightMembers(), "the swap does not reorder the list");
        for (int i = 0; i < 60 && roster.world().secondaryActionTimer(marine) > 0f; i++) session.tick();
        assertEquals(1, releases.size());
        assertEquals(FRAG.startingAmmo() - 1, roster.world().secondaryAmmo(marine));
        assertEquals(smoke.startingAmmo(), roster.world().beltAmmo(marine), "the smoke is untouched");
        assertTrue(session.canUseAbility(DirectControlAbility.SMOKE), "and can be thrown next, from the belt");
    }

    private void press(float x, float y) {
        session.submit(new ManualIntent(0, 0, x, y, false, false, true));
        session.tick();
        session.submit(new ManualIntent(0, 0, x, y, false));
    }

    private long spawn(SpecialEquipmentDef special, IntegralSystemDef system) {
        EntitySpec spec = new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID))
                .specialEquipment(special, special.startingAmmo());
        return roster.spawn(system != null ? spec.integralSystem(system) : spec);
    }
}
