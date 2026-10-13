package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.SurfaceZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.List;

/** Live boundary for Wayline's fixed Corvus destinations, including unpopulated moons. */
public final class CorvusWaylineCampaign {
    public static final String TITLE = "WAYLINE / THE CORVUS ACCOUNT";
    private CorvusWaylineCampaign() { }

    public static boolean unlocked(BrokenSupplyRouteProgress route) {
        return route != null && route.readable() && route.closed();
    }
    public static CorvusWaylineProgress current() {
        if (Global.getSector() == null) return null;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null || !unlocked(script.state().brokenSupplyRoute)) return null;
        // Old saves enter at chapter one, without mutating a save from a read.
        return script.state().corvusWayline == null ? new CorvusWaylineProgress() : script.state().corvusWayline;
    }
    public static boolean owns(String id) { return id != null && id.startsWith("wayline_corvus_"); }
    public static boolean isBattle(Mission m) {
        return m != null && m.source == MissionSource.STORY && CorvusWaylineProgress.forMission(m.id) != null;
    }
    public static OpeningBroker.Destination destination() {
        return destination(current(), Global.getSector());
    }
    static OpeningBroker.Destination destination(CorvusWaylineProgress p, SectorAPI sector) {
        if (p == null || p.chapter() == null || sector == null) return null;
        StarSystemAPI corvus = sector.getStarSystem("Corvus");
        if (corvus == null) return null;
        for (PlanetAPI planet : corvus.getPlanets()) {
            if (p.chapter().planetId.equals(planet.getId()) && !planet.isStar())
                return new OpeningBroker.Destination(planet.getId(), planet.getName(), corvus.getName(), planet);
        }
        return null;
    }
    public static boolean atSite(PlanetAPI planet) {
        OpeningBroker.Destination d = destination();
        return planet != null && d != null && d.planetId().equals(planet.getId());
    }
    public static List<Mission> offers(StoryEligibilityContext ctx) {
        if (ctx == null || ctx.roster == null || ctx.client == null || ctx.client.locked
                || ctx.client.patronHouseId != -1 || !"independent".equals(ctx.client.factionId)
                || WaylineArc.stage(ctx.roster) != WaylineArc.Stage.COMPLETE || !atSite(ctx.planet)) return List.of();
        return List.of(offer(current(), ctx.planet.getName(), SupplyRouteCampaign.current()));
    }
    public static Mission offer(CorvusWaylineProgress progress, String planetName, BrokenSupplyRouteProgress route) {
        if (progress == null || progress.chapter() == null) throw new IllegalArgumentException("No open Corvus chapter");
        return progress.chapter().mission(progress.missionId(), planetName, introduction(route));
    }
    public static String introduction(BrokenSupplyRouteProgress route) {
        if (route == null) return "Our dispatch records point beyond Jangala.";
        if (!route.readable() || !route.closed()) return route.status();
        String recovery;
        if (route.freight() != null) {
            int delivered = route.freight().deliveredCount();
            recovery = delivered > 0
                    ? "Dispatch has receipts for delivered loads: " + delivered + ". That is work Wayline can build on."
                        + (route.freight().lostCount() > 0 ? " The lost loads remain on the ledger too." : "")
                    : "No freight made it through. The depot is still ours, but this route has not recovered.";
        } else recovery = "The relay contract is closed. Its payment and your work remain on our books.";
        return route.status() + " " + recovery
                + " When we met, Wayline was coming apart. You gave us room to act."
                + " See to your company first. The work on Asharu can wait until you are ready.";
    }
    static CorvusWaylineProgress requireCurrent(Mission m) {
        CorvusWaylineProgress p = current();
        OpeningBroker.Destination d = destination();
        if (!isBattle(m) || p == null || !p.matches(m.id) || d == null
                || !d.planetName().equals(m.targetPlanetName) || m.payout != p.chapter().payment)
            throw new IllegalStateException("This Corvus chapter is no longer available at its destination.");
        return p;
    }
    static TargetProfile profile(Mission m) {
        CorvusWaylineProgress p = requireCurrent(m);
        PlanetAPI planet = destination().planet();
        return p.chapter().profile(SurfaceZoning.forPlanetType(planet.getTypeId()));
    }
    static void begin(Mission m) {
        CorvusWaylineProgress p = requireCurrent(m);
        if (!p.begin(m.id, m.targetPlanetName)) throw new IllegalStateException("This Corvus attempt has already launched.");
        CampaignStateScript.getInstance().state().corvusWayline = p;
    }
    static int settle(MissionOutcome outcome) {
        return settle(current(), outcome);
    }
    static int settle(CorvusWaylineProgress p, MissionOutcome outcome) {
        if (p == null || outcome == null || outcome.missionSource != MissionSource.STORY
                || outcome.targetIndustryId != null || outcome.lastLiftOutcome != null || outcome.freightOutcome != null) return -1;
        return p.settle(outcome.missionId, outcome.targetPlanetName, outcome.victory, outcome.payoutEarned);
    }
}
