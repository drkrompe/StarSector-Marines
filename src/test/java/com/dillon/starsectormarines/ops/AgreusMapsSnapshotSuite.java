package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
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
                            .box(57, 56, 69, 62, "1 / ISOLATION BAY", ReviewStyle.OBJECTIVE)
                            .box(74, 68, 86, 78, "2 / NORTH SWITCH", ReviewStyle.OBJECTIVE)
                            .box(74, 35, 86, 45, "2 / SOUTH SWITCH", ReviewStyle.OBJECTIVE)
                            .box(92, 67, 122, 80, "INTAKE PUMPS (NORTH SWITCH)", ReviewStyle.NOTE)
                            .box(92, 33, 122, 50, "SETTLING TANKS (SOUTH SWITCH)", ReviewStyle.NOTE)
                            .label(101, 57, "COVERED CONDUIT / CROSSINGS", ReviewStyle.NOTE)
                            .box(103, 84, 114, 94, "3 / CONSTRUCT ROAD GUN", ReviewStyle.KEEP)
                            .arrow(35, 59, 56, 59, "ESCORT", ReviewStyle.APPROACH)
                            .arrow(164, 90, 118, 90, "FINITE ROAD ATTACK", ReviewStyle.ROUTE)
                            .arrow(150, 102, 118, 102, "FLANK TRACK", ReviewStyle.ROUTE).build());
        }
        try (var sim = BattleSetup.createLimitedWarrant(15302, lift, profile, false)) {
            add(context, frames, sim, "02-low-quay", "LIMITED WARRANT / LOW QUAY / seed 15302", 82, 58,
                    ReviewAnnotations.builder()
                            .box(14, 44, 36, 84, "LIFT / RETURN CALIBRATION LOAD", ReviewStyle.LANDING)
                            .label(44, 66, "SECURE GATE", ReviewStyle.NOTE)
                            .box(66, 68, 80, 84, "1 / ARCHIVE: AUTHENTICATE RECORDS", ReviewStyle.OBJECTIVE)
                            .box(86, 26, 108, 50, "2 / CALIBRATION SHOP + CAGE", ReviewStyle.OBJECTIVE)
                            .box(104, 54, 120, 64, "3 / LOAD COURT", ReviewStyle.KEEP)
                            .box(80, 68, 104, 88, "BONDED WAREHOUSE", ReviewStyle.NOTE)
                            .box(44, 36, 61, 50, "DIVERTED CLINIC CONSIGNMENTS", ReviewStyle.NOTE)
                            .label(64, 47, "GALLERY", ReviewStyle.NOTE)
                            .label(63, 92, "GALLERY", ReviewStyle.NOTE)
                            .label(LowQuay.CROSS_X + 4, LowQuay.CROSS_Y, "CROSSING", ReviewStyle.APPROACH)
                            .arrow(104, 59, 40, 59, "CARRIER LANE TO APRON", ReviewStyle.APPROACH)
                            .polyline(List.of(new ReviewAnnotation.Point(73, 106), new ReviewAnnotation.Point(73, 94),
                                    new ReviewAnnotation.Point(64, 94), new ReviewAnnotation.Point(64, 66)),
                                    "FINITE REARGUARD VIA NORTH GALLERY", ReviewStyle.ROUTE).build());
        }
        try (var sim = BattleSetup.createYardOwners(15303, lift, profile)) {
            var dispatch = LatchkeyYard.SITES.get(0); var hall = LatchkeyYard.SITES.get(1); var entrance = LatchkeyYard.SITES.get(2);
            var marks = ReviewAnnotations.builder()
                    .box(14, 44, 36, 84, "COMPANY LIFT", ReviewStyle.LANDING)
                    .box(dispatch.left(), dispatch.bottom(), dispatch.right(), dispatch.top(), "1 / DISPATCH + FREIGHT GATE", ReviewStyle.OBJECTIVE)
                    .box(hall.left(), hall.bottom(), hall.right(), hall.top(), "2 / TOOLING HALL + INSPECTION", ReviewStyle.KEEP)
                    .box(entrance.left(), entrance.bottom(), entrance.right(), entrance.top(), "3 / OPERATOR ENTRANCE + LOCK", ReviewStyle.OBJECTIVE)
                    .label(LatchkeyYard.TOOL_X, LatchkeyYard.TOOL_Y - 4, "PRESERVE CALIBRATION MACHINE", ReviewStyle.KEEP)
                    .label(LatchkeyYard.HANDOVER.x(), LatchkeyYard.HANDOVER.y() - 4, "HANDOVER BAY", ReviewStyle.NOTE)
                    .label(LatchkeyYard.HALL_FREIGHT_DOORS.x(), LatchkeyYard.HALL_FREIGHT_DOORS.y() + 3, "FREIGHT DOORS", ReviewStyle.NOTE)
                    .label(LatchkeyYard.HALL_GALLERY_DOOR.x() + 9, 46, "SERVICE GALLERY", ReviewStyle.NOTE)
                    .label(104, 124, "HOMES + DISTRICT HEATING / NO CAPTURE DUTY", ReviewStyle.NOTE)
                    .label(152, 92, "CINDER STEPS", ReviewStyle.NOTE)
                    .arrow(38, 56, 52, 56, "FREIGHT GATE", ReviewStyle.APPROACH)
                    .arrow(LatchkeyYard.CINDER_LANE.x(), LatchkeyYard.CINDER_LANE.y(), LatchkeyYard.CINDER_GATE.x() + 2, LatchkeyYard.CINDER_GATE.y(), "COUNTERATTACK / CINDER LANE", ReviewStyle.ROUTE)
                    .arrow(LatchkeyYard.SOUTH_ROAD.x(), LatchkeyYard.SOUTH_ROAD.y(), LatchkeyYard.VESTIBULE.x() + 10, LatchkeyYard.SOUTH_ROAD.y(), "COUNTERATTACK / SOUTH ROAD", ReviewStyle.ROUTE);
            // Short forms of Lenna's machine labels; the battle markers carry the full attribution.
            marks.label(79, 77, "WEIR PUMP LATHE", ReviewStyle.NOTE).label(107, 77, "HEATING BENCH", ReviewStyle.NOTE)
                    .label(91, 63, "CLINIC PARTS BENCH", ReviewStyle.NOTE);
            add(context, frames, sim, "03-latchkey-yard", "THE YARD'S OWNERS / LATCHKEY / seed 15303", 93, 64, marks.build());
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
