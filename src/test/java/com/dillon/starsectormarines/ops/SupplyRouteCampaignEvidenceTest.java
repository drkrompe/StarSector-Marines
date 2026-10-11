package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Production construction/roster overlay seam, not a live Starsector campaign. */
@Tag("supply-route-campaign-evidence")
class SupplyRouteCampaignEvidenceTest {
    @Test void launchesAllFourFactoriesWithNamedSeatsAndFittedBoatsWithoutTouchingEmployerLift() throws Exception {
        Path output=Path.of(System.getProperty("supplyroute.command.evidence.outputDir","build/reports/commander/supply-route"));
        Files.createDirectories(output);JSONArray reports=new JSONArray();
        var renderer=new BattleReviewFrameRenderer(Path.of("mod"),1920,1080);
        for(int kind : new int[]{1,2,3,4}) for(int strength : new int[]{8,24})
                for(String liftCase : strength == 8 ? List.of("mixed") : List.of("paired-six", "single-ferry", "mixed")) {
            var route=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");
            if(kind!=1){String id=route.missionId();route.begin(id);route.settle(id,true,null);route.commit(kind == 4 ? 3 : kind);}
            if(kind==4){String id=route.missionId();route.begin(id);route.settle(id,true,null);}
            Mission mission=SupplyRouteCampaign.offers(route).get(0);
            var roster=new MarineRoster();roster.ensureActiveSoldiers(strength);
            if(strength==8) {
                var first=roster.squads().get(0);var second=roster.createSquad();
                for(var soldier:List.copyOf(roster.squadMembers(first)).subList(4,8)) assertTrue(roster.transferSoldier(soldier.id(),second.id()));
            }
            var selected=roster.squads().stream().filter(squad->!squad.reserve()).map(squad->squad.id()).collect(Collectors.toSet());
            assertTrue(SupplyRouteCampaign.forceReady(roster,selected));
            var deployment=CampaignMarineDeployment.freezeSelection(roster,selected,strength);
            var boats=List.of(new FittedBoat(ShuttleType.AEROSHUTTLE,BoatFitting.requireById("reinforced_plating"),BoatFitting.requireById("tuned_drive"),"campaign-alpha"),
                    new FittedBoat(liftCase.equals("paired-six") ? ShuttleType.AEROSHUTTLE : ShuttleType.VALKYRIE,BoatFitting.STANDARD_PLATING,BoatFitting.STANDARD_DRIVE,"campaign-bravo"));
            if (liftCase.equals("single-ferry")) boats = List.of(boats.get(0));
            var manifest=DetachmentResolver.buildShuttleManifestForPersonnel(mission,boats,strength);
            var profile=new TargetProfile(0,0,0,0,"",Set.of(),SurfacePalette.VERDANT,SettlementLink.ROAD);
            try(var sim=MissionLaunch.supplyRouteBuilder(route,9301L,manifest,profile).get()) {
                var untouched=new HashMap<Long,MarineLoadout[]>();
                for(long air:sim.getAirEntityIds()) if(sim.world().airFaction(air)!=Faction.MARINE)
                    untouched.put(air,sim.world().mission(air).marineLoadout);
                deployment.applyToAuthoredPlayerLift(sim);
                Set<String> actual=new HashSet<>();int playerBoats=0;
                for(long air:sim.getAirEntityIds()) {
                    var lift=sim.world().mission(air);
                    if(sim.world().airFaction(air)!=Faction.MARINE){assertSame(untouched.get(air),lift.marineLoadout);continue;}
                    assertSame(manifest.get(lift.manifestOrdinal).airframe,sim.world().airframe(air));playerBoats++;
                    for(var cycle:lift.cycleLoadouts)for(var seat:cycle){assertNotNull(seat.campaignSoldierId);assertNotNull(seat.campaignSquad);assertTrue(actual.add(seat.campaignSoldierId));}
                }
                assertEquals(manifest.size(),playerBoats);assertEquals(strength,actual.size());
                assertEquals(deployment.commitments().stream().map(c->c.campaignSoldierId()).collect(Collectors.toSet()),actual);
                var tags = new HashMap<String, CampaignSquadTag>();
                for (int seat = 0; seat < deployment.size(); seat++) {
                    var loadout = deployment.seat(seat);
                    tags.put(loadout.campaignSoldierId, loadout.campaignSquad);
                }
                var landedIds = new HashSet<String>();
                var battleSquads = new HashMap<String, Set<Integer>>();
                int ticks = 0;
                // Observe every deboard, including later ferry trips, before casualties
                // can hide a bad join. A boat's six seats must not become a new squad.
                while (ticks < 9000 && !sim.isComplete() && landedIds.size() < strength) {
                    sim.advance(BattleSimulation.TICK_DT);
                    ticks++;
                    for (int i = 0; i < sim.liveUnitCount(); i++) {
                        long unit = sim.liveUnitAt(i);
                        if (sim.identity().faction(unit) != Faction.MARINE) continue;
                        String soldier = (String) sim.getEntityWorld().getObject(unit,
                                sim.getBattleComponents().IDENTITY, BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                        assertNotNull(soldier);
                        if (!landedIds.add(soldier)) continue;
                        CampaignSquadTag tag = tags.get(soldier);
                        assertNotNull(tag, "Only committed named personnel may land");
                        assertEquals(tag.fireTeamIndex, sim.squad().fireTeamIndex(unit), "Preserve the roster fire team");
                        int battleSquad = sim.squad().squadId(unit);
                        battleSquads.computeIfAbsent(tag.squadId, ignored -> new HashSet<>()).add(battleSquad);
                        if (tag.leader) assertEquals(unit, sim.getSquads().stream()
                                .filter(s -> s.id == battleSquad).findFirst().orElseThrow().leaderId,
                                "The campaign NCO must lead the reunited squad");
                    }
                }
                String context = mission.name + " / " + strength + " / " + liftCase;
                assertEquals(actual, landedIds, context + ": all named seats must physically arrive");
                var squadReports = new JSONArray();
                for (String campaignSquad : selected) {
                    Set<Integer> ids = battleSquads.get(campaignSquad);
                    assertNotNull(ids, context);
                    assertEquals(1, ids.size(), context + ": one campaign squad must assemble across different berths");
                    var squad = sim.getSquads().stream().filter(s -> ids.contains(s.id)).findFirst().orElseThrow();
                    CampaignSquadTag tag = tags.values().stream().filter(t -> t.squadId.equals(campaignSquad)).findFirst().orElseThrow();
                    assertEquals(tag.strength, squad.originalSize, context);
                    assertEquals(tag.strength, squad.expectedSize, context);
                    assertEquals(tag.squadId, squad.campaignSquadId);
                    assertEquals(tag.label, squad.campaignLabel, "No accidental split-squad suffix");
                    assertEquals(tag.commandGroupIdentity, squad.commandGroupIdentity);
                    assertFalse(SquadFormUpSystem.formingUp(squad), "A fully delivered squad must release its command duty");
                    squadReports.put(new JSONObject().put("campaignSquad", campaignSquad).put("battleSquad", squad.id)
                            .put("delivered", squad.originalSize).put("expected", squad.expectedSize));
                }
                assertEquals(selected.size(), sim.getSquads().stream().filter(s -> s.faction == Faction.MARINE).count(), context);
                String file="operation-"+kind+"-"+strength+"-"+liftCase;
                ImageIO.write(renderer.render(sim,context+" / ALL NAMED SEATS LANDED"),"png",output.resolve(file+".png").toFile());
                reports.put(new JSONObject().put("operation",mission.name).put("namedSeats",actual.size()).put("fittedBoats",playerBoats)
                        .put("employerCraftUntouched",untouched.size()).put("companyLanded",true).put("liftCase",liftCase)
                        .put("arrivalTick",ticks).put("squads",squadReports).put("frame",file+".png"));
            }
        }
        Files.writeString(output.resolve("summary.json"),reports.toString(2));
    }
}
