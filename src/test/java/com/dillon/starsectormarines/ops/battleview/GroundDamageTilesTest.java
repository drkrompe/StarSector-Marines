package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.tools.tilesetauthoring.GroundDamageReskinTemplate;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GroundDamageTilesTest {
    @Test void materialPairsResolveOnlyOnDamagedFloorsAndPreserveThePerimeter() throws Exception {
        TileRegistry registry = TileRegistry.installed();
        CellTopology topology = new CellTopology(2, 2);
        topology.setGroundKind(0,0,CellTopology.GroundKind.INDOOR);
        var atlas = ImageIO.read(Path.of("mod/graphics/tilesets/colony-ground-damage.png").toFile());
        for (String material : GroundDamageReskinTemplate.MATERIALS) {
            topology.setSurface(0, 0, topology.surfaceIndex("colony." + material));
            topology.setTag(0, 0, CellTopology.Tag.GROUND_DAMAGED, false);
            assertNull(GroundDamageTiles.skin(registry, topology, 0, 0));
            topology.setTag(0, 0, CellTopology.Tag.GROUND_DAMAGED, true);
            var block = GroundDamageTiles.skin(registry, topology, 0, 0);
            assertEquals("colony." + material + "-damage", block.id);
            assertEquals(2, block.cells.length);
            for (int[] cell : block.cells) {
                int visible = 0;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int alpha = atlas.getRGB(cell[0] * 64 + x, cell[1] * 64 + y) >>> 24;
                    if (alpha > 64) visible++;
                    if (x < 3 || y < 3 || x >= 61 || y >= 61)
                        assertTrue(alpha <= 4, material + " must preserve the existing tile seams");
                }
                assertTrue(visible > 70 && visible < 1800, "damage must be legible and localized");
            }
        }
        topology.setWall(0, 0, true);
        assertNull(GroundDamageTiles.skin(registry, topology, 0, 0));
        topology.setWall(0, 0, false);
        topology.setGroundKind(0, 0, CellTopology.GroundKind.RUBBLE);
        assertNull(GroundDamageTiles.skin(registry, topology, 0, 0));
        topology.setGroundKind(0, 0, CellTopology.GroundKind.INDOOR);
        topology.setSurface(0, 0, topology.surfaceIndex("urban.floor"));
        assertEquals("terrain.blast-metal",GroundDamageTiles.skin(registry, topology, 0, 0).id);
    }

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

    @Test void blastVariantsLeaveCellBoundariesAndCornersTransparent() throws Exception {
        var atlas = ImageIO.read(Path.of("mod/graphics/tilesets/terrain-blast.png").toFile());
        for (String family : new String[]{"earth","paving","metal"}) {
            var block = TileRegistry.installed().block("terrain.blast-"+family);
            assertEquals(4,block.cells.length);
            for (int[] cell : block.cells) {
                for (int y=0;y<64;y++) for(int x=0;x<64;x++) {
                    if (x<3 || y<3 || x>=61 || y>=61)
                        assertTrue((atlas.getRGB(cell[0]*64+x,cell[1]*64+y)>>>24)<=4,
                                family+" scar must not create a square tile boundary");
                }
            }
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
