package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.smoke.SmokeFieldService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A Bombard on its creep and a marine squad holding a line (`bombard.md`, step 2): the body and the lob.
 *
 * <ul>
 *   <li>{@code standing-line}: the squad stands well inside its reach. It plants, winds up, and its glob bursts
 *       into haze on the squad.</li>
 *   <li>{@code melee}: the same squad, unarmed and sturdy, with Brutes on it from the start. It plants and holds
 *       its fire: it never lobs a haze that would cover a melee.</li>
 *   <li>{@code off-creep}: no creep anywhere. It never plants and never fires.</li>
 *   <li>{@code inside-minimum-range}: a rifle squad close in. It is never lobbed on; the rifles kill the
 *       Bombard, and its sac bursts into a last small haze where it falls.</li>
 * </ul>
 *
 * <p>The verdicts assert mechanics; the placement of a burst is checked against the scatter the rules allow.
 * Every number is UNTUNED.
 */
public final class BombardScene implements BehaviorScene {

    private static final int WIDTH = 80;
    private static final int HEIGHT = 54;
    private static final int TICKS = 1200;
    private static final int FRAME_EVERY_TICKS = 15;

    private static final int BOMBARD_X = 10;
    private static final int ROW_Y = 27;
    private static final int LINE_X = 46;
    private static final int CLOSE_X = 24;

    private static final List<String> LOOPS = List.of("standing-line", "melee", "off-creep", "inside-minimum-range");

    @Override public String id() { return "bombard"; }

    @Override
    public String label() {
        return "Bombard: planted on creep it lobs a spore glob onto a standing line, never into melee, never off"
                + " creep, never inside its minimum range, and its sac bursts when it dies.";
    }

    @Override public int frameDelayMillis() { return 60; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : LOOPS) reports.add(playOne(loop, frames));
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean close = loop.equals("inside-minimum-range");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false).seed(20261025L);
        var squad = builder.squad("line").faction(Faction.MARINE).size(6).at(close ? CLOSE_X : LINE_X, ROW_Y)
                .stationary();
        if (loop.equals("melee")) squad.unarmed().health(5000f);
        else squad.primary("weapon.field-rifle").health(close ? 400f : 2000f);
        squad.done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        if (loop.equals("melee")) {
            for (int i = 0; i < 3; i++) {
                sim.spawn(SwarmStrains.spec("Brute " + i, UnitType.SWARM_BRUTE, LINE_X + 2, ROW_Y - 1 + i).health(5000f));
            }
        }
        if (!loop.equals("off-creep")) {
            sim.creepField().putSource(1L, BOMBARD_X, ROW_Y, new CreepGrowth(6f, 6f));
            sim.matureCreep();
        }
        var spec = SwarmStrains.spec("Bombard", UnitType.SWARM_BOMBARD, BOMBARD_X, ROW_Y);
        if (!close) spec.health(5000f);
        long bombard = sim.spawn(spec);
        Trace trace = new Trace(world, bombard);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> String.format(Locale.ROOT,
                        "bombard / %s / tick %d / %s / globs %d / hazes %d", loop, tick, trace.phase(sim),
                        sim.swarmBombards().globsLanded(), sim.smokeFields().activeHazes().size()))
                .run(TICKS);
        return report(loop, trace, sim);
    }

    private SceneReport report(String loop, Trace t, BattleSimulation sim) {
        int globs = sim.swarmBombards().globsLanded();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "standing-line" -> {
                verdicts.add(Verdict.of("it-planted-then-wound-up-then-lobbed",
                        t.firstPlantedTick >= 0 && t.firstWindupTick > t.firstPlantedTick
                                && t.firstGlobTick > t.firstWindupTick,
                        "planted at tick " + t.firstPlantedTick + ", wound up at " + t.firstWindupTick
                                + ", first glob landed at " + t.firstGlobTick));
                int revealFloor = Math.round(SwarmBombards.LAUNCH_REVEAL_SECONDS / BattleSimulation.TICK_DT);
                verdicts.add(Verdict.of("each-launch-gave-it-away", t.revealedTicks >= globs * revealFloor,
                        t.revealedTicks + " ticks with its own cell observed for " + globs + " launches (wanted at least "
                                + revealFloor + " a launch)"));
                verdicts.add(Verdict.of("the-haze-landed-on-the-line", t.hazesOnTheLine > 0,
                        t.hazesOnTheLine + " of " + t.hazeCentres.size() + " hazes centred within "
                                + SwarmBombardBehavior.FAR_SCATTER + " cells of a marine (wanted some)"));
                // Half the reload: a pack leader's tempo may shorten it, never to nothing.
                int reloadFloor = Math.round(UnitType.SWARM_BOMBARD.attackCooldown * 0.5f / BattleSimulation.TICK_DT);
                verdicts.add(Verdict.of("it-reloaded-between-lobs", globs >= 2 && t.minGlobGapTicks >= reloadFloor,
                        globs + " globs, the closest two " + t.minGlobGapTicks + " ticks apart (wanted two or more, at"
                                + " least " + reloadFloor + " apart)"));
            }
            case "melee" -> {
                verdicts.add(Verdict.of("it-planted", t.firstPlantedTick >= 0, "planted at tick " + t.firstPlantedTick));
                verdicts.add(Verdict.of("it-never-lobbed-into-melee", globs == 0 && t.firstWindupTick < 0,
                        globs + " globs, first wind-up at tick " + t.firstWindupTick + " (wanted none)"));
            }
            case "off-creep" -> verdicts.add(Verdict.of("off-creep-it-never-fires",
                    t.firstPlantedTick < 0 && globs == 0,
                    "planted at tick " + t.firstPlantedTick + ", " + globs + " globs (wanted neither)"));
            case "inside-minimum-range" -> {
                verdicts.add(Verdict.of("it-never-lobbed-inside-its-minimum-range", globs == 0 && t.firstWindupTick < 0,
                        globs + " globs, first wind-up at tick " + t.firstWindupTick + " (wanted none)"));
                verdicts.add(Verdict.of("the-rifles-killed-it", t.deathTick >= 0, "died at tick " + t.deathTick));
                verdicts.add(Verdict.of("its-sac-burst-where-it-fell",
                        sim.swarmBombards().deathBursts() == 1 && t.hazeAtCorpse,
                        sim.swarmBombards().deathBursts() + " death bursts, haze at the corpse: " + t.hazeAtCorpse));
            }
            default -> { }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("globsLanded", globs);
        metrics.put("hazes", t.hazeCentres.size());
        metrics.put("hazesOnTheLine", t.hazesOnTheLine);
        metrics.put("firstPlantedTick", t.firstPlantedTick);
        metrics.put("firstWindupTick", t.firstWindupTick);
        metrics.put("firstGlobTick", t.firstGlobTick);
        metrics.put("lineHpLost", Math.round(t.lineHpLost));
        metrics.put("lineInHazeSamples", t.lineInHazeSamples);
        metrics.put("revealedTicks", t.revealedTicks);
        metrics.put("deathTick", t.deathTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Reads the Bombard's stance, the globs as they land, and every haze that stands. */
    private static final class Trace implements TickObserver {
        private final long bombard;
        private final long[] line;
        private final float lineStartHp;
        private final Set<Long> seenHazes = new HashSet<>();
        final List<float[]> hazeCentres = new ArrayList<>();
        int hazesOnTheLine;
        int firstPlantedTick = -1;
        int firstWindupTick = -1;
        int firstGlobTick = -1;
        int minGlobGapTicks = Integer.MAX_VALUE;
        int deathTick = -1;
        boolean hazeAtCorpse;
        float lineHpLost;
        int lineInHazeSamples;
        int revealedTicks;
        private int lastGlobs;
        private int lastGlobTick = -1;
        private float corpseX, corpseY;

        Trace(SceneWorld world, long bombard) {
            this.bombard = bombard;
            this.line = world.members("line");
            float hp = 0f;
            for (long id : line) hp += world.sim().world().hp(id);
            this.lineStartHp = hp;
        }

        String phase(BattleSimulation sim) {
            if (!sim.world().isAlive(bombard)) return "dead";
            if (sim.swarmBombards().windingUp(bombard)) return "winding up";
            return sim.swarmBombards().planted(bombard) ? "planted" : "not planted";
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            World w = sim.world();
            SwarmBombards bombards = sim.swarmBombards();
            boolean alive = w.isAlive(bombard);
            if (alive) {
                for (SwarmBombards.Reveal reveal : bombards.launchReveals()) {
                    if (reveal.cellX() == w.cellX(bombard) && reveal.cellY() == w.cellY(bombard)) revealedTicks++;
                }
                corpseX = w.x(bombard);
                corpseY = w.y(bombard);
                if (bombards.planted(bombard) && firstPlantedTick < 0) firstPlantedTick = tick;
                if (bombards.windingUp(bombard) && firstWindupTick < 0) firstWindupTick = tick;
            } else if (deathTick < 0) {
                deathTick = tick;
            }
            int globs = bombards.globsLanded();
            if (globs > lastGlobs) {
                if (firstGlobTick < 0) firstGlobTick = tick;
                if (lastGlobTick >= 0) minGlobGapTicks = Math.min(minGlobGapTicks, tick - lastGlobTick);
                lastGlobTick = tick;
                lastGlobs = globs;
            }
            float hp = 0f;
            for (long id : line) {
                if (!w.isAlive(id)) continue;
                hp += w.hp(id);
                if (sim.getGrid().hasHaze(w.cellX(id), w.cellY(id))) lineInHazeSamples++;
            }
            lineHpLost = lineStartHp - hp;
            for (SmokeFieldService.SmokeFieldView haze : sim.smokeFields().activeHazes()) {
                if (!seenHazes.add(haze.id())) continue;
                hazeCentres.add(new float[]{haze.x(), haze.y()});
                if (deathTick >= 0 && Math.hypot(haze.x() - corpseX, haze.y() - corpseY) < 1.5) hazeAtCorpse = true;
                for (long id : line) {
                    if (Math.hypot(haze.x() - w.x(id), haze.y() - w.y(id)) <= SwarmBombardBehavior.FAR_SCATTER) {
                        hazesOnTheLine++;
                        break;
                    }
                }
            }
        }
    }
}
