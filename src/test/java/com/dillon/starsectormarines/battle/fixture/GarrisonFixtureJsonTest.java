package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A market's garrison estimate survives a replay; a fixture without one (legacy) reads as none. */
class GarrisonFixtureJsonTest {

    private static CivilianRescueBattleFixture fixture(TargetProfile profile) {
        return new CivilianRescueBattleFixture(9_201L, List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, RiskLevel.LOW, 4, profile, false);
    }

    @Test
    void roundTripsTheGarrisonEstimate() throws Exception {
        MarketGarrison garrison = new MarketGarrison(240f, 0.4f, 0.25f, 0.75f);
        JSONObject encoded = BattleFixtureJson.toJson(fixture(TargetProfile.NEUTRAL.withGarrison(garrison)));
        TargetProfile decoded = ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile();
        assertEquals(garrison, decoded.garrison());

        JSONObject plain = BattleFixtureJson.toJson(fixture(TargetProfile.NEUTRAL));
        assertFalse(plain.getJSONObject("targetProfile").has("garrison"));
        encoded.getJSONObject("targetProfile").remove("garrison");
        TargetProfile legacy = ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile();
        assertEquals(MarketGarrison.NONE, legacy.garrison());
    }
}
