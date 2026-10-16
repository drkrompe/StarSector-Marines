package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.campaign.AgreusYardAgreement;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
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
import com.dillon.starsectormarines.marine.LoadoutCardInventory;
import com.dillon.starsectormarines.marine.LoadoutCardItemPlugin;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

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
            + "Cover its marked carrier back to the western egress. Drent Haulage crews hold the stores and may try to block the loading exit. Four constables cover our approach; bring your working tender and fitted boats. "
            + "Nadi Orr's signed shortage list identifies clinic consignments among the diverted cargo; those belong to the clinic, not our equipment award. "
            + "The stores are sealed; outside we use pressure protection. Losing the calibration load, or the technician before the kit is recovered, fails the job. "
            + "Authenticated records survive withdrawal. A new mobilization funds replacement calibration material and personnel. "
            + "The reward is a cargo card: learn it, then choose an Armory fitting. Nobody is refitted automatically. ";
    public static final String YARD_TERMS = "INSPECTION ADDENDUM: Secure Latchkey's dispatch house, tooling hall and operator entrance. Preserve the marked calibration machine. "
            + "On successful inspection, retain the Recovered Arbalest refurbishment pattern under Rusk's supplier guarantee. "
            + "No bombardment or fighter strikes: the machines, homes and heating service must survive. Clearing Greyhook does not authorize civilian seizure, new leases or debt collection. "
            + "Custody remains unsigned pending a separate agreement. Deploying accepts this inspection only; the offer has no deadline.";
    public static final String YARD_BRIEF = "Castle: Rusk offers 36,000 credits and the Recovered Arbalest service pattern for a working inspection at Latchkey Yard. "
            + "Secure dispatch first. The constables will hold our gains while we clear the tooling hall and operator entrance. Six seconds of uncontested company presence secures each site; enemy contact can take it back. "
            + "Hold all three together while a living technician completes twelve uninterrupted seconds at the tools, covered by company infantry. The marked calibration machine must survive ordinary fire and blast damage. "
            + "Lenna Saye has supplied machine labels and a safe route from the housing frontage. Voss's prize crews hold the yard and have a finite counterattack; workers are not targets. "
            + "Bring three full squads if possible, fitted boats and your working tender. Rusk funds heavy lift for your active owned mech lance if available; no walker is supplied. "
            + "Recovered Specter is useful, but regular issue can do this job. Sealed halls and pressure protection suit Agreus's airless surface. "
            + "Lost tooling or both inspection technicians ends this attempt. A later mobilization explicitly funds replacement personnel and tooling refit, with the losses kept on record. ";
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
    public static final String SMALL_WAR_TERMS = "HANDOVER TERMS: Seize Black Sluice's customs block, fuel-control house and dispatch pier, and hand the site to the agreed local custody party at fuel control. "
            + "The marked fuel storage is a preservation objective: its controls are a seizure target, not something to destroy. No bombardment or fighter strikes. "
            + "This breaks one Greyhook surface foothold and its levy route; it is not a commission against every pirate in Arcadia. Deploying accepts these terms; the offer has no deadline.";
    public static final String SMALL_WAR_BRIEF = "Castle: The lead from the Latchkey handover holds. Pell's recovered levy files carry a Drent Haulage dispatch order signed by Mika Drent. Its load numbers match Nadi Orr's shortage report and name Black Sluice's transfer schedule; local pilots confirm the service approach. "
            + "Rusk offers 44,000 credits to take that foothold and hand it to local holders. "
            + "Seize the causeway customs block first and build a rear position there with our tender; the holders occupy what we actually take. "
            + "Then fuel control. The causeway runs between walled settling beds with no cover, and Drent Haulage works that transfer road. A support gun covers it; the sealed service galleries north and south are the way round. "
            + "Prize crews and Berth Four cover dispatch. Take it and its transfer records, reclaim anything we lose, then escort the custody party into fuel control and hold all three sites while they complete the handover. "
            + "The marked fuel tanks must survive ordinary fire. Agreus is airless: pressure protection outside the sealed halls. "
            + "Losing the marked fuel infrastructure or the whole custody party fails the attempt; a later mobilization is a disclosed replacement and refit, with the loss kept on record. ";
    /** Shared by both agreements; neither text may imply otherwise. */
    public static final String AGREEMENT_COMMON = "Both agreements keep Rusk's secured title under Combine administration, the yard's enforceable debt, "
            + "the same core equipment rewards and the company's duty to remove Greyhook's coercive presence. "
            + "Chapter 4, Terms of Protection, pays 32,000 credits and a Boarding Jack section card on either route; victory ratifies the signed agreement.";
    public static final String CUSTODY_UNDERTAKING = "Escort Rusk's appointed custodians and put access under their office. Association workshops operate through new leases and debt deductions.";
    public static final String CUSTODY_BENEFIT = "Rusk funds a 12,000-credit premium, payable only with Chapter 5 victory. Later district escort work comes through his office.";
    public static final String CUSTODY_COST = "Independent tool access ends. Lenna records the company's role and does not become its worker-side contracting contact. The court terms remain lawful and burdensome.";
    public static final String COMPACT_UNDERTAKING = "Escort a joint custody party and preserve inspected common-service access under a signed repayment schedule. Rusk appoints security; the association retains shop operation and auditable service allocation.";
    public static final String COMPACT_BENEFIT = "Lenna Saye becomes the contact for later district escort work. The existing common workshop access continues.";
    public static final String COMPACT_COST = "The company forgoes the 12,000-credit premium to fund the transition guarantee; that fully satisfies its contribution, with no further debit. "
            + "Rusk accepts the agreement but records the company as an independent counterparty rather than his preferred enforcement contractor. Debt and security duties remain.";
    public static final String CASTLE_COMPARISON = "Castle: Both papers keep Rusk's title, the debt and the same equipment on either route; Greyhook still has to go. "
            + "Custody is the money and Rusk's office: 12,000 credits if Black Sluice falls, his managers stabilize the schedules, and the benches go on lease with deductions. Lenna stops being our contact on the workers' side. "
            + "The compact costs us that premium. Lenna becomes our contact for escort work, the shops stay common and inspected, and Rusk files us as an independent counterparty. Divided administration will rub. "
            + "Neither changes how we fight or what we can buy, and nobody on that yard is universally grateful either way. Nothing is committed until you sign; we can leave this and come back.";
    public static final String SIGNING_NOTE = "Signing is final: the other agreement closes. Defeat or a later retreat does not reopen it. "
            + "Before signing, the company may instead decline further work; completed fees and learned or carried cards are kept.";
    public static final String DECLINED_RECORD = "The company has declined further Latchkey work before signing. Rusk's three completion fees and both recovered section cards stand. "
            + "Custody stays a matter between House Rusk, the association and the Combine courts; no fourth commission will be offered.";
    private AgreusArcCampaign() { }

    /** Text-only correspondence; these new speakers have no recorded voice. */
    static final WaylineNarration.Line LENNA_GATE = correspondence("lenna-gate", "story_agreus_yard_owners", "correspondence.lenna.gate",
            "LENNA SAYE / LATCHKEY YARD ASSOCIATION",
            "You have opened the gate. Before you hand over the keys, read who is allowed through it tomorrow. Those benches repair the weir's pumps as well as Rusk's cargo gear.");
    static final WaylineNarration.Line LENNA_LIEN = correspondence("lenna-lien", "story_agreus_yard_owners", "correspondence.lenna.lien",
            "LENNA SAYE / LATCHKEY YARD ASSOCIATION",
            "Mara Venn says you broke a lien for Wayline on Jangala. Whose side is the lien on this time?");
    static final WaylineNarration.Line VOSS_WARNING = correspondence("voss-warning", "story_agreus_terms_of_protection", "correspondence.voss.warning",
            "SERA VOSS / GREYHOOK FLOTILLA",
            "Pell collected his share from that yard. Your new agreement leaves him nothing. I've told him to leave it. Plan for him not listening.");

    /** Voss's last line on the open channel; text only, never voiced. */
    static final WaylineNarration.Line VOSS_LAST = correspondence("voss-last", "story_agreus_small_war", "correspondence.voss.last",
            "SERA VOSS / OPEN CHANNEL", "Keep the pier. I've lost worse berths.");

    private static WaylineNarration.Line correspondence(String id, String missionId, String event, String speaker, String text) {
        return new WaylineNarration.Line(id, missionId, event, text, 12f, 10, 15f, List.of(), speaker, OpeningBroker.CREST, false, false);
    }

    /** Correspondence the current decision stage has actually earned, in reading order. */
    static List<WaylineNarration.Line> correspondence(AgreusArcProgress p) {
        if (p == null) return List.of();
        return switch (p.handoverStage()) {
            case NOT_REACHED -> List.of();
            case OPEN, DECLINED -> List.of(LENNA_GATE, LENNA_LIEN);
            case SIGNED -> List.of(LENNA_GATE, LENNA_LIEN, VOSS_WARNING);
            case RATIFIED -> List.of(LENNA_GATE, LENNA_LIEN, VOSS_WARNING, RUSK_FAMILY);
        };
    }

    /** The explicit signing action from the HQ agreement desk. */
    static boolean sign(AgreusYardAgreement agreement) {
        var p = current();
        if (p == null || !p.signAgreement(agreement)) return false;
        CampaignStateScript.getInstance().state().agreusArc = p;
        return true;
    }
    static boolean declineFurtherWork() {
        var p = current();
        if (p == null || !p.declineFurtherWork()) return false;
        CampaignStateScript.getInstance().state().agreusArc = p;
        return true;
    }
    static AgreusArcProgress.HandoverStage handoverStage() {
        var p = current();
        return p == null ? AgreusArcProgress.HandoverStage.NOT_REACHED : p.handoverStage();
    }
    static AgreusYardAgreement signedAgreement() {
        var p = current();
        return p == null ? null : p.signedAgreement();
    }

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
        if (p == null || p.nextMissionId() == null || ctx == null || ctx.roster == null || !isContact(ctx.client)
                || ctx.client.locked || ctx.client.patronHouseId != -1 || !atSite(ctx.planet)) return List.of();
        return List.of(offer(p, ctx.planet.getName()));
    }
    public static Mission offer(AgreusArcProgress p, String planetName) {
        if (p == null || p.nextMissionId() == null) throw new IllegalArgumentException("No open Agreus contract");
        if (AgreusArcProgress.smallWar(p.nextMissionId())) return smallWarOffer(p, planetName);
        if (AgreusArcProgress.termsOfProtection(p.nextMissionId())) return termsOffer(p, planetName);
        if (p.warrantCompleted()) return yardOffer(p, planetName);
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
    private static Mission yardOffer(AgreusArcProgress p, String planetName) {
        String losses = p.yardTechniciansLost() + p.yardConstablesLost() + p.toolingLost() == 0 ? ""
                : " Confirmed prior losses: " + p.yardTechniciansLost() + " technicians, " + p.yardConstablesLost()
                + " constables and " + p.toolingLost() + " calibration machines. This is a replacement inspection party and refit, not a pristine-yard report.";
        return Mission.builder().id(p.nextMissionId()).name("The Yard's Owners").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.YARD_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(YARD_BRIEF + losses + "\n\n" + YARD_TERMS)
                .requirements("Three full squads recommended; minimum two selected squads with four ready marines each. Fitted boats and owned working tender required. "
                        + "Eight constables, two technicians; employer heavy lift for an owned active mech lance, optional. "
                        + "36,000 credits and Recovered Arbalest card once on inspected handover. No custody agreement.")
                .requiredDrops(6).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15303L).build();
    }
    private static Mission smallWarOffer(AgreusArcProgress p, String planetName) {
        boolean custody = p.signedAgreement() == AgreusYardAgreement.HOUSE_CUSTODY;
        String losses = p.smallWarCustodiansLost() + p.smallWarHoldersLost() + p.fuelInfrastructureLost() == 0 ? ""
                : " Confirmed prior losses: " + p.smallWarCustodiansLost() + " custody-party members, " + p.smallWarHoldersLost()
                + " holders and " + p.fuelInfrastructureLost() + " marked fuel installations. This mobilization brings a replacement custody party, holders and refitted controls; the losses stay on record.";
        String route = custody ? " Ratified House Custody: Rusk's appointed custodians take the handover, and the disclosed 12,000-credit premium is paid once with this victory."
                : " Ratified Yard Compact: a joint custody party takes the handover; the premium was forgone to fund the transition guarantee.";
        return Mission.builder().id(p.nextMissionId()).name("A Small War").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.SMALL_WAR_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(SMALL_WAR_BRIEF + route + losses + "\n\n" + SMALL_WAR_TERMS)
                .requirements("Three full squads recommended; minimum two selected squads with four ready marines each. Fitted boats and owned working tender required. "
                        + "Twelve " + (custody ? "estate guards" : "constables") + " and a three-person custody party; employer heavy lift for an owned active mech lance, optional. "
                        + "44,000 credits once on completed handover" + (custody ? ", plus the 12,000-credit House Custody premium." : ". No suit card."))
                .requiredDrops(6).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15305L).build();
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
        if (AgreusArcProgress.smallWar(o.missionId)) {
            if (o.smallWar == null || Global.getSector().getPlayerFleet() == null) return -1;
            var credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
            return settleSmallWar(p, o, credits::add);
        }
        if (AgreusArcProgress.yardOwners(o.missionId)) {
            if (o.yardOwners == null || MarineRosterScript.getInstance() == null) return -1;
            var unavailable = LoadoutCardInventory.playerUnavailableCardIds();
            return settleYard(p,o,Global.getSector().getPlayerFleet().getCargo(),unavailable);
        }
        if (AgreusArcProgress.termsOfProtection(o.missionId)) {
            if (o.termsOfProtection == null || MarineRosterScript.getInstance() == null) return -1;
            var unavailable = LoadoutCardInventory.playerUnavailableCardIds();
            return settleTerms(p, o, Global.getSector().getPlayerFleet().getCargo(), unavailable);
        }
        if (AgreusArcProgress.limitedWarrant(o.missionId)) {
            if (o.limitedWarrant == null || MarineRosterScript.getInstance() == null) return -1;
            var unavailable = LoadoutCardInventory.playerUnavailableCardIds();
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
        var card = LoadoutCardItemPlugin.itemData(AgreusArcProgress.SPECTER_CARD);
        var r = o.limitedWarrant;
        int payment = p.settleWarrant(o.missionId, "agreus", o.victory, o.payoutEarned,
                r.recordsRecovered(), r.kitRecovered(), r.kitReturned(), r.companyReturned(),
                r.cargoLost(), r.techniciansLost(), r.constablesLost());
        if (payment > 0 && !unavailable.contains(AgreusArcProgress.SPECTER_CARD)) cargo.addSpecial(card, 1);
        return payment;
    }
    /**
     * Chapter 5's once-only settlement. The ordinary resolver credits the 44,000 fee from the
     * outcome; a ratified House Custody premium is paid here through {@code premium}, once.
     * Returns the total credits the receipt earned, or -1 for a refused receipt.
     */
    static int settleSmallWar(AgreusArcProgress p, MissionOutcome o, IntConsumer premium) {
        if (p == null || o == null || premium == null || o.smallWar == null || o.missionSource != MissionSource.STORY
                || !AgreusArcProgress.smallWar(o.missionId) || !p.acceptsTarget(o.targetPlanetName)) return -1;
        var r = o.smallWar;
        int total = p.settleSmallWar(o.missionId, "agreus", o.victory, o.payoutEarned, r.customsHeld(), r.fuelControlHeld(),
                r.dispatchHeld(), r.rearPositionBuilt(), r.handedOver(), r.companyPresent(), r.infrastructureLost(),
                r.custodiansLost(), r.holdersLost(), r.greyhookWithdrawn());
        if (total > o.payoutEarned) premium.accept(total - o.payoutEarned);
        return total;
    }
    static int settleYard(AgreusArcProgress p, MissionOutcome o, CargoAPI cargo, Set<String> unavailable) {
        if (p == null || o == null || cargo == null || unavailable == null || o.yardOwners == null
                || o.missionSource != MissionSource.STORY || !AgreusArcProgress.yardOwners(o.missionId)
                || !p.acceptsTarget(o.targetPlanetName)) return -1;
        var card = LoadoutCardItemPlugin.itemData(AgreusArcProgress.ARBALEST_CARD);
        var result = o.yardOwners;
        int payment = p.settleYard(o.missionId,"agreus",o.victory,o.payoutEarned,result.dispatchHeld(),result.toolingHeld(),
                result.entranceHeld(),result.inspected(),result.companyPresent(),result.toolingLost(),result.techniciansLost(),result.constablesLost());
        if (payment > 0 && !unavailable.contains(AgreusArcProgress.ARBALEST_CARD)) cargo.addSpecial(card,1);
        return payment;
    }

    // ---- Chapter 4: Terms of Protection ----

    public static final String TERMS_BRIEF_COMMON = "Castle: Rusk pays 32,000 credits to see the signed handover done at Latchkey. "
            + "Escort the party's carrier from the apron to the dispatch handover bay; it rolls only while our infantry is with it and nothing hostile has it in sight. "
            + "The representatives sign at the bay once dispatch is held. Being nearby signs nothing. "
            + "Then the utilities crew commissions the captured rig tooling in the calibration room: seventy-five seconds of work, paused, not restarted, while anyone hostile is inside the hall or our infantry is not. "
            + "Ratification needs a living representative at dispatch after every committed attack is resolved. The end of an attack ratifies nothing. "
            + "Losing both representatives, the records carrier, or both technicians before the work is done ends the attempt; other losses are recorded. "
            + "Constables still hold the operator entrance we secured. Bring your working tender; Rusk funds heavy lift for an owned active mech lance. No bombardment or fighter strikes over the homes and benches. ";
    public static final String TERMS_CUSTODY = "House Custody: Rusk's two appointed custodians ride with the custodial records carrier, with his utilities crew and four estate guards. "
            + "The guards will hold the Cinder Steps gate on Rusk's terms. That frees one of our sections; their post is the weak point if anyone comes down the lane. ";
    public static final String TERMS_COMPACT = "Yard Compact: the joint party, Rusk's custodian and the association's delegate, rides with the joint-party records carrier, with the utilities crew and three of Lenna's fitters. "
            + "The fitters will not leave the benches while the tooling is commissioned, so the hall itself has to be held, not only its doors. ";
    public static final String TERMS_AWARD = "Either route: the technicians' Boarding Jack refurbishment dossier is a once-only section card, delivered to fleet cargo on victory. "
            + "Learning and fitting it are explicit Armory choices; nobody is refitted automatically. ";
    public static final String TERMS_UNKNOWN = "Voss says she told Pell to leave it. We have no count of his people and no time. The yard's approaches are the south service road and the Cinder Steps lane. ";

    static final WaylineNarration.Line RUSK_FAMILY = correspondence("rusk-family", "story_agreus_terms_of_protection", "debrief.terms_of_protection.family",
            "BARONET ELIAN RUSK",
            "I have written to my family. I would rather not have. They will answer, and they will want to know why they had to.");
    /**
     * The place the arc's current or most recent chapter is fought at, for the contract-board
     * header: each chapter names its own ground rather than the first chapter's.
     */
    static String chapterPlace(AgreusArcProgress p) {
        if (p == null || !p.completed()) return "NORTH WEIR";
        if (!p.warrantCompleted()) return "LOW QUAY";
        if (!p.yardCompleted()) return "LATCHKEY YARD";
        return switch (p.handoverStage()) {
            case SIGNED -> "LATCHKEY HANDOVER";
            case RATIFIED -> p.smallWarSettled() ? "LOW QUAY FREIGHT LANE" : "BLACK SLUICE";
            default -> "LATCHKEY YARD";
        };
    }
    static final String DRENT_LEAD = "Recovered levy file: a dispatch order signed by Mika Drent of Drent Haulage. Its load numbers match Nadi Orr's shortage lists "
            + "and name Black Sluice's transfer schedule. The order is a document, not a sighting; it says where the loads went, not who will be waiting there.";

    /** Which signed route a Terms of Protection offer was written for; its own briefing names it. */
    public static boolean custodyRoute(Mission m) {
        return m != null && AgreusArcProgress.termsOfProtection(m.id) && m.flavor != null && m.flavor.contains(TERMS_CUSTODY);
    }

    private static Mission termsOffer(AgreusArcProgress p, String planetName) {
        var route = p.signedAgreement();
        String losses = p.termsRepresentativesLost() + p.termsTechniciansLost() + p.termsEmployerLost() + p.recordsCarriersLost() == 0 ? ""
                : " Confirmed prior losses: " + p.termsRepresentativesLost() + " representatives, " + p.termsTechniciansLost()
                + " technicians, " + p.termsEmployerLost() + " other employer people and " + p.recordsCarriersLost()
                + " records carriers. Rusk funds a replacement party and carrier for this mobilization; the signed agreement stands.";
        return Mission.builder().id(p.nextMissionId()).name("Terms of Protection").source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.TERMS_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(TERMS_BRIEF_COMMON + (route == AgreusYardAgreement.HOUSE_CUSTODY ? TERMS_CUSTODY : TERMS_COMPACT)
                        + TERMS_UNKNOWN + losses + "\n\n" + TERMS_AWARD)
                .requirements("Three squads recommended, mixed sections useful; minimum two selected squads with four ready marines each. Fitted boats and owned working tender required. "
                        + (route == AgreusYardAgreement.HOUSE_CUSTODY ? "Two custodians, two technicians, four estate guards" : "Two joint-party representatives, two technicians, three fitters")
                        + " and one records carrier; four constables at the operator entrance. Employer heavy lift for an owned active mech lance, optional. "
                        + "32,000 credits once and a Boarding Jack section entitlement on either route; victory ratifies the signed agreement.")
                .requiredDrops(6).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15304L).build();
    }

    /** The battle's route identity for the signed agreement; the battle never chooses it. */
    static TermsOfProtectionProgress.Route route(AgreusYardAgreement agreement) {
        if (agreement == null) throw new IllegalStateException("Terms of Protection requires a signed agreement");
        return agreement == AgreusYardAgreement.HOUSE_CUSTODY ? TermsOfProtectionProgress.Route.HOUSE_CUSTODY
                : TermsOfProtectionProgress.Route.YARD_COMPACT;
    }
    static AgreusYardAgreement agreement(TermsOfProtectionProgress.Route route) {
        if (route == null) return null;
        return route == TermsOfProtectionProgress.Route.HOUSE_CUSTODY ? AgreusYardAgreement.HOUSE_CUSTODY : AgreusYardAgreement.YARD_COMPACT;
    }

    static int settleTerms(AgreusArcProgress p, MissionOutcome o, CargoAPI cargo, Set<String> unavailable) {
        if (p == null || o == null || cargo == null || unavailable == null || o.termsOfProtection == null
                || o.missionSource != MissionSource.STORY || !AgreusArcProgress.termsOfProtection(o.missionId)
                || !p.acceptsTarget(o.targetPlanetName)) return -1;
        var r = o.termsOfProtection;
        int payment = p.settleTerms(o.missionId, "agreus", o.victory, o.payoutEarned, agreement(r.route()),
                r.delivered(), r.signed(), r.commissioned(), r.assaultsResolved(), r.ratified(), r.companyPresent(), r.carrierLost(),
                r.representativesLost(), r.techniciansLost(), r.fittersLost() + r.guardsLost() + r.constablesLost());
        if (payment > 0) deliverBoardingJack(p, cargo, unavailable);
        return payment;
    }

    /**
     * Places the recorded Boarding Jack card once its presentation is declared. While the pattern
     * is still pending, nothing is placed and the entitlement waits; an already learned or carried
     * card satisfies it without a duplicate. Returns whether a card went into cargo.
     */
    static boolean deliverBoardingJack(AgreusArcProgress p, CargoAPI cargo, Set<String> unavailable) {
        if (p == null || cargo == null || unavailable == null) return false;
        var card = LoadoutCard.parse(AgreusArcProgress.BOARDING_JACK_CARD);
        boolean ready = card != null && card.presentationDeclared();
        if (!p.claimBoardingJackDelivery(ready)) return false;
        if (unavailable.contains(AgreusArcProgress.BOARDING_JACK_CARD)) return false;
        cargo.addSpecial(LoadoutCardItemPlugin.itemData(AgreusArcProgress.BOARDING_JACK_CARD), 1);
        return true;
    }

    /** Game-load hook: a deferred Boarding Jack entitlement is delivered on the first load after its art ships. */
    public static boolean deliverPendingBoardingJack() {
        var p = current();
        if (p == null || !p.boardingJackDeferred() || Global.getSector().getPlayerFleet() == null) return false;
        var unavailable = LoadoutCardInventory.playerUnavailableCardIds();
        return deliverBoardingJack(p, Global.getSector().getPlayerFleet().getCargo(), unavailable);
    }
}
