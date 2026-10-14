package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.YardOwnersProgress;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.EquipmentTemplateLearning;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import com.fs.starfarer.api.campaign.SpecialItemData;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.dillon.starsectormarines.marine.MarineRoster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production inspection playback; terminal receipts feed the campaign ledger without a live sector. */
public final class YardOwnersSnapshotSuite implements SnapshotSuite {
    private MarineRoster earnedRoster;
    public String id() { return "yard-owners"; }
    public String label() { return "The Yard's Owners: named regular company and Arbalest reward and mixed issue"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot()); HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot()); InstalledHullSpecs.install(); InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>(); addBoard(context, frames);
        var report = new StringBuilder("Detached production mission / 36 named marines / two fitted six-seat boats.\nNo live travel, fleet ownership, host save/reload or economy acceptance is claimed.\n");
        var directory = context.projectRoot().resolve("build/reports/yard-owners"); Files.createDirectories(directory);
        String filter = System.getProperty("battle.yardOwners.fixture", "");
        try {
            if (filter.equals("mixed")) play(context,"ordinary-15303",afterWarrant(),frames,report);
            for (String fixture : List.of("ordinary-15303", "ordinary-16203", "ordinary-16204", "specter", "mixed", "absent", "tooling-loss", "technician-loss", "retreat-dispatch")) {
                if (!filter.isBlank() && !fixture.contains(filter)) continue;
                var progress = afterWarrant();
                play(context, fixture, progress, frames, report);
                if (fixture.equals("tooling-loss") || fixture.equals("technician-loss") || fixture.equals("retreat-dispatch"))
                    play(context, "replacement-" + fixture, progress, frames, report);
            }
        } finally { Files.writeString(directory.resolve("playback.txt"), report); }
        return frames;
    }
    private void play(SnapshotContext context, String fixture, AgreusArcProgress campaign,
                      List<SnapshotArtifact> frames, StringBuilder report) throws Exception {
        var mission = AgreusArcCampaign.offer(campaign, "Agreus");
        assertTrue(campaign.begin(mission.id, "agreus", "Agreus"));
        long seed = fixture.startsWith("ordinary-") ? Long.parseLong(fixture.substring(9)) : 15303;
        boolean absent = fixture.equals("absent"), toolingLoss = fixture.equals("tooling-loss"), techLoss = fixture.equals("technician-loss"), retreat = fixture.equals("retreat-dispatch");
        var roster = fixture.equals("mixed") ? earnedRoster
                : DebugMissionCatalog.expectedCompany(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission(), 3);
        assertNotNull(roster,"The mixed comparison must follow the actual earned reward and personnel writeback");
        if (fixture.equals("specter") || fixture.equals("mixed")) {
            EquipmentTemplateLearning.learn(roster.armory(),AgreusArcProgress.SPECTER_CARD);
            for (var squad : roster.squads()) if (!squad.reserve())
                assertEquals(SquadEquipmentResult.APPLIED,roster.applySquadEquipment(squad.id(),squad.weaponDoctrineId(),
                        fixture.equals("mixed") ? SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR : SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR));
        }
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 36);
        var boats = List.of(boat("yard-boat-one"), boat("yard-boat-two"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        report.append("\n").append(fixture).append("\n");
        try (var sim = BattleSetup.createYardOwners(seed, manifest,
                new TargetProfile(0,0,0,0,"pirates",Set.of(),SurfacePalette.ROCK,SettlementLink.ROAD))) {
            deployment.applyToAuthoredPlayerLift(sim);
            var sequence = sim.getYardOwners();
            var scheduled = new HashSet<String>(); int aircraft = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                var flight = sim.world().mission(air);
                for (var cycle : flight.cycleLoadouts) for (var person : cycle) {
                    assertNotNull(person.campaignSoldierId); assertTrue(scheduled.add(person.campaignSoldierId));
                    if (!fixture.equals("specter") && !fixture.equals("mixed")) assertEquals(ArmorClass.STANDARD,person.armorClass);
                }
                if (absent) flight.pendingDelay += 9999;
            }
            assertEquals(deployment.size(), scheduled.size()); assertEquals(2, aircraft);
            var landed = new HashSet<String>(); var phase = sequence.progress().phase();
            frames.add(frame(context, fixture + "-arrival.png", sim));
            int maxTicks = absent ? 3600 : Integer.getInteger("battle.yardOwners.maxTicks", 18000);
            boolean injected = false;
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (toolingLoss && !injected) { assertTrue(sim.damageCell(LatchkeyYard.TOOL_X,LatchkeyYard.TOOL_Y,100000)); injected = true; }
                if (techLoss && !injected) {
                    while (sim.squadMemberCount(sequence.technicianSquadId()) > 0) {
                        long id = sim.squadMemberAt(sequence.technicianSquadId(),0); sim.applyDamage(id,100000,100000);
                    } injected = true;
                }
                if (retreat && sequence.progress().visited(0)) { assertTrue(sim.retreat()); break; }
                sim.advance(BattleSimulation.TICK_DT);
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long id = sim.liveUnitAt(i);
                    if (sim.identity().faction(id) != Faction.MARINE || sim.identity().type(id) != UnitType.MARINE) continue;
                    String person = (String)sim.getEntityWorld().getObject(id,sim.getBattleComponents().IDENTITY,BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(scheduled.contains(person)); landed.add(person);
                }
                if (phase != sequence.progress().phase()) {
                    phase = sequence.progress().phase(); report.append(" tick=").append(sim.getSimTickIndex()).append(" phase=").append(phase).append("\n");
                    frames.add(frame(context,fixture + "-" + phase + "-" + sim.getSimTickIndex() + ".png",sim));
                }
                if (sim.getSimTickIndex() % 1800 == 0) System.out.println("[yard-owners] " + fixture + " tick=" + sim.getSimTickIndex() + " phase=" + phase);
            }
            var result = sequence.report(sim);
            report.append("receipt=").append(result).append(" winner=").append(sim.getWinner()).append(" landed=").append(landed.size()).append("\n");
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append(" squad=").append(squad.id).append(" side=").append(squad.faction).append(" n=").append(sim.squadMemberCount(squad.id))
                        .append(" at=").append(squad.centroidX).append(',').append(squad.centroidY).append(" goal=").append(squad.currentGoal)
                        .append(" order=").append(sim.getSquadCommandDirective(squad.id)).append("\n");
            frames.add(frame(context,fixture + "-final.png",sim));
            if (absent) { assertTrue(landed.isEmpty()); assertFalse(result.complete()); return; }
            if (toolingLoss) { assertTrue(result.toolingLost()); assertFalse(result.complete()); }
            else if (techLoss) { assertEquals(2,result.techniciansLost()); assertFalse(result.complete()); }
            else if (retreat) { assertTrue(sequence.progress().visited(0)); assertFalse(result.complete()); }
            else { assertEquals(deployment.size(),landed.size()); assertTrue(result.complete(), fixture + " must secure the yard and finish inspection"); assertEquals(Faction.MARINE,sim.getWinner()); }
            var outcome = MissionResolver.compute(sim, mission, null);
            assertEquals(result, outcome.yardOwners);
            report.append(" casualties=").append(outcome.marinesLost).append("/").append(outcome.marinesEngaged)
                    .append(" rounds=").append(outcome.soldierTelemetry.values().stream().mapToInt(row -> row.roundsFired()).sum()).append("\n");
            if (fixture.equals("mixed")) {
                assertTrue(outcome.soldierTelemetry.values().stream().anyMatch(row -> row.roundsFired() > 0));
                report.append("Post-reward mixed comparison: persisted survivors, learned earned card, explicit issue, normal fitted lift. No second campaign settlement.\n");
                return;
            }
            boolean victory = outcome.victory;
            int payment = outcome.payoutEarned;
            if (fixture.equals("ordinary-15303")) addDebrief(context, frames, outcome);
            var cards = new ArrayList<SpecialItemData>(); var cargo = YardOwnersRewardTest.cargo(cards);
            assertEquals(payment,AgreusArcCampaign.settleYard(campaign,outcome,cargo,Set.of()));
            assertEquals(-1,AgreusArcCampaign.settleYard(campaign,outcome,cargo,Set.of()));
            assertEquals(victory ? 1 : 0,cards.size());
            report.append(" ledger accepted once / payment=").append(payment).append("\n");
            if (fixture.equals("ordinary-15303")) {
                assertEquals(AgreusArcProgress.ARBALEST_CARD,cards.get(0).getData());
                EquipmentTemplateLearning.learn(roster.armory(),cards.get(0).getData());
                EquipmentTemplateLearning.learn(roster.armory(),AgreusArcProgress.SPECTER_CARD); // Previously earned prerequisite card.
                roster.applySoldierOutcome(MissionResolver.resolvePersonnelOutcomes(outcome),10f,12f,outcome.soldierTelemetry,outcome.victory);
                var bytes = new ByteArrayOutputStream();
                try (var out = new ObjectOutputStream(bytes)) { out.writeObject(roster); }
                try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { earnedRoster=(MarineRoster)in.readObject(); }
                assertTrue(earnedRoster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
                for (String person : scheduled) assertNotNull(earnedRoster.soldierById(person));
                report.append("Actual victory card learned; personnel settled and roster serialized for mixed follow-on.\n");
            }
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
            frames.add(new SnapshotArtifact("yard-owners-debrief.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }
    private void addBoard(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var mission = mission();
        var props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("locationLabel", "Agreus / Arcadia");
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-rusk", "client-rusk-name", "client-rusk-rep",
                OpeningBroker.CREST, "client-card selected", AgreusArcCampaign.NAME, "Latchkey Yard / Ko Combine concession", false, none)));
        props.put("missionHeader", "House Rusk / Latchkey Yard");
        MissionSelectScreen.putAgreus(props, afterWarrant(), "");
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(props, mission, none, none);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("yard-owners-contract-board.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }
    private FittedBoat boat(String id) {
        return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.standard(BoatFittingSlot.PLATING), BoatFitting.standard(BoatFittingSlot.DRIVE), id);
    }
    private Mission mission() { return AgreusArcCampaign.offer(afterWarrant(), "Agreus"); }
    private AgreusArcProgress afterWarrant() { return YardOwnersRewardTest.afterWarrant(); }
    private SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim) throws Exception {
        return new SnapshotArtifact(name, new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 88, 64, 176)
                .withImpactEffects().withUnitShadows().render(sim, "LATCHKEY YARD / " + sim.getYardOwners().progress().phase(), ReviewAnnotations.builder().build()));
    }
}
