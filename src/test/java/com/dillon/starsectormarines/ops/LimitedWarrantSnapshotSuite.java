package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production recovery playback; terminal receipts feed the campaign ledger without a live sector. */
public final class LimitedWarrantSnapshotSuite implements SnapshotSuite {
    public String id() { return "limited-warrant"; }
    public String label() { return "Limited Warrant: named regular company and first Specter recovery"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot()); HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot()); InstalledHullSpecs.install(); InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>(); addBoard(context, frames);
        var report = new StringBuilder("Detached production mission / 24 named regular marines / two fitted six-seat boats.\nNo live travel, fleet ownership, host save/reload or economy acceptance is claimed.\n");
        var directory = context.projectRoot().resolve("build/reports/limited-warrant"); Files.createDirectories(directory);
        String filter = System.getProperty("battle.limitedWarrant.fixture", "");
        try {
            for (String fixture : List.of("ordinary-15302", "ordinary-16103", "ordinary-16104", "absent", "cargo-loss", "technician-loss", "retreat-records")) {
                if (!filter.isBlank() && !fixture.contains(filter)) continue;
                var progress = afterWeir();
                play(context, fixture, progress, frames, report);
                if (fixture.equals("cargo-loss") || fixture.equals("technician-loss") || fixture.equals("retreat-records"))
                    play(context, "replacement-" + fixture, progress, frames, report);
            }
        } finally { Files.writeString(directory.resolve("playback.txt"), report); }
        return frames;
    }
    private void play(SnapshotContext context, String fixture, AgreusArcProgress campaign,
                      List<SnapshotArtifact> frames, StringBuilder report) throws Exception {
        var mission = AgreusArcCampaign.offer(campaign, "Agreus");
        assertTrue(campaign.begin(mission.id, "agreus", "Agreus"));
        long seed = fixture.startsWith("ordinary-") ? Long.parseLong(fixture.substring(9)) : 15302;
        boolean absent = fixture.equals("absent"), cargoLoss = fixture.equals("cargo-loss"), techLoss = fixture.equals("technician-loss"), retreat = fixture.equals("retreat-records");
        var roster = DebugMissionCatalog.expectedCompany(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission(), 2);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 24);
        var boats = List.of(boat("warrant-boat-one"), boat("warrant-boat-two"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        report.append("\n").append(fixture).append(" records retained=").append(campaign.recordsRecovered()).append("\n");
        try (var sim = BattleSetup.createLimitedWarrant(seed, manifest,
                new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ROCK,SettlementLink.ROAD), campaign.recordsRecovered())) {
            deployment.applyToAuthoredPlayerLift(sim);
            var sequence = sim.getLimitedWarrant();
            var scheduled = new HashSet<String>(); int aircraft = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                var flight = sim.world().mission(air);
                for (var cycle : flight.cycleLoadouts) for (var person : cycle) {
                    assertNotNull(person.campaignSoldierId); assertTrue(scheduled.add(person.campaignSoldierId));
                    assertEquals(ArmorClass.STANDARD, person.armorClass);
                }
                if (absent) flight.pendingDelay += 9999;
            }
            assertEquals(24, scheduled.size()); assertEquals(2, aircraft);
            var landed = new HashSet<String>(); var phase = sequence.progress().phase();
            frames.add(frame(context, fixture + "-arrival.png", sim));
            int maxTicks = absent ? 3600 : Integer.getInteger("battle.limitedWarrant.maxTicks", 18000);
            boolean injected = false;
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (cargoLoss && !injected) { sim.convoy().destroy(sequence.carrierId()); injected = true; }
                if (techLoss && !injected) {
                    long id = sim.squadMemberAt(sequence.technicianSquadId(), 0); sim.applyDamage(id,100000,100000); injected = true;
                }
                if (retreat && sequence.progress().recordsRecovered()) { assertTrue(sim.retreat()); break; }
                sim.advance(BattleSimulation.TICK_DT);
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long id = sim.liveUnitAt(i);
                    if (sim.identity().faction(id) != Faction.MARINE || sim.identity().type(id) != UnitType.MARINE) continue;
                    String person = (String)sim.getEntityWorld().getObject(id,sim.getBattleComponents().IDENTITY,BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(scheduled.contains(person)); landed.add(person);
                }
                if (phase != sequence.progress().phase()) {
                    phase = sequence.progress().phase(); report.append(" tick=").append(sim.getSimTickIndex()).append(" phase=").append(phase).append("\n");
                    frames.add(frame(context,fixture + "-" + phase + ".png",sim));
                }
                if (sim.getSimTickIndex() % 1800 == 0) System.out.println("[limited-warrant] " + fixture + " tick=" + sim.getSimTickIndex() + " phase=" + phase);
            }
            var result = sequence.report(sim);
            report.append("receipt=").append(result).append(" winner=").append(sim.getWinner()).append(" landed=").append(landed.size()).append("\n");
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append(" squad=").append(squad.id).append(" side=").append(squad.faction).append(" n=").append(sim.squadMemberCount(squad.id))
                        .append(" at=").append(squad.centroidX).append(',').append(squad.centroidY).append(" goal=").append(squad.currentGoal)
                        .append(" order=").append(sim.getSquadCommandDirective(squad.id)).append("\n");
            report.append(" cargo=").append(sim.convoy().body(sequence.carrierId()).x).append(',').append(sim.convoy().body(sequence.carrierId()).y).append("\n");
            frames.add(frame(context,fixture + "-final.png",sim));
            if (absent) { assertTrue(landed.isEmpty()); assertFalse(result.complete()); return; }
            if (cargoLoss) { assertTrue(result.cargoLost()); assertFalse(result.complete()); }
            else if (techLoss) { assertEquals(1,result.techniciansLost()); assertFalse(result.complete()); }
            else if (retreat) { assertTrue(result.recordsRecovered()); assertFalse(result.complete()); }
            else { assertEquals(24,landed.size()); assertTrue(result.complete(), fixture + " must physically recover the kit"); assertEquals(Faction.MARINE,sim.getWinner()); }
            var outcome = MissionResolver.compute(sim, mission, null);
            assertEquals(result, outcome.limitedWarrant);
            boolean victory = outcome.victory;
            int payment = outcome.payoutEarned;
            if (fixture.equals("ordinary-15302")) addDebrief(context, frames, outcome);
            assertEquals(payment,settle(campaign,mission,result,victory,payment));
            assertEquals(-1,settle(campaign,mission,result,victory,payment));
            report.append(" ledger accepted once / payment=").append(payment).append("\n");
        }
    }
    private int settle(AgreusArcProgress p, Mission m, LimitedWarrantProgress.Report r, boolean won, int payment) {
        return p.settleWarrant(m.id,"agreus",won,payment,r.recordsRecovered(),r.kitRecovered(),r.kitReturned(),r.companyReturned(),r.cargoLost(),r.techniciansLost(),r.constablesLost());
    }
    private void addDebrief(SnapshotContext context, List<SnapshotArtifact> frames, MissionOutcome outcome) throws Exception {
        var props = ResultsScreen.previewProps(true, false);
        props.put("missionName", outcome.missionName);
        props.put("resultRows", ResultsScreen.resultRows(outcome, LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
        props.put("squadRows", List.of());
        ResultsScreen.putStoryDebrief(props, outcome);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("limited-warrant-debrief.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }
    private void addBoard(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var mission = mission();
        var props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("locationLabel", "Agreus / Arcadia");
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-rusk", "client-rusk-name", "client-rusk-rep",
                OpeningBroker.CREST, "client-card selected", AgreusArcCampaign.NAME, "Low Quay / Ko Combine concession", false, none)));
        props.put("missionHeader", "House Rusk / Low Quay");
        MissionSelectScreen.putAgreus(props, afterWeir(), "");
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(props, mission, none, none);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("limited-warrant-contract-board.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }
    private FittedBoat boat(String id) {
        return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.standard(BoatFittingSlot.PLATING), BoatFitting.standard(BoatFittingSlot.DRIVE), id);
    }
    private Mission mission() { return AgreusArcCampaign.offer(afterWeir(), "Agreus"); }
    private AgreusArcProgress afterWeir() {
        var p = new AgreusArcProgress(); String id = p.missionId(); p.begin(id, "agreus");
        p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, false); return p;
    }
    private SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim) throws Exception {
        return new SnapshotArtifact(name, new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 72, 56, 144)
                .withImpactEffects().withUnitShadows().render(sim, "LOW QUAY / " + sim.getLimitedWarrant().progress().phase(), ReviewAnnotations.builder().build()));
    }
}
