package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Dressing a Raid works ({@link RaidIndustry#dressWorks}): the works' buildings, and props set on its open ground
 * where they block nothing. A prop yields to circulation rather than refusing the map: one whose footprint would
 * take a street or road, a doorway's neighbourhood, a building, a ramp, a landing pad's apron or another prop is not
 * placed.
 */
public final class WorksProps {

    private WorksProps() { }

    /** The buildings standing in the works' claim, by their interior anchors. */
    public static List<PointOfInterest> buildings(GenContext c, PrecinctPlan plan, Precinct works) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        int index = plan.precincts().indexOf(works);
        List<PointOfInterest> out = new ArrayList<>();
        for (PointOfInterest poi : c.pois) {
            int x = poi.interiorAnchorX, y = poi.interiorAnchorY;
            if (claim == null || index < 0 || x < 0 || y < 0 || x >= claim.length || y >= claim[0].length) continue;
            if (claim[x][y] == index) out.add(poi);
        }
        return out;
    }

    /** The room purpose a building was built for. */
    public static RoomPurpose purposeOf(GenContext c, PointOfInterest poi) {
        return c.topology.getRoomPurpose(poi.interiorAnchorX, poi.interiorAnchorY);
    }

    /**
     * A solid prop ({@code id}, a full id such as {@code ship-engineering.generator}, or a {@code doodad.} one with or
     * without that prefix) with its corner at {@code (x, y)}, turned
     * {@code turns} quarter turns, if its whole footprint is open; returns whether it stood. A placed prop blocks
     * movement, not sight, and is a fixture.
     */
    public static boolean place(GenContext c, int x, int y, String id, int turns) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return false;
        DoodadDef def = registry.doodad(id);
        if (def == null && !id.startsWith("doodad.")) def = registry.doodad("doodad." + id);
        if (def == null) return false;
        Doodad prop = new Doodad(x, y, def, turns);
        if (!open(c, x, y, prop.footprintCellsX, prop.footprintCellsY)) return false;
        for (int yy = y; yy < y + prop.footprintCellsY; yy++) {
            for (int xx = x; xx < x + prop.footprintCellsX; xx++) {
                c.grid.setWalkable(xx, yy, false);
                c.grid.setSeeThrough(xx, yy, true);
                c.topology.setFixture(xx, yy, true);
            }
        }
        c.doodads.add(prop);
        return true;
    }

    /**
     * A floor marking ({@code id}, a prop of no cover painted on the ground) with its corner at {@code (x, y)}, turned
     * {@code turns} quarter turns, if every cell under it is open outdoor ground; returns whether it was laid. Unlike
     * {@link #place} it blocks nothing: the cells stay walkable and are no fixture.
     */
    public static boolean decal(GenContext c, int x, int y, String id, int turns) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return false;
        DoodadDef def = registry.doodad(id.startsWith("doodad.") ? id : "doodad." + id);
        if (def == null) return false;
        Doodad mark = new Doodad(x, y, def, turns);
        for (int yy = y; yy < y + mark.footprintCellsY; yy++) {
            for (int xx = x; xx < x + mark.footprintCellsX; xx++) {
                if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy)
                        || c.topology.isWall(xx, yy) || c.topology.getBuildingId(xx, yy) != 0) {
                    return false;
                }
            }
        }
        c.doodads.add(mark);
        return true;
    }

    /** Whether a {@code w} by {@code h} footprint with its corner at {@code (x, y)} may take a prop. */
    public static boolean open(GenContext c, int x, int y, int w, int h) {
        LayoutGround ground = new LayoutGround(c);
        // A place's own roads, inside its claim, which the layout's ways leave to the place.
        int[][] roadOwner = c.get(BspKeys.PRECINCT_ROAD);
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (!c.grid.inBounds(xx, yy) || !c.grid.isWalkable(xx, yy) || c.topology.isFixture(xx, yy)
                        || c.topology.hasBuildingKindHint(xx, yy)
                        || c.topology.getGroundKind(xx, yy) == GroundKind.STREET
                        || ground.isWay(xx, yy)
                        || (roadOwner != null && roadOwner[xx][yy] != GrownTrunkPlan.UNOWNED)
                        || (c.grid.elevation() != null && c.grid.elevation().isRamp(xx, yy))) {
                    return false;
                }
                for (int ty = yy - 1; ty <= yy + 1; ty++) {
                    for (int tx = xx - 1; tx <= xx + 1; tx++) {
                        if (c.grid.inBounds(tx, ty) && c.grid.isDoorway(tx, ty)) return false;
                    }
                }
                for (LandingPad pad : c.landingPads) {
                    if (xx >= pad.left() - 2 && xx <= pad.right() + 2 && yy >= pad.bottom() - 2
                            && yy <= pad.top() + 2) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
