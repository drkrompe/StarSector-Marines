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
 * How long a hull lasts against another, class by class ({@code warship-tactics.md}, durability). One free duel per
 * matchup on open ground: both hulls under {@link ShipFireOrder#ALL}, their duel helms on, automatic shields, no
 * doctrine (so nobody breaks off and every fight is fought to a finish or to the clock). They start facing each other
 * a little over twice the longer reach apart, so the approach is part of the fight.
 *
 * <p>The question is time: a fight between ships of the line should last long enough for the ships to manoeuvre in
 * it. Each loop therefore reads the kill time beside the hulls' own manoeuvre clocks (how long each takes to come
 * through a right angle and to cross its own length) and beside a paper kill time from the base game's numbers
 * (target hit points over the attacker's sustained anti-ship damage per second, through the armour curve), so the
 * reading says how many manoeuvres a fight is worth, not just how many seconds.
 *
 * <p>Verdicts are mechanics only: both hulls find each other and fire, and rounds between hulls land. Who wins, the
 * kill time, the hit share and the damage split are readings, because a calibration must not be held in place by a
 * verdict that pins an outcome.
 */
public final class HullDurabilityScene implements BehaviorScene {

    private static final int TICKS = 9000;
    private static final int FRAME_EVERY = 15;
    private static final float TICK_SECONDS = 1f / 30f;

    /** One matchup: the hull and variant on each side. */
    private record Matchup(String id, String westHull, String westVariant, String eastHull, String eastVariant) {}

    private static final List<Matchup> MATCHUPS = List.of(
            new Matchup("wolf-mirror", "wolf_hegemony", "wolf_hegemony_Rusk", "wolf_hegemony", "wolf_hegemony_Rusk"),
            new Matchup("wolf-vs-hound", "wolf_hegemony", "wolf_hegemony_Rusk", "hound_d_pirates", "hound_d_pirates_Standard"),
            new Matchup("hound-mirror", "hound_d_pirates", "hound_d_pirates_Standard", "hound_d_pirates", "hound_d_pirates_Standard"),
            new Matchup("venture-vs-wolf", "venture", "venture_Greyhook", "wolf_hegemony", "wolf_hegemony_Rusk"),
            new Matchup("enforcer-mirror", "enforcer", "enforcer_Assault", "enforcer", "enforcer_Assault"),
            new Matchup("eagle-mirror", "eagle", "eagle_Assault", "eagle", "eagle_Assault"),
            new Matchup("onslaught-mirror", "onslaught", "onslaught_Standard", "onslaught", "onslaught_Standard"));

    @Override public String id() { return "hull-durability"; }

    @Override public String label() {
        return "Hull durability: one free duel per class matchup, kill time read against the hulls' manoeuvre clocks";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        String only = System.getProperty("battle.scene.hullDurability.only");
        List<SceneReport> reports = new ArrayList<>();
        for (Matchup m : MATCHUPS) {
            if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(m.id())) continue;
            reports.add(playOne(m, frames));
        }
        return reports;
    }

    private SceneReport playOne(Matchup matchup, FrameSink frames) throws Exception {
        Warship west = Warship.fit(matchup.westHull(), matchup.westVariant(), Faction.ALLY, 0f, 0f, 270f);
        Warship east = Warship.fit(matchup.eastHull(), matchup.eastVariant(), Faction.DEFENDER, 0f, 0f, 90f);
        float reach = Math.max(WarshipShipFire.longestReach(west), WarshipShipFire.longestReach(east));
        float length = Math.max(west.lengthCells, east.lengthCells);
        float start = 2.2f * reach + length;
        int width = Math.round(start + 6f * length + 40f);
        int height = Math.round(Math.max(1.6f * reach + 4f * length, 160f));
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(width - 4, height - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        float midX = width * 0.5f;
        float midY = height * 0.5f;
        place(west, midX - start * 0.5f, midY - 0.1f * reach);
        place(east, midX + start * 0.5f, midY + 0.1f * reach);
        sim.addWarship(west);
        sim.addWarship(east);
        Trace trace = new Trace(west, east);
        SceneRun.of(world).observe(trace)
                .frames(frames, matchup.id(), FRAME_EVERY, tick -> trace.caption(matchup.id(), tick))
                .run(TICKS);
        return report(matchup, trace, sim);
    }

    private static void place(Warship ship, float x, float y) {
        ship.x = x;
        ship.y = y;
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(ShipFireOrder.ALL);
    }

    private SceneReport report(Matchup matchup, Trace t, BattleSimulation sim) {
        WarshipShipFire fire = sim.warships().shipFire();
        List<Verdict> verdicts = new ArrayList<>();
        // Whether the weaker side ever answers is a reading (a Hound whose only gun is lost never does), not a verdict.
        verdicts.add(Verdict.of("the-hulls-find-each-other-and-fire", t.westShots() + t.eastShots() > 0,
                "west " + t.westShots() + " rounds, east " + t.eastShots()));
        verdicts.add(Verdict.of("rounds-between-hulls-land", fire.roundsOnHull() > 0,
                fire.roundsOnHull() + " of " + fire.roundsFired() + " rounds reached a hull"));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstHitSeconds", round1(t.firstHitTick < 0 ? -1f : t.firstHitTick * TICK_SECONDS));
        int down = t.firstDownTick;
        metrics.put("killSeconds", round1(down < 0 || t.firstHitTick < 0 ? -1f : (down - t.firstHitTick) * TICK_SECONDS));
        metrics.put("loser", t.west.destroyed() && t.east.destroyed() ? 3 : t.west.destroyed() ? 1 : t.east.destroyed() ? 2 : 0);
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("westRounds", t.westShots());
        metrics.put("eastRounds", t.eastShots());
        metrics.put("roundsOnHull", fire.roundsOnHull());
        metrics.put("hitSharePercent", fire.roundsFired() == 0 ? 0 : Math.round(100f * fire.roundsOnHull() / fire.roundsFired()));
        metrics.put("missilesFired", fire.missilesFired());
        metrics.put("missilesShotDown", fire.missilesShotDown());
        for (Warship s : List.of(t.west, t.east)) {
            String n = s == t.west ? "west" : "east";
            Warship other = s == t.west ? t.east : t.west;
            metrics.put(n + "HullPercent", Math.round(100f * Math.max(0f, s.hullFraction())));
            metrics.put(n + "ShieldAbsorbed", Math.round(s.shieldAbsorbedDamage()));
            metrics.put(n + "Overloads", s.overloadCount());
            metrics.put(n + "MountsLost", s.mountsKnockedOut());
            metrics.put(n + "FirstMountLostSeconds", round1(t.firstMountLost(s) < 0 ? -1f : (t.firstMountLost(s) - Math.max(0, t.firstHitTick)) * TICK_SECONDS));
            metrics.put(n + "StrongestGunBearsPercent", Math.round(100f * t.bearsShare(s)));
            metrics.put(n + "ReachCells", round1(WarshipShipFire.longestReach(s)));
            metrics.put(n + "LengthCells", round1(s.lengthCells));
            metrics.put(n + "SecondsToTurn90", round1(turnSeconds(s, 90f)));
            metrics.put(n + "SecondsToCrossOwnLength", round1(crossSeconds(s)));
            metrics.put(n + "PaperKillSeconds", round1(paperKillSeconds(s, other)));
            metrics.put(n + "SustainedAntiShipDps", Math.round(sustainedDps(s)));
        }
        metrics.put("meanRangeCells", round1(t.rangeTicks == 0 ? -1f : t.rangeSum / t.rangeTicks));
        metrics.put("closestGapCells", round1(t.closestGap));
        return new SceneReport(id(), matchup.id(), TICKS, verdicts, metrics);
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    /** Seconds to come through {@code degrees} from rest at the hull's own turn acceleration and rate. */
    static float turnSeconds(Warship s, float degrees) {
        float a = s.turnAccelDegPerSec2;
        float v = s.maxTurnDegPerSec;
        float spool = v / a;
        float spoolDegrees = v * spool; // spin up and down
        if (spoolDegrees >= degrees) return 2f * (float) Math.sqrt(degrees / a);
        return 2f * spool + (degrees - spoolDegrees) / v;
    }

    /** Seconds to cover the hull's own length from rest. */
    static float crossSeconds(Warship s) {
        float a = s.acceleration;
        float v = s.maxSpeed;
        float spool = v / a;
        float spoolDistance = v * spool;
        if (spoolDistance >= s.lengthCells) return 2f * (float) Math.sqrt(s.lengthCells / a);
        return 2f * spool + (s.lengthCells - spoolDistance) / v;
    }

    /** The attacker's anti-ship guns' damage per second, through the target's armour curve at its full rating. */
    static float sustainedDps(Warship s) {
        float sum = 0f;
        for (Warship.Mount m : s.mounts()) {
            if (WarshipShipRules.gunAtHull(m.weapon)) sum += WarshipShipRules.firepower(m.weapon);
        }
        return sum;
    }

    /**
     * The base game's paper kill time: the target's hit points over the attacker's anti-ship guns, each round taken
     * through the armour curve at the target's full rating (never under the floor), with every round landing and no
     * flux limit. It is a ceiling on pace, not a prediction.
     */
    static float paperKillSeconds(Warship attacker, Warship target) {
        float dps = 0f;
        for (Warship.Mount m : attacker.mounts()) {
            if (!WarshipShipRules.gunAtHull(m.weapon)) continue;
            VanillaWeapon w = m.weapon;
            float per = w.beam() ? w.damagePerSecond() * 0.1f : w.damagePerShot();
            float hit = per * WarshipSystem.armourMultiplier(w.damageType());
            float through = Math.max(0.15f, hit / (hit + target.armour));
            dps += WarshipShipRules.firepower(w) * WarshipSystem.armourMultiplier(w.damageType()) * through;
        }
        return dps <= 0f ? -1f : target.maxHitPoints / dps;
    }

    private static final class Trace implements TickObserver {
        final Warship west;
        final Warship east;
        int firstHitTick = -1;
        int firstDownTick = -1;
        float lastWestHp = Float.NaN;
        float lastEastHp = Float.NaN;
        float lastWestShield;
        float lastEastShield;
        final Map<Warship, Integer> mountLostTick = new LinkedHashMap<>();
        final Map<Warship, int[]> bears = new LinkedHashMap<>();
        float rangeSum;
        int rangeTicks;
        float closestGap = Float.MAX_VALUE;

        Trace(Warship west, Warship east) {
            this.west = west;
            this.east = east;
            bears.put(west, new int[2]);
            bears.put(east, new int[2]);
        }

        int westShots() { return shots(west); }
        int eastShots() { return shots(east); }

        static int shots(Warship s) {
            int n = 0;
            for (Warship.Mount m : s.mounts()) if (!m.pointDefence()) n += m.shotsFired;
            return n;
        }

        int firstMountLost(Warship s) {
            return mountLostTick.getOrDefault(s, -1);
        }

        float bearsShare(Warship s) {
            int[] b = bears.get(s);
            return b[1] == 0 ? 0f : (float) b[0] / b[1];
        }

        String caption(String loop, int tick) {
            return String.format(Locale.ROOT, "HULL DURABILITY / %s / %ds / west %s %d%% / east %s %d%%", loop,
                    tick / 30, west.hullId, Math.round(100f * Math.max(0f, west.hullFraction())), east.hullId,
                    Math.round(100f * Math.max(0f, east.hullFraction())));
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (firstHitTick < 0 && (west.shieldAbsorbedDamage() > lastWestShield || east.shieldAbsorbedDamage() > lastEastShield
                    || (!Float.isNaN(lastWestHp) && west.hitPoints() < lastWestHp)
                    || (!Float.isNaN(lastEastHp) && east.hitPoints() < lastEastHp))) {
                firstHitTick = tick;
            }
            lastWestHp = west.hitPoints();
            lastEastHp = east.hitPoints();
            lastWestShield = west.shieldAbsorbedDamage();
            lastEastShield = east.shieldAbsorbedDamage();
            if (firstDownTick < 0 && (west.destroyed() || east.destroyed())) firstDownTick = tick;
            for (Warship s : List.of(west, east)) {
                if (s.mountsKnockedOut() > 0) mountLostTick.putIfAbsent(s, tick);
                Warship other = s == west ? east : west;
                if (s.destroyed() || other.destroyed()) continue;
                Warship.Mount m = WarshipShipRules.strongest(s);
                if (m == null) continue;
                float mx = s.mountX(m);
                float my = s.mountY(m);
                float range = (float) Math.hypot(other.centerX() - mx, other.centerY() - my);
                if (range > WarshipScale.hullReach(m.weapon.rangePx())) continue;
                int[] b = bears.get(s);
                b[1]++;
                if (Warship.bears(m, s.facingDegrees, mx, my, other.centerX(), other.centerY(), 1e6f)) b[0]++;
            }
            if (!west.destroyed() && !east.destroyed()) {
                float d = (float) Math.hypot(west.centerX() - east.centerX(), west.centerY() - east.centerY());
                if (firstHitTick >= 0) {
                    rangeSum += d;
                    rangeTicks++;
                }
                closestGap = Math.min(closestGap, d - 0.5f * west.widthCells - 0.5f * east.widthCells);
            }
        }
    }
}
