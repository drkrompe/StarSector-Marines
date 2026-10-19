package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rounds between hulls meet the first hull on their way ({@link WarshipRoundSweep}), so a hull in the line of fire
 * stops what was meant for the hull behind it: an escort screens its charge with its own plate, and a friend in the
 * line is hit unless the shooter keeps it out of the line.
 *
 * <ul>
 *   <li>{@code screen}: an Enforcer held on its station fires on a Dominator; the Dominator's Lasher escort fights the
 *       Enforcer and interposes between them by its own doctrine. The Enforcer keeps the Dominator as its target.</li>
 *   <li>{@code screen-control}: the same with in-flight collision off ({@code -Dbattle.warship.inFlightCollision=false}):
 *       rounds resolve where they land, so they fly through the escort to the Dominator.</li>
 *   <li>{@code friend-in-line}: an Enforcer fires on a Dominator past a second Enforcer of its own side, held across
 *       part of the line; the clear-line check keeps its fouled mounts quiet.</li>
 *   <li>{@code friend-in-line-control}: the same with the clear-line check off
 *       ({@code -Dbattle.warship.clearLineFire=false}), so the friend takes rounds.</li>
 * </ul>
 *
 * Verdicts are mechanics: no round is ever inside a hull it can meet (the control shows rounds inside the escort, so
 * the reading can fail); the escort takes rounds aimed at its charge; friendly fire is rare under the clear-line check
 * while the shooter still lands rounds, and real without it. Hit counts, who took them and how long mounts held are
 * readings.
 */
public final class WarshipScreenScene implements BehaviorScene {

    static final int TICKS = 1200;
    private static final int FRAME_EVERY = 4;
    /** Hull facing with the nose east (+x) and west (-x). */
    private static final float EAST = 270f;
    private static final float WEST = 90f;
    /** Centre to centre, shooter to target, as a share of the shooter's longest anti-ship reach. */
    private static final float RANGE_SHARE = 0.75f;
    /** At most this share of the hits from the friend-in-line shooter may land on its friend. */
    private static final float FRIENDLY_SHARE_CEILING = 0.02f;

    /** Where the screen loop's camera looks, written when it is laid out: centre x, y and cells across. */
    static volatile float[] screenView = {200f, 100f, 200f};

    @Override public String id() { return "warship-screen"; }

    @Override public String label() {
        return "Rounds between hulls meet the first hull on their way: an escort interposed on the line takes fire meant "
                + "for its charge, and a ship holds the guns whose line crosses a friend";
    }

    @Override public int frameDelayMillis() { return 66; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("screen", "screen-control", "friend-in-line", "friend-in-line-control")) {
            reports.add(report(loop, playOne(loop, loop.equals("screen") ? frames : null)));
        }
        return reports;
    }

    private record Run(Trace trace, Warship shooter, Warship target, Warship other, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean screen = loop.startsWith("screen");
        Warship shooter = Warship.fit("enforcer", "enforcer_Assault", Faction.ALLY, 0f, 0f, EAST);
        Warship target = Warship.fit("dominator", "dominator_Assault", Faction.DEFENDER, 0f, 0f, WEST);
        Warship other = screen
                ? Warship.fit("lasher", "lasher_Standard", Faction.DEFENDER, 0f, 0f, WEST)
                : Warship.fit("enforcer", "enforcer_Assault", Faction.ALLY, 0f, 0f, EAST);
        float reach = WarshipShipFire.longestReach(shooter);
        float length = Math.max(target.lengthCells, shooter.lengthCells);
        float range = RANGE_SHARE * reach;
        int width = Math.round(range + 6f * length);
        int height = Math.round(Math.max(range, 6f * length));
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(width - 4, height - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261019L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        float midY = height * 0.5f;
        float sx = (width - range) * 0.5f;
        float tx = sx + range;
        held(shooter, sx, midY, ShipFireOrder.ALL);
        held(target, tx, midY, ShipFireOrder.OFF);
        target.raiseShield(false);
        if (screen) {
            // The escort starts on the line and keeps itself there by its own doctrine, fighting the shooter.
            other.x = (sx + tx) * 0.5f;
            other.y = midY;
            other.stationTo(other.x, other.y);
            other.limitFlux();
            other.useDoctrine();
            other.shipFire(ShipFireOrder.ALL);
            other.groundFire(Warship.GroundFire.HOLD);
            other.raiseShield(false);
            other.escort(target);
            screenView = new float[]{(sx + tx) * 0.5f, midY, Math.min(width, range + 3f * length)};
        } else {
            // Held across the northern part of the line, so some of the shooter's mounts see past it and some do not.
            held(other, sx + range * 0.4f, midY - other.widthCells * 0.6f, ShipFireOrder.OFF);
            other.raiseShield(false);
        }
        sim.addWarship(shooter);
        sim.addWarship(target);
        sim.addWarship(other);
        // The shooter fights the hull it was told to: a held ship keeps its hull while it stays in notice.
        shooter.hullTarget(target);
        if (loop.equals("screen-control")) sim.warships().inFlightCollision(false);
        if (loop.equals("friend-in-line-control")) sim.warships().clearLineFire(false);
        Trace trace = new Trace(shooter, target, other);
        SceneRun run = SceneRun.of(world).observe(trace);
        if (frames != null) run.frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick, sim));
        run.run(TICKS);
        return new Run(trace, shooter, target, other, sim);
    }

    private static void held(Warship ship, float x, float y, ShipFireOrder order) {
        ship.x = x;
        ship.y = y;
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(order);
        ship.groundFire(Warship.GroundFire.HOLD);
        ship.duelHelm(false);
    }

    // ------------------------------------------------------------------------------------------------ verdicts

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        WarshipShipFire fire = run.sim().warships().shipFire();
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Number> metrics = new LinkedHashMap<>();
        boolean collision = !loop.equals("screen-control");
        if (collision) {
            verdicts.add(Verdict.of("no-round-is-inside-a-hull-it-can-meet", t.roundsInsideHulls == 0,
                    t.roundsInsideHulls + " round-ticks inside a hull, over " + t.roundTicks + " round-ticks in flight"));
        }
        switch (loop) {
            case "screen" -> {
                verdicts.add(Verdict.of("the-escort-takes-rounds-aimed-at-its-charge", fire.screenHits() > 0,
                        String.format(Locale.ROOT, "%d hits on the escort from rounds at its charge; the charge took %d, "
                                        + "the escort was on the line %d of %d ticks", fire.screenHits(), t.targetHits(),
                                t.otherOnLineTicks, TICKS)));
                // Only while the escort stands squarely across every line can the charge be held untouched; read it.
            }
            case "screen-control" -> verdicts.add(Verdict.of("the-control-flies-rounds-through-the-escort",
                    t.roundsInsideHulls > 0 && fire.screenHits() == 0,
                    t.roundsInsideHulls + " round-ticks inside a hull, " + fire.screenHits() + " screen hits"));
            case "friend-in-line" -> {
                float share = fire.hullsStruck() == 0 ? 0f : (float) fire.friendlyHits() / fire.hullsStruck();
                verdicts.add(Verdict.of("friendly-fire-is-rare-with-a-clear-line", share <= FRIENDLY_SHARE_CEILING,
                        String.format(Locale.ROOT, "%d of %d hull hits on the friend (%.1f%%)", fire.friendlyHits(),
                                fire.hullsStruck(), 100f * share)));
                verdicts.add(Verdict.of("the-shooter-still-fires-past-its-friend", t.targetHits() > 0,
                        t.targetHits() + " hits on the target; mounts held " + round1(fire.lineHeldSeconds())
                                + " mount-seconds for the friend"));
            }
            case "friend-in-line-control" -> verdicts.add(Verdict.of("without-the-check-the-friend-is-hit",
                    fire.friendlyHits() > 0, fire.friendlyHits() + " hits on the friend"));
            default -> throw new IllegalStateException(loop);
        }
        HullContactReadings.put(metrics, fire);
        metrics.put("targetHits", t.targetHits());
        metrics.put("otherHits", t.otherHits());
        metrics.put("targetHullPercent", Math.round(100f * Math.max(0f, run.target().hullFraction())));
        metrics.put("otherHullPercent", Math.round(100f * Math.max(0f, run.other().hullFraction())));
        metrics.put("otherOnLineTicks", t.otherOnLineTicks);
        metrics.put("roundsInsideHulls", t.roundsInsideHulls);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    // ------------------------------------------------------------------------------------------------ trace

    private static final class Trace implements TickObserver {
        private final Warship shooter;
        private final Warship target;
        private final Warship other;
        private final int targetHitsAtStart;
        private final int otherHitsAtStart;
        int roundsInsideHulls;
        int roundTicks;
        int otherOnLineTicks;

        Trace(Warship shooter, Warship target, Warship other) {
            this.shooter = shooter;
            this.target = target;
            this.other = other;
            this.targetHitsAtStart = hits(target);
            this.otherHitsAtStart = hits(other);
        }

        /** Rounds that reached the hull, glancing or not, and rounds its shield took. */
        private static int hits(Warship ship) {
            return ship.hullHits() + ship.shieldHits();
        }

        int targetHits() {
            return hits(target) - targetHitsAtStart;
        }

        int otherHits() {
            return hits(other) - otherHitsAtStart;
        }

        String caption(String loop, int tick, BattleSimulation sim) {
            WarshipShipFire fire = sim.warships().shipFire();
            return String.format(Locale.ROOT, "%s / %.1fs / rounds meant for the Dominator caught by its escort: %d "
                            + "/ Dominator hit %d / escort hull %.0f%%", loop, tick / 30f, fire.screenHits(),
                    targetHits(), 100f * Math.max(0f, other.hullFraction()));
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            WarshipSystem ws = sim.warships();
            for (WarshipShell s : ws.shells()) {
                if (s.carrier() || s.hasBeam() || (s.hullTarget() == null && !s.stray())) continue;
                roundTicks++;
                if (inside(ws.ships(), s.shooter, s.x(), s.y())) roundsInsideHulls++;
            }
            for (WarheadStrike s : ws.strikes()) {
                if (s.hullTarget() == null) continue;
                roundTicks++;
                if (inside(ws.ships(), s.shooter, s.x(), s.y())) roundsInsideHulls++;
            }
            if (!other.destroyed() && !Float.isNaN(WarshipRoundSweep.footprintEntry(other, shooter.centerX(),
                    shooter.centerY(), target.centerX(), target.centerY(), 0f))) {
                otherOnLineTicks++;
            }
        }

        private static boolean inside(List<Warship> ships, Warship shooter, float x, float y) {
            for (Warship ship : ships) {
                if (ship == shooter || !WarshipRoundSweep.meets(ship)) continue;
                if (WarshipRoundSweep.footprintEntry(ship, x, y, x, y, 0f) == 0f) return true;
            }
            return false;
        }
    }
}
