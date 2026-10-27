package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SmokeGrenadeSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A sapper squad crossing open ground to a gun nest, with and without a long-lasting smoke screen between them
 * (`phased-assault.md`, "Smoke screens: the pot and the partition"). It is the picture of the one claim the planner relies on
 * when it asks for smoke: a screen cuts sight <em>both ways</em>, so the nest cannot see the sappers it would kill, and the
 * side that laid it cannot see through it either.
 *
 * <ul>
 *   <li>{@code open}: no screen. The control, and the proof that the nest is a gun worth screening against.</li>
 *   <li>{@code screened}: a pot's cloud stands across the sappers' line to the nest before they arrive, and holds for the
 *       whole crossing. A base of fire stands off to one side, in sight of the nest past the screen's edge.</li>
 *   <li>{@code screened-across-the-base}: a heavier cloud laid so that it also cuts the base of fire's line to the nest. The
 *       other edge of the partition: the screen that blinds the nest blinds the fire that was suppressing it.</li>
 *   <li>{@code thinned}: a screen that has run its course before the sappers arrive. A cloud is not a wall: when it has
 *       thinned away it cuts nothing, and the line is open again.</li>
 * </ul>
 *
 * <p>The verdicts assert mechanics (the cloud stood, sight was cut in both directions, the base of fire was obscured); how
 * many sappers live is reported and never asserted. Every number is UNTUNED.
 */
public final class SmokeScreenScene implements BehaviorScene {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 54;
    private static final int TICKS = 2400;
    private static final int FRAME_EVERY_TICKS = 30;

    private static final int NEST_X = 74;
    private static final int NEST_Y = 27;
    /** A nest reaches 24 cells. */
    private static final int REACH = 24;
    /** Where the sappers are going: the foot of the wall beside the nest. */
    private static final int SITE_X = 68;
    private static final int SITE_Y = NEST_Y;
    private static final int SAPPER_START_X = 10;
    /** The thinned loop starts the sappers further back, so the screen is gone by the time they arrive. */
    private static final int LATE_START_X = 4;
    private static final int BASE_X = 58;
    private static final int BASE_Y = 15;
    private static final String MARINE_RIFLE = "weapon.field-rifle";

    /** A long-burning screen that thins before it clears: two and a half minutes, the last thirty spent contracting. */
    private static SmokeGrenadeSpec pot() {
        return new SmokeGrenadeSpec(9f, 1.2f, 0.7f, 1.2f, 3f, 150f, 30f);
    }

    /** A heavy screen, wide enough to cut the base of fire's line as well. */
    private static SmokeGrenadeSpec heavy() {
        SmokeGrenadeSpec pot = pot();
        return new SmokeGrenadeSpec(pot.throwRange(), pot.throwDuration(), pot.flightSeconds(), pot.arcHeight(), 4.5f,
                pot.cloudDuration(), pot.thinSeconds());
    }

    /** A screen that is gone before the crossing: eight seconds whole and four of thin. */
    private static SmokeGrenadeSpec shortLived() {
        SmokeGrenadeSpec pot = pot();
        return new SmokeGrenadeSpec(pot.throwRange(), pot.throwDuration(), pot.flightSeconds(), pot.arcHeight(), 3f, 12f, 4f);
    }

    private static final List<String> LOOPS = List.of("open", "screened", "screened-across-the-base", "thinned");

    @Override public String id() { return "smoke-screen"; }

    @Override
    public String label() {
        return "Smoke screen: a pot's cloud between a sapper squad and a gun nest cuts sight both ways, for its own side too,"
                + " and is not a wall once it has thinned away.";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : LOOPS) reports.add(playOne(loop, frames));
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean late = loop.equals("thinned");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("sappers").faction(Faction.MARINE).size(6).at(late ? LATE_START_X : SAPPER_START_X, SITE_Y)
                .primary(MARINE_RIFLE).special(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, 2, 2)
                .assigned(squadId -> ObjectiveAssignment.attackMove(squadId, SITE_X, SITE_Y)).done();
        // Sturdy and short-ranged: the base of fire is here for its sight line to the nest, neither to be a casualty count nor to kill the nest before the crossing.
        builder.squad("base").faction(Faction.MARINE).size(6).at(BASE_X, BASE_Y).primary("weapon.smg").health(400f).stationary().done();
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, HEIGHT - 4);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 4, 3);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261015L);
        long nest = sim.spawn(MapTurret.create("nest", Faction.DEFENDER, TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, NEST_X, NEST_Y));
        Trace trace = new Trace(loop, world, nest);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY_TICKS, tick -> String.format(Locale.ROOT,
                        "smoke screen / %s / tick %d / sappers %d alive / screen %s", loop, tick, trace.sappersAlive,
                        trace.screenCaption(sim)))
                .run(TICKS);
        return report(loop, trace);
    }

    private SceneReport report(String loop, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("the-crossing-began", t.firstInReachTick >= 0,
                "first sapper inside the nest's " + REACH + " cells at tick " + t.firstInReachTick));
        switch (loop) {
            case "open" -> verdicts.add(Verdict.of("the-nest-is-a-gun-worth-screening-against", t.hpLost > 0f,
                    String.format(Locale.ROOT, "%.0f HP of the sappers lost to the nest with no screen (wanted some)", t.hpLost)));
            case "screened", "screened-across-the-base" -> {
                verdicts.add(Verdict.of("the-screen-stood-before-the-crossing",
                        t.firstCloudTick >= 0 && (t.firstInReachTick < 0 || t.firstCloudTick <= t.firstInReachTick),
                        "cloud first standing at tick " + t.firstCloudTick + ", first sapper in reach at tick " + t.firstInReachTick));
                verdicts.add(Verdict.of("the-screen-held-through-the-crossing", t.gapTicksInCrossing == 0,
                        t.gapTicksInCrossing + " ticks between first entering reach and first reaching the site (or the end of "
                                + "the loop) with no cloud standing at full radius (wanted none)"));
                verdicts.add(Verdict.of("smoke-cut-sight-in-both-directions",
                        t.cutBothWaysSamples > 0 && t.cutOneWaySamples * 100 <= t.cutBothWaysSamples,
                        t.cutBothWaysSamples + " samples with a sapper and the nest unable to see each other, "
                                + t.cutOneWaySamples + " with sight cut in one direction only (wanted some, then at most one in a hundred: "
                                + "the grid's line is not exactly symmetric at a cloud's edge cells)"));
                verdicts.add(Verdict.of("the-sappers-crossed-the-cloud",
                        t.sappersInCloudSamples > 0,
                        t.sappersInCloudSamples + " samples with a sapper standing inside the cloud (wanted some)"));
                if (loop.equals("screened-across-the-base")) {
                    verdicts.add(Verdict.of("the-screen-blinded-the-base-of-fire-too", t.baseObscuredSamples > 0,
                            t.baseObscuredSamples + " samples with smoke on the base of fire's line to the nest (wanted some)"));
                } else {
                    verdicts.add(Verdict.of("a-screen-beside-the-base-leaves-its-sight-alone", t.baseObscuredSamples == 0,
                            t.baseObscuredSamples + " samples with smoke on the base of fire's line to the nest (wanted none)"));
                }
            }
            case "thinned" -> {
                verdicts.add(Verdict.of("the-screen-stood-and-went", t.firstCloudTick >= 0 && t.cloudGoneTick > t.firstCloudTick,
                        "cloud standing from tick " + t.firstCloudTick + " to tick " + t.cloudGoneTick));
                verdicts.add(Verdict.of("the-screen-was-gone-before-the-crossing",
                        t.firstInReachTick < 0 || t.cloudGoneTick >= 0 && t.cloudGoneTick <= t.firstInReachTick,
                        "cloud gone at tick " + t.cloudGoneTick + ", first sapper in reach at tick " + t.firstInReachTick));
                verdicts.add(Verdict.of("a-thinned-screen-cuts-nothing", t.cutBothWaysSamples == 0 && t.cutOneWaySamples == 0,
                        t.cutBothWaysSamples + " samples of sight cut with the screen gone (wanted none)"));
            }
            default -> { }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("sappersAliveAtEnd", t.sappersAlive);
        metrics.put("sapperHpLost", Math.round(t.hpLost));
        metrics.put("firstInReachTick", t.firstInReachTick);
        metrics.put("firstAtSiteTick", t.firstAtSiteTick);
        metrics.put("firstCloudTick", t.firstCloudTick);
        metrics.put("cloudGoneTick", t.cloudGoneTick);
        metrics.put("cloudSecondsThrown", Math.round(t.cloudSeconds));
        metrics.put("crossingGapTicks", t.gapTicksInCrossing);
        metrics.put("sightCutBothWaysSamples", t.cutBothWaysSamples);
        metrics.put("sightCutOneWaySamples", t.cutOneWaySamples);
        metrics.put("sappersInCloudSamples", t.sappersInCloudSamples);
        metrics.put("baseObscuredSamples", t.baseObscuredSamples);
        metrics.put("nestAliveAtEnd", t.nestAlive ? 1 : 0);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Lays the screen for its loop, and reads the sappers against the nest and the cloud every tick. */
    private static final class Trace implements TickObserver {
        private final String loop;
        private final long[] sappers;
        private final long[] base;
        private final long nest;
        private final float startHp;

        int sappersAlive;
        float hpLost;
        int firstInReachTick = -1;
        int firstAtSiteTick = -1;
        int firstCloudTick = -1;
        int cloudGoneTick = -1;
        float cloudSeconds;
        int gapTicksInCrossing;
        int cutBothWaysSamples;
        int cutOneWaySamples;
        int sappersInCloudSamples;
        int baseObscuredSamples;
        boolean nestAlive = true;
        private boolean launched;

        Trace(String loop, SceneWorld world, long nest) {
            this.loop = loop;
            this.sappers = world.members("sappers");
            this.base = world.members("base");
            this.nest = nest;
            World w = world.sim().world();
            float hp = 0f;
            for (long id : sappers) hp += w.hp(id);
            this.startHp = hp;
        }

        String screenCaption(BattleSimulation sim) {
            if (sim.smokeFields().activeFields().isEmpty()) return "none";
            SmokeFieldService.SmokeFieldView cloud = sim.smokeFields().activeFields().get(0);
            return String.format(Locale.ROOT, "r %.1f of %.1f, %.0f s left", cloud.radius(), cloud.fullRadius(), cloud.remaining());
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            World w = sim.world();
            if (!launched && tick >= 5 && !loop.equals("open")) {
                launched = true;
                SmokeGrenadeSpec spec = switch (loop) {
                    case "screened-across-the-base" -> heavy();
                    case "thinned" -> shortLived();
                    default -> pot();
                };
                float cy = loop.equals("screened-across-the-base") ? 24f : NEST_Y;
                float cx = loop.equals("screened-across-the-base") ? 65f : 66f;
                sim.smokeFields().launch(0L, Faction.MARINE, cx - 9f, cy, cx, cy + 0.5f, spec);
                cloudSeconds = spec.cloudDuration();
            }
            List<SmokeFieldService.SmokeFieldView> clouds = sim.smokeFields().activeFields();
            if (!clouds.isEmpty() && firstCloudTick < 0) firstCloudTick = tick;
            if (clouds.isEmpty() && firstCloudTick >= 0 && cloudGoneTick < 0) cloudGoneTick = tick;
            boolean whole = false;
            for (SmokeFieldService.SmokeFieldView cloud : clouds) whole |= cloud.radius() >= cloud.fullRadius();
            nestAlive = w.isAlive(nest);
            float nestX = w.x(nest);
            float nestY = w.y(nest);
            int alive = 0;
            float hp = 0f;
            boolean anyInReach = false;
            boolean anyAtSite = false;
            NavigationGrid grid = sim.getGrid();
            for (long id : sappers) {
                if (!w.isAlive(id)) continue;
                alive++;
                hp += w.hp(id);
                double d = Math.hypot(w.x(id) - nestX, w.y(id) - nestY);
                if (d <= REACH) anyInReach = true;
                if (Math.hypot(w.x(id) - SITE_X, w.y(id) - SITE_Y) <= 3.0) anyAtSite = true;
                if (tick % 5 == 0 && nestAlive && d <= REACH) sampleSight(grid, w, id, nest);
            }
            sappersAlive = alive;
            hpLost = startHp - hp;
            if (anyInReach && firstInReachTick < 0) firstInReachTick = tick;
            if (anyAtSite && firstAtSiteTick < 0) firstAtSiteTick = tick;
            boolean crossing = firstInReachTick >= 0 && firstAtSiteTick < 0;
            if (crossing && !loop.equals("open") && !loop.equals("thinned") && !whole) gapTicksInCrossing++;
            if (tick % 5 == 0 && nestAlive && !clouds.isEmpty()) {
                for (long member : base) {
                    if (!w.isAlive(member)) continue;
                    if (grid.smokeDepthOnLine(w.x(member), w.y(member), nestX, nestY) > 0) {
                        baseObscuredSamples++;
                        break;
                    }
                }
            }
        }

        private void sampleSight(NavigationGrid grid, World w, long sapper, long nestId) {
            int sx = w.cellX(sapper);
            int sy = w.cellY(sapper);
            int nx = w.cellX(nestId);
            int ny = w.cellY(nestId);
            boolean forward = grid.hasLineOfSight(nx, ny, sx, sy);
            boolean back = grid.hasLineOfSight(sx, sy, nx, ny);
            boolean smoked = grid.smokeDepthOnLine(w.x(nestId), w.y(nestId), w.x(sapper), w.y(sapper)) > 0;
            if (smoked && !forward && !back) cutBothWaysSamples++;
            else if (smoked && forward != back) cutOneWaySamples++;
            if (grid.hasTransientOpacity(sx, sy)) sappersInCloudSamples++;
        }
    }
}
