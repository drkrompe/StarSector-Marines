package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import java.util.List;
import java.util.Set;

/** Basin Reclamation's local contract, introduced by Mara after the Corvus account. */
public final class ShepherdRecoveryCampaign {
    public static final String NAME="Tamsin Vale", TITLE="A WORKING SHIP / BASIN RECLAMATION";
    public static final String BRIEF="Tamsin Vale: Most of what I need is still in that Colossus, Captain. Pumps, machine tools, stock for the shops. We had to leave it all when the shooting started. There's another crew inside now. They say we abandoned it. I was getting our people out. "
            +"Morrow's the Shepherd in the cradle. Her crew got her home, but she won't be going anywhere without some work. Get us back into the yard, see her repaired and bring her out, and she's yours. "
            +"Our permit's in order. I'd still rather have those holds emptied before a Hegemony inspector wants a look. Easier to settle this while it's just us. "
            +"Start with the receiving store, then the workshop. I'll have militia follow you in. Our two technicians need a clear way to Morrow's southern service access and someone to watch their backs while they inspect her. Keep an eye on the south haul road; that's how the scrappers have been getting in. "
            +"Get the technicians in. They'll tell us what we've got to work with.";
    public static final String COLD_BRIEF="Tamsin Vale: I've got the distribution rig waiting at the apron. Get it to the workshop, then clear the pump station for our technicians. Once they've connected shore power, we'll have the yard lights and Morrow's drone bay back. "
            +"There's ground outside her east bay for a road gun. Select Build Vulcan Turret and choose a clear spot the builders can reach. Keep them covered while they work; we'll need that gun while the fitters finish inside. "
            +"I've only a few people on the south haul and quarry roads. Don't leave them out there alone if the scrappers push through. Help them back to the inner line. "
            +"If we have to stop, we won't have to redo a finished delivery or power connection. I'd rather send for another repair party than lose this one trying to hurry.";
    public static final String PASSAGE_BRIEF="Tamsin Vale: She's holding a hover. That's as far as I'm willing to trust her in the cradle. Bring her out along the road; there's room to test the drives in the eastern yard. "
            +"Clear the pump bend and give her builders a place to put a gun. Select Build Vulcan Turret on clear ground within reach of the ship. Then do the same at the narrows. I'll send holders in behind you; I need those turns clear for our haulers. Keep the company with her until the drive test is finished and she's away. "
            +"Once she's clear, Castle can finish the transfer. We'll make up her crew from the hands who want to stay with her. If the drives give out, break off. We'll bring her back and put her right before you try again.";
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
        if(p==null||p.departed()||ctx==null||ctx.roster==null||ctx.client==null||ctx.client.locked
                ||ctx.client.patronHouseId!=-1||!"independent".equals(ctx.client.factionId)||!atSite(ctx.planet))return List.of();
        return List.of(offer(p,ctx.planet.getName()));
    }
    public static Mission offer(ShepherdRecoveryProgress p,String planet){
        if(p==null||p.departed())throw new IllegalArgumentException("No open recovery claim");
        return Mission.builder().id(p.missionId()).name(p.repaired()?"Working Passage":p.claimSecured()?"Cold Start":"Salvage Rights").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(p.payment()).targetPlanetName(planet).defenderFactionOverride("pirates")
                .flavor((p.repaired()?PASSAGE_BRIEF:p.claimSecured()?repairBrief(p):BRIEF)+(p.inspectionLosses()>0?" Previous inspection losses: "+p.inspectionLosses()+". Vale has assigned a replacement party; the dead remain on the roll.":""))
                .requirements((p.repaired()?"Repaired hull ready for departure. Confirmed previous militia losses: "+p.departureLosses()+". Vale supplies new cooperative holders. ":p.claimSecured()?(p.cargoDelivered()?"Distribution rig delivered. ":"Distribution rig awaiting escort. ")+(p.shorePower()?"Yard lights and shore power online. ":"Shore power disconnected. ")+(p.repairLosses()>0?"Confirmed repair crew losses: "+p.repairLosses()+". A replacement party is assigned. ":""):"")+"Two squads recommended; two with at least four ready marines each required. "+(p.claimSecured()&&!p.repaired()?"Your selected squads begin on the secured receiving apron; company boats stay in reserve. ":"Company boats carry your selected squads. ")+"An owned mech is optional. "+(p.claimSecured()&&!p.repaired()?"Fourteen cooperative militia support the operation: eight inner holders and two three-person outer pickets. ":"Eight cooperative militia support the operation. ")+"Inspection and repair chapters also include two technicians.")
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(p.claimSecured()&&!p.repaired()?MarineArrivalPolicy.SECURED_GROUND:MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(14101L).build();
    }
    private static String repairBrief(ShepherdRecoveryProgress progress) {
        if (!progress.cargoDelivered()) return COLD_BRIEF;
        return "Tamsin Vale: The distribution rig is still at the workshop. "
                + (progress.shorePower()?"Shore power is holding and Morrow's builders are ready. ":"Cover the pump crew while they restore shore power. ")
                + "I've put another party together. Same terms, Castle; you haven't lost your claim on her. Bring the company back when you've had time to replace what you need. "
                + "We'll need a new road gun. Select Build Vulcan Turret and choose clear ground outside Morrow's east bay, then keep the fitters covered while they finish the workshop. Watch our pickets on both haul roads. If they have to come in, help them back to the inner line.";
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
    static int settle(MissionOutcome o){var p=current();if(p==null||o==null||o.missionSource!=MissionSource.STORY||(!ShepherdRecoveryProgress.workingPassage(o.missionId)&&!ShepherdRecoveryProgress.coldStart(o.missionId)&&o.salvageRights==null))return -1;
        if (ShepherdRecoveryProgress.workingPassage(o.missionId)) {
            if(o.workingPassage==null || o.coldStart!=null || o.salvageRights!=null || Global.getSector()==null || Global.getSector().getPlayerFleet()==null)return -1;
            if(!p.matches(o.missionId))return -1;
            var r=o.workingPassage;
            FleetMemberAPI ship=o.victory?Global.getFactory().createFleetMember(FleetMemberType.SHIP,"shepherd_Frontier"):null;
            int crew=ship==null?0:(int)Math.ceil(ship.getMinCrew());
            return settlePassage(p,o,ship==null?null:new FleetAward() {
                public String memberId(){return ship.getId();}
                public int minimumCrew(){return crew;}
                public void transfer(){
                    var fleet=Global.getSector().getPlayerFleet();
                    ship.setShipName("Morrow");
                    fleet.getFleetData().addFleetMember(ship);
                    try { fleet.getCargo().addCrew(crew); }
                    catch(RuntimeException failure){fleet.getFleetData().removeFleetMember(ship);throw failure;}
                }
            });
        }
        if(o.workingPassage!=null)return -1;
        if(ShepherdRecoveryProgress.coldStart(o.missionId)){
            if(o.coldStart==null || o.salvageRights!=null)return -1;
            var r=o.coldStart;return p.settleColdStart(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,r.crewLost(),r.cargoDelivered(),r.shorePower(),r.turretConstructed(),r.repaired());
        }
        if(o.coldStart!=null)return -1;
        return p.settle(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,o.salvageRights.crewLost(),o.salvageRights.inspected());}

    interface FleetAward {
        String memberId();
        int minimumCrew();
        void transfer();
    }
    /** Validates the frozen receipt before fleet writes; closes it only after delivery. */
    static int settlePassage(ShepherdRecoveryProgress p, MissionOutcome o, FleetAward award) {
        if(p==null || o==null || o.missionSource!=MissionSource.STORY || o.workingPassage==null)return -1;
        var r=o.workingPassage;
        String identity=award==null?null:award.memberId();
        int crew=award==null?0:award.minimumCrew();
        if(!p.acceptsWorkingPassage(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,
                r.militiaLost(),r.departed(),r.disabled(),r.aborted(),identity,crew))return -1;
        if(award!=null)award.transfer();
        return p.settleWorkingPassage(o.missionId,o.targetPlanetName,o.victory,o.payoutEarned,
                r.militiaLost(),r.departed(),r.disabled(),r.aborted(),identity,crew);
    }
}
