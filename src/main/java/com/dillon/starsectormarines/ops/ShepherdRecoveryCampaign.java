package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import java.util.List;
import java.util.Set;

/** Basin Reclamation's local contract, introduced by Mara after the Corvus account. */
public final class ShepherdRecoveryCampaign {
    public static final String NAME="Tamsin Vale", TITLE="A WORKING SHIP / BASIN RECLAMATION";
    public static final String BRIEF="Tamsin Vale: The lit buildings are occupied camp structures. The darkness between them is old salvage ground. "
            +"Morrow's crew brought her down in our receiving cradle after the fighting overhead. Armed scrappers moved in after we evacuated. "
            +"Clear the receiving store and repair workshop. My militia will hold the workshop while your company secures the hull's southern service access. "
            +"Keep our two inspection technicians alive and cover their work. Expect a response along the south haul road. "
            +"This secures the recovery claim, not a flying ship. Reopen the pump road and get her out in the later contracts, and the repaired Shepherd is yours. "
            +"The fixed yard stays with the cooperative. No deadline: rebuild your company before committing.";
    public static final String COLD_BRIEF="Tamsin Vale: Escort our distribution rig from the receiving apron to the workshop. Clear the pump station and cover our technicians while they reconnect shore power; you will see the yard lights come on. Select Build Vulcan Turret and choose clear ground outside Morrow's east bay. Her builders will fly out and assemble the gun. Keep the repair crew covered while they finish the drone workshop. Scrappers will come down the south haul and eastern quarry roads. Completed deliveries and power connections survive a withdrawal. Morrow still needs departure clearance before she joins your fleet. Working Passage is not yet available.";
    private ShepherdRecoveryCampaign(){}
    public static ShepherdRecoveryProgress current(){
        var script=Global.getSector()==null?null:CampaignStateScript.getInstance();
        if(script==null||script.state().corvusWayline==null||!script.state().corvusWayline.aftermathAcknowledged())return null;
        return script.state().shepherdRecovery==null?new ShepherdRecoveryProgress():script.state().shepherdRecovery;
    }
    public static boolean owns(String id){return ShepherdRecoveryProgress.owns(id);}
    public static boolean isBattle(Mission mission){return mission!=null&&mission.source==MissionSource.STORY&&owns(mission.id);}
    public static OpeningBroker.Destination destination(){
        if(current()==null||Global.getSector()==null)return null;
        var corvus=Global.getSector().getStarSystem("Corvus");
        if(corvus==null)return null;
        for(var planet:corvus.getPlanets())if("asharu".equals(planet.getId())&&!planet.isStar())
            return new OpeningBroker.Destination(planet.getId(),planet.getName(),corvus.getName(),planet);
        return null;
    }
    public static boolean atSite(PlanetAPI planet){var d=destination();return planet!=null&&d!=null&&planet.getId().equals(d.planetId());}
    public static List<Mission> offers(StoryEligibilityContext ctx){
        var p=current();
        if(p==null||p.repaired()||ctx==null||ctx.roster==null||ctx.client==null||ctx.client.locked
                ||ctx.client.patronHouseId!=-1||!"independent".equals(ctx.client.factionId)||!atSite(ctx.planet))return List.of();
        return List.of(offer(p,ctx.planet.getName()));
    }
    public static Mission offer(ShepherdRecoveryProgress p,String planet){
        if(p==null||p.repaired())throw new IllegalArgumentException("No open recovery claim");
        return Mission.builder().id(p.missionId()).name(p.claimSecured()?"Cold Start":"Salvage Rights").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(p.payment()).targetPlanetName(planet).defenderFactionOverride("pirates")
                .flavor((p.claimSecured()?repairBrief(p):BRIEF)+(p.inspectionLosses()>0?" Previous inspection losses: "+p.inspectionLosses()+". Vale has assigned a replacement party; the dead remain on the roll.":""))
                .requirements((p.claimSecured()?(p.cargoDelivered()?"Distribution rig delivered. ":"Distribution rig awaiting escort. ")+(p.shorePower()?"Yard lights and shore power online. ":"Shore power disconnected. ")+(p.repairLosses()>0?"Confirmed repair crew losses: "+p.repairLosses()+". A replacement party is assigned. ":""):"")+"Two squads recommended; two with at least four ready marines each required. Company boats carry your selected squads; an owned mech is optional. Eight cooperative militia and two technicians meet you at the apron.")
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(14101L).build();
    }
    private static String repairBrief(ShepherdRecoveryProgress progress) {
        if (!progress.cargoDelivered()) return COLD_BRIEF;
        return "Tamsin Vale: The distribution rig is still at the workshop. "
                + (progress.shorePower()?"Shore power is holding and Morrow's builders are ready. ":"Cover the pump crew while they restore shore power. ")
                + "Bring the company back to the yard. Select Build Vulcan Turret and choose clear ground outside Morrow's east bay, then cover the repair crew while they finish the drone workshop. We need a new road gun for this mobilization. Scrappers still use both haul roads. Morrow remains grounded until departure clearance; Working Passage is not yet available.";
    }
    static ShepherdRecoveryProgress requireCurrent(Mission m){
        var p=current();var d=destination();
        if(!isBattle(m)||p==null||!p.matches(m.id)||d==null||!d.planetName().equals(m.targetPlanetName)||m.payout!=p.payment())
            throw new IllegalStateException("This recovery contract is no longer current");
        return p;
    }
    static TargetProfile profile(Mission m){requireCurrent(m);return new TargetProfile(0,0,0,0,"pirates",Set.of(),
            SurfaceZoning.forPlanetType(destination().planet().getTypeId()),SettlementLink.ROAD);}
    static void begin(Mission m){var p=requireCurrent(m);if(!p.begin(m.id,m.targetPlanetName))throw new IllegalStateException("Recovery already launched");CampaignStateScript.getInstance().state().shepherdRecovery=p;}
    static int settle(MissionOutcome o){var p=current();if(p==null||o==null||o.missionSource!=MissionSource.STORY||(!ShepherdRecoveryProgress.coldStart(o.missionId)&&o.salvageRights==null))return -1;
        if(ShepherdRecoveryProgress.coldStart(o.missionId)){
            if(o.coldStart==null || o.salvageRights!=null)return -1;
            var r=o.coldStart;return p.settleColdStart(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,r.crewLost(),r.cargoDelivered(),r.shorePower(),r.turretConstructed(),r.repaired());
        }
        if(o.coldStart!=null)return -1;
        return p.settle(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,o.salvageRights.crewLost(),o.salvageRights.inspected());}
}
