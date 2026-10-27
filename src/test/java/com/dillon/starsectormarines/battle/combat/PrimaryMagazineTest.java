package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.MagazineDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A magazine-fed primary: one load, one reload, one supply for every fire path. */
class PrimaryMagazineTest {
    private final NavigationGrid grid = new NavigationGrid(32, 16);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(32, 16), null);
    private final CombatService combat = roster.combat();
    private int shots;
    private final BattleControl control = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getSimTickIndex" -> 7;
                case "firePointShot" -> {
                    shots++;
                    yield null;
                }
                default -> throw new AssertionError("Unexpected battle call: " + method);
            });
    private final long shooter;

    PrimaryMagazineTest() throws Exception {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
        }
        shooter = roster.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, 2, 5)
                .primaryWeapon(rifle()));
    }

    @Test
    void sizeFollowsTheGradeAndAFreshWeaponIsFull() {
        assertEquals(4, combat.magazineSize(shooter));
        assertEquals(4, combat.roundsLoaded(shooter));
        combat.setEquipmentGrade(shooter, EquipmentGrade.SURPLUS);
        assertEquals(3, combat.roundsLoaded(shooter));
        combat.setEquipmentGrade(shooter, EquipmentGrade.MASTERWORK);
        assertEquals(6, combat.roundsLoaded(shooter));
    }

    @Test
    void theLastRoundStartsTheReloadAndTheFireGateRefusesUntilItEnds() {
        for (int i = 0; i < 4; i++) combat.spendPrimaryRound(shooter);
        assertEquals(0, combat.roundsLoaded(shooter));
        assertTrue(combat.isReloading(shooter));
        assertEquals(combat.reloadSeconds(shooter), combat.reloadTimer(shooter), 1e-6f);

        combat.setPointFireIntent(shooter, new PointFireAim(12f, 5.5f), FireStance.STANCED);
        new FiringSystem(grid, roster).tick(control);
        assertEquals(0, shots);
        assertEquals(FireGate.RELOADING, combat.lastFireGate(shooter));

        int ticks = Math.round(combat.reloadSeconds(shooter) / BattleSimulation.TICK_DT);
        for (int i = 0; i < ticks; i++) combat.tickMagazine(shooter, BattleSimulation.TICK_DT);
        assertFalse(combat.isReloading(shooter));
        assertEquals(4, combat.roundsLoaded(shooter));

        combat.setPointFireIntent(shooter, new PointFireAim(12f, 5.5f), FireStance.STANCED);
        new FiringSystem(grid, roster).tick(control);
        assertEquals(1, shots);
    }

    @Test
    void aReloadNeedsASpentRoundAndDropsAQueuedBurst() {
        assertFalse(combat.beginReload(shooter), "a full load does not reload");
        combat.spendPrimaryRound(shooter);
        combat.setBurstRemaining(shooter, 2);
        assertTrue(combat.beginReload(shooter));
        assertFalse(combat.beginReload(shooter), "one reload at a time");
        assertEquals(0, combat.burstRemaining(shooter));
        assertNull(combat.burstPointAim(shooter));
    }

    @Test
    void recoilBuildsToItsCapAndRecovers() {
        for (int i = 0; i < 3; i++) combat.spendPrimaryRound(shooter);
        assertEquals(0.25f, combat.recoil(shooter), 1e-6f, "three 0.1 kicks against a 0.25 cap");
        combat.tickMagazine(shooter, 0.5f);
        assertEquals(0f, combat.recoil(shooter), 1e-6f);
    }

    @Test
    void reloadScalesWithTheGradeAsTheCycleDoes() {
        assertEquals(2f, combat.reloadSeconds(shooter), 1e-6f);
        combat.equipPrimaryWeapon(shooter, combat.primaryWeaponDef(shooter), EquipmentGrade.MILSPEC,
                SoldierProfile.REGULAR);
        assertEquals(2f * EquipmentGrade.MILSPEC.cooldownMult, combat.reloadSeconds(shooter), 1e-6f);
    }

    @Test
    void sustainedIntervalAveragesALoadAndItsReload() throws Exception {
        MagazineDef magazine = rifle().magazine();
        // Four pulls a load: three cycles of 0.5 s, then a 2 s reload, over four pulls.
        assertEquals((3 * 0.5f + 2f) / 4f,
                magazine.sustainedTriggerInterval(EquipmentGrade.SERVICE, 0.5f, 1, 2f), 1e-6f);
    }

    @Test
    void aMagazineMustNameOneSizePerGrade() throws JSONException {
        JSONObject json = rifleJson();
        json.getJSONObject("sim").getJSONObject("magazine").put("size", new JSONArray("[3, 4]"));
        assertThrows(JSONException.class, () -> WeaponDef.parse(json));
    }

    private static WeaponDef rifle() throws JSONException {
        return WeaponDef.parse(rifleJson());
    }

    private static JSONObject rifleJson() throws JSONException {
        return new JSONObject("""
                {
                  "id": "weapon.magazine-evidence", "mount": "marine-primary",
                  "catalog": {
                    "displayName": "Evidence Rifle",
                    "role": "LINE",
                    "description": "A magazine fixture."
                  },
                  "sim": {
                    "range": 20.0, "damage": 10.0, "accuracy": 1.0,
                    "cooldown": 0.5, "penetration": 1.0, "roundVelocity": 10.0,
                    "magazine": { "size": [3, 4, 5, 6], "reload": 2.0, "trigger": "semi",
                                  "recoil": 0.1, "recoilMax": 0.25, "recoilRecovery": 0.5 }
                  },
                  "render": { "heldSpriteFamily": "RIFLE" },
                  "fx": { "impact": [ {"kind": "glow", "radius": 0.2, "lifetime": 0.1} ] }
                }
                """);
    }
}
