package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.marine.ArmorClass;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production acceptance. Diagnostic casualties are labelled; an unresolved run fails ordinary acceptance. */
public final class WaterUnderSealSnapshotSuite implements SnapshotSuite {
    private enum Control { ORDINARY, ABSENT, NO_BUILD, CARGO_LOSS, TECHNICIAN_LOSS, RETREAT, LATE_TECHNICIAN_LOSS, GUN_LOSS }
    private record Fixture(String id, long seed, Control control) { }
    private record Observation(WaterUnderSealProgress.Report report, boolean retreat, Set<String> landed) { }
    public String id() { return "water-under-seal"; }
    public String label() { return "Water Under Seal: named company, owned tender, real construction and North Weir handover"; }

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
        addBoard(context, frames);
        var report = new StringBuilder("Water Under Seal / production factory / 1920x1080 / 100% scale.\n"
                + "Detached named company, two full squads with regular issued equipment, two fitted six-seat boats.\n"
                + "Detached member-backed Morrow support source uses normal Ship on Station activation and ship-bar build command.\n"
                + "Company and technicians otherwise use production orders; diagnostic absent, loss, no-build and withdrawal controls are explicit.\n"
                + "No live campaign, travel, ownership, economy, save/reload or host settlement claim follows from these frames.\n");
        var directory = context.projectRoot().resolve("build/reports/water-under-seal");
        Files.createDirectories(directory);
        var cases = List.of(new Fixture("ordinary-15301", 15301, Control.ORDINARY),
                new Fixture("ordinary-16101", 16101, Control.ORDINARY),
                new Fixture("ordinary-16102", 16102, Control.ORDINARY),
                new Fixture("absent-company", 16101, Control.ABSENT),
                new Fixture("no-construction", 16101, Control.NO_BUILD),
                new Fixture("cargo-loss", 16101, Control.CARGO_LOSS),
                new Fixture("technician-loss", 16101, Control.TECHNICIAN_LOSS),
                new Fixture("withdraw-at-construction", 16101, Control.RETREAT),
                new Fixture("late-technician-loss", 16101, Control.LATE_TECHNICIAN_LOSS),
                new Fixture("destroyed-gun-rebuild", 15301, Control.GUN_LOSS));
        String filter = System.getProperty("battle.waterUnderSeal.fixture", "");
        for (var fixture : cases) {
            if (!filter.isBlank() && !fixture.id().contains(filter)) continue;
            try {
                var progress = new AgreusArcProgress();
                var first = play(context, fixture, progress, frames, report);
                if (first.retreat() || fixture.control() == Control.CARGO_LOSS || fixture.control() == Control.TECHNICIAN_LOSS) {
                    var retry = play(context, new Fixture(fixture.id() + "-replacement", fixture.seed(), Control.ORDINARY), progress, frames, report);
                    assertTrue(retry.report().complete(), "Replacement mobilization must be playable after an observed failed attempt");
                }
            } finally { Files.writeString(directory.resolve("playback.txt"), report); }
        }
        return frames;
    }
    private Observation play(SnapshotContext context, Fixture fixture, AgreusArcProgress campaign, List<SnapshotArtifact> frames, StringBuilder report) throws Exception {
        var mission = AgreusArcCampaign.offer(campaign, "Agreus");
        assertTrue(campaign.begin(mission.id, "agreus"));
        var roster = DebugMissionCatalog.expectedCompany(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission(), 2);
        report.append("  regular issue=").append(roster.soldiers().stream().collect(Collectors.groupingBy(
                soldier -> soldier.primaryId() + "/" + soldier.primaryGrade() + "/" + soldier.armorId(), Collectors.counting()))).append('\n');
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 24);
        var boats = List.of(boat("water-boat-one"), boat("water-boat-two"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        var source = new ShipSupportSource("shepherd", "Morrow", true, Map.of(), "water-morrow-member");
        report.append('\n').append(fixture).append(" boats=").append(boats).append(" source=").append(source).append('\n');
        try (var sim = BattleSetup.createWaterUnderSeal(fixture.seed(), manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD))) {
            deployment.applyToAuthoredPlayerLift(sim);
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(source))));
            var sequence = sim.getWaterUnderSeal();
            report.append("  repairCargoEntity=").append(sequence.repairCarrierId()).append(" technicianSquad=").append(sequence.technicianSquadId())
                    .append(" technicians=").append(sequence.technicianEntityIds(sim)).append('\n');
            assertTrue(sim.tenders().tenders().isEmpty(), "Factory must not manufacture a replacement tender");
            var committed = new HashSet<String>();
            var campaignSquads = new HashMap<String, String>();
            for (var seat : deployment.commitments()) campaignSquads.put(seat.campaignSoldierId(), seat.campaignSquadId());
            int aircraft = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                var flight = sim.world().mission(air);
                for (var cycle : flight.cycleLoadouts) for (var person : cycle) {
                    assertNotNull(person.campaignSoldierId);
                    assertTrue(committed.add(person.campaignSoldierId), "Every committed person owns exactly one seat");
                }
                if (fixture.control() == Control.ABSENT) flight.pendingDelay += 9999;
            }
            assertEquals(2, aircraft);
            assertEquals(24, committed.size());
            report.append("  committed=").append(committed.stream().sorted().toList()).append('\n');
            var landed = new HashSet<String>();
            var reunited = new HashMap<String, Set<Integer>>();
            var phase = sequence.progress().phase();
            boolean requestedShip = false, requestedBuild = false, injected = false, withdrew = false, firingFrame = false, inspectedFirstAttack = false, landedFrame = false, buildAccepted = false;
            int gunShots = 0, acceptedBuilds = 0;
            frames.add(frame(context, fixture.id() + "-arrival.png", sim, "NORTH WEIR / " + phase, false));
            int maxTicks = fixture.control() == Control.ABSENT ? 3600 : Integer.getInteger("battle.waterUnderSeal.maxTicks", 27000);
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (fixture.control() == Control.CARGO_LOSS && !injected) {
                    sim.convoy().destroy(sequence.repairCarrierId());
                    injected = true;
                    report.append("  diagnostic: destroyed active repair cargo\n");
                }
                if ((fixture.control() == Control.TECHNICIAN_LOSS
                        || fixture.control() == Control.LATE_TECHNICIAN_LOSS && phase == WaterUnderSealProgress.Phase.ATTACK) && !injected
                        && !sequence.technicianEntityIds(sim).isEmpty()) {
                    for (long technician : sequence.technicianEntityIds(sim)) sim.applyDamage(technician, 100000, 100000);
                    injected = true;
                    report.append("  diagnostic: killed all actual deployed technicians at ").append(phase).append('\n');
                }
                if (fixture.control() == Control.GUN_LOSS && phase == WaterUnderSealProgress.Phase.ATTACK && !injected) {
                    var guns = sequence.turretEntityIds().stream().filter(sim.world()::isAlive).toList();
                    assertFalse(guns.isEmpty(), "Diagnostic loss must destroy an actual commissioned gun");
                    for (long gun : guns) sim.applyDamage(gun, 100000, 100000);
                    injected = true;
                    report.append("  diagnostic: destroyed actual commissioned road gun ").append(guns).append('\n');
                }
                if (phase == WaterUnderSealProgress.Phase.CONSTRUCTION) {
                    var picture = MissionObjectiveProjection.capture(sim).objectives().stream()
                            .filter(item -> item.id().equals("water-under-seal")).findFirst().orElseThrow();
                    assertEquals("weir-road-gun", picture.focusMarkerId());
                    assertEquals(sequence.buildX(), picture.focus().x());
                    assertEquals(sequence.buildY(), picture.focus().y());
                    assertTrue(picture.focus().radius() > 0, "Actual construction site must be legible on the mission picture");
                    if (fixture.control() == Control.RETREAT) { assertTrue(sim.retreat()); withdrew = true; break; }
                    if (!requestedShip && sim.playerSeesCell(sequence.buildX(), sequence.buildY())) {
                        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, sequence.buildX(), sequence.buildY());
                        requestedShip = true;
                        report.append("  requested normal Ship on Station tick=").append(sim.getSimTickIndex()).append('\n');
                    }
                    if (fixture.control() == Control.NO_BUILD && !requestedBuild && sequence.tender() != null) {
                        sim.shipCommands().requestHold(sequence.tender().ship());
                        sim.shipCommands().requestCancelBuilds(sequence.tender());
                        requestedBuild = true;
                        report.append("  explicit player HOLD and cancel construction on the actual called tender\n");
                    }
                    if (fixture.control() != Control.NO_BUILD && !requestedBuild && sequence.tender() != null) {
                        assertSame(source, sequence.tender().ship().supportSource());
                        assertFalse(sequence.tender().grounded());
                        sim.shipCommands().requestBuild(sequence.tender(), BuildOption.VULCAN_TURRET, sequence.buildX(), sequence.buildY());
                        requestedBuild = true;
                        report.append("  requested actual road gun tick=").append(sim.getSimTickIndex()).append('\n');
                    }
                }
                sim.advance(BattleSimulation.TICK_DT);
                if (!buildAccepted && requestedBuild && fixture.control() != Control.NO_BUILD && sequence.tender() != null
                        && sim.shipCommands().notice(sequence.tender().ship()) == ShipCommandService.Notice.LAID) {
                    buildAccepted = true;
                    acceptedBuilds++;
                    report.append("  manual road-gun request accepted by ordinary ship command service\n");
                }
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
                    String soldier = (String) sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY,
                            BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertNotNull(soldier, "No anonymous player replacement");
                    assertTrue(committed.contains(soldier));
                    landed.add(soldier);
                    reunited.computeIfAbsent(campaignSquads.get(soldier), ignored -> new HashSet<>()).add(sim.squad().squadId(unit));
                }
                if (!landedFrame && !landed.isEmpty()) {
                    landedFrame = true;
                    frames.add(frame(context, fixture.id() + "-first-named-landing.png", sim, "ACTUAL FITTED BOAT / FIRST NAMED ARRIVAL", false));
                }
                if (!inspectedFirstAttack && !sequence.roadSquadIds().isEmpty()) {
                    inspectedFirstAttack = true;
                    int squad = sequence.roadSquadIds().get(0), powered = 0, regular = 0;
                    for (int i = 0; i < sim.squadMemberCount(squad); i++) {
                        long attacker = sim.squadMemberAt(squad, i);
                        if (sim.identity().armorClass(attacker) == ArmorClass.POWER_SUIT) powered++; else regular++;
                    }
                    assertEquals(2, powered, "First committed road element must demonstrate two actual crude powered opponents");
                    assertEquals(4, regular, "Powered opponents remain individual wearers in a mixed regular squad");
                    report.append("  observed first mixed road element: powered=").append(powered).append(" regular=").append(regular).append('\n');
                }
                for (var shot : sim.getShotsThisFrame()) if (sequence.turretEntityIds().contains(shot.shooterId)) gunShots++;
                if (!firingFrame && gunShots > 0) {
                    firingFrame = true;
                    frames.add(frame(context, fixture.id() + "-road-gun-fire.png", sim, "REAL CONSTRUCTED GUN / FIRING", true));
                }
                var next = sequence.progress().phase();
                if (next != phase) {
                    report.append("  phase=").append(next).append(" tick=").append(sim.getSimTickIndex()).append(" landed=").append(landed.size()).append('\n');
                    frames.add(frame(context, fixture.id() + "-" + next.name().toLowerCase() + "-" + sim.getSimTickIndex() + ".png", sim, "WATER UNDER SEAL / " + next, true));
                    if (next == WaterUnderSealProgress.Phase.CONSTRUCTION) { requestedBuild = false; buildAccepted = false; }
                    phase = next;
                }
                if (sim.getSimTickIndex() % 1800 == 0) {
                    String status = "  tick=" + sim.getSimTickIndex() + " phase=" + phase + " landed=" + landed.size() + " gunShots=" + gunShots;
                    report.append(status).append('\n');
                    System.out.println("[water-under-seal] " + fixture.id() + status);
                }
                if (fixture.control() == Control.NO_BUILD && phase == WaterUnderSealProgress.Phase.CONSTRUCTION && sim.getSimTickIndex() > 12000) break;
            }
            var result = sequence.report(sim);
            report.append("  final=").append(result).append(" complete=").append(sim.isComplete()).append(" winner=").append(sim.getWinner())
                    .append(" gunShots=").append(gunShots).append(" acceptedBuilds=").append(acceptedBuilds).append(" landed=").append(landed.size()).append(" reunited=").append(reunited).append('\n');
            for (long gun : sequence.turretEntityIds()) report.append("  commissionedGun=").append(gun).append(" alive=")
                    .append(sim.world().isAlive(gun)).append(" hp=").append(sim.world().isAlive(gun) ? sim.world().hp(gun) : 0).append('\n');
            if (sequence.tender() != null) report.append("  tenderBuilt=").append(sequence.tender().built())
                    .append(" currentOrders=").append(sequence.tender().orders()).append(" departed=").append(sequence.tender().departed()).append('\n');
            report.append("  attackResolution=").append(sequence.attackReport(sim)).append('\n');
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append("  squad=").append(squad.id).append(" faction=").append(squad.faction).append(" members=")
                        .append(sim.squadMemberCount(squad.id)).append(" at=").append(squad.centroidX).append(',').append(squad.centroidY)
                        .append(" command=").append(sim.getSquadCommandDirective(squad.id)).append(" goal=").append(squad.currentGoal).append('\n');
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
                var squad = sim.squadOf(unit);
                var step = squad == null || squad.currentPlan == null ? null : squad.currentPlan.currentStep();
                report.append("  defender=").append(unit).append(" at=").append(sim.world().x(unit)).append(',').append(sim.world().y(unit))
                        .append(" goal=").append(squad == null || squad.currentGoal == null ? null : squad.currentGoal.name())
                        .append(" action=").append(step == null ? null : step.action.name()).append(" hp=").append(sim.world().hp(unit))
                        .append(" settled=").append(sim.movement().settled(unit)).append('\n');
            }
            for (long technician : sequence.technicianEntityIds(sim)) report.append("  technician=").append(technician).append(" at=")
                    .append(sim.world().x(technician)).append(',').append(sim.world().y(technician)).append('\n');
            report.append("  safeWork=").append(sequence.progress().work()).append(" carrierAt=").append(sim.convoy().body(sequence.repairCarrierId()).x)
                    .append(',').append(sim.convoy().body(sequence.repairCarrierId()).y).append('\n');
            for (var beat : sim.getStoryBeats()) report.append("  ").append(beat).append('\n');
            frames.add(frame(context, fixture.id() + "-final.png", sim, "FINAL OBSERVATION / " + phase, false));
            for (var ids : reunited.values()) assertEquals(1, ids.size(), "Boat trips must reassemble each original squad");
            switch (fixture.control()) {
                case ORDINARY -> {
                    if (maxTicks < 27000) {
                        report.append("  shortened diagnostic window; ordinary acceptance not asserted\n");
                        break;
                    }
                    assertEquals(24, landed.size());
                    assertTrue(result.complete(), "Ordinary named company must achieve every objective within the observation window");
                    assertTrue(sim.isComplete());
                    assertEquals(Faction.MARINE, sim.getWinner());
                    assertTrue(gunShots > 0, "Constructed gun must engage committed opposition");
                    assertTrue(requestedShip && requestedBuild && buildAccepted, "Actual player build request must be accepted");
                }
                case ABSENT -> { assertTrue(landed.isEmpty()); assertFalse(result.complete()); assertNotEquals(Faction.MARINE, sim.getWinner()); }
                case NO_BUILD -> { assertFalse(result.turretConstructed()); assertFalse(result.complete()); assertNotEquals(Faction.MARINE, sim.getWinner()); }
                case CARGO_LOSS -> { assertTrue(injected); assertTrue(result.cargoLost()); assertTrue(sim.isComplete()); assertNotEquals(Faction.MARINE, sim.getWinner()); }
                case TECHNICIAN_LOSS, LATE_TECHNICIAN_LOSS -> { assertTrue(injected); assertEquals(2, result.techniciansLost()); assertTrue(sim.isComplete()); assertNotEquals(Faction.MARINE, sim.getWinner()); }
                case GUN_LOSS -> {
                    assertTrue(injected);
                    assertTrue(acceptedBuilds >= 2, "Replacement must be an actual normal accepted build");
                    assertTrue(result.complete(), "A destroyed road gun can be rebuilt without replacing the committed attack pool");
                    assertEquals(Faction.MARINE, sim.getWinner());
                    assertEquals(12, sequence.attackReport(sim).deployed());
                    assertTrue(gunShots > 0);
                }
                case RETREAT -> { assertTrue(withdrew); assertTrue(sim.isComplete()); assertNotEquals(Faction.MARINE, sim.getWinner()); }
            }
            if (sim.isComplete()) {
                boolean victory = sim.getWinner() == Faction.MARINE;
                int payment = victory ? AgreusArcProgress.PAYMENT : 0;
                assertEquals(payment, settle(campaign, mission, result, victory, payment));
                assertEquals(-1, settle(campaign, mission, result, victory, payment), "Same observed attempt cannot settle twice");
                report.append("  actual observed result accepted by campaign ledger once; payment=").append(payment)
                        .append(" nextAttempt=").append(campaign.missionId()).append('\n');
            }
            if (sim.getWinner() == Faction.MARINE) addDebrief(context, frames, fixture.id(), mission, result, sim);
            return new Observation(result, withdrew, Set.copyOf(landed));
        }
    }
    private int settle(AgreusArcProgress progress, Mission mission, WaterUnderSealProgress.Report result, boolean victory, int payment) {
        return progress.settle(mission.id, "agreus", victory, payment, result.cargoDelivered(), result.northRoomReconnected(),
                result.southRoomReconnected(), result.turretConstructed(), result.attackResolved(), result.companySurvived(),
                result.techniciansLost(), result.militiaLost(), result.cargoLost());
    }
    private void addDebrief(SnapshotContext context, List<SnapshotArtifact> frames, String prefix, Mission mission,
                           WaterUnderSealProgress.Report result, BattleSimulation sim) throws Exception {
        int survivors = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) == Faction.MARINE && sim.identity().type(unit) == UnitType.MARINE) survivors++;
        }
        var outcome = MissionOutcome.builder().mission(mission).victory(true).payoutEarned(AgreusArcProgress.PAYMENT)
                .marinesEngaged(24).marinesLost(24 - survivors).alliesEngaged(10)
                .alliesLost(result.militiaLost() + result.techniciansLost()).waterUnderSeal(result).build();
        var props = ResultsScreen.previewProps(true, false);
        props.put("missionName", mission.name);
        props.put("resultRows", ResultsScreen.resultRows(outcome, LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
        props.put("squadRows", List.of());
        ResultsScreen.putStoryDebrief(props, outcome);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), ResultsScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), ResultsScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact(prefix + "-debrief.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }
    private void addBoard(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var mission = mission();
        var props = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        props.put("locationLabel", "Agreus / Arcadia");
        props.put("clients", List.of(new MissionSelectScreen.ClientRow("client-rusk", "client-rusk-name", "client-rusk-rep",
                OpeningBroker.CREST, "client-card selected", AgreusArcCampaign.NAME, "North Weir / Ko Combine concession", false, none)));
        props.put("missionHeader", "House Rusk / North Weir");
        MissionSelectScreen.putAgreus(props, new AgreusArcProgress(), "");
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(props, mission, none, none);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("north-weir-contract-board.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }
    private FittedBoat boat(String id) {
        return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.standard(BoatFittingSlot.PLATING), BoatFitting.standard(BoatFittingSlot.DRIVE), id);
    }
    private Mission mission() { return AgreusArcCampaign.offer(new AgreusArcProgress(), "Agreus"); }
    private SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim, String caption, boolean detail) throws Exception {
        var sequence = sim.getWaterUnderSeal();
        var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                detail ? sequence.targetX() : 88, detail ? sequence.targetY() : 56, detail ? 110 : 176)
                .withImpactEffects().withUnitShadows();
        var annotations = ReviewAnnotations.builder();
        return new SnapshotArtifact(name, renderer.render(sim, caption, annotations.build()));
    }
}
