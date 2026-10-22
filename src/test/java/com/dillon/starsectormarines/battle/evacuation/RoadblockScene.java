package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A swarm coming down a street at a manned roadblock ({@code colony-breach-nouns.md},
 * Roadblock). The street runs east-west between two solid blocks; the line is
 * laid across it by the production planner's layout and raised by the
 * production {@link ColonyRoadblocks#raise}, and a pack of Runners starts at
 * the west end. Two loops: {@code gap} leaves the cohort's two-cell lane open
 * through the line, {@code closed} closes the street.
 *
 * <p>Verdicts assert mechanics: nobody walks through a piece that stands, the
 * line is on its bags when the swarm arrives, the line engages, and while the
 * swarm is in front of it nothing the line or its gun fires lands on its own side.
 * Who wins, and what a scrum behind the bags does, are readings. In {@code closed}
 * the swarm goes round by an alley through the north block and comes at the line
 * from behind, which is what closing a street does in a town.
 */
public final class RoadblockScene implements BehaviorScene {

    static final int WIDTH = 84;
    static final int HEIGHT = 31;
    /** The street between the blocks: rows 9..21, thirteen wide. */
    private static final int STREET_TOP = 9, STREET_BOTTOM = 21;
    static final int LINE_X = 46;
    /** The alley through the north block: rows 3..4, entered at x 18..19 and left at x 70..71. */
    private static final int ALLEY_TOP = 3, ALLEY_BOTTOM = 4, ALLEY_WEST = 18, ALLEY_EAST = 70;
    private static final int LANE_Y = 15;
    private static final int RUNNERS = 36;
    private static final int TICKS = 1800;
    /** A Runner this near a living rifleman of the line is in contact with it. */
    private static final int CONTACT_RANGE = 8;
    /** Ticks after contact by which a manned line has fired. */
    private static final int ENGAGE_WITHIN = 150;

    @Override public String id() { return "roadblock"; }

    @Override public String label() {
        return "Runners down a street at a militia roadblock: closed where it stands, manned when they arrive";
    }

    @Override public int frameDelayMillis() { return 60; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("gap", "closed")) reports.add(run(loop, loop.equals("gap"), frames));
        return reports;
    }

    static SceneWorld build() {
        return SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                .seed(20261022L)
                // The north block, with an alley through it from well up the street to well past the line, so
                // a closed street is gone round rather than leaving the swarm nowhere to go.
                .wall(0, 0, WIDTH - 1, ALLEY_TOP - 1)
                .wall(0, ALLEY_BOTTOM + 1, ALLEY_WEST - 1, STREET_TOP - 1)
                .wall(ALLEY_WEST + 2, ALLEY_BOTTOM + 1, ALLEY_EAST - 1, STREET_TOP - 1)
                .wall(ALLEY_EAST + 2, ALLEY_BOTTOM + 1, WIDTH - 1, STREET_TOP - 1)
                .wall(0, STREET_BOTTOM + 1, WIDTH - 1, HEIGHT - 1)
                .build();
    }

    /** The scene's line: the production layout across the street, inward to the east. */
    static ColonyRoadblocks.Plan plan(BattleSimulation sim, boolean gap) {
        ColonyRoadblocks.Ground ground = ColonyRoadblocks.ground(sim);
        int[] span = ColonyRoadblocks.frontageSpan(ground, LINE_X, LANE_Y, 0, 1);
        if (span == null) throw new IllegalStateException("the scene's street has no frontage");
        RedoubtWorks.Keep keep = gap ? (x, y) -> y != LANE_Y && y != LANE_Y + 1 : (x, y) -> true;
        ColonyRoadblocks.Plan plan = ColonyRoadblocks.layout(ground, keep, LINE_X, LANE_Y, 1, 0,
                span, span, null, ColonyMilitia.POST_SIZE, 7L);
        if (plan == null) throw new IllegalStateException("the planner refused the scene's line");
        return plan;
    }

    private SceneReport run(String loop, boolean gap, FrameSink frames) {
        SceneWorld scene = build();
        try (BattleSimulation sim = scene.sim()) {
            ColonyRoadblocks.Plan plan = plan(sim, gap);
            List<Integer> teams = ColonyRoadblocks.raise(sim, List.of(plan), new ArrayList<>(), RiskLevel.MEDIUM, new Random(5L));
            sim.getNavigationMesh().rebuild();
            Set<Long> line = new HashSet<>();
            long gun = 0L;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long u = sim.liveUnitAt(i);
                if (sim.identity().faction(u) != Faction.ALLY) continue;
                if (sim.identity().type(u).isTurret()) gun = u;
                else line.add(u);
            }
            Set<Long> pieceCells = new HashSet<>();
            for (RedoubtWorks.Piece p : plan.obstacles())
                for (int y = p.y(); y < p.y() + p.height(); y++)
                    for (int x = p.x(); x < p.x() + p.width(); x++) pieceCells.add(key(x, y));
            Set<Long> bagCells = new HashSet<>();
            for (RedoubtWorks.Bag b : plan.bags()) bagCells.add(key(b.x(), b.y()));

            long[] runners = new long[RUNNERS];
            for (int i = 0; i < RUNNERS; i++) {
                runners[i] = sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER,
                        UnitType.SWARM_RUNNER, 2 + (i % 6), STREET_TOP + 1 + (i / 6) * 2)
                        .role(UnitRole.SWARM_PRESSURE));
            }

            boolean throughPiece = false;
            String throughWhere = "";
            int contact = -1, manned = -1, onBags = 0, crossings = 0;
            Set<Long> crossed = new HashSet<>();
            int firedBy = -1, behindTick = -1;
            float friendlyInFront = 0f;
            for (int tick = 0; tick < TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                // Until a Runner is past the bags, every round the line fires is at an enemy in front of it.
                if (behindTick < 0) friendlyInFront = friendly(sim.telemetry(), line, gun);
                for (long r : runners) {
                    if (sim.resolveUnit(r) == 0L) continue;
                    int cx = (int) Math.floor(sim.world().x(r)), cy = (int) Math.floor(sim.world().y(r));
                    if (pieceCells.contains(key(cx, cy)) && !sim.getGrid().isWalkable(cx, cy) && !throughPiece) {
                        throughPiece = true;
                        throughWhere = " (" + cx + "," + cy + " on tick " + tick + ")";
                    }
                    boolean behind = cx > LINE_X + 1 && cy >= STREET_TOP && cy <= STREET_BOTTOM;
                    if (behind && crossed.add(r)) crossings++;
                    if (behind && behindTick < 0) behindTick = tick;
                    if (contact < 0 && nearLine(sim, line, cx, cy)) contact = tick;
                }
                if (contact == tick) {
                    manned = 0;
                    for (long m : line) {
                        if (sim.resolveUnit(m) == 0L) continue;
                        manned++;
                        int mx = (int) Math.floor(sim.world().x(m)), my = (int) Math.floor(sim.world().y(m));
                        if (nearBag(bagCells, mx, my)) onBags++;
                    }
                }
                if (contact >= 0 && firedBy < 0 && rounds(sim.telemetry(), line, gun) > 0) firedBy = tick;
                if (tick % 15 == 0) frames.frame(loop, sim, tick, loop + " / " + tick);
            }

            CombatTelemetryService telemetry = sim.telemetry();
            int runnersLeft = 0;
            for (long r : runners) if (sim.resolveUnit(r) != 0L) runnersLeft++;
            int lineLeft = 0;
            for (long m : line) if (sim.resolveUnit(m) != 0L) lineLeft++;
            int standing = 0;
            for (long cell : pieceCells) if (!sim.getGrid().isWalkable((int) (cell >> 32), (int) cell)) standing++;

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("team-seated", teams.size() == 1 && line.size() == ColonyMilitia.POST_SIZE && gun != 0L,
                    teams.size() + " team(s), " + line.size() + " on the line, gun " + (gun != 0L ? "seated" : "missing")));
            verdicts.add(Verdict.of("pieces-close-the-street", !throughPiece,
                    "no Runner stood on a hedgehog, crate or truck while it stood" + throughWhere));
            verdicts.add(Verdict.of("lane-left-open", gap == (plan.gap() > 0),
                    plan.gap() + " line cells left open for the cohort's lane"));
            verdicts.add(Verdict.of("manned-at-contact", contact < 0 || manned == 0 || onBags == manned,
                    contact < 0 ? "the swarm never reached the line"
                            : onBags + " of " + manned + " living line militia on the bags at contact (tick " + contact + ")"));
            verdicts.add(Verdict.of("line-engages", contact < 0 || firedBy >= 0 && firedBy - contact <= ENGAGE_WITHIN,
                    contact < 0 ? "no contact" : "first round " + (firedBy < 0 ? "never" : (firedBy - contact) + " ticks after contact")));
            // Once Runners are among the line a round may pass through a friend, as any fire may: that is a
            // reading. What the layout owns is that nothing fires over or through its own riflemen at the front.
            verdicts.add(Verdict.of("no-fire-through-own-line", friendlyInFront == 0f,
                    friendlyInFront + " damage landed on the line's own side while the swarm was in front of it"
                            + (behindTick < 0 ? "" : " (first Runner past the bags on tick " + behindTick + ")")));

            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("contactTick", contact);
            metrics.put("runnersBehindLine", crossings);
            metrics.put("runnersLeft", runnersLeft);
            metrics.put("lineLeft", lineLeft);
            metrics.put("piecesStanding", standing);
            metrics.put("piecesLaid", pieceCells.size());
            metrics.put("lineRounds", rounds(telemetry, line, 0L));
            float lineFriendly = 0f;
            for (long m : line) lineFriendly += telemetry.friendlyFireDamage(m);
            metrics.put("lineFriendlyDamage", lineFriendly);
            metrics.put("firstBehindTick", behindTick);
            metrics.put("gunFriendlyDamage", gun == 0L ? 0f : telemetry.friendlyFireDamage(gun));
            metrics.put("gunRounds", gun == 0L ? 0 : telemetry.roundsFired(gun));
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static float friendly(CombatTelemetryService telemetry, Set<Long> line, long gun) {
        float total = gun == 0L ? 0f : telemetry.friendlyFireDamage(gun);
        for (long m : line) total += telemetry.friendlyFireDamage(m);
        return total;
    }

    private static int rounds(CombatTelemetryService telemetry, Set<Long> line, long gun) {
        int total = gun == 0L ? 0 : telemetry.roundsFired(gun);
        for (long m : line) total += telemetry.roundsFired(m);
        return total;
    }

    private static boolean nearLine(BattleSimulation sim, Set<Long> line, int x, int y) {
        for (long m : line) {
            if (sim.resolveUnit(m) == 0L) continue;
            if (Math.max(Math.abs(sim.world().x(m) - x), Math.abs(sim.world().y(m) - y)) <= CONTACT_RANGE) return true;
        }
        return false;
    }

    /** On a bag, or a cell from one: a rifleman leaning over the course still mans it. */
    private static boolean nearBag(Set<Long> bags, int x, int y) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) if (bags.contains(key(x + dx, y + dy))) return true;
        return false;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
