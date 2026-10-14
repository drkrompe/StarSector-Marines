package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Current production geography before combat; annotations explain duties, not new map features. */
public final class AgreusMapsSnapshotSuite implements SnapshotSuite {
    public String id() { return "agreus-maps"; }
    public String label() { return "Agreus: three production maps, labelled overview and interior detail"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        var profile = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD);
        var lift = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        var frames = new ArrayList<SnapshotArtifact>();
        try (var sim = BattleSetup.createWaterUnderSeal(15301, lift, profile)) {
            add(context, frames, sim, "01-north-weir", "WATER UNDER SEAL / NORTH WEIR / seed 15301", 87, 57,
                    ReviewAnnotations.builder()
                            .box(14, 44, 36, 84, "SERVICE APRON / COMPANY LIFT", ReviewStyle.LANDING)
                            .label(64, 59, "1 / DELIVER REPAIR LOAD", ReviewStyle.OBJECTIVE)
                            .box(74, 68, 86, 78, "2 / NORTH SWITCH", ReviewStyle.OBJECTIVE)
                            .box(74, 35, 86, 45, "2 / SOUTH SWITCH", ReviewStyle.OBJECTIVE)
                            .box(103, 84, 114, 94, "3 / CONSTRUCT ROAD GUN", ReviewStyle.KEEP)
                            .arrow(35, 59, 61, 59, "ESCORT", ReviewStyle.APPROACH)
                            .arrow(164, 90, 118, 90, "FINITE ROAD ATTACK", ReviewStyle.ROUTE).build());
        }
        try (var sim = BattleSetup.createLimitedWarrant(15302, lift, profile, false)) {
            add(context, frames, sim, "02-low-quay", "LIMITED WARRANT / LOW QUAY / seed 15302", 81, 59,
                    ReviewAnnotations.builder()
                            .box(14, 44, 36, 84, "LIFT / RETURN CALIBRATION LOAD", ReviewStyle.LANDING)
                            .box(63, 68, 75, 78, "1 / AUTHENTICATE RECORDS", ReviewStyle.OBJECTIVE)
                            .box(81, 35, 93, 45, "2 / TECHNICIAN CALIBRATION", ReviewStyle.OBJECTIVE)
                            .box(86, 53, 102, 63, "3 / RECOVER CARRIER", ReviewStyle.KEEP)
                            .arrow(85, 58, 38, 58, "ESCORT BACK TO APRON", ReviewStyle.APPROACH)
                            .arrow(135, 94, 111, 76, "FINITE REARGUARD", ReviewStyle.ROUTE).build());
        }
        try (var sim = BattleSetup.createYardOwners(15303, lift, profile)) {
            add(context, frames, sim, "03-latchkey-yard", "THE YARD'S OWNERS / LATCHKEY / seed 15303", 82, 58,
                    ReviewAnnotations.builder()
                            .box(14, 44, 36, 84, "COMPANY LIFT", ReviewStyle.LANDING)
                            .box(51, 65, 63, 75, "1 / DISPATCH + FREIGHT GATE", ReviewStyle.OBJECTIVE)
                            .box(85, 61, 103, 79, "2 / TOOLING + INSPECTION", ReviewStyle.KEEP)
                            .box(81, 35, 93, 45, "3 / OPERATOR ENTRANCE", ReviewStyle.OBJECTIVE)
                            .label(98, 59, "PRESERVE CALIBRATION MACHINE", ReviewStyle.KEEP)
                            .label(140, 119, "SEPARATE FRONTAGE / NO CAPTURE DUTY", ReviewStyle.NOTE)
                            .arrow(39, 69, 49, 69, "TAKE / HAND OFF", ReviewStyle.APPROACH)
                            .arrow(148, 74, 119, 69, "FINITE COUNTERATTACK", ReviewStyle.ROUTE).build());
        }
        return frames;
    }

    private void add(SnapshotContext context, List<SnapshotArtifact> frames, BattleSimulation sim,
                     String id, String caption, int x, int y, ReviewAnnotations marks) throws Exception {
        frames.add(new SnapshotArtifact(id + "-overview.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080)
                .render(sim, caption + " / CURRENT MAP / roofs omitted / observer annotations", marks)));
        frames.add(new SnapshotArtifact(id + "-detail.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, x, y, 88)
                .render(sim, caption + " / INTERIOR DETAIL / 88 cells across / no review labels")));
    }
}
