package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CoverPropDamage;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.SurfaceRole;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The catalog sheets one battle's map draws, and the ones its fighting can bring on
 * ({@code battle-textures.md}): the battle's half of what {@code ShipDeckBattleScene.terrainSheets}
 * is for the company deck. Read from the generated map before the first tick.
 *
 * <ul>
 *   <li>the mapped block of every ground kind on the map, with its blast-damage block;
 *   <li>the block of every surface the topology registered (rooms' walls and floors, pavement,
 *       cliffs and terrain), of the floor under every wall, and every surface role's block;
 *   <li>for each of those blocks, its rubble, damage, window and door blocks, closed over, so a
 *       wall knocked down or a cell blasted draws its art rather than loading it then;
 *   <li>pavement edges, only where the map blends its pavement by edge mask (no road surface);
 *   <li>every placed doodad, its damaged and destroyed states (the names the damage channels
 *       look up), its machine motion parts, and its blight stages and felled dead wood where
 *       creep can spread;
 *   <li>the damage states of the rocks scattered as nature overlay, and the field-salvage art
 *       where the battle has caches.
 * </ul>
 *
 * <p>A sheet this misses still draws: {@link BattleSprites#extraTerrainSheet} loads it on first
 * ask, and the dev build logs it as an unplanned load. GL-free and pure.
 */
public final class BattleTerrainSheets {
    private BattleTerrainSheets() {}

    /** The ground feather's sheet: every pass draws it where materials meet. */
    private static final String FEATHER = GroundFeather.SHEET;

    public static Set<String> of(BattleSimulation sim) {
        boolean salvage = sim.fieldSalvage() != null && !sim.fieldSalvage().caches().isEmpty();
        return of(TileRegistry.installed(), GenMappingRegistry.installed(), sim.getTopology(),
                sim.getGrid().getWidth(), sim.getGrid().getHeight(), sim.getDoodads(),
                BattleTextureSet.creepCanSpread(sim), salvage);
    }

    /** The rules alone, over a map's topology and placed props; {@link #of(BattleSimulation)} reads them off a battle. */
    static Set<String> of(TileRegistry registry, GenMappingRegistry mapping, CellTopology topology, int w, int h,
                          List<Doodad> placed, boolean creep, boolean salvage) {
        if (registry == null) return Set.of();
        Set<String> blocks = new LinkedHashSet<>();
        Set<String> doodads = new LinkedHashSet<>();
        EnumSet<CellTopology.GroundKind> kinds = EnumSet.noneOf(CellTopology.GroundKind.class);
        Set<Integer> overlays = new HashSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                kinds.add(topology.getGroundKind(x, y));
                int overlay = topology.getNatureOverlayIndex(x, y);
                if (overlay >= 0) overlays.add(overlay);
                // The floor a wall stands on, which its fall uncovers.
                if (topology.isWall(x, y)) blocks.add(topology.getWallGroundSurfaceId(x, y));
            }
        }
        // A wall knocked down is rubble ground, or shows the floor it stood on, or indoor floor over solid ground.
        kinds.add(CellTopology.GroundKind.INDOOR);
        kinds.add(CellTopology.GroundKind.RUBBLE);
        if (mapping != null) {
            for (CellTopology.GroundKind kind : kinds) {
                blocks.add(mapping.groundBlockId(kind));
                blocks.add(mapping.groundDamageBlockId(kind));
            }
            for (SurfaceRole role : SurfaceRole.values()) blocks.add(mapping.surfaceBlockId(role));
        }
        boolean pavementEdges = false;
        for (int i = 1; i <= topology.surfaceCount(); i++) {
            String id = topology.surfaceId(i);
            blocks.add(id);
            if (id != null && id.startsWith("pavement.")) pavementEdges = true;
        }
        blocks.remove(null);

        Set<String> sheets = new LinkedHashSet<>();
        for (GridBlockDef block : closure(registry, blocks)) {
            sheets.add(block.sheetPath);
            // GroundRenderSystem blends pavement by edge mask only where no road surface smooths it.
            if (pavementEdges && topology.roadSurface() == null && block.id.startsWith("pavement.")) {
                for (int mask = 1; mask < 16; mask++) {
                    GridBlockDef edge = registry.block(block.id + ".edge-" + mask);
                    if (edge != null) sheets.add(edge.sheetPath);
                }
            }
        }
        sheets.add(FEATHER);

        for (Doodad doodad : placed) {
            if (doodad.sheetPath != null) sheets.add(doodad.sheetPath);
            String id = doodad.definitionId();
            if (id == null) continue;
            doodads.add(id);
            addDamageStates(id, doodads);
            NaturalPropShape shape = NaturalPropShape.find(id);
            if (shape != null) addDamageStates(shape.id(), doodads);
            if (creep) {
                for (int k = 1; k <= 4; k++) doodads.add(id + "-blight-" + k);
                doodads.add(id + "-destroyed" + CreepTreeTwins.FELLED);
            }
        }
        // Rocks scattered as overlay become doodads of their own when first hit.
        for (int overlay : overlays) {
            TileDef tile = registry.byIndex(overlay);
            if (tile != null) addDamageStates(tile.id, doodads);
        }
        if (salvage) {
            for (SalvageResource resource : SalvageResource.values()) {
                doodads.add(SalvageCacheRenderSystem.spriteId(resource));
            }
        }
        for (String id : List.copyOf(doodads)) {
            addMotion(registry.motion(id), doodads);
            addMotion(registry.damagedMotion(id), doodads);
        }
        for (String id : doodads) {
            DoodadDef def = registry.doodad(id);
            if (def != null) sheets.add(def.sheetPath);
        }
        sheets.remove(null);
        return sheets;
    }

    /** The damaged and destroyed states the damage channels look a prop up by. */
    private static void addDamageStates(String id, Set<String> out) {
        out.add(id + "-damaged");
        out.add(id + "-destroyed");
        String[] family = CoverPropDamage.damageStateIds(id);
        if (family != null) {
            out.add(family[0]);
            out.add(family[1]);
        }
    }

    private static void addMotion(PropMotion motion, Set<String> out) {
        if (motion == null) return;
        for (PropMotion.Part part : motion.parts()) out.add(part.doodadId());
    }

    /** {@code ids}' blocks and every rubble, damage, window and door block they lead to. */
    private static Set<GridBlockDef> closure(TileRegistry registry, Set<String> ids) {
        Set<GridBlockDef> seen = new LinkedHashSet<>();
        Deque<String> open = new ArrayDeque<>(ids);
        while (!open.isEmpty()) {
            GridBlockDef block = registry.block(open.pop());
            if (block == null || !seen.add(block)) continue;
            for (String next : new String[]{block.rubbleBlockId, block.damageBlockId,
                    block.windowBlockId, block.doorBlockId}) {
                if (next != null) open.push(next);
            }
        }
        return seen;
    }
}
