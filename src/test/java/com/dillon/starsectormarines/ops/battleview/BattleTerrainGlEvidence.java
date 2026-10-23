package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.GameLikeTextureStore;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * A battle loads only the terrain its map draws ({@code battle-textures.md}): on a real driver,
 * through the shipping loaders, each battle's load queue is run twice over — once loading every
 * catalog sheet as a battle did before ({@code all}), once loading {@link BattleTerrainSheets} —
 * and each reports megabytes held, loading time, its longest single step and what that step
 * loaded, and the filtered battle's first frames report anything loaded on first ask. A
 * measurement, not a gate: a miss is a hitch the dev build logs, and the rules that pick the set
 * are tested at their unit ({@code BattleTerrainSheetsTest}). It plays nothing, so it costs the
 * machine little more than the loads it measures.
 *
 * <p>Writes {@code build/reports/battle-terrain/summary.md}. Times are wall clock on a shared
 * machine: each mode runs twice and the second is reported, so both read from a warm file cache.
 */
@Tag("shader-evidence")
class BattleTerrainGlEvidence {

    private static final int W = 1920;
    private static final int H = 1080;

    private record Battle(String id, Callable<BattleSimulation> build) { }

    private record Load(String battle, String mode, int terrainSheets, int textures, double heldMb,
                        double loadMs, double atlasMs, double firstFrameMs, double longestMs, String longest) { }

    @Test
    void eachBattleLoadsOnlyItsOwnTerrain() throws Exception {
        try (HeadlessGl gl = HeadlessGl.createOrNull(W, H)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            RenderBudgetEvidence.configureSurface();
            DiskRegistries.install();
            List<Path> roots = new ArrayList<>();
            roots.add(Path.of("mod").toAbsolutePath());
            String install = System.getProperty("starsectorDir");
            if (install != null) roots.add(Path.of(install, "starsector-core"));
            GameLikeTextureStore store = new GameLikeTextureStore(roots).screen(W, H);
            SettingsAPI previous = Global.getSettings();
            Global.setSettings(store.settings());
            try {
                run();
            } finally {
                Global.setSettings(previous);
            }
        }
    }

    private static List<Battle> battles() {
        List<Battle> battles = new ArrayList<>();
        battles.add(new Battle("small battle (40x30)", () -> SceneBuilder.openGround(40, 30)
                .squad("infantry").size(4).inFile(4, 4, 1, 0).done()
                .squad("lance").size(1).mech(MechVariant.HOUND, MechRole.BALANCED).inFile(10, 10, 1, 0).done()
                .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 36, 26).build().sim()));
        for (RenderBudgetEvidence.MapSpec spec : RenderBudgetEvidence.MapSpec.MATRIX) {
            if (spec.id().equals("560x336 conquest") || spec.id().equals("400x240 colony")
                    || spec.id().equals("400x240 the burn")) {
                battles.add(new Battle(spec.id(), spec::build));
            }
        }
        String only = System.getProperty("battle.terrain.evidence.maps");
        if (only != null && !only.isBlank()) {
            List<String> words = List.of(only.toLowerCase(Locale.ROOT).split(","));
            battles.removeIf(b -> words.stream().noneMatch(w -> b.id().toLowerCase(Locale.ROOT).contains(w.trim())));
        }
        return battles;
    }

    private static void run() throws Exception {
        TextureLedger ledger = TextureLedger.shared();
        List<Load> loads = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        List<String> residue = new ArrayList<>();
        Set<String> sessionBefore = new HashSet<>(ledger.session().paths());
        for (Battle battle : battles()) {
            long started = System.nanoTime();
            try (BattleSimulation sim = battle.build().call()) {
                System.out.printf("[shader-evidence] %s stood up in %.1f s%n", battle.id(), (System.nanoTime() - started) / 1e9);
                Set<String> derived = BattleTerrainSheets.of(sim);
                Load all = null, filtered = null;
                for (int pass = 0; pass < 2; pass++) {
                    all = load(battle.id(), "all", sim, null, ledger, false, misses);
                    filtered = load(battle.id(), "derived", sim, derived, ledger, pass == 1, misses);
                }
                loads.add(all);
                loads.add(filtered);
                Set<String> left = new LinkedHashSet<>(ledger.session().paths());
                left.removeAll(sessionBefore);
                if (!left.isEmpty()) residue.add(battle.id() + ": " + left);
            }
        }
        String md = markdown(loads, misses, residue);
        Path out = Path.of("build/reports/battle-terrain").toAbsolutePath();
        Files.createDirectories(out);
        Files.writeString(out.resolve("summary.md"), md, StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("[shader-evidence] wrote " + out.resolve("summary.md"));
    }

    /**
     * One battle's load queue under a fresh lease, its atlases and a first whole-map frame; with
     * {@code record}, a close frame too, and every load the set did not plan recorded.
     */
    private static Load load(String id, String mode, BattleSimulation sim, Set<String> terrain,
                             TextureLedger ledger, boolean record, List<String> misses) throws Exception {
        TextureLease lease = ledger.lease("battle");
        BattleSprites sprites = new BattleSprites(lease);
        double loadMs = 0, longestMs = 0;
        String longest = "";
        for (TextureLoadQueue.Step step : sprites.battleLoadSteps(BattleTextureSet.of(sim), terrain, key -> key)) {
            Set<String> had = new HashSet<>(lease.paths());
            long t = System.nanoTime();
            step.work().run();
            double ms = (System.nanoTime() - t) / 1e6;
            loadMs += ms;
            if (ms > longestMs) {
                longestMs = ms;
                Set<String> added = new LinkedHashSet<>(lease.paths());
                added.removeAll(had);
                longest = step.stage() + ": " + summarize(added);
            }
        }
        int terrainSheets = sprites.terrainSheetCount();
        int textures = lease.paths().size();
        double heldMb = lease.bytesHeld() / 1e6;

        trace(id + " " + mode + ": loaded");
        BattleRenderer renderer = new BattleRenderer(sprites);
        long t = System.nanoTime();
        renderer.buildTileBatches();
        renderer.onAttach();
        double atlasMs = (System.nanoTime() - t) / 1e6;
        trace(id + " " + mode + ": atlases built");
        int gridW = sim.getGrid().getWidth(), gridH = sim.getGrid().getHeight();
        t = System.nanoTime();
        draw(renderer, sim, gridW, gridH, false);
        double firstFrameMs = (System.nanoTime() - t) / 1e6;
        trace(id + " " + mode + ": first frame drawn");
        try {
            if (record) {
                draw(renderer, sim, gridW, gridH, true);
                for (String miss : sprites.unplannedLoads()) misses.add(id + ": " + miss);
            }
        } finally {
            renderer.disposeGlResources();
            lease.release();
        }
        return new Load(id, mode, terrainSheets, textures, heldMb, loadMs, atlasMs, firstFrameMs, longestMs, longest);
    }

    /** One frame: the whole map, or close on the middle of the marines. */
    private static void draw(BattleRenderer renderer, BattleSimulation sim, int gridW, int gridH, boolean close) {
        BattleLayout layout = new BattleLayout(RenderBudgetEvidence.position(), gridW, gridH);
        BattleCamera camera = new BattleCamera(gridW, gridH);
        camera.setViewport(layout.gridX, layout.gridY, layout.gridW, layout.gridH, layout.cellSize);
        if (close) {
            RenderBudgetEvidence.zoomTo(camera, BattleCamera.MAX_ZOOM);
            float[] centre = centroid(sim, gridW, gridH);
            camera.centerOn(centre[0], centre[1]);
        } else {
            camera.centerOn(gridW * 0.5f, gridH * 0.5f);
        }
        RenderContext rc = new RenderContext(sim, camera, layout, 1f, 0f, false, null, null,
                BattleRenderHostProfile.STANDALONE_BATTLE, BattleReviewFrameRenderer.REVIEW_CLOCK_SECONDS);
        glClear(GL_COLOR_BUFFER_BIT);
        renderer.renderWorld(rc);
        glFinish();
    }

    private static float[] centroid(BattleSimulation sim, int gridW, int gridH) {
        double x = 0, y = 0;
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE) continue;
            x += sim.world().cellX(unit);
            y += sim.world().cellY(unit);
            n++;
        }
        return n == 0 ? new float[]{gridW * 0.5f, gridH * 0.5f} : new float[]{(float) (x / n), (float) (y / n)};
    }

    private static void trace(String what) {
        if (Boolean.getBoolean("battle.terrain.evidence.trace")) {
            System.out.println("[shader-evidence] " + what);
            System.out.flush();
        }
    }

    private static String summarize(Set<String> paths) {
        if (paths.isEmpty()) return "(nothing new)";
        List<String> names = new ArrayList<>();
        for (String path : paths) names.add(path.substring(path.lastIndexOf('/') + 1));
        return names.size() <= 4 ? String.join(", ", names)
                : String.join(", ", names.subList(0, 4)) + " and " + (names.size() - 4) + " more";
    }

    private static String markdown(List<Load> loads, List<String> misses, List<String> residue) {
        StringBuilder md = new StringBuilder("# Battle terrain loads\n\n");
        md.append("`all` loads every catalog sheet, as battles did; `derived` loads `BattleTerrainSheets`. ")
                .append("Load is the queue's steps (the loading screen's work before the atlas step); ")
                .append("atlas is `buildTileBatches` + `onAttach`; first frame is a whole-map draw.\n\n");
        md.append("| Battle | Mode | Catalog sheets | Textures | Held MB | Load ms | Atlas ms | First frame ms | Longest step ms | Longest step |\n");
        md.append("|---|---|--:|--:|--:|--:|--:|--:|--:|---|\n");
        for (Load l : loads) {
            md.append(String.format(Locale.ROOT, "| %s | %s | %d | %d | %.1f | %.0f | %.0f | %.0f | %.0f | %s |%n",
                    l.battle(), l.mode(), l.terrainSheets(), l.textures(), l.heldMb(), l.loadMs(), l.atlasMs(),
                    l.firstFrameMs(), l.longestMs(), l.longest()));
        }
        md.append("\n## Loaded on first ask (the derived set's misses)\n\n");
        if (misses.isEmpty()) md.append("None.\n");
        for (String miss : misses) md.append("- ").append(miss).append('\n');
        md.append("\n## Loaded under the session lease during a battle (never released)\n\n");
        if (residue.isEmpty()) md.append("None.\n");
        for (String line : residue) md.append("- ").append(line).append('\n');
        return md.toString();
    }
}
