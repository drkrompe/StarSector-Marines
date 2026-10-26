package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Aperture;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who holds which {@link FightingPositionIndex fighting-position} cell. Claims are by marine,
 * exclusive across squads, and decided once per tick in the serial phase between the squad
 * replan and the parallel unit dispatch ({@link #tick}); during dispatch the maps are only
 * read, so the answer a marine gets does not depend on which worker asks first. The maps are
 * immutable snapshots published through volatile fields, and the registry belongs to one
 * simulation, so concurrent simulations in a JVM share nothing.
 *
 * <p>Only squads whose current plan step is a {@link DefendArea} on their standing player or
 * commander area order take part. Each squad's marines fill the free position cells nearest
 * the area's centre, preferring cells with cover toward the squad's believed threat, grown
 * outward from one anchor cell so the squad takes a contiguous block and fire teams (adjacent
 * in member order) stand together. A marine whose previous cell is still valid and unclaimed
 * keeps it, because moving would reset the dig-in clock; retention is settled for every squad
 * before anyone is given a new cell, so a lower-numbered squad cannot take a cell a neighbour
 * is already standing in. Marines left without a cell get no entry and use the ordinary
 * search. With {@link DigInSystem#isEnabled()} off, or no positions on the map, no claims
 * exist and {@link DefendArea} behaves exactly as it did, except for a drawn line: the window
 * and door posts of buildings it crosses are positions of its own, and need no trench.
 *
 * <p>See the fighting-positions section of {@code ai-nouns.md}.
 */
public final class FightingPositionClaims {

    /** What the allocator needs to know about the ground; a seam so it can be tested on a bare grid. */
    public interface Probe {
        boolean walkable(int x, int y);

        /** Cover at the cell against fire from {@code (fromDx, fromDy)}; {@code (0, 0)} asks for omnidirectional cover. */
        int cover(int x, int y, int fromDx, int fromDy);

        /** Whether a shot from the centre of the first cell reaches the point; a bare test grid has no walls. */
        default boolean lineOfFire(int x, int y, float toX, float toY) {
            return true;
        }
    }

    /**
     * One squad's ordered circle and the threat it believes in ({@code threatX < 0}: none). A
     * line order also carries the drawn {@link FrontageLine}, and then the line's band rather
     * than the circle is the ground its positions are taken from, and the window and door posts
     * of the buildings it crosses: {@code posts} maps each post's {@link #key} to a run id of its
     * own building, numbered past the map's trench runs, so a building is filled as one run.
     */
    public record Order(int centerX, int centerY, int radius, int threatX, int threatY,
                        int runId, FrontageLine line, Map<Long, Integer> posts) {
        /** An order that may use any position run inside the circle. */
        public Order(int centerX, int centerY, int radius, int threatX, int threatY) {
            this(centerX, centerY, radius, threatX, threatY, -1);
        }

        public Order(int centerX, int centerY, int radius, int threatX, int threatY, int runId) {
            this(centerX, centerY, radius, threatX, threatY, runId, null);
        }

        public Order(int centerX, int centerY, int radius, int threatX, int threatY,
                     int runId, FrontageLine line) {
            this(centerX, centerY, radius, threatX, threatY, runId, line, Map.of());
        }

        /** The run a cell belongs to: a trench run, else a building post's run, else -1. */
        int runAt(FightingPositionIndex index, int x, int y) {
            int run = index == null ? -1 : index.runIdAt(x, y);
            return run >= 0 ? run : posts.getOrDefault(key(x, y), -1);
        }

        /** Whether the order may take the cell: a position cell inside its ground, on its run if it names one. */
        boolean admits(FightingPositionIndex index, int x, int y) {
            int run = runAt(index, x, y);
            boolean inGround = line != null ? line.covers(x, y) : inside(x, y);
            return run >= 0 && inGround && (runId < 0 || runId == run);
        }

        /**
         * Cover a cell gives against the line's front, which the player drew and which outranks
         * whatever the squad happens to believe; else against the believed threat, else all round.
         */
        int cover(Probe probe, int x, int y) {
            if (line != null) return Math.round(LinePlacement.frontCover(probe, x, y, line));
            if (hasThreat()) return probe.cover(x, y, threatX - x, threatY - y);
            return probe.cover(x, y, 0, 0);
        }

        boolean hasThreat() {
            return threatX >= 0 && threatY >= 0;
        }

        boolean inside(int x, int y) {
            int dx = x - centerX;
            int dy = y - centerY;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private volatile Map<Long, int[]> byMember = Map.of();
    /** The line and fire teams each line-holding squad was cut for last tick; tick-phase only. */
    private Map<Integer, LineCut> lineCuts = Map.of();

    private record LineCut(FrontageLine line, List<Integer> teams) { }
    private volatile Map<Integer, Long> byCell = Map.of();
    /** Each line-holding squad's building posts, kept until its line or the walls change; tick-phase only. */
    private final Map<Integer, LinePosts> linePosts = new HashMap<>();

    private record LinePosts(FrontageLine line, long revision, Map<Long, Integer> posts) { }

    /**
     * Where each line-holding marine with no position stands on open ground ({@link LinePlacement#openCells}),
     * settled here with the positions so two squadmates never pick the same sandbag; and the
     * reverse lookup. Published with the claims.
     */
    private volatile Map<Long, int[]> openByMember = Map.of();
    private volatile Map<Integer, Long> openByCell = Map.of();

    /** A cell as a map key; posts are keyed this way because the order does not know the map's width. */
    static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /** The cell this marine holds, or {@code null}. The array is shared; do not modify it. */
    public int[] cellOf(long member) {
        return byMember.get(member);
    }

    /** The open-ground cell this line-holding marine was given, or {@code null}. The array is shared; do not modify it. */
    public int[] openCellOf(long member) {
        return openByMember.get(member);
    }

    /** Whether a marine other than {@code member} holds this cell, as a position or as his place on a line. */
    public boolean isClaimedByOther(int x, int y, int width, long member) {
        int cell = y * width + x;
        Long holder = byCell.get(cell);
        if (holder == null) holder = openByCell.get(cell);
        return holder != null && holder != member;
    }

    public boolean isEmpty() {
        return byMember.isEmpty();
    }

    /** Recomputes every claim. Serial phase only: after the squad replan, before unit dispatch. */
    public void tick(BattleView sim) {
        FightingPositionIndex index = DigInSystem.isEnabled() ? sim.fightingPositions() : null;
        // Without trench runs only a drawn line has anything to take, from the buildings it crosses.
        boolean trenches = index != null && index.runCount() > 0;
        int width = sim.getGrid().getWidth();
        Probe probe = probe(sim);

        List<Squad> squads = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.isDroneSquad() || squad.isMechSquad() || squad.aliveMembers <= 0) continue;
            if (!trenches && sim.frontageLineOf(squad.id) == null) continue;
            if (defendArea(squad) != null) squads.add(squad);
        }
        Set<Integer> lineSquads = new HashSet<>();
        for (Squad squad : squads) if (sim.frontageLineOf(squad.id) != null) lineSquads.add(squad.id);
        linePosts.keySet().retainAll(lineSquads);
        if (squads.isEmpty()) {
            publish(Map.of(), Map.of());
            publishOpen(Map.of(), Map.of());
            return;
        }
        squads.sort(Comparator.comparingInt(s -> s.id));

        Map<Long, int[]> previous = byMember;
        Map<Long, int[]> previousOpen = openByMember;
        int n = squads.size();
        boolean[] recuts = new boolean[n];
        long[][] members = new long[n][];
        Order[] orders = new Order[n];
        int[][] cells = new int[n][];
        int[][] teams = new int[n][];
        List<List<Integer>> squadTeams = new ArrayList<>();
        Map<Integer, LineCut> nextLineCuts = new HashMap<>();
        Set<Integer> claimed = new HashSet<>();
        for (int s = 0; s < n; s++) {
            Squad squad = squads.get(s);
            DefendArea area = defendArea(squad);
            int[] threat = threatOf(squad);
            FrontageLine line = sim.frontageLineOf(squad.id);
            orders[s] = new Order(area.centerX(), area.centerY(), area.radius(), threat[0], threat[1],
                    occupyRun(sim, squad), line,
                    line == null ? Map.of() : postsFor(squad.id, line, firstPostRun(index), sim));
            List<Long> available = availableMembers(squad, sim);
            squadTeams.add(squadTeams(squad, sim));
            int size = available.size();
            members[s] = new long[size];
            cells[s] = new int[size];
            teams[s] = new int[size];
            int[] before = new int[size];
            // A line is cut per fire team, so when the squad's teams change — one lands, or one is
            // lost — the whole cut is made again rather than kept: a team that took the whole line
            // while it was alone would otherwise hold the newcomers' share for good. A new line is
            // cut afresh too, so it is the cut its preview showed rather than the old line's
            // cells wherever the two bands overlap.
            LineCut cut = orders[s].line() == null ? null : new LineCut(orders[s].line(), squadTeams.get(s));
            boolean recut = cut != null && !cut.equals(lineCuts.get(squad.id));
            if (cut != null) nextLineCuts.put(squad.id, cut);
            recuts[s] = recut;
            for (int i = 0; i < size; i++) {
                long member = available.get(i);
                members[s][i] = member;
                teams[s][i] = sim.squad().fireTeamIndex(member);
                int[] held = previous.get(member);
                before[i] = held == null || recut ? -1 : held[1] * width + held[0];
            }
            retain(index, width, orders[s], probe, before, cells[s], claimed);
        }
        for (int s = 0; s < n; s++) {
            if (orders[s].line() != null) {
                fillLine(index, width, orders[s], probe, cells[s], teams[s], squadTeams.get(s), claimed,
                        along(orders[s].line(), members[s], sim));
            } else {
                fill(index, width, orders[s], probe, cells[s], claimed);
            }
        }

        Map<Long, int[]> nextByMember = new HashMap<>();
        Map<Integer, Long> nextByCell = new HashMap<>();
        for (int s = 0; s < n; s++) {
            for (int i = 0; i < members[s].length; i++) {
                int cell = cells[s][i];
                if (cell < 0) continue;
                nextByMember.put(members[s][i], new int[]{cell % width, cell / width});
                nextByCell.put(cell, members[s][i]);
            }
        }
        // Open ground last, once every squad's positions are out, so a line's leftover men take
        // cover round the trenches rather than in them.
        Map<Long, int[]> nextOpenByMember = new HashMap<>();
        Map<Integer, Long> nextOpenByCell = new HashMap<>();
        for (int s = 0; s < n; s++) {
            FrontageLine line = orders[s].line();
            if (line == null) continue;
            int[] before = new int[members[s].length];
            for (int i = 0; i < before.length; i++) {
                int[] held = recuts[s] ? null : previousOpen.get(members[s][i]);
                before[i] = held == null ? -1 : held[1] * width + held[0];
            }
            int count = Math.max(1, squads.get(s).autonomousMemberCount(sim));
            int[] open = LinePlacement.openCells(line, count, cells[s], before, projections(line, members[s], sim),
                    width, probe, claimed,
                    orders[s].threatX(), orders[s].threatY());
            for (int i = 0; i < open.length; i++) {
                if (open[i] < 0) continue;
                nextOpenByMember.put(members[s][i], new int[]{open[i] % width, open[i] / width});
                nextOpenByCell.put(open[i], members[s][i]);
            }
        }
        lineCuts = nextLineCuts;
        publish(nextByMember, nextByCell);
        publishOpen(nextOpenByMember, nextOpenByCell);
    }

    /**
     * The cells {@code squad} would claim if it were given {@code line} now, one per member of
     * {@code members} (the squad's members available to plan, in squad order), or -1 for a member
     * the line has no position for. A dry run of {@link #tick} for one squad, for the order's
     * preview: the same cut, the same posts and the same rules, against every cell another
     * squad holds. Nothing is published.
     */
    public int[] previewLine(BattleView sim, Squad squad, FrontageLine line, List<Long> members) {
        FightingPositionIndex index = DigInSystem.isEnabled() ? sim.fightingPositions() : null;
        int[] threat = threatOf(squad);
        int[] circle = line.enclosingCircle();
        Order order = new Order(circle[0], circle[1], circle[2], threat[0], threat[1], -1, line,
                buildingPosts(line, firstPostRun(index), sim));
        Set<Long> own = new HashSet<>(members);
        Set<Integer> claimed = new HashSet<>();
        for (Map.Entry<Integer, Long> held : byCell.entrySet()) {
            if (!own.contains(held.getValue())) claimed.add(held.getKey());
        }
        int[] cells = new int[members.size()];
        int[] teams = new int[members.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = -1;
            teams[i] = sim.squad().fireTeamIndex(members.get(i));
        }
        long[] ids = new long[members.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = members.get(i);
        fillLine(index, sim.getGrid().getWidth(), order, probe(sim), cells, teams, squadTeams(squad, sim), claimed,
                along(line, ids, sim));
        return cells;
    }

    /**
     * The open-ground cells {@code squad}'s members would take on {@code line} now, parallel to
     * {@code members}, given the positions {@code cells} {@link #previewLine} found for them (-1
     * where a member holds a position). The tick's own open-ground pass for a freshly cut line,
     * against every cell another squad holds.
     */
    public int[] previewOpen(BattleView sim, Squad squad, FrontageLine line, List<Long> members, int[] cells) {
        Set<Long> own = new HashSet<>(members);
        Set<Integer> taken = new HashSet<>();
        for (Map.Entry<Integer, Long> held : byCell.entrySet()) {
            if (!own.contains(held.getValue())) taken.add(held.getKey());
        }
        for (Map.Entry<Integer, Long> held : openByCell.entrySet()) {
            if (!own.contains(held.getValue())) taken.add(held.getKey());
        }
        for (int cell : cells) if (cell >= 0) taken.add(cell);
        int count = Math.max(1, squad.autonomousMemberCount(sim));
        int[] threat = threatOf(squad);
        long[] ids = new long[members.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = members.get(i);
        return LinePlacement.openCells(line, count, cells, null, projections(line, ids, sim), sim.getGrid().getWidth(),
                probe(sim), taken,
                threat[0], threat[1]);
    }

    /** Where each member stands along {@code line}, parallel to {@code members}. */
    private static float[] along(FrontageLine line, long[] members, BattleView sim) {
        float[] along = new float[members.length];
        for (int i = 0; i < members.length; i++) {
            long unit = sim.resolveUnit(members[i]);
            along[i] = unit == 0L ? 0f : line.along(sim.world().cellX(unit), sim.world().cellY(unit));
        }
        return along;
    }

    /** Where each member stands along {@code line}, unclamped ({@link FrontageLine#projection}), parallel to {@code members}. */
    private static float[] projections(FrontageLine line, long[] members, BattleView sim) {
        float[] along = new float[members.length];
        for (int i = 0; i < members.length; i++) {
            long unit = sim.resolveUnit(members[i]);
            along[i] = unit == 0L ? 0f : line.projection(sim.world().x(unit), sim.world().y(unit));
        }
        return along;
    }

    /** The squad's members who take part in planning, in squad order: the ones a line places. */
    public static List<Long> availableMembers(Squad squad, BattleView sim) {
        List<Long> available = new ArrayList<>();
        for (int i = 0, count = sim.squadMemberCount(squad.id); i < count; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (squad.availableToPlan(member, sim)) available.add(member);
        }
        return available;
    }

    /**
     * Every fire team a squad's line is cut for, ascending: the team of every live member, and the
     * teams still to land, so a squad arriving in lifts has its later teams' stretches kept for
     * them rather than taken by whoever touched down first.
     */
    private static List<Integer> squadTeams(Squad squad, BattleView sim) {
        TreeSet<Integer> everyTeam = new TreeSet<>();
        for (int i = 0, count = sim.squadMemberCount(squad.id); i < count; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.resolveUnit(member) != 0L) everyTeam.add(sim.squad().fireTeamIndex(member));
        }
        int expected = Math.max(squad.expectedSize, squad.originalSize);
        for (int t = 0; t * Squad.FIRE_TEAM_SIZE < expected; t++) everyTeam.add(t);
        return new ArrayList<>(everyTeam);
    }

    /** The squad's believed primary threat cell, or {@code {-1, -1}}. */
    private static int[] threatOf(Squad squad) {
        SquadContactPicture picture = squad.contactPicture;
        boolean threat = picture != null && picture.hasContacts()
                && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0;
        return threat ? new int[]{picture.primaryCellX(), picture.primaryCellY()} : new int[]{-1, -1};
    }

    /** Building runs are numbered past the trench runs, so the two never share an id. */
    private static int firstPostRun(FightingPositionIndex index) {
        return index != null ? index.runCount() : 0;
    }

    private static Probe probe(BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        return new Probe() {
            @Override public boolean walkable(int x, int y) {
                return grid.inBounds(x, y) && grid.isWalkable(x, y);
            }

            @Override public int cover(int x, int y, int fromDx, int fromDy) {
                if (fromDx == 0 && fromDy == 0) {
                    return grid.getCoverAt(x, y) + sim.getDoodadCoverAt(x, y);
                }
                return grid.getCoverAt(x, y, fromDx, fromDy)
                        + sim.getDoodadCoverAt(x, y, fromDx, fromDy);
            }

            @Override public boolean lineOfFire(int x, int y, float toX, float toY) {
                float endX = Math.max(0.5f, Math.min(grid.getWidth() - 0.5f, toX + 0.5f));
                float endY = Math.max(0.5f, Math.min(grid.getHeight() - 0.5f, toY + 0.5f));
                return grid.hasLineOfFire(x + 0.5f, y + 0.5f, endX, endY);
            }
        };
    }

    private Map<Long, Integer> postsFor(int squadId, FrontageLine line, int firstRun, BattleView sim) {
        long revision = sim.getGrid().topologyRevision();
        LinePosts cached = linePosts.get(squadId);
        if (cached != null && cached.line().equals(line) && cached.revision() == revision) return cached.posts();
        Map<Long, Integer> posts = buildingPosts(line, firstRun, sim);
        linePosts.put(squadId, new LinePosts(line, revision, posts));
        return posts;
    }

    /**
     * The posts a drawn line takes from held buildings: each building's window and door stances
     * inside the line's band whose opening faces the line's front, one run id per building from
     * {@code firstRun}. A building is held from inside, so its posts are its frontage stances
     * rather than the line's own cells, and a window facing the rear is no post on this line.
     */
    static Map<Long, Integer> buildingPosts(FrontageLine line, int firstRun, BattleView sim) {
        TacticalMap map = sim.getTacticalMap();
        if (map == null) return Map.of();
        int reach = FrontageLine.BAND + DefenseFrontage.COMPOUND_MARGIN + 1;
        int minX = Math.min(line.x0(), line.x1()) - reach;
        int maxX = Math.max(line.x0(), line.x1()) + reach;
        int minY = Math.min(line.y0(), line.y1()) - reach;
        int maxY = Math.max(line.y0(), line.y1()) + reach;
        Map<Long, Integer> posts = new HashMap<>();
        int run = firstRun;
        for (TacticalNode node : map.all()) {
            if (node.kind != TacticalNode.Kind.INNER_POSITION) continue;
            if (node.right < minX || node.left > maxX || node.bottom < minY || node.top > maxY) continue;
            boolean any = false;
            for (Aperture aperture : DefenseFrontage.forStructure(node, sim)) {
                if (aperture.facing().dx * line.frontX() + aperture.facing().dy * line.frontY() <= 0f) continue;
                if (!line.covers(aperture.stanceX(), aperture.stanceY())) continue;
                if (posts.putIfAbsent(key(aperture.stanceX(), aperture.stanceY()), run) == null) any = true;
            }
            if (any) run++;
        }
        return posts;
    }

    private void publish(Map<Long, int[]> members, Map<Integer, Long> cells) {
        byMember = members;
        byCell = cells;
    }

    private void publishOpen(Map<Long, int[]> members, Map<Integer, Long> cells) {
        openByMember = members;
        openByCell = cells;
    }

    /**
     * The run this squad's claims are scoped to: the run a player's occupy order named, else the run its commander's
     * garrison order names, else -1 for an ordinary area.
     */
    private static int occupyRun(BattleView sim, Squad squad) {
        int run = sim.occupyRunOf(squad.id);
        if (run >= 0) return run;
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        return assignment == null ? -1 : assignment.occupyRunId();
    }

    /** The {@link DefendArea} the squad is executing on its standing area order, or {@code null}. */
    private static DefendArea defendArea(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA
                || assignment.targetRadiusCells() < 1) {
            return null;
        }
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return null;
        SquadPlan.Step step = plan.currentStep();
        if (step == null || !(step.action instanceof DefendArea area)) return null;
        return area.centerX() == assignment.targetCellX()
                && area.centerY() == assignment.targetCellY()
                && area.radius() == assignment.targetRadiusCells() ? area : null;
    }

    /**
     * Keeps each member's previous cell ({@code cell index = y * width + x}, or -1) when it is
     * still a walkable position cell inside the circle, still has cover toward the believed
     * threat, and nobody else has it. Writes survivors into {@code cells} and {@code claimed}.
     */
    static void retain(FightingPositionIndex index, int width, Order order, Probe probe,
                       int[] previous, int[] cells, Set<Integer> claimed) {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = -1;
            int cell = previous[i];
            if (cell < 0 || claimed.contains(cell)) continue;
            int x = cell % width;
            int y = cell / width;
            if (!order.admits(index, x, y) || !probe.walkable(x, y)) continue;
            // A line's front is fixed with the order, so a held cell keeps its cover until the ground changes.
            if (order.line() == null && order.hasThreat()
                    && probe.cover(x, y, order.threatX() - x, order.threatY() - y) <= 0) continue;
            cells[i] = cell;
            claimed.add(cell);
        }
    }

    /** {@link #fillLine} with the squad's teams read off the members being placed. */
    static void fillLine(FightingPositionIndex index, int width, Order order, Probe probe,
                         int[] cells, int[] teams, Set<Integer> claimed) {
        fillLine(index, width, order, probe, cells, teams,
                new ArrayList<>(new TreeSet<>(Arrays.stream(teams).boxed().toList())), claimed);
    }

    /**
     * The line order's {@link #fill}: the drawn line is cut into one stretch per fire team, in
     * team order from its start, and each team takes free position cells near its own stretch. A
     * drag along one long trench therefore spreads the teams over its length rather than packing
     * them at one end, and a drag across two trenches puts teams in each.
     *
     * <p>A team stays on one run while that run has room: runs are tried nearest the team's
     * stretch first, covered runs before uncovered ones, and a team spills onto the next run
     * only when the first is full, and then only onto a run that reaches its own stretch: a team
     * that fills a small building does not take the next team's. Fire teams fight as teams, so
     * splitting one across two trenches to stand a cell nearer its stretch is the wrong trade.
     * Within its runs a team spreads over its stretch: each man takes the free cell nearest an
     * even share of it, so how far apart they stand follows how long the line was drawn.
     * Members left without a cell stay -1 and hold the line on ordinary ground.
     *
     * @param teams      each member's fire-team index, parallel to {@code cells}
     * @param squadTeams every team the squad has, ascending, including teams with nobody to place
     *                   yet: the line is cut for the whole squad, so a team still forming up does
     *                   not leave its stretch to be taken by the others
     */
    static void fillLine(FightingPositionIndex index, int width, Order order, Probe probe,
                         int[] cells, int[] teams, List<Integer> squadTeams, Set<Integer> claimed) {
        fillLine(index, width, order, probe, cells, teams, squadTeams, claimed, null);
    }

    /**
     * As above, with {@code along} each member's place along the line ({@link FrontageLine#along}
     * of where he stands, parallel to {@code cells}), or {@code null} to hand cells out in member
     * order.
     */
    static void fillLine(FightingPositionIndex index, int width, Order order, Probe probe,
                         int[] cells, int[] teams, List<Integer> squadTeams, Set<Integer> claimed,
                         float[] along) {
        FrontageLine line = order.line();
        if (squadTeams.isEmpty()) return;
        float length = Math.max(1f, line.length());
        int teamCount = squadTeams.size();

        List<int[]> candidates = new ArrayList<>();
        int band = FrontageLine.BAND;
        for (int y = Math.min(line.y0(), line.y1()) - band; y <= Math.max(line.y0(), line.y1()) + band; y++) {
            for (int x = Math.min(line.x0(), line.x1()) - band; x <= Math.max(line.x0(), line.x1()) + band; x++) {
                if (x < 0 || y < 0 || x >= width || !order.admits(index, x, y) || !probe.walkable(x, y)) continue;
                int cell = y * width + x;
                if (claimed.contains(cell)) continue;
                candidates.add(new int[]{cell, order.cover(probe, x, y), order.runAt(index, x, y)});
            }
        }
        if (candidates.isEmpty()) return;

        for (int k = 0; k < teamCount; k++) {
            int team = squadTeams.get(k);
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) if (teams[i] == team && cells[i] < 0) slots.add(i);
            if (slots.isEmpty()) continue;
            float from = (float) k / teamCount;
            float to = (float) (k + 1) / teamCount;
            float mid = (from + to) * 0.5f;
            List<int[]> free = new ArrayList<>();
            for (int[] candidate : candidates) if (!claimed.contains(candidate[0])) free.add(candidate);
            if (free.isEmpty()) return;
            Comparator<int[]> nearStretch = Comparator
                    .<int[]>comparingDouble(c -> {
                        float t = line.along(c[0] % width, c[0] / width);
                        return Math.max(0f, Math.max(from - t, t - to)) * length;
                    })
                    .thenComparingDouble(c -> Math.abs(line.along(c[0] % width, c[0] / width) - mid))
                    .thenComparingInt(c -> c[0]);
            // Each run's best cell for this team decides the run's place in the queue.
            Map<Integer, int[]> bestOfRun = new HashMap<>();
            Map<Integer, Boolean> coveredRun = new HashMap<>();
            for (int[] c : free) {
                bestOfRun.merge(c[2], c, (a, b) -> nearStretch.compare(a, b) <= 0 ? a : b);
                if (c[1] > 0) coveredRun.put(c[2], true);
            }
            free.sort(Comparator
                    .<int[]>comparingInt(c -> coveredRun.getOrDefault(c[2], false) ? 0 : 1)
                    .thenComparing(c -> bestOfRun.get(c[2]), nearStretch)
                    .thenComparingInt(c -> c[1] > 0 ? 0 : 1)
                    .thenComparing(nearStretch));
            // The team's home is the first run in that order lying within a band's width of its
            // stretch; a run further off is another team's ground, and a team with nothing near
            // holds its stretch in the open rather than crowding into somebody else's trench.
            // Beyond its home a team takes only runs that reach its stretch, so one that fills a
            // small building does not spill into the next team's and leave that team short.
            int home = -1;
            for (int[] c : free) {
                if (nearStretchGap(bestOfRun.get(c[2]), line, width, from, to) * length <= FrontageLine.BAND) {
                    home = c[2];
                    break;
                }
            }
            if (home < 0) continue;
            // The home run alone while it has room for the whole team. A team it cannot hold takes
            // all of it and reaches onto runs that touch its stretch, in the order sorted above.
            final int homeRun = home;
            List<int[]> pool = new ArrayList<>();
            for (int[] c : free) if (c[2] == homeRun) pool.add(c);
            if (pool.stream().anyMatch(c -> c[1] > 0)) pool.removeIf(c -> c[1] <= 0);
            List<int[]> chosen = new ArrayList<>();
            int n = slots.size();
            if (pool.size() <= n) {
                chosen.addAll(pool);
                pool.clear();
                for (int[] c : free) {
                    if (chosen.size() >= n) break;
                    if (c[2] != homeRun && nearStretchGap(bestOfRun.get(c[2]), line, width, from, to) == 0f) {
                        chosen.add(c);
                    }
                }
            }
            // The men spread over their stretch as far as the drawn line asks: each takes the free
            // cell nearest an even share of it, so a long drag across a trench opens the team out
            // along it and a short one keeps them shoulder to shoulder.
            for (int j = 0; j < n && !pool.isEmpty(); j++) {
                float[] target = line.pointAt(from + (j + 0.5f) / n * (to - from), 0f);
                int pick = 0;
                float best = Float.MAX_VALUE;
                for (int i = 0; i < pool.size(); i++) {
                    int cell = pool.get(i)[0];
                    float dx = cell % width - target[0];
                    float dy = cell / width - target[1];
                    float d = dx * dx + dy * dy;
                    if (d < best || (d == best && cell < pool.get(pick)[0])) {
                        pick = i;
                        best = d;
                    }
                }
                chosen.add(pool.remove(pick));
            }
            // Hand the cells out in the order the men stand along the line, so the leftmost takes
            // the leftmost cell: shorter walks, and nobody crosses a squadmate's path to his post.
            chosen.sort(Comparator.<int[]>comparingDouble(c -> line.along(c[0] % width, c[0] / width))
                    .thenComparingInt(c -> c[0]));
            if (along != null) slots.sort(Comparator.comparingDouble(i -> along[i]));
            for (int j = 0; j < chosen.size(); j++) {
                cells[slots.get(j)] = chosen.get(j)[0];
                claimed.add(chosen.get(j)[0]);
            }
        }
    }

    /** How far, as a fraction of the line, a cell lies outside the stretch {@code [from, to]}. */
    private static float nearStretchGap(int[] cell, FrontageLine line, int width, float from, float to) {
        float t = line.along(cell[0] % width, cell[0] / width);
        return Math.max(0f, Math.max(from - t, t - to));
    }


    /**
     * Gives every member whose {@code cells} entry is -1 a free position cell, if there are
     * enough; the rest stay -1 (overflow). Candidates are covered cells before uncovered ones,
     * then nearest an anchor: the squad's first retained cell, else the best cell by cover and
     * nearness to the circle's centre. The chosen block is handed out in member order along a
     * nearest-neighbour chain that starts at one end of it.
     */
    static void fill(FightingPositionIndex index, int width, Order order, Probe probe,
                     int[] cells, Set<Integer> claimed) {
        int need = 0;
        int anchor = -1;
        for (int cell : cells) {
            if (cell < 0) need++;
            else if (anchor < 0) anchor = cell;
        }
        if (need == 0) return;

        List<int[]> candidates = new ArrayList<>();
        for (int y = order.centerY() - order.radius(); y <= order.centerY() + order.radius(); y++) {
            for (int x = order.centerX() - order.radius(); x <= order.centerX() + order.radius(); x++) {
                if (!order.admits(index, x, y) || !probe.walkable(x, y)) continue;
                int cell = y * width + x;
                if (claimed.contains(cell)) continue;
                int cover = order.hasThreat()
                        ? probe.cover(x, y, order.threatX() - x, order.threatY() - y)
                        : probe.cover(x, y, 0, 0);
                candidates.add(new int[]{cell, cover});
            }
        }
        if (candidates.isEmpty()) return;

        int anchorX;
        int anchorY;
        if (anchor >= 0) {
            anchorX = anchor % width;
            anchorY = anchor / width;
        } else {
            int[] best = candidates.get(0);
            for (int[] candidate : candidates) {
                if (candidate[1] > best[1]
                        || (candidate[1] == best[1] && distanceToCentre(candidate[0], width, order)
                        < distanceToCentre(best[0], width, order))) {
                    best = candidate;
                }
            }
            anchorX = best[0] % width;
            anchorY = best[0] / width;
        }

        final int ax = anchorX;
        final int ay = anchorY;
        candidates.sort(Comparator
                .<int[]>comparingInt(c -> c[1] > 0 ? 0 : 1)
                .thenComparingInt(c -> distanceSq(c[0] % width, c[0] / width, ax, ay))
                .thenComparingInt(c -> c[0]));
        List<int[]> chosen = new ArrayList<>(candidates.subList(0, Math.min(need, candidates.size())));

        // Walk the block from one end so consecutive members are neighbours, not from the
        // anchor in the middle, whose nearest-neighbour hops jump from side to side.
        long sumX = 0;
        long sumY = 0;
        for (int[] c : chosen) {
            sumX += c[0] % width;
            sumY += c[0] / width;
        }
        int size = chosen.size();
        int curX = anchorX;
        int curY = anchorY;
        long farthest = -1;
        for (int[] c : chosen) {
            long dx = (long) (c[0] % width) * size - sumX;
            long dy = (long) (c[0] / width) * size - sumY;
            long d = dx * dx + dy * dy;
            if (d > farthest) {
                farthest = d;
                curX = c[0] % width;
                curY = c[0] / width;
            }
        }
        int member = 0;
        while (!chosen.isEmpty()) {
            int pick = 0;
            int pickDistance = Integer.MAX_VALUE;
            for (int i = 0; i < chosen.size(); i++) {
                int cell = chosen.get(i)[0];
                int d = distanceSq(cell % width, cell / width, curX, curY);
                if (d < pickDistance || (d == pickDistance && cell < chosen.get(pick)[0])) {
                    pick = i;
                    pickDistance = d;
                }
            }
            int cell = chosen.remove(pick)[0];
            curX = cell % width;
            curY = cell / width;
            while (cells[member] >= 0) member++;
            cells[member] = cell;
            claimed.add(cell);
        }
    }

    private static int distanceToCentre(int cell, int width, Order order) {
        return distanceSq(cell % width, cell / width, order.centerX(), order.centerY());
    }

    private static int distanceSq(int ax, int ay, int bx, int by) {
        int dx = ax - bx;
        int dy = ay - by;
        return dx * dx + dy * dy;
    }
}
