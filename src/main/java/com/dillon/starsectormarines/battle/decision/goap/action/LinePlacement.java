package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Where each marine of a squad stands on a drawn line: at the position the claims give him, or,
 * with none, at his own share of the line a cell behind it, settled into the best cover there.
 *
 * <p>One rule for two callers. {@link DefendArea} walks a marine to it, and the drag preview
 * draws it before the order is given, so the place a marker promises is the place the marine
 * goes rather than a second guess at it.
 */
public final class LinePlacement {

    /** How far behind the drawn line a marine without a position stands. */
    static final float REARWARD_OFFSET = 1f;
    /** How far ahead of the line cover is judged from when no threat is believed. */
    static final float FRONT_PROBE = 10f;
    /** How far from his share a marine without a position looks for cover. */
    static final int COVER_SEARCH_RADIUS = 2;

    /** Where one marine will stand, and whether it is a position (a trench or a building post) rather than open ground. */
    public record Place(long member, int x, int y, boolean position) { }

    private LinePlacement() { }

    /** Member {@code ordinal} of {@code count}'s share of the line, a cell behind it. */
    static int[] share(FrontageLine line, int ordinal, int count) {
        float[] share = line.pointAt((ordinal + 0.5f) / count, -REARWARD_OFFSET);
        return new int[]{Math.round(share[0]), Math.round(share[1])};
    }

    /**
     * The covered cell near member {@code ordinal}'s share, judged against the believed threat or,
     * with none ({@code threatX < 0}), against a point ahead of the line, skipping every cell
     * {@code taken} accepts (a position, or a squadmate's stand); {@code null} when no covered cell
     * inside the band is free, and the caller falls back to the share itself.
     */
    static int[] coveredCell(FrontageLine line, int ordinal, int count, int threatX, int threatY,
                             BattleView sim, BiPredicate<Integer, Integer> taken) {
        int[] desired = share(line, ordinal, count);
        int fromX = threatX;
        int fromY = threatY;
        if (fromX < 0 || fromY < 0) {
            float[] front = line.pointAt((ordinal + 0.5f) / count, FRONT_PROBE);
            fromX = Math.round(front[0]);
            fromY = Math.round(front[1]);
        }
        return sim.getTacticalScoring().bestCoverCell(
                fromX, fromY, desired[0], desired[1], COVER_SEARCH_RADIUS,
                (x, y) -> !line.covers(x, y) || taken.test(x, y));
    }

    /**
     * Where every member of {@code squad} available to plan would stand if given {@code line} now.
     * The positions are the claims' own dry run; the rest are placed as {@link DefendArea} places
     * them, in order, each avoiding the cells given before him, falling back to the nearest
     * walkable cell of the band.
     */
    public static List<Place> preview(BattleView sim, Squad squad, FrontageLine line) {
        List<Long> members = FightingPositionClaims.availableMembers(squad, sim);
        if (members.isEmpty()) return List.of();
        int[] cells = sim.fightingPositionClaims().previewLine(sim, squad, line, members);
        int width = sim.getGrid().getWidth();
        Set<Integer> held = new HashSet<>();
        for (int cell : cells) if (cell >= 0) held.add(cell);
        SquadContactPicture picture = squad.contactPicture;
        boolean threat = picture != null && picture.hasContacts()
                && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0;
        int threatX = threat ? picture.primaryCellX() : -1;
        int threatY = threat ? picture.primaryCellY() : -1;
        int count = Math.max(1, squad.autonomousMemberCount(sim));
        List<Place> places = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            long member = members.get(i);
            if (cells[i] >= 0) {
                places.add(new Place(member, cells[i] % width, cells[i] / width, true));
                continue;
            }
            int[] cell = coveredCell(line, i, count, threatX, threatY, sim,
                    (x, y) -> held.contains(y * width + x)
                            || sim.fightingPositionClaims().isClaimedByOther(x, y, width, member));
            if (cell == null) cell = nearestWalkable(line, share(line, i, count), sim.getGrid());
            held.add(cell[1] * width + cell[0]);
            places.add(new Place(member, cell[0], cell[1], false));
        }
        return places;
    }

    /** {@code wanted} if it is walkable, else the nearest walkable cell of the band, else {@code wanted}. */
    private static int[] nearestWalkable(FrontageLine line, int[] wanted, NavigationGrid grid) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        int reach = FrontageLine.BAND;
        for (int y = wanted[1] - reach; y <= wanted[1] + reach; y++) {
            for (int x = wanted[0] - reach; x <= wanted[0] + reach; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !line.covers(x, y)) continue;
                int dx = x - wanted[0];
                int dy = y - wanted[1];
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{x, y};
                }
            }
        }
        return best != null ? best : wanted;
    }
}
