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
        if(p==null||p.claimSecured()||ctx==null||ctx.roster==null||ctx.client==null||ctx.client.locked
                ||ctx.client.patronHouseId!=-1||!"independent".equals(ctx.client.factionId)||!atSite(ctx.planet))return List.of();
        return List.of(offer(p,ctx.planet.getName()));
    }
    public static Mission offer(ShepherdRecoveryProgress p,String planet){
        if(p==null||p.claimSecured())throw new IllegalArgumentException("No open recovery claim");
        return Mission.builder().id(p.missionId()).name("Salvage Rights").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(ShepherdRecoveryProgress.PAYMENT).targetPlanetName(planet).defenderFactionOverride("pirates")
                .flavor(BRIEF+(p.inspectionLosses()>0?" Previous inspection losses: "+p.inspectionLosses()+". Vale has assigned a replacement party; the dead remain on the roll.":""))
                .requirements("Two squads recommended; two with at least four ready marines each required. Actual company boats; optional owned mech support. Eight cooperative militia and two technicians are already at the apron.")
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(14101L).build();
    }
    static ShepherdRecoveryProgress requireCurrent(Mission m){
        var p=current();var d=destination();
        if(!isBattle(m)||p==null||!p.matches(m.id)||d==null||!d.planetName().equals(m.targetPlanetName)||m.payout!=ShepherdRecoveryProgress.PAYMENT)
            throw new IllegalStateException("This recovery contract is no longer current");
        return p;
    }
    static TargetProfile profile(Mission m){requireCurrent(m);return new TargetProfile(0,0,0,0,"pirates",Set.of(),
            SurfaceZoning.forPlanetType(destination().planet().getTypeId()),SettlementLink.ROAD);}
    static void begin(Mission m){var p=requireCurrent(m);if(!p.begin(m.id,m.targetPlanetName))throw new IllegalStateException("Recovery already launched");CampaignStateScript.getInstance().state().shepherdRecovery=p;}
    static int settle(MissionOutcome o){var p=current();if(p==null||o==null||o.missionSource!=MissionSource.STORY||o.salvageRights==null)return -1;
        return p.settle(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,o.salvageRights.crewLost(),o.salvageRights.inspected());}
}
