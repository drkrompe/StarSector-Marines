package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroundRosterRegistryTest {

    @Test
    public void resolvesVanillaFactionAndFallsBackForUnknownModFaction() {
        assertEquals("roster.hegemony", GroundRosterRegistry.resolve("hegemony").id());
        assertEquals("roster.luddic-church",
                GroundRosterRegistry.resolve("knights_of_ludd").id());
        assertEquals("roster.independent",
                GroundRosterRegistry.resolve("some_submod_faction").id());
        assertEquals("roster.independent", GroundRosterRegistry.resolve(null).id());
    }

    @Test
    public void factionProfilesProduceDistinctEquipmentThroughSharedRoller() {
        GroundRosterProfile hegemony = GroundRosterRegistry.resolve("hegemony");
        GroundRosterProfile pirates = GroundRosterRegistry.resolve("pirates");

        MarineLoadout heg = InfantryLoadoutRolls.defenderLoadout(
                hegemony, GroundRosterProfile.ForceTier.BULK,
                RiskLevel.LOW, new ZeroRandom());
        MarineLoadout pirate = InfantryLoadoutRolls.defenderLoadout(
                pirates, GroundRosterProfile.ForceTier.BULK,
                RiskLevel.LOW, new ZeroRandom());

        assertEquals(WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), heg.primaryDef());
        assertEquals(LayeredArmorFamily.MILITIA, heg.armorFamily);
        assertEquals(WeaponRegistry.require(WeaponRegistry.SMG_ID), pirate.primaryDef());
        assertEquals(LayeredArmorFamily.ARMORLESS, pirate.armorFamily);
        assertNotEquals(heg.primaryDef(), pirate.primaryDef());
        assertNotNull(heg.equipmentGrade);
        assertNotNull(pirate.soldierProfile);
    }

    @Test
    public void heavySupportSelectionUsesFactionAuthoredCycle() {
        GroundRosterProfile path = GroundRosterRegistry.resolve("luddic_path");
        assertEquals(3, path.heavySupportCycle(3).size());
        assertEquals(MechVariant.HOUND, path.heavySupportCycle(3).get(0));
        assertEquals(MechVariant.HOUND, path.heavySupportCycle(3).get(2));

        GroundRosterProfile triTachyon = GroundRosterRegistry.resolve("tritachyon");
        assertEquals(MechVariant.SIROCCO, triTachyon.heavySupportCycle(3).get(0));
        assertEquals(MechVariant.HOUND, triTachyon.heavySupportCycle(3).get(1));
        assertEquals(MechVariant.SIROCCO, triTachyon.heavySupportCycle(3).get(2));
    }

    @Test
    public void deterministicFactionSamplesSeparateAutomaticAndShredderDoctrine() {
        GroundRosterProfile.Issue hegemony = GroundRosterRegistry.resolve("hegemony")
                .issue(GroundRosterProfile.ForceTier.BULK);
        GroundRosterProfile.Issue path = GroundRosterRegistry.resolve("luddic_path")
                .issue(GroundRosterProfile.ForceTier.BULK);
        Random hegRandom = new Random(8_101L);
        Random pathRandom = new Random(8_101L);
        int hegemonyAutomatics = 0;
        int hegemonyShredders = 0;
        int pathAutomatics = 0;
        int pathShredders = 0;
        for (int i = 0; i < 2_000; i++) {
            WeaponDef hegWeapon = hegemony.pickPrimaryDef(hegRandom);
            WeaponDef pathWeapon = path.pickPrimaryDef(pathRandom);
            if (hegWeapon == WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)) hegemonyAutomatics++;
            if (hegWeapon == WeaponRegistry.require(WeaponRegistry.SMG_ID)) hegemonyShredders++;
            if (pathWeapon == WeaponRegistry.require(WeaponRegistry.SQUAD_AUTOMATIC_ID)) pathAutomatics++;
            if (pathWeapon == WeaponRegistry.require(WeaponRegistry.SMG_ID)) pathShredders++;
        }

        assertTrue(hegemonyAutomatics > 300,
                "Hegemony regulars should visibly issue squad automatics");
        assertEquals(0, pathAutomatics,
                "Path cells do not gain the disciplined automatic role by fallback");
        assertTrue(pathShredders > hegemonyShredders * 4,
                "Path cells should strongly weight close-range shredders");
    }

    @Test
    public void battleFreezesTargetFactionAndSeedsInitialDefendersFromIt() {
        TargetProfile target = new TargetProfile(4, 5, 1, 1, "pirates", Set.of());
        BattleSimulation sim = BattleSetup.createPlaceholder(
                4_242L, List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)),
                false, RiskLevel.LOW, MissionType.ASSAULT, target);

        assertEquals("roster.pirates", sim.getGroundRoster().id());
        int defenders = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            if (!sim.identity().type(unit).usesInfantryTraining()) continue;
            defenders++;
            assertNotNull(sim.combat().primaryWeapon(unit));
            assertNotNull(sim.world().layeredBodyFamily(unit));
        }
        assertNotEquals(0, defenders);
    }

    private static final class ZeroRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    }
}
