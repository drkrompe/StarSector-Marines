package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GroundDamageTilesTest {
    @Test void ordinaryGroundHasAftermathAndExplicitMaterialPairsWin() {
        TileRegistry registry = TileRegistry.installed();
        CellTopology topology = new CellTopology(1,1);
        topology.setTag(0,0,CellTopology.Tag.GROUND_DAMAGED,true);
        for (var kind : CellTopology.GroundKind.values()) {
            topology.setGroundKind(0,0,kind);
            var skin = GroundDamageTiles.skin(registry,topology,0,0);
            if (kind == CellTopology.GroundKind.VOID || kind == CellTopology.GroundKind.WATER
                    || kind == CellTopology.GroundKind.RUBBLE) assertNull(skin,kind.name());
            else assertNotNull(skin,kind.name());
        }
        topology.setGroundKind(0,0,CellTopology.GroundKind.INDOOR);
        topology.setSurface(0,0,topology.surfaceIndex("road.vent"));
        assertEquals("terrain.blast-metal",GroundDamageTiles.skin(registry,topology,0,0).id);
        topology.setSurface(0,0,topology.surfaceIndex("colony.timber"));
        assertEquals("colony.timber-damage",GroundDamageTiles.skin(registry,topology,0,0).id);
        topology.setGroundKind(0,0,CellTopology.GroundKind.SNOW);
        for (String material : new String[]{"asphalt","concrete"}) {
            for (String age : new String[]{"new","worn","cracked"}) {
                topology.setSurface(0,0,topology.surfaceIndex("pavement."+material+"."+age));
                assertEquals("terrain.blast-paving",GroundDamageTiles.skin(registry,topology,0,0).id,
                        "the authored apron or road material takes precedence over planetary ground");
            }
        }
    }

    @Test void defaultDamageMappingRejectsCollisionsMissingAssetsAndConnectedLayouts() throws Exception {
        GenMappingRegistry mapping = new GenMappingRegistry();
        mapping.ingest(new JSONObject("{groundDamage:{GRASS:'terrain.blast-earth'}}"));
        mapping.validateReferences();
        assertThrows(JSONException.class,
                () -> mapping.ingest(new JSONObject("{groundDamage:{GRASS:'terrain.blast-paving'}}")));
        for (String id : new String[]{"missing.damage","urban.wall"}) {
            GenMappingRegistry broken = new GenMappingRegistry();
            broken.ingest(new JSONObject("{groundDamage:{GRASS:'"+id+"'}}"));
            assertThrows(IllegalStateException.class,broken::validateReferences);
        }
    }

    @Test void damagePairRejectsMissingAndTopologyDependentBlocks() throws Exception {
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(new JSONObject("{sheet:'a.png',cellPx:64,blocks:["
                + "{id:'floor',layout:'single',origin:[0,0],damageBlock:'damage'}]}"));
        assertThrows(IllegalStateException.class, registry::validateReferences);
        registry.ingestSheet(new JSONObject("{sheet:'b.png',cellPx:64,blocks:["
                + "{id:'damage',layout:'wall-faces-4x4',origin:[0,0]}]}"));
        assertThrows(IllegalStateException.class, registry::validateReferences);
    }
}
