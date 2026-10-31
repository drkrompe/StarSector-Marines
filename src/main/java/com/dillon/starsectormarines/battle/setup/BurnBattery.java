package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Burn's battery (`heart-nouns.md`, Battery): each Locust dug into a gun
 * pit at the bases behind the start line, a ring of sandbag round it with a gap at the back for
 * the crew, and a fireteam of the company's gunners on the bags facing the
 * heart. The crews are not the company: they hold their pits under the
 * battery's own garrison order and never join the push, so the guns are what
 * the swarm has to come through them to reach, and a battery with no gun left
 * is overrun.
 */
final class BurnBattery {

    /** The commander that holds the pits; the company's commander leaves squads under it alone. */
    static final String ISSUER = "heart-battery";
    /** Cells from the gun to its ring of bags. */
    static final int PIT_RADIUS = 2;
    /** A pit's crew. */
    static final int CREW = 4;
    /** How far a crew may go from its gun to fight for it. */
    static final int HOLD_RADIUS = PIT_RADIUS + 3;
    /** How far from where a gun is wanted its pit may be dug. */
    private static final int SEARCH_RADIUS = 14;

    private BurnBattery() { }

    /** One sandbag of a pit's ring. */
    record Bag(int x, int y, String doodad) { }

    /**
     * Digs one gun in as near each of {@code wants} as open ground allows, every pit clear of the landing pads and of
     * the {@code keepOff} rectangles ({@code {x0, y0, x1, y1}}, an airbase lot's fence and verge), crews it and
     * returns the guns' ids.
     */
    static List<Long> dig(BattleSimulation sim, Form form, List<LandingPad> pads, List<int[]> keepOff,
                          String structureId, List<int[]> wants, long seed) {
        Random rng = new Random(seed);
        TileRegistry registry = TileRegistry.installed();
        List<Long> guns = new ArrayList<>();
        // Every gun faces the heart: the form's advance.
        int[] near = form.cellAt(0f, 0.5f), far = form.cellAt(1f, 0.5f);
        int fx = Integer.signum(far[0] - near[0]), fy = Integer.signum(far[1] - near[1]);
        List<LandingPad> clearOf = new ArrayList<>(pads);
        for (int[] r : keepOff) {
            clearOf.add(new LandingPad((r[0] + r[2]) / 2, (r[1] + r[3]) / 2, (r[2] - r[0] + 1) / 2, (r[3] - r[1] + 1) / 2,
                    LandingPad.Approach.NORTH, LandingPad.Purpose.FALLBACK));
        }
        for (int i = 0; i < wants.size(); i++) {
            int[] want = wants.get(i);
            Set<Long> dressed = dressedCells(sim);
            int[] cell = site(sim, clearOf, dressed, want[0], want[1], PIT_RADIUS);
            if (cell == null) cell = site(sim, clearOf, dressed, want[0], want[1], 0);
            if (cell == null) continue;
            int number = i + 1;
            guns.add(sim.spawn(MapTurret.create("burn-battery-" + number, Faction.MARINE, structureId, cell[0], cell[1])));
            List<Bag> ring = registry == null ? List.of() : ring(sim, clearOf, dressed, registry, cell[0], cell[1], fx, fy);
            lay(sim, registry, ring);
            crew(sim, ring, cell[0], cell[1], fx, fy, "Battery " + number, rng);
        }
        return guns;
    }

    /**
     * The ring of bags round a gun at {@code (gx, gy)}, each facing out, less
     * the cell at the back toward the start edge, which is the way in. A cell
     * that is not open ground is left out, so a pit pressed against a hedge
     * uses the hedge.
     */
    static List<Bag> ring(BattleSimulation sim, List<LandingPad> pads, Set<Long> dressed, TileRegistry registry,
                          int gx, int gy, int fx, int fy) {
        List<Bag> bags = new ArrayList<>();
        int r = PIT_RADIUS;
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
            // The way in: the middle of the back side.
            if (dx == -fx * r && dy == -fy * r) continue;
            int x = gx + dx, y = gy + dy;
            if (!open(sim, pads, dressed, x, y)) continue;
            int ox = Math.abs(dx) == r ? Integer.signum(dx) : 0;
            int oy = Math.abs(dy) == r ? Integer.signum(dy) : 0;
            String name = ox != 0 && oy != 0
                    ? "corner-" + letter(0, oy) + letter(ox, 0)
                    : String.valueOf(letter(ox, oy));
            String id = "doodad.fort-sandbag-" + name;
            if (registry.doodad(id) == null) continue;
            bags.add(new Bag(x, y, id));
        }
        return bags;
    }

    /** Sets a pit's bags down on the live map, as a held base's aprons are: walkable, open to sight, cover from the props. */
    private static void lay(BattleSimulation sim, TileRegistry registry, List<Bag> ring) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        for (Bag bag : ring) {
            DoodadDef def = registry.doodad(bag.doodad());
            grid.setSeeThrough(bag.x(), bag.y(), true);
            topology.setFixture(bag.x(), bag.y(), true);
            sim.addDoodad(new Doodad(bag.x(), bag.y(), def));
        }
    }

    /**
     * A pit's crew: a fireteam of gunners on the bags that face the heart,
     * every other bag first so they spread along the front, ordered to hold the
     * pit. Without bags they stand round the gun.
     */
    private static void crew(BattleSimulation sim, List<Bag> ring, int gx, int gy, int fx, int fy,
                             String name, Random rng) {
        List<int[]> spots = new ArrayList<>();
        for (Bag bag : ring) spots.add(new int[]{bag.x(), bag.y()});
        if (spots.size() < CREW) {
            spots.clear();
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && sim.getGrid().isWalkable(gx + dx, gy + dy)) spots.add(new int[]{gx + dx, gy + dy});
            }
            if (spots.size() < CREW) return;
        }
        spots.sort(Comparator.comparingInt((int[] c) -> -((c[0] - gx) * fx + (c[1] - gy) * fy))
                .thenComparingInt(c -> Math.abs((c[0] - gx) * fy) + Math.abs((c[1] - gy) * fx)));
        List<int[]> seats = new ArrayList<>();
        for (int i = 0; i < spots.size() && seats.size() < CREW; i += 2) seats.add(spots.get(i));
        for (int i = 1; i < spots.size() && seats.size() < CREW; i += 2) seats.add(spots.get(i));
        int squadId = sim.mintSquad(Faction.ALLY, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        MarineLoadout[] loadouts = InfantryLoadoutRolls.playerSquad(CREW, rng);
        for (int i = 0; i < CREW; i++) {
            int[] seat = seats.get(i);
            EntitySpec spec = new EntitySpec(name + "-" + (i + 1), Faction.ALLY, UnitType.MARINE, seat[0], seat[1])
                    .role(UnitRole.GARRISON)
                    .squad(squadId)
                    .home(seat[0], seat[1]);
            loadouts[i].seedInto(spec);
            long id = sim.spawn(spec);
            if (squad.leaderId == 0L) squad.leaderId = id;
        }
        squad.originalSize = CREW;
        int run = ring.isEmpty() ? -1 : sim.fightingPositions().runIdAt(seats.get(0)[0], seats.get(0)[1]);
        sim.assignSquadCommand(run >= 0 ? ObjectiveAssignment.defendRun(squadId, gx, gy, HOLD_RADIUS, run)
                        : ObjectiveAssignment.defendArea(squadId, gx, gy, HOLD_RADIUS),
                CommandAuthority.GARRISON, ISSUER, "hold the gun pit");
    }

    /** The open cell nearest {@code (x0, y0)} with open ground for {@code radius} cells round it. */
    private static int[] site(BattleSimulation sim, List<LandingPad> pads, Set<Long> dressed, int x0, int y0, int radius) {
        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                if (clear(sim, pads, dressed, x0 + dx, y0 + dy, radius)) return new int[]{x0 + dx, y0 + dy};
            }
        }
        return null;
    }

    private static boolean clear(BattleSimulation sim, List<LandingPad> pads, Set<Long> dressed, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++)
            for (int dx = -radius; dx <= radius; dx++)
                if (!open(sim, pads, dressed, x + dx, y + dy)) return false;
        return true;
    }

    /** Open ground a pit can be dug on: walkable, off the map's edge, no building, door or prop, clear of the pads. */
    private static boolean open(BattleSimulation sim, List<LandingPad> pads, Set<Long> dressed, int x, int y) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        if (x < 1 || y < 1 || x >= grid.getWidth() - 1 || y >= grid.getHeight() - 1) return false;
        if (!grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.getBuildingId(x, y) != 0
                || topology.isFixture(x, y) || topology.isWall(x, y) || dressed.contains(key(x, y))) return false;
        for (LandingPad pad : pads) {
            if (x >= pad.left() - 2 && x <= pad.right() + 2 && y >= pad.bottom() - 2 && y <= pad.top() + 2) return false;
        }
        return true;
    }

    private static Set<Long> dressedCells(BattleSimulation sim) {
        Set<Long> cells = new HashSet<>();
        for (Doodad d : sim.getDoodads()) {
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++)
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) cells.add(key(x, y));
        }
        return cells;
    }

    /** The sandbag art's facing letter for a world direction, as the prepared-ground courses name it. */
    private static char letter(int dx, int dy) {
        if (dy > 0) return 'n';
        if (dy < 0) return 's';
        return dx > 0 ? 'e' : 'w';
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
