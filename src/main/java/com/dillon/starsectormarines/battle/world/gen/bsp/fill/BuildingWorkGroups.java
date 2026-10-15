package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete working groups: furniture, contained visual layers and protected access turn together. */
final class BuildingWorkGroups {
    record Part(String id, int x, int y, boolean blocking) {}
    record Group(int width, int height, List<Part> parts) {
        Group { parts = List.copyOf(parts); }
    }
    private record Candidate(int x, int y, int turn, int score) {}
    private final NavigationGrid grid;
    private final CellTopology topology;
    private final List<Doodad> doodads;
    private final Set<Cell> access = new HashSet<>();

    BuildingWorkGroups(NavigationGrid grid, CellTopology topology, List<Doodad> doodads) {
        this.grid = grid; this.topology = topology; this.doodads = doodads;
        for (Doodad d : doodads) if (d.definitionId() != null && d.definitionId().startsWith("console-bank.") && !d.definitionId().endsWith("corner")) {
            int dx = d.definitionId().endsWith("end-left") ? 1 : d.definitionId().endsWith("end-right") ? -1 : 0;
            int dy = dx == 0 ? -1 : 0;
            for (int turn = 0; turn < d.quarterTurns; turn++) { int old = dx; dx = -dy; dy = old; }
            for (int depth = 1; depth <= 2; depth++) access.add(new Cell(d.cellX + dx * depth, d.cellY + dy * depth));
        }
        for (Doodad d : doodads) if ("doodad.industrial-machine-tool".equals(d.definitionId())
                || "doodad.industrial-fluid-tank".equals(d.definitionId())) {
            for (int[] step : STEPS) {
                int bx = d.cellX - step[0], by = d.cellY - step[1];
                if (grid.inBounds(bx, by) && !grid.isWalkable(bx, by) && !topology.isFixture(bx, by))
                    for (int depth = 1; depth <= 2; depth++) access.add(new Cell(d.cellX + step[0] * depth, d.cellY + step[1] * depth));
            }
        }
    }

    static Group bedBay() {
        return new Group(3, 5, List.of(new Part("sickbay-clinic.monitor", 0, 0, true),
                new Part("sickbay-clinic.bed", 0, 1, true),
                new Part("sickbay-clinic.linen-low", 0, 1, false)));
    }

    static Group examBay() {
        return new Group(4, 4, List.of(new Part("sickbay-clinic.exam", 0, 0, true),
                new Part("sickbay-clinic.monitor", 2, 0, true),
                new Part("sickbay-clinic.trolley", 0, 2, true)));
    }

    static Group sink() { return new Group(2, 3, List.of(new Part("sickbay-clinic.sink", 0, 0, true))); }
    static Group nurseStation() { return new Group(2, 3, List.of(new Part("sickbay-clinic.station", 0, 0, true))); }
    static Group compactExam() {
        return new Group(3, 3, List.of(new Part("sickbay-clinic.exam", 0, 0, true),
                new Part("sickbay-clinic.monitor", 2, 0, true)));
    }
    static Group waitingSeats() {
        return new Group(4, 3, List.of(new Part("mess-galley.stool", 0, 0, false),
                new Part("mess-galley.stool", 2, 0, false)));
    }

    /** Cargo desks keep a two-cell working apron; the tally stool is traversable. */
    static Group cargoDesk(boolean packing) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(packing ? "main-stores.packing" : "main-stores.tally", 0, 0, true));
        if (!packing) parts.add(new Part("mess-galley.stool", 1, 1, false));
        return new Group(3, 3, parts);
    }

    /** Adjacent cabinet modules form a continuous wall bank with a two-cell working aisle. */
    static Group cabinets(int modules) {
        if (modules < 1) throw new IllegalArgumentException("A cabinet bank needs modules");
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < modules; i++) parts.add(new Part("sickbay-clinic.cabinet", 2 * i, 0, true));
        return new Group(2 * modules, 3, parts);
    }

    /** Three stock slots snap onto one rack; the retaining frame belongs above all stock. */
    static Group stockedRack(String stock) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("main-stores.rack", 0, 0, true));
        for (int slot = 0; slot < 3; slot++) parts.add(new Part("main-stores." + stock, 0, slot * 2, false));
        parts.add(new Part("main-stores.frame", 0, 0, false));
        return new Group(4, 8, parts);
    }

    /** Repeatable two-cell pallet slots, with a shared two-cell handling apron. */
    static Group pallets(int modules, String stock) {
        if (modules < 1) throw new IllegalArgumentException("A pallet bank needs modules");
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < modules; i++) {
            parts.add(new Part("main-stores.pallet", i * 2, 0, true));
            parts.add(new Part("main-stores." + stock, i * 2, 0, false));
        }
        return new Group(modules * 2, 4, parts);
    }

    /** An island of joined pallet slots keeps a handling aisle on every side, including corners. */
    static Group palletIsland(List<Cell> slots, String stock) {
        if (slots.isEmpty() || slots.stream().anyMatch(c -> c.x() < 0 || c.y() < 0))
            throw new IllegalArgumentException("Pallet slots must use nonnegative coordinates");
        List<Part> parts = new ArrayList<>();
        for (Cell slot : slots) {
            parts.add(new Part("main-stores.pallet", 2 + slot.x() * 2, 2 + slot.y() * 2, true));
            parts.add(new Part("main-stores." + stock, 2 + slot.x() * 2, 2 + slot.y() * 2, false));
        }
        return new Group(6 + slots.stream().mapToInt(Cell::x).max().orElseThrow() * 2,
                6 + slots.stream().mapToInt(Cell::y).max().orElseThrow() * 2, parts);
    }

    /** Fit whole groups in the named room; wall support wins, then stable authored coordinates. */
    int fit(int bl, int bt, int br, int bb, RoomPurpose purpose, Group group, int budget, int... allowedTurns) {
        int placed = 0;
        int[] turns = allowedTurns.length == 0 ? new int[]{0, 1, 2, 3} : allowedTurns;
        while (placed < budget) {
            List<Candidate> candidates = new ArrayList<>();
            for (int turn : turns) {
                RoomPose pose = new RoomPose(turn, false);
                int w = pose.posedWidth(group.width, group.height), h = pose.posedHeight(group.width, group.height);
                for (int y = bt + 1; y + h <= bb; y++) for (int x = bl + 1; x + w <= br; x++) {
                    if (!available(group, x, y, turn, purpose)) continue;
                    int score = 0;
                    for (Cell cell : blocked(group, x, y, turn)) for (int[] d : STEPS) {
                        int nx = cell.x() + d[0], ny = cell.y() + d[1];
                        if (grid.inBounds(nx, ny) && !grid.isWalkable(nx, ny) && !topology.isFixture(nx, ny)) score++;
                    }
                    candidates.add(new Candidate(x, y, turn, score));
                }
            }
            candidates.sort((a, b) -> Integer.compare(b.score, a.score));
            boolean accepted = false;
            for (Candidate candidate : candidates) if (place(group, candidate.x, candidate.y, candidate.turn, purpose)) {
                placed++; accepted = true; break;
            }
            if (!accepted) break;
        }
        return placed;
    }

    boolean place(Group group, int x, int y, int turn, RoomPurpose purpose) {
        if (!available(group, x, y, turn, purpose)) return false;
        List<Cell> blocked = blocked(group, x, y, turn), applied = new ArrayList<>();
        for (Cell cell : blocked) {
            if (PlacementGuards.wouldStrandGround(grid, cell.x(), cell.y(), 1, 1)) {
                for (Cell undo : applied) grid.setWalkable(undo.x(), undo.y(), true);
                return false;
            }
            grid.setWalkable(cell.x(), cell.y(), false); applied.add(cell);
        }
        for (Cell cell : blocked) {
            topology.setFixture(cell.x(), cell.y(), true);
            grid.setSeeThrough(cell.x(), cell.y(), true);
        }
        for (Part part : group.parts) {
            var def = TileRegistry.installed().doodad(part.id);
            int[] at = anchor(group, part, turn);
            doodads.add(new Doodad(x + at[0], y + at[1], def, turn));
        }
        RoomPose pose = new RoomPose(turn, false);
        for (int dy = 0; dy < pose.posedHeight(group.width, group.height); dy++)
            for (int dx = 0; dx < pose.posedWidth(group.width, group.height); dx++) {
                Cell cell = new Cell(x + dx, y + dy);
                if (!blocked.contains(cell)) access.add(cell);
            }
        return true;
    }

    private boolean available(Group group, int x, int y, int turn, RoomPurpose purpose) {
        for (Part part : group.parts) if (TileRegistry.installed().doodad(part.id) == null) return false;
        RoomPose pose = new RoomPose(turn, false);
        for (int dy = 0; dy < pose.posedHeight(group.width, group.height); dy++)
            for (int dx = 0; dx < pose.posedWidth(group.width, group.height); dx++) {
                int cx = x + dx, cy = y + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy) || grid.isDoorway(cx, cy)
                        || topology.getRoomPurpose(cx, cy) != purpose
                        || doodads.stream().anyMatch(d -> d.occupiesCell(cx, cy))) return false;
            }
        for (Cell cell : blocked(group, x, y, turn))
            if (access.contains(cell) || PlacementGuards.touchesDoorway(grid, cell.x(), cell.y())
                    || PlacementGuards.touchesEdgeBarrier(grid, cell.x(), cell.y())) return false;
        return true;
    }

    private static List<Cell> blocked(Group group, int x, int y, int turn) {
        List<Cell> cells = new ArrayList<>();
        for (Part part : group.parts) if (part.blocking) {
            var def = TileRegistry.installed().doodad(part.id);
            int[] at = anchor(group, part, turn);
            int w = (turn & 1) == 0 ? def.footprintCellsX : def.footprintCellsY;
            int h = (turn & 1) == 0 ? def.footprintCellsY : def.footprintCellsX;
            for (int dy = 0; dy < h; dy++) for (int dx = 0; dx < w; dx++) {
                Cell cell = new Cell(x + at[0] + dx, y + at[1] + dy);
                if (!cells.contains(cell)) cells.add(cell);
            }
        }
        return cells;
    }

    private static int[] anchor(Group group, Part part, int turn) {
        var def = TileRegistry.installed().doodad(part.id);
        RoomPose pose = new RoomPose(turn, false);
        int[] a = pose.map(part.x, part.y, group.width, group.height);
        int[] b = pose.map(part.x + def.footprintCellsX - 1, part.y + def.footprintCellsY - 1, group.width, group.height);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1])};
    }

    private static final int[][] STEPS = {{1,0},{-1,0},{0,1},{0,-1}};
}
