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

/** Campaign boundary for the authored route; projection never commits a choice or an attempt. */
public final class SupplyRouteCampaign {
    public static final String IONA_PORTRAIT = "graphics/portraits/portrait33.png";
    private static final String CHOICE = "supply_route_choice_";
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
    public static boolean recovery(Mission m) {
        return m != null && m.id != null && (m.id.equals(CHOICE + BrokenSupplyRouteProgress.LIFT)
                || m.id.startsWith(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.LIFT + "_"));
    }
    public static boolean freight(Mission m) {
        return m != null && m.id != null && m.id.startsWith(BrokenSupplyRouteProgress.PREFIX + BrokenSupplyRouteProgress.THUNDER + "_");
    }
    public static String operationLabel(Mission m) {
        return m != null && owns(m.id) ? recovery(m) ? "ESCORT / BOARDING" : freight(m) ? "CONVOY / BREAKTHROUGH" : "CAPTURE / HOLD" : m == null ? "OPERATION" : m.type.name();
    }
    public static int choice(Mission m) {
        if (m == null) return 0;
        if ((CHOICE + BrokenSupplyRouteProgress.LIFT).equals(m.id)) return BrokenSupplyRouteProgress.LIFT;
        if ((CHOICE + BrokenSupplyRouteProgress.RELAY).equals(m.id)) return BrokenSupplyRouteProgress.RELAY;
        return 0;
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
    /** Explicitly opening the route dossier freezes the site, before any launch or branch decision. */
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
        if (p.choicePending()) return List.of(build(p, BrokenSupplyRouteProgress.LIFT, true), build(p, BrokenSupplyRouteProgress.RELAY, true));
        return p.available() ? List.of(build(p, p.operation(), false)) : List.of();
    }
    private static Mission build(BrokenSupplyRouteProgress p, int kind, boolean choice) {
        boolean repeat = p.job() > 0;
        String name = kind == BrokenSupplyRouteProgress.JUNCTION ? "Open the Junction"
                : kind == BrokenSupplyRouteProgress.LIFT ? (repeat ? "Crew Recovery" : "Last Lift")
                : kind == BrokenSupplyRouteProgress.THUNDER ? "Thunder Run" : repeat ? "Route Security" : "Relay Nine";
        String terms = kind == BrokenSupplyRouteProgress.JUNCTION
                ? "Mara Venn: Wayline has its depot back, but both outlying roads are cut. Iona Serr, our dispatch coordinator, needs the junction opened. Capture Weigh Station, Freight Yard and Junction Dispatch together. Six militia hold your gains while the company advances. Cr. 12,000 on success; then choose which recovery mobilization we authorize."
                : kind == BrokenSupplyRouteProgress.LIFT
                ? "Iona Serr: Reach the stranded crew, escort them through the crossing and protect physical boarding. Six militia support you; an employer pickup reserves twelve civilian seats, separate from your insertion boats. Cr. 4,000 + 500 per person recovered across this job, up to 10,000, paid once on victory. Rescued and confirmed dead people stay recorded; retries cover only those still stranded."
                : kind == BrokenSupplyRouteProgress.THUNDER
                ? "Mara Venn: The relay is ours. Now we put freight through it. Land at the staging apron, secure Dispatch Post, then East Crossing and Outbound Gate. Eight militia hold your gains. A twin-cannon heavy tank leads two reinforced APCs and three unarmed container haulers. The tank breaks the exit gun line; the APCs screen the crossing while the freight passes between them. Keep the road open for the final thunder run; raiders will try to cut off the column. These vehicles are locally authorized Wayline assets. Cr. 6,000 + 4,000 per carrier delivered across the job, up to 18,000, paid once on victory. Deliveries and losses persist; retries move only unresolved freight. Getting at least one load through establishes Route Security."
                : "Mara Venn: Capture Power Yard, Freight Gate and Relay Hall, retaining the landing apron. Eight militia hold secured ground while your company pushes forward. A finite raider reserve will try to retake your gains. " + (repeat ? "Cr. 12,000 for this new security job." : "Cr. 16,000 on success; secure the relay and authorize Thunder Run, the freight convoy finale. Route Security follows the convoy delivery.");
        if (!repeat && (kind == BrokenSupplyRouteProgress.LIFT || kind == BrokenSupplyRouteProgress.RELAY)) terms += kind == BrokenSupplyRouteProgress.LIFT
                ? " Support to Rill withdraws the relay crew: the commercial route stays suspended. Successful rescue establishes Crew Recovery work. Relay Nine is not undertaken."
                : " Support to Relay Nine withdraws Rill's escort request. Its rescue is not undertaken; no death toll or safe evacuation is claimed. Iona remains a correspondent.";
        if (choice) terms += " Inspect either request freely. Only COMMIT SUPPORT issues the permanent order; Back changes nothing. No timer or faction-standing penalty.";
        if (kind == BrokenSupplyRouteProgress.LIFT && p.crew() != null) terms += " Crew ledger: " + p.crew().summary() + ".";
        if (kind == BrokenSupplyRouteProgress.THUNDER && p.freight() != null) terms += " Freight ledger: " + p.freight().summary() + ".";
        return Mission.builder().id(choice ? CHOICE + kind : p.missionId()).name(name)
                .source(repeat ? MissionSource.RECURRING : MissionSource.STORY).type(kind == BrokenSupplyRouteProgress.LIFT ? MissionType.EXTRACTION : MissionType.ASSAULT)
                .tier(OperationTier.FIRST_CONTRACT).risk(RiskLevel.LOW)
                .payout(kind == BrokenSupplyRouteProgress.THUNDER ? 18000 : kind == BrokenSupplyRouteProgress.JUNCTION ? 12000 : kind == BrokenSupplyRouteProgress.LIFT ? 10000 : repeat ? 12000 : 16000)
                .flavor(terms).requirements("2 squads recommended; at least 2 squads with 4 ready marines each. Up to 4 fitted company boats; ground operation without fighter or mech support.")
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
