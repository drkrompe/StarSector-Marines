package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
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
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A committed ship called over a battle through the command deck.
 *
 * <p>{@code control} is a walled, garrisoned compound with a company holding off to the west
 * and nobody calling anything: the garrison lives. {@code called} is the same world with the
 * power activated on the ground in front of the compound, with a stock Onslaught as the source:
 * it should enter from the company's own edge, take station over the targeted cell, work the
 * garrison under the company's stance, and depart when its station time is spent.
 * {@code tender} calls a Shepherd instead, in front of a squad advancing on a picket: it enters,
 * stations, and builds by its stance director before it leaves.
 */
public final class ShipSupportCallScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 7200;
    private static final int CALL_TICK = 30;
    private static final int TARGET_X = 88;
    private static final int TARGET_Y = 45;
    private static final String CARBINE = "weapon.smg";
    private static final int PICKET_X = 112;
    private static final int SUPPLIES = 20;

    @Override public String id() { return "ship-support-call"; }

    @Override public String label() {
        return "Ship on Station: a called ship comes in from the company's edge, stations over the targeted ground, "
                + "works it under the company's stance, and departs when its station time is spent; a called tender builds";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "called", "tender")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
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

    private SceneReport playOne(String loop, FrameSink frames) {
        boolean tender = loop.equals("tender");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (tender) {
            builder.squad("company").faction(Faction.MARINE).size(4).at(30, 40).primary(CARBINE)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, PICKET_X, 40)).done();
            builder.squad("garrison").faction(Faction.DEFENDER).size(3).at(PICKET_X, 40).primary(CARBINE)
                    .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, PICKET_X, 40)).done();
        } else {
            builder.wall(100, 30, 122, 30).wall(100, 60, 122, 60).wall(100, 30, 100, 60).wall(122, 30, 122, 60)
                    .doorway(100, 45);
            builder.squad("company").faction(Faction.MARINE).size(4).at(70, 45).primary(CARBINE)
                    .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, 70, 45)).done();
            builder.squad("garrison").faction(Faction.DEFENDER).size(8).at(110, 45).primary(CARBINE)
                    .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, 110, 45)).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261013L);
        Supplies resources = new Supplies();
        int tx = tender ? 36 : TARGET_X;
        int ty = tender ? 40 : TARGET_Y;
        if (!loop.equals("control")) {
            ShipSupportSource source = tender
                    ? ShipSupportSource.stock(Tender.HULL_ID, null, "Fold", true)
                    : ShipSupportSource.stock("onslaught", "onslaught_Standard", "Anvil", false);
            sim.setCommandPowers(List.of(new ShipOnStation(List.of(source))));
            sim.setCommandPowerResources(resources);
        }
        Trace trace = new Trace(world, loop, tx, ty);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 48, tick -> loop + " / tick " + tick
                        + (trace.ship != null ? String.format(Locale.ROOT, " / ship %.0f,%.0f%s", trace.ship.x,
                                trace.ship.y, trace.ship.departing() ? " leaving" : "") : ""))
                .run(TICKS);
        return report(loop, trace, resources, sim);
    }

    private SceneReport report(String loop, Trace t, Supplies resources, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        float ticksPerSecond = 1f / BattleSimulation.TICK_DT;
        int stationTicks = Math.round(ShipOnStation.STATION_SECONDS * ticksPerSecond);
        int charges = sim.getCommandPowerService().getChargesRemaining(ShipOnStation.ID);
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("no-ship-without-a-call", t.firstShipTick < 0 && t.garrisonDead == 0,
                    "ships seen " + (t.firstShipTick < 0 ? "none" : "at tick " + t.firstShipTick)
                            + ", garrison dead " + t.garrisonDead + " of " + t.garrisonSize));
        } else {
            verdicts.add(Verdict.of("the-call-was-seen-and-paid-for",
                    t.targetSeen && charges == 0 && resources.supplies == SUPPLIES - ShipOnStation.SUPPLY_COST,
                    "target seen " + t.targetSeen + ", charges left " + charges + ", supplies " + resources.supplies
                            + " of " + SUPPLIES));
            // A called ship breaks out of the clouds a short glide short of its station, on the
            // company's side of it, rather than at the map edge (ShipStationing.callIn).
            float glide = (float) Math.hypot(tx(loop) + 0.5f - t.entryX, ty(loop) + 0.5f - t.entryY);
            verdicts.add(Verdict.of("comes-in-from-the-companys-side-a-short-glide-out",
                    t.firstShipTick >= 0 && t.entryX < tx(loop) && glide >= ShipStationing.CALL_SHORT_CELLS - 1f
                            && glide <= ShipStationing.CALL_SHORT_CELLS + 15f,
                    String.format(Locale.ROOT, "first seen at tick %d at (%.1f, %.1f), %.1f cells short of its station; "
                            + "the company's side is the west", t.firstShipTick, t.entryX, t.entryY, glide)));
            verdicts.add(Verdict.of("stations-over-the-targeted-ground",
                    t.arrivedTick >= 0 && t.arrivalDistance <= 1.5f,
                    String.format(Locale.ROOT, "arrived at tick %d, %.2f cells from the target (%d, %d)",
                            t.arrivedTick, t.arrivalDistance, tx(loop), ty(loop))));
            verdicts.add(Verdict.of("holds-the-companys-stance",
                    t.stance == MissionStance.ATTACK, "stance " + t.stance + " with the company attacking"));
            int stayed = t.stationaryTicks;
            verdicts.add(Verdict.of("departs-when-its-station-time-is-spent",
                    t.arrivedTick >= 0 && t.departingTick >= 0 && Math.abs(stayed - stationTicks) <= 10
                            && t.departedTick >= 0,
                    String.format(Locale.ROOT, "stationary %.1f s of %.0f s, began to leave at tick %d, gone at tick %d",
                            stayed / ticksPerSecond, ShipOnStation.STATION_SECONDS, t.departingTick, t.departedTick)));
            if (loop.equals("called")) {
                verdicts.add(Verdict.of("works-the-garrison",
                        t.rounds > 0 && t.garrisonDead >= 1,
                        t.rounds + " rounds fired, garrison dead " + t.garrisonDead + " of " + t.garrisonSize
                                + ", first death at tick " + t.firstDeath));
                verdicts.add(Verdict.of("fires-only-while-on-the-clock",
                        t.roundsAfterDeparture == 0,
                        t.roundsAfterDeparture + " rounds fired after it began to leave"));
            } else {
                verdicts.add(Verdict.of("builds-by-its-stance",
                        t.tender != null && t.tender.built() >= 1 && t.laid > 0,
                        (t.tender == null ? 0 : t.tender.built()) + " built, up to " + t.laid + " orders queued at once"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstShipTick", t.firstShipTick);
        metrics.put("arrivedTick", t.arrivedTick);
        metrics.put("departingTick", t.departingTick);
        metrics.put("departedTick", t.departedTick);
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("rounds", t.rounds);
        metrics.put("built", t.tender == null ? 0 : t.tender.built());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static int tx(String loop) { return loop.equals("tender") ? 36 : TARGET_X; }

    private static int ty(String loop) { return loop.equals("tender") ? 40 : TARGET_Y; }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final String loop;
        private final int targetX;
        private final int targetY;
        Warship ship;
        Tender tender;
        MissionStance stance;
        boolean targetSeen;
        int firstShipTick = -1;
        int arrivedTick = -1;
        int departingTick = -1;
        int departedTick = -1;
        float entryX;
        float entryY;
        float arrivalDistance = Float.MAX_VALUE;
        int garrisonSize;
        int garrisonDead;
        int firstDeath = -1;
        int rounds;
        int roundsAfterDeparture;
        int laid;
        private int lastRounds;
        int stationaryTicks;

        private Trace(SceneWorld world, String loop, int targetX, int targetY) {
            this.world = world;
            this.loop = loop;
            this.targetX = targetX;
            this.targetY = targetY;
            this.garrisonSize = world.members("garrison").length;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (tick == CALL_TICK && !loop.equals("control")) {
                targetSeen = sim.playerSeesCell(targetX, targetY);
                sim.getCommandPowerService().requestActivation(ShipOnStation.ID, targetX, targetY);
            }
            if (ship == null && !sim.warships().ships().isEmpty()) {
                ship = sim.warships().ships().get(0);
                firstShipTick = tick;
                entryX = ship.x;
                entryY = ship.y;
                stance = ship.stance();
                if (loop.equals("tender")) tender = sim.tenders().of(ship);
            }
            int dead = 0;
            for (long u : world.members("garrison")) {
                if (!sim.getRoster().isAliveById(u)) dead++;
            }
            if (dead > 0 && firstDeath < 0) firstDeath = tick;
            garrisonDead = dead;
            if (ship == null) return;
            if (arrivedTick < 0 && !ship.isMoving()) {
                arrivedTick = tick;
                arrivalDistance = (float) Math.hypot(ship.x - (targetX + 0.5f), ship.y - (targetY + 0.5f));
            }
            if (!ship.departing() && !ship.isMoving()) stationaryTicks++;
            if (departingTick < 0 && ship.departing()) departingTick = tick;
            if (departedTick < 0 && ship.departed()) departedTick = tick;
            int fired = 0;
            for (Warship.Mount m : ship.mounts()) {
                if (!m.pointDefence()) fired += m.shotsFired;
            }
            if (departingTick >= 0 && fired > lastRounds) roundsAfterDeparture += fired - lastRounds;
            lastRounds = fired;
            rounds = fired;
            if (tender != null) laid = Math.max(laid, tender.orders().size());
        }
    }
}
