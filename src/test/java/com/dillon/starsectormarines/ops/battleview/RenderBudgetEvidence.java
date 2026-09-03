package com.dillon.starsectormarines.ops.battleview;

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
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCensus;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.json.JSONObject;
import org.lwjgl.BufferUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
            try (GlSpriteTokens tokens = new GlSpriteTokens()) {
                HeadlessBattleSprites sprites = new HeadlessBattleSprites(modRoot, tokens);
                sprites.ensureUnitSheets();
                sprites.ensureEngineFxSprites();
                BattleRenderer renderer = new BattleRenderer(sprites);
                renderer.onAttach();
                renderer.buildTileBatches();

                for (MapSpec spec : MapSpec.MATRIX) {
                    try (BattleSimulation sim = spec.build()) {
                        play(sim, ticks);
                        List<EqualityCheck> checks = new ArrayList<>();
                        rows.addAll(profile(spec, sim, renderer, frames, frameCosts, checks));
                        // After every timed frame on this map, never between
                        // them: a readback is eight megabytes off the card and
                        // an allocation to match, and interleaved it landed in
                        // the next framing's measurement as noise several times
                        // the effect being measured.
                        for (EqualityCheck check : checks) {
                            assertCulledFrameIsIdentical(renderer, check.context(),
                                    check.map(), check.framing());
                        }
                        assertStraddlingBodiesSurvive(sim, renderer,
                                new BattleLayout(position(), sim.getGrid().getWidth(),
                                        sim.getGrid().getHeight()),
                                sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                                spec.id());
                    }
                }
                assertStandUpRepeats(renderer, rows, ticks);
                System.out.println("[render-evidence] textures uploaded: "
                        + tokens.uploadedTextures());
            }

            String markdown = markdown(rows, frameCosts, ticks, frames);
            Files.writeString(output.resolve("summary.md"), markdown, StandardCharsets.UTF_8);
            Files.writeString(output.resolve("summary.json"),
                    json(rows, frameCosts, ticks, frames), StandardCharsets.UTF_8);
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
    private record MapSpec(String id, Builder builder) {

        interface Builder {
            BattleSimulation build() throws Exception;
        }

        static final String CANONICAL_CONQUEST =
                "/battle-fixtures/conquest-reinforced-south-v3.json";

        static final List<MapSpec> MATRIX = List.of(
                new MapSpec("280x168 control", MapSpec::control),
                new MapSpec("560x336 conquest", MapSpec::conquest));

        BattleSimulation build() throws Exception {
            return builder.build();
        }

        private static BattleSimulation conquest() throws Exception {
            return canonical().build();
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
     * canonical Conquest again from the same fixture, plays it the same number of
     * ticks, profiles it at the same three framings, and compares every layer's
     * command count against the first stand-up's. A scheduler that has gone
     * non-deterministic again, or a collector that has acquired a dependency on
     * something outside the world, fails here and nowhere else.
     */
    private static void assertStandUpRepeats(BattleRenderer renderer, List<Row> first, int ticks)
            throws Exception {
        MapSpec spec = new MapSpec("560x336 conquest", MapSpec::conquest);
        List<Row> repeat;
        try (BattleSimulation sim = spec.build()) {
            play(sim, ticks);
            repeat = profile(spec, sim, renderer, 1, new ArrayList<>(), new ArrayList<>());
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
                                     List<EqualityCheck> equalityChecks) {
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
            float[] focus = marineCentroid(sim, gridW, gridH);
            camera.centerOn(focus[0], focus[1]);

            RenderContext rc = new RenderContext(sim, camera, layout, 1f, 0f, false,
                    null, null, BattleRenderHostProfile.STANDALONE_BATTLE);

            for (int warmup = 0; warmup < WARMUP_FRAMES; warmup++) drawFrame(renderer, rc, null);

            FrameCensus census = new FrameCensus();
            long[][] collect = new long[frames][];
            long[][] drain = new long[frames][];
            long[] wall = new long[frames];
            DrawCensus[] counts = new DrawCensus[RenderLayer.values().length];
            int firstTotal = -1;
            for (int frame = 0; frame < frames; frame++) {
                census.reset();
                long started = System.nanoTime();
                drawFrame(renderer, rc, census);
                wall[frame] = System.nanoTime() - started;
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
                    null, null, BattleRenderHostProfile.STANDALONE_BATTLE);
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
    private static final ByteBuffer READ_BUFFER =
            BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
    private static final byte[] CULLED = new byte[SURFACE_W * SURFACE_H * 4];
    private static final byte[] WHOLE = new byte[SURFACE_W * SURFACE_H * 4];

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
    private static void zoomTo(BattleCamera camera, float target) {
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

    // ---- the report ----------------------------------------------------------

    private static String markdown(List<Row> rows, List<FrameCost> frameCosts,
                                   int ticks, int frames) {
        StringBuilder out = new StringBuilder();
        out.append("# Render budget\n\n");
        out.append("Collected and drained through the shipping `BattleRenderer` on a real\n")
                .append("OpenGL context, ").append(SURFACE_W).append('x').append(SURFACE_H)
                .append(", after ").append(ticks).append(" ticks of battle. Times are the\n")
                .append("median of ").append(frames)
                .append(" frames after warm-up and are **CPU** cost — building the\n")
                .append("command stream and submitting it. The GPU's own work and the game's\n")
                .append("frame around ours are not in these numbers.\n\n");
        out.append("A `CUSTOM` pass owns its GL, so its draws and binds are its own and are\n")
                .append("not counted in the draw-call column; its time is.\n\n");
        out.append("Levers this run: zoom gates **")
                .append(ZoomDetail.enabled() ? "on" : "off")
                .append("**, resident ground mesh **")
                .append(GroundMesh.enabled() ? "on" : "off")
                .append("**, resident relief fields **")
                .append(ReliefFieldMesh.enabled() ? "on" : "off")
                .append("**, resident fog field **")
                .append(FogField.enabled() ? "on" : "off")
                .append("**, ground atlas **")
                .append(GroundAtlas.enabled() ? "on" : "off")
                .append("**, resident decoration **")
                .append(GroundMesh.decorationEnabled() ? "on" : "off")
                .append("**, unit atlas **")
                .append(UnitAtlas.enabled() ? "on" : "off")
                .append("**, resident roofs **")
                .append(RoofMesh.enabled() ? "on" : "off")
                .append("**, collect culling **")
                .append(ViewCull.enabled() ? "on" : "off").append("**.\n\n");

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
                               int ticks, int frames) throws Exception {
        JSONObject root = new JSONObject();
        root.put("surfaceWidth", SURFACE_W);
        root.put("surfaceHeight", SURFACE_H);
        root.put("ticks", ticks);
        root.put("frames", frames);
        root.put("zoomGates", ZoomDetail.enabled());
        root.put("groundMesh", GroundMesh.enabled());
        root.put("residentRelief", ReliefFieldMesh.enabled());
        root.put("fogField", FogField.enabled());
        root.put("groundAtlas", GroundAtlas.enabled());
        root.put("residentDecoration", GroundMesh.decorationEnabled());
        root.put("unitAtlas", UnitAtlas.enabled());
        root.put("residentRoofs", RoofMesh.enabled());
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
    private static void configureSurface() {
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
    private static SettingsAPI screenSettings() {
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

    private static PositionAPI position() {
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
