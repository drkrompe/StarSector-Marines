package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Brute against a held colony building.
 *
 * <p>Three marines hold a walled building with two windows on the west wall,
 * the side the Brute comes from. The marines are squadless and so hold their
 * fire, and the Brute is given a large health pool: this scene is about what
 * the Brute does to the building, not about who wins.
 * <ul>
 *   <li>{@code far-door}: the only door is on the far side. The Brute breaks in
 *       through the near wall or a window, then goes through the hole.</li>
 *   <li>{@code near-door}: a door on the near side. The Brute uses it and
 *       breaks nothing.</li>
 *   <li>{@code fortified}: the same building at fortified wall strength, door on
 *       the far side. Nothing is light enough to break; the Brute walks round.</li>
 *   <li>{@code emplacement}: the far-door building plus an anti-air
 *       emplacement in the open. The Brute goes for the emplacement first.</li>
 *   <li>{@code fenced-yard}: the marines stand in the open inside a ring of
 *       industrial fence beyond the building, its only gate on the far side,
 *       as a colony redoubt's works are laid. The Brute breaks the fence
 *       between it and them rather than walking round to the gate.</li>
 *   <li>{@code works}: the far-door building at the same colony wall strength,
 *       its ground marked as a fort's ({@link FortifiedWorks}). The Brute still
 *       breaks in, and its first breach comes later than {@code far-door}'s.</li>
 *   <li>{@code works-yard}: the fenced yard marked as a fort's. A charge rams the
 *       fence rather than bowling it over, and the first fence cell opens later
 *       than {@code fenced-yard}'s.</li>
 * </ul>
 *
 * <p>Each loop reports {@code firstBreachTick}, the tick its first wall, window or
 * fence cell opened: the time a Brute takes to get in, house wall against works.
 */
public final class SwarmBruteBreachScene implements BehaviorScene {

    private static final int TICKS = 1500;
    /** Long enough for a Brute to get through works, which are meant to hold it several times longer. */
    private static final int WORKS_TICKS = 3600;
    private static final int COLONY_WALL_HP = 100;
    private static final int FORTIFIED_WALL_HP = 240;

    @Override public String id() { return "swarm-brute-breach"; }

    @Override public String label() {
        return "Swarm Brute: breaks into a held building where no door is near, spares heavy walls,"
                + " goes for emplacements";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        int[] firstBreach = new int[2];
        int[] worksBreach = new int[2];
        for (String loop : List.of("far-door", "near-door", "fortified", "emplacement", "fenced-yard",
                "works", "works-yard")) {
            SceneReport report = playOne(loop, frames);
            int breach = report.metrics().get("firstBreachTick").intValue();
            switch (loop) {
                case "far-door" -> firstBreach[0] = breach;
                case "fenced-yard" -> firstBreach[1] = breach;
                case "works" -> worksBreach[0] = breach;
                case "works-yard" -> worksBreach[1] = breach;
                default -> { }
            }
            reports.add(report);
        }
        reports.add(comparison("works-hold-longer", firstBreach, worksBreach));
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean fortified = loop.equals("fortified");
        boolean emplacement = loop.equals("emplacement");
        boolean works = loop.startsWith("works");
        boolean yard = loop.equals("fenced-yard") || loop.equals("works-yard");
        int ticks = works ? WORKS_TICKS : TICKS;
        SwarmStrainGround ground = new SwarmStrainGround(yard ? 56 : 48, 28, 22, 10, 29, 17,
                fortified ? FORTIFIED_WALL_HP : COLONY_WALL_HP)
                .window(21, 12).window(21, 15);
        if (works) {
            FortifiedWorks.mark(ground.grid, 0, 0, ground.grid.getWidth() - 1, ground.grid.getHeight() - 1);
        }
        if (loop.equals("near-door")) ground.door(21, 13);
        else ground.door(30, 14);
        // The yard's fence: closed to movement, open to sight, its gate on the side away from the Brute.
        if (yard) {
            for (int y = YARD_TOP; y <= YARD_BOTTOM; y++) {
                for (int x = YARD_LEFT; x <= YARD_RIGHT; x++) {
                    if (!onYardFence(x, y)) continue;
                    ground.grid.setWalkable(x, y, false);
                    ground.grid.setSeeThrough(x, y, true);
                    ground.topology.setFixture(x, y, true);
                }
            }
        }
        int mx = yard ? 46 : 23, my = yard ? 46 : 23, mz = yard ? 48 : 26;

        SceneWorld world = SceneBuilder.over(ground.grid, ground.topology).seed(20261021L)
                .missionCompletion(false)
                .unit("m1", Faction.MARINE, UnitType.MARINE, mx, 12, spec -> spec.health(5000f))
                .unit("m2", Faction.MARINE, UnitType.MARINE, my, 15, spec -> spec.health(5000f))
                .unit("m3", Faction.MARINE, UnitType.MARINE, mz, 14, spec -> spec.health(5000f))
                .build();
        try (BattleSimulation sim = world.sim()) {
            int fence = yard ? layYardFence(sim) : 0;
            long turret = emplacement
                    ? sim.spawn(MapTurret.create("aa", Faction.MARINE, "structure.aa-locust", 12, 22))
                    : 0L;
            long brute = sim.spawn(SwarmStrains.spec("Brute", UnitType.SWARM_BRUTE, 6, 13)
                    .health(5000f));
            long[] marines = {world.unit("m1"), world.unit("m2"), world.unit("m3")};
            Trace trace = new Trace(brute, turret, marines, ground, sim);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 30, tick -> loop + " / brute / tick " + tick)
                    .run(ticks);
            return yard ? yardReport(loop, ticks, trace, sim, fence) : report(loop, ticks, trace, sim, ground, turret);
        }
    }

    private static final int YARD_LEFT = 40, YARD_RIGHT = 52, YARD_TOP = 7, YARD_BOTTOM = 21;
    private static final int GATE_TOP = 13, GATE_BOTTOM = 14;

    private static boolean onYardFence(int x, int y) {
        boolean ring = x == YARD_LEFT || x == YARD_RIGHT || y == YARD_TOP || y == YARD_BOTTOM;
        boolean gate = x == YARD_RIGHT && y >= GATE_TOP && y <= GATE_BOTTOM;
        return ring && !gate;
    }

    /** Sets the fence art on the closed cells, which makes each a destructible barricade; returns the pieces laid. */
    private static int layYardFence(BattleSimulation sim) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return 0;
        int laid = 0;
        for (int y = YARD_TOP; y <= YARD_BOTTOM; y++) {
            for (int x = YARD_LEFT; x <= YARD_RIGHT; x++) {
                if (!onYardFence(x, y)) continue;
                DoodadDef def = registry.doodad("doodad.industrial-fence-straight-"
                        + (y == YARD_TOP || y == YARD_BOTTOM ? "h" : "v"));
                if (def == null) return laid;
                sim.addDoodad(new Doodad(x, y, def));
                laid++;
            }
        }
        return laid;
    }

    /** Both works loops' first breach comes later than their house-wall twin's: the Brute takes longer to get in. */
    private SceneReport comparison(String loop, int[] house, int[] works) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("building-holds-longer", later(works[0], house[0]),
                "first breach tick " + works[0] + " on works against " + house[0] + " on a house wall"));
        verdicts.add(Verdict.of("fence-holds-longer", later(works[1], house[1]),
                "first fence cell open tick " + works[1] + " on works against " + house[1] + " in a plain yard"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("houseBreachTick", house[0]);
        metrics.put("worksBreachTick", works[0]);
        metrics.put("yardBreachTick", house[1]);
        metrics.put("worksYardBreachTick", works[1]);
        return new SceneReport(id(), loop, WORKS_TICKS, verdicts, metrics);
    }

    /** Whether a breach at {@code works} (-1 for none) came later than one at {@code house}, which must have come. */
    private static boolean later(int works, int house) {
        return house >= 0 && (works < 0 || works > house);
    }

    private SceneReport yardReport(String loop, int ticks, Trace trace, BattleSimulation sim, int fence) {
        SwarmStrikeSystem strikes = sim.swarmStrikes();
        int opened = 0;
        for (int y = YARD_TOP; y <= YARD_BOTTOM; y++) {
            for (int x = YARD_LEFT; x <= YARD_RIGHT; x++) {
                if (onYardFence(x, y) && sim.getGrid().isWalkable(x, y)) opened++;
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fence-is-a-barricade", fence > 0,
                fence + " fence pieces laid (none without the tile registry)"));
        // A charge bowls a fence piece over outright (colony-breach-nouns.md, Brute); a walking Brute strikes it down.
        long bowled = sim.bruteCharges().events().stream().filter(e -> e.what().equals("prop")).count();
        int struck = strikes.blowsLanded() + (int) bowled;
        verdicts.add(Verdict.of("strikes-the-fence", fence == 0 || struck > 0,
                strikes.blowsLanded() + " blows landed, " + bowled + " pieces bowled over by a charge"));
        verdicts.add(Verdict.of("breaks-through", fence == 0 || struck == 0 || opened > 0,
                opened + " fence cells opened"));
        verdicts.add(Verdict.of("spares-the-building", trace.ringHpUnchanged(),
                "building ring hp unchanged " + trace.ringHpUnchanged()));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fencePieces", fence);
        metrics.put("blows", strikes.blowsLanded());
        metrics.put("bowled", bowled);
        metrics.put("fenceCellsOpened", opened);
        metrics.put("firstBreachTick", trace.firstBreachTick);
        metrics.put("marineDamage", trace.marineDamage());
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private SceneReport report(String loop, int ticks, Trace trace, BattleSimulation sim,
                               SwarmStrainGround ground, long turret) {
        SwarmStrikeSystem strikes = sim.swarmStrikes();
        int breaches = 0;
        for (int y = ground.minY - 1; y <= ground.maxY + 1; y++) {
            for (int x = ground.minX - 1; x <= ground.maxX + 1; x++) {
                if (ground.onRing(x, y) && trace.wasClosed(x, y)
                        && ground.grid.isWalkable(x, y)) breaches++;
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "far-door", "works" -> {
                verdicts.add(Verdict.of("strikes-the-building", strikes.blowsLanded() > 0,
                        strikes.blowsLanded() + " blows on walls and windows"));
                verdicts.add(Verdict.of("breaks-in", breaches > 0,
                        breaches + " wall or window cells of the building opened"));
                verdicts.add(Verdict.of("goes-through-the-hole", breaches == 0
                                || trace.firstInsideTick >= 0,
                        breaches == 0 ? "no breach to go through"
                                : "inside from tick " + trace.firstInsideTick));
                verdicts.add(Verdict.of("strikes-the-occupants", trace.firstInsideTick < 0
                                || trace.marineDamage() > 0f,
                        trace.firstInsideTick < 0 ? "never inside"
                                : "occupants took " + trace.marineDamage()));
            }
            case "near-door" -> {
                verdicts.add(Verdict.of("uses-the-near-door", strikes.blowsLanded() == 0,
                        strikes.blowsLanded() + " blows; a door was in reach"));
                verdicts.add(Verdict.of("gets-in", trace.firstInsideTick >= 0,
                        "inside from tick " + trace.firstInsideTick));
            }
            case "fortified" -> {
                verdicts.add(Verdict.of("heavy-walls-untouched", strikes.blowsLanded() == 0
                                && trace.ringHpUnchanged(),
                        strikes.blowsLanded() + " blows; ring hp unchanged " + trace.ringHpUnchanged()));
            }
            case "emplacement" -> {
                verdicts.add(Verdict.of("emplacement-first", trace.firstTarget == turret,
                        "first target " + trace.firstTarget + ", emplacement " + turret));
                verdicts.add(Verdict.of("strikes-the-emplacement", !trace.reachedTurret
                                || trace.turretDamage > 0f,
                        trace.reachedTurret ? "emplacement took " + trace.turretDamage
                                : "never reached it"));
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("blows", strikes.blowsLanded());
        metrics.put("structuresBroken", strikes.structuresBroken());
        metrics.put("breachedRingCells", breaches);
        metrics.put("firstInsideTick", trace.firstInsideTick);
        metrics.put("firstBreachTick", trace.firstBreachTick);
        metrics.put("marineDamage", trace.marineDamage());
        metrics.put("turretDamage", trace.turretDamage);
        return new SceneReport(id(), loop, ticks, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final long brute;
        private final long turret;
        private final long[] marines;
        private final float[] initialHp;
        private final SwarmStrainGround ground;
        private final boolean[] closedAtStart;
        private final int[] ringHp;
        private final float turretHp;
        private long firstTarget = -1L;
        private int firstInsideTick = -1;
        private int firstBreachTick = -1;
        private boolean reachedTurret;
        private float turretDamage;
        private float[] lastHp;

        Trace(long brute, long turret, long[] marines, SwarmStrainGround ground,
              BattleSimulation sim) {
            this.brute = brute;
            this.turret = turret;
            this.marines = marines;
            this.ground = ground;
            initialHp = new float[marines.length];
            for (int i = 0; i < marines.length; i++) initialHp[i] = sim.world().hp(marines[i]);
            lastHp = initialHp.clone();
            int w = ground.grid.getWidth();
            closedAtStart = new boolean[w * ground.grid.getHeight()];
            ringHp = new int[closedAtStart.length];
            for (int y = 0; y < ground.grid.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    closedAtStart[y * w + x] = !ground.grid.isWalkable(x, y);
                    ringHp[y * w + x] = ground.grid.getWallHp(x, y);
                }
            }
            turretHp = turret == 0L ? 0f : sim.world().hp(turret) + sim.world().armor(turret);
        }

        boolean wasClosed(int x, int y) {
            return closedAtStart[y * ground.grid.getWidth() + x];
        }

        boolean ringHpUnchanged() {
            int w = ground.grid.getWidth();
            for (int y = 0; y < ground.grid.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    if (ground.onRing(x, y) && ground.grid.getWallHp(x, y) != ringHp[y * w + x]) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Whether any cell closed at the start, wall, window or fence, now stands open. */
        private boolean breached(BattleSimulation sim) {
            int w = ground.grid.getWidth();
            for (int i = 0; i < closedAtStart.length; i++) {
                if (closedAtStart[i] && sim.getGrid().isWalkable(i % w, i / w)) return true;
            }
            return false;
        }

        float marineDamage() {
            float total = 0f;
            for (int i = 0; i < marines.length; i++) total += initialHp[i] - lastHp[i];
            return total;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (!sim.world().isAlive(brute)) return;
            for (int i = 0; i < marines.length; i++) {
                if (sim.world().isAlive(marines[i])) lastHp[i] = sim.world().hp(marines[i]);
            }
            long target = sim.combat().targetId(brute);
            if (firstTarget < 0 && target != 0L) firstTarget = target;
            if (firstBreachTick < 0 && breached(sim)) firstBreachTick = tick;
            if (firstInsideTick < 0
                    && ground.inside(sim.world().cellX(brute), sim.world().cellY(brute))) {
                firstInsideTick = tick;
            }
            if (turret != 0L) {
                float dx = sim.world().x(turret) - sim.world().x(brute);
                float dy = sim.world().y(turret) - sim.world().y(brute);
                if (Math.hypot(dx, dy) <= sim.combat().attackRange(brute)
                        + sim.getRoster().radius(turret)) {
                    reachedTurret = true;
                }
                float now = sim.world().isAlive(turret)
                        ? sim.world().hp(turret) + sim.world().armor(turret) : 0f;
                turretDamage = turretHp - now;
            }
        }
    }
}
