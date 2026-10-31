package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/**
 * The Road's line under rising pressure ({@code cordon-nouns.md}, Line bores and The final surge): bores that break
 * out in front of and between the redoubts as the evacuation goes on, and the swarm's surge at the line once the town
 * no longer needs the company, so the fall-back behind the line is a fighting retreat.
 *
 * <p>Sites are planned at setup on open ground the line must answer: in front of each redoubt's front wall and of each
 * stretch of picket line between two, never on or beside the convoys' road, never in the safe camp, never on or
 * beside a redoubt or the town, never behind the line. They open in order of distance from the gap, the far redoubts
 * first, so the pressure spreads along the line away from the road; the ones nearest the gap are kept for the surge.
 * Each scheduled site is due at its step's time or sooner when the evacuation runs ahead of it (convoys home, people
 * out); it stirs for a warning first (ground thrown up at the site, a beat, a beacon on the HUD) and then opens through
 * the ordinary bore seam as a fissure that grows as any bore does.
 *
 * <p>The surge ({@link #surges}) opens the sites left, a few seconds apart, calls a tide on every living bore and holds
 * the swarm at the tide's strength for a while, then at a raised floor. Nothing here touches the end rules: it only
 * opens bores in front of the line, so the company can always walk back behind it.
 */
public final class CordonBoreEscalation {

    /** Nearest and furthest a site stands in front of the front wall it threatens, in cells. */
    static final int NEAR = 18, FAR = 42;
    /** Cells either side of a site's anchor (a redoubt's middle, or the middle of the stretch between two) it may open. */
    static final int SPREAD = 20;
    /** Cells a site keeps from every cell of the convoys' road (Chebyshev). */
    static final int OFF_ROAD = 16;
    /** Cells a site keeps from a redoubt's wall: never on top of one. */
    static final int OFF_REDOUBT = 12;
    /** Cells a site keeps from the safe camp's ground. */
    static final int OFF_CAMP = 16;
    /** Cells a site keeps from the cut-off town's wall: the town is besieged, not eaten. */
    static final int OFF_TOWN = 30;
    /** Cells a site keeps from every other site and from the bores already standing. */
    static final int APART = 16;
    /** Cells kept from the map's edge. */
    static final int EDGE = 10;
    /** Candidate cells are looked at on this pitch. */
    private static final int PITCH = 3;
    private static final long SALT = 0x4C494E45424F5245L;

    /** Seconds a scheduled site stirs before it opens: the player's warning. */
    static final int WARNING_SECONDS = 20;
    /** Fewest seconds between one scheduled site starting to stir and the next, so a jump in progress never stacks them. */
    static final int STEP_GAP_SECONDS = 45;
    /** Seconds a surge site stirs before it opens, and between one surge site starting to stir and the next. */
    static final int SURGE_WARNING_SECONDS = 8, SURGE_STAGGER_SECONDS = 5;
    /** The most sites the surge opens. */
    static final int SURGE_MAX = 4;
    /** Seconds the surge holds the swarm at the tide's strength, and the floor it holds after. */
    static final int SURGE_HOLD_SECONDS = 120;
    static final float SURGE_TAIL_PRESSURE = 0.4f;
    /** The surge comes at this time whatever the town is doing, so the end is a fight before the hard cap. */
    static final int SURGE_LATEST_SECONDS = 1500;
    /** A Marine this close to the town's wall is still in the town. */
    static final int TOWN_REACH = CordonSequence.CONTACT_RANGE;
    /** Share of the company on the ground behind the line that says it is falling back. */
    static final float FALLBACK_SHARE = 0.5f;
    /** Seconds between puffs of ground thrown up at a stirring site. */
    private static final float DUST_SECONDS = 1f;

    /**
     * One scheduled step: due at {@code seconds} into the battle, or once {@code tripsHome} convoys are out, or once
     * {@code peopleShare} of the briefing's number of townspeople are out, whichever comes first.
     */
    public record Step(int seconds, int tripsHome, float peopleShare) { }

    /**
     * Ain Tafrant's schedule over the fifteen-minute timetable: a bore stirring about a minute before each of the first
     * three tides and one more in the last stretch, each brought forward when the convoys run ahead of the clock. UNTUNED.
     */
    public static final List<Step> STANDARD = List.of(
            new Step(180, 1, 0.25f),
            new Step(330, 2, 0.5f),
            new Step(510, 3, 0.75f),
            new Step(660, 4, 1f));

    /** A box of cells, inclusive. */
    public record Box(int left, int top, int right, int bottom) {
        /** Cells from the box to a cell outside it (Chebyshev), 0 inside. */
        int distance(int x, int y) {
            return Math.max(Math.max(left - x, x - right), Math.max(Math.max(top - y, y - bottom), 0));
        }
    }

    /** Whether a cell is open ground a bore can break out on. */
    public interface Open { boolean at(int x, int y); }

    /**
     * A planned site: its place in the opening order from 1, its cell, the redoubts it stands before ({@code west} and
     * {@code east} the same for a redoubt's own front, neighbours for the stretch between), and whether it is kept for
     * the surge.
     */
    public record Site(int order, int x, int y, int west, int east, boolean surge) {
        public boolean between() { return west != east; }
    }

    /** How a site stands now. */
    public enum Status { PLANNED, STIRRING, OPEN, COLLAPSED }

    /** A site as the HUD reads it: its status, ticks left until it opens while it stirs, and its bore's number once open. */
    public record Reading(Site site, Status status, int ticksLeft, int number) { }

    private final List<Site> sites;
    private final List<Step> schedule;
    private final float tidePressure, ordinaryPressure;
    private final float lineY;
    private final int frontward;
    private final int[] warnTick, openTick, number;
    private int nextScheduled;
    private int lastWarn = Integer.MIN_VALUE / 2;
    private boolean surging;
    private int surgeTick = -1;
    private boolean retided;
    private float floor;

    public CordonBoreEscalation(List<Site> sites, List<Step> schedule, CordonSequence.Timetable timetable, float lineY,
                                int frontward) {
        this.sites = List.copyOf(sites);
        this.schedule = List.copyOf(schedule);
        this.tidePressure = timetable.tidePressure();
        this.ordinaryPressure = timetable.ordinaryPressure();
        this.lineY = lineY;
        this.frontward = frontward;
        warnTick = new int[this.sites.size()];
        openTick = new int[this.sites.size()];
        number = new int[this.sites.size()];
        Arrays.fill(warnTick, -1);
        Arrays.fill(openTick, -1);
        Arrays.fill(number, -1);
    }

    /**
     * Plans the Road's line bores on the battle's ground at setup: one site per anchor that has open ground, ordered
     * for opening, the first {@code schedule.size()} scheduled and the rest kept for the surge.
     */
    public static CordonBoreEscalation plan(BattleSimulation sim, List<CordonRedoubt> redoubts, CordonConvoys convoys,
                                            CordonTown town, CordonSequence.Timetable timetable, float lineY,
                                            int frontward, long seed) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        List<Box> walls = new ArrayList<>();
        for (CordonRedoubt r : redoubts) walls.add(new Box(r.left(), r.top(), r.right(), r.bottom()));
        List<Box> keepOut = new ArrayList<>();
        CordonCamp camp = convoys.camp();
        if (camp != null) {
            keepOut.add(inflate(new Box(camp.centreX() - CordonCamp.HALF, camp.centreY() - CordonCamp.HALF,
                    camp.centreX() + CordonCamp.HALF, camp.centreY() + CordonCamp.HALF), OFF_CAMP));
        }
        if (town != null) keepOut.add(inflate(new Box(town.left(), town.top(), town.right(), town.bottom()), OFF_TOWN));
        List<int[]> bores = new ArrayList<>();
        for (SwarmBore b : sim.swarmBores().bores()) if (!b.collapsed()) bores.add(new int[]{b.x(), b.y()});
        List<int[]> road = convoys.corridor();
        int[] crossing = road.get(Math.min(convoys.gapIndex(), road.size() - 1));
        List<Site> sites = plan(walls, frontward, road, crossing[0], keepOut, bores,
                (x, y) -> open(grid, topology, x, y), grid.getWidth(), grid.getHeight(), STANDARD.size(), seed);
        return new CordonBoreEscalation(sites, STANDARD, timetable, lineY, frontward);
    }

    /**
     * The sites, in opening order ({@code cordon-nouns.md}, Line bores). Anchors are each redoubt's front and the middle
     * of each stretch between neighbours (redoubts west to east); each takes a seeded open cell {@link #NEAR} to
     * {@link #FAR} in front of the front wall there and within {@link #SPREAD} across, kept {@link #OFF_ROAD} from the
     * road, {@link #OFF_REDOUBT} from every redoubt's wall, out of every {@code keepOut} box and {@link #APART} from
     * {@code bores} and every site before it. An anchor with no such cell has no site. They open furthest from the gap
     * at {@code gapX} first (the west first on a tie); the first {@code scheduled} are scheduled, the rest the surge's.
     */
    static List<Site> plan(List<Box> redoubts, int frontward, List<int[]> road, int gapX, List<Box> keepOut,
                           List<int[]> bores, Open open, int width, int height, int scheduled, long seed) {
        record Anchor(int x, int frontRow, int west, int east) { }
        List<Box> sorted = new ArrayList<>(redoubts);
        sorted.sort(Comparator.comparingInt(b -> b.left() + b.right()));
        List<Anchor> anchors = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Box r = sorted.get(i);
            anchors.add(new Anchor((r.left() + r.right()) / 2, front(r, frontward), i + 1, i + 1));
            if (i + 1 < sorted.size()) {
                Box n = sorted.get(i + 1);
                anchors.add(new Anchor(((r.left() + r.right()) / 2 + (n.left() + n.right()) / 2) / 2,
                        Math.round((front(r, frontward) + front(n, frontward)) / 2f), i + 1, i + 2));
            }
        }
        anchors.sort(Comparator.comparingInt((Anchor a) -> -Math.abs(a.x() - gapX)).thenComparingInt(Anchor::x));
        SplittableRandom rng = new SplittableRandom(seed ^ SALT);
        List<int[]> taken = new ArrayList<>(bores);
        List<Site> out = new ArrayList<>();
        for (Anchor a : anchors) {
            List<int[]> candidates = new ArrayList<>();
            for (int d = NEAR; d <= FAR; d += PITCH) {
                int y = a.frontRow() + frontward * d;
                if (y < EDGE || y >= height - EDGE) continue;
                for (int x = Math.max(EDGE, a.x() - SPREAD); x <= Math.min(width - 1 - EDGE, a.x() + SPREAD); x += PITCH) {
                    if (allowed(x, y, sorted, road, keepOut, taken) && open.at(x, y)) candidates.add(new int[]{x, y});
                }
            }
            if (candidates.isEmpty()) continue;
            int[] c = candidates.get(rng.nextInt(candidates.size()));
            taken.add(c);
            out.add(new Site(out.size() + 1, c[0], c[1], a.west(), a.east(), out.size() >= scheduled));
        }
        return out;
    }

    /** Whether a cell is ground a site may stand on: off the road, the redoubts, the kept-out boxes and every bore. */
    static boolean allowed(int x, int y, List<Box> redoubts, List<int[]> road, List<Box> keepOut, List<int[]> taken) {
        for (Box r : redoubts) if (r.distance(x, y) < OFF_REDOUBT) return false;
        for (Box k : keepOut) if (k.distance(x, y) == 0) return false;
        if (road != null) {
            for (int[] c : road) if (Math.abs(c[0] - x) <= OFF_ROAD && Math.abs(c[1] - y) <= OFF_ROAD) return false;
        }
        for (int[] t : taken) {
            long dx = t[0] - x, dy = t[1] - y;
            if (dx * dx + dy * dy < (long) APART * APART) return false;
        }
        return true;
    }

    /** The row of a redoubt's front wall. */
    private static int front(Box r, int frontward) {
        return frontward > 0 ? r.bottom() : r.top();
    }

    private static Box inflate(Box b, int by) {
        return new Box(b.left() - by, b.top() - by, b.right() + by, b.bottom() + by);
    }

    /**
     * Whether scheduled step {@code step} is due ({@code cordon-nouns.md}, Line bores): its time has come, or the
     * evacuation has run ahead of it in convoys home or people out against the briefing's number.
     */
    static boolean due(Step step, int seconds, int tripsHome, int peopleOut, int needed) {
        if (seconds >= step.seconds()) return true;
        if (step.tripsHome() > 0 && tripsHome >= step.tripsHome()) return true;
        return needed > 0 && step.peopleShare() > 0f && peopleOut >= Math.ceil(step.peopleShare() * needed);
    }

    /**
     * Whether the final surge comes ({@code cordon-nouns.md}, The final surge): at once when the town is abandoned; at
     * {@link #SURGE_LATEST_SECONDS} whatever else; otherwise once the town no longer needs the company and no truck is
     * left loading at its gate: the town is finished (everyone out), or the briefing's number is out and the company is
     * falling back (nobody left in the town and at least {@link #FALLBACK_SHARE} of it behind the line).
     */
    static boolean surges(boolean abandoned, boolean townFinished, boolean loading, int peopleOut, int needed,
                          boolean companyInTown, float companyBehindShare, int seconds) {
        if (abandoned || seconds >= SURGE_LATEST_SECONDS) return true;
        if (loading) return false;
        if (townFinished) return true;
        return peopleOut >= needed && !companyInTown && companyBehindShare >= FALLBACK_SHARE;
    }

    /** The planned sites, in opening order. */
    public List<Site> sites() { return sites; }

    /** Whether the final surge has come. */
    public boolean surging() { return surging; }

    /** The swarm's pressure share this escalation holds the director to at least between tides; 0 before the surge. */
    public float pressureFloor() { return floor; }

    /** Every site as it stands now. */
    public List<Reading> readings(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        List<Reading> out = new ArrayList<>(sites.size());
        for (int i = 0; i < sites.size(); i++) {
            Status status = Status.PLANNED;
            if (number[i] >= 0) {
                SwarmBore bore = bore(sim, number[i]);
                status = bore == null ? Status.STIRRING : bore.collapsed() ? Status.COLLAPSED : Status.OPEN;
            } else if (warnTick[i] >= 0) {
                status = Status.STIRRING;
            }
            int left = status == Status.STIRRING && openTick[i] >= 0 ? Math.max(0, openTick[i] - tick) : 0;
            out.add(new Reading(sites.get(i), status, left, number[i]));
        }
        return out;
    }

    /** One tick, from the sequence after its tides: due sites stir, stirred sites open, and the surge when it comes. */
    void advance(BattleSimulation sim, int tick, CordonSequence sequence, Consumer<String> beat) {
        int seconds = Math.round(tick * BattleSimulation.TICK_DT);
        if (!surging && checkSurge(sim, sequence, seconds)) startSurge(sim, tick, sequence, beat);
        if (!surging && nextScheduled < sites.size() && !sites.get(nextScheduled).surge()
                && nextScheduled < schedule.size() && tick - lastWarn >= CordonSequence.ticks(STEP_GAP_SECONDS)) {
            CordonConvoys convoys = sequence.convoys();
            int trips = convoys == null ? 0 : convoys.out();
            int out = convoys == null ? 0 : convoys.peopleOut();
            if (due(schedule.get(nextScheduled), seconds, trips, out, sequence.peopleNeeded())) {
                warn(nextScheduled, tick, tick + CordonSequence.ticks(WARNING_SECONDS), beat);
                lastWarn = tick;
                nextScheduled++;
            }
        }
        boolean surgeOpen = surging;
        for (int i = 0; i < sites.size(); i++) {
            if (warnTick[i] < 0 || number[i] >= 0) {
                continue;
            }
            if (tick < warnTick[i]) {
                surgeOpen = false;
                continue;
            }
            if (tick >= openTick[i]) {
                Site s = sites.get(i);
                number[i] = sequence.openBeatBore(sim, s.x(), s.y());
                beat.accept("line_bore_" + s.order());
            } else {
                surgeOpen = false;
                if ((tick - warnTick[i]) % Math.max(1, CordonSequence.ticks(Math.round(DUST_SECONDS))) == 0) {
                    stir(sim, sites.get(i));
                }
            }
        }
        if (!surging) return;
        // Once the last surge site is open, every living bore is called again, the new ones with them.
        if (surgeOpen && !retided) {
            retided = true;
            sim.swarmBores().tide(tick);
        }
        if (floor > SURGE_TAIL_PRESSURE && tick - surgeTick >= CordonSequence.ticks(SURGE_HOLD_SECONDS)) {
            floor = SURGE_TAIL_PRESSURE;
            if (!sequence.inTide()) sim.setSwarmPressureShare(Math.max(ordinaryPressure, floor));
        }
    }

    private boolean checkSurge(BattleSimulation sim, CordonSequence sequence, int seconds) {
        CordonConvoys convoys = sequence.convoys();
        boolean abandoned = "town_abandoned".equals(sequence.townFinished());
        boolean loading = false;
        if (convoys != null) {
            for (int c = 1; c <= convoys.convoys(); c++) if ("loading".equals(convoys.phase(c))) loading = true;
        }
        int out = convoys == null ? 0 : convoys.peopleOut();
        boolean townFinished = sequence.townFinished() != null;
        int needed = sequence.peopleNeeded();
        // Only look for the company falling back once nothing else decides it.
        if (abandoned || seconds >= SURGE_LATEST_SECONDS || loading || townFinished || out < needed) {
            return surges(abandoned, townFinished, loading, out, needed, true, 0f, seconds);
        }
        boolean inTown = false;
        int total = 0, behind = 0;
        CordonTown town = sequence.town();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)) continue;
            if (sim.identity().faction(unit) != Faction.MARINE || !CordonSequence.isCompany(sim.identity().type(unit))) continue;
            int x = sim.world().cellX(unit), y = sim.world().cellY(unit);
            total++;
            if (CordonSequence.behindLine(y, lineY, frontward)) behind++;
            if (town != null && town.contains(x, y, TOWN_REACH)) inTown = true;
        }
        return surges(false, false, false, out, needed, inTown, total == 0 ? 0f : behind / (float) total, seconds);
    }

    /**
     * The final surge: every site not yet stirring stirs in turn, nearest the gap first, a few seconds apart; every
     * living bore is called; the swarm is let out at the tide's strength.
     */
    private void startSurge(BattleSimulation sim, int tick, CordonSequence sequence, Consumer<String> beat) {
        surging = true;
        surgeTick = tick;
        beat.accept("final_surge");
        int queued = 0;
        for (int i = sites.size() - 1; i >= 0 && queued < SURGE_MAX; i--) {
            if (warnTick[i] >= 0) continue;
            int start = tick + CordonSequence.ticks(queued * SURGE_STAGGER_SECONDS);
            warnTick[i] = start;
            openTick[i] = start + CordonSequence.ticks(SURGE_WARNING_SECONDS);
            queued++;
        }
        sim.swarmBores().tide(tick);
        floor = tidePressure;
        sim.setSwarmPressureShare(Math.max(ordinaryPressure, floor));
    }

    private void warn(int i, int tick, int open, Consumer<String> beat) {
        warnTick[i] = tick;
        openTick[i] = open;
        beat.accept("line_bore_stirring_" + sites.get(i).order());
    }

    /** Ground thrown up at a stirring site: the bloom digging up under it. Presentation only. */
    private static void stir(BattleSimulation sim, Site s) {
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6;
            sim.emitGroundDust(s.x() + 0.5f, s.y() + 0.5f, (float) Math.cos(a) * 0.8f, (float) Math.sin(a) * 0.8f,
                    0.5f, EffectsService.DUST_WHEEL);
        }
    }

    private static SwarmBore bore(BattleSimulation sim, int number) {
        for (SwarmBore b : sim.swarmBores().bores()) if (!b.mother() && b.number() == number) return b;
        return null;
    }

    /** Open ground with room round it: walkable, outdoors, nothing solid within two cells. */
    private static boolean open(NavigationGrid grid, CellTopology topology, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx, ny = y + dy;
                if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || topology.isWall(nx, ny)
                        || topology.isRoofIntact(nx, ny)) return false;
            }
        }
        return true;
    }
}
