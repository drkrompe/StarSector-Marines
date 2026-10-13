package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.FleetArmoryViewModel;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Opt-in generated-range replay. Default tests remain small, simulation-free units. */
public final class ArmoryRangeSnapshotSuite implements SnapshotSuite {
    public String id() { return "armory-range"; }
    public String label() { return "Disposable armory firing-range trial"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        var ship = new CompanyShip(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                100, 600, 400, .45f, null, null);
        var generator = new ShipDeckGenerator();
        var map = generator.generateDeck(ship.deckPlan(), 43L, null);
        var room = generator.getLastDeckGraph().compartments().stream()
                .filter(r -> r.purpose() == RoomPurpose.FIRING_RANGE)
                .filter(r -> !ArmoryRangeTrial.lanes(map, r).isEmpty()).findFirst().orElseThrow();
        var lane = ArmoryRangeTrial.lanes(map, room).get(0);
        int[] before = new int[map.grid.getWidth() * map.grid.getHeight()];
        for (int y = 0; y < map.grid.getHeight(); y++) for (int x = 0; x < map.grid.getWidth(); x++)
            before[y * map.grid.getWidth() + x] = map.grid.getWallHp(x, y);
        var roster = new MarineRoster();
        roster.bootstrapInitialComplement(12);
        var model = new FleetArmoryViewModel(new Reactor(), roster);
        var candidate = model.rangeCandidate();
        if (candidate == null) throw new AssertionError("Starting marine cannot test issued kit");
        List<SnapshotArtifact> frames = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var trial = new ArmoryRangeTrial(map, room, candidate.name(), candidate.loadout())) {
                var sim = trial.simulation();
                if (!sim.directControl().enter(trial.marine())) throw new AssertionError("Manual entry failed");
                float aimX = lane.fixtureX() - room.left() + 1.5f;
                float aimY = lane.fixtureY() - room.top() + 1.5f;
                float hp = sim.world().hp(trial.marine());
                long target = sim.getRoster().factionDenseArray(Faction.DEFENDER)[0];
                float targetHp = sim.world().hp(target);
                int shots = 0;
                // A service rifle at nineteen cells misses often; observe a full firing string.
                for (int tick = 0; tick < 3600; tick++) {
                    sim.directControl().submit(new ManualIntent(0, 0, aimX, aimY, true));
                    sim.advance(1f / 30f);
                    shots += sim.getShotsThisFrame().size();
                    if (attempt == 0 && (tick == 0 || tick == 45 || tick == 149))
                        frames.add(new SnapshotArtifact("range-" + tick + ".png", frame(renderer, sim)));
                }
                if (shots == 0) throw new AssertionError("Manual trigger never fired down the fitted lane");
                if (sim.world().hp(target) >= targetHp) throw new AssertionError("Rounds never reached the target: " + shots);
                if (sim.isComplete() || sim.world().hp(trial.marine()) != hp)
                    throw new AssertionError("Trial completed or target attacked marine");
                float x = sim.world().renderX(trial.marine()), y = sim.world().renderY(trial.marine());
                float dx = x - aimX, dy = y - aimY, length = (float) Math.hypot(dx, dy);
                for (int tick = 0; tick < 15; tick++) {
                    sim.directControl().submit(new ManualIntent(dx / length, dy / length, aimX, aimY, false));
                    sim.advance(1f / 30f);
                }
                if (Math.hypot(sim.world().renderX(trial.marine()) - x,
                        sim.world().renderY(trial.marine()) - y) < .05f)
                    throw new AssertionError("Manual movement could not leave the firing point");
                sim.directControl().exit();
                if (sim.directControl().active()) throw new AssertionError("Manual exit failed");
                System.out.println("Range trial " + attempt + ": " + shots + " shots; manual entry/exit passed");
            }
        }
        for (int y = 0; y < map.grid.getHeight(); y++) for (int x = 0; x < map.grid.getWidth(); x++)
            if (before[y * map.grid.getWidth() + x] != map.grid.getWallHp(x, y))
                throw new AssertionError("Trial mutated the cached deck");
        return frames;
    }

    private BufferedImage frame(HeadlessUiRenderer renderer, BattleSimulation sim) throws Exception {
        BattleCamera camera = new BattleCamera(sim.getGrid().getWidth(), sim.getGrid().getHeight());
        camera.setViewport(0, 0, 1920, 1080,
                Math.min(1920f / sim.getGrid().getWidth(), 900f / sim.getGrid().getHeight()));
        long marine = sim.directControl().activeUnitId();
        camera.centerOn(sim.world().renderX(marine), sim.world().renderY(marine));
        return renderer.renderHostPass(new BattleSceneHostPass() {
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                return new BattleSceneFrame(new RenderContext(sim, camera, null, alpha, 0, false,
                        null, null, BattleRenderHostProfile.EMBEDDED_SCENE), EnumSet.of(
                        RenderLayer.GROUND, RenderLayer.DECALS, RenderLayer.DOODADS,
                        RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS, RenderLayer.SHOTS));
            }
            public void draw(CanvasHostViewport viewport, float alpha) {
                throw new UnsupportedOperationException("Headless range evidence");
            }
        }, 1920, 1080);
    }
}
