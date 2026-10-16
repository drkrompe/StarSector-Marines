package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Technical and freight silhouettes, registered wreck swaps and real damage effects on Thunder Run. */
public final class FreightVehiclesSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "freight-vehicles"; }
    @Override public String label() { return "Technical and industrial hauler live/wreck review"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        NavigationGrid grid = new NavigationGrid(64, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
        try (var sim = new BattleSimulation(grid, new CellTopology(64, 40))) {
            var labels = ReviewAnnotations.builder();
            VehicleType[] types = {VehicleType.MILITIA_TECHNICAL, VehicleType.CARGO_CARRIER};
            for (int row = 0; row < types.length; row++) for (int col = 0; col < 4; col++) {
                float x = 17 + col * 10, y = row == 0 ? 26 : 14;
                long id = sim.convoy().spawn(types[row], Faction.ALLY, VehicleMission.deployed(x, y));
                sim.convoy().body(id).teleport(x, y, col * 90);
                if (types[row].hasTurretWeapon()) sim.convoy().turret(id).facingDeg = col * 135;
                labels.label(x, y - 3.2f, types[row].name() + " / " + col * 90, ReviewStyle.NOTE);
            }
            var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 32, 20, 46);
            BufferedImage live = renderer.render(sim,
                    "TECHNICAL + HAULER / overhead silhouettes / hidden wheels / camera zoom only / UI 100%", labels.build());
            artifacts.add(new SnapshotArtifact("headings.png", live));
            artifacts.add(new SnapshotArtifact("battle-scale.png", renderer.refocus(32, 20, 64).render(sim,
                    "TECHNICAL + HAULER / battle-scale review / UI 100%", labels.build())));
            for (long id : sim.getConvoyVehicleIds()) sim.applyDamage(id, 10000, 10000);
            BufferedImage wreck = renderer.refocus(32, 20, 46).render(sim,
                    "TECHNICAL + HAULER / wrecks at final hull and gun bearings / particles omitted / UI 100%", labels.build());
            artifacts.add(new SnapshotArtifact("wreck-headings.png", wreck));
            artifacts.add(SnapshotArtifact.colorAnimation("wreck-swap.gif", List.of(live, wreck), 900));
        }
        campaign(context, artifacts);
        return List.copyOf(artifacts);
    }

    private static void campaign(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        try (var sim = BattleSetup.createThunderRun(9301, manifest, profile,
                new FreightRunProgress("freight-art-thunder-run").previewAttempt(), 0)) {
            sim.setMissionCompletionEnabled(false);
            long technical = 0, hauler = 0;
            for (long id : sim.getConvoyVehicleIds()) {
                var type = sim.convoy().vehicleType(id);
                if (type.hasTurretWeapon()) {
                    sim.convoy().turret(id).ammo = 0;
                    sim.convoy().turret(id).facingDeg = sim.convoy().body(id).facingDegrees;
                }
                if (type == VehicleType.MILITIA_TECHNICAL) technical = id;
                // Use the leading freight vehicle so the whole column fits in the review.
                if (type == VehicleType.CARGO_CARRIER && (hauler == 0
                        || sim.convoy().body(id).x > sim.convoy().body(hauler).x)) hauler = id;
            }
            if (technical == 0 || hauler == 0) throw new IllegalStateException("Missing freight column actors");
            var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 24, 76, 44);
            artifacts.add(new SnapshotArtifact("campaign-comparison.png", renderer.render(sim,
                    "THUNDER RUN / revised technical and freight alongside tank and APCs / starting poses / UI 100%")));
            destruction(context, artifacts, sim, technical, "technical", 11);
            destruction(context, artifacts, sim, hauler, "hauler", 16);
            artifacts.add(new SnapshotArtifact("campaign-wrecks.png", renderer.render(sim,
                    "THUNDER RUN / technical and leading freight destroyed / particles omitted / UI 100%")));
        }
    }

    private static void destruction(SnapshotContext context, List<SnapshotArtifact> artifacts,
                                    BattleSimulation sim, long id, String name, float width) throws Exception {
        var body = sim.convoy().body(id);
        var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, body.x, body.y, width)
                .withImpactEffects();
        artifacts.add(new SnapshotArtifact(name + "-live.png", renderer.render(sim,
                name.toUpperCase() + " / live production art on Thunder Run / UI 100%")));
        var impacts = new ReviewImpactFx(context.modRoot());
        List<BufferedImage> frames = new ArrayList<>();
        for (int tick = 0; tick < 150; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            if (tick == 15) sim.applyDamage(id, 10000, 10000);
            impacts.feed(sim);
            if (tick % 3 == 0) frames.add(renderer.render(sim,
                    name.toUpperCase() + " / controlled lethal hit / real vehicle-specific destruction effects / UI 100%",
                    ReviewAnnotations.NONE, impacts));
        }
        artifacts.add(SnapshotArtifact.colorAnimation(name + "-destruction.gif", frames, 100));
        artifacts.add(new SnapshotArtifact(name + "-wreck.png",
                new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, body.x, body.y, width).render(sim,
                        name.toUpperCase() + " / registered wreck / transient particles omitted / UI 100%")));
    }
}
