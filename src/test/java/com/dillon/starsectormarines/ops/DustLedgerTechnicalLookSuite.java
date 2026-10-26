package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.FiniteCommandPowerResources;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Sable Wake's rocket truck working its corners in the Dust Ledger, through the look preset: the commanderEvidence company (two boats,
 * field-security kit, the recovered Hound called down at the first pad) under autonomous command. One camera holds
 * the truck's corner of the east yard, the ledger room's east door in frame; one rides the truck itself. Look
 * evidence, not balance; opt-in.
 */
public final class DustLedgerTechnicalLookSuite implements SnapshotSuite {

    /** Ticks recorded: the headless run ends near 5,950, when the truck has crippled the Hound and it falls. */
    static final int TICKS = 6000;
    /** A frame every this many ticks: four times real speed at 30 fps. */
    static final int EVERY = 4;
    /**
     * Recorded frames kept as stills, from the headless run: the truck parked in the quiet yard (tick 2,000), driving
     * through the south link at the alarm (2,560) to the south yard (2,640), back up the lane between the pump house
     * and the workshop as the Hound comes on (4,860), and its fight at the east door from its first salvo (5,500)
     * to its wreck (5,668).
     */
    static final int[] STILLS = {500, 640, 660, 1215, 1376, 1392, 1416};
    /** The armor-drop pick naming the Hound, the one machine aboard. */
    static final List<Integer> HOUND_PICK = List.of(0);

    @Override public String id() { return "dust-ledger-technical-look"; }

    @Override public String label() { return "The Dust Ledger's rocket truck in its yard and against the Hound"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            // The evidence company exactly (commanderEvidence corvus): the same roster, kit, boats and Hound, so the
            // recording plays the battle that run measured, and the stills land on its moments.
            var chapter = CorvusChapter.DUST_LEDGER;
            Mission mission = offered(chapter);
            var roster = new MarineRoster();
            roster.ensureActiveSoldiers(chapter.squads * 12);
            if (!roster.mechBay().receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment())))
                throw new IllegalStateException("the bay refused the recovered Hound");
            Set<String> selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
            for (String squad : selected) roster.applySquadEquipment(squad, SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                    SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
            var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, chapter.squads * 12, null);
            var boats = IntStream.range(0, 2).mapToObj(i -> FittedBoat.standard(ShuttleType.AEROSHUTTLE)).toList();
            var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, deployment.size());
            var fixture = BattleFixtureJson.fromJson(BattleFixtureJson.toJson(
                    chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface))));
            List<CommandPower> powers = PowerCatalog.resolve(List.of(), mission, roster.mechBay().activeDeployment());
            return LookReview.record(context.modRoot(), new LookReview.Shot("dust-ledger-technical",
                    LookGround.of(SurfacePalette.ARID, mission.battleSeed), 40, 30, placement -> {
                        BattleSimulation sim = fixture.build();
                        var resources = new FiniteCommandPowerResources(30);
                        BattleLaunchOverlay.capture(0, deployment, FlybyRoster.EMPTY, FlybyRoster.EMPTY, powers,
                                resources.availableSupplies(), mission.fieldPresencePolicy).applyTo(sim, resources);
                        callHound(sim);
                        return sim;
                    },
                    placement -> List.of(LookReview.View.close("yard", 202f, 47f, 40f), truck()),
                    TICKS, EVERY, STILLS.length).withStills(STILLS));
        }
    }

    /** The chapter as the campaign offers it, the earlier chapters settled won, so its Mech Support is sourced. */
    static Mission offered(CorvusChapter wanted) {
        var progress = new CorvusWaylineProgress();
        var route = CorvusWaylineCampaignTest.closedRoute(true);
        for (CorvusChapter chapter : CorvusChapter.values()) {
            Mission mission = CorvusWaylineCampaign.offer(progress, chapter.planetName, route);
            if (chapter == wanted) return mission;
            if (!progress.begin(mission.id, chapter.planetName)) throw new IllegalStateException("cannot begin " + chapter);
            progress.settle(mission.id, chapter.planetName, true, chapter.payment);
        }
        throw new IllegalStateException("no chapter " + wanted);
    }

    /** Rides the truck at play zoom; holds where it burned once it is gone. */
    static LookReview.View truck() {
        float[] at = {202f, 47f};
        return new LookReview.View("truck", sim -> {
            long technical = sim.getDustLedger() == null ? 0L : sim.getDustLedger().technical();
            if (technical != 0L && sim.convoy().mission(technical) != null) {
                at[0] = sim.convoy().body(technical).x;
                at[1] = sim.convoy().body(technical).y;
            }
            return new float[] {at[0], at[1], 48f};
        });
    }

    /** Calls the Hound down at the first pad that takes it, before the first tick, as the evidence harness does. */
    private static void callHound(BattleSimulation sim) {
        var service = sim.getCommandPowerService();
        var support = service.getPower(MechSupport.ID);
        if (!service.canActivate(support)) throw new IllegalStateException("Mech Support is not ready at launch");
        for (var pad : sim.getMarineLandingPads()) if (support.canTarget(pad.centerX, pad.centerY, sim, HOUND_PICK)) {
            service.requestActivation(MechSupport.ID, pad.centerX, pad.centerY, HOUND_PICK);
            return;
        }
        throw new IllegalStateException("no pad takes the Hound");
    }
}
