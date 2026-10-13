package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.DefensePost;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Scatters exactly the defence nests the mission declared ({@link GroundDefences#nests()}, carried on the target
 * profile as {@link TargetProfile#declaredDefences()}): the dispersed network of small hardened emplacements that the
 * Ground Defenses industry stands for. Neither whether a battle has nests nor how many is decided here; this stage
 * never reads the market's signals and never derives a count from the map's area. With no declaration it places
 * nothing and the map is byte-identical to one generated without it.
 *
 * <p>A nest is not a new structure. It is the ordinary light defence post (a vent ring round one sealed mount) built
 * by {@link DefensePostStamper}, and the guns are the catalog's existing emplacements: nests alternate the dual flak
 * emplacement (the anti-aircraft gun) with the heavy machine gun (the anti-infantry one). Every turret already
 * engages aircraft through the air-threat field, so a nest is a threat to aircraft and people and, deliberately, not
 * to a capital: ordinary ground-to-ship fire stays weak. Nests are unmanned, like the overwatch towers: they emit no
 * guard-post node, so the defender roster is untouched.
 *
 * <p>Sites are deterministic and draw no random numbers, so adding a declaration perturbs nothing else about the map.
 * Candidates are open outdoor ground that fits the light post's footprint, off the vehicle corridors and clear of
 * authored infantry places and of other posts, in the contested middle or deep band. Selection spreads the nests:
 * each pick is the candidate furthest from every post and nest already standing, with distance counted only up to a
 * spacing set by the map's area and the declared count, and ties going to the ground nearest the map's centre. A
 * network, not a cluster.
 */
public final class DefenceNestStage implements GenStage {

    /** Minimum distance between a nest and any other post, in cells. */
    private static final int POST_SEPARATION = 10;
    /** Floor on the spacing the spread aims for, in cells. */
    private static final int MIN_SPACING = 20;
    /** Share of the even-lattice pitch (the square root of area per nest) the spread aims for. */
    private static final double SPACING_OF_PITCH = 0.8;
    private static final int SCAN_STRIDE = 3;
    private static final EnumSet<DepthBand> ALLOWED_BANDS = EnumSet.of(DepthBand.MID, DepthBand.DEEP, DepthBand.UNSET);

    /** The two guns a nest carries in turn: anti-air first, anti-infantry second. */
    static final String[] NEST_GUNS = {
            TurretCatalogRegistry.DUAL_FLAK_STRUCTURE_ID,
            TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID
    };

    private record Site(int x, int y, long centreDistSq) {}

    public DefenceNestStage() {}

    @Override
    public void run(GenContext ctx) {
        TargetProfile profile = ctx.get(BspKeys.MARKET_PROFILE);
        if (profile == null || profile.declaredDefences().nests() <= 0) return;
        TacticalRegionMap regions = ctx.get(BspKeys.TACTICAL_REGIONS);
        if (regions == null) {
            throw new IllegalStateException(
                    "DefenceNestStage requires TACTICAL_REGIONS - must run after TacticalRegionStage");
        }
        NavigationGrid grid = ctx.grid;
        TraversalAxis axis = ctx.get(BspKeys.AXIS);
        boolean[][] roadReservation = ctx.get(BspKeys.ROAD_RESERVATION);

        int budget = profile.declaredDefences().nests();
        List<Site> sites = candidates(ctx, regions, roadReservation);
        double pitch = Math.sqrt((double) ctx.width * ctx.height / budget);
        double cap = Math.max(MIN_SPACING, SPACING_OF_PITCH * pitch);
        double capSq = cap * cap;

        // Distance (squared, capped) from each candidate to the nearest post or nest already standing.
        double[] nearest = new double[sites.size()];
        for (int i = 0; i < nearest.length; i++) {
            nearest[i] = capSq;
            for (DefensePost p : ctx.defensePosts) {
                nearest[i] = Math.min(nearest[i], distSq(sites.get(i), p.anchorX, p.anchorY));
            }
        }

        long postSq = (long) POST_SEPARATION * POST_SEPARATION;
        boolean[] spent = new boolean[sites.size()];
        int placed = 0;
        while (placed < budget) {
            int best = -1;
            for (int i = 0; i < nearest.length; i++) {
                if (spent[i] || nearest[i] < postSq) continue;
                if (best < 0 || nearest[i] > nearest[best]
                        || (nearest[i] == nearest[best] && sites.get(i).centreDistSq() < sites.get(best).centreDistSq())) {
                    best = i;
                }
            }
            if (best < 0) break;
            spent[best] = true;
            Site s = sites.get(best);
            // The ring must not cut the walkable graph; a refused site is simply not used.
            if (PlacementGuards.wouldStrandGround(grid, DefensePostStamper.nestBlockedFootprint(s.x(), s.y()))) continue;
            ctx.defensePosts.add(DefensePostStamper.stampNest(grid, ctx.topology, ctx.doodads, s.x(), s.y(),
                    NEST_GUNS[placed % NEST_GUNS.length]));
            placed++;
            for (int i = 0; i < nearest.length; i++) {
                nearest[i] = Math.min(nearest[i], distSq(sites.get(i), s.x(), s.y()));
            }
        }
        // The sealed cells changed walkability after the regions published; refresh them as the tower stage does.
        if (placed > 0) {
            ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, ctx.topology, axis));
        }
    }

    private static double distSq(Site s, int x, int y) {
        double dx = s.x() - x;
        double dy = s.y() - y;
        return dx * dx + dy * dy;
    }

    private static List<Site> candidates(GenContext ctx, TacticalRegionMap regions, boolean[][] roadReservation) {
        List<Site> out = new ArrayList<>();
        float cx = ctx.width * 0.5f;
        float cy = ctx.height * 0.5f;
        for (int y = 2; y < ctx.height - 2; y += SCAN_STRIDE) {
            for (int x = 2; x < ctx.width - 2; x += SCAN_STRIDE) {
                TacticalRegion region = regions.regionAt(x, y);
                if (region != null && !ALLOWED_BANDS.contains(region.depthBand)) continue;
                if (!DefensePostStamper.nestFits(ctx.grid, ctx.topology, roadReservation, x, y)) continue;
                if (insideAuthoredInfantryPlace(ctx.tactical, x, y)) continue;
                long dx = Math.round(x - cx);
                long dy = Math.round(y - cy);
                out.add(new Site(x, y, dx * dx + dy * dy));
            }
        }
        return out;
    }

    private static boolean insideAuthoredInfantryPlace(List<TacticalNode> nodes, int x, int y) {
        for (TacticalNode node : nodes) {
            if (node.standPositions().isEmpty()) continue;
            if (x >= node.left - 2 && x <= node.right + 2 && y >= node.top - 2 && y <= node.bottom + 2) return true;
        }
        return false;
    }
}
