package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PrecinctGroundMarginsTest {
    @Test void singleCellScrapsUsePlanetGroundWhileClaimsAndSurvivingRoadsKeepTheirPaint() {
        for (SurfacePalette planet : SurfacePalette.values()) {
            var topology = new CellTopology(3, 1);
            var ctx = new GenContext(new NavigationGrid(3, 1), topology, new Random(42), 3, 1, 42);
            ctx.put(BspKeys.MARKET_PROFILE, new TargetProfile(0, 0, 0, 0, "", Set.of(), planet, null));
            int empty = GrownTrunkPlan.UNOWNED;
            int[][] claim = {{empty}, {0}, {empty}}, road = {{empty}, {empty}, {0}};
            for (int x = 0; x < 3; x++) topology.setGroundKind(x, 0, GroundKind.STREET);
            HinterlandFillStage.paintUnclaimedGround(ctx, claim, road);
            assertEquals(planet.inlandGround(), topology.getGroundKind(0, 0), "pruned road remnant");
            assertEquals(GroundKind.STREET, topology.getGroundKind(1, 0), "claimed land");
            assertEquals(GroundKind.STREET, topology.getGroundKind(2, 0), "surviving road");
        }
    }
}
