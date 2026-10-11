package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.battle.air.FittedBoat;
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
    @Test void launchesAllThreeFactoriesWithNamedSeatsAndFittedBoatsWithoutTouchingEmployerLift() throws Exception {
        Path output=Path.of(System.getProperty("supplyroute.command.evidence.outputDir","build/reports/commander/supply-route"));
        Files.createDirectories(output);JSONArray reports=new JSONArray();
        var renderer=new BattleReviewFrameRenderer(Path.of("mod"),1920,1080);
        for(int kind : new int[]{1,2,3}) for(int strength : new int[]{8,24}) {
            var route=new BrokenSupplyRouteProgress("jangala","Jangala","Corvus");
            if(kind!=1){String id=route.missionId();route.begin(id);route.settle(id,true,null);route.commit(kind);}
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
                    new FittedBoat(ShuttleType.VALKYRIE,BoatFitting.STANDARD_PLATING,BoatFitting.STANDARD_DRIVE,"campaign-bravo"));
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
                boolean landed=false;
                for(int tick=0;tick<1500&&!sim.isComplete();tick++) {
                    sim.advance(BattleSimulation.TICK_DT);
                    for(int i=0;i<sim.liveUnitCount();i++)if(sim.identity().faction(sim.liveUnitAt(i))==Faction.MARINE)landed=true;
                }
                assertTrue(landed,"Named company must actually arrive");
                String file="operation-"+kind+"-"+strength;
                ImageIO.write(renderer.render(sim,mission.name+" / CAMPAIGN SEAT BRIDGE / "+strength+" NAMED MARINES / 50s"),"png",output.resolve(file+".png").toFile());
                reports.put(new JSONObject().put("operation",mission.name).put("namedSeats",actual.size()).put("fittedBoats",playerBoats)
                        .put("employerCraftUntouched",untouched.size()).put("companyLanded",landed).put("frame",file+".png"));
            }
        }
        Files.writeString(output.resolve("summary.json"),reports.toString(2));
    }
}
