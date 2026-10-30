package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A controlled Marine's left button cycling onto a direct-fire special, on real component data. */
class MarineLeftSpecialSessionTest {
    private final NavigationGrid grid = new NavigationGrid(20, 12);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(20, 12), null);
    /** Every point a special was fired at, as {x, y}. */
    private final List<float[]> specialShots = new ArrayList<>();
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "physicalRadius" -> UnitType.MARINE.radius;
                case "squadOf" -> null;
                case "clearPath" -> null;
                case "fireSecondaryAtPoint" -> {
                    long id = (long) args[0];
                    roster.world().setSecondaryAmmo(id, roster.world().secondaryAmmo(id) - 1);
                    specialShots.add(new float[]{(float) args[1], (float) args[2]});
                    yield true;
                }
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster, id -> false, () -> false);

    MarineLeftSpecialSessionTest() {
        for (int y = 0; y < 12; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
    }

    @Test
    void qCyclesFromThePrimaryToTheLauncherAndBack() {
        long marine = spawn(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID), 3);
        assertTrue(session.enter(marine));
        assertNull(session.leftSpecial());
        int key = session.selectionKey();
        assertTrue(session.cycle(FireTrigger.LEFT));
        assertEquals(SpecialEquipmentRegistry.SRM_1_ID, session.leftSpecial().id());
        assertTrue(session.selectionKey() != key, "a pick change releases held fire");
        assertTrue(session.cycle(FireTrigger.LEFT));
        assertNull(session.leftSpecial());
        assertTrue(session.selectWeapon(1), "key 2 jumps to the special");
        assertEquals(SpecialEquipmentRegistry.SRM_1_ID, session.leftSpecial().id());
    }

    @Test
    void aMarineWithoutADirectFireSpecialHasNothingToCycle() {
        long marine = spawn(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), 2);
        assertTrue(session.enter(marine));
        assertFalse(session.cycle(FireTrigger.LEFT));
        assertFalse(session.canSelectWeapon(1));
        assertNull(session.leftSpecial());
    }

    @Test
    void thePickedSpecialWindsUpThenFiresOneRoundAtTheCursorAndThePrimaryHolds() {
        SpecialEquipmentDef launcher = SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID);
        long marine = spawn(launcher, 3);
        assertTrue(session.enter(marine));
        assertTrue(session.cycle(FireTrigger.LEFT));
        int windupTicks = (int) Math.ceil(launcher.aimDuration() / BattleSimulation.TICK_DT);
        for (int i = 0; i < windupTicks + 30; i++) {
            session.submit(new ManualIntent(0, 0, 15, 6, true));
            session.tick();
            if (i < windupTicks - 1) assertTrue(specialShots.isEmpty(), "fired during the wind-up at tick " + i);
            assertNull(roster.combat().pointFireAim(marine), "the primary held while the launcher was up");
        }
        assertEquals(1, specialShots.size(), "a held button fires one round");
        assertEquals(15f, specialShots.get(0)[0]);
        assertEquals(6f, specialShots.get(0)[1]);
        assertEquals(2, roster.world().secondaryAmmo(marine));
        assertEquals(launcher.cooldown(), roster.world().secondaryCooldownTimer(marine),
                30 * BattleSimulation.TICK_DT + 1e-4f);
    }

    @Test
    void anEmptySpecialStaysPickedAndDoesNotFallBackToThePrimary() {
        long marine = spawn(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), 0);
        assertTrue(session.enter(marine));
        assertTrue(session.cycle(FireTrigger.LEFT));
        for (int i = 0; i < 90; i++) {
            session.submit(new ManualIntent(0, 0, 15, 6, i % 4 < 2));
            session.tick();
        }
        assertTrue(specialShots.isEmpty());
        assertNull(roster.combat().pointFireAim(marine));
        assertEquals(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID, session.leftSpecial().id());
    }

    @Test
    void handbackResetsThePickToThePrimary() {
        long marine = spawn(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SRM_1_ID), 3);
        assertTrue(session.enter(marine));
        assertTrue(session.cycle(FireTrigger.LEFT));
        session.exit();
        assertEquals(MarineLeftTrigger.PRIMARY, session.marineLeft().pick());
        assertTrue(session.enter(marine));
        assertNull(session.leftSpecial());
    }

    private long spawn(SpecialEquipmentDef special, int ammo) {
        EntitySpec spec = new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5);
        spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
        spec.specialEquipment(special, ammo);
        return roster.spawn(spec);
    }
}
