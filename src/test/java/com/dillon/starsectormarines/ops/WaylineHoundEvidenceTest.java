package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.fixture.CommandPowerCommitment;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.WaylineHoundRecovery;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in production recovery, arrival and command handback evidence. */
@Tag("counterattack-command-evidence")
class WaylineHoundEvidenceTest {
    private static final Path OUTPUT = Path.of("build/reports/commander/wayline-hound");

    @Test void recoveryRequiresCompanyInfantryAndFiveUncontestedSecondsAndNeverRespawns() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        try (var sim = new BattleSimulation(grid, new CellTopology(40, 40))) {
            var log = new StoryBeatLog();
            var recovery = new WaylineHoundRecovery(sim, 20, 20, log);
            assertFalse(sim.directControl().canEnter(recovery.frameId()));
            sim.spawn(new EntitySpec("militia", Faction.ALLY, UnitType.MILITIA, 18, 20));
            tick(recovery, sim, 180);
            assertFalse(recovery.found());
            assertEquals(0, recovery.mechId());
            long soldier = sim.spawn(new EntitySpec("company", Faction.MARINE, UnitType.MARINE, 22, 20));
            long raider = sim.spawn(new EntitySpec("raider", Faction.DEFENDER, UnitType.MILITIA, 25, 20));
            tick(recovery, sim, 180);
            assertTrue(recovery.found());
            assertEquals(0, recovery.mechId(), "contested equipment cannot be commissioned");
            sim.takeOffTheField(raider);
            tick(recovery, sim, 100);
            sim.takeOffTheField(soldier);
            tick(recovery, sim, 1);
            sim.spawn(new EntitySpec("replacement", Faction.MARINE, UnitType.MARINE, 22, 20));
            tick(recovery, sim, 100);
            assertEquals(0, recovery.mechId(), "leaving the berth resets startup");
            sim.world().setArmor(recovery.frameId(), 123f);
            tick(recovery, sim, 51);
            long mech = recovery.mechId();
            assertNotEquals(0, mech);
            assertEquals(123f, sim.world().armor(mech), "activation cannot repair combat damage");
            assertFalse(sim.world().isAlive(recovery.frameId()));
            assertEquals(MechVariant.HOUND, sim.identity().mechVariant(mech));
            assertEquals(OpeningOperationCommand.issuer(Faction.MARINE),
                    sim.getSquadCommandDirective(sim.squad().squadId(mech)).issuer());
            assertFalse(sim.directControl().active(), "the introduction is optional");
            assertTrue(sim.directControl().enter(mech));
            sim.directControl().exit();
            assertFalse(sim.directControl().active());
            assertEquals(MechWeaponComponent.NOSE_CHAINGUN, sim.world().mechLoadout(mech).mount(MechMountSlot.ARMS).component);
            assertNull(sim.world().mechLoadout(mech).mount(MechMountSlot.LEFT_SHOULDER));
            assertNull(sim.world().mechLoadout(mech).mount(MechMountSlot.RIGHT_SHOULDER));
            sim.applyExternalDamage(mech, 100000f, 10000f);
            tick(recovery, sim, 180);
            assertEquals(new WaylineHoundRecovery.Report(true, true), recovery.report(sim));
            assertEquals(mech, recovery.mechId(), "no replacement appears during the mission");
            assertEquals(List.of(WaylineHoundRecovery.FOUND, WaylineHoundRecovery.READY, WaylineHoundRecovery.LOST),
                    log.events().stream().map(StoryBeatLog.Event::id).toList());
        }
    }

    @Test void counterattackArrivalCanSecureTheBerthAndReturnTheWalkerToMissionCommand() throws Exception {
        Files.createDirectories(OUTPUT);
        var roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        var deployment = CampaignMarineDeployment.freeze(roster, 12);
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        try (var sim = BattleSetup.createOpeningOperation(8101, manifest, 1,
                OpeningOperationKind.COUNTERATTACK, profile(), true)) {
            deployment.applyTo(sim, 1);
            var recovery = sim.getWaylineHound();
            var renderer = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080,
                    CounterattackDepot.HOUND_X, CounterattackDepot.HOUND_Y, 72);
            ImageIO.write(renderer.render(sim, "COUNTERATTACK / Wayward offline in the maintenance berth"),
                    "png", OUTPUT.resolve("01-offline.png").toFile());
            int ordered = -1;
            for (int t = 0; t < 9000 && !sim.isComplete() && recovery.mechId() == 0; t++) {
                sim.advance(BattleSimulation.TICK_DT);
                for (var squad : sim.getSquads()) if (squad.faction == Faction.MARINE
                        && squad.campaignSquadId != null && squad.originalSize == 12 && squad.id != ordered) {
                    // An explicit player detour to the marked berth, after both ferry trips land.
                    sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id, CounterattackDepot.HOUND_X - 2, CounterattackDepot.HOUND_Y - 1),
                            CommandAuthority.PLAYER_INTERVENTION, "hound-evidence", "Secure the marked berth");
                    ordered = squad.id;
                }
            }
            ImageIO.write(renderer.render(sim, "COUNTERATTACK / company secures Wayward"),
                    "png", OUTPUT.resolve("02-recovery.png").toFile());
            assertNotEquals(0, recovery.mechId(), "real company arrival must be able to reach and secure the berth");
            long mech = recovery.mechId();
            assertTrue(sim.directControl().canEnter(mech));
            assertFalse(sim.directControl().active());
            assertTrue(sim.directControl().enter(mech));
            sim.directControl().exit();
            if (ordered >= 0) sim.handoffSquadCommand(ordered, "hound-evidence", CommandAuthority.MISSION_COMMAND,
                    OpeningOperationCommand.issuer(Faction.MARINE), null, "Return to the depot advance");
            int readyTick = sim.getSimTickIndex();
            float startX = sim.world().x(mech), startY = sim.world().y(mech);
            int shots = 0;
            for (int t = 0; t < 6000 && !sim.isComplete() && sim.world().isAlive(mech); t++) {
                sim.advance(BattleSimulation.TICK_DT);
                for (var shot : sim.getShotsThisFrame()) if (shot.shooterId == mech) shots++;
            }
            ImageIO.write(renderer.render(sim, "COUNTERATTACK / Wayward rejoins the depot advance"),
                    "png", OUTPUT.resolve("03-advance.png").toFile());
            var order = sim.getSquad(sim.squad().squadId(mech)).assignmentForExecution();
            assertNotNull(order, "handback must retain an executable mission order");
            Files.writeString(OUTPUT.resolve("recovery.json"), new JSONObject()
                    .put("endTick", sim.getSimTickIndex()).put("battleComplete", sim.isComplete()).put("houndAlive", sim.world().isAlive(mech)).put("readyTick", readyTick).put("companySquad", ordered).put("activated", true)
                    .put("handoffX", sim.world().x(mech)).put("handoffY", sim.world().y(mech))
                    .put("order", order.kind().name()).put("chaingunShots", shots).put("directControlOptional", true).toString(2));
            assertTrue(Math.hypot(sim.world().x(mech) - startX, sim.world().y(mech) - startY) > 2, "the walker must leave the berth under AI control");
            assertTrue(shots > 0, "the recovered chaingun must engage during the advance");
        }
    }

    @Test void allLaterStoryFactoriesPhysicallyDeliverTheOwnedHoundAndGiveItAnOrder() throws Exception {
        Files.createDirectories(OUTPUT);
        JSONArray reports = new JSONArray();
        for (int kind : new int[]{1, 2, 3, 4}) {
            var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
            if (kind != 1) { String id = route.missionId(); route.begin(id); route.settle(id, true, null); route.commit(kind == 4 ? 3 : kind); }
            if (kind == 4) { String id = route.missionId(); route.begin(id); route.settle(id, true, null); }
            var mission = SupplyRouteCampaign.offers(route).get(0);
            var roster = new MarineRoster(); roster.ensureActiveSoldiers(24);
            MissionResolver.recordStoryCompletion(roster, MissionOutcome.builder()
                    .missionId(OpeningOperationKind.COUNTERATTACK.missionId).missionSource(MissionSource.STORY)
                    .victory(true).waylineHound(new WaylineHoundRecovery.Report(true, true)).build());
            var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission,
                    List.of(FittedBoat.standard(ShuttleType.AEROSHUTTLE), FittedBoat.standard(ShuttleType.AEROSHUTTLE)), 24);
            var power = (MechSupport) CommandPowerCommitment.capture(
                    PowerCatalog.resolve(List.of(), mission, roster.mechBay().activeDeployment()).get(0)).toPower();
            try (var sim = MissionLaunch.supplyRouteBuilder(route, 9301, manifest, profile()).get()) {
                CampaignMarineDeployment.freeze(roster, 24).applyToAuthoredPlayerLift(sim);
                sim.setCommandPowers(List.of(power));
                var insertion = sim.world().mission(sim.getAirEntityIds()[0]);
                int x = (int) insertion.lzX, y = (int) insertion.lzY;
                assertTrue(power.canTarget(x, y, sim), mission.name + ": shared apron must accept heavy lift");
                sim.getCommandPowerService().requestActivation(power.id, x, y);
                long mech = 0;
                for (int t = 0; t < 1800 && !sim.isComplete(); t++) {
                    sim.advance(BattleSimulation.TICK_DT);
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long id = sim.liveUnitAt(i);
                        if (sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id) == UnitType.HEAVY_MECH) mech = id;
                    }
                    if (mech != 0 && sim.getSquad(sim.squad().squadId(mech)).assignmentForExecution() != null) break;
                }
                assertNotEquals(0, mech, mission.name + ": heavy transport must actually unload");
                assertEquals(WaylineHound.NAME, sim.identity().name(mech));
                assertEquals(MechVariant.HOUND, sim.identity().mechVariant(mech));
                assertEquals(MechWeaponComponent.NOSE_CHAINGUN, sim.world().mechLoadout(mech).mount(MechMountSlot.ARMS).component);
            assertNull(sim.world().mechLoadout(mech).mount(MechMountSlot.LEFT_SHOULDER));
                assertNull(sim.world().mechLoadout(mech).mount(MechMountSlot.RIGHT_SHOULDER));
                var directive = sim.getSquadCommandDirective(sim.squad().squadId(mech));
                assertEquals(sim.playerSupportCommandClaim().issuer(), directive.issuer());
                assertNotNull(directive.assignment(), mission.name + ": the mission commander must employ the walker");
                reports.put(new JSONObject().put("mission", mission.name).put("arrivalTick", sim.getSimTickIndex())
                        .put("commander", directive.issuer()).put("order", directive.assignment().kind().name()));
                if (kind == 1) ImageIO.write(new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080)
                        .render(sim, "OPEN THE JUNCTION / recovered Wayward arrives by employer heavy lift"),
                        "png", OUTPUT.resolve("04-later-deployment.png").toFile());
            }
        }
        Files.writeString(OUTPUT.resolve("later-missions.json"), reports.toString(2));
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
    }
    private static void tick(WaylineHoundRecovery recovery, BattleSimulation sim, int count) {
        for (int i = 0; i < count; i++) recovery.tick(sim);
    }
}
