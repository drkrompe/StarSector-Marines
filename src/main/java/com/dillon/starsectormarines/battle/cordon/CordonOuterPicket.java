package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The pickets: the outer picket ({@code cordon-nouns.md}, Outer picket), one allied team out in front of each redoubt
 * on the road the swarm comes down, put there to be lost, and the picket line's teams ({@code cordon-nouns.md}, Picket
 * line), a few allied teams in the trench between each pair of redoubts. The outer picket is the line's eyes while the
 * company is still landing and moving up: what it sees the player sees (an allied body casts the side's fog like a
 * Marine), and the battle hears it make contact. Every post holds until it is pressed, then falls back to a redoubt
 * and joins the garrison: an outer post down its road to its own redoubt (most of it dies on the way), a line team
 * along its trench to the nearer one still manned. None of them calls, and nobody is sent for them; all of them are
 * under garrison authority, never in the player's command pool.
 */
public final class CordonOuterPicket {

    /**
     * The outer picket on or off. Experiment (owner, 2026-10-25): on by default; off is the control while the
     * mission's feel is settled. Remove the switch, with the off path, once the owner has seen the picket in play.
     */
    public static final String PROPERTY = "battle.cordon.outerPicket";

    /** Who holds the outer picket, as the command log names it. */
    public static final String ISSUER = "cordon-picket";
    /** Cells in front of its redoubt's front wall a post is wanted. */
    static final int DEPTH = 28;
    /** The nearest a post is put to its redoubt's front wall, where nothing further out will do. */
    static final int MIN_DEPTH = 12;
    /** Cells either side of its redoubt's first street mouth a post may move along its row. */
    static final int ACROSS = 12;
    /** Cells a post keeps from every bore: it watches the front, it is not set down in a bore's mouth. */
    static final int BORE_CLEAR = 26;
    /** Cells round its post the team holds. */
    static final int HOLD = 4;
    /** Cells from the post a swarm body is in contact. */
    static final int CONTACT = 20;
    /** Cells from the post a swarm body presses it. */
    static final int PRESSED = 8;
    /** Ticks between looks. */
    static final int LOOK_TICKS = 30;
    /** Cells of picket course a line team holds: one team a stretch this long, rounded, at least one. UNTUNED. */
    static final int LINE_SPAN = 50;
    /** The most teams one stretch of picket line holds. */
    static final int LINE_MAX = 3;
    /** Cells round its post along the trench a line team holds. */
    static final int LINE_HOLD = 6;

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    private static final class Post {
        final int number;
        /** The redoubt it falls back to: an outer post's own; a line team's, the nearer manned one when it goes. */
        CordonRedoubt redoubt;
        final int x, y, squad, team;
        /** The picket course a line team holds ({@code "1-2"}), or {@code null} for an outer post. */
        final String course;
        int contactTick = -1, backTick = -1, lostTick = -1, joined;
        boolean fallingBack;

        Post(int number, CordonRedoubt redoubt, int x, int y, int squad, int team, String course) {
            this.number = number;
            this.redoubt = redoubt;
            this.x = x;
            this.y = y;
            this.squad = squad;
            this.team = team;
            this.course = course;
        }

        boolean line() { return course != null; }
    }

    private final List<Post> posts;
    private final List<CordonRedoubt> redoubts;

    private CordonOuterPicket(List<Post> posts, List<CordonRedoubt> redoubts) {
        this.posts = posts;
        this.redoubts = List.copyOf(redoubts);
    }

    /** Whether the outer picket stands ({@link #PROPERTY}). */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    /**
     * Stands the pickets: the outer picket when {@code outer} ({@link #enabled}), and the picket line's teams in every
     * laid picket course ({@link #standLine}).
     *
     * @param front +1 where the front is north (high y), -1 where it is south
     */
    public static CordonOuterPicket stand(BattleSimulation sim, CordonArmour armour, List<CordonRedoubt> redoubts,
                                          int front, RiskLevel risk, Random rng, boolean outer, LaidLayout layout,
                                          Random lineRng) {
        List<Post> posts = outer ? outerPosts(sim, armour, redoubts, front, risk, rng) : new ArrayList<>();
        posts.addAll(standLine(sim, redoubts, layout, risk, lineRng));
        return new CordonOuterPicket(posts, redoubts);
    }

    /** Puts a team out in front of each redoubt, at the post {@link #postCell} finds from its first street mouth. */
    private static List<Post> outerPosts(BattleSimulation sim, CordonArmour armour, List<CordonRedoubt> redoubts,
                                         int front, RiskLevel risk, Random rng) {
        List<int[]> bores = new ArrayList<>();
        for (SwarmBore bore : sim.getSwarmBores()) bores.add(new int[]{bore.x(), bore.y()});
        CellTest open = (x, y) -> sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y);
        CellTest street = (x, y) -> {
            CellTopology.GroundKind kind = sim.getTopology().getGroundKind(x, y);
            return kind == CellTopology.GroundKind.STREET || kind == CellTopology.GroundKind.SIDEWALK;
        };
        List<Post> posts = new ArrayList<>();
        for (CordonRedoubt r : redoubts) {
            int mouthX = armour.mouths(sim, r).get(0)[0];
            int frontRow = front > 0 ? r.bottom() : r.top();
            int[] at = postCell(open, street, mouthX, frontRow, front, bores);
            if (at == null) continue;
            List<int[]> cells = CordonGround.clusterNear(sim, at[0], at[1], ColonyMilitia.POST_SIZE);
            if (cells.size() < ColonyMilitia.POST_SIZE) continue;
            int number = posts.size() + 1;
            String name = "Outer picket " + number;
            int squad = ColonyMilitia.seatTeam(sim, cells, name, risk, rng);
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, at[0], at[1], HOLD),
                    CommandAuthority.GARRISON, ISSUER, name + " watches the road in front of redoubt " + r.number());
            posts.add(new Post(number, r, at[0], at[1], squad, sim.squadMemberCount(squad), null));
        }
        return posts;
    }

    /**
     * The picket line's teams ({@code cordon-nouns.md}, Picket line): in each laid picket course, west to east,
     * {@link #lineTeams} teams, each seated at an even share along the course ({@link #lineStops}) on the trench's
     * fighting positions nearest that stop, ordered to man the trench's run there as a holdout's line team mans its
     * step. The garrison's guns are already in the course's nests.
     */
    static List<Post> standLine(BattleSimulation sim, List<CordonRedoubt> redoubts, LaidLayout layout, RiskLevel risk,
                                Random rng) {
        List<Post> posts = new ArrayList<>();
        if (layout == null || redoubts.isEmpty()) return posts;
        NavigationGrid grid = sim.getGrid();
        List<int[]> taken = new ArrayList<>();
        for (Laid laid : layout.pieces()) {
            if (laid.kind() != Laid.Kind.LINE || !laid.isLaid() || !laid.name().startsWith(CordonPlan.PICKET_LINE)) {
                continue;
            }
            String course = laid.name().substring(CordonPlan.PICKET_LINE.length());
            // Where a team can stand on the course: its trench and bags, a fighting position before bare ground.
            List<int[]> standing = new ArrayList<>();
            for (int[] c : laid.cells()) if (grid.inBounds(c[0], c[1]) && grid.isWalkable(c[0], c[1])) standing.add(c);
            if (standing.size() < ColonyMilitia.POST_SIZE) continue;
            // The stretch is the whole course, its wire and barricades too: a team's share of it, not of its trench.
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            for (int[] c : laid.cells()) {
                minX = Math.min(minX, c[0]);
                maxX = Math.max(maxX, c[0]);
            }
            int k = 0;
            for (int stop : lineStops(minX, maxX, lineTeams(maxX - minX + 1))) {
                List<int[]> cells = lineSeats(standing, stop, taken,
                        (x, y) -> sim.fightingPositions().isFightingPositionCell(x, y), ColonyMilitia.POST_SIZE);
                if (cells.size() < ColonyMilitia.POST_SIZE) continue;
                taken.addAll(cells);
                String name = "Picket " + course + " team " + (++k);
                int squad = ColonyMilitia.seatTeam(sim, cells, name, risk, rng);
                int[] at = cells.get(0);
                int run = sim.fightingPositions().runIdAt(at[0], at[1]);
                sim.assignSquadCommand(run >= 0 ? ObjectiveAssignment.defendRun(squad, at[0], at[1], LINE_HOLD, run)
                                : ObjectiveAssignment.defendArea(squad, at[0], at[1], LINE_HOLD),
                        CommandAuthority.GARRISON, ISSUER, name + " mans the picket line");
                posts.add(new Post(k, nearest(redoubts, at[0], at[1]), at[0], at[1], squad,
                        sim.squadMemberCount(squad), course));
            }
        }
        return posts;
    }

    /** How many teams hold a stretch of picket course {@code length} cells long: one per {@link #LINE_SPAN}, 1 to {@link #LINE_MAX}. */
    static int lineTeams(int length) {
        return Math.max(1, Math.min(LINE_MAX, Math.round(length / (float) LINE_SPAN)));
    }

    /** Where along a course from {@code minX} to {@code maxX} each of {@code teams} stands: the middle of its even share. */
    static int[] lineStops(int minX, int maxX, int teams) {
        int[] out = new int[teams];
        float span = (maxX - minX + 1) / (float) teams;
        for (int i = 0; i < teams; i++) out[i] = minX + (int) Math.floor(span * (i + 0.5f));
        return out;
    }

    /**
     * A line team's seats: the {@code size} course cells nearest the column {@code stop} (along the course, then the
     * cell's own row breaking a tie), fighting positions before bare ground, none already {@code taken}. Pure.
     */
    static List<int[]> lineSeats(List<int[]> course, int stop, List<int[]> taken, CellTest position, int size) {
        List<int[]> free = new ArrayList<>();
        for (int[] c : course) {
            boolean used = false;
            for (int[] t : taken) used |= t[0] == c[0] && t[1] == c[1];
            if (!used) free.add(c);
        }
        free.sort(Comparator.<int[]>comparingInt(c -> position.test(c[0], c[1]) ? 0 : 1)
                .thenComparingInt(c -> Math.abs(c[0] - stop)).thenComparingInt(c -> c[1]));
        // Fighting positions first only within reach of the stop: a trench cell across the stretch is no seat.
        List<int[]> near = new ArrayList<>();
        for (int[] c : free) if (Math.abs(c[0] - stop) <= LINE_HOLD) near.add(c);
        List<int[]> pool = near.size() >= size ? near : free;
        pool.sort(Comparator.<int[]>comparingInt(c -> position.test(c[0], c[1]) ? 0 : 1)
                .thenComparingInt(c -> Math.abs(c[0] - stop)).thenComparingInt(c -> c[1]));
        return new ArrayList<>(pool.subList(0, Math.min(size, pool.size())));
    }

    /** The manned redoubt nearest {@code (x, y)}, or the nearest of all where none is manned. */
    static CordonRedoubt nearest(List<CordonRedoubt> redoubts, int x, int y) {
        CordonRedoubt best = null;
        for (CordonRedoubt r : redoubts) {
            boolean better = best == null || r.manned() && !best.manned()
                    || r.manned() == best.manned() && Math.hypot(r.centreX() - x, r.centreY() - y)
                    < Math.hypot(best.centreX() - x, best.centreY() - y);
            if (better) best = r;
        }
        return best;
    }

    /**
     * Where a post stands: on the row {@link #DEPTH} cells in front of the front wall, the cell nearest the mouth's
     * column, a street's first, at least {@link #BORE_CLEAR} from every bore; nearer the wall a row at a time where
     * that row has none, down to {@link #MIN_DEPTH}; {@code null} where no row will do.
     */
    static int[] postCell(CellTest open, CellTest street, int mouthX, int frontRow, int front, List<int[]> bores) {
        for (int depth = DEPTH; depth >= MIN_DEPTH; depth--) {
            int y = frontRow + front * depth;
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k <= 2 * ACROSS; k++) {
                    int x = mouthX + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
                    if (!open.test(x, y) || pass == 0 && !street.test(x, y) || nearBore(x, y, bores)) continue;
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean nearBore(int x, int y, List<int[]> bores) {
        for (int[] b : bores) if (Math.hypot(b[0] - x, b[1] - y) < BORE_CLEAR) return true;
        return false;
    }

    /**
     * Whether a post pressed by the swarm falls back: half its team down with the swarm on it, or outnumbered two to
     * one where it stands.
     */
    static boolean fallsBack(int standing, int team, int pressing) {
        if (standing <= 0 || pressing <= 0) return false;
        return standing * 2 <= team || pressing >= standing * 2;
    }

    /**
     * One tick: contact, the fall back when pressed, and the survivors into the garrison at their redoubt; a line team
     * falls back to the nearest redoubt still manned. Only the outer picket's posts publish beats.
     */
    public void advance(BattleSimulation sim, int tick, Consumer<String> beat) {
        if (posts.isEmpty() || tick % LOOK_TICKS != 0) return;
        int[] contact = new int[posts.size()], pressing = new int[posts.size()];
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            if (sim.identity().faction(unit) != Faction.DEFENDER || !sim.identity().type(unit).isSwarm()) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            for (int p = 0; p < posts.size(); p++) {
                double d = Math.hypot(x - posts.get(p).x, y - posts.get(p).y);
                if (d <= CONTACT) contact[p]++;
                if (d <= PRESSED) pressing[p]++;
            }
        }
        for (int p = 0; p < posts.size(); p++) {
            Post post = posts.get(p);
            if (post.lostTick >= 0 || post.backTick >= 0) continue;
            int standing = sim.squadMemberCount(post.squad);
            if (standing == 0) {
                post.lostTick = tick;
                if (!post.line()) beat.accept("picket_lost_" + post.number);
                continue;
            }
            if (post.contactTick < 0 && contact[p] > 0) {
                post.contactTick = tick;
                if (!post.line()) beat.accept("picket_contact_" + post.number);
            }
            if (post.line() && !post.fallingBack) post.redoubt = nearest(redoubts, post.x, post.y);
            CordonRedoubt home = post.redoubt;
            if (!post.fallingBack && home.manned() && fallsBack(standing, post.team, pressing[p])) {
                post.fallingBack = true;
                NavigationGrid grid = sim.getGrid();
                int[] to = fallBackCell((x, y) -> grid.isWalkable(x, y)
                        && grid.arePathConnected(post.x, post.y, x, y, GridPathfinder.USE_CARDINAL_NAVIGATION), home);
                sim.assignSquadCommand(ObjectiveAssignment.withdraw(post.squad, to[0], to[1]),
                        CommandAuthority.GARRISON, ISSUER, label(post) + " falls back");
            }
            if (post.fallingBack && home.manned() && inside(sim, post.squad, home)) {
                post.backTick = tick;
                post.joined = standing;
                CordonSequence.join(sim, home, List.of(post.squad));
                if (!post.line()) beat.accept("picket_back_" + post.number);
            }
        }
    }

    /**
     * Where a post falls back to: the cell inside its redoubt nearest the middle that its team can walk to
     * ({@code reach}), or the middle where none is. The middle itself may be a raised or built cell no one walks on
     * (town Road arid 8: redoubt 2's middle was a terrace cell, and picket 2's last man stood still in the street from
     * tick 4500 to the cap with no way to it).
     */
    static int[] fallBackCell(CellTest reach, CordonRedoubt r) {
        int cx = r.centreX(), cy = r.centreY();
        int span = Math.max(r.right() - r.left(), r.bottom() - r.top());
        for (int d = 0; d <= span; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != d) continue;
                    int x = cx + dx, y = cy + dy;
                    if (r.contains(x, y, 0) && reach.test(x, y)) return new int[]{x, y};
                }
            }
        }
        return new int[]{cx, cy};
    }

    private static String label(Post post) {
        return post.line() ? "Picket " + post.course + " team " + post.number : "Outer picket " + post.number;
    }

    private static boolean inside(BattleSimulation sim, int squad, CordonRedoubt r) {
        long leader = sim.getSquad(squad) == null ? 0L : sim.getSquad(squad).leaderId;
        if (leader == 0L || sim.resolveUnit(leader) == 0L || !sim.world().hasPosition(leader)) return false;
        return r.contains(sim.world().cellX(leader), sim.world().cellY(leader), 0);
    }

    /** Each post in a line for evidence: where it stood, when it made contact, and whether it came back or was lost. */
    public String readings() {
        if (posts.isEmpty()) return "none";
        StringBuilder out = new StringBuilder();
        for (Post p : posts) {
            if (out.length() > 0) out.append("; ");
            out.append(String.format(Locale.ROOT, "%s (r%d, %d,%d)",
                    p.line() ? "line " + p.course + " " + p.number : String.valueOf(p.number), p.redoubt.number(), p.x, p.y));
            out.append(p.contactTick < 0 ? " no contact" : String.format(Locale.ROOT, " contact %.1f", p.contactTick * BattleSimulation.TICK_DT / 60f));
            if (p.backTick >= 0) out.append(String.format(Locale.ROOT, ", back %.1f with %d of %d", p.backTick * BattleSimulation.TICK_DT / 60f, p.joined, p.team));
            else if (p.lostTick >= 0) out.append(String.format(Locale.ROOT, ", lost %.1f", p.lostTick * BattleSimulation.TICK_DT / 60f));
            else if (p.fallingBack) out.append(", falling back");
            else out.append(", holding");
        }
        return out.toString();
    }
}
