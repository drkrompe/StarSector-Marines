package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.story.ColdStartProgress;
import com.dillon.starsectormarines.battle.story.ColdStartPicket;
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
                           boolean retreat, boolean delivered, boolean powered, boolean boatless) {
        boolean support() { return id.equals("picket-support"); }
    }

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
        if (selected("interface")) addInterfaceFrames(context, frames);
        var output = context.projectRoot().resolve("build/reports/cold-start");
        Files.createDirectories(output);
        var report = new StringBuilder("Cold Start production factory with detached named two-squad company.\n"
                + "1920x1080 / 100% UI scale. Serial unit updates. No Hound, officer bonuses or casualty carryover.\n"
                + "Ordinary fixtures request one yard-construction command at the suggested site; infantry remain autonomous.\n"
                + "Outcomes and turret fire are observations. This does not certify live campaign persistence or balance.\n");
        report.append("Fixture selector=").append(System.getProperty("battle.coldStart.fixture", "all"))
                .append("; omitted controls remain unverified in this run.\n")
                .append("picket-support is a diagnostic normal player Defend Area order, followed by a Move order to the inner hold.\n");
        if (selected("airborne-control")) airborneControl(context, frames, report);
        var fixtures = List.of(
                new Fixture("paired-aero-14101", 14101, false, false, true, false, false, false, false),
                new Fixture("mixed-lift-14102", 14102, true, false, true, false, false, false, false),
                new Fixture("absent-company", 14101, false, true, false, false, false, false, false),
                new Fixture("no-build", 14101, false, false, false, false, false, false, false),
                new Fixture("withdraw-after-power", 14101, false, false, false, true, false, false, false),
                new Fixture("no-company-boats", 14101, false, false, true, false, false, false, true),
                new Fixture("picket-support", 14101, false, false, true, false, false, false, false));
        ColdStartProgress.Report withdrawal = null;
        for (var fixture : fixtures) {
            if (!selected(fixture.id) && !(fixture.retreat && selected("retry-powered"))) continue;
            try {
                var observed = play(context, fixture, frames, report);
                if (fixture.retreat) withdrawal = observed;
            } finally {
                Files.writeString(output.resolve("playback.txt"), report);
            }
        }
        if (selected("retry-powered") && withdrawal != null && withdrawal.shorePower()) {
            var receipt = new ShepherdRecoveryProgress();
            String claim = receipt.missionId();
            receipt.begin(claim, "Asharu");
            receipt.settle(claim, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true);
            String attempt = receipt.missionId();
            assertTrue(receipt.begin(attempt, "Asharu"));
            assertEquals(0, receipt.settleColdStart(attempt, "Asharu", false, 0, withdrawal.crewLost(),
                    withdrawal.cargoDelivered(), withdrawal.shorePower(), withdrawal.turretConstructed(), withdrawal.repaired()));
            play(context, new Fixture("retry-powered", 14101, false, false, true, false,
                    receipt.cargoDelivered(), receipt.shorePower(), false), frames, report);
        } else if (selected("retry-powered")) report.append("\nPowered retry not observed: withdrawal fixture did not restore shore power within the bounded run.\n");
        Files.writeString(output.resolve("playback.txt"), report);
        return frames;
    }

    private boolean selected(String id) {
        String selector = System.getProperty("battle.coldStart.fixture", "all");
        for (String part : selector.split(","))
            if (part.trim().equals("all") || !part.isBlank() && id.contains(part.trim())) return true;
        return false;
    }

    /** Same campaign overlay on the preceding chapter still requires physical air arrival. */
    private void airborneControl(SnapshotContext context, List<SnapshotArtifact> frames, StringBuilder report) throws Exception {
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        assertEquals(MarineArrivalPolicy.PAIRED_HALF_SQUAD, mission.marineArrivalPolicy);
        var roster = DebugMissionCatalog.starterCompany(2);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 24);
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission,
                List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE)), 24);
        try (var sim = BattleSetup.createSalvageRights(14101, manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD))) {
            assertFalse(sim.companyStartsOnGround());
            deployment.applyToAuthoredPlayerLift(sim);
            Set<String> aboard = new HashSet<>();
            for (long air : sim.getAirEntityIds()) {
                if (sim.world().airFaction(air) != Faction.MARINE) continue;
                var flight = sim.world().mission(air);
                assertEquals(ShuttleState.PENDING, flight.state);
                for (var cycle : flight.cycleLoadouts) for (var seat : cycle) assertTrue(aboard.add(seat.campaignSoldierId));
            }
            assertEquals(24, aboard.size());
            for (int i = 0; i < sim.liveUnitCount(); i++) assertNotEquals(Faction.MARINE, sim.identity().faction(sim.liveUnitAt(i)));
            Set<String> landed = new HashSet<>();
            while (sim.getSimTickIndex() < 6000 && landed.size() < 24 && !sim.isComplete()) {
                sim.advance(BattleSimulation.TICK_DT);
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.identity().faction(unit) != Faction.MARINE) continue;
                    String soldier = (String) sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY,
                            BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(aboard.contains(soldier));
                    landed.add(soldier);
                }
            }
            assertEquals(24, landed.size(), "Preceding chapter retains physical repeated shuttle insertion");
            frames.add(new SnapshotArtifact("salvage-rights-airborne-control.png",
                    new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 45, 88, 95).render(sim,
                            "UNCHANGED SALVAGE RIGHTS / PHYSICAL SHUTTLE ARRIVALS / TICK " + sim.getSimTickIndex())));
            report.append("Airborne control: Salvage Rights; zero company ashore at launch, 24 named arrivals by tick ")
                    .append(sim.getSimTickIndex()).append(".\n");
        }
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
        List<FittedBoat> boats = fixture.boatless ? List.of() : List.of(FittedBoat.standard(fixture.mixed ? ShuttleType.HERMES : ShuttleType.AEROSHUTTLE),
                FittedBoat.standard(ShuttleType.AEROSHUTTLE));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        try (var sim = BattleSetup.createColdStart(fixture.seed, manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD),
                fixture.delivered, fixture.powered)) {
            if (!fixture.absent) deployment.applyToAuthoredPlayerLift(sim);
            var sequence = sim.getColdStart();
            long initialCarrier = sequence.repairCarrierId();
            float carrierStartX = initialCarrier == 0 ? Float.NaN : sim.convoy().body(initialCarrier).x;
            float carrierStartY = initialCarrier == 0 ? Float.NaN : sim.convoy().body(initialCarrier).y;
            var committed = new HashSet<String>();
            var soldierSquads = new HashMap<String, String>();
            for (var seat : deployment.commitments()) soldierSquads.put(seat.campaignSoldierId(), seat.campaignSquadId());
            for (var seat : deployment.commitments()) assertTrue(committed.add(seat.campaignSoldierId()));
            int aircraft = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                var flight = sim.world().mission(air);
                assertEquals(0, flight.marinesRemaining, "Company has no inbound passengers");
                assertEquals(0, flight.marineLoadout.length);
                assertEquals(ShuttleState.GONE, flight.state, "Fitted boat remains in reserve");
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
            var occupiedStarts = new HashSet<String>();
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE) continue;
                String soldier = (String) sim.getEntityWorld().getObject(unit, sim.getBattleComponents().IDENTITY,
                        BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                assertNotNull(soldier, "Every company member begins with named campaign identity");
                assertTrue(occupiedStarts.add(sim.world().cellX(unit) + "," + sim.world().cellY(unit)), "Distinct occupied start positions");
                assertTrue(sim.playerSeesCell(sim.world().cellX(unit), sim.world().cellY(unit)), "Company contributes fog at tick zero");
                assertTrue(committed.contains(soldier));
                assertTrue(landed.add(soldier), "Each named marine is deployed once at tick zero");
                reunited.computeIfAbsent(soldierSquads.get(soldier), ignored -> new HashSet<>()).add(sim.squad().squadId(unit));
                var seat = deployment.commitments().stream().filter(value -> value.campaignSoldierId().equals(soldier)).findFirst().orElseThrow();
                var squad = sim.getSquad(sim.squad().squadId(unit));
                assertEquals(seat.campaignSquadStrength(), sim.squadMemberCount(squad.id));
                assertEquals(seat.campaignFireTeamIndex(), sim.squad().fireTeamIndex(unit));
                if (seat.campaignSquadLeader()) assertEquals(unit, squad.leaderId, "Campaign NCO leads the ground formation");
                assertTrue(sim.getGrid().isWalkable((int) sim.world().x(unit), (int) sim.world().y(unit)));
            }
            assertEquals(fixture.absent ? 0 : 24, landed.size(), "Selected company starts entirely ashore before time advances");
            frames.add(frame(fixture.id + "-company-ground-start.png", sim, landingRenderer, "SELECTED NAMED COMPANY / TICK ZERO / ALREADY ASHORE"));
            var shots = new HashMap<Long, Integer>();
            var picketReadings = new HashMap<String, ColdStartPicket.Report>();
            var picketFrameKeys = new HashSet<String>();
            var picketShots = new HashMap<String, Integer>();
            var picketUnits = new HashMap<Long, String>();
            for (var picket : sequence.pickets()) {
                assertTrue(picket.initialMembers() >= 2 && picket.initialMembers() <= 4,
                        "Each allied forward picket is a small physical detachment");
                report.append("  initial picket=").append(picket).append('\n');
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long unit = sim.liveUnitAt(i);
                    if (sim.squad().hasSquad(unit) && sim.squad().squadId(unit) == picket.squadId()) picketUnits.put(unit, picket.key());
                }
            }
            frames.add(frame(fixture.id + "-outer-picket-line.png", sim, renderer, "ACTUAL ALLIED OUTER PICKETS / TICK ZERO"));
            int supportSquad = -1;
            boolean supportReleased = false;
            int supportNearOuterTicks = 0;
            int supportShots = 0;
            int innerPressureTicks = 0;
            int turretPressureShots = 0;
            boolean requested = false;
            boolean firingFrame = false;
            boolean buildingFrame = false;
            boolean landingFrame = true;
            boolean escortFrame = false;
            int maxTicks = Integer.getInteger("battle.coldStart.maxTicks", fixture.absent ? 3600 : 18000);
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (fixture.support() && supportSquad < 0 && sequence.report(sim).cargoDelivered()) {
                    var picket = sequence.pickets().get(1);
                    for (var squad : sim.getSquads()) if (squad.faction == Faction.MARINE && sim.squadMemberCount(squad.id) > 0) {
                        supportSquad = squad.id;
                        sim.getSquadMoveOrderService().requestDefendArea(squad.id, picket.outerX(), picket.outerY());
                        report.append("  DIAGNOSTIC player support Defend Area tick=").append(sim.getSimTickIndex())
                                .append(" squad=").append(squad.id).append(" picket=").append(picket.key())
                                .append(" target=").append(picket.outerX()).append(',').append(picket.outerY()).append('\n');
                        break;
                    }
                }
                if (fixture.support() && supportSquad >= 0 && !supportReleased) {
                    var picket = sequence.pickets().get(1);
                    if (picket.withdrawing() || picket.aliveMembers() == 0 || phase.name().equals("REPAIR")) {
                        sim.getSquadMoveOrderService().requestMove(supportSquad, picket.innerX(), picket.innerY());
                        supportReleased = true;
                        report.append("  DIAGNOSTIC player support replaced with Move to inner hold tick=")
                                .append(sim.getSimTickIndex()).append(" squad=").append(supportSquad).append('\n');
                    }
                }
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
                boolean innerPressure = hostileNearInner(sim);
                if (innerPressure) innerPressureTicks++;
                if (supportSquad >= 0) {
                    var picket = sequence.pickets().get(1);
                    boolean nearby = false;
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long unit = sim.liveUnitAt(i);
                        if (!sim.squad().hasSquad(unit) || sim.squad().squadId(unit) != supportSquad) continue;
                        float dx = sim.world().x(unit) - picket.outerX(), dy = sim.world().y(unit) - picket.outerY();
                        if (dx * dx + dy * dy <= 100) nearby = true;
                    }
                    if (nearby) {
                        supportNearOuterTicks++;
                        if (picketFrameKeys.add("support-arrived")) {
                            report.append("  actual company support within 10 cells of outer post tick=")
                                    .append(sim.getSimTickIndex()).append('\n');
                            frames.add(frame(fixture.id + "-support-arrived.png", sim,
                                    new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                                            picket.outerX(), picket.outerY(), 80).withImpactEffects(),
                                    "DIAGNOSTIC PLAYER ORDER / ACTUAL COMPANY SUPPORT AT OUTER POST"));
                        }
                    }
                }
                for (var shot : sim.getShotsThisFrame()) {
                    if (sequence.turretEntityIds().contains(shot.shooterId)) {
                        shots.merge(shot.shooterId, 1, Integer::sum);
                        if (innerPressure) turretPressureShots++;
                    }
                    String post = picketUnits.get(shot.shooterId);
                    if (post != null) picketShots.merge(post, 1, Integer::sum);
                    if (supportSquad >= 0 && shot.shooterFaction == Faction.MARINE
                            && sim.squad().hasSquad(shot.shooterId)
                            && sim.squad().squadId(shot.shooterId) == supportSquad) supportShots++;
                }
                for (var picket : sequence.pickets()) {
                    var before = picketReadings.put(picket.key(), picket);
                    if (!picket.equals(before)) report.append("  picket observation tick=").append(sim.getSimTickIndex())
                            .append(' ').append(picket).append(" lost=").append(picket.lostMembers())
                            .append(" shots=").append(picketShots.getOrDefault(picket.key(), 0)).append('\n');
                    String transition = picket.key() + "-" + picket.state().name().toLowerCase();
                    if (before != null && picket.state() != before.state() && picketFrameKeys.add(transition))
                        frames.add(frame(fixture.id + "-" + transition + ".png", sim,
                                new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                                        (picket.outerX() + picket.innerX()) / 2f,
                                        (picket.outerY() + picket.innerY()) / 2f, 140).withImpactEffects(),
                                "ACTUAL " + picket.key() + " / " + picket.state() + " / ALIVE " + picket.aliveMembers()
                                        + " LOST " + picket.lostMembers() + " INNER " + picket.innerMembers()));
                    if (picket.contacted() && picketFrameKeys.add(picket.key() + "-contact"))
                        frames.add(frame(fixture.id + "-" + picket.key() + "-contact.png", sim,
                                new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                                        picket.outerX(), picket.outerY(), 80).withImpactEffects(),
                                "ACTUAL PICKET CONTACT / " + picket.key() + " / TICK " + sim.getSimTickIndex()));
                    if (picket.overrun() && picketFrameKeys.add(picket.key() + "-overrun"))
                        frames.add(frame(fixture.id + "-" + picket.key() + "-overrun.png", sim,
                                new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                                        picket.outerX(), picket.outerY(), 80).withImpactEffects(),
                                "ACTUAL POST BREACH OR TOTAL LOSS / " + picket.key() + " / ALIVE " + picket.aliveMembers()));
                }
                if (innerPressure && turretPressureShots > 0 && picketFrameKeys.add("turret-inner-pressure"))
                    frames.add(frame(fixture.id + "-turret-inner-pressure.png", sim, detailRenderer,
                            "ACTUAL TURRET FIRE / HOSTILES WITHIN 20 CELLS OF INNER HOLD / TICK " + sim.getSimTickIndex()));
                if (!landingFrame && !landed.isEmpty()) {
                    landingFrame = true;
                    frames.add(frame(fixture.id + "-first-landing.png", sim, landingRenderer,
                            "ACTUAL COMPANY GROUND START / " + boats + " / TICK " + sim.getSimTickIndex()));
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
            } else if (!fixture.delivered) assertEquals(24, landed.size(), "Every committed marine begins on secured ground");
            for (var ids : reunited.values()) assertEquals(1, ids.size(), "Each campaign squad remains one formation");
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
                    .append(" deniedAtBoundary=").append(sequence.deniedRoadMembers())
                    .append(" finalResponseReached=").append(sequence.finalResponseReached())
                    .append(" turretIds=").append(sequence.turretEntityIds()).append(" turretShots=").append(shots).append('\n');
            report.append("  matched-fixture comparison seed=").append(fixture.seed).append(" fixture=").append(fixture.id)
                    .append(" buildRequested=").append(requested).append(" playerSupport=").append(fixture.support())
                    .append(" companyAlive=").append(alive).append(" innerPressureTicks=").append(innerPressureTicks)
                    .append(" turretShotsUnderInnerPressure=").append(turretPressureShots)
                    .append(" supportNearOuterTicks=").append(supportNearOuterTicks).append(" supportSquadShots=").append(supportShots)
                    .append(" picketShots=").append(picketShots).append(" pickets=").append(sequence.pickets()).append('\n');
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

    /** Observer-only proximity is pressure evidence, not a commander belief or a claim of damage. */
    private boolean hostileNearInner(BattleSimulation sim) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (!Faction.MARINE.hostileTo(sim.identity().faction(unit))) continue;
            for (var picket : sim.getColdStart().pickets()) {
                float dx = sim.world().x(unit) - picket.innerX(), dy = sim.world().y(unit) - picket.innerY();
                if (dx * dx + dy * dy <= 400) return true;
            }
        }
        return false;
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
                .marinesEngaged(24).marinesLost(3).alliesEngaged(16).alliesLost(1)
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
                sequence.displayName(), ReviewStyle.OBJECTIVE);
        for (var picket : sequence.pickets()) {
            marks.label(picket.outerX(), picket.outerY(), picket.key() + " " + picket.state()
                    + " alive=" + picket.aliveMembers() + " lost=" + picket.lostMembers(), ReviewStyle.OBJECTIVE);
            marks.label(picket.innerX(), picket.innerY(), "INNER HOLD / " + picket.key()
                    + " present=" + picket.innerMembers(), ReviewStyle.OBJECTIVE);
        }
        return new SnapshotArtifact(name, renderer.render(sim, caption, marks.build()));
    }
}
