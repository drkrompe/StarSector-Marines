package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
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
        return m != null && owns(m.id) ? freight(m) ? "CONVOY / BREAKTHROUGH" : "CAPTURE / HOLD" : m == null ? "OPERATION" : m.type.name();
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
        String name = kind == BrokenSupplyRouteProgress.JUNCTION ? "Open the Junction"
                : kind == BrokenSupplyRouteProgress.THUNDER ? "Thunder Run" : "Route Security";
        String terms = kind == BrokenSupplyRouteProgress.JUNCTION
                ? "Mara Venn: Getting the depot back has put Wayline on its feet again, Captain. We've scraped together enough to hire close air support: Talon Two, a Mudskipper gunship and crew. Both outlying roads are still cut. Iona Serr, our dispatch coordinator, needs the junction opened. Capture Weigh Station, Freight Yard and Junction Dispatch together. Six militia hold your gains while the company advances. Wayline directs Talon Two over your company's advance and the militia settling in behind it. Cr. 12,000 on success; then escort our freight through the corridor on Thunder Run."
                : kind == BrokenSupplyRouteProgress.THUNDER
                ? "Mara Venn: The junction is open. Now we put freight through the corridor. Land at the staging apron, clear the roadside strongpoints at Dispatch Post, East Crossing and Outbound Gate. On our first arrival, an earlier unescorted hauler is already under attack; its local camera feed shows the danger. Our contracted loads wait at staging. Talon Two returns to give your company close air support after Dispatch is secured. We've hired more ground muscle for the final breakthrough: a twin-cannon heavy tank and two reinforced APCs. Clear Dispatch, then defend for 90 seconds; take East Crossing and defend for 120 seconds while small raider groups enter along the cross streets. Select an infantry squad and use DEFEND AREA to cover an approach. Eight militia anchor the gains and assemble two roadside machine guns if their crews reach the posts. Give your squads a new order when the dispatch window ends. Our three loads move in stages, with freight released at 45-second intervals during the final breakthrough. Beyond Outbound Gate, raiders hold a fenced inspection checkpoint with dug-in machine guns and rifle bays. The tank breaks that gun line; the APCs screen the crossing while the freight passes between them. Keep the road open for the convoy; raiders will try to cut off the column. Wayline is paying for the armored crews and vehicles; your company leads the advance. Cr. 6,000 + 4,000 per carrier delivered across the job, up to 18,000, settled when the run is complete. Dispatch tracks every delivery and loss; if we have to pull back, we return for the remaining loads. Get at least one through and we can fund regular Route Security contracts."
                : "Mara Venn: Capture Power Yard, Freight Gate and Relay Hall, retaining the landing apron. Eight militia hold secured ground while your company pushes forward. A limited raider reserve will try to retake your gains. Cr. 12,000 for this new security job.";
        if (kind == BrokenSupplyRouteProgress.THUNDER && p.freight() != null) terms += " Freight ledger: " + p.freight().summary() + ".";
        return Mission.builder().id(p.missionId()).name(name)
                .source(repeat ? MissionSource.RECURRING : MissionSource.STORY).type(MissionType.ASSAULT)
                .tier(OperationTier.FIRST_CONTRACT).risk(RiskLevel.LOW)
                .payout(kind == BrokenSupplyRouteProgress.THUNDER ? 18000 : 12000)
                .flavor(terms).requirements("2 squads recommended; at least 2 squads with 4 ready marines each. Up to 4 fitted company boats. Wayline provides heavy lift for your active mech lance; select Mech Support and call it onto clear ground. No fighter cover.")
                .requiredDrops(4).employerShuttles(0).marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED).targetPlanetName(p.planetName())
                .mapPosition(0.5f, 0.5f).battleSeed((long) p.planetId().hashCode() * 31 + kind * 1000L + p.job()).build();
    }
    public static boolean forceReady(MarineRoster roster, Set<String> selected) {
        return roster != null && selected != null
                && selected.stream().allMatch(id -> roster.squadById(id) != null
                        && !roster.squadById(id).reserve() && !roster.squadById(id).stationed())
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
            return " The delivered loads are real work on Wayline's books again. We can fund regular Route Security. Take time to see to your company before we talk about the next contract.";
        if (outcome.missionId.startsWith(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.JUNCTION + "_"))
            return " You have opened the junction. We have ground to organize from. Thunder Run will put our freight through the corridor once your company is ready.";
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
    static int settle(MissionOutcome outcome) { return settle(current(), outcome); }
    static int settle(BrokenSupplyRouteProgress p, MissionOutcome outcome) {
        if (p == null || !p.readable() || outcome == null || outcome.missionSource != (p.job() > 0 ? MissionSource.RECURRING : MissionSource.STORY)
                || !p.planetName().equals(outcome.targetPlanetName)
                || p.payment(outcome.missionId, outcome.victory, outcome.lastLiftOutcome, outcome.freightOutcome) != outcome.payoutEarned) return -1;
        return p.settle(outcome.missionId, outcome.victory, outcome.lastLiftOutcome, outcome.freightOutcome);
    }
}
