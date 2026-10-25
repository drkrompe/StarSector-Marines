package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.evacuation.ColonyMilitia;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The outer picket ({@code cordon-nouns.md}, Outer picket): one allied team out in front of each redoubt on the road
 * the swarm comes down, put there to be lost. It is the line's eyes while the company is still landing and moving up:
 * what it sees the player sees (an allied body casts the side's fog like a Marine), and the battle hears it make
 * contact. It holds its post until it is pressed, then falls back down its road to its redoubt and joins the
 * garrison; most of it dies on the way. It never calls, and nobody is sent for it.
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

    /** A test on a cell. */
    interface CellTest {
        boolean test(int x, int y);
    }

    private static final class Post {
        final int number;
        final CordonRedoubt redoubt;
        final int x, y, squad, team;
        int contactTick = -1, backTick = -1, lostTick = -1, joined;
        boolean fallingBack;

        Post(int number, CordonRedoubt redoubt, int x, int y, int squad, int team) {
            this.number = number;
            this.redoubt = redoubt;
            this.x = x;
            this.y = y;
            this.squad = squad;
            this.team = team;
        }
    }

    private final List<Post> posts;

    private CordonOuterPicket(List<Post> posts) {
        this.posts = posts;
    }

    /** Whether the outer picket stands ({@link #PROPERTY}). */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    /**
     * Puts a team out in front of each redoubt, at the post {@link #postCell} finds from its first street mouth.
     *
     * @param front +1 where the front is north (high y), -1 where it is south
     */
    public static CordonOuterPicket stand(BattleSimulation sim, CordonArmour armour, List<CordonRedoubt> redoubts,
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
            posts.add(new Post(number, r, at[0], at[1], squad, sim.squadMemberCount(squad)));
        }
        return new CordonOuterPicket(posts);
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

    /** One tick: contact, the fall back when pressed, and the survivors into the garrison at their redoubt. */
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
                beat.accept("picket_lost_" + post.number);
                continue;
            }
            if (post.contactTick < 0 && contact[p] > 0) {
                post.contactTick = tick;
                beat.accept("picket_contact_" + post.number);
            }
            CordonRedoubt home = post.redoubt;
            if (!post.fallingBack && home.manned() && fallsBack(standing, post.team, pressing[p])) {
                post.fallingBack = true;
                sim.assignSquadCommand(ObjectiveAssignment.withdraw(post.squad, home.centreX(), home.centreY()),
                        CommandAuthority.GARRISON, ISSUER, "Outer picket " + post.number + " falls back");
            }
            if (post.fallingBack && home.manned() && inside(sim, post.squad, home)) {
                post.backTick = tick;
                post.joined = standing;
                CordonSequence.join(sim, home, List.of(post.squad));
                beat.accept("picket_back_" + post.number);
            }
        }
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
            out.append(String.format(Locale.ROOT, "%d (r%d, %d,%d)", p.number, p.redoubt.number(), p.x, p.y));
            out.append(p.contactTick < 0 ? " no contact" : String.format(Locale.ROOT, " contact %.1f", p.contactTick / 3600f));
            if (p.backTick >= 0) out.append(String.format(Locale.ROOT, ", back %.1f with %d of %d", p.backTick / 3600f, p.joined, p.team));
            else if (p.lostTick >= 0) out.append(String.format(Locale.ROOT, ", lost %.1f", p.lostTick / 3600f));
            else if (p.fallingBack) out.append(", falling back");
            else out.append(", holding");
        }
        return out.toString();
    }
}
