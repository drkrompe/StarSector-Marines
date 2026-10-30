package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusDistrictEscortProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
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

/**
 * Opt-in District Escort playback on the production Low Quay freight lane: both issuing offices,
 * three ordinary seeds, absent arrival, consignment loss, party loss and withdrawal, each loss
 * followed by its new posting. Terminal receipts feed the ledger once, without a live sector.
 */
public final class DistrictEscortSnapshotSuite implements SnapshotSuite {
    public String id() { return "district-escort"; }
    public String label() { return "District Escort: recurring Low Quay consignment escort after A Small War"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot()); HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot()); InstalledHullSpecs.install(); InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>();
        addBoard(context, frames, AgreusYardAgreement.HOUSE_CUSTODY);
        addBoard(context, frames, AgreusYardAgreement.YARD_COMPACT);
        var report = new StringBuilder("Detached production mission / 24 named regular marines / two fitted six-seat boats.\n"
                + "Ledgers are constructed under each ratified agreement; no live travel, Chapter 5 settlement, host save/reload or economy acceptance is claimed.\n");
        var directory = context.projectRoot().resolve("build/reports/district-escort"); Files.createDirectories(directory);
        String filter = System.getProperty("battle.districtEscort.fixture", "");
        var custody = new AgreusDistrictEscortProgress(AgreusYardAgreement.HOUSE_CUSTODY);
        var compact = new AgreusDistrictEscortProgress(AgreusYardAgreement.YARD_COMPACT);
        try {
            for (String fixture : List.of("custody-15400", "compact-15401", "compact-16211", "absent", "cargo-loss", "party-loss", "retreat")) {
                if (!filter.isBlank() && !fixture.contains(filter)) continue;
                var ledger = fixture.startsWith("custody") || fixture.equals("cargo-loss") || fixture.equals("retreat") ? custody : compact;
                play(context, fixture, ledger, frames, report);
                if (fixture.equals("cargo-loss") || fixture.equals("party-loss") || fixture.equals("retreat"))
                    play(context, "replacement-" + fixture, ledger, frames, report);
            }
        } finally { Files.writeString(directory.resolve("playback.txt"), report); }
        return frames;
    }

    private void play(SnapshotContext context, String fixture, AgreusDistrictEscortProgress ledger,
                      List<SnapshotArtifact> frames, StringBuilder report) throws Exception {
        var mission = DistrictEscortCampaign.offer(ledger, "Agreus");
        String previous = ledger.lastMissionId();
        assertNotEquals(previous, mission.id, "every posting is a new identity");
        assertTrue(ledger.begin(mission.id, "agreus", "Agreus"));
        int dash = fixture.lastIndexOf('-');
        long seed = fixture.startsWith("custody-") || fixture.startsWith("compact-") ? Long.parseLong(fixture.substring(dash + 1)) : mission.battleSeed;
        boolean absent = fixture.equals("absent"), cargoLoss = fixture.equals("cargo-loss"),
                partyLoss = fixture.equals("party-loss"), retreat = fixture.equals("retreat");
        var roster = DebugMissionCatalog.agreusCompany(2);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 24);
        var boats = List.of(boat("escort-boat-one"), boat("escort-boat-two"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        var letterhead = DistrictEscortCampaign.Letterhead.of(ledger.agreement());
        report.append("\n").append(fixture).append(" id=").append(mission.id).append(" consignment=").append(ledger.consignmentId())
                .append(" office=").append(letterhead.sender()).append(" seed=").append(seed).append("\n");
        try (var sim = BattleSetup.createDistrictEscort(seed, manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD), letterhead.partyName())) {
            deployment.applyToAuthoredPlayerLift(sim);
            var sequence = sim.getDistrictEscort();
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
            int maxTicks = absent ? 3600 : Integer.getInteger("battle.districtEscort.maxTicks", 18000);
            boolean injected = false;
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                boolean escorting = sequence.progress().linkedUp();
                if (cargoLoss && escorting && !injected) { sim.convoy().destroy(sequence.carrierId()); injected = true; }
                if (partyLoss && escorting && !injected) {
                    while (sim.squadMemberCount(sequence.partySquadId()) > 0) {
                        long id = sim.squadMemberAt(sequence.partySquadId(), 0); sim.applyDamage(id, 100000, 100000);
                    }
                    injected = true;
                }
                if (retreat && escorting) { assertTrue(sim.retreat()); break; }
                sim.advance(BattleSimulation.TICK_DT);
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long id = sim.liveUnitAt(i);
                    if (sim.identity().faction(id) != Faction.MARINE || sim.identity().type(id) != UnitType.MARINE) continue;
                    String person = (String) sim.getEntityWorld().getObject(id, sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(scheduled.contains(person)); landed.add(person);
                }
                if (phase != sequence.progress().phase()) {
                    phase = sequence.progress().phase();
                    report.append(" tick=").append(sim.getSimTickIndex()).append(" phase=").append(phase).append("\n");
                    frames.add(frame(context, fixture + "-" + phase + ".png", sim));
                }
                if (sim.getSimTickIndex() % 300 == 0) {
                    var body = sim.convoy().body(sequence.carrierId());
                    report.append("  t=").append(sim.getSimTickIndex()).append(" phase=").append(phase)
                            .append(" cargo=").append(Math.round(body.x)).append(',').append(Math.round(body.y))
                            .append(" covered=").append(sequence.progress().covered())
                            .append(" party=").append(sim.squadMemberCount(sequence.partySquadId()))
                            .append("@").append(Math.round(sim.getSquad(sequence.partySquadId()).centroidX)).append(',')
                            .append(Math.round(sim.getSquad(sequence.partySquadId()).centroidY))
                            .append(" constables=").append(sim.squadMemberCount(sequence.constableSquadId()))
                            .append(sequence.reserveSquadId() < 0 ? "" : " raiders=" + sim.squadMemberCount(sequence.reserveSquadId())
                                    + "@" + Math.round(sim.getSquad(sequence.reserveSquadId()).centroidX) + "," + Math.round(sim.getSquad(sequence.reserveSquadId()).centroidY)).append("\n");
                    if (sim.getSimTickIndex() % 1800 == 0 && !absent) frames.add(frame(context, fixture + "-t" + sim.getSimTickIndex() + ".png", sim));
                    System.out.println("[district-escort] " + fixture + " tick=" + sim.getSimTickIndex() + " phase=" + phase);
                }
            }
            var result = sequence.report(sim);
            report.append("receipt=").append(result).append(" winner=").append(sim.getWinner()).append(" landed=").append(landed.size())
                    .append(" ticks=").append(sim.getSimTickIndex()).append("\n");
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append(" squad=").append(squad.id).append(" side=").append(squad.faction).append(" n=").append(sim.squadMemberCount(squad.id))
                        .append(" at=").append(Math.round(squad.centroidX)).append(',').append(Math.round(squad.centroidY)).append(" goal=").append(squad.currentGoal).append("\n");
            frames.add(frame(context, fixture + "-final.png", sim));
            if (absent) { assertTrue(landed.isEmpty()); assertFalse(result.linkedUp()); assertFalse(result.complete()); }
            else if (cargoLoss) { assertTrue(result.cargoLost()); assertFalse(result.complete()); }
            else if (partyLoss) { assertEquals(2, result.partyLost()); assertFalse(result.complete()); }
            else if (retreat) { assertTrue(result.linkedUp()); assertFalse(result.complete()); }
            else {
                assertEquals(24, landed.size());
                assertTrue(result.complete(), fixture + " must physically deliver the consignment and its party");
                assertEquals(Faction.MARINE, sim.getWinner());
            }
            var outcome = MissionResolver.compute(sim, mission, null);
            assertEquals(result, outcome.districtEscort);
            assertEquals(result.complete(), outcome.victory);
            if (fixture.equals("custody-15400")) addDebrief(context, frames, outcome);
            assertEquals(outcome.payoutEarned, DistrictEscortCampaign.settle(ledger, outcome));
            assertEquals(-1, DistrictEscortCampaign.settle(ledger, outcome), "an outcome settles once");
            report.append(" ledger accepted once / payment=").append(outcome.payoutEarned).append(" / ").append(ledger.status()).append("\n");
        }
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
            frames.add(new SnapshotArtifact("district-escort-debrief.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }

    private void addBoard(SnapshotContext context, List<SnapshotArtifact> frames, AgreusYardAgreement agreement) throws Exception {
        var ledger = new AgreusDistrictEscortProgress(agreement);
        var mission = DistrictEscortCampaign.offer(ledger, "Agreus");
        var letterhead = DistrictEscortCampaign.Letterhead.of(agreement);
        var props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("locationLabel", "Agreus / Arcadia");
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-escort", "client-escort-name", "client-escort-rep",
                OpeningBroker.CREST, "client-card selected", letterhead.signatory, "Latchkey District / Low Quay", false, none)));
        props.put("missionHeader", letterhead.signatory);
        MissionSelectScreen.putDistrictEscort(props, ledger);
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(props, mission, none, none);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            String name = agreement == AgreusYardAgreement.HOUSE_CUSTODY ? "custody" : "compact";
            frames.add(new SnapshotArtifact("district-escort-board-" + name + ".png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }

    private FittedBoat boat(String id) {
        return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.standard(BoatFittingSlot.PLATING), BoatFitting.standard(BoatFittingSlot.DRIVE), id);
    }
    private SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim) throws Exception {
        return new SnapshotArtifact(name, new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 72, 56, 144)
                .withImpactEffects().withUnitShadows().render(sim, "LOW QUAY FREIGHT LANE / " + sim.getDistrictEscort().progress().phase(),
                        ReviewAnnotations.builder().build()));
    }
}
