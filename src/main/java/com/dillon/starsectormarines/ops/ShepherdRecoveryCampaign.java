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
    public static final String BRIEF="Tamsin Vale: The lit buildings are occupied camp structures. The darkness between them is old salvage ground. "
            +"Morrow's crew brought her down in our receiving cradle after the fighting overhead. Armed scrappers moved in after we evacuated. "
            +"The broken Colossus against the rear canyon wall holds the cargo and equipment our shops depend on, with valuable hull scrap around it. That is what the raiders want. We need the yard and its stores back in use. "
            +"Clear the receiving store and repair workshop. My militia will hold the workshop while your company secures the hull's southern service access. "
            +"Keep our two inspection technicians alive and cover their work. Expect a response along the south haul road. "
            +"This secures the recovery claim, not a flying ship. Reopen the pump road and get her out in the later contracts, and the repaired Shepherd is yours. "
            +"Morrow is our smaller salvage tender, your payment in kind for all three contracts. The Colossus, its stores and the fixed yard stay with the cooperative; that broken freighter is no part of the ship transfer. No deadline: rebuild your company before committing.";
    public static final String COLD_BRIEF="Tamsin Vale: Escort our distribution rig from the receiving apron to the workshop. Clear the pump station and cover our technicians while they reconnect shore power; you will see the yard lights come on. Select Build Vulcan Turret and choose clear ground outside Morrow's east bay. Her builders will fly out and assemble the gun. Keep the repair crew covered while they finish the drone workshop. Our small pickets watch the south haul and eastern quarry roads. Support them if you can; if their posts break, cover the survivors back to the inner line. The new gun helps hold that tighter defense while the repairs continue. Completed deliveries and power connections survive a withdrawal. Morrow still needs departure clearance before she joins your fleet. Working Passage follows these repairs.";
    public static final String PASSAGE_BRIEF="Tamsin Vale: Morrow can hover, but the repaired drives need clear ground for ascent. Walk her past the pumps and through the narrows. Clear a position, use Build Vulcan Turret to harden it, then bring the company forward. Our militia will hold the gains. Secure the eastern climb-out yard and cover the drive test. Only physical climb-out transfers the named Shepherd Morrow to your fleet. The agreed berth offer includes willing working crew sufficient for her minimum complement; they join fleet crew, not your marine squads. If she is critically disabled, we recover and refit the same grounded hull for another mobilization. The claim and completed repairs remain yours.";
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
                + "Bring the company back to the yard. Select Build Vulcan Turret and choose clear ground outside Morrow's east bay, then cover the repair crew while they finish the drone workshop. We need a new road gun for this mobilization. Our small pickets still watch both haul roads. Support them or cover their withdrawal to the inner holds; the new gun helps defend the repair line. Morrow remains grounded until departure clearance; Working Passage follows these repairs.";
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
