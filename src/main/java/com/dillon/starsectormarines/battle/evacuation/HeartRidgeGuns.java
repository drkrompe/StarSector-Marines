package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartLayout;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The cordon's guns on its forward line (`heart-nouns.md`, Cordon's works): the line along the cordon ridge's crest
 * has held the bloom for weeks, so it is armed. An allied gun stands in every nest the line laid, a heavy machine gun
 * with every third a Hephaestus, and a flak emplacement a few cells behind every other nest, so the ridge reads as a
 * front line and its guns shoot what comes up to it. The guns are the military's ({@code ALLY}): they never end the
 * battle, and the flak meets the flier law as the base's does.
 *
 * <p>On The Burn the line is held as well as armed (owner, 2026-10-31: "static defense and squads in the trench
 * line"): along every run of it, a militia team every {@link #TEAM_SPAN} cells, each with a heavy machine gun of its
 * own on the trench beside it, manning that stretch of the run ({@link #holdLine}).
 */
public final class HeartRidgeGuns {

    private HeartRidgeGuns() {}

    /** Every this many nests along the front, counted west to east (then south to north), is a Hephaestus. */
    static final int HEAVY_EVERY = 3;
    /** Every this many nests has a flak emplacement behind it. */
    static final int FLAK_EVERY = 2;
    /** Cells behind the line, toward the start, that a nest's flak is dug. */
    static final int FLAK_BEHIND = 3;
    /** Cells round its place a flak emplacement may move to find open ground. */
    static final int FLAK_SEARCH = 4;
    /** Cells of forward line one of The Burn's line teams holds: one team a stretch this long, rounded, at least one. */
    static final int TEAM_SPAN = 40;
    /** Cells along the run either side of its stop a line team's seats and gun are sought. */
    static final int TEAM_REACH = 6;
    /** Cells round its post along the trench a line team holds. */
    static final int TEAM_HOLD = 6;
    /** Who holds the line's teams, as the command log names it. */
    static final String ISSUER = "ridge-line";

    /** Seats the forward line's guns and flak; returns every gun seated, west to east. Call once at setup. */
    public static List<Long> man(BattleSimulation sim, LaidLayout layout, Form form) {
        List<int[]> nests = nests(layout, form);
        List<Long> guns = new ArrayList<>();
        NavigationGrid grid = sim.getGrid();
        int back = form.advancesAlongY() ? -1 : 0, backX = form.advancesAlongY() ? 0 : -1;
        for (int i = 0; i < nests.size(); i++) {
            int[] nest = nests.get(i);
            if (grid.isWalkable(nest[0], nest[1])) {
                guns.add(ColonyMilitia.seatEmplacement(sim, nest, "Ridge gun " + (i + 1),
                        i % HEAVY_EVERY == HEAVY_EVERY - 1 ? TurretCatalogRegistry.HEPHAESTUS_STRUCTURE_ID
                                : TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID));
            }
            if (i % FLAK_EVERY != 0) continue;
            int[] pit = openPit(grid, nest[0] + backX * FLAK_BEHIND, nest[1] + back * FLAK_BEHIND);
            if (pit != null) {
                guns.add(ColonyMilitia.seatEmplacement(sim, pit, "Ridge flak " + (i / FLAK_EVERY + 1),
                        TurretCatalogRegistry.ANTI_AIR_STRUCTURE_ID));
            }
        }
        return guns;
    }

    /**
     * The Burn's line teams: along each laid run of the forward line, {@link #teams} teams at an even share of it, each
     * a heavy machine gun on the run's trench nearest its stop and a militia team on the trench cells round it,
     * fighting positions first, ordered to man that stretch of the run. Returns the teams' squads. Call once at setup,
     * after {@link #man}.
     */
    public static List<Integer> holdLine(BattleSimulation sim, LaidLayout layout, Form form, RiskLevel risk, Random rng) {
        List<Integer> squads = new ArrayList<>();
        if (layout == null) return squads;
        NavigationGrid grid = sim.getGrid();
        int k = 0;
        for (Laid laid : layout.pieces()) {
            if (laid.kind() != Laid.Kind.LINE || !laid.isLaid() || !laid.name().startsWith(HeartLayout.FORWARD_LINE)) {
                continue;
            }
            List<int[]> trench = new ArrayList<>();
            int from = Integer.MAX_VALUE, to = Integer.MIN_VALUE;
            for (int[] c : laid.cells()) {
                int along = along(form, c);
                from = Math.min(from, along);
                to = Math.max(to, along);
                if (grid.inBounds(c[0], c[1]) && grid.isWalkable(c[0], c[1]) && !grid.isDoorway(c[0], c[1])) trench.add(c);
            }
            if (trench.isEmpty()) continue;
            for (int stop : stops(from, to, teams(to - from + 1))) {
                List<int[]> seats = seats(trench, stop, form,
                        (x, y) -> sim.fightingPositions().isFightingPositionCell(x, y), ColonyMilitia.POST_SIZE + 1);
                if (seats.size() < 2) continue;
                k++;
                // The gun on the seat nearest the stop, the team on the rest.
                ColonyMilitia.seatEmplacement(sim, seats.get(0), "Line gun " + k,
                        TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID);
                trench.removeAll(seats);
                List<int[]> cells = seats.subList(1, seats.size());
                String name = "Line team " + k;
                int squad = ColonyMilitia.seatTeam(sim, cells, name, risk, rng);
                int[] at = cells.get(0);
                int run = sim.fightingPositions().runIdAt(at[0], at[1]);
                sim.assignSquadCommand(run >= 0 ? ObjectiveAssignment.defendRun(squad, at[0], at[1], TEAM_HOLD, run)
                                : ObjectiveAssignment.defendArea(squad, at[0], at[1], TEAM_HOLD),
                        CommandAuthority.GARRISON, ISSUER, name + " mans the forward line");
                squads.add(squad);
            }
        }
        return squads;
    }

    /** How many teams hold a run {@code length} cells long: one per {@link #TEAM_SPAN}, at least one. */
    static int teams(int length) {
        return Math.max(1, Math.round(length / (float) TEAM_SPAN));
    }

    /** Where along a run from {@code from} to {@code to} each of {@code teams} stands: the middle of its even share. */
    static int[] stops(int from, int to, int teams) {
        int[] out = new int[teams];
        float span = (to - from + 1) / (float) teams;
        for (int i = 0; i < teams; i++) out[i] = from + (int) Math.floor(span * (i + 0.5f));
        return out;
    }

    /** Whether a cell is a trench's fighting position. */
    interface CellTest { boolean test(int x, int y); }

    /**
     * Up to {@code size} of {@code trench}'s cells within {@link #TEAM_REACH} of {@code stop} along the line, fighting
     * positions first, nearest the stop first. Pure.
     */
    static List<int[]> seats(List<int[]> trench, int stop, Form form, CellTest position, int size) {
        List<int[]> near = new ArrayList<>();
        for (int[] c : trench) if (Math.abs(along(form, c) - stop) <= TEAM_REACH) near.add(c);
        near.sort(Comparator.<int[]>comparingInt(c -> position.test(c[0], c[1]) ? 0 : 1)
                .thenComparingInt(c -> Math.abs(along(form, c) - stop)).thenComparingInt(c -> c[0] + c[1]));
        return new ArrayList<>(near.subList(0, Math.min(size, near.size())));
    }

    /** A cell's place along the front: across the advance. */
    private static int along(Form form, int[] c) {
        return form.advancesAlongY() ? c[0] : c[1];
    }

    /** The forward line's nests, every run of it, ordered across the front. */
    static List<int[]> nests(LaidLayout layout, Form form) {
        List<int[]> out = new ArrayList<>();
        for (Laid laid : layout.pieces()) {
            if (laid.kind() == Laid.Kind.LINE && laid.isLaid() && laid.name().startsWith(HeartLayout.FORWARD_LINE)) {
                out.addAll(laid.nests());
            }
        }
        out.sort(Comparator.comparingInt(c -> form.advancesAlongY() ? c[0] : c[1]));
        return out;
    }

    /** The open cell nearest {@code (x0, y0)} within {@link #FLAK_SEARCH} with all four sides walkable, or null. */
    private static int[] openPit(NavigationGrid grid, int x0, int y0) {
        for (int r = 0; r <= FLAK_SEARCH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = x0 + dx, y = y0 + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                    int open = 0;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (grid.inBounds(x + d[0], y + d[1]) && grid.isWalkable(x + d[0], y + d[1])) open++;
                    }
                    if (open == 4) return new int[]{x, y};
                }
            }
        }
        return null;
    }
}
