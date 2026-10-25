package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The work in an ordinary building, read back off what generation stocked it with.
 *
 * <p>A surface building is furnished with complete work groups — pallet slots,
 * rack bays, a tally desk, machines, shelf runs — but publishes no work, because
 * nobody was ever going to do any there. A staffed building is one a map has
 * decided somebody does work in, and its work is recovered the way a job site is
 * ({@code ai-nouns.md}, Job site): each stock fixture affords its job from one
 * free floor cell beside it. Affordance stays a property of the placement — it
 * is this building being staffed that makes a pallet stock rather than spoil —
 * so the same crate in a yard nobody works is still scenery.
 *
 * <p>Which kind of building it is comes from the same reading ({@link #kindOf}):
 * a depot is a warehouse unless a production machine makes it a factory, and
 * shelf runs with a counter make a shop.
 */
public final class BuildingWork {

    private BuildingWork() { }

    /** What a staffed building is, read off what it holds. */
    public enum Kind {
        WAREHOUSE(RoomPurpose.STOCKROOM),
        FACTORY(RoomPurpose.PRODUCTION_FLOOR),
        GROCERY(RoomPurpose.SHOP_FLOOR);

        private final RoomPurpose purpose;

        Kind(RoomPurpose purpose) { this.purpose = purpose; }

        /** The purpose the building's site answers to, as a room of its own would. */
        public RoomPurpose purpose() { return purpose; }
    }

    /** One placed fixture, posed, with the job it affords here; null for scenery. */
    public record Fixture(int x, int y, int width, int height, String definitionId) {

        /** Whether the fixture stands on the cell. */
        public boolean covers(int cellX, int cellY) {
            return cellX >= x && cellX < x + width && cellY >= y && cellY < y + height;
        }
    }

    /** Floor somebody may stand on to work: walkable, inside, and not a threshold. */
    @FunctionalInterface
    public interface Floor {
        boolean standable(int cellX, int cellY);
    }

    /**
     * The job a fixture affords in a worked building, or null for one nobody works.
     *
     * <p>Stock is stores work in any of the three: a warehouse's pallets, a
     * factory's feed stacks and a shop's shelves are all somewhere something is
     * fetched from and put back. A desk or console is a tally, and a machine is
     * tended.
     */
    public static Affordance affordanceOf(String definitionId) {
        if (definitionId == null) return null;
        return switch (definitionId) {
            case "main-stores.pallet", "main-stores.rack", "colony-furnishings.shelf",
                 "doodad.industrial-pallet-stack", "doodad.industrial-crate-stack" -> Affordance.STOW;
            case "main-stores.tally", "main-stores.packing", "colony-furnishings.desk",
                 "doodad.industrial-control-console" -> Affordance.READOUT;
            case "doodad.industrial-machine-tool", "doodad.industrial-fluid-tank",
                 "factory-machines.fabricator", "factory-machines.assembly" -> Affordance.TEND;
            default -> null;
        };
    }

    /**
     * One task per worked fixture, stood on the first free floor cell beside it.
     *
     * <p>Beside rather than on, because nobody stands inside a rack, and the
     * cells are tried round the fixture in a fixed order so the same building
     * always yields the same work. A cell is given to one fixture only: two
     * pallets sharing an aisle cell are two places to stand, not one place two
     * people stand on. A fixture with no free cell round it is worked by nobody.
     */
    public static List<FixtureTask> recover(List<Fixture> fixtures, Floor floor) {
        List<FixtureTask> tasks = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (Fixture fixture : fixtures) {
            Affordance job = affordanceOf(fixture.definitionId());
            if (job == null) continue;
            int[] stand = standBeside(fixture, fixtures, floor, taken);
            if (stand == null) continue;
            taken.add(key(stand[0], stand[1]));
            tasks.add(FixtureTask.at(stand[0], stand[1], job, stand[2], stand[3]));
        }
        return tasks;
    }

    /**
     * What kind of building this work makes, or null where there is too little
     * of any kind to staff.
     *
     * <p>Generation files a warehouse and a factory alike as a depot and a shop
     * as a residence, so the filing only says which question to ask. A depot
     * with a production machine in it is a factory and any other depot a
     * warehouse; anywhere else, shelf runs with a counter make a shop, and
     * shelves without one are somebody's bookcases.
     *
     * @param depot whether generation filed the building as a depot
     * @param least the fewest stores points worth a crew
     */
    public static Kind kindOf(List<Fixture> fixtures, List<FixtureTask> tasks, boolean depot, int least) {
        boolean machine = false, counter = false;
        int shelves = 0;
        for (Fixture fixture : fixtures) {
            String id = fixture.definitionId();
            if (id == null) continue;
            if (affordanceOf(id) == Affordance.TEND) machine = true;
            if (id.equals("colony-furnishings.desk")) counter = true;
            if (id.equals("colony-furnishings.shelf")) shelves++;
        }
        int stores = 0;
        for (FixtureTask task : tasks) if (task.affordance() == Affordance.STOW) stores++;
        if (depot && machine) return Kind.FACTORY;
        if (stores < least) return null;
        if (depot) return Kind.WAREHOUSE;
        return counter && shelves >= least ? Kind.GROCERY : null;
    }

    /** The stand cell and the fixture cell it faces, or null where every cell round it is taken. */
    private static int[] standBeside(Fixture fixture, List<Fixture> fixtures, Floor floor, Set<Long> taken) {
        int x0 = fixture.x(), y0 = fixture.y(), x1 = x0 + fixture.width() - 1, y1 = y0 + fixture.height() - 1;
        // The long faces first, which is where stock is handled from; then the ends.
        List<int[]> ring = new ArrayList<>();
        boolean wide = fixture.width() >= fixture.height();
        if (wide) {
            for (int x = x0; x <= x1; x++) { ring.add(new int[]{x, y0 - 1, x, y0}); ring.add(new int[]{x, y1 + 1, x, y1}); }
            for (int y = y0; y <= y1; y++) { ring.add(new int[]{x0 - 1, y, x0, y}); ring.add(new int[]{x1 + 1, y, x1, y}); }
        } else {
            for (int y = y0; y <= y1; y++) { ring.add(new int[]{x0 - 1, y, x0, y}); ring.add(new int[]{x1 + 1, y, x1, y}); }
            for (int x = x0; x <= x1; x++) { ring.add(new int[]{x, y0 - 1, x, y0}); ring.add(new int[]{x, y1 + 1, x, y1}); }
        }
        for (int[] cell : ring) {
            if (taken.contains(key(cell[0], cell[1]))) continue;
            if (!floor.standable(cell[0], cell[1])) continue;
            if (onAnyFixture(fixtures, cell[0], cell[1])) continue;
            return cell;
        }
        return null;
    }

    private static boolean onAnyFixture(List<Fixture> fixtures, int x, int y) {
        for (Fixture fixture : fixtures) if (fixture.covers(x, y)) return true;
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
