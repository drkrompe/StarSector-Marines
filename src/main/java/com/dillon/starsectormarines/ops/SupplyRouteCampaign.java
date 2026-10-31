package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.List;
import java.util.Set;

/** Campaign boundary for the authored route; projection never advances a chapter or starts an attempt. */
public final class SupplyRouteCampaign {
    private SupplyRouteCampaign() { }

    public static BrokenSupplyRouteProgress current() {
        CampaignStateScript script = Global.getSector() == null ? null : CampaignStateScript.getInstance();
        return script == null ? null : script.state().brokenSupplyRoute;
    }
    public static boolean owns(String id) { return id != null && id.startsWith("supply_route_"); }
    public static boolean isBattle(Mission m) {
        return m != null && m.id != null && m.id.startsWith(BrokenSupplyRouteProgress.PREFIX)
                && (m.source == MissionSource.STORY || m.source == MissionSource.RECURRING);
    }
    public static boolean freight(Mission m) {
        return m != null && m.id != null && m.id.startsWith(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.THUNDER + "_");
    }
    public static String operationLabel(Mission m) {
        if (m == null) return Strings.get("supplyRouteLabelOperation");
        if (!owns(m.id)) return m.type.name();
        return Strings.get(freight(m) ? "supplyRouteLabelConvoy" : "supplyRouteLabelCapture");
    }
    public static OpeningBroker.Destination destination() {
        BrokenSupplyRouteProgress p = current();
        if (p == null) return OpeningBroker.current();
        if (!p.readable() || Global.getSector() == null || Global.getSector().getEconomy() == null) return null;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            PlanetAPI planet = market.getPlanetEntity();
            if (planet != null && p.planetId().equals(planet.getId()) && accessible(market))
                return new OpeningBroker.Destination(p.planetId(), p.planetName(), p.systemName(), planet);
        }
        return null;
    }
    private static boolean accessible(MarketAPI market) {
        return !market.isHidden() && !market.isPlayerOwned() && market.getSize() > 0
                && market.getFaction() != null && (Global.getSector().getPlayerFaction() == null
                || !market.getFaction().isHostileTo(Global.getSector().getPlayerFaction()));
    }
    public static boolean atSite(PlanetAPI planet) {
        OpeningBroker.Destination destination = destination();
        return planet != null && destination != null && planet.getId().equals(destination.planetId());
    }
    /** Explicitly opening the route dossier freezes the site, before any launch. */
    static boolean introduce(PlanetAPI planet) {
        if (!atSite(planet)) return false;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return false;
        if (script.state().brokenSupplyRoute == null) {
            OpeningBroker.Destination d = destination();
            script.state().brokenSupplyRoute = new BrokenSupplyRouteProgress(d.planetId(), d.planetName(), d.systemName());
        }
        return true;
    }
    public static List<Mission> offers(StoryEligibilityContext ctx) {
        if (ctx == null || ctx.roster == null || ctx.client == null || ctx.client.locked
                || ctx.client.patronHouseId != -1 || !"independent".equals(ctx.client.factionId)
                || WaylineArc.stage(ctx.roster) != WaylineArc.Stage.COMPLETE || !atSite(ctx.planet)) return List.of();
        BrokenSupplyRouteProgress p = current();
        if (p == null) {
            OpeningBroker.Destination d = destination();
            p = new BrokenSupplyRouteProgress(d.planetId(), d.planetName(), d.systemName());
        }
        return offers(p);
    }
    public static List<Mission> offers(BrokenSupplyRouteProgress p) {
        if (p == null || !p.readable()) return List.of();
        return p.available() ? List.of(build(p, p.operation())) : List.of();
    }
    private static Mission build(BrokenSupplyRouteProgress p, int kind) {
        boolean repeat = p.job() > 0;
        String name = Strings.get(kind == BrokenSupplyRouteProgress.JUNCTION ? "supplyRouteJunctionName"
                : kind == BrokenSupplyRouteProgress.THUNDER ? "supplyRouteThunderName" : "supplyRouteSecurityName");
        String terms = kind == BrokenSupplyRouteProgress.JUNCTION ? Strings.get("supplyRouteJunctionTerms")
                : kind == BrokenSupplyRouteProgress.THUNDER
                ? Strings.join("waylineSentenceJoin", Strings.get("supplyRouteThunderTerms"), Strings.get("supplyRouteThunderPay"))
                : Strings.get("supplyRouteSecurityTerms");
        if (kind == BrokenSupplyRouteProgress.THUNDER && p.freight() != null)
            terms = Strings.join("waylineSentenceJoin", terms, Strings.format("supplyRouteFreightLedger", p.freight().summary()));
        return Mission.builder().id(p.missionId()).name(name)
                .source(repeat ? MissionSource.RECURRING : MissionSource.STORY).type(MissionType.ASSAULT)
                .tier(OperationTier.FIRST_CONTRACT).risk(RiskLevel.LOW)
                .payout(kind == BrokenSupplyRouteProgress.THUNDER ? 18000 : 12000)
                .flavor(terms).requirements(Strings.get("supplyRouteRequirements"))
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).targetPlanetName(p.planetName())
                .mapPosition(0.5f, 0.5f).battleSeed((long) p.planetId().hashCode() * 31 + kind * 1000L + p.job()).build();
    }
    /**
     * The supply route's own launch check: at least one deployable squad, every selected one deployable. Two full
     * squads are recommended, never required; a smaller force is the player's choice (owner, 2026-10-27).
     */
    public static boolean forceDeployable(MarineRoster roster, Set<String> selected) {
        return roster != null && selected != null && !selected.isEmpty() && selected.stream().allMatch(id -> roster.squadById(id) != null
                && !roster.squadById(id).reserve() && !roster.squadById(id).stationed());
    }
    /** The recommended force, and the gate the later arcs still apply: two selected squads of four ready marines. */
    public static boolean forceReady(MarineRoster roster, Set<String> selected) {
        return forceDeployable(roster, selected)
                && roster.squads().stream()
                .filter(squad -> !squad.reserve() && selected.contains(squad.id()))
                .filter(squad -> roster.squadMembers(squad).stream()
                        .filter(soldier -> soldier.status() == MarineSoldierStatus.ACTIVE).count() >= 4).count() >= 2;
    }
    /** The quiet beat after a story victory; recurring work and defeated attempts keep their factual reports. */
    static String recoveryDebrief(MissionOutcome outcome) {
        if (outcome == null || !outcome.victory || outcome.missionSource != MissionSource.STORY
                || !owns(outcome.missionId)) return "";
        if (outcome.freightOutcome != null)
            return Strings.get("waylineSentenceJoin") + Strings.get("supplyRouteRecoveryFreight");
        if (outcome.missionId.startsWith(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.JUNCTION + "_"))
            return Strings.get("waylineSentenceJoin") + Strings.get("supplyRouteRecoveryJunction");
        return "";
    }

    static BrokenSupplyRouteProgress requireCurrent(Mission m) {
        BrokenSupplyRouteProgress p = current();
        if (!isBattle(m) || p == null || !p.matches(m.id) || destination() == null
                || m.source != (p.job() > 0 ? MissionSource.RECURRING : MissionSource.STORY)
                || !p.planetName().equals(m.targetPlanetName)) throw new IllegalStateException("Supply-route work is no longer available at its frozen site.");
        return p;
    }
    static int payment(Mission m, boolean victory, LastLiftProgress.Outcome rescue, FreightRunProgress.Outcome cargo) {
        BrokenSupplyRouteProgress p = current();
        return p == null ? 0 : p.payment(m.id, victory, rescue, cargo);
    }
    static int settle(MissionOutcome outcome) {
        BrokenSupplyRouteProgress p = current();
        int paid = settle(p, outcome);
        if (paid >= 0) {
            OpeningBroker.Destination site = destination();
            ArcIssueListings.afterLive(ArcIssueListings.Beat.THUNDER_RUN, p.successful(),
                    site == null ? null : site.planet());
        }
        return paid;
    }
    static int settle(BrokenSupplyRouteProgress p, MissionOutcome outcome) {
        if (p == null || !p.readable() || outcome == null || outcome.missionSource != (p.job() > 0 ? MissionSource.RECURRING : MissionSource.STORY)
                || !p.planetName().equals(outcome.targetPlanetName)
                || p.payment(outcome.missionId, outcome.victory, outcome.lastLiftOutcome, outcome.freightOutcome) != outcome.payoutEarned) return -1;
        return p.settle(outcome.missionId, outcome.victory, outcome.lastLiftOutcome, outcome.freightOutcome);
    }
}
