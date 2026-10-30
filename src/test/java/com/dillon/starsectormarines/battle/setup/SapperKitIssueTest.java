package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.CivilianRescueBattleFixture;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.infantry.SquadSupportDeclaration;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The kit that gives a fixture sappers and smoke pots: periods, seats, and surviving a replay. */
class SapperKitIssueTest {

    @Test
    void sappersAndScreenersFollowTheirOwnPeriods() {
        SquadSupportKit kit = new SquadSupportKit(0, 0, 3, 4);
        int sappers = 0;
        int screeners = 0;
        for (int i = 0; i < 12; i++) {
            if (kit.sapper(i)) sappers++;
            if (kit.screener(i)) screeners++;
        }
        assertEquals(4, sappers);
        assertEquals(3, screeners);
        assertFalse(kit.isNone());
        assertFalse(kit.mortarTeam(2) || kit.observer(1), "the older items are not issued by the new periods");
        assertTrue(new SquadSupportKit(6, 4).isNone() == false && new SquadSupportKit(0, 0).isNone());
    }

    @Test
    void theOldTwoPeriodKitIssuesNoSappers() {
        SquadSupportKit kit = new SquadSupportKit(6, 4);
        assertEquals(0, kit.satchelEvery());
        assertEquals(0, kit.smokeEvery());
        assertEquals(new SquadSupportKit(6, 4, 3, 5), kit.withSappers(3, 5));
    }

    @Test
    void aSapperSquadGetsAChargeAndAScreenerSquadSmokeInSeatsNobodyElseHolds() {
        List<MarineSeatCommitment> seats = new ArrayList<>();
        for (int i = 0; i < 6; i++) seats.add(seat("a" + i, "alpha", null));
        for (int i = 0; i < 6; i++) seats.add(seat("b" + i, "bravo", i == 1 ? SpecialEquipmentRegistry.COMMS_RADIO_ID : null));

        List<MarineSeatCommitment> issued = SupportKitIssue.issueGround(new SquadSupportKit(0, 0, 1, 2), seats);

        // Every squad is a sapper squad (period one); the second is also a screener squad (period two).
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, issued.get(1).specialEquipmentId());
        assertNull(issued.get(0).specialEquipmentId(), "the first seat is the tube-holder's");
        assertNull(issued.get(5).specialEquipmentId(), "the last seat is the launcher's");
        assertEquals(SpecialEquipmentRegistry.COMMS_RADIO_ID, issued.get(7).specialEquipmentId(), "an issued item is never taken away");
        assertEquals(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, issued.get(8).specialEquipmentId(), "the next free seat");
        assertEquals(SpecialEquipmentRegistry.SMOKE_GRENADE_ID, issued.get(9).specialEquipmentId());
    }

    @Test
    void aFactoryRolledCycleReceivesItsSapperAndScreenerItemsByOrdinal() {
        SquadSupportKit kit = new SquadSupportKit(0, 0, 3, 2);
        int charges = 0;
        int smokes = 0;
        for (int ordinal = 0; ordinal < 6; ordinal++) {
            MarineLoadout[] cycle = InfantryLoadoutRolls.playerSquad(6, new Random(ordinal), false, false);
            int issued = SupportKitIssue.issueBreachKit(cycle, kit, ordinal);
            assertEquals((kit.sapper(ordinal) ? 1 : 0) + (kit.screener(ordinal) ? 1 : 0), issued);
            for (MarineLoadout seat : cycle) {
                if (SpecialEquipmentRegistry.SATCHEL_CHARGE_ID.equals(seat.specialEquipmentId)) charges++;
                if (SpecialEquipmentRegistry.SMOKE_GRENADE_ID.equals(seat.specialEquipmentId)) smokes++;
            }
            assertEquals(SpecialEquipmentRegistry.SRM_1_ID, cycle[5].specialEquipmentId, "the launcher keeps the last seat");
        }
        assertEquals(2, charges);
        assertEquals(3, smokes);
        MarineLoadout[] none = InfantryLoadoutRolls.playerSquad(6, new Random(1), false, false);
        assertEquals(0, SupportKitIssue.issueBreachKit(none, SquadSupportKit.NONE, 2), "a kit with no sappers issues nothing");
    }

    @Test
    void sappersAndSmokeSurviveAReplayAndALegacyKitStillReadsAsHavingNone() throws Exception {
        SquadSupportDeclaration declared = SquadSupportDeclaration.NONE.with(Faction.MARINE, new SquadSupportKit(6, 4, 3, 5));
        JSONObject encoded = BattleFixtureJson.toJson(fixture(declared));
        assertEquals(declared, ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile().declaredSupportKit());

        SquadSupportDeclaration legacy = SquadSupportDeclaration.NONE.with(Faction.MARINE, new SquadSupportKit(6, 4));
        JSONObject plain = BattleFixtureJson.toJson(fixture(legacy));
        JSONObject kit = plain.getJSONObject("targetProfile").getJSONObject("declaredSupportKit").getJSONObject("MARINE");
        assertFalse(kit.has("satchelEvery") || kit.has("smokeEvery"), "a kit without sappers writes exactly what it always wrote");
    }

    private static CivilianRescueBattleFixture fixture(SquadSupportDeclaration kit) {
        return new CivilianRescueBattleFixture(9_127L, List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, RiskLevel.LOW, 4,
                TargetProfile.NEUTRAL.withDeclaredSupportKit(kit), false);
    }

    private static MarineSeatCommitment seat(String soldier, String squad, String special) {
        return new MarineSeatCommitment(soldier, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).id,
                EquipmentGrade.MILSPEC, new SoldierProfile(SoldierAptitude.GIFTED, 321), special,
                LayeredArmorFamily.CHARCOAL, 60f, 5f, 0.95f, 0.9f, squad, squad, soldier.endsWith("0"), 3, 0);
    }
}
