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
    public static String choiceSummary(Mission mission) {
        return switch (choice(mission)) {
            case BrokenSupplyRouteProgress.LIFT -> "RILL: Last Lift ends this story branch. Successful rescue opens recurring Crew Recovery. Relay Nine and Thunder Run are forgone; the freight route stays closed. Orders are permanent.";
            case BrokenSupplyRouteProgress.RELAY -> "FREIGHT: Relay Nine leads to Thunder Run, then recurring Route Security after a delivery. Last Lift is forgone; Rill loses its escort and its people remain unaccounted for. Orders are permanent.";
            default -> "";
        };
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
                ? "Iona Serr: Reach the stranded crew, escort them through the crossing and cover them until everyone is aboard. Six militia support you; our pickup has twelve seats reserved for civilians, separate from your insertion boats. Cr. 4,000 + 500 per person recovered across this job, up to 10,000, settled when the rescue is complete. We keep a manifest of everyone recovered or confirmed lost. If you have to pull out, we return for those still stranded."
                : kind == BrokenSupplyRouteProgress.THUNDER
                ? "Mara Venn: The relay is ours. Now we put freight through it. Land at the staging apron, clear the roadside strongpoints at Dispatch Post, East Crossing and Outbound Gate. On our first arrival, an earlier unescorted hauler is already under attack; its local camera feed shows the danger. Our contracted loads wait at staging. Clear Dispatch, then defend for 90 seconds; take East Crossing and defend for 120 seconds while small raider groups enter along the cross streets. Select an infantry squad and use DEFEND AREA to cover an approach. Eight militia anchor the gains and assemble two roadside machine guns if their crews reach the posts. Give your squads a new order when the dispatch window ends. Our three loads move in stages; a twin-cannon heavy tank and two reinforced APCs join for the final breakthrough, with freight released at 45-second intervals. Beyond Outbound Gate, raiders hold a fenced inspection checkpoint with dug-in machine guns and rifle bays. The tank breaks that gun line; the APCs screen the crossing while the freight passes between them. Keep the road open for the convoy; raiders will try to cut off the column. These are the cooperative's own vehicles. Cr. 6,000 + 4,000 per carrier delivered across the job, up to 18,000, settled when the run is complete. Dispatch tracks every delivery and loss; if we have to pull back, we return for the remaining loads. Get at least one through and we can fund regular Route Security contracts."
                : "Mara Venn: Capture Power Yard, Freight Gate and Relay Hall, retaining the landing apron. Eight militia hold secured ground while your company pushes forward. A limited raider reserve will try to retake your gains. " + (repeat ? "Cr. 12,000 for this new security job." : "Cr. 16,000 on success; secure the relay and we can send our freight convoy through on Thunder Run. Once a load gets through, we can offer regular Route Security contracts.");
        if (!repeat && (kind == BrokenSupplyRouteProgress.LIFT || kind == BrokenSupplyRouteProgress.RELAY)) terms += kind == BrokenSupplyRouteProgress.LIFT
                ? " If we send support to Rill, we have to withdraw the relay crew and keep the freight route closed. Last Lift is our final operation at Rill. Bring Rill's people home, and Iona can offer recurring Crew Recovery contracts. We withdraw the relay crew; there will be no relay assault or freight convoy."
                : " If we send support to Relay Nine, Rill loses its escort. We cannot promise those people a way out. Iona will keep us informed.";
        if (choice) terms += " We can fund only one mobilization. Review both requests before you commit support; dispatch will wait for your decision. Once issued, those orders stand. Take the time you need. Neither crew will hold your choice against your company.";
        if (kind == BrokenSupplyRouteProgress.LIFT && p.crew() != null) terms += " Crew ledger: " + p.crew().summary() + ".";
        if (kind == BrokenSupplyRouteProgress.THUNDER && p.freight() != null) terms += " Freight ledger: " + p.freight().summary() + ".";
        return Mission.builder().id(choice ? CHOICE + kind : p.missionId()).name(name)
                .source(repeat ? MissionSource.RECURRING : MissionSource.STORY).type(kind == BrokenSupplyRouteProgress.LIFT ? MissionType.EXTRACTION : MissionType.ASSAULT)
                .tier(OperationTier.FIRST_CONTRACT).risk(RiskLevel.LOW)
                .payout(kind == BrokenSupplyRouteProgress.THUNDER ? 18000 : kind == BrokenSupplyRouteProgress.JUNCTION ? 12000 : kind == BrokenSupplyRouteProgress.LIFT ? 10000 : repeat ? 12000 : 16000)
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
