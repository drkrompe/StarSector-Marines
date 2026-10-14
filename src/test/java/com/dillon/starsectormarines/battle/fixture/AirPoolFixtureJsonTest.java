package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A mission's air pools survive a replay by side; a fixture without them (legacy) reads as none. */
class AirPoolFixtureJsonTest {

    @Test
    void roundTripsDeclaredAirPools() throws Exception {
        AirPoolDeclaration pools = AirPoolDeclaration.NONE
                .with(Faction.MARINE, AirPoolAllowance.of(new AirPoolStock(2, 3, 60f), new AirPoolStock(1, 2, 45f), false))
                .with(Faction.DEFENDER, AirPoolAllowance.of(new AirPoolStock(1, 1, 90f), null, true).strikeAtTick(600));
        TargetProfile declared = TargetProfile.NEUTRAL.withDeclaredAirPool(pools);
        CivilianRescueBattleFixture fixture = new CivilianRescueBattleFixture(
                9_127L, List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, RiskLevel.LOW, 4, declared, false);

        JSONObject encoded = BattleFixtureJson.toJson(fixture);
        JSONObject defender = encoded.getJSONObject("targetProfile").getJSONObject("declaredAirPool")
                .getJSONObject("DEFENDER");
        assertEquals(600, defender.getInt("debugStrikeTick"));
        assertEquals(1, defender.getJSONObject("stocks").getJSONObject("BOMBER").getInt("cap"));
        TargetProfile decoded = ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile();
        assertEquals(pools, decoded.declaredAirPool());
        assertEquals(AirPoolAllowance.NO_DEBUG_STRIKE,
                decoded.declaredAirPool().forSide(Faction.MARINE).debugStrikeTick());
        assertEquals(60f, decoded.declaredAirPool().forSide(Faction.MARINE).stock(AirPoolKind.BOMBER).regenSeconds());

        // Nothing declared: nothing is written, and a fixture without the key (legacy) reads as none.
        JSONObject plain = BattleFixtureJson.toJson(new CivilianRescueBattleFixture(
                9_128L, List.of(new ShuttleAssignment(ShuttleType.VALKYRIE, 1)),
                false, RiskLevel.LOW, 4, TargetProfile.NEUTRAL, false));
        assertFalse(plain.getJSONObject("targetProfile").has("declaredAirPool"));
        encoded.getJSONObject("targetProfile").remove("declaredAirPool");
        TargetProfile legacy = ((CivilianRescueBattleFixture) BattleFixtureJson.fromJson(encoded)).targetProfile();
        assertEquals(AirPoolDeclaration.NONE, legacy.declaredAirPool());
    }
}
