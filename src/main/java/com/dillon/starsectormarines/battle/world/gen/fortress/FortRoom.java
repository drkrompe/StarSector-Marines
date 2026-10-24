package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup.DoorSlot;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Building;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.List;

/**
 * One room of a fort aspect's suite, in the fort's local frame: where its floor is, where its doors are, how it is
 * furnished (an authored {@link RoomLayout}, so it passes through the same circulation guards as any fitted room) and
 * where the garrison stands in it. An aspect is housed in a suite, not at a point: the <em>main</em> room carries the
 * aspect's core structure, and an aspect may add ante-rooms (an operations hall
 * in front of a plotting vault, a loading bay in front of a store). The aspect's place is the union of the suite's floors.
 *
 * <p>A gatehouse tower ({@link GateTowers}) is a room of no aspect: held and fought from, but no part of any suite.
 *
 * @param aspect  the aspect this room belongs to, or null for a gatehouse tower
 * @param main    whether this room holds the aspect's core
 * @param purpose what the room is for
 * @param x       floor origin, footprint frame
 * @param y       floor origin, footprint frame
 * @param width   floor width
 * @param height  floor height
 * @param doors   doorways, as ring cells relative to the floor origin (a door at the room's edge, so {@code (-1, 3)} is
 *                in the west wall)
 * @param ops     the authored furnishing, in the room's own frame (origin at the floor's top-left)
 * @param stands  floor cells, relative to the floor origin, where the room's garrison stands
 * @param core    for a main room, the middle of the three-by-three core structure relative to the floor origin
 */
public record FortRoom(FortAspect aspect, boolean main, RoomPurpose purpose, int x, int y, int width, int height,
                       List<Doorway> doors, List<LayoutOp> ops, List<int[]> stands, int[] core) {

    public FortRoom {
        doors = List.copyOf(doors);
        ops = List.copyOf(ops);
        stands = List.copyOf(stands);
        if (main != (core != null)) throw new IllegalArgumentException("exactly the main room holds the core");
    }

    /** The floor as {@code {x, y, width, height}} in the footprint frame. */
    public int[] floor() {
        return new int[]{x, y, width, height};
    }

    /** The compound-layout building the stamp shells, windows and doors. */
    public Building building() {
        return new Building(purpose, RoomShape.rectangle(width, height), x, y, doors);
    }

    /** The furnishing as an authored room layout, which is what circulation is checked against. */
    public RoomLayout layout() {
        List<DoorSlot> slots = doors.stream().map(d -> DoorSlot.run(d.x(), d.y(), 1, 1)).toList();
        return new RoomLayout(purpose, RoomFit.STANDARD, RoomShape.rectangle(width, height), ops,
                slots.isEmpty() ? List.of() : List.of(new Hookup(slots)), false);
    }

    /** The middle of the core structure in the footprint frame; only a main room has one. */
    public int[] coreCentre() {
        if (core == null) throw new IllegalStateException("not the main room of " + aspect);
        return new int[]{x + core[0], y + core[1]};
    }

    /** The doors as footprint cells of the wall ring. */
    public List<int[]> doorCells() {
        return doors.stream().map(d -> new int[]{x + d.x(), y + d.y()}).toList();
    }

    /** The garrison's stand cells in the footprint frame. */
    public List<int[]> standCells() {
        return stands.stream().map(s -> new int[]{x + s[0], y + s[1]}).toList();
    }
}
