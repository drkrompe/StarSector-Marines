package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.TermsOfProtectionSequence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Phase;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.marine.LoadoutCardLearning;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in production playback of Terms of Protection on both signed routes. The factory, the
 * company's lift, the employer parties, Berth Four's authored groups and every order come from
 * production code; the controls are explicit diagnostics (an absent company, killed technicians or
 * representatives, a withdrawal) followed by the replacement mobilization each one opens. Each
 * terminal receipt goes through the real campaign ledger and award path. Detached evidence: no live
 * travel, fleet ownership, host save/reload or economy acceptance.
 */
public final class TermsOfProtectionSnapshotSuite implements SnapshotSuite {
    /** A tender-built Vulcan covering the Cinder Steps gate from inside the wall. */
    static final int BUILD_X = 114, BUILD_Y = 50;

    enum Control { ORDINARY, ABSENT, TECHNICIAN_LOSS, REPRESENTATIVE_LOSS, RETREAT }
    record Fixture(String id, long seed, AgreusYardAgreement route, Control control) { }

    public String id() { return "terms-of-protection"; }
    public String label() { return "Terms of Protection: both signed routes, Berth Four's mortar and rigs, failure and replacement controls"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String property = "battle.unitUpdate.minimumParallelUnits";
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try { return renderSerial(context); }
        finally { if (previous == null) System.clearProperty(property); else System.setProperty(property, previous); }
    }

    private List<SnapshotArtifact> renderSerial(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>();
        for (var route : AgreusYardAgreement.values()) addBoard(context, frames, route);
        var report = new StringBuilder("Terms of Protection / production factory / 1920x1080 / 100% scale.\n"
                + "Detached named company: 36 marines in three Salvage-assisted Sections (learned and explicitly fitted), two fitted six-seat boats.\n"
                + "Detached member-backed Morrow source through normal Ship on Station activation and ship-bar build command.\n"
                + "Diagnostic controls are explicit; every receipt is settled through the real ledger and award path.\n"
                + "No live campaign, travel, ownership, economy, save/reload or host settlement claim follows from these frames.\n");
        var directory = context.projectRoot().resolve("build/reports/terms-of-protection");
        Files.createDirectories(directory);
        var cases = List.of(
                new Fixture("custody-15304", 15304, AgreusYardAgreement.HOUSE_CUSTODY, Control.ORDINARY),
                new Fixture("custody-16304", 16304, AgreusYardAgreement.HOUSE_CUSTODY, Control.ORDINARY),
                new Fixture("compact-15304", 15304, AgreusYardAgreement.YARD_COMPACT, Control.ORDINARY),
                new Fixture("compact-16304", 16304, AgreusYardAgreement.YARD_COMPACT, Control.ORDINARY),
                new Fixture("absent-company", 15304, AgreusYardAgreement.HOUSE_CUSTODY, Control.ABSENT),
                new Fixture("technician-loss", 15304, AgreusYardAgreement.YARD_COMPACT, Control.TECHNICIAN_LOSS),
                new Fixture("representative-loss", 15304, AgreusYardAgreement.HOUSE_CUSTODY, Control.REPRESENTATIVE_LOSS),
                new Fixture("retreat", 16304, AgreusYardAgreement.YARD_COMPACT, Control.RETREAT));
        String filter = System.getProperty("battle.termsOfProtection.fixture", "");
        boolean[] debriefed = new boolean[2];
        try {
            for (var fixture : cases) {
                if (!filter.isBlank() && !fixture.id().contains(filter)) continue;
                var campaign = TermsOfProtectionRewardTest.signed(fixture.route());
                var first = play(context, fixture, campaign, frames, report, debriefed);
                if (fixture.control() != Control.ORDINARY && fixture.control() != Control.ABSENT) {
                    assertFalse(first, fixture.id() + " must not complete");
                    assertEquals(AgreusYardAgreement.HOUSE_CUSTODY == fixture.route(),
                            AgreusArcCampaign.custodyRoute(AgreusArcCampaign.offer(campaign, "Agreus")), "the signed route stands for the replacement");
                    play(context, new Fixture("replacement-" + fixture.id(), fixture.seed(), fixture.route(), Control.ORDINARY),
                            campaign, frames, report, debriefed);
                }
            }
        } finally { Files.writeString(directory.resolve("playback.txt"), report); }
        return frames;
    }

    /** Plays one mobilization and settles it; returns whether it was a victory. */
    private boolean play(SnapshotContext context, Fixture fixture, AgreusArcProgress campaign, List<SnapshotArtifact> frames,
                         StringBuilder report, boolean[] debriefed) throws Exception {
        var mission = AgreusArcCampaign.offer(campaign, "Agreus");
        assertEquals(fixture.route() == AgreusYardAgreement.HOUSE_CUSTODY, AgreusArcCampaign.custodyRoute(mission));
        assertTrue(campaign.begin(mission.id, "agreus", "Agreus"));
        assertEquals(fixture.route(), campaign.activeAgreement());
        var roster = DebugMissionCatalog.agreusCompany(3);
        LoadoutCardLearning.learn(roster.armory(), AgreusArcProgress.SPECTER_CARD);
        LoadoutCardLearning.learn(roster.armory(), AgreusArcProgress.ARBALEST_CARD);
        for (var squad : roster.squads()) if (!squad.reserve())
            assertEquals(SquadEquipmentResult.APPLIED, roster.applySquadEquipment(squad.id(), squad.weaponDoctrineId(), SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 36);
        var boats = List.of(boat("terms-boat-one"), boat("terms-boat-two"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        var source = new ShipSupportSource("shepherd", "Morrow", true, Map.of(), "terms-morrow-member");
        report.append('\n').append(fixture).append(" mission=").append(mission.id).append('\n');
        boolean absent = fixture.control() == Control.ABSENT;
        try (var sim = BattleSetup.createTermsOfProtection(fixture.seed(), manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD),
                AgreusArcCampaign.route(fixture.route()))) {
            deployment.applyToAuthoredPlayerLift(sim);
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(source))));
            var sequence = sim.getTermsOfProtection();
            var parties = sequence.parties();
            assertTrue(sim.tenders().tenders().isEmpty(), "the factory never manufactures a tender");
            assertEquals(fixture.route() == AgreusYardAgreement.HOUSE_CUSTODY, parties.guards() >= 0);
            assertEquals(fixture.route() == AgreusYardAgreement.YARD_COMPACT, parties.fitters() >= 0);
            var scheduled = new HashSet<String>(); int aircraft = 0, powered = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                for (var cycle : sim.world().mission(air).cycleLoadouts) for (var person : cycle) {
                    assertNotNull(person.campaignSoldierId); assertTrue(scheduled.add(person.campaignSoldierId));
                    if (person.armorClass == ArmorClass.POWER_SUIT) powered++;
                }
                if (absent) sim.world().mission(air).pendingDelay += 9999;
            }
            assertEquals(36, scheduled.size()); assertEquals(2, aircraft);
            assertEquals(12, powered, "three Salvage-assisted Sections wear four powered suits each");
            report.append("  parties=").append(parties).append(" carrier=").append(sequence.carrierId()).append(" companyPowered=").append(powered).append('\n');
            frames.add(frame(context, fixture.id() + "-arrival.png", sim, false));
            var landed = new HashSet<String>();
            var phase = sequence.progress().phase();
            boolean injected = false, requestedShip = false, requestedBuild = false, buildAccepted = false, retreated = false;
            boolean mortarFrame = false, rigFrame = false, laneFrame = false;
            int counterBatteryTick = -1, mortarRoundsSeen = 0;
            String lastOrders = ""; boolean vulcanCompleted = false;
            int maxTicks = absent ? 3600 : Integer.getInteger("battle.termsOfProtection.maxTicks", 21600);
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (fixture.control() == Control.TECHNICIAN_LOSS && !injected && sequence.progress().work() > 20) {
                    killSquad(sim, parties.technicians()); injected = true;
                    report.append("  diagnostic: killed both utilities technicians at work=").append(Math.round(sequence.progress().work())).append("s\n");
                }
                if (fixture.control() == Control.REPRESENTATIVE_LOSS && !injected && sequence.progress().signed()) {
                    killSquad(sim, parties.representatives()); injected = true;
                    report.append("  diagnostic: killed both custody representatives after signing tick=").append(sim.getSimTickIndex()).append('\n');
                }
                if (fixture.control() == Control.RETREAT && sequence.pellSighted()) {
                    assertTrue(sim.retreat()); retreated = true;
                    report.append("  player withdrawal after Berth Four was sighted tick=").append(sim.getSimTickIndex()).append('\n');
                    break;
                }
                if (phase == Phase.COMMISSIONING && !requestedShip && sim.playerSeesCell(BUILD_X, BUILD_Y)) {
                    sim.getCommandPowerService().requestActivation(ShipOnStation.ID, BUILD_X, BUILD_Y);
                    requestedShip = true;
                    report.append("  requested normal Ship on Station tick=").append(sim.getSimTickIndex()).append('\n');
                }
                Tender tender = sim.tenders().tenders().isEmpty() ? null : sim.tenders().tenders().get(0);
                if (requestedShip && !requestedBuild && tender != null && !tender.grounded()) {
                    assertSame(source, tender.ship().supportSource());
                    sim.shipCommands().requestBuild(tender, BuildOption.VULCAN_TURRET, BUILD_X, BUILD_Y);
                    requestedBuild = true;
                    report.append("  requested Vulcan at the Cinder Steps gate approach tick=").append(sim.getSimTickIndex()).append('\n');
                }
                sim.advance(BattleSimulation.TICK_DT);
                if (!buildAccepted && requestedBuild && tender != null && sim.shipCommands().notice(tender.ship()) == ShipCommandService.Notice.LAID) {
                    buildAccepted = true; report.append("  build accepted by the ordinary ship command service tick=").append(sim.getSimTickIndex()).append('\n');
                }
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
                    String person = (String) sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(scheduled.contains(person), "no anonymous replacement"); landed.add(person);
                    if (counterBatteryTick < 0 && sequence.mortarSquadId() >= 0
                            && Math.hypot(sim.world().x(unit) - LatchkeyYard.MORTAR_POST.x(), sim.world().y(unit) - LatchkeyYard.MORTAR_POST.y()) < 8)
                        counterBatteryTick = sim.getSimTickIndex();
                }
                mortarRoundsSeen = Math.max(mortarRoundsSeen, sim.fieldMortars().tubes().stream()
                        .filter(t -> t.squadId == sequence.mortarSquadId()).mapToInt(t -> t.roundsFired()).sum());
                if (tender != null) {
                    String orders = tender.orders().stream().map(o -> o.phase() + (o.failure() == null ? "" : "(" + o.failure() + ")")).toList().toString();
                    if (!orders.equals(lastOrders)) { lastOrders = orders; vulcanCompleted |= orders.contains("COMPLETE"); report.append("  tick=").append(sim.getSimTickIndex()).append(" tenderOrders=").append(orders).append('\n'); }
                }
                if (!rigFrame && sequence.pellCommitted()) {
                    for (int squad : sequence.pellSquadIds()) for (int m = 0; m < sim.squadMemberCount(squad); m++) {
                        long id = sim.squadMemberAt(squad, m);
                        if (sim.identity().armorClass(id) == ArmorClass.POWER_SUIT && sim.playerSeesCell(sim.world().cellX(id), sim.world().cellY(id))) {
                            rigFrame = true;
                            frames.add(frameAt(context, fixture.id() + "-rig-sighted-" + sim.getSimTickIndex() + ".png", sim,
                                    sim.world().cellX(id), sim.world().cellY(id), 70, "BERTH FOUR / BOARDING JACK SIGHTED"));
                        }
                    }
                }
                if (!mortarFrame && sequence.mortarReported()) {
                    mortarFrame = true;
                    frames.add(frameAt(context, fixture.id() + "-mortar-incoming-" + sim.getSimTickIndex() + ".png", sim, 116, 66, 80, "MORTAR FIRE ON THE HALL"));
                }
                if (!laneFrame && sequence.laneSighted()) {
                    laneFrame = true;
                    frames.add(frameAt(context, fixture.id() + "-lane-" + sim.getSimTickIndex() + ".png", sim, 128, 48, 80, "PRIZE CREWS / CINDER STEPS LANE"));
                }
                if (phase != sequence.progress().phase()) {
                    phase = sequence.progress().phase();
                    report.append("  tick=").append(sim.getSimTickIndex()).append(" phase=").append(phase).append('\n');
                    frames.add(frame(context, fixture.id() + "-" + phase + "-" + sim.getSimTickIndex() + ".png", sim, false));
                }
                if (sim.getSimTickIndex() % 1800 == 0) System.out.println("[terms-of-protection] " + fixture.id() + " tick=" + sim.getSimTickIndex()
                        + " phase=" + phase + " work=" + Math.round(sequence.progress().work()));
            }
            var result = sequence.report(sim);
            report.append("  receipt=").append(result).append(" winner=").append(sim.getWinner()).append(" landed=").append(landed.size())
                    .append(" failure=").append(sequence.progress().failure()).append('\n');
            report.append("  berthFour committed=").append(sequence.pellCommitted()).append(" pellSighted=").append(sequence.pellSighted())
                    .append(" laneCommitted=").append(sequence.laneCommitted()).append(" laneSighted=").append(sequence.laneSighted())
                    .append(" mortarReported=").append(sequence.mortarReported()).append(" mortarSighted=").append(sequence.mortarSighted())
                    .append(" mortarWithdrew=").append(sequence.mortarWithdrawing()).append(" escaped=").append(sequence.escapedCount())
                    .append(" mortarSetUps=").append(sim.fieldMortars().setUps()).append(" mortarRoundsFired=").append(mortarRoundsSeen)
                    .append(" counterBatteryArrivalTick=").append(counterBatteryTick).append('\n');
            report.append("  tender requested=").append(requestedShip).append(" buildAccepted=").append(buildAccepted)
                    .append(" vulcanCompleted=").append(vulcanCompleted).append('\n');
            report.append("  beats=").append(sim.getStoryBeats().stream().map(e -> e.tick() + ":" + e.id()).toList()).append('\n');
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append("  squad=").append(squad.id).append(" side=").append(squad.faction).append(" n=").append(sim.squadMemberCount(squad.id))
                        .append(" at=").append(Math.round(squad.centroidX)).append(',').append(Math.round(squad.centroidY))
                        .append(" order=").append(sim.getSquadCommandDirective(squad.id)).append('\n');
            frames.add(frame(context, fixture.id() + "-final.png", sim, false));
            if (absent) {
                assertTrue(landed.isEmpty()); assertFalse(result.delivered(), "employer people never deliver without the company");
                assertFalse(sequence.pellCommitted(), "nothing commits against a handover that never began");
                report.append("  absent: no landing, no delivery, no commitment; ledger not called for an unresolved observation window\n");
                return false;
            }
            if (fixture.control() == Control.TECHNICIAN_LOSS) assertEquals(TermsOfProtectionProgress.Failure.TECHNICIANS_LOST, sequence.progress().failure());
            if (fixture.control() == Control.REPRESENTATIVE_LOSS) assertEquals(TermsOfProtectionProgress.Failure.REPRESENTATIVES_LOST, sequence.progress().failure());
            if (retreated) assertFalse(result.complete());
            var outcome = MissionResolver.compute(sim, mission, null);
            assertEquals(result, outcome.termsOfProtection);
            report.append("  casualties=").append(outcome.marinesLost).append('/').append(outcome.marinesEngaged)
                    .append(" rounds=").append(outcome.soldierTelemetry.values().stream().mapToInt(row -> row.roundsFired()).sum()).append('\n');
            int payment = AgreusArcCampaign.settleTerms(campaign, outcome);
            assertEquals(outcome.victory ? 32000 : 0, payment);
            assertEquals(-1, AgreusArcCampaign.settleTerms(campaign, outcome), "a duplicate receipt writes nothing");
            assertEquals(outcome.victory, campaign.agreementRatified());
            report.append("  ledger accepted once / payment=").append(payment).append(" ratified=").append(campaign.agreementRatified()).append('\n');
            int route = fixture.route().ordinal();
            if (outcome.victory && !debriefed[route]) { debriefed[route] = true; addDebrief(context, frames, outcome, fixture.route()); }
            return outcome.victory;
        }
    }

    private static void killSquad(BattleSimulation sim, int squad) {
        while (sim.squadMemberCount(squad) > 0) sim.applyDamage(sim.squadMemberAt(squad, 0), 100000, 100000);
    }

    private void addDebrief(SnapshotContext context, List<SnapshotArtifact> frames, MissionOutcome outcome, AgreusYardAgreement route) throws Exception {
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
            frames.add(new SnapshotArtifact("terms-" + route.name().toLowerCase().replace('_', '-') + "-debrief.png",
                    new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                            .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }

    private void addBoard(SnapshotContext context, List<SnapshotArtifact> frames, AgreusYardAgreement route) throws Exception {
        var progress = TermsOfProtectionRewardTest.signed(route);
        var mission = AgreusArcCampaign.offer(progress, "Agreus");
        var props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("locationLabel", "Agreus / Arcadia");
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-rusk", "client-rusk-name", "client-rusk-rep",
                OpeningBroker.CREST, "client-card selected", AgreusArcCampaign.NAME, "Latchkey Yard / Ko Combine concession", false, none)));
        props.put("missionHeader", "House Dormer / Latchkey handover");
        MissionSelectScreen.putAgreus(props, progress, "");
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(props, mission, none, none);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("terms-" + route.name().toLowerCase().replace('_', '-') + "-contract-board.png",
                    new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                            .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }

    private FittedBoat boat(String id) {
        return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.standard(BoatFittingSlot.PLATING), BoatFitting.standard(BoatFittingSlot.DRIVE), id);
    }

    private SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim, boolean ignored) throws Exception {
        return frameAt(context, name, sim, 88, 64, 176, null);
    }

    private SnapshotArtifact frameAt(SnapshotContext context, String name, BattleSimulation sim, int x, int y, int across, String note) throws Exception {
        var s = sim.getTermsOfProtection();
        var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, x, y, across).withImpactEffects().withUnitShadows();
        var marks = ReviewAnnotations.builder()
                .label(LatchkeyYard.HANDOVER.x(), LatchkeyYard.HANDOVER.y() - 2, "HANDOVER BAY", ReviewStyle.OBJECTIVE)
                .box(LatchkeyYard.CALIBRATION_LEFT, LatchkeyYard.CALIBRATION_BOTTOM, LatchkeyYard.CALIBRATION_RIGHT, LatchkeyYard.CALIBRATION_TOP,
                        "COMMISSIONING", ReviewStyle.OBJECTIVE)
                .label(LatchkeyYard.MORTAR_POST.x(), LatchkeyYard.MORTAR_POST.y() + 4, "MORTAR PIT", ReviewStyle.ROUTE);
        if (s.route() == TermsOfProtectionProgress.Route.HOUSE_CUSTODY)
            marks.label(LatchkeyYard.GUARD_POST.x(), LatchkeyYard.GUARD_POST.y() + 3, "ESTATE GUARDS", ReviewStyle.LANDING);
        else marks.label(93, 83, "FITTERS' BENCHES", ReviewStyle.LANDING);
        String caption = "LATCHKEY / TERMS OF PROTECTION / " + s.route() + " / " + s.progress().phase()
                + " / WORK " + Math.round(s.progress().work()) + "s" + (note == null ? "" : " / " + note);
        return new SnapshotArtifact(name, renderer.render(sim, caption, marks.build()));
    }

}
