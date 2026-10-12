package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommand;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.CounterattackDepot;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import java.util.Set;
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

/** Opt-in, actual boats and one campaign squad: detour to Wayward versus ordinary advance. */
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
                boolean handedBack = false;
                JSONArray beats = new JSONArray();
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
                        ImageIO.write(renderer.render(sim, name + " / Wayward online / observer view"), "png", out.resolve(name + "-hound.png").toFile());
                    }
                    for (var shot : sim.getShotsThisFrame()) if (shot.shooterId == hound.mechId()) houndShots++;
                    var hold = sim.getDepotHold().progress();
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
                        .put("pendingRaiders", sim.getDepotHold().pendingMembers()).put("beats", beats);
                results.put(result);
                Files.writeString(out.resolve("results.json"), results.toString(2));
                ImageIO.write(renderer.render(sim, name + " / " + (sim.isComplete() ? sim.getWinner() : "UNRESOLVED") + " / observer view"), "png", out.resolve(name + "-end.png").toFile());
                System.out.println(result.toString());
                if (sim.getWinner() == Faction.MARINE) {
                    assertEquals(2, wave, "victory cannot skip the defense");
                    assertTrue(sim.getDepotHold().isComplete());
                }
                if (recover) assertTrue(handedBack, "the real arriving squad must be able to commission Wayward");
            }
        }
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
}
