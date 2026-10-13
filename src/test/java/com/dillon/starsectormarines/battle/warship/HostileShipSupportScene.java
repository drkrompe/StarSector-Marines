package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.HostileShipMarkers;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The defending world's own ship, on the same mechanism as the company's.
 *
 * <p>A company of marines advances on a walled keep whose command post the defender must hold.
 * What differs between loops is the world behind it:
 * <ul>
 *   <li>{@code low-defence}: a lightly defended world. Its profile warrants no ship, so none is
 *       installed, nothing is warned of and nothing arrives. The control for the rest.</li>
 *   <li>{@code high-defence}: a defended Hegemony world. The marines come within thirty cells of
 *       the command post, the defender decides to call, the player is warned, and six seconds later
 *       the ship descends on the defender's side and works the ground under DEFEND.</li>
 *   <li>{@code both-ships}: the same world, and the company calls its own ship the moment the
 *       warning goes up. Two capitals are over the battle; ships are not yet targets of ship guns
 *       (G4), so neither is hurt by the other.</li>
 *   <li>{@code strike}: the same world with a company strike wing arriving after the ship is on
 *       station. Aircraft against ships is the G3 rule: the craft go for the hull and the ship's
 *       flak answers.</li>
 * </ul>
 */
public final class HostileShipSupportScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 7200;
    private static final int KEEP_X = 111;
    private static final int KEEP_Y = 45;
    private static final String CARBINE = "weapon.smg";
    private static final int SUPPLIES = 20;

    @Override public String id() { return "hostile-ship-support"; }

    @Override public String label() {
        return "Hostile ship support: a defended world calls its own ship once, warned first, on the defender's side, "
                + "holding the ground; a lightly defended world calls none; the company's ship and strike craft coexist with it";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run low = playOne("low-defence", frames);
        reports.add(report("low-defence", low, null));
        Run high = playOne("high-defence", frames);
        reports.add(report("high-defence", high, low));
        reports.add(report("both-ships", playOne("both-ships", frames), high));
        reports.add(report("strike", playOne("strike", frames), high));
        return reports;
    }

    private static TargetProfile world(int defenseLevel) {
        return new TargetProfile(6, 5, defenseLevel, 1, "hegemony", Set.<EconomicFunction>of(),
                SurfacePalette.ROCK, SettlementLink.ROAD);
    }

    private static final class Supplies implements CommandPowerResources {
        int supplies = SUPPLIES;

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (amount < 0 || supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }

    private record Run(Trace trace, BattleSimulation sim, ShipSupportSource source) {}

    private Run playOne(String loop, FrameSink frames) {
        boolean low = loop.equals("low-defence");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.wall(100, 30, 122, 30).wall(100, 60, 122, 60).wall(100, 30, 100, 60).wall(122, 30, 122, 60)
                .doorway(100, KEEP_Y);
        builder.squad("marines").faction(Faction.MARINE).size(10).at(30, KEEP_Y).primary(CARBINE)
                .assigned(id -> ObjectiveAssignment.attackMove(id, 104, KEEP_Y)).done();
        builder.squad("garrison").faction(Faction.DEFENDER).size(6).at(KEEP_X, KEEP_Y).primary(CARBINE)
                .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, KEEP_X, KEEP_Y)).done();
        // A marine nobody can reach: the company is wiped out in the loops with a ship, and the strike
        // wing needs a friendly body to say which edge it comes from.
        builder.unit("reserve", Faction.MARINE, UnitType.MARINE, 4, 84);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);
        sim.setTacticalMap(new TacticalMap(List.of(new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                KEEP_X, KEEP_Y, KEEP_X - 2, KEEP_Y - 2, KEEP_X + 2, KEEP_Y + 2, Faction.DEFENDER, 70, 6, true))));
        // The launch seam's decision, taken the way the launch takes it: from the world's profile.
        ShipSupportSource hostile = HostileShipSupport.sourceFor(world(low ? 1 : 5));
        if (hostile != null) sim.installHostileShipSupport(hostile, false, null);
        Supplies resources = new Supplies();
        if (loop.equals("both-ships")) {
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(
                    ShipSupportSource.stock("onslaught", "onslaught_Standard", "Anvil", false)))));
            sim.setCommandPowerResources(resources);
        }
        if (loop.equals("strike")) {
            sim.setFlybyRoster(new FlybyRoster(List.of(new FighterWing(FighterProfile.DAGGER, Faction.MARINE, 4, 50f, 4f))));
        }
        Trace trace = new Trace(world, loop);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 48, tick -> loop + " / tick " + tick + trace.caption())
                .run(TICKS);
        return new Run(trace, sim, hostile);
    }

    private SceneReport report(String loop, Run run, Run other) {
        Trace t = run.trace();
        BattleSimulation sim = run.sim();
        List<Verdict> verdicts = new ArrayList<>();
        float tps = 1f / BattleSimulation.TICK_DT;
        int warningTicks = Math.round(HostileShipSupport.WARNING_SECONDS * tps);
        if (loop.equals("low-defence")) {
            verdicts.add(Verdict.of("a-lightly-defended-world-brings-no-ship", run.source() == null,
                    "defence level 1 of threshold " + HostileShipSupport.DEFENSE_LEVEL_THRESHOLD + ", source "
                            + (run.source() == null ? "none" : run.source().hullId())));
            verdicts.add(Verdict.of("nothing-is-warned-or-called",
                    t.hostile == null && !t.warned && sim.warships().ships().isEmpty(),
                    "warned " + t.warned + ", ships over the battle " + sim.warships().ships().size()));
        } else {
            verdicts.add(Verdict.of("a-defended-world-names-its-owners-hull",
                    run.source() != null && run.source().hullId().equals("onslaught") && !run.source().weapons().isEmpty(),
                    run.source() == null ? "no source" : run.source().hullId() + " with " + run.source().weapons().size() + " weapons"));
            verdicts.add(Verdict.of("called-on-the-threat-not-the-timer",
                    t.warnTick >= 0 && t.warnTick < Math.round(HostileShipSupport.CONTACT_SECONDS * tps)
                            && t.nearestAtWarning <= HostileShipSupport.THREAT_RADIUS_CELLS + 4f,
                    String.format(Locale.ROOT, "warned at tick %d with the nearest marine %.1f cells from the command post "
                            + "(radius %.0f)", t.warnTick, t.nearestAtWarning, HostileShipSupport.THREAT_RADIUS_CELLS)));
            verdicts.add(Verdict.of("warns-before-it-arrives",
                    t.warned && t.markerAtWarning && t.firstShipTick - t.warnTick >= warningTicks - 2
                            && t.arrivedTick > t.firstShipTick,
                    String.format(Locale.ROOT, "beat at tick %d with the marker up (%s), ship called at tick %d (%.1f s later), "
                            + "settled at tick %d", t.warnTick, t.markerAtWarning, t.firstShipTick,
                            (t.firstShipTick - t.warnTick) / tps, t.arrivedTick)));
            verdicts.add(Verdict.of("one-ship-on-the-defenders-side",
                    t.hostile != null && t.hostileShips == 1 && t.hostile.faction == Faction.DEFENDER
                            && t.entryX > KEEP_X + 0.5f,
                    String.format(Locale.ROOT, "%d hostile ship(s), faction %s, entered at x=%.1f of a keep at x=%d",
                            t.hostileShips, t.hostile == null ? "-" : t.hostile.faction, t.entryX, KEEP_X)));
            verdicts.add(Verdict.of("stations-over-the-held-ground-and-holds-it",
                    t.arrivedTick >= 0 && t.arrivalDistance <= 1.5f && t.stance == MissionStance.DEFEND,
                    String.format(Locale.ROOT, "%.2f cells from the command post, stance %s", t.arrivalDistance, t.stance)));
            verdicts.add(Verdict.of("departs-when-its-station-time-is-spent",
                    t.departingTick >= 0 && t.departedTick >= 0
                            && Math.abs(t.stationaryTicks - Math.round(ShipOnStation.STATION_SECONDS * tps)) <= 10,
                    String.format(Locale.ROOT, "stationary %.1f s, leaving at tick %d, gone at tick %d",
                            t.stationaryTicks / tps, t.departingTick, t.departedTick)));
            if (loop.equals("high-defence")) {
                verdicts.add(Verdict.of("fires-on-the-marines",
                        t.rounds > 0 && t.marinesDead > other.trace().marinesDead,
                        String.format(Locale.ROOT, "%d rounds, marines dead %d against %d with no ship", t.rounds,
                                t.marinesDead, other.trace().marinesDead)));
            }
        }
        if (loop.equals("both-ships")) {
            verdicts.add(Verdict.of("the-company-called-its-own-too",
                    t.company != null && t.company.faction == Faction.MARINE && t.companyCalled,
                    "company ship " + (t.company == null ? "none" : t.company.hullId) + ", target seen " + t.companyTargetSeen));
            verdicts.add(Verdict.of("both-over-the-battle-at-once", t.ticksBothOnStation >= 300,
                    String.format(Locale.ROOT, "%.1f s with both on station", t.ticksBothOnStation / tps)));
            // Ship guns do not target hulls (G4). What a shield can still take is ground ordnance
            // landing under a hull, which the ship-vs-ground rule has always allowed.
            verdicts.add(Verdict.of("neither-hull-is-hurt",
                    t.hostile != null && t.company != null && t.hostile.hullFraction() == 1f && t.company.hullFraction() == 1f,
                    String.format(Locale.ROOT, "hostile hull %.3f (%d shield hits from splash), company hull %.3f (%d shield hits from splash)",
                            t.hostile == null ? -1f : t.hostile.hullFraction(), t.hostile == null ? -1 : t.hostile.shieldHits(),
                            t.company == null ? -1f : t.company.hullFraction(), t.company == null ? -1 : t.company.shieldHits())));
            verdicts.add(Verdict.of("each-works-the-ground",
                    t.rounds > 0 && t.companyRounds > 0,
                    "hostile rounds " + t.rounds + ", company rounds " + t.companyRounds));
        }
        if (loop.equals("strike")) {
            verdicts.add(Verdict.of("strike-craft-go-for-the-hostile-hull", t.craftAimedAtHull >= 1,
                    t.craftAimedAtHull + " of " + t.craftSeen + " craft were sent at the hull"));
            verdicts.add(Verdict.of("the-hull-is-hurt-or-the-flak-answers",
                    t.hostile != null && (t.hostile.hullFraction() < 1f || t.hostile.shieldHits() > 0 || t.craftLost >= 1),
                    String.format(Locale.ROOT, "hull %.3f, %d shield hits, %d craft lost",
                            t.hostile == null ? -1f : t.hostile.hullFraction(), t.hostile == null ? -1 : t.hostile.shieldHits(),
                            t.craftLost)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("warnTick", t.warnTick);
        metrics.put("firstShipTick", t.firstShipTick);
        metrics.put("arrivedTick", t.arrivedTick);
        metrics.put("departedTick", t.departedTick);
        metrics.put("marinesDead", t.marinesDead);
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("rounds", t.rounds);
        metrics.put("companyRounds", t.companyRounds);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final String loop;
        Warship hostile;
        Warship company;
        MissionStance stance;
        boolean warned;
        boolean markerAtWarning;
        boolean companyCalled;
        boolean companyTargetSeen;
        int hostileShips;
        int warnTick = -1;
        int firstShipTick = -1;
        int arrivedTick = -1;
        int departingTick = -1;
        int departedTick = -1;
        int stationaryTicks;
        int ticksBothOnStation;
        int marinesDead;
        int garrisonDead;
        int rounds;
        int companyRounds;
        float entryX;
        float arrivalDistance = Float.MAX_VALUE;
        float nearestAtWarning = Float.MAX_VALUE;
        int craftSeen;
        int craftLost;
        int craftAimedAtHull;
        private final Set<Long> seenCraft = new HashSet<>();
        private final Set<Long> aimedCraft = new HashSet<>();
        private int lossesSeen;

        private Trace(SceneWorld world, String loop) {
            this.world = world;
            this.loop = loop;
        }

        String caption() {
            return hostile == null ? "" : String.format(Locale.ROOT, " / hostile ship %.0f,%.0f%s", hostile.x, hostile.y,
                    hostile.departing() ? " leaving" : "");
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            HostileShipSupport support = sim.getHostileShipSupport();
            if (support != null && !warned) {
                for (StoryBeatLog.Event e : sim.getStoryBeats()) {
                    if (e.id().equals(HostileShipSupport.BEAT_INBOUND)) {
                        warned = true;
                        warnTick = e.tick();
                        markerAtWarning = !HostileShipMarkers.forSupport(support).isEmpty();
                        nearestAtWarning = nearestMarine(sim, KEEP_X + 0.5f, KEEP_Y + 0.5f);
                    }
                }
            }
            if (loop.equals("both-ships") && warned && !companyCalled) {
                companyCalled = true;
                companyTargetSeen = sim.playerSeesCell(98, KEEP_Y);
                sim.getCommandPowerService().requestActivation(ShipOnStation.ID, 98, KEEP_Y);
            }
            for (Warship ship : sim.warships().ships()) {
                if (ship.faction == Faction.DEFENDER) {
                    if (hostile == null) {
                        hostile = ship;
                        firstShipTick = tick;
                        entryX = ship.x;
                        stance = ship.stance();
                    }
                } else if (company == null) {
                    company = ship;
                }
            }
            if (hostile != null) hostileShips = 1;
            int dead = 0;
            for (long u : world.members("marines")) if (!sim.getRoster().isAliveById(u)) dead++;
            marinesDead = dead;
            dead = 0;
            for (long u : world.members("garrison")) if (!sim.getRoster().isAliveById(u)) dead++;
            garrisonDead = dead;
            if (hostile != null) {
                if (arrivedTick < 0 && !hostile.isMoving()) {
                    arrivedTick = tick;
                    arrivalDistance = (float) Math.hypot(hostile.x - (KEEP_X + 0.5f), hostile.y - (KEEP_Y + 0.5f));
                }
                if (!hostile.departing() && !hostile.isMoving()) stationaryTicks++;
                if (departingTick < 0 && hostile.departing()) departingTick = tick;
                if (departedTick < 0 && hostile.departed()) departedTick = tick;
                rounds = groundRounds(hostile);
                if (company != null && !hostile.departing() && !hostile.isMoving()
                        && !company.departing() && !company.isMoving()) ticksBothOnStation++;
            }
            if (company != null) companyRounds = groundRounds(company);
            if (hostile != null && loop.equals("strike")) trackCraft(sim);
        }

        private void trackCraft(BattleSimulation sim) {
            float reach = 0.5f * Math.max(hostile.lengthCells, hostile.widthCells) + 2f;
            for (long id : sim.getAirEntityIds()) {
                ShuttleMission mission = sim.world().mission(id);
                if (mission == null || !mission.strikeSortie) continue;
                if (seenCraft.add(id)) craftSeen++;
                boolean working = mission.state == ShuttleState.INCOMING || mission.state == ShuttleState.REPOSITION
                        || mission.state == ShuttleState.ATTACK_RUN;
                if (working && Math.hypot(mission.lzX - hostile.centerX(), mission.lzY - hostile.centerY()) <= reach
                        && aimedCraft.add(id)) craftAimedAtHull++;
            }
            List<AirLoss> lost = sim.getAirLosses();
            for (int i = lossesSeen; i < lost.size(); i++) {
                if (lost.get(i).frame() instanceof FighterProfile) craftLost++;
            }
            lossesSeen = lost.size();
        }

        private float nearestMarine(BattleSimulation sim, float x, float y) {
            float best = Float.MAX_VALUE;
            for (long u : world.members("marines")) {
                if (!sim.getRoster().isAliveById(u)) continue;
                best = Math.min(best, (float) Math.hypot(sim.world().x(u) - x, sim.world().y(u) - y));
            }
            return best;
        }

        private static int groundRounds(Warship ship) {
            int fired = 0;
            for (Warship.Mount m : ship.mounts()) if (!m.pointDefence()) fired += m.shotsFired;
            return fired;
        }
    }
}
