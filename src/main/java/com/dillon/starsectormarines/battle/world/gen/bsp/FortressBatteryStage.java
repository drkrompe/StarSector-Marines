package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.DepthBand;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegion;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Stands exactly the fortress batteries the mission declared ({@link GroundDefences#batteries()}, carried on the
 * target profile as {@link TargetProfile#declaredDefences()}) and finds ground for them. Neither whether a battle has
 * batteries nor how many is decided here: this stage never reads the market's signals and never derives a count from
 * the map's area. With no declaration it places nothing and the map is byte-identical to one generated without it.
 *
 * <p>A battery is a multi-cell structure, emitted as a one-turret {@link DefensePost} like an overwatch tower, so the
 * existing spawn path stands it up and the simulation registers it as a battery because its definition says it is one.
 * It runs post-finalize beside {@link OverwatchTowerStage}, draws no random numbers and ranks its sites by a fixed
 * rule, so adding a declaration perturbs nothing else about the map.
 *
 * <p>Site rule: open outdoor ground with a one-cell clear apron all round the footprint (so a squad can stand at the
 * fire-control point), off the vehicle corridors and doorways, clear of authored infantry places and of other posts,
 * and in the contested middle or deep band. Deep sites are taken first, then those nearest the map centre, which is
 * where a fortress ward keeps its guns.
 */
public final class FortressBatteryStage implements GenStage {

    /** Minimum distance between two batteries, and from any other post, in cells. */
    private static final int BATTERY_SEPARATION = 40;
    private static final int POST_SEPARATION = 12;
    private static final int SCAN_STRIDE = 3;
    private static final EnumSet<DepthBand> ALLOWED_BANDS = EnumSet.of(DepthBand.MID, DepthBand.DEEP, DepthBand.UNSET);
    private static final EnumSet<GroundKind> REFUSED_GROUND = EnumSet.of(GroundKind.INDOOR, GroundKind.WATER,
            GroundKind.TILE, GroundKind.STREET, GroundKind.LZ_MARKER, GroundKind.VOID, GroundKind.SIDEWALK);

    private record Site(int x, int y, int band, long centreDistSq) {}

    public FortressBatteryStage() {}

    @Override
    public void run(GenContext ctx) {
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        if (profile == null || profile.declaredDefences().batteries() <= 0) return;
        TacticalRegionMap regions = ctx.get(BspKeys.TACTICAL_REGIONS);
        if (regions == null) {
            throw new IllegalStateException(
                    "FortressBatteryStage requires TACTICAL_REGIONS - must run after TacticalRegionStage");
        }
        NavigationGrid grid = ctx.grid;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        boolean[][] roadReservation = ctx.get(BspKeys.ROAD_RESERVATION);
        StructureDef def = TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID);

        int budget = profile.declaredDefences().batteries();
        List<Site> sites = candidates(ctx, regions, def, roadReservation);
        sites.sort(Comparator.comparingInt(Site::band).thenComparingLong(Site::centreDistSq)
                .thenComparingInt(Site::y).thenComparingInt(Site::x));

        List<int[]> placed = new ArrayList<>();
        for (Site s : sites) {
            if (placed.size() >= budget) break;
            if (tooClose(ctx.defensePosts, placed, s.x(), s.y())) continue;
            // The footprint must not cut the walkable graph; the apron is walkable by construction.
            if (PlacementGuards.wouldStrandGround(grid, StructureFootprint.minX(def, s.x()),
                    StructureFootprint.minY(def, s.y()), def.footprintCellsX, def.footprintCellsY)) continue;
            StructureFootprint.seal(grid, def, s.x(), s.y());
            List<DefensePost.TurretSpec> turrets = new ArrayList<>(1);
            turrets.add(new DefensePost.TurretSpec(def.id, s.x(), s.y()));
            ctx.defensePosts.add(new DefensePost(DefensePostKind.LIGHT, s.x(), s.y(), turrets));
            placed.add(new int[]{s.x(), s.y()});
        }
        // The sealed cells changed walkability after the regions published; refresh them as the tower stage does.
        if (!placed.isEmpty()) {
            ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, ctx.topology, axis));
        }
    }

    private static List<Site> candidates(GenContext ctx, TacticalRegionMap regions, StructureDef def,
                                         boolean[][] roadReservation) {
        List<Site> out = new ArrayList<>();
        int halfX = def.footprintCellsX / 2 + 1;
        int halfY = def.footprintCellsY / 2 + 1;
        float cx = ctx.width * 0.5f;
        float cy = ctx.height * 0.5f;
        for (int y = halfY; y < ctx.height - halfY; y += SCAN_STRIDE) {
            for (int x = halfX; x < ctx.width - halfX; x += SCAN_STRIDE) {
                TacticalRegion region = regions.regionAt(x, y);
                if (region != null && !ALLOWED_BANDS.contains(region.depthBand)) continue;
                if (!apronIsClear(ctx, x, y, halfX, halfY, roadReservation)) continue;
                if (insideAuthoredInfantryPlace(ctx.tactical, x, y)) continue;
                int band = region != null && region.depthBand == DepthBand.DEEP ? 0 : 1;
                long dx = Math.round(x - cx);
                long dy = Math.round(y - cy);
                out.add(new Site(x, y, band, dx * dx + dy * dy));
            }
        }
        return out;
    }

    /** Every cell of the footprint and its one-cell apron is open outdoor ground off the corridors and doorways. */
    private static boolean apronIsClear(GenContext ctx, int x, int y, int halfX, int halfY, boolean[][] roadReservation) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        for (int dy = -halfY; dy <= halfY; dy++) {
            for (int dx = -halfX; dx <= halfX; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) return false;
                if (REFUSED_GROUND.contains(topology.getGroundKind(cx, cy))) return false;
                if (PlacementGuards.touchesDoorway(grid, cx, cy)) return false;
                if (roadReservation != null && roadReservation[cx][cy]) return false;
            }
        }
        return true;
    }

    private static boolean tooClose(List<DefensePost> posts, List<int[]> placed, int x, int y) {
        long postSq = (long) POST_SEPARATION * POST_SEPARATION;
        for (DefensePost p : posts) {
            long dx = p.anchorX - x;
            long dy = p.anchorY - y;
            if (dx * dx + dy * dy < postSq) return true;
        }
        long batterySq = (long) BATTERY_SEPARATION * BATTERY_SEPARATION;
        for (int[] b : placed) {
            long dx = b[0] - x;
            long dy = b[1] - y;
            if (dx * dx + dy * dy < batterySq) return true;
        }
        return false;
    }

    private static boolean insideAuthoredInfantryPlace(List<TacticalNode> nodes, int x, int y) {
        for (TacticalNode node : nodes) {
            if (node.standPositions().isEmpty()) continue;
            if (x >= node.left - 2 && x <= node.right + 2 && y >= node.top - 2 && y <= node.bottom + 2) return true;
        }
        return false;
    }
}
