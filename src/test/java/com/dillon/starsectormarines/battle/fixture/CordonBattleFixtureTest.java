package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CordonBattleFixtureTest {

    @Test
    void roundTripsItsVariantSeedAndLift() throws Exception {
        CordonBattleFixture fixture = new CordonBattleFixture(7117L, CordonVariant.ROAD, SurfacePalette.FROZEN,
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)));
        JSONObject json = BattleFixtureJson.toJson(fixture);
        CordonBattleFixture decoded = (CordonBattleFixture) BattleFixtureJson.fromJson(json);
        assertEquals(7117L, decoded.seed());
        assertEquals(CordonVariant.ROAD, decoded.variant());
        assertEquals(SurfacePalette.FROZEN, decoded.biome());
        assertEquals(1, decoded.manifest().size());
        assertEquals(ShuttleType.AEROSHUTTLE, decoded.manifest().get(0).type);
        assertEquals(json.toString(), BattleFixtureJson.toJson(decoded).toString());
    }
}
