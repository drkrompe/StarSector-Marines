package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.story.ColdStartProgress;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in bounded production playback. A timeout is an observation, never a victory. */
public final class ColdStartSnapshotSuite implements SnapshotSuite {
    private record Fixture(String id, long seed, boolean mixed, boolean absent, boolean build,
                           boolean retreat, boolean delivered, boolean powered) { }

    public String id() { return "cold-start"; }
    public String label() { return "Cold Start: named company, physical repair carrier and grounded builders"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String property = "battle.unitUpdate.minimumParallelUnits";
        String previous = System.getProperty(property);
        System.setProperty(property, Integer.toString(Integer.MAX_VALUE));
        try {
            return renderSerial(context);
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    private List<SnapshotArtifact> renderSerial(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>();
        addInterfaceFrames(context, frames);
        var output = context.projectRoot().resolve("build/reports/cold-start");
        Files.createDirectories(output);
        var report = new StringBuilder("Cold Start production factory with detached named two-squad company.\n"
                + "1920x1080 / 100% UI scale. Serial unit updates. No Hound, officer bonuses or casualty carryover.\n"
                + "Ordinary fixtures request one yard-construction command at the suggested site; infantry remain autonomous.\n"
                + "Outcomes and turret fire are observations. This does not certify live campaign persistence or balance.\n");
        var fixtures = List.of(
                new Fixture("paired-aero-14101", 14101, false, false, true, false, false, false),
                new Fixture("mixed-lift-14102", 14102, true, false, true, false, false, false),
                new Fixture("absent-company", 14101, false, true, false, false, false, false),
                new Fixture("no-build", 14101, false, false, false, false, false, false),
                new Fixture("withdraw-after-power", 14101, false, false, false, true, false, false));
        ColdStartProgress.Report withdrawal = null;
        for (var fixture : fixtures) {
            try {
                var observed = play(context, fixture, frames, report);
                if (fixture.retreat) withdrawal = observed;
            } finally {
                Files.writeString(output.resolve("playback.txt"), report);
            }
        }
        if (withdrawal != null && withdrawal.shorePower()) {
            var receipt = new ShepherdRecoveryProgress();
            String claim = receipt.missionId();
            receipt.begin(claim, "Asharu");
            receipt.settle(claim, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true);
            String attempt = receipt.missionId();
            assertTrue(receipt.begin(attempt, "Asharu"));
            assertEquals(0, receipt.settleColdStart(attempt, "Asharu", false, 0, withdrawal.crewLost(),
                    withdrawal.cargoDelivered(), withdrawal.shorePower(), withdrawal.turretConstructed(), withdrawal.repaired()));
            play(context, new Fixture("retry-powered", 14101, false, false, true, false,
                    receipt.cargoDelivered(), receipt.shorePower()), frames, report);
        } else report.append("\nPowered retry not observed: withdrawal fixture did not restore shore power within the bounded run.\n");
        Files.writeString(output.resolve("playback.txt"), report);
        return frames;
    }

    private ColdStartProgress.Report play(SnapshotContext context, Fixture fixture, List<SnapshotArtifact> frames,
                      StringBuilder report) throws Exception {
        var progress = new ShepherdRecoveryProgress();
        String claim = progress.missionId();
        assertTrue(progress.begin(claim, "Asharu"));
        assertEquals(ShepherdRecoveryProgress.PAYMENT,
                progress.settle(claim, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true));
        var mission = ShepherdRecoveryCampaign.offer(progress, "Asharu");
        var roster = DebugMissionCatalog.starterCompany(2);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 24);
        var boats = List.of(FittedBoat.standard(fixture.mixed ? ShuttleType.HERMES : ShuttleType.AEROSHUTTLE),
                FittedBoat.standard(ShuttleType.AEROSHUTTLE));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        try (var sim = BattleSetup.createColdStart(fixture.seed, manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD),
                fixture.delivered, fixture.powered)) {
            deployment.applyToAuthoredPlayerLift(sim);
            var sequence = sim.getColdStart();
            long initialCarrier = sequence.repairCarrierId();
            float carrierStartX = initialCarrier == 0 ? Float.NaN : sim.convoy().body(initialCarrier).x;
            float carrierStartY = initialCarrier == 0 ? Float.NaN : sim.convoy().body(initialCarrier).y;
            var committed = new HashSet<String>();
            var soldierSquads = new HashMap<String, String>();
            for (var seat : deployment.commitments()) soldierSquads.put(seat.campaignSoldierId(), seat.campaignSquadId());
            int aircraft = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                var flight = sim.world().mission(air);
                for (var cycle : flight.cycleLoadouts) for (var person : cycle) {
                    assertNotNull(person.campaignSoldierId);
                    assertTrue(committed.add(person.campaignSoldierId));
                }
                if (fixture.absent) flight.pendingDelay += 9999;
            }
            assertEquals(24, committed.size());
            assertEquals(boats.size(), aircraft);
            report.append('\n').append(fixture.id).append(" seed=").append(fixture.seed)
                    .append(" initialDelivered=").append(fixture.delivered).append(" initialPowered=").append(fixture.powered)
                    .append(" boats=").append(boats).append(" committed=").append(committed.stream().sorted().toList()).append('\n');
            var renderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 159, 112, 220).withImpactEffects();
            var detailRenderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 153, 115, 100).withImpactEffects();
            var approachRenderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 95, 95, 190).withImpactEffects();
            var landingRenderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 45, 88, 95).withImpactEffects();
            var escortRenderer = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 60, 96, 115).withImpactEffects();
            var phase = sequence.progress().phase();
            frames.add(frame(fixture.id + "-approach.png", sim, approachRenderer, "COLD START / " + phase));
            var landed = new HashSet<String>();
            var reunited = new HashMap<String, Set<Integer>>();
            var shots = new HashMap<Long, Integer>();
            boolean requested = false;
            boolean firingFrame = false;
            boolean buildingFrame = false;
            boolean landingFrame = false;
            boolean escortFrame = false;
            int maxTicks = fixture.absent ? 3600 : Integer.getInteger("battle.coldStart.maxTicks", 18000);
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (fixture.build && !requested && phase.name().equals("CONSTRUCTION")
                        && sim.getCommandPowerService().canActivate(sim.getCommandPowerService().getPower("yard-construction"))) {
                    sim.getCommandPowerService().requestActivation("yard-construction", sequence.buildX(), sequence.buildY());
                    requested = true;
                    report.append("  explicit player construction request tick=").append(sim.getSimTickIndex()).append('\n');
                }
                try {
                    sim.advance(BattleSimulation.TICK_DT);
                } catch (RuntimeException failure) {
                    report.append("  playback exception tick=").append(sim.getSimTickIndex()).append(" phase=")
                            .append(sequence.progress().phase()).append(" target=").append(sequence.targetX()).append(',')
                            .append(sequence.targetY()).append(" report=").append(sequence.report(sim)).append('\n');
                    if (sequence.tender() != null) {
                        report.append("  grounded legal candidates:");
                        for (int y = 100; y < 131; y++) for (int x = 148; x < 164; x++)
                            if (sequence.tender().groundSiteReachable(x + .5f, y + .5f, sim.getGrid()))
                                report.append(' ').append(x).append(',').append(y);
                        report.append('\n').append("  site blocked cells:");
                        for (int y = 107; y <= 113; y++) for (int x = 151; x <= 157; x++)
                            if (!sim.getGrid().isWalkable(x, y)) report.append(' ').append(x).append(',').append(y);
                        report.append('\n');
                    }
                    for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                        report.append("    ").append(squad.faction).append(" squad=").append(squad.id).append(" count=")
                                .append(sim.squadMemberCount(squad.id)).append(" centroid=").append(squad.centroidX)
                                .append(',').append(squad.centroidY).append(" order=").append(sim.getSquadCommandDirective(squad.id)).append('\n');
                    throw failure;
                }
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
                    String soldier = (String) sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY,
                            BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertNotNull(soldier, "No anonymous company replacements");
                    assertTrue(committed.contains(soldier), "No anonymous company replacement");
                    landed.add(soldier);
                    reunited.computeIfAbsent(soldierSquads.get(soldier), ignored -> new HashSet<>()).add(sim.squad().squadId(unit));
                }
                for (var shot : sim.getShotsThisFrame()) if (sequence.turretEntityIds().contains(shot.shooterId))
                    shots.merge(shot.shooterId, 1, Integer::sum);
                if (!landingFrame && !landed.isEmpty()) {
                    landingFrame = true;
                    frames.add(frame(fixture.id + "-first-landing.png", sim, landingRenderer,
                            "ACTUAL COMPANY LIFT / " + boats + " / TICK " + sim.getSimTickIndex()));
                }
                if (!escortFrame && initialCarrier != 0 && sim.convoy().body(initialCarrier).x >= 40
                        && sim.convoy().body(initialCarrier).x <= 55) {
                    escortFrame = true;
                    frames.add(frame(fixture.id + "-carrier-escort.png", sim, escortRenderer,
                            "ACTUAL DISTRIBUTION RIG / TICK " + sim.getSimTickIndex()));
                }
                if (!firingFrame && !shots.isEmpty()) {
                    firingFrame = true;
                    frames.add(frame(fixture.id + "-first-turret-fire.png", sim, detailRenderer,
                            "ACTUAL CONSTRUCTED TURRET FIRE / TICK " + sim.getSimTickIndex()));
                    report.append("  first constructed-turret shot tick=").append(sim.getSimTickIndex()).append('\n');
                }
                var next = sequence.progress().phase();
                if (!buildingFrame && sequence.tender() != null && sequence.tender().orders().stream()
                        .anyMatch(order -> order.fraction() > .1f && order.fraction() < .9f)) {
                    buildingFrame = true;
                    frames.add(frame(fixture.id + "-builders-working.png", sim, detailRenderer,
                            "ACTUAL GROUNDED BUILDER WORK / TICK " + sim.getSimTickIndex()));
                }
                if (next != phase) {
                    report.append("  tick=").append(sim.getSimTickIndex()).append(" phase=").append(next).append('\n');
                    report.append("  unreleased finite attackers=").append(sequence.pendingMembers()).append('\n');
                    frames.add(frame(fixture.id + "-" + next.name().toLowerCase() + ".png", sim, renderer,
                            "COLD START / " + next + " / " + sim.getSimTickIndex() / 30 + " SECONDS"));
                    phase = next;
                    if (next.name().equals("POWER") || next.name().equals("CONSTRUCTION"))
                        frames.add(frame(fixture.id + "-" + next.name().toLowerCase() + "-detail.png", sim, detailRenderer,
                                "ACTUAL SHORE CIRCUIT / " + next + " / TICK " + sim.getSimTickIndex()));
                }
                if (fixture.retreat && sequence.report(sim).shorePower()) {
                    assertTrue(sim.retreat());
                    report.append("  confirmed withdrawal tick=").append(sim.getSimTickIndex()).append('\n');
                }
                if (sim.getSimTickIndex() % 1800 == 0)
                    System.out.println("[cold-start] " + fixture.id + " tick=" + sim.getSimTickIndex() + " phase=" + phase + " landed=" + landed.size());
            }
            var result = sequence.report(sim);
            if (fixture.absent) {
                assertEquals(0, landed.size());
                assertFalse(result.cargoDelivered());
                assertFalse(result.shorePower());
                assertFalse(result.repaired());
                assertEquals(carrierStartX, sim.convoy().body(initialCarrier).x, .01f, "Carrier physically waits for absent escort");
                assertEquals(carrierStartY, sim.convoy().body(initialCarrier).y, .01f, "Carrier physically waits for absent escort");
            } else if (!fixture.delivered) assertEquals(24, landed.size(), "Every committed marine lands through real boat cycles");
            for (var ids : reunited.values()) assertEquals(1, ids.size(), "Each campaign squad reunites across physical trips");
            if (!fixture.build) assertFalse(result.turretConstructed());
            if (fixture.retreat && result.shorePower()) assertFalse(result.repaired());
            boolean powered = sim.getTopology().nightLighting().poweredCircuits().contains("yard");
            assertEquals(result.shorePower(), powered, "Actual yard light circuit follows shore-power milestone");
            long carrier = sequence.repairCarrierId();
            boolean cargoAlive = carrier != 0 && sim.convoy().isVehicle(carrier)
                    && sim.convoy().mission(carrier).state != VehicleState.WRECKED;
            int alive = 0;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) == Faction.MARINE && soldierSquads.containsKey((String)
                        sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY,
                                BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID))) alive++;
            }
            report.append("  final=").append(phase).append(" ticks=").append(sim.getSimTickIndex())
                    .append(" complete=").append(sim.isComplete()).append(" landed=").append(landed.size())
                    .append(" companyAlive=").append(alive).append(" companyGroundLost=").append(landed.size() - alive)
                    .append(" companyNotLanded=").append(24 - landed.size())
                    .append(" cargoId=").append(carrier).append(" cargoAlive=").append(cargoAlive)
                    .append(" cargoStart=").append(carrierStartX).append(',').append(carrierStartY)
                    .append(" cargoEnd=").append(carrier == 0 ? "not fielded: already delivered" :
                            sim.convoy().body(carrier).x + "," + sim.convoy().body(carrier).y)
                    .append(" poweredCircuit=").append(powered).append(" report=").append(result)
                    .append(" unreleasedAttackers=").append(sequence.pendingMembers())
                    .append(" finalResponseReached=").append(sequence.finalResponseReached())
                    .append(" turretIds=").append(sequence.turretEntityIds()).append(" turretShots=").append(shots).append('\n');
            if (sequence.tender() != null) report.append("  tender built=").append(sequence.tender().built())
                    .append(" buildersLost=").append(sequence.tender().buildersLost()).append('\n');
            if (sequence.tender() != null) for (var order : sequence.tender().orders())
                report.append("  construction phase=").append(order.phase()).append(" fraction=").append(order.fraction())
                        .append(" contested=").append(order.contested()).append(" underFire=").append(order.underFire())
                        .append(" failure=").append(order.failure()).append('\n');
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append("  ").append(squad.faction).append(" squad=").append(squad.id).append(" count=")
                        .append(sim.squadMemberCount(squad.id)).append(" centroid=").append(squad.centroidX).append(',')
                        .append(squad.centroidY).append(" order=").append(sim.getSquadCommandDirective(squad.id)).append('\n');
            report.append("  reunitedSquads=").append(reunited).append('\n');
            for (var beat : sim.getStoryBeats()) report.append("  ").append(beat).append('\n');
            frames.add(frame(fixture.id + "-final.png", sim, renderer, "FINAL OBSERVATION / " + phase));
            return result;
        }
    }

    private void addInterfaceFrames(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        var progress = new ShepherdRecoveryProgress();
        String claim = progress.missionId();
        progress.begin(claim, "Asharu");
        progress.settle(claim, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true);
        var mission = ShepherdRecoveryCampaign.offer(progress, "Asharu");
        var board = MissionSelectScreen.previewProps();
        Runnable none = () -> { };
        board.put("locationLabel", "Asharu / Corvus");
        board.put("clients", List.of(new MissionSelectScreen.ClientRow("client-vale", "client-vale-name", "client-vale-rep",
                OpeningBroker.CREST, "client-card selected", "Tamsin Vale", "Basin Reclamation Cooperative", false, none)));
        board.put("missionHeader", "Basin Reclamation Cooperative");
        board.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        board.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(board, mission, none, none);
        MissionSelectScreen.putShepherd(board, progress);
        frames.add(document(context, renderer, "contract-board.png", MissionSelectScreen.COMPONENT_PATHS,
                MissionSelectScreen.ROOT_COMPONENT, board));
        var outcome = MissionOutcome.builder().mission(mission).victory(true).payoutEarned(ShepherdRecoveryProgress.COLD_PAYMENT)
                .marinesEngaged(24).marinesLost(3).alliesEngaged(10).alliesLost(1)
                .coldStart(new ColdStartProgress.Report(true, true, true, true, 1)).build();
        var debrief = ResultsScreen.previewProps(true, false);
        debrief.put("missionName", mission.name);
        debrief.put("resultRows", ResultsScreen.resultRows(outcome, LootManifest.EMPTY, ModStrings.fromDisk(context.modRoot())));
        debrief.put("squadRows", List.of());
        ResultsScreen.putStoryDebrief(debrief, outcome);
        frames.add(document(context, renderer, "repair-debrief.png", ResultsScreen.COMPONENT_PATHS,
                ResultsScreen.ROOT_COMPONENT, debrief));
    }

    private SnapshotArtifact document(SnapshotContext context, HeadlessUiRenderer renderer, String name,
                                      List<String> paths, String root, Map<String, Object> props) throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), paths);
        loader.reload();
        try (var instance = loader.build(new Reactor(), root, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            return new SnapshotArtifact(name, renderer.renderRelative(document, 1920, 1080, 1f,
                    MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT));
        }
    }

    private SnapshotArtifact frame(String name, BattleSimulation sim, BattleReviewFrameRenderer renderer, String caption) throws Exception {
        var sequence = sim.getColdStart();
        var marks = ReviewAnnotations.builder().label(sequence.targetX(), sequence.targetY(),
                sequence.displayName(), ReviewStyle.OBJECTIVE).build();
        return new SnapshotArtifact(name, renderer.render(sim, caption, marks));
    }
}
