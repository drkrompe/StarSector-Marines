package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** A claimed clinic yard: receiving, patient transfer and utilities around a clear vehicle spine. */
public final class MedicalServiceCourt {
    private final GenContext c;
    private final BlockLeaf parcel;
    private final RoomPose pose;
    private final int width, height;

    private MedicalServiceCourt(GenContext c, BlockLeaf parcel, int turn) {
        this.c = c;
        this.parcel = parcel;
        pose = new RoomPose(turn, false);
        width = pose.upright() ? parcel.width() : parcel.height();
        height = pose.upright() ? parcel.height() : parcel.width();
    }

    /** Local south faces shared circulation. No random draws, roads or neighboring ground are consumed. */
    public static void stamp(GenContext c, BlockLeaf parcel, int turn) {
        new MedicalServiceCourt(c, parcel, turn).stamp();
    }

    private void stamp() {
        int mid = width / 2;
        int halfDrive = width >= 12 ? 2 : 1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int[] at = cell(x, y);
            if (!available(at[0], at[1])) continue;
            boolean drive = Math.abs(x-mid) <= halfDrive && y > 0 && y < height-1;
            c.topology.setGroundKind(at[0], at[1], GroundKind.COURTYARD);
            c.topology.setSurface(at[0], at[1], c.topology.surfaceIndex(
                    (drive ? Material.ASPHALT : Material.CONCRETE).block(1)));
            c.markMadeGround(at[0], at[1]);
        }
        // A five-cell drive (three in narrow courts) and a clear cross-court frontage.
        // The outer row stays open so a neighboring doorway always has a way past the court.
        int leftEnd = mid-halfDrive-1, rightStart = mid+halfDrive+1;
        int front = height >= 10 ? 3 : 2, rear = height-3;
        if (leftEnd >= 1 && rear-front >= 2) {
            if (leftEnd >= 2) bay(1, front, leftEnd, rear);
            boolean truck = prop("doodad.parked-cargo-truck", 1, front, 1, leftEnd, rear, true);
            if (!truck) prop("doodad.industrial-pallet-stack", 1, front, 0, leftEnd, rear, true);
            if (rear >= 8) prop("doodad.industrial-crate-stack", 1, rear, 0, leftEnd, rear, true);
        }
        // An open patient-transfer stand, with a trolley at its rear.
        if (width-2-rightStart >= 1 && rear-front >= 2) {
            bay(rightStart, front, width-2, rear);
            if (rear >= 8) prop("sickbay-clinic.trolley", width-2, rear, 0, width-2, rear, true);
        }
        // Service equipment sits in small rear pockets, outside the cross-court route.
        if (height >= 9 && leftEnd >= 1) {
            prop("doodad.industrial-generator", 1, height-2, 0, leftEnd, height-2, true);
            if (leftEnd >= 3) prop("doodad.industrial-pump-set", 3, height-2, 0, leftEnd, height-2, true);
        }
        if (width-rightStart >= 3 && height >= 9) {
            prop("mess-galley.stool", rightStart, height-2, 0, width-2, height-2, false);
            prop("mess-galley.stool", width-2, height-2, 0, width-2, height-2, false);
        }
    }

    private void bay(int l, int b, int r, int t) {
        for (int x : new int[]{l,r}) for (int y : new int[]{b,t})
            prop("doodad.fl-striped-yellow", x, y, y==b ? 1 : 0, r, t, false);
    }

    /** Full rotated footprints, actual edge connectivity and rollback before any prop is recorded. */
    private boolean prop(String id, int x, int y, int turn, int maxX, int maxY, boolean solid) {
        var def = TileRegistry.installed().doodad(id);
        if (def == null) throw new IllegalStateException("Missing medical court asset: "+id);
        Doodad local = new Doodad(x, y, def, turn);
        if (x < 1 || y < 1 || x+local.footprintCellsX-1 > maxX || y+local.footprintCellsY-1 > maxY
                || x+local.footprintCellsX >= width || y+local.footprintCellsY >= height) return false;
        int[] a = cell(x,y), b = cell(x+local.footprintCellsX-1,y+local.footprintCellsY-1);
        Doodad placed = new Doodad(Math.min(a[0],b[0]), Math.min(a[1],b[1]), def, (turn+pose.quarterTurns())%4);
        List<int[]> occupied = new ArrayList<>();
        for (int py=placed.cellY;py<placed.cellY+placed.footprintCellsY;py++)
            for (int px=placed.cellX;px<placed.cellX+placed.footprintCellsX;px++) {
                if (!available(px,py) || PlacementGuards.touchesDoorway(c.grid,px,py)
                        || PlacementGuards.touchesEdgeBarrier(c.grid,px,py)) return false;
                for (Doodad existing : c.doodads)
                    if (existing.occupiesCell(px,py) && !"doodad.fl-striped-yellow".equals(existing.definitionId())) return false;
                occupied.add(new int[]{px,py});
            }
        if (solid) {
            List<int[]> applied = new ArrayList<>();
            for (int[] at : occupied) {
                if (PlacementGuards.wouldStrandGround(c.grid,at[0],at[1])) {
                    for (int[] undo : applied) c.grid.setWalkable(undo[0],undo[1],true);
                    return false;
                }
                c.grid.setWalkable(at[0],at[1],false);
                applied.add(at);
            }
            for (int[] at : occupied) {
                c.grid.setSeeThrough(at[0],at[1],true);
                c.topology.setFixture(at[0],at[1],true);
            }
        }
        c.doodads.add(placed);
        return true;
    }

    private int[] cell(int x, int y) {
        int[] at = pose.map(x,y,width,height);
        return new int[]{parcel.left+at[0],parcel.top+at[1]};
    }

    private boolean available(int x, int y) {
        boolean[][] reserved = c.get(BspKeys.ROAD_RESERVATION);
        boolean[][] roads = c.get(BspKeys.ROAD_CELLS);
        if (!c.grid.inBounds(x,y) || !c.grid.isWalkable(x,y) || c.grid.isDoorway(x,y)
                || c.topology.isFixture(x,y) || c.topology.hasBuildingKindHint(x,y)
                || (reserved != null && reserved[x][y]) || (roads != null && roads[x][y])) return false;
        for (LandingPad pad : c.landingPads)
            if (x>=pad.left()-2 && x<=pad.right()+2 && y>=pad.bottom()-2 && y<=pad.top()+2) return false;
        return true;
    }
}
