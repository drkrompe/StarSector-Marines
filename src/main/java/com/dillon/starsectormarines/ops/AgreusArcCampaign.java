package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.dillon.starsectormarines.marine.EquipmentTemplateCardInventory;
import com.dillon.starsectormarines.marine.EquipmentTemplateCardItemPlugin;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import java.util.List;
import java.util.Set;

/** House Rusk's authored concession beneath the existing Agreus administration. */
public final class AgreusArcCampaign {
    public static final String CONTACT_ID = "marines_elian_rusk", NAME = "Baronet Elian Rusk";
    public static final String TITLE = "A SMALL WAR / HOUSE RUSK";
    public static final String WARRANT = "CIVIL-SECURITY PROCUREMENT WARRANT: Recover the bonded service archive and sealed calibration kit at Low Quay. "
            + "On successful return, retain the Recovered Specter refurbishment pattern. Rusk's bond guarantees the supplier's service files, calibration tooling and compatible surplus stock. "
            + "This covers named Greyhook occupations within his concession under Ko Combine administration. It grants no planetary police commission, civilian seizure right, military procurement status or orbital-fire authority. "
            + "Yard management and debt enforcement require a separate agreement; this job chooses neither. Learned patterns remain yours. Deploying accepts these terms; you may leave and return without a deadline.";
    public static final String WARRANT_BRIEF = "Castle: North Weir is working. Rusk now offers 30,000 credits and the Recovered Specter service pattern for the Low Quay recovery. "
            + "Secure the records office, then escort the supplier's technician into the calibration cage. Eight seconds of protected work at each site authenticate the archive and release the kit. "
            + "Cover its marked carrier back to the western egress. The Cask Runners hold the stores and may try to block the loading exit. Four constables cover our approach; bring your working tender and fitted boats. "
            + "Nadi Orr's signed shortage list identifies clinic consignments among the diverted cargo; those belong to the clinic, not our equipment award. "
            + "The stores are sealed; outside we use pressure protection. Losing the calibration load, or the technician before the kit is recovered, fails the job. "
            + "Authenticated records survive withdrawal. A new mobilization funds replacement calibration material and personnel. "
            + "The reward is a cargo card: learn it, then choose an Armory fitting. Nobody is refitted automatically. ";
    public static final String REFERRAL = "Mara Venn: Tamsin Vale sent a reference after Morrow's departure and fleet handover. "
            + "Baronet Elian Rusk needs a company and a working construction tender at North Weir on Agreus, in Arcadia. "
            + "His concession operates under Ko Combine administration. Water Under Seal pays 26,000 credits on victory. "
            + "Replenish your people and fitted boats before leaving Corvus; this optional work has no deadline.";
    public static final String BRIEF = "Castle: Rusk pays for the weir to work when we leave. Tor Edek's technicians handle the sealed coolant switches. "
            + "Escort their repair load, clear both switching rooms and protect the reconnection. Bring your owned tender into safe working range and build a Vulcan road gun. "
            + "Protect its builders while the constables inherit the hold. Expect Greyhook to contest the maintenance road; hold until Edek reports the work secure. "
            + "Agreus is cold and airless: our regular infantry use pressure protection outside the sealed halls. "
            + "Destroyed repair cargo or the loss of both technicians forces withdrawal. Rusk provides a new load and replacement party for a later mobilization. "
            + "This local security contract grants no planetary police commission or orbital-fire authority.";
    private AgreusArcCampaign() { }

    static boolean eligibleHistory(ShepherdRecoveryProgress recovery) { return recovery != null && recovery.departed(); }
    public static AgreusArcProgress current() {
        var script = Global.getSector() == null ? null : CampaignStateScript.getInstance();
        if (script == null || !eligibleHistory(script.state().shepherdRecovery)) return null;
        return script.state().agreusArc == null ? new AgreusArcProgress() : script.state().agreusArc;
    }
    public static boolean owns(String id) { return AgreusArcProgress.owns(id); }
    public static boolean isBattle(Mission m) { return m != null && m.source == MissionSource.STORY && owns(m.id); }
    public static boolean isContact(Client client) { return client != null && CONTACT_ID.equals(client.contactId); }
    public static OpeningBroker.Destination destination() { return current() == null ? null : resolve(Global.getSector()); }
    static OpeningBroker.Destination resolve(SectorAPI sector) {
        if (sector == null) return null;
        var arcadia = sector.getStarSystem("Arcadia");
        if (arcadia == null) return null;
        for (var planet : arcadia.getPlanets()) {
            if (!"agreus".equals(planet.getId()) || planet.isStar()) continue;
            var market = planet.getMarket();
            if (market == null || market.getPlanetEntity() != planet || market.isHidden() || market.getSize() <= 0
                    || market.isPlayerOwned() || !"independent".equals(market.getFactionId()) || market.getFaction() == null
                    || sector.getPlayerFaction() != null && market.getFaction().isHostileTo(sector.getPlayerFaction())) return null;
            return new OpeningBroker.Destination(planet.getId(), planet.getName(), arcadia.getName(), planet);
        }
        return null;
    }
    public static String accessReason() { return destination() == null ? "Agreus / Arcadia access unavailable or its administration has changed; Rusk's contract is paused." : ""; }
    public static boolean atSite(PlanetAPI planet) { var d = destination(); return planet != null && d != null && d.planetId().equals(planet.getId()); }
    public static List<Mission> offers(StoryEligibilityContext ctx) {
        var p = current();
        if (p == null || p.warrantCompleted() || ctx == null || ctx.roster == null || !isContact(ctx.client)
                || ctx.client.locked || ctx.client.patronHouseId != -1 || !atSite(ctx.planet)) return List.of();
        return List.of(offer(p, ctx.planet.getName()));
    }
    public static Mission offer(AgreusArcProgress p, String planetName) {
        if (p == null || p.warrantCompleted()) throw new IllegalArgumentException("No open Agreus contract");
        if (p.completed()) return warrantOffer(p, planetName);
        String losses = p.techniciansLost() + p.militiaLost() + p.repairLoadsLost() == 0 ? ""
                : " Previous confirmed employer losses: " + p.techniciansLost() + " technicians, " + p.militiaLost()
                + " constables and " + p.repairLoadsLost() + " repair loads. This mobilization supplies replacement personnel and cargo.";
        return Mission.builder().id(p.missionId()).name("Water Under Seal").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(BRIEF + losses).requirements("Two squads recommended; two selected squads with at least four ready marines each required. "
                        + "Regular issue is sufficient. Your fitted boats carry your selected marines. You must bring an owned working construction tender. "
                        + "Eight constables and two technicians support a fresh repair load. North Weir / Ko Combine concession.")
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15301L).build();
    }
    static AgreusArcProgress requireCurrent(Mission m) {
        var p = current(); var d = destination();
        if (!isBattle(m) || p == null || !p.matchesNext(m.id) || d == null || !d.planetName().equals(m.targetPlanetName)
                || m.payout != p.nextPayment()) throw new IllegalStateException("This Agreus contract is no longer current. " + accessReason());
        return p;
    }
    private static Mission warrantOffer(AgreusArcProgress p, String planetName) {
        String history = p.recordsRecovered() ? " The service archive is already authenticated; this commission begins with the calibration recovery." : "";
        if (p.warrantTechniciansLost() + p.warrantConstablesLost() + p.calibrationLoadsLost() > 0)
            history += " Previous confirmed losses: " + p.warrantTechniciansLost() + " supplier technicians, "
                    + p.warrantConstablesLost() + " constables and " + p.calibrationLoadsLost() + " calibration loads. Replacement people and calibration stock are funded for this mobilization.";
        return Mission.builder().id(p.nextMissionId()).name("Limited Warrant").source(MissionSource.STORY)
                .type(MissionType.RAID).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.WARRANT_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(WARRANT_BRIEF + history + "\n\n" + WARRANT)
                .requirements("Two full regular squads recommended; two selected squads with at least four ready marines each required. "
                        + "Your fitted boats and owned working tender; four constables and one supplier technician. "
                        + "30,000 credits and one Recovered Specter card on successful recovery, once. No yard agreement.")
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15302L).build();
    }
    static TargetProfile profile(Mission m) { requireCurrent(m); return new TargetProfile(0, 0, 0, 0, "pirates", Set.of(),
            SurfaceZoning.forPlanetType(destination().planet().getTypeId()), SettlementLink.ROAD); }
    static void begin(Mission m) {
        var p = requireCurrent(m);
        if (!p.begin(m.id, destination().planetId(), m.targetPlanetName)) throw new IllegalStateException("Agreus contract already launched");
        CampaignStateScript.getInstance().state().agreusArc = p;
    }
    static int settle(MissionOutcome o) {
        var p = current();
        if (p == null || o == null || o.missionSource != MissionSource.STORY || !owns(o.missionId)
                || !p.acceptsTarget(o.targetPlanetName)) return -1;
        if (AgreusArcProgress.limitedWarrant(o.missionId)) {
            if (o.limitedWarrant == null || MarineRosterScript.getInstance() == null) return -1;
            var unavailable = EquipmentTemplateCardInventory.playerUnavailableTemplateIds();
            return settleWarrant(p, o, Global.getSector().getPlayerFleet().getCargo(), unavailable);
        }
        if (o.waterUnderSeal == null) return -1;
        var r = o.waterUnderSeal;
        return p.settle(o.missionId, "agreus", o.victory, o.payoutEarned, r.cargoDelivered(), r.northRoomReconnected(),
                r.southRoomReconnected(), r.turretConstructed(), r.attackResolved(), r.companySurvived(),
                r.techniciansLost(), r.militiaLost(), r.cargoLost());
    }
    static int settleWarrant(AgreusArcProgress p, MissionOutcome o, CargoAPI cargo, Set<String> unavailable) {
        if (p == null || o == null || cargo == null || unavailable == null || o.limitedWarrant == null
                || o.missionSource != MissionSource.STORY || !AgreusArcProgress.limitedWarrant(o.missionId)
                || !p.acceptsTarget(o.targetPlanetName)) return -1;
        // Validate catalog and cargo before consuming the attempt; ownership is never learned here.
        var card = EquipmentTemplateCardItemPlugin.itemData(AgreusArcProgress.SPECTER_CARD);
        var r = o.limitedWarrant;
        int payment = p.settleWarrant(o.missionId, "agreus", o.victory, o.payoutEarned,
                r.recordsRecovered(), r.kitRecovered(), r.kitReturned(), r.companyReturned(),
                r.cargoLost(), r.techniciansLost(), r.constablesLost());
        if (payment > 0 && !unavailable.contains(AgreusArcProgress.SPECTER_CARD)) cargo.addSpecial(card, 1);
        return payment;
    }
}
