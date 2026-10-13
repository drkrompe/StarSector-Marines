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
import java.util.List;
import java.util.Set;

/** House Rusk's authored concession beneath the existing Agreus administration. */
public final class AgreusArcCampaign {
    public static final String CONTACT_ID = "marines_elian_rusk", NAME = "Baronet Elian Rusk";
    public static final String TITLE = "A SMALL WAR / NORTH WEIR";
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
        if (p == null || p.completed() || ctx == null || ctx.roster == null || !isContact(ctx.client)
                || ctx.client.locked || ctx.client.patronHouseId != -1 || !atSite(ctx.planet)) return List.of();
        return List.of(offer(p, ctx.planet.getName()));
    }
    public static Mission offer(AgreusArcProgress p, String planetName) {
        if (p == null || p.completed()) throw new IllegalArgumentException("No open North Weir contract");
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
        if (!isBattle(m) || p == null || !p.matches(m.id) || d == null || !d.planetName().equals(m.targetPlanetName)
                || m.payout != AgreusArcProgress.PAYMENT) throw new IllegalStateException("This North Weir contract is no longer current. " + accessReason());
        return p;
    }
    static TargetProfile profile(Mission m) { requireCurrent(m); return new TargetProfile(0, 0, 0, 0, "pirates", Set.of(),
            SurfaceZoning.forPlanetType(destination().planet().getTypeId()), SettlementLink.ROAD); }
    static void begin(Mission m) {
        var p = requireCurrent(m);
        if (!p.begin(m.id, destination().planetId(), m.targetPlanetName)) throw new IllegalStateException("North Weir already launched");
        CampaignStateScript.getInstance().state().agreusArc = p;
    }
    static int settle(MissionOutcome o) {
        var p = current();
        if (p == null || o == null || o.missionSource != MissionSource.STORY || o.waterUnderSeal == null || !owns(o.missionId)
                || !p.acceptsTarget(o.targetPlanetName)) return -1;
        var r = o.waterUnderSeal;
        return p.settle(o.missionId, "agreus", o.victory, o.payoutEarned, r.cargoDelivered(), r.northRoomReconnected(),
                r.southRoomReconnected(), r.turretConstructed(), r.attackResolved(), r.companySurvived(),
                r.techniciansLost(), r.militiaLost(), r.cargoLost());
    }
}
