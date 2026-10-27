package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Basin Reclamation's local contract, introduced by Zoey after the Corvus account. */
public final class ShepherdRecoveryCampaign {
    public static final String NAME="Tamsin Vale", TITLE="A WORKING SHIP / BASIN RECLAMATION";
    public static final List<MissionDialogue.Turn> BRIEF_TURNS = List.of(
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageSchedules", "basin_salvage_schedules_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageWeWere", "basin_salvage_we_were_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageCareful", "basin_salvage_careful_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageNotWayline", "basin_salvage_not_wayline_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageWreckfall", "basin_salvage_wreckfall_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageLongTime", "basin_salvage_long_time_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageCamp", "basin_salvage_camp_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageWordQuestion", "basin_salvage_word_question_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageFuelOrder", "basin_salvage_fuel_order_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageSchedulesSold", "basin_salvage_schedules_sold_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageOccupation", "basin_salvage_occupation_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageSurvivors", "basin_salvage_survivors_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageCousin", "basin_salvage_cousin_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvagePermitQuestion", "basin_salvage_permit_question_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvagePermit", "basin_salvage_permit_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageScopeQuestion", "basin_salvage_scope_question_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageHandlers", "basin_salvage_handlers_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdSalvageCradleQuestion", "basin_salvage_cradle_question_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageShepherd", "basin_salvage_shepherd_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdSalvageOrders", "basin_salvage_orders_01"));
    public static String brief() { return MissionDialogue.transcript(BRIEF_TURNS); }
    public static final List<MissionDialogue.Turn> COLD_TURNS = List.of(
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdColdRepairTerms", "basin_cold_repair_terms_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdColdRoadsQuestion", "basin_cold_roads_question_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdColdCheckpoints", "basin_cold_checkpoints_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdColdDayHands", "basin_cold_day_hands_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdColdCheap", "basin_cold_cheap_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdColdCheckpointWorth", "basin_cold_checkpoint_worth_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdColdPastQuarry", "basin_cold_past_quarry_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdColdBuildersQuestion", "basin_cold_builders_question_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdColdBuilders", "basin_cold_builders_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdColdCompanyCover", "basin_cold_company_cover_01"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdColdPickets", "basin_cold_pickets_01"));
    public static final List<MissionDialogue.Turn> PASSAGE_TURNS = List.of(
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation1", "basin_passage_negotiation_01"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation2", "basin_passage_negotiation_02"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation3", "basin_passage_negotiation_03"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation4", "basin_passage_negotiation_04"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation5", "basin_passage_negotiation_05"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation6", "basin_passage_negotiation_06"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation7", "basin_passage_negotiation_07"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation8", "basin_passage_negotiation_08"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation9", "basin_passage_negotiation_09"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation10", "basin_passage_negotiation_10"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation11", "basin_passage_negotiation_11"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation12", "basin_passage_negotiation_12"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation13", "basin_passage_negotiation_13"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation14", "basin_passage_negotiation_14"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation15", "basin_passage_negotiation_15"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation16", "basin_passage_negotiation_16"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation17", "basin_passage_negotiation_17"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation18", "basin_passage_negotiation_18"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation19", "basin_passage_negotiation_19"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation20", "basin_passage_negotiation_20"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation21", "basin_passage_negotiation_21"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation22", "basin_passage_negotiation_22"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation23", "basin_passage_negotiation_23"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation24", "basin_passage_negotiation_24"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation25", "basin_passage_negotiation_25"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation26", "basin_passage_negotiation_26"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation27", "basin_passage_negotiation_27"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation28", "basin_passage_negotiation_28"),
            new MissionDialogue.Turn("castle", "@dialogueSpeakerCastle", "@shepherdPassageNegotiation29", "basin_passage_negotiation_29"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageNegotiation30", "basin_passage_negotiation_30"),
            new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageInstructions", "basin_passage_instructions_01"));
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
        return Mission.builder().id(p.missionId()).name(p.repaired()?Strings.get("shepherdMissionPassage"):p.claimSecured()?Strings.get("shepherdMissionCold"):Strings.get("shepherdMissionSalvage")).source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(p.payment()).targetPlanetName(planet).defenderFactionOverride("pirates")
                .dialogue(briefingTurns(p))
                .requirements(requirements(p))
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(p.claimSecured()&&!p.repaired()?MarineArrivalPolicy.SECURED_GROUND:MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(14101L).build();
    }
    private static String requirements(ShepherdRecoveryProgress progress) {
        if (progress.repaired()) return Strings.format("shepherdPassageStatus", progress.departureLosses())
                + Strings.get("shepherdPassageTerms") + Strings.get("shepherdForceRequirement")
                + Strings.get("shepherdPassageDeployment");
        if (!progress.claimSecured()) return Strings.get("shepherdSalvageTerms")
                + Strings.get("shepherdForceRequirement") + Strings.get("shepherdInspectionDeployment");
        return Strings.get(progress.cargoDelivered() ? "shepherdColdStatusDelivered" : "shepherdColdStatusUndelivered")
                + Strings.get(progress.shorePower() ? "shepherdColdStatusPowered" : "shepherdColdStatusUnpowered")
                + (progress.repairLosses() > 0 ? Strings.format("shepherdColdReplacementParty", progress.repairLosses()) : "")
                + Strings.get("shepherdColdTerms") + Strings.get("shepherdForceRequirement")
                + Strings.get("shepherdColdDeployment");
    }
    private static List<MissionDialogue.Turn> briefingTurns(ShepherdRecoveryProgress progress) {
        var turns = new ArrayList<>(progress.repaired() ? (progress.departureFailures() > 0 ? List.of(new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", "@shepherdPassageRetry")) : PASSAGE_TURNS)
                : progress.claimSecured() ? (progress.cargoDelivered()
                        ? List.of(new MissionDialogue.Turn("vale", "@dialogueSpeakerVale", repairBrief(progress)))
                        : COLD_TURNS) : BRIEF_TURNS);
        if (progress.inspectionLosses() > 0) turns.add(MissionDialogue.prose(
                Strings.format("shepherdInspectionLosses", progress.inspectionLosses())));
        return List.copyOf(turns);
    }
    private static String repairBrief(ShepherdRecoveryProgress progress) {
        return progress.shorePower() ? "@shepherdColdRetryPowered" : "@shepherdColdRetryUnpowered";
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
            int paid=settlePassage(p,o,ship==null?null:new FleetAward() {
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
            if(paid>=0){var site=destination();
                ArcIssueListings.afterLive(ArcIssueListings.Beat.WORKING_PASSAGE,o.victory,site==null?null:site.planet());}
            return paid;
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
