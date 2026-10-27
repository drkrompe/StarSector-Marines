package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusDistrictEscortProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;

import java.util.List;
import java.util.Set;

/**
 * Campaign boundary for District Escort, the repeatable job after *A Small War*. Projection never
 * starts an attempt; only launch begins one and only its own receipt settles it. The issuing office
 * changes the letterhead and the party's names, never the support, fee or terms.
 */
public final class DistrictEscortCampaign {
    public static final String LENNA_NAME = "Lenna Saye";
    private DistrictEscortCampaign() { }

    public static String name() { return Strings.get("districtEscortCampaignName"); }
    public static String terms() { return Strings.format("districtEscortCampaignTerms", AgreusDistrictEscortProgress.PAYMENT); }

    /** How the issuing office writes; identical support behind either letterhead. */
    public enum Letterhead {
        RUSK_OFFICE("districtEscortCampaignRusk", AgreusArcCampaign.NAME),
        LENNA_ASSOCIATION("districtEscortCampaignLenna", LENNA_NAME);

        public final String signatory;
        private final String textKey;
        Letterhead(String textKey, String signatory) {
            this.textKey = textKey; this.signatory = signatory;
        }
        public String sender() { return Strings.get(textKey + "Sender"); }
        public String partyName() { return Strings.get(textKey + "Party"); }
        public String brief(String consignment) { return Strings.format(textKey + "Brief", consignment); }
        public static Letterhead of(AgreusYardAgreement agreement) {
            return agreement == AgreusYardAgreement.HOUSE_CUSTODY ? RUSK_OFFICE : LENNA_ASSOCIATION;
        }
    }

    /** The open ledger, or null while *A Small War* has not settled under a ratified agreement. */
    public static AgreusDistrictEscortProgress current() {
        var arc = AgreusArcCampaign.current();
        if (!AgreusDistrictEscortProgress.offerable(arc)) return null;
        var script = CampaignStateScript.getInstance();
        if (script == null) return null;
        return resolve(arc, script.state().agreusDistrictEscort);
    }
    /** A stored ledger must belong to the ratified route; otherwise a preview ledger is offered. */
    static AgreusDistrictEscortProgress resolve(AgreusArcProgress arc, AgreusDistrictEscortProgress stored) {
        if (!AgreusDistrictEscortProgress.offerable(arc)) return null;
        if (stored == null) return new AgreusDistrictEscortProgress(arc.signedAgreement());
        return stored.agreement() == arc.signedAgreement() ? stored : null;
    }
    public static boolean owns(String id) { return AgreusDistrictEscortProgress.owns(id); }
    public static boolean isBattle(Mission m) { return m != null && m.source == MissionSource.RECURRING && owns(m.id); }
    public static boolean isContact(Client client) {
        var p = current();
        return p != null && isContact(p, client);
    }
    static boolean isContact(AgreusDistrictEscortProgress p, Client client) {
        return p != null && client != null && p.contactId().equals(client.contactId);
    }
    /** Lenna replaces Dormer as the Agreus contact once the compact's escort work is open. */
    public static boolean lennaContact() {
        var p = current();
        return p != null && AgreusYardAgreement.LENNA_ASSOCIATION.equals(p.contactId());
    }

    public static List<Mission> offers(StoryEligibilityContext ctx) {
        var p = current();
        if (p == null || ctx == null || ctx.roster == null || !isContact(p, ctx.client) || ctx.client.locked
                || ctx.client.patronHouseId != -1 || !AgreusArcCampaign.atSite(ctx.planet)) return List.of();
        return List.of(offer(p, ctx.planet.getName()));
    }
    public static Mission offer(AgreusDistrictEscortProgress p, String planetName) {
        if (p == null || p.active()) throw new IllegalArgumentException("No open District Escort posting");
        var letterhead = Letterhead.of(p.agreement());
        String history = p.jobsCompleted() + p.partyLost() + p.consignmentsLost() == 0 ? ""
                : Strings.format("districtEscortCampaignHistory", p.status());
        return Mission.builder().id(p.missionId()).name(name()).source(MissionSource.RECURRING)
                .type(MissionType.RAID).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusDistrictEscortProgress.PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(Strings.join("waylineSentenceJoin", letterhead.brief(p.consignmentId()), history) + "\n\n" + terms())
                .requirements(Strings.get("districtEscortCampaignRequirements"))
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                .battleSeed(15400L + 7919L * p.job() + p.attempt()).build();
    }

    static AgreusDistrictEscortProgress requireCurrent(Mission m) {
        var p = current(); var d = AgreusArcCampaign.destination();
        if (!isBattle(m) || p == null || !p.matches(m.id) || d == null || !d.planetName().equals(m.targetPlanetName)
                || m.payout != AgreusDistrictEscortProgress.PAYMENT)
            throw new IllegalStateException("This District Escort posting is no longer current. " + AgreusArcCampaign.accessReason());
        return p;
    }
    static String partyName(Mission m) { return Letterhead.of(requireCurrent(m).agreement()).partyName(); }
    static TargetProfile profile(Mission m) {
        requireCurrent(m);
        return new TargetProfile(0, 0, 0, 0, "pirates", Set.of(),
                SurfaceZoning.forPlanetType(AgreusArcCampaign.destination().planet().getTypeId()), SettlementLink.ROAD);
    }
    static void begin(Mission m) {
        var p = requireCurrent(m);
        if (!p.begin(m.id, AgreusArcCampaign.destination().planetId(), m.targetPlanetName))
            throw new IllegalStateException("District Escort already launched");
        CampaignStateScript.getInstance().state().agreusDistrictEscort = p;
    }
    static int settle(MissionOutcome o) {
        var script = Global.getSector() == null ? null : CampaignStateScript.getInstance();
        return settle(script == null ? null : script.state().agreusDistrictEscort, o);
    }
    /** Writes only the escort ledger: no card, premium, ratification or standing. */
    static int settle(AgreusDistrictEscortProgress p, MissionOutcome o) {
        if (p == null || o == null || o.districtEscort == null || o.missionSource != MissionSource.RECURRING
                || !owns(o.missionId) || !p.acceptsTarget(o.targetPlanetName)) return -1;
        var r = o.districtEscort;
        return p.settle(o.missionId, "agreus", o.victory, o.payoutEarned, r.linkedUp(), r.cargoDelivered(),
                r.partyDelivered(), r.companyAtEgress(), r.cargoLost(), r.partyLost(), r.constablesLost());
    }

    /** Results-screen dispatch: factual, attributable to the issuing office. */
    static String debrief(MissionOutcome o, AgreusYardAgreement agreement) {
        var letterhead = Letterhead.of(agreement);
        return Strings.format(o.victory ? "districtEscortCampaignDebriefWon" : "districtEscortCampaignDebriefLost",
                letterhead.signatory, o.payoutEarned);
    }
}
