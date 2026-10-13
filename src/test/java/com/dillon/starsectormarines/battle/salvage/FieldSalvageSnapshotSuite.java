package com.dillon.starsectormarines.battle.salvage;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.FieldSalvageUiPreview;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.SalvageCacheRenderSystem;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Opt-in production-map placement and controlled live recovery, at 1920x1080 / 100%. */
public final class FieldSalvageSnapshotSuite implements SnapshotSuite {
    public String id() { return "field-salvage"; }
    public String label() { return "Exploration salvage discovery and recovery"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        var fontPath = context.modRoot().resolve(Fonts.ORBITRON_12_BOLD.sourcePath());
        if (!Files.exists(fontPath)) fontPath = context.starsectorCore().resolve(Fonts.ORBITRON_12_BOLD.sourcePath());
        Fonts.ORBITRON_12_BOLD.installMetrics(Files.readString(fontPath));
        var overlay = new BattleLaunchOverlay(0,List.of(),List.of(),List.of(),List.of(),0);
        for (var kind : OpeningOperationKind.values()) {
            try (var sim = BattleSetup.createOpeningOperation(9303,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE,1,6)),0,kind,TargetProfile.NEUTRAL)) {
                overlay.applyTo(sim,CommandPowerResources.FREE);
                require(sim.fieldSalvage().caches().size() == 3, "Three reachable finds in " + kind);
                System.out.println("[field-salvage] " + kind + " / three reachable optional caches");
            }
        }
        var map = ThunderRunCompound.generate(9303,TargetProfile.NEUTRAL).map();
        try (var sim = BattleSetup.buildMap(map,List.of(),List.of(),List.of(),9303).sim()) {
            overlay.applyTo(sim,CommandPowerResources.FREE);
            require(sim.fieldSalvage().caches().size() == 3, "Thunder Run has three finds");
            var cache = sim.fieldSalvage().caches().get(0);
            var renderer = new BattleReviewFrameRenderer(context.modRoot(),1920,1080,cache.cellX,cache.cellY,48);
            var frames = new ArrayList<SnapshotArtifact>();
            frames.add(new SnapshotArtifact("01-undiscovered.png",renderer.render(sim,
                    "FIELD SALVAGE / CONTROLLED RECOVERY SCENE / no cache marker before observation",ReviewAnnotations.NONE)));
            // Stationary actors isolate collection from fighting/commander decisions. They still
            // participate in real faction vision, presence, and the ordinary fixed-tick pipeline.
            spawn(sim,Faction.DEFENDER,10,10);
            spawn(sim,Faction.MARINE,20,75);
            long ally = spawn(sim,Faction.ALLY,cache.cellX+2,cache.cellY);
            advance(sim,180);
            require(!cache.secured(), "Employer infantry cannot collect company salvage");
            sim.takeOffTheField(ally);
            spawn(sim,Faction.MARINE,cache.cellX+2,cache.cellY);
            advance(sim,75);
            require(cache.discovered() && cache.fraction() > 0 && !cache.secured(), "Visible marine works on cache");
            frames.add(new SnapshotArtifact("02-securing.png",renderer.render(sim,
                    "FIELD SALVAGE / " + SalvageCacheRenderSystem.label(cache) + " / " + SalvageCacheRenderSystem.status(cache),ReviewAnnotations.NONE)));
            long blocker = spawn(sim,Faction.DEFENDER,cache.cellX-2,cache.cellY);
            advance(sim,1);
            require(cache.contested() && cache.fraction() == 0, "Nearby enemy interrupts work");
            frames.add(new SnapshotArtifact("03-contested.png",renderer.render(sim,
                    "FIELD SALVAGE / " + SalvageCacheRenderSystem.status(cache),ReviewAnnotations.NONE)));
            sim.takeOffTheField(blocker);
            advance(sim,151);
            require(cache.secured() && sim.fieldSalvage().recovered().size() == 1, "Real tick secures exactly one find");
            frames.add(new SnapshotArtifact("04-secured.png",renderer.render(sim,
                    "FIELD SALVAGE / " + SalvageCacheRenderSystem.label(cache) + " / " + SalvageCacheRenderSystem.status(cache),ReviewAnnotations.NONE)));
            advance(sim,180);
            require(sim.fieldSalvage().recovered().size() == 1, "Remaining nearby never duplicates the find");
            System.out.println("[field-salvage] Thunder Run: unknown, observed, company-only, interrupted, secured once");
            frames.add(new SnapshotArtifact("05-cargo-claim.png",FieldSalvageUiPreview.render(context,sim.fieldSalvage().recovered())));
            // Real placements, current player vision, and the same marker collector for each material.
            for (int i = 1; i < sim.fieldSalvage().caches().size(); i++) {
                var other = sim.fieldSalvage().caches().get(i);
                spawn(sim, Faction.MARINE, other.cellX + 2, other.cellY);
                advance(sim, 15);
                require(other.discovered() && !other.secured(), "Material preview is an observed pickup");
                var materialView = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                        other.cellX, other.cellY, 48);
                frames.add(new SnapshotArtifact("0" + (5 + i) + "-" + other.resource.commodityId + ".png",
                        materialView.render(sim, "FIELD SALVAGE / " + SalvageCacheRenderSystem.label(other),
                                ReviewAnnotations.NONE)));
            }
            return frames;
        }
    }
    private static long spawn(BattleSimulation sim, Faction side, int x, int y) {
        return sim.spawn(new EntitySpec("salvage-scene-" + side,side,UnitType.MARINE,x,y).role(UnitRole.STRUCTURE));
    }
    private static void advance(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }
}
