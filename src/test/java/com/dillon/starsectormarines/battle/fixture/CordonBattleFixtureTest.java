package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonPosture;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.unit.Faction;
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
        assertEquals(CordonPlan.Density.RURAL, decoded.density());
        assertEquals(json.toString(), BattleFixtureJson.toJson(decoded).toString());
    }

    @Test
    void roundTripsItsDensity() throws Exception {
        CordonBattleFixture fixture = new CordonBattleFixture(7L, CordonVariant.LINE, SurfacePalette.ARID,
                CordonPlan.Density.URBAN, List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)));
        CordonBattleFixture decoded = (CordonBattleFixture) BattleFixtureJson.fromJson(BattleFixtureJson.toJson(fixture));
        assertEquals(CordonPlan.Density.URBAN, decoded.density());
    }

    @Test
    void roundTripsTheMechLanceItsCompanyMayCall() throws Exception {
        MechDropDeclaration drops = MechDropDeclaration.NONE.with(Faction.MARINE,
                new MechDropAllowance(1, List.of(MechVariant.BULWARK, MechVariant.HOUND)).calledAtTick(300));
        CordonBattleFixture fixture = new CordonBattleFixture(7L, CordonVariant.LINE, SurfacePalette.ARID,
                CordonPlan.Density.TOWN, List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1)),
                CordonPosture.HOLD, drops);
        CordonBattleFixture decoded = (CordonBattleFixture) BattleFixtureJson.fromJson(BattleFixtureJson.toJson(fixture));
        assertEquals(drops.forSide(Faction.MARINE), decoded.mechDrops().forSide(Faction.MARINE));
    }
}
