package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.infantry.SquadSupportDeclaration;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A mission's support kit survives a replay by side; a fixture without one (legacy) reads as none. */
class SquadSupportFixtureJsonTest {

    @Test
    void roundTripsADeclaredSupportKitAndLegacyReadsAsNone() throws Exception {
        SquadSupportDeclaration kit = SquadSupportDeclaration.NONE.with(Faction.MARINE, new SquadSupportKit(6, 4));
        CivilianRescueBattleFixture fixture = new CivilianRescueBattleFixture(
                9_127L, List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, RiskLevel.LOW, 4, TargetProfile.NEUTRAL.withDeclaredSupportKit(kit), false);

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        TargetProfile decoded = ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile();
        assertEquals(kit, decoded.declaredSupportKit());

        JSONObject plain = BattleFixtureJson.toJson(new CivilianRescueBattleFixture(
                9_128L, List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, RiskLevel.LOW, 4, TargetProfile.NEUTRAL, false));
        assertFalse(plain.getJSONObject("targetProfile").has("declaredSupportKit"));
        encoded.getJSONObject("targetProfile").remove("declaredSupportKit");
        TargetProfile legacy = ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile();
        assertEquals(SquadSupportDeclaration.NONE, legacy.declaredSupportKit());
    }
}
