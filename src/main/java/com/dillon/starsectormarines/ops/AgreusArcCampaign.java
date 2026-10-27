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
import com.dillon.starsectormarines.i18n.Strings;
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

/** House Dormer's authored concession beneath the existing Agreus administration. */
public final class AgreusArcCampaign {
    public static final String CONTACT_ID = "marines_elian_rusk", NAME = "Baronet Elian Dormer";

    private AgreusArcCampaign() { }

    /** Text-only correspondence; these new speakers have no recorded voice. Speaker and text are {@code @key} references resolved at display. */
    static final WaylineNarration.Line LENNA_GATE = correspondence("lenna-gate", "story_agreus_yard_owners", "correspondence.lenna.gate",
            "@agreusArcSpeakerLenna", "@agreusArcLennaGate");
    static final WaylineNarration.Line LENNA_LIEN = correspondence("lenna-lien", "story_agreus_yard_owners", "correspondence.lenna.lien",
            "@agreusArcSpeakerLenna", "@agreusArcLennaLien");
    static final WaylineNarration.Line VOSS_WARNING = correspondence("voss-warning", "story_agreus_terms_of_protection", "correspondence.voss.warning",
            "@agreusArcSpeakerVoss", "@agreusArcVossWarning");

    /** Voss's last line on the open channel; text only, never voiced. */
    static final WaylineNarration.Line VOSS_LAST = correspondence("voss-last", "story_agreus_small_war", "correspondence.voss.last",
            "@agreusArcSpeakerVossOpen", "@agreusArcVossLast");

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
    /** Whether this Agreus battle needs an owned working tender: every chapter but A Small War, which has no construction step. */
    public static boolean requiresConstructionTender(Mission m) { return isBattle(m) && !AgreusArcProgress.smallWar(m.id); }
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
    public static String accessReason() { return destination() == null ? Strings.get("agreusArcAccessUnavailable") : ""; }
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
                : " " + Strings.format("agreusArcLossesWater", p.techniciansLost(), p.militiaLost(), p.repairLoadsLost());
        return Mission.builder().id(p.missionId()).name(Strings.get("agreusArcMissionWaterUnderSeal")).source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(Strings.get("agreusArcBrief") + losses).requirements(Strings.get("agreusArcReqWater"))
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
        String history = p.recordsRecovered() ? " " + Strings.get("agreusArcWarrantRecordsDone") : "";
        if (p.warrantTechniciansLost() + p.warrantConstablesLost() + p.calibrationLoadsLost() > 0)
            history += " " + Strings.format("agreusArcLossesWarrant", p.warrantTechniciansLost(), p.warrantConstablesLost(), p.calibrationLoadsLost());
        return Mission.builder().id(p.nextMissionId()).name(Strings.get("agreusArcMissionLimitedWarrant")).source(MissionSource.STORY)
                .type(MissionType.RAID).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.WARRANT_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(Strings.get("agreusArcWarrantBrief") + history + "\n\n" + Strings.get("agreusArcWarrant"))
                .requirements(Strings.get("agreusArcReqWarrant"))
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15302L).build();
    }
    private static Mission yardOffer(AgreusArcProgress p, String planetName) {
        String losses = p.yardTechniciansLost() + p.yardConstablesLost() + p.toolingLost() == 0 ? ""
                : " " + Strings.format("agreusArcLossesYard", p.yardTechniciansLost(), p.yardConstablesLost(), p.toolingLost());
        return Mission.builder().id(p.nextMissionId()).name(Strings.get("agreusArcMissionYardOwners")).source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.YARD_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(Strings.get("agreusArcYardBrief") + losses + "\n\n" + Strings.get("agreusArcYardTerms"))
                .requirements(Strings.get("agreusArcReqYard"))
                .requiredDrops(6).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).battleSeed(15303L).build();
    }
    private static Mission smallWarOffer(AgreusArcProgress p, String planetName) {
        boolean custody = p.signedAgreement() == AgreusYardAgreement.HOUSE_CUSTODY;
        String losses = p.smallWarCustodiansLost() + p.smallWarHoldersLost() + p.fuelInfrastructureLost() == 0 ? ""
                : " " + Strings.format("agreusArcLossesSmallWar", p.smallWarCustodiansLost(), p.smallWarHoldersLost(), p.fuelInfrastructureLost());
        String route = " " + Strings.get(custody ? "agreusArcRouteCustody" : "agreusArcRouteCompact");
        return Mission.builder().id(p.nextMissionId()).name(Strings.get("agreusArcMissionSmallWar")).source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.SMALL_WAR_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(Strings.get("agreusArcSmallWarBrief") + route + losses + "\n\n" + Strings.get("agreusArcSmallWarTerms"))
                .requirements(Strings.get(custody ? "agreusArcReqSmallWarCustody" : "agreusArcReqSmallWarCompact"))
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
                r.dispatchHeld(), r.handedOver(), r.companyPresent(), r.infrastructureLost(),
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

    static final WaylineNarration.Line RUSK_FAMILY = correspondence("rusk-family", "story_agreus_terms_of_protection", "debrief.terms_of_protection.family",
            "@agreusArcSpeakerRusk", "@agreusArcRuskFamily");
    /**
     * The place the arc's current or most recent chapter is fought at, for the contract-board
     * header: each chapter names its own ground rather than the first chapter's.
     */
    static String chapterPlace(AgreusArcProgress p) {
        if (p == null || !p.completed()) return Strings.get("agreusArcPlaceNorthWeir");
        if (!p.warrantCompleted()) return Strings.get("agreusArcPlaceLowQuay");
        if (!p.yardCompleted()) return Strings.get("agreusArcPlaceLatchkeyYard");
        return Strings.get(switch (p.handoverStage()) {
            case SIGNED -> "agreusArcPlaceLatchkeyHandover";
            case RATIFIED -> p.smallWarSettled() ? "agreusArcPlaceLowQuayFreightLane" : "agreusArcPlaceBlackSluice";
            default -> "agreusArcPlaceLatchkeyYard";
        });
    }
    /** A {@code @key} reference; resolve with {@link Strings#resolve} where it is shown. */
    static final String DRENT_LEAD = "@agreusArcDrentLead";

    /** Which signed route a Terms of Protection offer was written for; its own briefing names it. */
    public static boolean custodyRoute(Mission m) {
        return m != null && AgreusArcProgress.termsOfProtection(m.id) && m.flavor != null && m.flavor.contains(Strings.get("agreusArcTermsCustody"));
    }

    private static Mission termsOffer(AgreusArcProgress p, String planetName) {
        var route = p.signedAgreement();
        boolean custody = route == AgreusYardAgreement.HOUSE_CUSTODY;
        String losses = p.termsRepresentativesLost() + p.termsTechniciansLost() + p.termsEmployerLost() + p.recordsCarriersLost() == 0 ? ""
                : " " + Strings.format("agreusArcLossesTerms", p.termsRepresentativesLost(), p.termsTechniciansLost(), p.termsEmployerLost(), p.recordsCarriersLost());
        return Mission.builder().id(p.nextMissionId()).name(Strings.get("agreusArcMissionTermsOfProtection")).source(MissionSource.STORY)
                .type(MissionType.ASSAULT).tier(OperationTier.ESTABLISHED).risk(RiskLevel.MEDIUM)
                .payout(AgreusArcProgress.TERMS_PAYMENT).targetPlanetName(planetName).defenderFactionOverride("pirates")
                .flavor(Strings.get("agreusArcTermsBriefCommon") + " " + Strings.get(custody ? "agreusArcTermsCustody" : "agreusArcTermsCompact")
                        + " " + Strings.get("agreusArcTermsUnknown") + losses + "\n\n" + Strings.get("agreusArcTermsAward"))
                .requirements(Strings.get(custody ? "agreusArcReqTermsCustody" : "agreusArcReqTermsCompact"))
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
