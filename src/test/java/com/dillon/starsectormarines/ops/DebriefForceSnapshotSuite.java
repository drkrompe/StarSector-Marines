package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.SquadMuster;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The debrief's field force over the production markup: a hard fight, and a clean one. 1920x1080 at 100% UI scale. */
public final class DebriefForceSnapshotSuite implements SnapshotSuite {

    @Override public String id() {
        return "debrief-force";
    }

    @Override public String label() {
        return "Debrief / field force and repair days";
    }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (var strings = new HeadlessStrings(context.modRoot())) {
            HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
            HeadlessSvgAssets.install(context.modRoot());
            List<DebriefForce.Machine> hard = List.of(
                    mech(0, "Rook", MechVariant.HOUND, report(.55f, false), false),
                    mech(1, "Anvil", MechVariant.WARDEN, report(.2f, true), false),
                    mech(2, "Bastion", MechVariant.BULWARK, report(1f, false), false),
                    mech(3, "Kestrel", MechVariant.HOUND, report(0f, true), true),
                    vehicle(4, VehicleType.values()[0], false),
                    vehicle(5, VehicleType.values()[0], true));
            return List.of(
                    frame(context, "01-hard-fight.png", 3, 3, hard),
                    frame(context, "02-many-tiles.png", 9, 1, hard),
                    frame(context, "03-no-machines.png", 2, 0, List.of()));
        }
    }

    private static SnapshotArtifact frame(SnapshotContext context, String name, int squadCount, int lost,
                                          List<DebriefForce.Machine> machines) throws Exception {
        Map<String, Object> props = ResultsScreen.previewProps(true, false);
        props.put("sceneClasses", "results-scene console-well");
        props.put("sceneImage", null);
        props.put("sceneCaption", "LOCATION ARCHIVE / WAYLINE CHECKPOINT");
        MarineRoster roster = new MarineRoster();
        List<BriefingViewModel.SquadPicture> squads = new ArrayList<>();
        List<ResultsScreen.PersonnelRow> rows = new ArrayList<>();
        for (int i = 0; i < squadCount; i++) {
            var squad = roster.createSquad();
            List<MarineSoldier> marines = roster.recruitToSquad(squad.id(), 12);
            Set<String> gone = marines.stream().limit(i == 0 ? lost : 0).map(MarineSoldier::id).collect(Collectors.toSet());
            var row = new ResultsScreen.PersonnelRow("result-squad-" + i, squad.name(), (12 - gone.size()) + "R 0W 0M " + gone.size() + "K",
                    "label heading results-squad-summary tone-good", "Hale R · Chen R · Ilyin W · Okafor R · Bell K · Ruiz R");
            rows.add(row);
            squads.add(DebriefForce.squad(i, SquadMuster.from(roster, squad.id()).without(gone), row));
        }
        props.put("squadRows", List.copyOf(rows));
        List<Object> tiles = DebriefForce.put(props, squads, machines);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
        try (var markup = loader.reloadAndBuild(new Reactor(), ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(markup.root()).theme(MarineOpsThemes.standard());
            markup.styles().forEach(document::addStyleSheet);
            DebriefForce.install(document, markup, tiles, new HeadlessArmoryPreviewRenderer(context.modRoot()).assets());
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            return new SnapshotArtifact(name, renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }

    private static DebriefForce.Machine mech(int index, String name, MechVariant variant, MechSections.Report report, boolean lost) {
        float days = lost ? 0f : DebriefForce.repairDays(new CompanyMechLedger.Fielded(
                null, name, variant, report, false), null, 0f);
        return new DebriefForce.Machine("results-field-machine-" + index, name, variant, report, null, lost, days);
    }

    private static DebriefForce.Machine vehicle(int index, VehicleType type, boolean lost) {
        return new DebriefForce.Machine("results-field-machine-" + index, "Mule", null, null, type, lost, 0f);
    }

    /** Every section at {@code remaining}; {@code armTorn} shoots the left arm away. */
    private static MechSections.Report report(float remaining, boolean armTorn) {
        int n = MechSection.values().length;
        boolean[] fitted = new boolean[n];
        float[] structure = new float[n];
        float[] armor = new float[n];
        boolean[] destroyed = new boolean[n];
        for (int i = 0; i < n; i++) {
            fitted[i] = true;
            structure[i] = remaining;
            armor[i] = remaining * .6f;
        }
        if (armTorn) {
            destroyed[MechSection.LEFT_ARM.ordinal()] = true;
            structure[MechSection.LEFT_ARM.ordinal()] = 0f;
        }
        return new MechSections.Report("plan", fitted, structure, armor, destroyed,
                new MechWeaponComponent[MechMountSlot.values().length]);
    }
}
