package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusDistrictEscortProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
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
    public static final String NAME = "District Escort";
    public static final String LENNA_NAME = "Lenna Saye";
    public static final String TERMS = "Terms: 24,000 credits once, on delivery of this consignment with its party and your company at the western apron. "
            + "No equipment award, premium, house promotion, MRB standing, stationing contract or faction commission; the signed agreement and its ratification are unchanged. "
            + "Four district constables hold the apron. Two squads recommended; your fitted boats carry your selected marines. "
            + "If the consignment or both of its party are lost, or the company withdraws, the job is unpaid and the next posting is a new consignment with new people.";
    private DistrictEscortCampaign() { }

    /** How the issuing office writes; identical support behind either letterhead. */
    public enum Letterhead {
        RUSK_OFFICE("OFFICE OF BARONET ELIAN RUSK / CONCESSION SECURITY", AgreusArcCampaign.NAME,
                "Rusk custodial clerk",
                "Rusk's office: Under the custody agreement, the concession ships its own freight on its own schedule. "
                        + "Consignment %s leaves the Low Quay loading lane for the western apron with two of my custodial clerks, who sign it over at the gate. "
                        + "Raiders still work the freight lane; two pickets have been seen on it and more will come onto the lane once your boats are down. "
                        + "Meet the carrier, then keep it covered. My managers expect it delivered, not defended in place."),
        LENNA_ASSOCIATION("LATCHKEY YARD ASSOCIATION / COMMON SERVICE DESK", LENNA_NAME,
                "Association service delegate",
                "Lenna Saye: The compact lets the benches ship common-service work without asking Rusk's office for a lease. "
                        + "Consignment %s is pump parts and recovered stock for the weir and the yard; two of our delegates travel with it and sign it over at the western apron. "
                        + "Raiders still work the freight lane. Two pickets sit on it, and more will come onto the lane once your boats are down. "
                        + "Meet the carrier at the loading lane and get it, and our people, across.");

        public final String sender, signatory, partyName;
        private final String brief;
        Letterhead(String sender, String signatory, String partyName, String brief) {
            this.sender = sender; this.signatory = signatory; this.partyName = partyName; this.brief = brief;
        }
        public String brief(String consignment) { return String.format(brief, consignment); }
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
    /** Lenna replaces Rusk as the Agreus contact once the compact's escort work is open. */
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
                : " Escort record: " + p.status() + ". This posting is a new consignment with new people.";
        return Mission.builder().id(p.missionId()).name(NAME).source(MissionSource.RECURRING)
                .type(MissionType.RAID).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusDistrictEscortProgress.PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(letterhead.brief(p.consignmentId()) + history + "\n\n" + TERMS)
                .requirements("Two squads recommended; two selected squads with at least four ready marines each required. "
                        + "Up to four fitted company boats. Four constables and a two-person handover party. No tender or mech required.")
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
    static String partyName(Mission m) { return Letterhead.of(requireCurrent(m).agreement()).partyName; }
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
        if (o.victory) return letterhead.signatory + ": The consignment is signed over at the apron. "
                + String.format(Strings.locale(), "%,d", o.payoutEarned) + " credits paid. The next load is new freight and new people; post again when the company is ready.";
        return letterhead.signatory + ": The consignment did not arrive. No fee. Confirmed losses stay on record; "
                + "the next posting is a new consignment with new people, not this load retried.";
    }
}
