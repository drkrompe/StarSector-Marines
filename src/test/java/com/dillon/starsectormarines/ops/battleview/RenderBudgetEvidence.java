package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.ColonyCreep;
import com.dillon.starsectormarines.battle.creep.CreepVegetationSnapshotSuite;
import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.fire.FireCost;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.battle.evacuation.ColonyBreachSequence;
import com.dillon.starsectormarines.battle.evacuation.ColonyEruptionPlan;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fixture.FighterWingCommitment;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.nav.ElevationMap;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationTestMaps;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCensus;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.BufferUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * What a frame costs, per layer, on the map the owner actually plays.
 *
 * <p>The renderer was built for 280x168 and measured there. At 560x336 it is
 * four times the cells, and every explanation for the stutter that follows is
 * plausible: dense terrain emits a command per visible cell, the shadow layer
 * is a whole-sprite draw per body, effects and decals draw at every zoom. Which
 * of them is the ceiling is the one thing nobody had measured, and every lever
 * worth pulling is a different lever depending on the answer — a layer emitting
 * a hundred thousand cheap commands that batch into six draws is bound by
 * collection and merging does nothing for it, while a layer emitting four
 * hundred sprites that cannot batch at all is bound by submission and merging
 * is the whole win.
 *
 * <p><b>Through the real pipeline.</b> It stands up the canonical Conquest
 * fixture, plays it until units, decals, shadows and effects exist, and then
 * runs {@link BattleRenderer#renderWorld} — the shipping collect and the
 * shipping drain — into a real OpenGL context from {@link HeadlessGl}. The
 * Java2D review renderer is not this; it re-implements the painting and could
 * not report a texture bind if it wanted to.
 *
 * <p><b>What it measures and what it does not.</b> The times are CPU cost:
 * building the command stream, and submitting it. The GPU's own work is
 * deliberately not folded in — the drain returns as soon as the driver has
 * taken the calls — and the game's own frame around ours is not visible at all,
 * so this reports our share and says so. Counts are exact and repeat; times are
 * a measurement and do not.
 *
 * <p><b>Opt-in and excluded from {@code test}</b>, for both of
 * {@code shaderEvidence}'s reasons: it needs an accelerated driver, and a suite
 * that requires a GPU fails on the machine that has none. Where no context can
 * be made it reports that it skipped. Run it with {@code gradlew.bat
 * renderEvidence}.
 */
@Tag("render-evidence")
class RenderBudgetEvidence {

    /** A real screen, so the pixels per cell a framing works out to are the ones a player sees. */
    private static final int SURFACE_W = 1920;
    private static final int SURFACE_H = 1080;

    /**
     * Ticks played before the first frame is drawn.
     *
     * <p>Enough that the thing being measured exists. A battle at tick zero has
     * marines in shuttles, no decals, no craters, no smoke and nothing in
     * flight, so a profile taken there would report a renderer nobody runs. Six
     * hundred ticks is twenty seconds of battle: the lift is down, the ground is
     * scarred, and the leading squads are in contact.
     */
    private static final int DEFAULT_TICKS = 600;

    /** Frames measured per framing, after the warm-up. Median, so a stray GC does not become the reading. */
    private static final int DEFAULT_FRAMES = 9;

    /**
     * Frames drawn and thrown away first.
     *
     * <p>The first frame of a framing uploads every texture it touches and grows
     * every batch's backing array to its high-water mark, and it is tens of
     * times the cost of the second. Reported, it would say the ground layer
     * costs half a second.
     *
     * <p><b>Thirty rather than three, because three was measuring the JIT.</b>
     * Three covers the uploads and the array growth and does not come close to
     * covering compilation: at three, the 280x168 control's mid framing
     * reported {@code GROUND} at 0.36 ms of collection while its whole-map
     * framing — four times the visible cells, through the same loop — reported
     * 0.14, which is not a thing a renderer can do. Played thirty-one times the
     * same framing reads 0.08 and the ordering comes right. Everything early in
     * the run was inflated, and by enough to invent a regression and hide a
     * win: a lever measured against a control at three frames was being credited
     * or blamed for where its code happened to sit in C2's queue. Six framings
     * of thirty discarded frames costs a couple of seconds and is the difference
     * between an instrument and a random number.
     */
    private static final int WARMUP_FRAMES = 30;

    private static final EnumSet<RenderLayer> ALL_LAYERS = EnumSet.allOf(RenderLayer.class);

    /**
     * A camera framing, as a zoom.
     *
     * <p>Zoom 1 is the camera's own floor and means cover-fit — the whole map on
     * the screen — so "whole map" is not a separate mode, it is as far out as
     * the player can go. The other two are what the player spends the battle at.
     */
    private record Framing(String id, float zoom, String what) { }

    private static final List<Framing> FRAMINGS = List.of(
            new Framing("close", BattleCamera.MAX_ZOOM, "one compound"),
            new Framing("mid", 3f, "one lane"),
            new Framing("whole-map", BattleCamera.MIN_ZOOM, "the whole map"));

    /** One row of the report: a map, a framing, a layer, and what they cost together. */
    private record Row(String map, String framing, String layer,
                       DrawCensus census, double collectMs, double drainMs) { }

    /**
     * A whole frame, wall clock, with the GPU waited on.
     *
     * <p>The per-layer times are our CPU share and cannot see the other half of
     * a lever's bill. A resident ground draws the whole map every frame however
     * close the camera is, so it trades a submission cost we measure for a fill
     * cost we do not, and a change that halved our side while doubling the
     * driver's would read as a win in every other column here. This is the
     * column that refuses that.
     */
    private record FrameCost(String map, String framing, double frameMs, double ourMs,
                             int residentQuads, int meshBuffers) { }

    @Test
    void profilesTheRealPipelineAtThreeFramings() throws Exception {
        requireSerialScheduler();
        try (HeadlessGl gl = HeadlessGl.createOrNull(SURFACE_W, SURFACE_H)) {
            if (gl == null) {
                System.out.println("[render-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            System.out.println("[render-evidence] context: " + gl.rendererDescription());
            configureSurface();
            Global.setSettings(screenSettings());
            try {

            int ticks = Integer.getInteger("render.evidence.maxTicks", DEFAULT_TICKS);
            int frames = Integer.getInteger("render.evidence.frames", DEFAULT_FRAMES);
            Path output = Path.of(System.getProperty(
                    "render.evidence.outputDir", "build/reports/render"))
                    .toAbsolutePath().normalize();
            Files.createDirectories(output);

            Path modRoot = Path.of("mod").toAbsolutePath().normalize();
            List<Row> rows = new ArrayList<>();
            List<FrameCost> frameCosts = new ArrayList<>();
            List<String> runs = new ArrayList<>();
            List<String> atlases = new ArrayList<>();
            try (GlSpriteTokens tokens = new GlSpriteTokens()) {
                HeadlessBattleSprites sprites = new HeadlessBattleSprites(modRoot, tokens);
                sprites.ensureUnitSheets();
                sprites.ensureEngineFxSprites();
                BattleRenderer renderer = new BattleRenderer(sprites);
                renderer.onAttach();
                renderer.buildTileBatches();

                for (MapSpec spec : MapSpec.selected()) {
                    long standUp = System.nanoTime();
                    try (BattleSimulation sim = spec.build()) {
                        if (spec.playout()) play(sim, ticks);
                        System.out.printf(Locale.ROOT, "[render-evidence] %s stood up in %.1f s%n",
                                spec.id(), (System.nanoTime() - standUp) / 1e9);
                        List<EqualityCheck> checks = new ArrayList<>();
                        rows.addAll(profile(spec, sim, renderer, frames, frameCosts, checks, runs));
                        DecorationAtlas decoration = renderer.getDecorationAtlas();
                        atlases.add(spec.id() + " — " + (decoration == null
                                ? "decoration atlas: never planned" : decoration.report()));
                        // After every timed frame on this map, never between
                        // them: a readback is eight megabytes off the card and
                        // an allocation to match, and interleaved it landed in
                        // the next framing's measurement as noise several times
                        // the effect being measured.
                        for (EqualityCheck check : checks) {
                            assertCulledFrameIsIdentical(renderer, check.context(),
                                    check.map(), check.framing());
                            writeFrame(output.resolve("frames"), check.map(), check.framing());
                        }
                        assertStraddlingBodiesSurvive(sim, renderer,
                                new BattleLayout(position(), sim.getGrid().getWidth(),
                                        sim.getGrid().getHeight()),
                                sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                                spec.id());
                    }
                }
                for (MapSpec spec : MapSpec.selected()) {
                    if (spec.id().equals(MapSpec.CONQUEST_ID) || spec.id().equals(MapSpec.STRESS_ID)) {
                        assertStandUpRepeats(renderer, rows, ticks, spec);
                    }
                }
                atlases.add(0, renderer.getUnitAtlas().report());
                atlases.add(0, renderer.getGroundAtlas().report());
                System.out.println("[render-evidence] textures uploaded: "
                        + tokens.uploadedTextures());
            }

            String markdown = markdown(rows, frameCosts, ticks, frames, atlases, runs);
            Files.writeString(output.resolve("summary.md"), markdown, StandardCharsets.UTF_8);
            Files.writeString(output.resolve("summary.json"),
                    json(rows, frameCosts, ticks, frames, atlases), StandardCharsets.UTF_8);
            System.out.println(markdown);
            System.out.println("[render-evidence] wrote " + output.resolve("summary.md"));
            } finally {
                Global.setSettings(null);
            }
        }
    }

    // ---- the maps under test -------------------------------------------------

    /**
     * The two maps: the one the owner plays, and the one the renderer was built
     * for.
     *
     * <p>The control cannot be a Conquest. {@code MapScale.CONQUEST} is stated
     * by the mission rather than scaled by its tier, so a 560x336 Conquest has
     * no 280x168 twin to compare against. What the control holds fixed instead
     * is everything a frame's cost is made of — the same generator, the same
     * tile catalogs, the same collectors, and the canonical Conquest's own
     * company and target — and varies the one thing under test, which is how
     * much ground there is. It is an assault at {@code REINFORCED} because that
     * is the tier whose scale is {@code LARGE}, the 280x168 the renderer was
     * built and measured on.
     */
    record MapSpec(String id, Builder builder, boolean playout) {

        MapSpec(String id, Builder builder) {
            this(id, builder, true);
        }

        interface Builder {
            BattleSimulation build() throws Exception;
        }

        static final String CANONICAL_CONQUEST =
                "/battle-fixtures/conquest-reinforced-south-v3.json";

        static final String CANONICAL_COLONY = "/battle-fixtures/colony-breach-v1.json";

        /** Seconds of outbreak the colony's creep is grown for before it is drawn. */
        static final int COLONY_OUTBREAK_SECONDS = 240;

        static final List<MapSpec> MATRIX = List.of(
                new MapSpec("280x168 control", MapSpec::control),
                new MapSpec("560x336 conquest", MapSpec::conquest),
                new MapSpec("400x240 colony", MapSpec::colony),
                new MapSpec("400x240 green colony", MapSpec::greenColony),
                new MapSpec("400x240 green colony burning", MapSpec::burningColony),
                new MapSpec("280x168 body pile", MapSpec::bodyPile),
                new MapSpec("overlook raised ground", MapSpec::raisedGround),
                new MapSpec("400x240 the burn", () -> theBurn(false)),
                new MapSpec("400x240 the burn at night", () -> theBurn(true)),
                new MapSpec(MapSpec.STRESS_ID, MapSpec::stress, false));

        static final String CONQUEST_ID = "560x336 conquest";
        /** {@link RenderStressScene}: stood up dense with no playout; {@code -PrenderMaps=stress} runs it alone. */
        static final String STRESS_ID = "400x240 stress";
        static final String PILE_ID = "280x168 body pile";

        /**
         * The maps {@code -Drender.evidence.maps} names, by id substring, comma-separated; every map
         * when it is unset. A lever measured on one map against its own control need not pay for six.
         */
        static List<MapSpec> selected() {
            String wanted = System.getProperty("render.evidence.maps", "").trim();
            if (wanted.isEmpty()) return MATRIX;
            List<MapSpec> out = new ArrayList<>();
            for (MapSpec spec : MATRIX) {
                for (String part : wanted.split(",")) {
                    if (!part.isBlank() && spec.id().contains(part.trim())) {
                        out.add(spec);
                        break;
                    }
                }
            }
            return out;
        }

        /** Bodies in the pile: a wave's worth of dead outside a defended line, many times over. */
        static final int PILE_BODIES = Integer.getInteger("render.evidence.pileBodies", 1200);
        /** Radius of the ground the pile lies on, cells: a killing ground in front of a wire. */
        static final int PILE_RADIUS = 12;
        /** Deaths per tick while the pile is laid down, so the bodies have distinct ages. */
        static final int PILE_PER_TICK = 30;

        /**
         * The control map with {@link #PILE_BODIES} Runners killed onto the open ground nearest its
         * centre, {@link #PILE_PER_TICK} a tick, at seeded places: the pile of dead a wave leaves
         * outside a redoubt where nothing clears them. There is no creep and no bore, so only the
         * body pile cap ({@code -Dbattle.bodyPileCap=false} is its control) takes any of them away.
         * The close framing is centred on it ({@link #focusOf}).
         */
        static BattleSimulation bodyPile() throws Exception {
            BattleSimulation sim = control();
            NavigationGrid grid = sim.getGrid();
            int[] centre = pileCentre(grid);
            List<int[]> cells = new ArrayList<>();
            for (int dy = -PILE_RADIUS; dy <= PILE_RADIUS; dy++) {
                for (int dx = -PILE_RADIUS; dx <= PILE_RADIUS; dx++) {
                    int x = centre[0] + dx, y = centre[1] + dy;
                    if (dx * dx + dy * dy > PILE_RADIUS * PILE_RADIUS) continue;
                    if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()) continue;
                    if (grid.isWalkable(x, y)) cells.add(new int[]{x, y});
                }
            }
            Random random = new Random(0xB0D1E5L);
            sim.setMissionCompletionEnabled(false);
            for (int laid = 0; laid < PILE_BODIES; ) {
                for (int k = 0; k < PILE_PER_TICK && laid < PILE_BODIES; k++, laid++) {
                    int[] cell = cells.get(random.nextInt(cells.size()));
                    long runner = SwarmStrains.spawn(sim, "dead " + laid, UnitType.SWARM_RUNNER, cell[0], cell[1]);
                    sim.applyDamage(runner, 1_000_000f, 1000f);
                }
                sim.advance(BattleSimulation.TICK_DT);
            }
            PILE_FOCUS.put(sim, new float[]{centre[0] + 0.5f, centre[1] + 0.5f});
            return sim;
        }

        /**
         * The overlook meadow (a grass plateau with its ramps, in daylight), the close
         * framing on the ramp nearest the centre: what raised ground costs to draw, and
         * the GL picture of its cliffs. {@code -Dbattle.render.cliffLook=false} draws the
         * cliffs with the cliff tiles instead, which is its control.
         */
        static BattleSimulation raisedGround() {
            MapResult map = ElevationTestMaps.overlookMap(1, CellTopology.GroundKind.GRASS);
            // A squad below the ramp and one on the top, as the high-ground scene stands them,
            // so the ground in view is seen rather than under fog.
            BattleSimulation sim = SceneBuilder.over(map.grid, map.topology).seed(20261022L)
                    .squad("below").faction(Faction.MARINE).size(6).inFile(4, 16, 0, 1).done()
                    .squad("above").faction(Faction.MARINE).size(6).inFile(32, 16, 0, 1).done()
                    .build().sim();
            ElevationMap elevation = map.grid.elevation();
            int w = elevation.width(), h = elevation.height();
            float[] focus = null;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < w * h; i++) {
                if (!elevation.isRampAt(i)) continue;
                double d = Math.hypot(i % w - w / 2.0, i / w - h / 2.0);
                if (d < best) {
                    best = d;
                    focus = new float[]{i % w + .5f, i / w + .5f};
                }
            }
            if (focus != null) PILE_FOCUS.put(sim, focus);
            return sim;
        }

        /** Where a map's close framing looks: the pile for the pile, the company for the rest. */
        static final Map<BattleSimulation, float[]> PILE_FOCUS = new IdentityHashMap<>();

        /** The cell nearest the map centre with the most open ground around it within the pile's radius. */
        private static int[] pileCentre(NavigationGrid grid) {
            int cx = grid.getWidth() / 2, cy = grid.getHeight() / 2;
            int[] best = {cx, cy};
            int bestOpen = -1;
            for (int y = cy - 40; y <= cy + 40; y += 4) {
                for (int x = cx - 60; x <= cx + 60; x += 4) {
                    int open = 0;
                    for (int dy = -PILE_RADIUS; dy <= PILE_RADIUS; dy += 2) {
                        for (int dx = -PILE_RADIUS; dx <= PILE_RADIUS; dx += 2) {
                            int px = x + dx, py = y + dy;
                            if (px >= 0 && py >= 0 && px < grid.getWidth() && py < grid.getHeight()
                                    && grid.isWalkable(px, py)) open++;
                        }
                    }
                    if (open > bestOpen) {
                        bestOpen = open;
                        best = new int[]{x, y};
                    }
                }
            }
            return best;
        }

        /** {@link RenderStressScene}, its framings on the middle of the crowd. */
        static BattleSimulation stress() throws Exception {
            RenderStressScene.Stood stood = RenderStressScene.build();
            PILE_FOCUS.put(stood.sim(), stood.focus());
            return stood.sim();
        }

        /** Seconds the green colony burns for before it is drawn: the front well into its groves. */
        static final int BURN_SECONDS = 45;

        BattleSimulation build() throws Exception {
            return builder.build();
        }

        private static BattleSimulation conquest() throws Exception {
            return canonical().build();
        }

        /**
         * The canonical Colony Breach, with every eruption site open and its
         * creep grown for {@link #COLONY_OUTBREAK_SECONDS}: the infested colony
         * the creep layer is measured on. Only the creep field is advanced for
         * the outbreak, so the battle itself is the ordinary playout.
         */
        static BattleSimulation colony() throws Exception {
            BattleFixture parsed = BattleFixtureJson.fromJson(new JSONObject(resource(CANONICAL_COLONY)));
            BattleSimulation sim = (parsed instanceof BattleLaunchFixture launch ? launch.construction() : parsed).build();
            ColonyBreachSequence breach = sim.getColonyBreach();
            if (breach == null) throw new IllegalStateException("The colony fixture installs no breach");
            for (ColonyEruptionPlan.Site site : breach.plan().sites()) ColonyCreep.siteErupted(sim, site, 0);
            CreepField creep = sim.creepField();
            int ticks = Math.round(COLONY_OUTBREAK_SECONDS / BattleSimulation.TICK_DT);
            for (int t = 0; t < ticks; t++) creep.tick(t);
            return sim;
        }

        /**
         * The canonical colony on a verdant world, grown for {@link #COLONY_OUTBREAK_SECONDS} with the town's sites
         * open and a bore under every fourth tree ({@link CreepVegetationSnapshotSuite.Outbreak}), so its groves are
         * overgrown, some of their trees dead, and its grass and fields blighted: the creep's vegetation measured on
         * the map that carries it.
         */
        static BattleSimulation greenColony() throws Exception {
            BattleSimulation sim = CreepVegetationSnapshotSuite.verdantColony();
            CreepVegetationSnapshotSuite.Outbreak outbreak = new CreepVegetationSnapshotSuite.Outbreak(sim);
            outbreak.runTo(Math.round(COLONY_OUTBREAK_SECONDS / BattleSimulation.TICK_DT));
            return sim;
        }

        /**
         * The green colony with a fire line lit down its west edge in a fresh wind and burnt for
         * {@link #BURN_SECONDS}: flames, glow, embers, scorch and charred trees all on screen at once, the fire
         * layers measured against {@code -Dbattle.render.fire=false}. Only the fire field is advanced.
         */
        static BattleSimulation burningColony() throws Exception {
            BattleSimulation sim = greenColony();
            FireField fire = sim.fireField();
            fire.setWind(1f, 0.15f, 0.6f);
            int h = sim.getGrid().getHeight();
            for (int y = 0; y < h; y++) fire.addHeat(2, y, 3000);
            int ticks = Math.round(BURN_SECONDS / BattleSimulation.TICK_DT);
            for (int t = 0; t < ticks; t++) fire.tick(t);
            return sim;
        }

        static final String THE_BURN = "/battle-fixtures/heart-burn-v1.json";
        /** Seconds of The Burn played before it is drawn: inside its peak, 160 to 240 s. */
        static final int BURN_PEAK_SECONDS = Integer.getInteger("render.evidence.burnSeconds", 200);
        /** The window, from the end of the play, over which the simulation's cost is read. */
        static final int BURN_COST_WINDOW_SECONDS = 80;

        /**
         * The Burn (`heart-nouns.md`) at seed 202, the whole battle played to {@link #BURN_PEAK_SECONDS}: the swarm,
         * the company, the barrage and the fire it lights, all live. While it plays, every tick of the last
         * {@link #BURN_COST_WINDOW_SECONDS} is timed whole and the fire's own share read off
         * {@link BattleSimulation#fireCostProfile()}, and written beside the render report
         * ({@code burn-sim.md}): the serial scheduler this task forces, so the whole tick is the
         * single-thread figure, not production's parallel one. The framings centre on the densest fire, and at
         * night the same moment is drawn with no lamps, so the fire light is all there is.
         */
        static BattleSimulation theBurn(boolean night) throws Exception {
            BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(resource(THE_BURN)));
            BattleSimulation sim = fixture.build();
            sim.setMissionCompletionEnabled(false);
            int end = Math.round(BURN_PEAK_SECONDS / BattleSimulation.TICK_DT);
            int windowStart = end - Math.round(BURN_COST_WINDOW_SECONDS / BattleSimulation.TICK_DT);
            List<Long> tickNanos = new ArrayList<>();
            int peakBurning = 0, fireTicksBefore = -1;
            while (sim.getSimTickIndex() < end) {
                if (fireTicksBefore < 0 && sim.getSimTickIndex() >= windowStart) {
                    fireTicksBefore = sim.fireCostProfile().ticks();
                }
                long t0 = System.nanoTime();
                sim.advance(BattleSimulation.TICK_DT);
                long dt = System.nanoTime() - t0;
                if (sim.getSimTickIndex() > windowStart) {
                    tickNanos.add(dt);
                    peakBurning = Math.max(peakBurning, sim.fireField().burningCount());
                }
            }
            if (!night) writeBurnSimCost(sim, tickNanos, peakBurning, windowStart, end);
            PILE_FOCUS.put(sim, densestFire(sim.fireField()));
            if (night) sim.getTopology().setNightLighting(NIGHT);
            return sim;
        }

        /** The middle of the 16-cell square with the most cells burning: the framings look at the fire, not the company. */
        private static float[] densestFire(FireField fire) {
            int bin = 16, w = fire.width(), bw = (w + bin - 1) / bin, bh = (fire.height() + bin - 1) / bin;
            int[] counts = new int[bw * bh];
            int best = 0;
            for (int i = 0, n = fire.burningCount(); i < n; i++) {
                int idx = fire.burningCell(i), b = (idx / w / bin) * bw + idx % w / bin;
                if (++counts[b] > counts[best]) best = b;
            }
            return new float[]{(best % bw) * bin + bin / 2f, (best / bw) * bin + bin / 2f};
        }

        /** A dark night with no lamps: whatever is lit is lit by the fire. */
        private static final NightLighting NIGHT = new NightLighting(0.78f, List.of(), Set.of());

        private static void writeBurnSimCost(BattleSimulation sim, List<Long> tickNanos, int peakBurning,
                                             int windowStart, int end) throws IOException {
            long[] sorted = tickNanos.stream().mapToLong(Long::longValue).sorted().toArray();
            double mean = Arrays.stream(sorted).average().orElse(0) / 1e6;
            double p99 = sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(0.99 * sorted.length) - 1)] / 1e6;
            double max = sorted.length == 0 ? 0 : sorted[sorted.length - 1] / 1e6;
            FireCost fire = sim.fireCostProfile();
            String md = String.format(Locale.ROOT, "# The Burn: simulation cost at its peak%n%n"
                    + "Seed 202, ticks %d..%d (%d..%d s), serial scheduler. Fire cost is over every tick with a fire "
                    + "since it was first lit (the field only records ticks with something burning or warm).%n%n"
                    + "| measure | mean ms | p99 ms | max ms |%n|---|---|---|---|%n"
                    + "| whole tick, window | %.3f | %.3f | %.3f |%n"
                    + "| fire share, whole battle | %.3f | %.3f | %.3f |%n%n"
                    + "Peak burning cells in the window: %d. Fire ticks recorded: %d (%d steps).%n",
                    windowStart, end, windowStart / 30, end / 30, mean, p99, max,
                    fire.meanMillis(), fire.p99Millis(), fire.maxMillis(),
                    peakBurning, fire.ticks(), fire.steps());
            Path out = Path.of(System.getProperty("render.evidence.outputDir", "build/reports/render"))
                    .toAbsolutePath().normalize();
            Files.createDirectories(out);
            Files.writeString(out.resolve("burn-sim.md"), md, StandardCharsets.UTF_8);
            System.out.println(md);
        }

        private static String resource(String path) throws Exception {
            try (InputStream stream = RenderBudgetEvidence.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing fixture " + path);
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static BattleSimulation control() throws Exception {
            ConquestBattleFixture c = canonical();
            return BattleSetup.createPlaceholder(c.seed(), c.manifest(),
                    c.enemyHasHeavyArmor(), OperationTier.REINFORCED, c.risk(),
                    MissionType.ASSAULT, c.targetProfile(),
                    FighterWingCommitment.toRoster(c.marineFighterSupport()),
                    FighterWingCommitment.toRoster(c.enemyFighterSupport()));
        }

        private static ConquestBattleFixture canonical() throws Exception {
            String json;
            try (InputStream stream =
                         RenderBudgetEvidence.class.getResourceAsStream(CANONICAL_CONQUEST)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing fixture " + CANONICAL_CONQUEST);
                }
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            BattleFixture parsed = BattleFixtureJson.fromJson(new JSONObject(json));
            BattleFixture construction = parsed instanceof BattleLaunchFixture launch
                    ? launch.construction() : parsed;
            return (ConquestBattleFixture) construction;
        }
    }

    // ---- the instrument's own two obligations --------------------------------

    /**
     * The world this profiles is whatever 600 ticks produced, so the playout has
     * to land in the same place every run.
     *
     * <p>It did not. Under {@link UnitUpdateSystem}'s production scheduler the
     * canonical Conquest collected 496, 567 and 537 {@code UNITS} commands on
     * three runs of unchanged code — four hundred seats is well over the
     * parallel-dispatch floor, so six hundred ticks of it is six hundred
     * chances for two workers to resolve in a different order, and the battle
     * that comes out is a different battle. The frame-to-frame assertion in
     * {@link #profile} cannot see that: the harness does not advance the
     * simulation between frames, so within one run the counts repeat perfectly
     * while meaning something different from the last run's.
     *
     * <p>Forced serial the counts are a fact about the code, which is the whole
     * point of an instrument a lever is judged against. This asserts rather than
     * sets it, because {@code UnitUpdateSystem} reads the property when a
     * battle's pool is constructed and a test that quietly set it here would
     * disagree with the Gradle task about what was measured.
     */
    private static void requireSerialScheduler() {
        int configured = UnitUpdateSystem.configuredMinimumParallelUnits();
        assertEquals(Integer.MAX_VALUE, configured,
                "render evidence must play its fixtures under the serial scheduler, or the "
                        + "world it measures differs run to run: set -D"
                        + UnitUpdateSystem.MINIMUM_PARALLEL_UNITS_PROPERTY + "="
                        + Integer.MAX_VALUE + " (the renderEvidence task does)");
    }

    /**
     * The same fixture, stood up a second time, collects the same commands.
     *
     * <p>This is the assertion the frame-to-frame one cannot make. It builds the
     * canonical Conquest (or the stress scene) again from the same fixture, plays it
     * the same number of ticks, profiles it at the same three framings, and compares every layer's
     * command count against the first stand-up's. A scheduler that has gone
     * non-deterministic again, or a collector that has acquired a dependency on
     * something outside the world, fails here and nowhere else.
     */
    private static void assertStandUpRepeats(BattleRenderer renderer, List<Row> first, int ticks, MapSpec spec)
            throws Exception {
        List<Row> repeat;
        try (BattleSimulation sim = spec.build()) {
            if (spec.playout()) play(sim, ticks);
            repeat = profile(spec, sim, renderer, 1, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        // Keyed rather than positional, and only where something was collected:
        // a layer that emitted nothing is kept or dropped from the report by a
        // timing threshold, which is a measurement and is allowed to move.
        Map<String, Integer> baseline = countsByLayer(first, spec.id());
        Map<String, Integer> second = countsByLayer(repeat, spec.id());
        for (String key : baseline.keySet()) {
            assertEquals(baseline.get(key), second.get(key),
                    "the same fixture stood up twice must collect the same commands: "
                            + spec.id() + " / " + key);
        }
        for (String key : second.keySet()) {
            assertEquals(baseline.get(key), second.get(key),
                    "a second stand-up of " + spec.id() + " collected in a layer the first did "
                            + "not: " + key);
        }
        System.out.println("[render-evidence] stand-up repeats: " + baseline.size()
                + " layer rows identical across two stand-ups of " + spec.id());
    }

    private static Map<String, Integer> countsByLayer(List<Row> rows, String mapId) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Row row : rows) {
            if (!row.map().equals(mapId)) continue;
            if (row.census().commands() == 0) continue;
            out.put(row.framing() + " / " + row.layer(), row.census().commands());
        }
        return out;
    }

    private static void play(BattleSimulation sim, int ticks) {
        // Neither map is being asked who won, and a battle that terminates stops
        // ticking everything in it — including the aircraft and effects the
        // profile is here to price.
        sim.setMissionCompletionEnabled(false);
        while (sim.getSimTickIndex() < ticks) {
            sim.advance(BattleSimulation.TICK_DT);
        }
    }

    // ---- the measurement -----------------------------------------------------

    private static List<Row> profile(MapSpec spec, BattleSimulation sim,
                                     BattleRenderer renderer, int frames,
                                     List<FrameCost> frameCosts,
                                     List<EqualityCheck> equalityChecks,
                                     List<String> runs) {
        int gridW = sim.getGrid().getWidth();
        int gridH = sim.getGrid().getHeight();
        String mapId = spec.id();
        BattleLayout layout = new BattleLayout(position(), gridW, gridH);
        List<Row> rows = new ArrayList<>();

        for (Framing framing : FRAMINGS) {
            BattleCamera camera = new BattleCamera(gridW, gridH);
            camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH,
                    layout.cellSize);
            zoomTo(camera, framing.zoom());
            float[] pile = MapSpec.PILE_FOCUS.get(sim);
            float[] focus = pile != null ? pile : marineCentroid(sim, gridW, gridH);
            camera.centerOn(focus[0], focus[1]);

            RenderContext rc = new RenderContext(sim, camera, layout, 1f, 0f, false,
                    null, null, BattleRenderHostProfile.STANDALONE_BATTLE, ANIMATION_CLOCK_SECONDS);

            for (int warmup = 0; warmup < WARMUP_FRAMES; warmup++) {
                GlFrameMarkers.push(mapId + " / " + framing.id() + " / warm-up " + warmup);
                drawFrame(renderer, rc, null);
                GlFrameMarkers.pop();
            }
            if (!framing.id().equals("close")) {
                DrawList collected = renderer.collectWorld(rc, ALL_LAYERS);
                for (RenderLayer layer : RUN_LAYERS) {
                    String line = runBreakdown(collected, layer, renderer);
                    if (line != null) runs.add("- **" + mapId + " / " + framing.id() + " / " + layer + "** — " + line);
                }
            }

            FrameCensus census = new FrameCensus();
            long[][] collect = new long[frames][];
            long[][] drain = new long[frames][];
            long[] wall = new long[frames];
            DrawCensus[] counts = new DrawCensus[RenderLayer.values().length];
            int firstTotal = -1;
            for (int frame = 0; frame < frames; frame++) {
                census.reset();
                GlFrameMarkers.push(mapId + " / " + framing.id() + " / frame " + frame);
                long started = System.nanoTime();
                drawFrame(renderer, rc, census);
                wall[frame] = System.nanoTime() - started;
                GlFrameMarkers.pop();
                collect[frame] = perLayer(census, true);
                drain[frame] = perLayer(census, false);
                int total = census.total().commands();
                if (frame == 0) {
                    firstTotal = total;
                    for (RenderLayer layer : RenderLayer.values()) {
                        DrawCensus copy = new DrawCensus();
                        copy.add(census.drain(layer));
                        counts[layer.ordinal()] = copy;
                    }
                } else {
                    assertEquals(firstTotal, total,
                            "the same world at the same framing must collect the same "
                                    + "commands every frame: " + mapId + " / " + framing.id());
                }
            }

            for (RenderLayer layer : RenderLayer.values()) {
                DrawCensus layerCounts = counts[layer.ordinal()];
                double collectMs = medianMs(collect, layer.ordinal());
                double drainMs = medianMs(drain, layer.ordinal());
                if (layerCounts.commands() == 0 && collectMs < 0.005 && drainMs < 0.005) continue;
                rows.add(new Row(mapId, framingId(framing, camera),
                        layer.name(), layerCounts, collectMs, drainMs));
            }
            double ours = 0;
            for (RenderLayer layer : RenderLayer.values()) {
                ours += medianMs(collect, layer.ordinal()) + medianMs(drain, layer.ordinal());
            }
            GroundMesh mesh = renderer.getGroundMesh();
            frameCosts.add(new FrameCost(mapId, framingId(framing, camera),
                    median(wall), ours, mesh.residentQuads(), mesh.bucketCount()));
            equalityChecks.add(new EqualityCheck(mapId, framingId(framing, camera), rc));
        }
        return rows;
    }

    /** One framing to compare culled against unculled, once the timing is done with. */
    private record EqualityCheck(String map, String framing, RenderContext context) { }

    // ---- what culling is not allowed to change -------------------------------

    /**
     * The culled frame and the unculled one are the same picture.
     *
     * <p>This is the whole acceptance for collect culling, and nothing else can
     * stand in for it. A command count says how much was collected and a timing
     * says how long it took; neither can see a body that stopped being drawn,
     * and a body wrongly rejected is silent by construction — the frame is
     * simply missing something, and it looks like a perfectly ordinary frame.
     *
     * <p>Both pictures come out of the shipping renderer at the same framing on
     * the same still world, one after the other in the same context, so the only
     * difference between them is whether the collectors rejected anything.
     */
    private static void assertCulledFrameIsIdentical(BattleRenderer renderer, RenderContext rc,
                                                     String mapId, String framing) {
        String previous = System.getProperty(ViewCull.PROPERTY);
        try {
            System.setProperty(ViewCull.PROPERTY, "true");
            FrameCensus culledCensus = new FrameCensus();
            readBack(renderer, rc, culledCensus, CULLED);

            System.setProperty(ViewCull.PROPERTY, "false");
            FrameCensus wholeCensus = new FrameCensus();
            readBack(renderer, rc, wholeCensus, WHOLE);

            // A control against measuring nothing: at a framing where culling
            // rejects nothing, an equal picture proves only that two identical
            // runs are identical. The whole-map framings genuinely reject
            // almost nothing, so this is asserted only where it can be.
            int culledCommands = culledCensus.total().commands();
            int wholeCommands = wholeCensus.total().commands();
            assertTrue(culledCommands <= wholeCommands,
                    "culling must never collect more than the control: " + mapId + " / "
                            + framing + " (" + culledCommands + " vs " + wholeCommands + ")");

            assertArrayEquals(WHOLE, CULLED,
                    "a culled frame must be pixel-identical to an unculled one: "
                            + mapId + " / " + framing + " (collected " + culledCommands
                            + " of " + wholeCommands + " commands)");
        } finally {
            if (previous == null) System.clearProperty(ViewCull.PROPERTY);
            else System.setProperty(ViewCull.PROPERTY, previous);
        }
    }

    /**
     * A body whose centre is off screen and whose sprite is not.
     *
     * <p>The framings above cut through a field of bodies and so contain plenty
     * of these by accident, which is worth something and is not the same as
     * asking the question. This puts one there on purpose: the camera is placed
     * so a chosen marine's centre sits exactly on the right edge of the viewport
     * and then walked out past it in half-cell steps, which sweeps the body from
     * wholly inside to wholly outside and takes in every partial overlap on the
     * way. Each step is compared against its own unculled control, so the step
     * where a margin was a fraction of a cell too small is the step that fails.
     */
    private static void assertStraddlingBodiesSurvive(BattleSimulation sim, BattleRenderer renderer,
                                                      BattleLayout layout, int gridW, int gridH,
                                                      String mapId) {
        long[] marines = sim.getRoster().factionDenseArray(Faction.MARINE);
        World world = sim.world();
        long chosen = 0;
        for (long id : marines) {
            if (world.isAlive(id)) { chosen = id; break; }
        }
        if (chosen == 0) return;

        BattleCamera camera = new BattleCamera(gridW, gridH);
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH,
                layout.cellSize);
        zoomTo(camera, BattleCamera.MAX_ZOOM);
        float bodyX = (float) world.x(chosen);
        float bodyY = (float) world.y(chosen);
        // Half the viewport to the left of the body puts the body on the right
        // edge; each further step walks the camera left, taking the body out.
        float halfViewCells = (camera.vpW() * 0.5f) / camera.cellPxSize();
        for (int step = 0; step <= 8; step++) {
            camera.centerOn(bodyX - halfViewCells - step * 0.5f, bodyY);
            RenderContext rc = new RenderContext(sim, camera, layout, 1f, 0f, false,
                    null, null, BattleRenderHostProfile.STANDALONE_BATTLE, ANIMATION_CLOCK_SECONDS);
            drawFrame(renderer, rc, null);
            assertCulledFrameIsIdentical(renderer, rc, mapId,
                    "straddle +" + (step * 0.5f) + " cells past the right edge");
        }
    }

    /**
     * One surface's worth of pixels, read into a buffer this class keeps.
     *
     * <p>Held rather than allocated per call: a frame is eight megabytes, and
     * the comparison wants two of them at once for every framing on both maps.
     */
    /**
     * The animation clock every frame here is drawn at. Left unset, a context falls back to the wall clock, and
     * the fire lights' flicker ({@code FireLight.clock}) moved between the culled readback and its unculled
     * control: the burning colony's whole-map frames differed by one channel level, on whichever run the
     * milliseconds happened to land across a rounding step. A still world needs a still clock as well.
     */
    private static final float ANIMATION_CLOCK_SECONDS = BattleReviewFrameRenderer.REVIEW_CLOCK_SECONDS;

    private static final ByteBuffer READ_BUFFER =
            BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
    private static final byte[] CULLED = new byte[SURFACE_W * SURFACE_H * 4];
    private static final byte[] WHOLE = new byte[SURFACE_W * SURFACE_H * 4];

    /**
     * The frame just read back, as a PNG under {@code frames/}: a lever's run and its control's are then the same
     * picture or they are not, which {@code RawFrameDump} can say pixel by pixel. The GL frame, not a review
     * frame, because a lever that only the GL drain sees is invisible to every Java2D picture.
     */
    private static void writeFrame(Path dir, String mapId, String framing) throws IOException {
        Files.createDirectories(dir);
        BufferedImage image = new BufferedImage(SURFACE_W, SURFACE_H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SURFACE_H; y++) {
            int row = (SURFACE_H - 1 - y) * SURFACE_W * 4;   // GL rows run bottom up
            for (int x = 0; x < SURFACE_W; x++) {
                int i = row + x * 4;
                image.setRGB(x, y, (WHOLE[i] & 0xFF) << 16 | (WHOLE[i + 1] & 0xFF) << 8 | (WHOLE[i + 2] & 0xFF));
            }
        }
        String name = (mapId + "-" + framing.substring(0, Math.max(0, framing.indexOf(" ("))))
                .replaceAll("[^A-Za-z0-9]+", "-") + ".png";
        ImageIO.write(image, "png", dir.resolve(name).toFile());
    }

    private static void readBack(BattleRenderer renderer, RenderContext rc,
                                 FrameCensus census, byte[] into) {
        drawFrame(renderer, rc, census);
        READ_BUFFER.clear();
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, READ_BUFFER);
        READ_BUFFER.get(into);
    }

    private static String framingId(Framing framing, BattleCamera camera) {
        return framing.id() + " (" + framing.what() + ", "
                + String.format(Locale.ROOT, "%.1f", camera.cellPxSize()) + " px/cell)";
    }

    private static void drawFrame(BattleRenderer renderer, RenderContext rc, FrameCensus census) {
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.renderWorld(rc, ALL_LAYERS, census);
        // The driver is free to buffer the whole frame, so without this the next
        // frame's clock starts while this one is still being drawn and the queue
        // grows until something blocks on it. Outside the census: the wait is
        // the GPU's work, and the census reports ours.
        glFinish();
    }

    /**
     * Where the company is, so a close framing frames the battle rather than an
     * empty corner of the map.
     *
     * <p>The map centre is not that: a Conquest lands its force at one edge and
     * puts its objective at the far one, so a camera at the centre at eight
     * times zoom looks at unoccupied city.
     */
    private static float[] marineCentroid(BattleSimulation sim, int gridW, int gridH) {
        long[] marines = sim.getRoster().factionDenseArray(Faction.MARINE);
        World world = sim.world();
        double sumX = 0;
        double sumY = 0;
        int counted = 0;
        for (long id : marines) {
            if (!world.isAlive(id)) continue;
            sumX += world.x(id);
            sumY += world.y(id);
            counted++;
        }
        if (counted == 0) return new float[]{gridW * 0.5f, gridH * 0.5f};
        return new float[]{(float) (sumX / counted), (float) (sumY / counted)};
    }

    /** Walks the camera to a zoom through its own control, which is the only way in. */
    static void zoomTo(BattleCamera camera, float target) {
        for (int step = 0; step < 200 && camera.zoom() < target - 1e-4f; step++) {
            camera.zoomAt(1f, camera.vpX() + camera.vpW() * 0.5f,
                    camera.vpY() + camera.vpH() * 0.5f);
        }
    }

    private static long[] perLayer(FrameCensus census, boolean collect) {
        long[] out = new long[RenderLayer.values().length];
        for (RenderLayer layer : RenderLayer.values()) {
            out[layer.ordinal()] = collect
                    ? census.collectNanos(layer) : census.drainNanos(layer);
        }
        return out;
    }

    private static double median(long[] nanos) {
        long[] values = nanos.clone();
        Arrays.sort(values);
        return values[values.length / 2] / 1_000_000.0;
    }

    private static double medianMs(long[][] samples, int layer) {
        long[] values = new long[samples.length];
        for (int i = 0; i < samples.length; i++) values[i] = samples[i][layer];
        Arrays.sort(values);
        return values[values.length / 2] / 1_000_000.0;
    }

    // ---- where a layer's runs break -------------------------------------------

    /** The layers whose runs are reported: the props, and the two that draw bodies. */
    private static final List<RenderLayer> RUN_LAYERS =
            List.of(RenderLayer.DOODADS, RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS);

    /**
     * What one layer's command stream is made of: each texture with the quads drawn from it, how many times the
     * run broke and on what, and every whole sprite by image. A run breaks wherever the drain must flush — a
     * different sheet, a solid fill or line between quads, a sprite drawn through its own call — and the binds
     * column cannot say which of those it was.
     */
    private static String runBreakdown(DrawList list, RenderLayer layer, BattleRenderer renderer) {
        DrawCommand[] buffer = list.buffer(layer);
        int count = list.count(layer);
        if (count == 0) return null;
        Map<String, Integer> quadsBySheet = new TreeMap<>();
        Map<String, Integer> breaks = new TreeMap<>();
        Map<String, Integer> sprites = new TreeMap<>();
        Object previous = null;
        for (int i = 0; i < count; i++) {
            DrawCommand c = buffer[i];
            Object key;
            String reason;
            switch (c.kind()) {
                case SHEET_QUAD -> {
                    key = c.sprite();
                    reason = "sheet change";
                    quadsBySheet.merge(sheetName(c.sprite(), renderer), 1, Integer::sum);
                }
                case MESH -> {
                    key = c.sprite() != null ? c.sprite() : c.mesh();
                    reason = "mesh";
                    quadsBySheet.merge(c.sprite() != null ? sheetName(c.sprite(), renderer) : "(mesh images)",
                            1, Integer::sum);
                }
                case SOLID_RECT, POLY -> { key = "solid"; reason = "solid fill"; }
                case LINE -> { key = "line@" + c.angleDegrees(); reason = "line"; }
                case RIBBON -> { key = "ribbon"; reason = "ribbon"; }
                case SPRITE -> {
                    key = new Object();
                    reason = c.additive() ? "additive sprite" : "sprite";
                    String name = sheetName(c.sprite(), renderer) + (c.additive() ? " (additive)" : "");
                    sprites.merge(name, 1, Integer::sum);
                }
                default -> { key = new Object(); reason = "custom"; }
            }
            if (previous != null && !key.equals(previous)) breaks.merge(reason, 1, Integer::sum);
            previous = key;
        }
        StringBuilder out = new StringBuilder();
        out.append(count).append(" commands; breaks ").append(breaks.isEmpty() ? "none" : breaks.toString());
        if (!quadsBySheet.isEmpty()) out.append("; quads by sheet ").append(quadsBySheet);
        if (!sprites.isEmpty()) out.append("; whole sprites ").append(sprites);
        return out.toString();
    }

    private static String sheetName(SpriteAPI sheet, BattleRenderer renderer) {
        if (sheet == null) return "(none)";
        if (sheet == renderer.getGroundAtlas().sheet()) return "ground atlas";
        if (sheet == renderer.getUnitAtlas().sheet()) return "unit atlas";
        DecorationAtlas decoration = renderer.getDecorationAtlas();
        if (decoration != null && sheet == decoration.sheet()) return "decoration atlas";
        String name = sheet.toString();
        int open = name.indexOf('[');
        if (open >= 0 && name.endsWith("]")) name = name.substring(open + 1, name.length() - 1);
        // The file and its folder: a family's body.png is only a name with the folder it is in.
        int slash = name.lastIndexOf('/');
        int parent = slash > 0 ? name.lastIndexOf('/', slash - 1) : -1;
        return name.substring(parent + 1);
    }

    // ---- the report ----------------------------------------------------------

    private static String markdown(List<Row> rows, List<FrameCost> frameCosts,
                                   int ticks, int frames, List<String> atlases, List<String> runs) {
        StringBuilder out = new StringBuilder();
        out.append("# Render budget\n\n");
        out.append("Collected and drained through the shipping `BattleRenderer` on a real\n")
                .append("OpenGL context, ").append(SURFACE_W).append('x').append(SURFACE_H)
                .append(", after ").append(ticks).append(" ticks of battle. Times are the\n")
                .append("median of ").append(frames)
                .append(" frames after warm-up and are **CPU** cost — building the\n")
                .append("command stream and submitting it. The GPU's own work and the game's\n")
                .append("frame around ours are not in these numbers.\n\n");
        if (rows.stream().anyMatch(row -> row.map().equals(MapSpec.STRESS_ID))) {
            out.append("`").append(MapSpec.STRESS_ID).append("` is not played: it is stood up dense and settled for ")
                    .append(RenderStressScene.SETTLE_TICKS).append(" ticks (`RenderStressScene`).\n\n");
        }
        out.append("A `CUSTOM` pass owns its GL, so its draws and binds are its own and are\n")
                .append("not counted in the draw-call column; its time is.\n\n");
        out.append("Levers this run: decoration atlas **")
                .append(DecorationAtlas.enabled() ? "on" : "off")
                .append("**, creep **")
                .append(CreepLayer.enabled() ? "on" : "off")
                .append("**, fire **")
                .append(FireRenderSystem.enabled() ? "on" : "off")
                .append("**, collect culling **")
                .append(ViewCull.enabled() ? "on" : "off").append("**.\n\n");

        out.append("## Atlases\n\n")
                .append("What each atlas holds, and everything it left out and why. An image left out\n")
                .append("draws from its own texture: the same picture, one more bind.\n\n");
        for (String atlas : atlases) out.append("- ").append(atlas).append('\n');
        out.append('\n');

        out.append("## Whole frames\n\n")
                .append("Wall clock with the GPU waited on, against the sum of our own\n")
                .append("per-layer time. The gap is the driver and the card.\n\n")
                .append("| map | framing | frame ms | our ms | resident quads | ground buffers |\n")
                .append("|---|---|--:|--:|--:|--:|\n");
        for (FrameCost cost : frameCosts) {
            out.append("| ").append(cost.map())
                    .append(" | ").append(cost.framing())
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f", cost.frameMs()))
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f", cost.ourMs()))
                    .append(" | ").append(cost.residentQuads())
                    .append(" | ").append(cost.meshBuffers())
                    .append(" |\n");
        }

        String map = null;
        String framing = null;
        for (Row row : rows) {
            if (!row.map().equals(map)) {
                map = row.map();
                framing = null;
                out.append("\n## ").append(map).append("\n");
            }
            if (!row.framing().equals(framing)) {
                framing = row.framing();
                out.append("\n### ").append(framing).append("\n\n")
                        .append("| layer | commands | sheet quads | sprites | custom "
                                + "| draws | binds | collect ms | drain ms |\n")
                        .append("|---|--:|--:|--:|--:|--:|--:|--:|--:|\n");
            }
            DrawCensus c = row.census();
            out.append("| ").append(row.layer())
                    .append(" | ").append(c.commands())
                    .append(" | ").append(c.sheetQuads())
                    .append(" | ").append(c.sprites())
                    .append(" | ").append(c.customs())
                    .append(" | ").append(c.drawCalls())
                    .append(" | ").append(c.textureBinds())
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f", row.collectMs()))
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f", row.drainMs()))
                    .append(" |\n");
        }

        out.append("\n## Where the runs break\n\n")
                .append("Mid and whole-map framings. A break is a flush the drain had to make: a\n")
                .append("different sheet, a fill or a line between quads, or a whole sprite.\n\n");
        for (String run : runs) out.append(run).append('\n');

        out.append("\n## The ceiling\n\n");
        for (String scope : rows.stream().map(Row::map).distinct().toList()) {
            for (String frameId : rows.stream().filter(r -> r.map().equals(scope))
                    .map(Row::framing).distinct().toList()) {
                List<Row> group = rows.stream()
                        .filter(r -> r.map().equals(scope) && r.framing().equals(frameId))
                        .toList();
                double total = group.stream().mapToDouble(r -> r.collectMs() + r.drainMs()).sum();
                Row worst = group.stream()
                        .max((a, b) -> Double.compare(a.collectMs() + a.drainMs(),
                                b.collectMs() + b.drainMs())).orElseThrow();
                out.append("- **").append(scope).append(" / ").append(frameId).append("** — ")
                        .append(String.format(Locale.ROOT, "%.2f ms", total))
                        .append(" of our own, of which ").append(worst.layer()).append(" is ")
                        .append(String.format(Locale.ROOT, "%.2f ms (%.0f%%)",
                                worst.collectMs() + worst.drainMs(),
                                100.0 * (worst.collectMs() + worst.drainMs())
                                        / Math.max(1e-6, total)))
                        .append(", ").append(String.format(Locale.ROOT, "%.2f collect / %.2f drain",
                                worst.collectMs(), worst.drainMs()))
                        .append(".\n");
            }
        }
        return out.toString();
    }

    private static String json(List<Row> rows, List<FrameCost> frameCosts,
                               int ticks, int frames, List<String> atlases) throws Exception {
        JSONObject root = new JSONObject();
        root.put("surfaceWidth", SURFACE_W);
        root.put("surfaceHeight", SURFACE_H);
        root.put("ticks", ticks);
        root.put("frames", frames);
        root.put("decorationAtlas", DecorationAtlas.enabled());
        root.put("atlasReports", new JSONArray(atlases));
        root.put("creep", CreepLayer.enabled());
        root.put("fire", FireRenderSystem.enabled());
        root.put("collectCulling", ViewCull.enabled());
        List<JSONObject> costs = new ArrayList<>();
        for (FrameCost cost : frameCosts) {
            JSONObject entry = new JSONObject();
            entry.put("map", cost.map());
            entry.put("framing", cost.framing());
            entry.put("frameMs", cost.frameMs());
            entry.put("ourMs", cost.ourMs());
            entry.put("residentQuads", cost.residentQuads());
            entry.put("groundBuffers", cost.meshBuffers());
            costs.add(entry);
        }
        root.put("frames_measured", costs);
        List<JSONObject> encoded = new ArrayList<>();
        for (Row row : rows) {
            JSONObject entry = new JSONObject();
            entry.put("map", row.map());
            entry.put("framing", row.framing());
            entry.put("layer", row.layer());
            entry.put("commands", row.census().commands());
            entry.put("sheetQuads", row.census().sheetQuads());
            entry.put("solidRects", row.census().solidRects());
            entry.put("polygons", row.census().polygons());
            entry.put("lines", row.census().lines());
            entry.put("ribbons", row.census().ribbons());
            entry.put("sprites", row.census().sprites());
            entry.put("customs", row.census().customs());
            entry.put("drawCalls", row.census().drawCalls());
            entry.put("textureBinds", row.census().textureBinds());
            entry.put("collectMs", row.collectMs());
            entry.put("drainMs", row.drainMs());
            encoded.add(entry);
        }
        root.put("rows", encoded);
        return root.toString(2);
    }

    // ---- surface -------------------------------------------------------------

    /**
     * The fixed-function state the game's battle screen draws under: a pixel
     * ortho with the origin bottom-left, matching the screen-space coordinates
     * every collector emits.
     */
    static void configureSurface() {
        glViewport(0, 0, SURFACE_W, SURFACE_H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glClearColor(0f, 0f, 0f, 1f);
    }

    /**
     * Just enough {@link SettingsAPI} for the ground composite to size its own
     * targets.
     *
     * <p>The surface-relief pipeline reads the screen off the game rather than
     * off the camera, so without this the GROUND redirect throws and the layer
     * the profile most wants to price is the one layer it cannot run. Everything
     * else answers as it does when there is no game at all — a texture load
     * hands back null, and every consumer of one already takes that path.
     */
    static SettingsAPI screenSettings() {
        return (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getScreenWidth" -> (float) SURFACE_W;
                    case "getScreenHeight" -> (float) SURFACE_H;
                    case "getScreenScaleMult" -> 1f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "HeadlessSettings";
                    default -> HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                });
    }

    static PositionAPI position() {
        return (PositionAPI) Proxy.newProxyInstance(
                PositionAPI.class.getClassLoader(), new Class<?>[]{PositionAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX", "getY" -> 0f;
                    case "getWidth" -> (float) SURFACE_W;
                    case "getHeight" -> (float) SURFACE_H;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
