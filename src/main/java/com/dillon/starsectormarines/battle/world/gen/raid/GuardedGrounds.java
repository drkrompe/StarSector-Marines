package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctDefence;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.RaidSiteTemplate;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.List;
import java.util.Map;

/**
 * An open site's guns, spread over what it guards ({@code raid-site-templates.md}, spaceport; owner on
 * {@code raid-site-port-r6}: "Where would the defenders logically be here? Likely they would be spread out"). A site
 * with no wall round what matters to it (a port's pads, its fuel farm, its lines in) declares each guarded place, the
 * ways into it and the guns it holds ({@link RaidIndustry#guardedGrounds}); each is armed through the shared entry for
 * a field with no wall ({@link PrecinctDefence#stampField}), by the doctrine a walled place's guns are sited by: its
 * guns stand outside the place's ground facing out, the gates flanked first, then the corners toward the threat, each
 * with its lane open. This stamps them in place of the works' outworks guns
 * ({@code WorksDefences}), which would mass every gun round the one walled place.
 *
 * <p>No gun stands on another place's ground, on a road, in a gate's approach, inside the works, within
 * {@link DefenceDoctrine#SPACING} of a gun already standing or on a cover prop. Each is crewed as an outworks gun is,
 * by the riflemen security adds ({@link RaidSiteTemplate#WORKS_FORWARD}).
 */
public final class GuardedGrounds {

    private GuardedGrounds() { }

    /**
     * A guarded place: the ground its guns stand round and lay out from (the ships on their pads, a tank bund), the
     * ways in their first two flank, and the guns it holds.
     */
    public record Place(String name, List<PortHub.Rect> ground, List<DefenceDoctrine.Gate> gates,
                        Map<DefensePostKind, Integer> posts) {
        public Place {
            ground = List.copyOf(ground);
            gates = List.copyOf(gates);
            posts = Map.copyOf(posts);
        }
    }

    /** What a site guards, and the ground no gun of its stands on (its roads, the things it keeps). */
    public record Grounds(List<Place> places, List<PortHub.Rect> keepClear) {
        public Grounds {
            places = List.copyOf(places);
            keepClear = List.copyOf(keepClear);
        }
    }

    /**
     * Cells round a gun already standing kept clear of a new one's box: the stamper refuses two closer than
     * {@link DefenceDoctrine#SPACING}, and a site the doctrine planned there would be lost rather than moved.
     */
    static final int GUN_CLEAR = DefenceDoctrine.SPACING - 2;

    /**
     * Stamps every place's guns through {@link PrecinctDefence#stampField}; how many stood. Laid after the works is
     * dressed, so on top of the shared clear ground (the roads, each place's ground, the works' claim) every cover prop
     * and the ground round every gun already standing are kept clear, and each new guard post joins the tactical map,
     * which was linked before the dressing.
     */
    public static int arm(GenContext c, PrecinctPlan plan, Precinct works, Grounds grounds) {
        int w = c.width, h = c.height;
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        boolean[][] clear = new boolean[w][h];
        boolean[][] reservation = c.get(BspKeys.ROAD_RESERVATION);
        // Every walled place keeps its own guns behind and before its wall: the works, the garrison.
        boolean[] walled = new boolean[plan.precincts().size()];
        for (int i = 0; i < walled.length; i++) {
            walled[i] = i == index || plan.precincts().get(i).boundary() == Precinct.Boundary.WALLED;
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                clear[x][y] = reservation != null && reservation[x][y]
                        || claim != null && claim[x][y] >= 0 && claim[x][y] < walled.length && walled[claim[x][y]];
            }
        }
        for (PortHub.Rect r : grounds.keepClear()) fill(clear, r);
        for (Place place : grounds.places()) for (PortHub.Rect r : place.ground()) fill(clear, r);
        // A crate stack is walkable and marks no fixture, and a gun's sandbags over it are two cover props on a cell.
        for (Doodad d : c.doodads) {
            if (d.cover <= 0) continue;
            fill(clear, new PortHub.Rect(d.cellX, d.cellY, d.cellX + Math.max(1, d.footprintCellsX) - 1,
                    d.cellY + Math.max(1, d.footprintCellsY) - 1));
        }
        TacticalMap linked = c.get(BspKeys.TACTICAL_MAP);
        int placed = 0;
        for (Place place : grounds.places()) {
            boolean[][] keep = new boolean[w][h];
            for (int x = 0; x < w; x++) System.arraycopy(clear[x], 0, keep[x], 0, h);
            for (DefensePost post : c.defensePosts) {
                fill(keep, new PortHub.Rect(post.anchorX - GUN_CLEAR, post.anchorY - GUN_CLEAR,
                        post.anchorX + GUN_CLEAR, post.anchorY + GUN_CLEAR));
            }
            boolean[][] field = new boolean[w][h];
            for (PortHub.Rect r : place.ground()) fill(field, r);
            int nodes = c.tactical.size();
            placed += PrecinctDefence.stampField(c, field, place.gates(), place.posts(), keep,
                    RaidSiteTemplate.WORKS_FORWARD).placedCount();
            if (linked != null) for (int i = nodes; i < c.tactical.size(); i++) linked.add(c.tactical.get(i));
        }
        if (placed > 0 && c.get(BspKeys.TACTICAL_REGIONS) != null) {
            c.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(c.grid, c.topology, c.get(BspKeys.AXIS)));
        }
        return placed;
    }

    private static void fill(boolean[][] mask, PortHub.Rect r) {
        for (int x = Math.max(0, r.x0()); x <= Math.min(mask.length - 1, r.x1()); x++) {
            for (int y = Math.max(0, r.y0()); y <= Math.min(mask[0].length - 1, r.y1()); y++) mask[x][y] = true;
        }
    }
}