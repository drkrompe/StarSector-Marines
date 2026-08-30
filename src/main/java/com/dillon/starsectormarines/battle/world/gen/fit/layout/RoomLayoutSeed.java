package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFitting;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFittings;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The first draft of an authored room: what the program that owns it does, written
 * down as a document.
 *
 * <p>This is why no authored room starts from an empty grid. The procedural
 * fittings are years of decisions about how a galley or an armoury is arranged,
 * and an editor that made somebody re-place every bunk by hand would throw all
 * of it away on the first edit. So the fitting is run once against a canonical
 * floor with a recorder attached, and what it did becomes the starting document.
 *
 * <p>The same shape the tileset walkthrough uses: measure and propose
 * automatically, correct by hand, write. A seed is a draft and nothing more —
 * saving it unchanged reproduces the room that already ships, which is exactly
 * the property that makes the first edit safe to make.
 */
public final class RoomLayoutSeed {

    private RoomLayoutSeed() {}

    /**
     * Record what the procedural fitting for this room does, as a layout.
     *
     * @return the draft, or null where no fitting owns the purpose — a room
     *     nobody has furnished has nothing to copy, and seeding an empty
     *     document for it would look like a fitting that produces nothing
     */
    public static RoomLayout from(RoomPurpose purpose, RoomShape shape, RoomFit fit) {
        RoomFitting fitting = RoomFittings.forPurpose(purpose);
        return fitting == null ? null : from(fitting, purpose, shape, fit);
    }

    /** Record what one named fitting does at this footprint. */
    public static RoomLayout from(RoomFitting fitting, RoomPurpose purpose,
                                  RoomShape shape, RoomFit fit) {
        List<LayoutOp> ops = new ArrayList<>();
        RoomFloor floor = canonicalFloor(shape, purpose, fit, hookupDoors(fitting, shape));
        floor.record(ops::add);
        fitting.fit(floor);

        return new RoomLayout(purpose, fit, shape, ops,
                fitting.hookups(shape), fitting.handed());
    }

    /**
     * A floor for the room standing on its own, at the canonical pose.
     *
     * <p>Canonical because that is the frame a layout is authored in, and
     * because a seed taken from a turned room would bake this deck's particular
     * rotation into a document meant to serve every deck.
     *
     * <p>Standing on its own, with the deck around it walkable and empty, so the
     * recorded arrangement is the fitting's own and not what one hull's packing
     * happened to leave it room for.
     */
    private static RoomFloor canonicalFloor(RoomShape shape, RoomPurpose purpose,
                                            RoomFit fit, List<Doorway> doors) {
        int margin = 4;
        int width = shape.width() + margin * 2;
        int height = shape.height() + margin * 2;
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        // A fixed seed, because a seeded document has to be the same document
        // every time it is taken. A fitting that placed at random would
        // otherwise make the editor's first screen differ from its second.
        GenContext ctx = new GenContext(grid, topology, new Random(0L), width, height, 0L);

        List<Doorway> placed = new ArrayList<>();
        for (Doorway door : doors) {
            placed.add(new Doorway(margin + door.x(), margin + door.y()));
        }
        return new RoomFloor(ctx, new SeedRoom(shape, margin, margin,
                RoomPose.CANONICAL, purpose, placed), fit);
    }

    /**
     * Where the room's own hookups say its doors are, or one door on the near
     * bulkhead when it authors none.
     *
     * <p>A fitting reads its doors while it arranges itself — a berth puts its
     * stowage by the hatches — so seeding without any would record an
     * arrangement that no real room ever takes.
     */
    private static List<Doorway> hookupDoors(RoomFitting fitting, RoomShape shape) {
        List<Doorway> doors = new ArrayList<>();
        for (Hookup hookup : fitting.hookups(shape)) {
            for (Hookup.DoorSlot slot : hookup.slots()) {
                int[] cell = slot.cells().get(0);
                doors.add(new Doorway(cell[0], cell[1]));
            }
            break;
        }
        if (doors.isEmpty()) doors.add(new Doorway(0, shape.height() / 2));
        return doors;
    }

    private record SeedRoom(RoomShape shape, int originX, int originY, RoomPose pose,
                            RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }
}
