package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerrainCatalogTest {
    @Test void otherWorldsLoseGrassWithoutChangingAuthoredGeometry() {
        var plan=new TerrainPlan(8,8,TerrainPlan.Finish.GRASS_TOP,3.4,.7,1,
                List.of(new TerrainPlan.Stroke(TerrainPlan.Mode.RAISE,2,List.of(new TerrainPlan.Point(4,4)))));
        assertSame(plan,TerrainCatalog.forSurface(plan,SurfacePalette.VERDANT));
        for(var palette:List.of(SurfacePalette.ROCK,SurfacePalette.ARID,SurfacePalette.FROZEN)) {
            var adapted=TerrainCatalog.forSurface(plan,palette);
            assertEquals(TerrainPlan.Finish.ROCK,adapted.finish());
            assertEquals(plan.strokes(),adapted.strokes());assertEquals(plan.seed(),adapted.seed());
            assertEquals(plan.ruggedness(),adapted.ruggedness());assertEquals(plan.faceDepth(),adapted.faceDepth());
            assertEquals(plan.width(),adapted.width());assertEquals(plan.height(),adapted.height());
        }
    }
    @Test void missionBindingsRefuseWrongExtentsAndUnknownNames() {
        var plan=new TerrainPlan(192,112,TerrainPlan.Finish.ROCK,3,.5,1,List.of());
        assertDoesNotThrow(()->TerrainCatalog.validate(TerrainCatalog.JUNCTION,plan));
        assertThrows(IllegalArgumentException.class,()->TerrainCatalog.validate(TerrainCatalog.RELAY_NINE,plan));
        assertThrows(IllegalArgumentException.class,()->TerrainCatalog.validate(TerrainCatalog.THUNDER_RUN,plan));
        var freight=new TerrainPlan(320,144,TerrainPlan.Finish.ROCK,3,.5,1,List.of());
        assertDoesNotThrow(()->TerrainCatalog.validate(TerrainCatalog.THUNDER_RUN,freight));
        assertThrows(IllegalArgumentException.class,()->TerrainCatalog.validate(TerrainCatalog.RELIEF,plan));
        assertThrows(IllegalArgumentException.class,()->TerrainCatalog.validate(TerrainCatalog.COUNTERATTACK,plan));
        assertDoesNotThrow(()->TerrainCatalog.validate(TerrainCatalog.RELIEF,
                new TerrainPlan(112,64,TerrainPlan.Finish.ROCK,3,.5,1,List.of())));
        assertDoesNotThrow(()->TerrainCatalog.validate(TerrainCatalog.COUNTERATTACK,
                new TerrainPlan(160,96,TerrainPlan.Finish.ROCK,3,.5,1,List.of())));
        assertThrows(IllegalArgumentException.class,()->TerrainCatalog.validate("unknown",plan));
    }
}
