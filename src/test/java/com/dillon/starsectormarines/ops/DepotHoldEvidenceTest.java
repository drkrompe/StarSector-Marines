package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, actual boats and one campaign squad: detour to the Hound versus ordinary advance. */
@Tag("counterattack-command-evidence")
class DepotHoldEvidenceTest {
    @Test void playWarehouseDefenseWithTwelveCompanyTroopsAndSixMilitia() throws Exception {
        Path out = Path.of("build/reports/commander/depot-hold");
        Files.createDirectories(out);
        JSONArray results = new JSONArray();
        for (long seed : new long[]{8101, 8102}) for (boolean recover : new boolean[]{true, false}) {
            String name = seed + (recover ? "-recover-hound" : "-ordinary-advance");
            var roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
            var deployment = CampaignMarineDeployment.freeze(roster, 12);
            var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
            try (var sim = BattleSetup.createOpeningOperation(seed, manifest, 1,
                    OpeningOperationKind.COUNTERATTACK, new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD), true)) {
                deployment.applyTo(sim, 1);
                var renderer = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080).withImpactEffects();
                int ordered = -1, captureTick = -1, wave = 0, houndShots = 0;
                Map<Integer, Integer> roadSpawn = new TreeMap<>(), roadInside = new TreeMap<>();
                int technicalEntered = -1, technicalParked = -1, technicalDestroyed = -1, technicalShots = 0;
                boolean handedBack = false;
                JSONArray beats = new JSONArray();
                JSONArray timeline = new JSONArray();
                int lastMilitia = 0, militiaShots = 0, militiaDefenseShots = 0, militiaNearHold = 0;
                boolean everyLivingMilitiaGathered = false;
                Set<Long> militiaIds = new HashSet<>();
                for (int tick = 0; tick < 18000 && !sim.isComplete(); tick++) {
                    sim.advance(BattleSimulation.TICK_DT);
                    var hound = sim.getWaylineHound();
                    if (recover && ordered < 0) for (var squad : sim.getSquads()) {
                        if (squad.faction != Faction.MARINE || squad.campaignSquadId == null || squad.originalSize != 12) continue;
                        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id,
                                        CounterattackDepot.HOUND_X - 2, CounterattackDepot.HOUND_Y - 1),
                                CommandAuthority.PLAYER_INTERVENTION, "depot-review", "Secure the marked berth");
                        ordered = squad.id;
                        break;
                    }
                    if (recover && !handedBack && hound.mechId() != 0) {
                        sim.handoffSquadCommand(ordered, "depot-review", CommandAuthority.MISSION_COMMAND,
                                OpeningOperationCommand.issuer(Faction.MARINE), null, "Join the warehouse assault");
                        handedBack = true;
                        ImageIO.write(renderer.render(sim, name + " / the Hound online / observer view"), "png", out.resolve(name + "-hound.png").toFile());
                    }
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long id = sim.liveUnitAt(i);
                        if (sim.identity().faction(id) == Faction.ALLY) militiaIds.add(id);
                    }
                    long technical = sim.getDepotHold().technicalId();
                    if (technical != 0L) {
                        if (technicalEntered < 0) technicalEntered = sim.getSimTickIndex();
                        int[] route = sim.getDepotHold().technicalRoute();
                        var body = sim.convoy().body(technical);
                        if (technicalParked < 0 && Math.hypot(body.x - route[2] - .5f, body.y - route[3] - .5f) <= 1.5f) {
                            technicalParked = sim.getSimTickIndex();
                            ImageIO.write(renderer.render(sim, name + " / technical parked / observer view"), "png", out.resolve(name + "-technical.png").toFile());
                        }
                        if (technicalDestroyed < 0 && !sim.convoy().isPresent(technical)) {
                            technicalDestroyed = sim.getSimTickIndex();
                            // And back into dispatch to finish the hold, as a player would.
                            for (var squad : sim.getSquads())
                                if (squad.faction == Faction.MARINE && squad.aliveMembers > 0)
                                    sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id,
                                                    (int) sim.getDepotHold().door(sim)[0] - 2, (int) sim.getDepotHold().door(sim)[1]),
                                            CommandAuthority.PLAYER_INTERVENTION, "depot-review", "Back to dispatch's door");
                        }
                        // The autopilot does not hunt a parked vehicle; send the company at it as a player would.
                        if (technicalParked >= 0 && technicalDestroyed < 0 && (sim.getSimTickIndex() - technicalParked) % 600 == 0)
                            for (var squad : sim.getSquads())
                                if (squad.faction == Faction.MARINE && squad.aliveMembers > 0)
                                    sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad.id, (int) body.x, (int) body.y),
                                            CommandAuthority.PLAYER_INTERVENTION, "depot-review", "Take out the technical");
                    }
                    for (var shot : sim.getShotsThisFrame()) {
                        if (shot.shooterId == hound.mechId()) houndShots++;
                        if (technical != 0L && shot.shooterId == technical) technicalShots++;
                        if (militiaIds.contains(shot.shooterId)) {
                            militiaShots++;
                            if (sim.getDepotHold().progress().committed()) militiaDefenseShots++;
                        }
                    }
                    if (tick == 600) ImageIO.write(renderer.render(sim, name + " / company assembling / observer view"),
                            "png", out.resolve(name + "-assembly.png").toFile());
                    int militia = count(sim, Faction.ALLY);
                    if (tick % 150 == 0 || militia < lastMilitia) {
                        JSONArray squads = new JSONArray();
                        for (var squad : sim.getSquads()) {
                            if (squad.faction != Faction.MARINE && squad.faction != Faction.ALLY) continue;
                            var directive = sim.getSquadCommandDirective(squad.id);
                            squads.put(new JSONObject().put("reason", directive == null ? "none" : directive.reason()).put("id", squad.id).put("faction", squad.faction.name())
                                    .put("alive", squad.aliveMembers).put("x", squad.centroidX).put("y", squad.centroidY)
                                    .put("forming", SquadFormUpSystem.formingUp(squad)));
                        }
                        timeline.put(new JSONObject().put("seconds", sim.getSimTickIndex() / 30f)
                                .put("militiaAlive", militia).put("houndOnline", hound.mechId() != 0).put("squads", squads));
                    }
                    lastMilitia = militia;
                    var hold = sim.getDepotHold().progress();
                    for (int road : sim.getDepotHold().roadSquads()) {
                        roadSpawn.putIfAbsent(road, sim.getSimTickIndex());
                        if (roadInside.containsKey(road)) continue;
                        for (int m = 0, n = sim.squadMemberCount(road); m < n; m++) {
                            long member = sim.squadMemberAt(road, m);
                            if (sim.world().hasPosition(member) && sim.getDepotHold().inside(sim.world().x(member), sim.world().y(member))) {
                                roadInside.put(road, sim.getSimTickIndex() - roadSpawn.get(road));
                                break;
                            }
                        }
                    }
                    if (hold.committed()) {
                        int near = 0;
                        for (int i = 0; i < sim.liveUnitCount(); i++) {
                            long id = sim.liveUnitAt(i);
                            if (sim.identity().faction(id) == Faction.ALLY && Math.hypot(sim.world().x(id) - sim.getDepotHold().x(),
                                    sim.world().y(id) - sim.getDepotHold().y()) <= 8) near++;
                        }
                        militiaNearHold = Math.max(militiaNearHold, near);
                        if (near > 0 && near == count(sim, Faction.ALLY)) everyLivingMilitiaGathered = true;
                    }
                    if (captureTick < 0 && hold.committed()) {
                        captureTick = sim.getSimTickIndex();
                        ImageIO.write(renderer.render(sim, name + " / warehouse captured; defense begins / observer view"), "png", out.resolve(name + "-capture.png").toFile());
                    }
                    if (hold.waves() > wave) {
                        wave = hold.waves();
                        beats.put(new JSONObject().put("wave", wave).put("tick", sim.getSimTickIndex())
                                .put("marinesAlive", count(sim, Faction.MARINE)).put("militiaAlive", count(sim, Faction.ALLY))
                                .put("raidersAlive", count(sim, Faction.DEFENDER)));
                        ImageIO.write(renderer.render(sim, name + " / road wave " + wave + " / observer view"), "png", out.resolve(name + "-wave-" + wave + ".png").toFile());
                    }
                }
                JSONObject result = new JSONObject().put("case", name).put("complete", sim.isComplete())
                        .put("winner", String.valueOf(sim.getWinner())).put("seconds", sim.getSimTickIndex() / 30f)
                        .put("captureTick", captureTick).put("waves", wave).put("houndRecovered", sim.getWaylineHound().mechId() != 0)
                        .put("houndAlive", sim.world().isAlive(sim.getWaylineHound().mechId()))
                        .put("companyInfantryAlive", infantry(sim)).put("houndShots", houndShots).put("marinesAliveIncludingHound", count(sim, Faction.MARINE))
                        .put("militiaAlive", count(sim, Faction.ALLY)).put("raidersAlive", count(sim, Faction.DEFENDER))
                        .put("pendingRaiders", sim.getDepotHold().pendingMembers())
                        .put("roadSquadTicksToDispatch", new JSONObject(roadInside)).put("technicalEntered", technicalEntered).put("technicalParked", technicalParked)
                        .put("technicalDestroyed", technicalDestroyed).put("technicalShots", technicalShots)
                        .put("technicalStructure", sim.getDepotHold().technicalId() == 0L ? -1 : sim.convoy().structure(sim.getDepotHold().technicalId()))
                        .put("technicalArmor", sim.getDepotHold().technicalId() == 0L ? -1 : sim.convoy().armor(sim.getDepotHold().technicalId()))
                        .put("technicalNearestMarine", nearestMarine(sim, sim.getDepotHold().technicalId()))
                        .put("door", new JSONArray(sim.getDepotHold().door(sim)))
                        .put("houndAt", sim.world().isAlive(sim.getWaylineHound().mechId())
                                ? new JSONArray(new float[]{sim.world().x(sim.getWaylineHound().mechId()), sim.world().y(sim.getWaylineHound().mechId())}) : JSONObject.NULL).put("beats", beats)
                        .put("militiaShots", militiaShots).put("militiaDefenseShots", militiaDefenseShots)
                        .put("maxMilitiaWithinEightCellsOfHoldAnchor", militiaNearHold)
                        .put("everyLivingMilitiaGathered", everyLivingMilitiaGathered);
                results.put(result);
                Files.writeString(out.resolve(name + "-timeline.json"), timeline.toString(2));
                Files.writeString(out.resolve("results.json"), results.toString(2));
                ImageIO.write(renderer.render(sim, name + " / " + (sim.isComplete() ? sim.getWinner() : "UNRESOLVED") + " / observer view"), "png", out.resolve(name + "-end.png").toFile());
                System.out.println(result.toString());
                if (sim.getWinner() == Faction.MARINE) {
                    assertEquals(2, wave, "victory cannot skip the defense");
                    assertTrue(sim.getDepotHold().isComplete());
                }
                // A mechanic, not an outcome: every militiaman still alive joins the defense. One shot on
                // the way in is the fight's reading (maxMilitiaWithinEightCellsOfHoldAnchor), not a fault.
                // The defense needs the warehouse taken first; a company stopped short of it is a reading.
                if (captureTick >= 0) {
                    assertTrue(everyLivingMilitiaGathered, "every living militiaman must reach the warehouse defense");
                    assertTrue(militiaDefenseShots > 0, "militia must contribute fire during the defense");
                }
                if (recover) assertTrue(handedBack, "the real arriving squad must be able to commission the Hound");
            }
        }
    }
    @Test void militiaWaitWhenCompanyBoatIsDelayedOrAbsentDuringObservation() throws Exception {
        Path out = Path.of("build/reports/commander/depot-hold");
        Files.createDirectories(out);
        JSONArray controls = new JSONArray();
        for (int delay : new int[]{60, 600}) {
            var roster = new MarineRoster(); roster.ensureActiveSoldiers(12);
            var deployment = CampaignMarineDeployment.freeze(roster, 12);
            var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
            try (var sim = BattleSetup.createOpeningOperation(8101, manifest, 1,
                    OpeningOperationKind.COUNTERATTACK, new TargetProfile(0, 0, 0, 0, "", Set.of(),
                    SurfacePalette.VERDANT, SettlementLink.ROAD), true)) {
                deployment.applyTo(sim, 1);
                for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.MARINE)
                    sim.world().mission(air).pendingDelay += delay;
                boolean sawWait = false, sawScreen = false;
                int lossesBeforeReady = 0;
                for (int tick = 0; tick < 3600 && !sim.isComplete(); tick++) {
                    sim.advance(BattleSimulation.TICK_DT);
                    boolean companyReady = sim.getSquads().stream().anyMatch(squad -> squad.faction == Faction.MARINE
                            && squad.aliveMembers > 0 && !SquadFormUpSystem.formingUp(squad));
                    for (var squad : sim.getSquads()) if (squad.faction == Faction.ALLY && squad.aliveMembers > 0) {
                        var order = sim.getSquadCommandDirective(squad.id);
                        if (order != null) {
                            sawWait |= order.reason().equals("DEPOT_WAIT_FOR_COMPANY");
                            sawScreen |= order.reason().equals("DEPOT_SCREEN_COMPANY");
                            if (!companyReady && order.assignment() != null) {
                                assertEquals("DEPOT_WAIT_FOR_COMPANY", order.reason());
                                assertTrue(order.assignment().targetCellX() < 35, "hold the apron; do not assault without the company");
                            }
                        }
                    }
                    if (!companyReady && tick > 600) lossesBeforeReady = Math.max(lossesBeforeReady, 6 - count(sim, Faction.ALLY));
                }
                assertTrue(sawWait);
                assertEquals(delay == 60, sawScreen);
                assertEquals(0, lossesBeforeReady);
                controls.put(new JSONObject().put("playerBoatDelaySeconds", delay).put("observationSeconds", sim.getSimTickIndex() / 30f)
                        .put("sawWait", sawWait).put("sawScreen", sawScreen).put("lossesBeforeCompanyReady", lossesBeforeReady)
                        .put("militiaAlive", count(sim, Faction.ALLY)));
                var renderer = new BattleReviewFrameRenderer(Path.of("mod"), 1920, 1080);
                ImageIO.write(renderer.render(sim, "Company boat delayed " + delay + "s / 120s observation / observer view"),
                        "png", out.resolve("delay-" + delay + ".png").toFile());
            }
        }
        Files.writeString(out.resolve("arrival-controls.json"), controls.toString(2));
    }

    private static int infantry(BattleSimulation sim) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == Faction.MARINE && sim.identity().type(id).usesInfantryTraining()) n++;
        }
        return n;
    }
    private static int count(BattleSimulation sim, Faction faction) {
        int n = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) == faction && sim.identity().type(id).combatant) n++;
        }
        return n;
    }

    private static double nearestMarine(BattleSimulation sim, long hull) {
        if (hull == 0L) return -1;
        var body = sim.convoy().body(hull);
        double best = -1;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.world().hasPosition(id)) continue;
            double d = Math.hypot(sim.world().x(id) - body.x, sim.world().y(id) - body.y);
            if (best < 0 || d < best) best = d;
        }
        return best;
    }
}
