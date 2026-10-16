package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.BoatFittingSlot;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.BlackSluiceDuel;
import com.dillon.starsectormarines.battle.setup.SmallWarForces;
import com.dillon.starsectormarines.battle.setup.SmallWarSequence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.SmallWarProgress;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
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

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in Black Sluice playback: a named regular company with fitted boats and an owned tender
 * through the production factory and command. The suite plays the player's part only where a
 * player must act (calling the tender and placing the rear position); diagnostic losses and the
 * withdrawal are labelled controls. Terminal receipts feed the campaign ledger without a live
 * sector. The production duel overhead is installed in every fixture but one: {@code ordinary-15305-no-duel}
 * is the same battle with an empty sky, and both must end the same way on the ground. The duel is
 * recorded as {@code ordinary-15305-duel.gif} from Voss's order on.
 */
public final class BlackSluiceSnapshotSuite implements SnapshotSuite {
    private enum Control { ORDINARY, ABSENT, FUEL_LOSS, CUSTODY_LOSS, RETREAT }
    /** Diagnostic: report and keep minute frames without asserting outcomes. */
    private static final boolean OBSERVE = Boolean.getBoolean("battle.blackSluice.observe");
    private record Fixture(String id, long seed, Control control, AgreusYardAgreement route, boolean duel) {
        Fixture(String id, long seed, Control control, AgreusYardAgreement route) { this(id, seed, control, route, true); }
    }
    /** Duel frames: every this many ticks from the order, for at most this many ticks. */
    private static final int DUEL_FRAME_EVERY = 30, DUEL_FRAME_TICKS = 6600, DUEL_FRAME_TAIL = 300;
    private final Map<String, Boolean> victories = new HashMap<>();
    public String id() { return "black-sluice"; }
    public String label() { return "A Small War: Black Sluice handover with named company, owned tender and Greyhook's split"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot()); HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot()); InstalledHullSpecs.install(); InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>();
        addMap(context, frames);
        addBoard(context, frames);
        var report = new StringBuilder("Detached production mission / 36 named marines / two fitted six-seat boats / member-backed Morrow.\n"
                + "The production duel overhead is installed except in the no-duel control: ground victory never depends on it. No live travel, host save/reload or economy acceptance is claimed.\n");
        var directory = context.projectRoot().resolve("build/reports/black-sluice"); Files.createDirectories(directory);
        String filter = System.getProperty("battle.blackSluice.fixture", "");
        var fixtures = List.of(
                new Fixture("ordinary-15305", 15305, Control.ORDINARY, AgreusYardAgreement.HOUSE_CUSTODY),
                new Fixture("ordinary-15305-no-duel", 15305, Control.ORDINARY, AgreusYardAgreement.HOUSE_CUSTODY, false),
                new Fixture("ordinary-16205", 16205, Control.ORDINARY, AgreusYardAgreement.HOUSE_CUSTODY),
                new Fixture("ordinary-16206-compact", 16206, Control.ORDINARY, AgreusYardAgreement.YARD_COMPACT),
                new Fixture("absent", 15305, Control.ABSENT, AgreusYardAgreement.HOUSE_CUSTODY),
                new Fixture("fuel-loss", 15305, Control.FUEL_LOSS, AgreusYardAgreement.HOUSE_CUSTODY),
                new Fixture("custody-loss", 15305, Control.CUSTODY_LOSS, AgreusYardAgreement.YARD_COMPACT),
                new Fixture("withdrawal", 15305, Control.RETREAT, AgreusYardAgreement.HOUSE_CUSTODY));
        try {
            for (var fixture : fixtures) {
                if (filter.equals("map")) break;
                if (!filter.isBlank() && !fixture.id().contains(filter)) continue;
                var campaign = ratified(fixture.route());
                boolean won = play(context, fixture, campaign, frames, report);
                if (fixture.control() != Control.ORDINARY && fixture.control() != Control.ABSENT) {
                    assertFalse(won);
                    assertTrue(play(context, new Fixture("replacement-" + fixture.id(), fixture.seed(), Control.ORDINARY, fixture.route()),
                            campaign, frames, report), "A replacement mobilization must be playable after the failed attempt");
                }
            }
        } finally { Files.writeString(directory.resolve("playback.txt"), report); }
        if (victories.containsKey("ordinary-15305") && victories.containsKey("ordinary-15305-no-duel"))
            assertEquals(victories.get("ordinary-15305-no-duel"), victories.get("ordinary-15305"),
                    "The same battle with and without the duel overhead must end the same way on the ground");
        return frames;
    }

    private boolean play(SnapshotContext context, Fixture fixture, AgreusArcProgress campaign,
                         List<SnapshotArtifact> frames, StringBuilder report) throws Exception {
        var mission = AgreusArcCampaign.offer(campaign, "Agreus");
        assertEquals("A Small War", mission.name);
        assertTrue(campaign.begin(mission.id, "agreus", "Agreus"));
        var roster = DebugMissionCatalog.expectedCompany(DebugMissionCatalog.Scenario.WORKING_PASSAGE.mission(), 3);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, 36);
        var boats = List.of(boat("sluice-boat-one"), boat("sluice-boat-two"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
        var source = new ShipSupportSource("shepherd", "Morrow", true, Map.of(), "sluice-morrow-member");
        boolean custody = fixture.route() == AgreusYardAgreement.HOUSE_CUSTODY;
        report.append("\n").append(fixture).append(" mission=").append(mission.id).append('\n');
        try (var sim = BattleSetup.createSmallWar(fixture.seed(), manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD), custody, fixture.duel())) {
            deployment.applyToAuthoredPlayerLift(sim);
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(source))));
            var sequence = sim.getSmallWar();
            if (fixture.duel()) assertInstanceOf(BlackSluiceDuel.class, sequence.duel(), "Production Chapter 5 installs the duel overhead");
            else assertNull(sequence.duel(), "The no-duel control leaves the sky empty");
            var duel = (BlackSluiceDuel) sequence.duel();
            boolean recordDuel = fixture.duel() && fixture.id().equals("ordinary-15305");
            var duelFrames = new ArrayList<BufferedImage>();
            int duelOverTick = -1, dockContactTick = -1;
            assertTrue(sim.tenders().tenders().isEmpty(), "The factory must not manufacture a tender");
            var scheduled = new HashSet<String>(); int aircraft = 0;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE) {
                assertSame(boats.get(aircraft++), sim.world().airframe(air));
                var flight = sim.world().mission(air);
                for (var cycle : flight.cycleLoadouts) for (var person : cycle) {
                    assertNotNull(person.campaignSoldierId); assertTrue(scheduled.add(person.campaignSoldierId));
                }
                if (fixture.control() == Control.ABSENT) flight.pendingDelay += 9999;
            }
            assertEquals(deployment.size(), scheduled.size()); assertEquals(2, aircraft);
            var landed = new HashSet<String>();
            var phase = sequence.progress().phase();
            frames.add(frame(context, fixture.id() + "-arrival.png", sim, false));
            int maxTicks = fixture.control() == Control.ABSENT ? 5400 : Integer.getInteger("battle.blackSluice.maxTicks", 27000);
            boolean requestedShip = false, requestedBuild = false, injected = false, splitSeen = false, buildAccepted = false;
            int splitTick = -1, gunShots = 0, rearShots = 0;
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                if (phase == SmallWarProgress.Phase.REAR_POSITION) {
                    if (!requestedShip && sim.playerSeesCell(sequence.buildX(), sequence.buildY())) {
                        sim.getCommandPowerService().requestActivation(ShipOnStation.ID, sequence.buildX(), sequence.buildY());
                        requestedShip = true;
                        report.append("  requested normal Ship on Station tick=").append(sim.getSimTickIndex()).append('\n');
                    }
                    if (!requestedBuild && sequence.tender() != null) {
                        assertSame(source, sequence.tender().ship().supportSource());
                        sim.shipCommands().requestBuild(sequence.tender(), BuildOption.VULCAN_TURRET, sequence.buildX(), sequence.buildY());
                        requestedBuild = true;
                        report.append("  requested actual rear-position gun tick=").append(sim.getSimTickIndex()).append('\n');
                    }
                }
                if (fixture.control() == Control.RETREAT && sequence.progress().visited(SmallWarProgress.CUSTOMS)) {
                    assertTrue(sim.retreat()); report.append("  explicit company withdrawal after customs tick=").append(sim.getSimTickIndex()).append('\n'); break;
                }
                if (fixture.control() == Control.FUEL_LOSS && !injected && sequence.progress().split()) {
                    for (int[] tank : BlackSluice.TANKS) sim.damageCell(tank[0], tank[1], 100000);
                    injected = true; report.append("  diagnostic: destroyed the marked fuel tanks at ").append(phase).append('\n');
                }
                if (fixture.control() == Control.CUSTODY_LOSS && !injected && phase == SmallWarProgress.Phase.HANDOVER) {
                    while (sim.squadMemberCount(sequence.custodySquadId()) > 0)
                        sim.applyDamage(sim.squadMemberAt(sequence.custodySquadId(), 0), 100000, 100000);
                    injected = true; report.append("  diagnostic: killed the whole custody party at HANDOVER\n");
                }
                sim.advance(BattleSimulation.TICK_DT);
                if (!buildAccepted && requestedBuild && sequence.tender() != null
                        && sim.shipCommands().notice(sequence.tender().ship()) == ShipCommandService.Notice.LAID) {
                    buildAccepted = true; report.append("  rear-position build accepted by the ordinary ship command service\n");
                }
                for (int i = 0; i < sim.liveUnitCount(); i++) {
                    long id = sim.liveUnitAt(i);
                    if (sim.identity().faction(id) != Faction.MARINE || sim.identity().type(id) != UnitType.MARINE) continue;
                    String person = (String) sim.getEntityWorld().getObject(id, sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                    assertTrue(scheduled.contains(person)); landed.add(person);
                }
                for (var shot : sim.getShotsThisFrame()) {
                    if (shot.shooterId == sequence.supportGunId()) gunShots++;
                    if (sequence.rearTurretIds().contains(shot.shooterId)) rearShots++;
                }
                if (!splitSeen && sequence.progress().split()) {
                    splitSeen = true; splitTick = sim.getSimTickIndex();
                    report.append("  SPLIT tick=").append(splitTick).append(" withdrawing squads=").append(sequence.withdrawingSquads()).append('\n');
                    frames.add(frame(context, fixture.id() + "-split-" + splitTick + ".png", sim, false));
                }
                if (OBSERVE && sim.getSimTickIndex() % 1200 == 0)
                    frames.add(frame(context, fixture.id() + "-minute-" + sim.getSimTickIndex() + ".png", sim, false));
                if (OBSERVE && sim.getSimTickIndex() % 300 == 0 && sim.getSimTickIndex() >= Integer.getInteger("battle.blackSluice.closeFrom", Integer.MAX_VALUE)
                        && sim.getSimTickIndex() <= Integer.getInteger("battle.blackSluice.closeTo", 0))
                    frames.add(new SnapshotArtifact(fixture.id() + "-close-" + sim.getSimTickIndex() + ".png",
                            new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, Integer.getInteger("battle.blackSluice.closeX", 110),
                                    Integer.getInteger("battle.blackSluice.closeY", 170), 70).withImpactEffects()
                                    .render(sim, "BLACK SLUICE / CLOSE / tick " + sim.getSimTickIndex(), ReviewAnnotations.builder().build())));
                if (recordDuel && splitSeen) {
                    int since = sim.getSimTickIndex() - splitTick;
                    boolean over = duel.hulls().stream().allMatch(h -> h.destroyed() || h.departed());
                    if (over && duelOverTick < 0) duelOverTick = sim.getSimTickIndex();
                    boolean tail = duelOverTick < 0 || sim.getSimTickIndex() - duelOverTick <= DUEL_FRAME_TAIL;
                    if (since <= DUEL_FRAME_TICKS && since % DUEL_FRAME_EVERY == 0 && tail)
                        duelFrames.add(new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 150, 76, 300)
                                .withImpactEffects().withUnitShadows()
                                .render(sim, "BLACK SLUICE / THE DUEL OVER THE SOUTHERN GROUND / " + since / 30 + "s after Voss's order / "
                                        + duelCaption(duel), ReviewAnnotations.builder().build()));
                }
                if (recordDuel && splitSeen && (sim.getSimTickIndex() - splitTick) % 90 == 0
                        && sim.getSimTickIndex() - splitTick <= 990)
                    frames.add(new SnapshotArtifact(String.format(Locale.ROOT, "%s-liftoff-t%03d.png", fixture.id(), sim.getSimTickIndex() - splitTick),
                            new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 176, 40, 200).withImpactEffects().withUnitShadows()
                                    .render(sim, "BLACK SLUICE / LIFTOFF / " + String.format(Locale.ROOT, "%.1f", (sim.getSimTickIndex() - splitTick) / 30f)
                                            + "s after Voss's order / " + duelCaption(duel), ReviewAnnotations.builder().build())));
                if (recordDuel && dockContactTick < 0 && marineNear(sim, 200)) {
                    dockContactTick = sim.getSimTickIndex();
                    frames.add(new SnapshotArtifact(fixture.id() + "-dock-contact-" + dockContactTick + ".png",
                            new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 222, 160, 80).withImpactEffects().withUnitShadows()
                                    .render(sim, "BLACK SLUICE / MARINES IN THE BERTH YARD / tick " + dockContactTick, ReviewAnnotations.builder().build())));
                }
                if (splitSeen && sim.getSimTickIndex() == splitTick + 360)
                    frames.add(frame(context, fixture.id() + "-withdrawal-" + sim.getSimTickIndex() + ".png", sim, false));
                var next = sequence.progress().phase();
                if (next != phase) {
                    phase = next;
                    report.append("  tick=").append(sim.getSimTickIndex()).append(" phase=").append(phase).append(" landed=").append(landed.size()).append('\n');
                    frames.add(frame(context, fixture.id() + "-" + phase.name().toLowerCase() + "-" + sim.getSimTickIndex() + ".png", sim, false));
                }
                if (sim.getSimTickIndex() % (OBSERVE ? 300 : 1800) == 0) {
                    int marines = 0, greyhook = 0;
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long id = sim.liveUnitAt(i);
                        if (!sim.identity().type(id).combatant) continue;
                        if (sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id) == UnitType.MARINE) marines++;
                        if (sim.identity().faction(id) == Faction.DEFENDER) greyhook++;
                    }
                    String status = "  tick=" + sim.getSimTickIndex() + " phase=" + phase + " landed=" + landed.size()
                            + " marinesAlive=" + marines + " greyhookAlive=" + greyhook
                            + " held=" + sequence.progress().held(0) + "/" + sequence.progress().held(1) + "/" + sequence.progress().held(2)
                            + " gunShots=" + gunShots + " rearShots=" + rearShots;
                    if (OBSERVE) {
                        var where = new StringBuilder();
                        for (int i = 0; i < sim.liveUnitCount(); i++) {
                            long id = sim.liveUnitAt(i);
                            if (sim.squad().hasSquad(id) && sim.squad().squadId(id) == sequence.custodySquadId())
                                where.append(String.format(" (%.0f,%.0f)", sim.world().x(id), sim.world().y(id)));
                        }
                        status += " custody=" + where + " safe=" + sequence.progress().safeHandover();
                    }
                    report.append(status).append('\n');
                    System.out.println("[black-sluice] " + fixture.id() + status);
                }
            }
            var result = sequence.report(sim);
            report.append("  receipt=").append(result).append(" winner=").append(sim.getWinner()).append(" landed=").append(landed.size())
                    .append(" gunShots=").append(gunShots).append(" gunAlive=").append(sim.world().isAlive(sequence.supportGunId()))
                    .append(" rearShots=").append(rearShots).append(" fuelIntact=").append(SmallWarSequence.infrastructureIntact(sim)).append('\n');
            report.append("  greyhook=").append(sequence.forceReport(sim)).append('\n');
            for (var squad : sim.getSquads()) if (sim.squadMemberCount(squad.id) > 0)
                report.append("  squad=").append(squad.id).append(" side=").append(squad.faction).append(" n=").append(sim.squadMemberCount(squad.id))
                        .append(" at=").append(Math.round(squad.centroidX)).append(',').append(Math.round(squad.centroidY))
                        .append(" goal=").append(squad.currentGoal).append(" order=").append(sim.getSquadCommandDirective(squad.id) == null ? null
                                : sim.getSquadCommandDirective(squad.id).assignment()).append('\n');
            frames.add(frame(context, fixture.id() + "-final.png", sim, false));
            var outcome = MissionResolver.compute(sim, mission, null);
            assertEquals(result, outcome.smallWar);
            if (duel != null) {
                report.append("  duel ordered=").append(duel.ordered()).append('\n');
                for (Warship hull : duel.hulls()) {
                    var line = duel.entry(hull);
                    report.append("    ").append(hull.hullId).append(' ').append(hull.faction).append(" outcome=")
                            .append(line == null ? null : line.outcome()).append(" at=").append(line == null ? null : line.seconds())
                            .append("s hull=").append(Math.round(hull.hullFraction() * 100)).append("% groundShots=").append(hull.groundShots()).append('\n');
                }
                report.append("  overhead=").append(outcome.smallWarOverhead).append('\n');
                assertEquals(duel.outcomeSummary(), outcome.smallWarOverhead, "The debrief reads the duel's own summary");
                for (Warship hull : duel.hulls()) {
                    assertEquals(0, hull.groundShots(), hull.hullId + " never fires at the ground");
                    assertNotNull(duel.entry(hull), hull.hullId + " has a ledger line");
                }
                for (Warship wolf : duel.wolves()) assertTrue(wolf.missionOwned(), "The Wolfs are the mission's, not the player's");
                if (result.split()) {
                    assertTrue(duel.ordered(), "Voss's order starts the duel");
                    assertEquals(2, duel.wolves().size());
                    assertNotNull(outcome.smallWarOverhead);
                } else assertNull(outcome.smallWarOverhead, "No order, no duel to report");
            } else assertNull(outcome.smallWarOverhead);
            if (!duelFrames.isEmpty()) frames.add(SnapshotArtifact.animation(fixture.id() + "-duel.gif", duelFrames, 100));
            report.append("  casualties=").append(outcome.marinesLost).append("/").append(outcome.marinesEngaged).append('\n');
            victories.put(fixture.id(), outcome.victory);
            if (OBSERVE) return outcome.victory;
            switch (fixture.control()) {
                case ABSENT -> { assertTrue(landed.isEmpty()); assertFalse(result.complete()); assertFalse(result.customsHeld()); }
                case FUEL_LOSS -> { assertTrue(result.infrastructureLost()); assertFalse(result.complete()); }
                case CUSTODY_LOSS -> { assertEquals(SmallWarProgress.CUSTODIANS, result.custodiansLost()); assertFalse(result.complete()); }
                case RETREAT -> { assertTrue(sequence.progress().visited(SmallWarProgress.CUSTOMS)); assertFalse(result.complete()); }
                case ORDINARY -> {
                    assertTrue(result.split(), fixture.id() + " must actually take fuel control");
                    assertTrue(result.complete(), fixture.id() + " must hand Black Sluice over");
                    assertEquals(Faction.MARINE, sim.getWinner());
                    var drent = sequence.forceReport(sim).get(SmallWarForces.Outfit.DRENT_HAULAGE);
                    assertTrue(drent.withdrawn() > 0, fixture.id() + ": Drent survivors must physically leave by the service road");
                    assertTrue(result.greyhookWithdrawn() + sequence.forceReport(sim).values().stream().mapToInt(SmallWarSequence.ForceReport::living).sum() < SmallWarForces.total(),
                            "The withdrawal and the fighting both cost Greyhook real strength");
                }
            }
            if (fixture.id().equals("ordinary-15305")) addDebrief(context, frames, outcome);
            var premiums = new ArrayList<Integer>();
            boolean wasSettled = campaign.smallWarSettled();
            int total = AgreusArcCampaign.settleSmallWar(campaign, outcome, premiums::add);
            assertEquals(outcome.victory ? outcome.payoutEarned + (custody ? 12000 : 0) : 0, total);
            assertEquals(-1, AgreusArcCampaign.settleSmallWar(campaign, outcome, premiums::add), "A duplicate receipt is refused");
            assertEquals(outcome.victory, campaign.smallWarSettled() && !wasSettled);
            report.append("  ledger accepted once / credits=").append(total).append(" premium=").append(premiums)
                    .append(" smallWarSettled=").append(campaign.smallWarSettled()).append('\n');
            return outcome.victory;
        }
    }

    private void addMap(SnapshotContext context, List<SnapshotArtifact> frames) throws Exception {
        var lift = DetachmentResolver.buildShuttleManifestForPersonnel(mission(), List.of(boat("map-one"), boat("map-two")), 36);
        try (var sim = BattleSetup.createSmallWar(15305, lift, new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD), true)) {
            var marks = ReviewAnnotations.builder()
                    .box(14, 148, 40, 188, "COMPANY LIFT / HOLDERS / CUSTODY PARTY", ReviewStyle.LANDING);
            var labels = new String[]{"1 / CUSTOMS BLOCK", "2 / FUEL CONTROL / HANDOVER", "3 / DISPATCH PIER"};
            for (int i = 0; i < 3; i++) {
                var site = BlackSluice.SITES.get(i);
                marks.box(site.left(), site.bottom(), site.right(), site.top(), labels[i], i == 1 ? ReviewStyle.KEEP : ReviewStyle.OBJECTIVE);
            }
            marks.box(48, 148, 60, 157, "REAR POSITION / TENDER BUILD", ReviewStyle.KEEP)
                    .box(140, 182, 157, 195, "PRESERVE MARKED FUEL TANKS", ReviewStyle.KEEP)
                    .label(BlackSluice.GUN_X, BlackSluice.GUN_Y - 3, "SUPPORT GUN", ReviewStyle.ROUTE)
                    .arrow(66, 164, 106, 164, "EXPOSED CAUSEWAY", ReviewStyle.ROUTE)
                    .arrow(66, 188, 118, 188, "NORTH SERVICE GALLERY", ReviewStyle.APPROACH)
                    .arrow(66, 139, 108, 139, "SOUTH SERVICE GALLERY", ReviewStyle.APPROACH)
                    .arrow(134, 144, 192, 127, "SERVICE ROAD / VEHICLE LOCK / WAY OUT", ReviewStyle.NOTE)
                    .arrow(BlackSluice.PIER_INGRESS_X, BlackSluice.PIER_INGRESS_Y, 170, 160, "PIER STAIR", ReviewStyle.ROUTE)
                    .box(BlackSluice.DOCK_LEFT, BlackSluice.DOCK_BOTTOM, BlackSluice.DOCK_RIGHT, BlackSluice.DOCK_TOP,
                            "BERTH YARD", ReviewStyle.NOTE)
                    .arrow(190, 160, 206, 160, "PRESSURE VESTIBULE", ReviewStyle.APPROACH)
                    .arrow(240, 160, BlackSluice.GATE_X + 4, BlackSluice.GATE_Y, "BERTH GATE", ReviewStyle.ROUTE)
                    .arrow(283, 150, 283, 74, "BERTH ROAD: THE ONLY WAY DOWN", ReviewStyle.ROUTE)
                    .label(150, 92, "RIDGE: NO WAY THROUGH", ReviewStyle.NOTE)
                    .label(56, 40, "ROCK MASSIF", ReviewStyle.NOTE)
                    .box(0, 0, BlackSluice.WIDTH - 1, BlackSluice.AIRSPACE_TOP, "DUEL AIRSPACE", ReviewStyle.NOTE);
            var cradle = BlackSluice.VENTURE_CRADLE;
            marks.box(cradle.left(), cradle.bottom(), cradle.right(), cradle.top(), "VENTURE CRADLE", ReviewStyle.OBJECTIVE);
            for (var berth : BlackSluice.HOUND_BERTHS)
                marks.box(berth.left(), berth.bottom(), berth.right(), berth.top(), "HOUND BERTH", ReviewStyle.OBJECTIVE);
            frames.add(new SnapshotArtifact("black-sluice-overview.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080)
                    .render(sim, "A SMALL WAR / BLACK SLUICE / seed 15305 / roofs omitted / observer annotations", marks.build())));
            frames.add(new SnapshotArtifact("black-sluice-detail-fuel-control.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 134, 174, 88)
                    .render(sim, "BLACK SLUICE / FUEL CONTROL, GUN AND TANK YARD / 88 cells across")));
            // Greyhook's ships landed at true scale on the southern pads, below the ridge from the ground fight.
            frames.add(new SnapshotArtifact("black-sluice-detail-dock.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 196, 44, 192)
                    .withUnitShadows().render(sim, "BLACK SLUICE / SOUTHERN BERTHS / Venture and two Hound (P) landed at true scale / 192 cells across")));
            frames.add(new SnapshotArtifact("black-sluice-detail-venture-stern.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 222, 36, 64)
                    .withUnitShadows().render(sim, "BLACK SLUICE / VENTURE STERN, CONCRETE PAD AND GANTRY ARMS / 64 cells across")));
            frames.add(new SnapshotArtifact("black-sluice-detail-hound-berth.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 142, 34, 48)
                    .withUnitShadows().render(sim, "BLACK SLUICE / WEST HOUND BERTH, GANTRY ARMS AND KERB / 48 cells across")));
            frames.add(new SnapshotArtifact("black-sluice-detail-berth-gate.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 262, 124, 112)
                    .withUnitShadows().render(sim, "BLACK SLUICE / BERTH YARD, BERTH GATE, BERTH ROAD AND RIDGE / 112 cells across")));
            frames.add(new SnapshotArtifact("black-sluice-detail-customs.png", new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 70, 164, 88)
                    .render(sim, "BLACK SLUICE / CUSTOMS, REAR BAY AND GALLERY MOUTHS / 88 cells across")));
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
            frames.add(new SnapshotArtifact("black-sluice-debrief.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
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
        props.put("missionHeader", "House Rusk / Black Sluice");
        MissionSelectScreen.putAgreus(props, ratified(AgreusYardAgreement.HOUSE_CUSTODY), "");
        props.put("missions", MissionSelectScreen.missionRows(List.of(mission), mission, ignored -> { }));
        props.put("stationingRows", List.of());
        MissionSelectScreen.putDetail(props, mission, none, none);
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), MissionSelectScreen.COMPONENT_PATHS);
        loader.reload();
        try (var instance = loader.build(new Reactor(), MissionSelectScreen.ROOT_COMPONENT, props)) {
            var document = new UiDocument(instance.root());
            for (var style : instance.styles()) document.addStyleSheet(style);
            document.theme(MarineOpsThemes.standard());
            frames.add(new SnapshotArtifact("black-sluice-contract-board.png", new HeadlessUiRenderer(context.modRoot(), context.starsectorCore())
                    .renderRelative(document, 1920, 1080, 1f, MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT)));
        }
    }

    private static String duelCaption(BlackSluiceDuel duel) {
        var s = new StringBuilder();
        for (Warship hull : duel.hulls()) {
            String name = switch (hull.hullId) { case "venture" -> "Greyhook"; case "hound" -> "Hound"; default -> "Wolf"; };
            s.append(name).append(' ').append(hull.destroyed() ? "down" : hull.departed() ? "gone" : hull.departing() ? "leaving"
                    : Math.round(hull.hullFraction() * 100) + "%").append("  ");
        }
        return s.toString().trim();
    }

    private static AgreusArcProgress ratified(AgreusYardAgreement route) {
        var p = YardOwnersRewardTest.afterWarrant(); String id = p.nextMissionId();
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(36000, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0));
        assertTrue(p.signAgreement(route));
        // Chapter 4 does not exist yet; its victory hook is called directly to reach a ratified route.
        assertTrue(p.ratifyAgreement(route));
        return p;
    }
    private FittedBoat boat(String id) {
        return new FittedBoat(ShuttleType.AEROSHUTTLE, BoatFitting.standard(BoatFittingSlot.PLATING), BoatFitting.standard(BoatFittingSlot.DRIVE), id);
    }
    /** Whether a company marine is at or east of {@code x}: the first marines to reach the dock apron. */
    private static boolean marineNear(BattleSimulation sim, float x) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id) == UnitType.MARINE && sim.world().x(id) >= x)
                return true;
        }
        return false;
    }
    private Mission mission() { return AgreusArcCampaign.offer(ratified(AgreusYardAgreement.HOUSE_CUSTODY), "Agreus"); }
    private SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim, boolean detail) throws Exception {
        var sequence = sim.getSmallWar();
        return new SnapshotArtifact(name, new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080)
                .withImpactEffects().withUnitShadows().render(sim, "BLACK SLUICE / " + sequence.progress().phase()
                        + (sequence.progress().split() ? " / AFTER VOSS'S ORDER" : ""), ReviewAnnotations.builder().build()));
    }
}
