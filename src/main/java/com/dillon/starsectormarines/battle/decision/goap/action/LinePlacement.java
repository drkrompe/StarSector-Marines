package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.goap.action.FightingPositionClaims.Probe;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Where each marine of a squad stands on a drawn line: at the position the claims give him, or,
 * with none, in the best cover against the line's front near his own share of it, a cell behind
 * the line.
 *
 * <p>The front is the direction the player's drag implies, and it is what cover is judged
 * against: not the squad's believed primary contact, which can be one runner on a flank. A front
 * that runs between two compass points takes the cover on both of its sides, each in proportion
 * to how much of the front it faces, so a line dragged on the diagonal settles behind a corner
 * of sandbags rather than behind whichever one face the front happened to snap to.
 *
 * <p>The line gives way to the fight only where the player would agree it should: a marine whose
 * place gives him <em>no</em> cover against the fire actually coming in — a charge from the
 * flank, say — moves to real cover against it ({@link #MIN_REACTIVE_COVER} or better) on the
 * line's own ground, if there is some he can shoot back from. Cover that helps at all is kept,
 * and he returns to the front's cover once the threat is gone. So the choice the squad makes is
 * never a sidegrade of the player's: it is from nothing to something.
 *
 * <p>One rule for every caller. {@link FightingPositionClaims} settles it once per tick for
 * every line-holding squad, so squadmates never pick the same sandbag; {@link DefendArea}
 * walks a marine to the cell it settled; and the drag preview runs it before the order is
 * given, so the place a marker promises is the place the marine goes.
 */
public final class LinePlacement {

    /** How far behind the drawn line a marine's share of it lies. */
    public static final float REARWARD_OFFSET = 1f;
    /** How far ahead along the front a covered cell must see, so cover is a place to fight from. */
    static final float FRONT_PROBE = 10f;
    /** How far from his share a marine looks for cover: the band's own half-width. */
    static final int COVER_SEARCH_RADIUS = FrontageLine.BAND;
    /** How far in front of the drawn line a marine may settle, so a line drawn just behind a wall reaches it. */
    static final float MAX_AHEAD = 1f;
    /**
     * The least cover against a believed threat worth leaving the front's place for: medium, "worth
     * a sidestep to grab". Light rubble is not enough to justify breaking the line the player drew.
     */
    static final int MIN_REACTIVE_COVER = 2;

    /** Where one marine will stand, and whether it is a position (a trench or a building post) rather than open ground. */
    public record Place(long member, int x, int y, boolean position) { }

    private LinePlacement() { }

    /** Member {@code ordinal} of {@code count}'s share of the line, a cell behind it. */
    static int[] share(FrontageLine line, int ordinal, int count) {
        float[] share = line.pointAt((ordinal + 0.5f) / count, -REARWARD_OFFSET);
        return new int[]{Math.round(share[0]), Math.round(share[1])};
    }

    /**
     * Cover the cell gives against fire from the line's front. Cover is held per compass facing,
     * so a front between two facings weighs each by the share of the front it faces; a front
     * square to one facing reads that facing alone.
     */
    static float frontCover(Probe probe, int x, int y, FrontageLine line) {
        float fx = line.frontX();
        float fy = line.frontY();
        float ax = Math.abs(fx);
        float ay = Math.abs(fy);
        if (ax + ay < 1e-6f) return 0f;
        float across = ax < 1e-3f ? 0f : probe.cover(x, y, fx > 0f ? 8 : -8, 0);
        float down = ay < 1e-3f ? 0f : probe.cover(x, y, 0, fy > 0f ? 8 : -8);
        return (ax * across + ay * down) / (ax + ay);
    }

    /**
     * Open-ground cells for the members a line gave no position. Parallel to {@code cells}, the
     * squad's members available to plan in squad order: each slot whose cell is -1 is given the
     * cell with the most cover against the front within {@link #COVER_SEARCH_RADIUS} of its
     * share, nearest the share among equals, on the line's band, no more than
     * {@link #MAX_AHEAD} in front of it, with a line of fire out along the front and free of
     * {@code taken}; with no covered cell, the nearest free walkable cell of the band to the
     * share. A slot whose {@code previous} cell is still covered, free and on the band keeps it,
     * so a marine is not marched along the line each time a squadmate's choice shifts. Slots
     * holding a position stay -1. Every cell handed out joins {@code taken}.
     *
     * <p>With a believed threat, a slot whose cell gives no cover at all against it moves to the
     * most covered cell against it near its share, if one gives {@link #MIN_REACTIVE_COVER} or
     * more and has a line of fire to it, nearest his cell among equals ({@link #reactiveCell}).
     * Such a cell is kept while the threat it answered still stands and it still covers him.
     *
     * @param count how many shares the line is cut into
     * @param previous each slot's open cell last tick ({@code y * width + x}), or -1
     * @param threatX the squad's believed primary threat cell, or -1 for none
     */
    static int[] openCells(FrontageLine line, int count, int[] cells, int[] previous, int width,
                           Probe probe, Set<Integer> taken, int threatX, int threatY) {
        boolean threat = threatX >= 0 && threatY >= 0;
        int[] open = new int[cells.length];
        Arrays.fill(open, -1);
        for (int i = 0; i < cells.length; i++) {
            int cell = previous == null ? -1 : previous[i];
            if (cells[i] >= 0 || cell < 0 || taken.contains(cell)) continue;
            int x = cell % width;
            int y = cell / width;
            if (!admits(line, x, y, probe)) continue;
            boolean front = frontCover(probe, x, y, line) > 0f && seesFront(line, x, y, probe);
            boolean answered = threat && threatCover(probe, x, y, threatX, threatY) >= MIN_REACTIVE_COVER
                    && probe.lineOfFire(x, y, threatX, threatY);
            if (front || answered) {
                open[i] = cell;
                taken.add(cell);
            }
        }
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] >= 0 || open[i] >= 0) continue;
            int[] share = share(line, i, Math.max(count, i + 1));
            int cell = coveredCell(line, share, width, probe, taken);
            if (cell < 0) cell = nearestFree(line, share, width, probe, taken);
            open[i] = cell;
            if (cell >= 0) taken.add(cell);
        }
        if (!threat) return open;
        for (int i = 0; i < cells.length; i++) {
            int cell = open[i];
            if (cell < 0 || threatCover(probe, cell % width, cell / width, threatX, threatY) > 0) continue;
            int[] share = share(line, i, Math.max(count, i + 1));
            int better = reactiveCell(line, share, cell, width, probe, taken, threatX, threatY);
            if (better < 0) continue;
            taken.remove(cell);
            taken.add(better);
            open[i] = better;
        }
        return open;
    }

    /** Cover the cell gives against fire from the believed threat, by the one facing a shot from it reads. */
    static int threatCover(Probe probe, int x, int y, int threatX, int threatY) {
        if (x == threatX && y == threatY) return 0;
        return probe.cover(x, y, threatX - x, threatY - y);
    }

    /**
     * The most covered cell against the threat near {@code share}, on the line's ground, free, with
     * a line of fire to the threat and at least {@link #MIN_REACTIVE_COVER}; nearest {@code from}
     * among equals, so the sidestep is short. -1 when there is none.
     */
    private static int reactiveCell(FrontageLine line, int[] share, int from, int width, Probe probe,
                                    Set<Integer> taken, int threatX, int threatY) {
        int r = COVER_SEARCH_RADIUS;
        int fromX = from % width;
        int fromY = from / width;
        int best = -1;
        int bestCover = MIN_REACTIVE_COVER - 1;
        int bestDistance = Integer.MAX_VALUE;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int x = share[0] + dx;
                int y = share[1] + dy;
                if (x < 0 || y < 0 || x >= width || !admits(line, x, y, probe)) continue;
                int cell = y * width + x;
                if (taken.contains(cell)) continue;
                int cover = threatCover(probe, x, y, threatX, threatY);
                if (cover < MIN_REACTIVE_COVER) continue;
                int distance = (x - fromX) * (x - fromX) + (y - fromY) * (y - fromY);
                if (cover < bestCover || (cover == bestCover && (distance > bestDistance
                        || (distance == bestDistance && cell > best)))) continue;
                if (!probe.lineOfFire(x, y, threatX, threatY)) continue;
                best = cell;
                bestCover = cover;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The most covered admissible cell near {@code share} that sees the front, or -1. */
    private static int coveredCell(FrontageLine line, int[] share, int width, Probe probe, Set<Integer> taken) {
        int r = COVER_SEARCH_RADIUS;
        int best = -1;
        float bestCover = 0f;
        int bestDistance = Integer.MAX_VALUE;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int distance = dx * dx + dy * dy;
                if (distance > r * r) continue;
                int x = share[0] + dx;
                int y = share[1] + dy;
                if (x < 0 || y < 0 || x >= width || !admits(line, x, y, probe)) continue;
                int cell = y * width + x;
                if (taken.contains(cell)) continue;
                float cover = frontCover(probe, x, y, line);
                if (cover <= 0f || cover < bestCover) continue;
                if (cover == bestCover && (distance > bestDistance
                        || (distance == bestDistance && cell > best))) continue;
                // The line of fire last: it is the one test that walks the grid.
                if (!seesFront(line, x, y, probe)) continue;
                best = cell;
                bestCover = cover;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The nearest free walkable cell of the band to {@code share}, or -1 when the band has none. */
    private static int nearestFree(FrontageLine line, int[] share, int width, Probe probe, Set<Integer> taken) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        int reach = FrontageLine.BAND;
        for (int y = share[1] - reach; y <= share[1] + reach; y++) {
            for (int x = share[0] - reach; x <= share[0] + reach; x++) {
                if (x < 0 || y < 0 || x >= width || !probe.walkable(x, y) || !line.covers(x, y)) continue;
                int cell = y * width + x;
                if (taken.contains(cell)) continue;
                int dx = x - share[0];
                int dy = y - share[1];
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
        }
        return best;
    }

    private static boolean admits(FrontageLine line, int x, int y, Probe probe) {
        return probe.walkable(x, y) && line.covers(x, y) && line.ahead(x, y) <= MAX_AHEAD;
    }

    private static boolean seesFront(FrontageLine line, int x, int y, Probe probe) {
        return probe.lineOfFire(x, y, x + line.frontX() * FRONT_PROBE, y + line.frontY() * FRONT_PROBE);
    }

    /**
     * Where every member of {@code squad} available to plan would stand if given {@code line} now:
     * the claims' own dry run, positions and open ground alike.
     */
    public static List<Place> preview(BattleView sim, Squad squad, FrontageLine line) {
        List<Long> members = FightingPositionClaims.availableMembers(squad, sim);
        if (members.isEmpty()) return List.of();
        FightingPositionClaims claims = sim.fightingPositionClaims();
        int[] cells = claims.previewLine(sim, squad, line, members);
        int[] open = claims.previewOpen(sim, squad, line, members, cells);
        int width = sim.getGrid().getWidth();
        List<Place> places = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            long member = members.get(i);
            if (cells[i] >= 0) {
                places.add(new Place(member, cells[i] % width, cells[i] / width, true));
            } else if (open[i] >= 0) {
                places.add(new Place(member, open[i] % width, open[i] / width, false));
            } else {
                int[] share = share(line, i, Math.max(1, squad.autonomousMemberCount(sim)));
                places.add(new Place(member, share[0], share[1], false));
            }
        }
        return places;
    }
}
