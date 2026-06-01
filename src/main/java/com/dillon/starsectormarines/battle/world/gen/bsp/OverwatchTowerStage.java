package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.DepthBand;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.OverwatchScorer;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.OverwatchSite;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegion;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The first runtime <em>consumer</em> of the structural taxonomy's positional
 * layer: places a handful of defender overwatch guns at genuine corner-tower
 * positions — cover at the back, a clear field of fire <em>out</em> over the
 * kill approach — instead of the uniform-random-in-a-biome-box anchors the
 * {@link DefensePostStamper} rolls. This is the composable realization of the
 * "generators publish, passes consume" pattern: it reads only the published
 * {@link TacticalRegionMap} + the grid (via {@link OverwatchScorer}) and emits
 * {@link DefensePost} records, so any recipe can opt in by listing the stage.
 * It generalizes the {@link FortressWallStamper}'s hand-placed wall towers from
 * "the fortress wall" to "every walled compound facing the attacker".
 *
 * <p><b>Runs post-finalize</b> (after {@link com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalRegionStage},
 * which needs final walkability + ground to segment). {@code FinalizeStage} has
 * therefore already baked cover, seeded wall HP, flagged walls, and flood-filled
 * buildings — so this stage self-bakes cover + HP for the few cells it turns
 * non-walkable, exactly as {@code DefensePostStamper}'s turret-mount stamp does
 * for its turret cells. Towers are not {@code INDOOR}, so the already-run building
 * flood-fill is unaffected.
 *
 * <p><b>Unmanned</b> for this first slice — it emits no
 * {@link com.dillon.starsectormarines.battle.decision.TacticalNode.Kind#GUARDPOST}
 * node, so the defender roster balance is untouched (the autonomous
 * {@link com.dillon.starsectormarines.battle.turret.MapTurret} still fires).
 * Manning the towers + adopting the stage into the legacy recipe are captured
 * follow-ups; see {@code roadmap/mapgen/stories/structural-taxonomy.md}.
 *
 * <p>Draws no {@code rng}: placement is a deterministic function of the scored
 * sites, and it runs after every randomized pass, so adding it perturbs no
 * earlier RNG — the only output delta is the towers themselves.
 */
public final class OverwatchTowerStage implements GenStage {

    /**
     * Map cells per overwatch gun — the tower budget scales with map size rather
     * than a flat cap, so a big conquest map fields a proportionally longer
     * overwatch line (240×160 ≈ 15) and a small map a short one. The physical
     * site supply (wall-backed, attacker-facing corners) and {@link #MIN_SEPARATION}
     * often bite before the budget does, so this is a ceiling on intent, not a
     * guaranteed count.
     *
     * <p><b>Future:</b> this base budget is the natural multiply-point for a
     * campaign-driven <em>defense intensity</em> — a market's Planetary Defenses /
     * Heavy Industry level and command-HQ presence should raise the gun count once
     * market data is plumbed into generation. See
     * {@code roadmap/mapgen/stories/structural-taxonomy.md}.
     */
    private static final int CELLS_PER_TOWER = 2500;
    /** Floor so even a small map fields a token overwatch line. */
    private static final int MIN_TOWERS = 3;
    /** Safety ceiling so a pathological map size can't request hundreds of placement attempts. */
    private static final int MAX_TOWERS = 32;
    /** Minimum cell distance from any existing defense post (and from another tower) — avoids doubling up on a position the kill-zone posts already cover. */
    private static final int MIN_SEPARATION = 12;
    /**
     * Depth bands a tower may stand in. Excludes {@link DepthBand#FORWARD} (the
     * attacker's beachhead — defender guns there would spawn under the landing)
     * and {@link DepthBand#REAR} (behind the objective, already artillery's
     * band): towers belong in the contested middle + the wall approach the
     * assault actually crosses.
     */
    private static final EnumSet<DepthBand> ALLOWED_BANDS = EnumSet.of(DepthBand.MID, DepthBand.DEEP);

    /** Single light auto-cannon — the same turret the LIGHT defense post mounts. */
    private static final TurretKind TOWER_TURRET = TurretKind.VULCAN;

    public OverwatchTowerStage() {}

    @Override
    public void run(GenContext ctx) {
        TacticalRegionMap regions = ctx.get(BspKeys.TACTICAL_REGIONS);
        if (regions == null) {
            throw new IllegalStateException(
                    "OverwatchTowerStage requires TACTICAL_REGIONS — must run after TacticalRegionStage");
        }
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);

        // Budget scales with map size — see CELLS_PER_TOWER.
        int budget = Math.max(MIN_TOWERS, Math.min(MAX_TOWERS,
                Math.round((float) (ctx.width * ctx.height) / CELLS_PER_TOWER)));

        List<OverwatchSite> sites = OverwatchScorer.findSites(grid, regions, axis);
        List<DefensePost> posts = ctx.defensePosts;
        List<int[]> placed = new ArrayList<>();

        int towers = 0;
        for (OverwatchSite s : sites) {
            if (towers >= budget) break;
            int x = s.x(), y = s.y();
            // Depth gate — keep towers on the contested approach (axis maps depth
            // to a band; legacy maps have UNSET depth, so the gate is skipped).
            TacticalRegion stand = regions.regionAt(x, y);
            if (stand != null && stand.depthBand != DepthBand.UNSET
                    && !ALLOWED_BANDS.contains(stand.depthBand)) {
                continue;
            }
            if (tooClose(posts, placed, x, y)) continue;
            // Turning this single cell non-walkable must not sever the walkable
            // graph (a tower mounted in a 1-wide gap would wall it off).
            if (PlacementGuards.wouldPartitionWalkable(grid, new int[][]{{x, y}})) continue;

            stampTowerMount(grid, topology, x, y);
            List<DefensePost.TurretSpec> turrets = new ArrayList<>(1);
            turrets.add(new DefensePost.TurretSpec(TOWER_TURRET, x, y));
            // LIGHT tier = single turret; no GUARDPOST node is emitted (see class
            // doc), so the tier's garrison metadata is inert — the tower is unmanned.
            posts.add(new DefensePost(DefensePostKind.LIGHT, x, y, turrets));
            placed.add(new int[]{x, y});
            towers++;
        }

        // Towers flipped cells to non-walkable AFTER TacticalRegionStage
        // published, so the artifact no longer matches the grid. This stage
        // caused the staleness, so it refreshes the published map — downstream
        // consumers and the "non-walkable ⇒ regionId -1" invariant rely on it.
        if (towers > 0) {
            ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, axis));
        }
    }

    /** True if {@code (x,y)} is within {@link #MIN_SEPARATION} of any existing post anchor or already-placed tower. */
    private static boolean tooClose(List<DefensePost> posts, List<int[]> placed, int x, int y) {
        int minSq = MIN_SEPARATION * MIN_SEPARATION;
        for (DefensePost p : posts) {
            int dx = p.anchorX - x, dy = p.anchorY - y;
            if (dx * dx + dy * dy < minSq) return true;
        }
        for (int[] t : placed) {
            int dx = t[0] - x, dy = t[1] - y;
            if (dx * dx + dy * dy < minSq) return true;
        }
        return false;
    }

    /**
     * Mount a turret on a corner cell: non-walkable STONE pad with manual cover
     * bake + wall HP. Mirrors {@code DefensePostStamper}'s turret-mount stamp —
     * {@link NavigationGrid#recomputeCoverAt} early-returns for non-walkable
     * cells, so the per-facing cover is set directly from the cardinal
     * neighbors. The site already has a wall at its back (that's why the scorer
     * chose it), so the mount inherits real cover toward the rear and keeps its
     * forward arc open over the kill ground.
     */
    private static void stampTowerMount(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, false);
        topology.setGroundKind(x, y, GroundKind.STONE);
        grid.setWallHp(x, y, 100);
        grid.setCoverAtFacing(x, y, NavigationGrid.FACING_N, coverFromNeighbor(grid, x, y - 1));
        grid.setCoverAtFacing(x, y, NavigationGrid.FACING_E, coverFromNeighbor(grid, x + 1, y));
        grid.setCoverAtFacing(x, y, NavigationGrid.FACING_S, coverFromNeighbor(grid, x, y + 1));
        grid.setCoverAtFacing(x, y, NavigationGrid.FACING_W, coverFromNeighbor(grid, x - 1, y));
    }

    /** 1 if the neighbor at {@code (x,y)} is in-bounds and non-walkable, 0 otherwise. */
    private static int coverFromNeighbor(NavigationGrid grid, int x, int y) {
        if (!grid.inBounds(x, y)) return 0;
        return grid.isWalkable(x, y) ? 0 : 1;
    }
}
