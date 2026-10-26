package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechCarriedWear;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The briefing's armor lift section ({@link BriefingLift}): the company's and the mission's lift,
 * and each bay machine as the battle's damage doll, 1920 x 1080 at 100% UI scale.
 */
public final class BriefingLiftSnapshotSuite implements SnapshotSuite {
    @Override
    public String id() { return "briefing-lift"; }

    @Override
    public String label() { return "Briefing armor lift (1080p, 100%)"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        // The doll captions are the one Orbitron 10 on the page; the game has it loaded long before a briefing.
        Fonts.ORBITRON_10.installMetrics(Files.readString(context.starsectorCore().resolve(Fonts.ORBITRON_10.sourcePath())));
        List<FittedBoat> hermes = List.of(FittedBoat.standard(ShuttleType.HERMES), FittedBoat.standard(ShuttleType.HERMES));
        FittedBoat buffalo = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");
        List<MechDeploymentSpec> lance = List.of(named(MechVariant.HOUND, "Rook", worn()),
                named(MechVariant.WARDEN, "Anvil", null), named(MechVariant.HOUND, "Kestrel", null));
        List<MechDeploymentSpec> heldBack = List.of(named(MechVariant.BULWARK, "Tower", null));
        List<VehicleDeploymentSpec> vehicles = List.of(new VehicleDeploymentSpec(null, "Mule", VehicleType.HEAVY_APC));
        MechSupport heavy = MechSupport.configured(lance, vehicles, null, true);
        MechSupport cradle = MechSupport.configured(lance, List.of(), buffalo, false);
        return List.of(
                frame(context, renderer, "no-drop-call.png", BriefingLift.read(hermes, 2,
                        List.of(new OrbitalBarrage()), Set.of(), lance, vehicles, heldBack)),
                frame(context, renderer, "mission-heavy-lift.png", BriefingLift.read(hermes, 2,
                        List.of(heavy), Set.of(MechSupport.ID), lance, vehicles, heldBack)),
                frame(context, renderer, "mission-lift-not-selected.png", BriefingLift.read(hermes, 2,
                        List.of(heavy), Set.of(), lance, vehicles, List.of())),
                frame(context, renderer, "company-cradle-short.png", BriefingLift.read(List.of(buffalo), 2,
                        List.<CommandPower>of(cradle), Set.of(MechSupport.ID), lance, List.of(), List.of())),
                frame(context, renderer, "conquest-officers.png", true, BriefingLift.read(hermes, 2,
                        List.of(heavy), Set.of(MechSupport.ID), lance, vehicles, List.of())));
    }

    private static SnapshotArtifact frame(SnapshotContext context, HeadlessUiRenderer renderer, String name,
                                          BriefingLift.Readout readout) throws Exception {
        return frame(context, renderer, name, false, readout);
    }

    private static SnapshotArtifact frame(SnapshotContext context, HeadlessUiRenderer renderer, String name,
                                          boolean conquest, BriefingLift.Readout readout) throws Exception {
        Map<String, Object> props = BriefingViewModel.campaignPreviewProps(conquest);
        BriefingLift.put(props, readout);
        return new SnapshotArtifact(name, UiSnapshotSuite.renderMissionBriefing(context, renderer,
                1920, 1080, props, 1f, false, null));
    }

    private static MechDeploymentSpec named(MechVariant variant, String callsign, MechCarriedWear wear) {
        MechDeploymentSpec stock = MechDeploymentSpec.standard(variant);
        MechDeploymentSpec spec = new MechDeploymentSpec(stock.variant(), stock.role(), stock.missileReplenisher(),
                stock.arms(), stock.leftArm(), stock.rightArm(), stock.leftShoulder(), stock.rightShoulder(),
                callsign, stock.frame(), null);
        return wear == null ? spec : spec.withWear(wear);
    }

    /** A Hound back from a hard drop: legs stripped to structure, torso armor half gone, gun mount wrecked. */
    private static MechCarriedWear worn() {
        float[] structure = new float[MechSection.values().length];
        float[] armor = new float[MechSection.values().length];
        armor[MechSection.LEGS.ordinal()] = 1f;
        structure[MechSection.LEGS.ordinal()] = 0.6f;
        armor[MechSection.TORSO.ordinal()] = 0.5f;
        armor[MechSection.ARMS.ordinal()] = 1f;
        structure[MechSection.ARMS.ordinal()] = 1f;
        return new MechCarriedWear(structure, armor);
    }
}
