package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * One room, authored rather than programmed: its footprint, and the steps that
 * furnish it.
 *
 * <p>This is what the workbench writes and what generation prefers over the
 * procedural fitting for the same purpose. The fitting is not replaced so much
 * as demoted to a <b>seed</b>: a layout normally begins life as the recorded
 * output of the fitting that used to own the room, and is then corrected by
 * hand. A purpose with no layout keeps its fitting, so authoring one room never
 * disturbs another.
 *
 * <p><b>A layout is bound to a footprint.</b> It matches only a room whose
 * canonical shape is the mask it was authored against, which is what keeps a
 * shipboard armoury's layout off a fortress armoury of a different size and
 * makes the binding self-enforcing rather than a rule somebody has to remember.
 * Rooms aboard ship are fixed-footprint and only turned, so this costs nothing
 * there and refuses exactly the cases it should.
 *
 * <p>It is also bound to a {@link RoomFit}. An authored arrangement is one
 * arrangement and cannot express the refit ladder the way a procedure can, so
 * the level is part of the key and a level nobody has authored falls back to the
 * procedure. That keeps all three reachable without demanding all three be drawn
 * before any of them is useful.
 */
public record RoomLayout(RoomPurpose purpose, RoomFit fit, RoomShape shape,
                         List<LayoutOp> ops, List<Hookup> hookups, boolean handed) {

    public RoomLayout {
        if (purpose == null) throw new IllegalArgumentException("a layout is for some purpose");
        if (fit == null) throw new IllegalArgumentException("a layout is fitted to some level");
        if (shape == null) throw new IllegalArgumentException("a layout needs a footprint");
        ops = List.copyOf(ops);
        hookups = List.copyOf(hookups);
    }

    /**
     * Whether this layout is the one to use for a room of this purpose, shape
     * and level. Shape equality is by mask, so a rectangle authored eight by six
     * does not answer for one authored six by eight.
     */
    public boolean matches(RoomPurpose otherPurpose, RoomShape otherShape, RoomFit otherFit) {
        return purpose == otherPurpose && fit == otherFit && shape.equals(otherShape);
    }

    /**
     * How many fixtures this layout stands up, which is the room's capacity.
     *
     * <p>Counted from the document rather than asserted beside it. Law 4 says
     * capacity is the fixture count, and a berth whose recipe claims nine bunks
     * while its layout draws seven is how a ship comes to berth more people than
     * she has racks.
     *
     * <p>Paving and berths are not fixtures. A berth is floor kept clear for
     * something the host parks there, and counting it would charge the room for
     * a machine it does not own.
     */
    public int provides() {
        int count = 0;
        for (LayoutOp op : ops) {
            if (op instanceof LayoutOp.Fixture) count++;
        }
        return count;
    }

    /**
     * The poses this arrangement actually survives, and the reason it is worth
     * asking.
     *
     * <p>A doodad's footprint does not turn with the room, so a two-cell bench
     * authored across the beam still occupies two cells along it once the room
     * is quarter-turned — and where that runs it into a neighbour or off the
     * shape, {@code RoomFloor.place} refuses silently and the room simply comes
     * out sparser. The packer is free to choose any pose, so a layout that only
     * works upright is a room that is fine on one deck and half empty on the
     * next, with nothing anywhere to say why.
     *
     * <p>This does not constrain placement — the placer is not this story's to
     * touch. It is what the editor reports before a layout is saved, so the
     * choice between reshaping the arrangement and accepting the loss is made
     * deliberately rather than discovered later.
     */
    public List<RoomPose> posesThatFit() {
        List<RoomPose> good = new ArrayList<>();
        for (RoomPose pose : RoomPose.all()) {
            if (survives(pose)) good.add(pose);
        }
        return good;
    }

    private boolean survives(RoomPose pose) {
        int canonicalWidth = shape.width();
        int canonicalHeight = shape.height();
        int posedWidth = pose.posedWidth(canonicalWidth, canonicalHeight);
        int posedHeight = pose.posedHeight(canonicalWidth, canonicalHeight);
        boolean[][] taken = new boolean[posedWidth][posedHeight];
        for (LayoutOp op : ops) {
            if (!(op instanceof LayoutOp.Fixture fixture)) continue;
            DoodadDef def = TileRegistry.installed().doodad(fixture.doodadId());
            if (def == null) return false;
            int[] cell = pose.map(fixture.x(), fixture.y(), canonicalWidth, canonicalHeight);
            for (int dx = 0; dx < def.footprintCellsX; dx++) {
                for (int dy = 0; dy < def.footprintCellsY; dy++) {
                    int x = cell[0] + dx;
                    int y = cell[1] + dy;
                    if (x < 0 || y < 0 || x >= posedWidth || y >= posedHeight) return false;
                    // The posed room is the same mask turned, so a cell that is
                    // outside it once carried back is a fixture hanging out of
                    // the room rather than merely out of its bounding box.
                    int[] canonical = pose.unmap(x, y, canonicalWidth, canonicalHeight);
                    if (!shape.contains(canonical[0], canonical[1])) return false;
                    if (taken[x][y]) return false;
                    taken[x][y] = true;
                }
            }
        }
        return true;
    }

    /**
     * Every doodad id this layout names, so a caller can check them against the
     * catalog in one pass.
     *
     * <p>Worth its own method because the failure it guards is silent: a missing
     * id makes {@code RoomFloor.place} return false and the room comes out bare
     * with nothing to say it went wrong.
     *
     * <p>A bulkhead names a <em>block</em> rather than a doodad and is reported
     * by {@link #blockIds()} instead. Checking one against the other's catalog
     * would refuse every valid wall in the project.
     */
    public List<String> blockIds() {
        List<String> ids = new ArrayList<>();
        for (LayoutOp op : ops) {
            if (op instanceof LayoutOp.Bulkhead bulkhead) ids.add(bulkhead.blockId());
            if (op instanceof LayoutOp.Flooring flooring) ids.add(flooring.blockId());
        }
        return ids;
    }

    /**
     * The bulkhead this room asks for, or null to draw the deck's own.
     *
     * <p>Last one wins, because the list is a script and a later step is a later
     * decision — the same rule paving over a fixture follows.
     */
    public String bulkhead() {
        String named = null;
        for (LayoutOp op : ops) {
            if (op instanceof LayoutOp.Bulkhead bulkhead) named = bulkhead.blockId();
        }
        return named;
    }

    public List<String> doodadIds() {
        List<String> ids = new ArrayList<>();
        for (LayoutOp op : ops) {
            if (op instanceof LayoutOp.Fixture fixture) ids.add(fixture.doodadId());
            if (op instanceof LayoutOp.Paving paving) ids.add(paving.doodadId());
        }
        return ids;
    }
}
