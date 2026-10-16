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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fleet actions in atmosphere ({@code warship-tactics.md}): small groups of hulls fighting each other with nothing
 * scripted, read for spacing, target distribution and manoeuvre.
 *
 * <p>Loops:
 * <ul>
 *   <li>{@code pack-vs-escorted}: two Enforcers hunting a Dominator screened by two Lashers.</li>
 *   <li>{@code line-of-battle}: two Ventures (broadside ships) against two Enforcers (bow ships).</li>
 *   <li>{@code mirror}: two of Rusk's Wolfs against two more.</li>
 * </ul>
 * Every hull fights under {@link ShipFireOrder#ALL} with its duel helm, automatic shield and doctrine (so a beaten
 * ship can break off); the escorts are named as such. The sides start out of reach on opposite sides of the ground.
 *
 * <p>Verdicts assert mechanics: no two hulls ever pass through one another, no friendly pair closes inside the other's
 * footprint. Duration, damage each way, losses, the least friendly gap and how the hunters shared their targets are
 * readings.
 */
public final class WarshipTacticsScene implements BehaviorScene {

    private static final int TICKS = 9000;
    private static final int FRAME_EVERY = 30;
    private static final float TICK_SECONDS = 1f / 30f;
    /** The deepest two hulls may overlap at the end of a tick, in cells, before contact is said to have failed. */
    private static final float CONTACT_TOLERANCE_CELLS = 0.5f;

    /** One hull of a loop. {@code escortOf} is the index of the hull it screens, or -1. */
    private record Spec(String hull, String variant, Faction side, int escortOf) {}

    private record Loop(String id, List<Spec> west, List<Spec> east) {}

    private static final List<Loop> LOOPS = List.of(
            new Loop("pack-vs-escorted",
                    List.of(new Spec("enforcer", "enforcer_Assault", Faction.ALLY, -1),
                            new Spec("enforcer", "enforcer_Assault", Faction.ALLY, -1)),
                    List.of(new Spec("dominator", "dominator_Assault", Faction.DEFENDER, -1),
                            new Spec("lasher", "lasher_Standard", Faction.DEFENDER, 0),
                            new Spec("lasher", "lasher_Standard", Faction.DEFENDER, 0))),
            new Loop("line-of-battle",
                    List.of(new Spec("venture", "venture_Greyhook", Faction.ALLY, -1),
                            new Spec("venture", "venture_Greyhook", Faction.ALLY, -1)),
                    List.of(new Spec("enforcer", "enforcer_Assault", Faction.DEFENDER, -1),
                            new Spec("enforcer", "enforcer_Assault", Faction.DEFENDER, -1))),
            new Loop("mirror",
                    List.of(new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, -1),
                            new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, -1)),
                    List.of(new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.DEFENDER, -1),
                            new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.DEFENDER, -1))));

    @Override public String id() { return "warship-tactics"; }

    @Override public String label() {
        return "Warship tactics: a pack against an escorted heavy cruiser, a line of broadside ships, and a mirror "
                + "match, read for spacing, targets and manoeuvre";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        String only = System.getProperty("battle.scene.warshipTactics.only");
        List<SceneReport> reports = new ArrayList<>();
        for (Loop loop : LOOPS) {
            if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(loop.id())) continue;
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(Loop loop, FrameSink frames) throws Exception {
        List<Warship> west = fit(loop.west(), 270f);
        List<Warship> east = fit(loop.east(), 90f);
        float reach = 0f;
        float length = 0f;
        for (Warship s : concat(west, east)) {
            reach = Math.max(reach, WarshipShipFire.longestReach(s));
            length = Math.max(length, s.lengthCells);
        }
        float start = 2.0f * reach + 2f * length;
        int width = Math.round(start + 6f * length + 60f);
        int height = Math.round(Math.max(2.2f * reach + 4f * length, 200f));
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
        place(west, loop.west(), midX - start * 0.5f, midY, length);
        place(east, loop.east(), midX + start * 0.5f, midY, length);
        for (Warship s : concat(west, east)) sim.addWarship(s);
        Trace trace = new Trace(west, east);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop.id(), FRAME_EVERY, tick -> trace.caption(loop.id(), tick))
                .run(TICKS);
        return report(loop, trace, sim);
    }

    private static List<Warship> fit(List<Spec> specs, float facing) throws Exception {
        List<Warship> out = new ArrayList<>();
        for (Spec s : specs) out.add(Warship.fit(s.hull(), s.variant(), s.side(), 0f, 0f, facing));
        return out;
    }

    /** Lays a side out in a column across the ground, a hull length and a half apart, escorts on their charge. */
    private static void place(List<Warship> ships, List<Spec> specs, float x, float midY, float length) {
        float spacing = 1.5f * length;
        for (int i = 0; i < ships.size(); i++) {
            Warship s = ships.get(i);
            s.x = x;
            s.y = midY + (i - (ships.size() - 1) * 0.5f) * spacing;
            s.stationTo(s.x, s.y);
            s.limitFlux();
            s.useDoctrine();
            s.shipFire(ShipFireOrder.ALL);
            s.groundFire(Warship.GroundFire.HOLD);
        }
        for (int i = 0; i < ships.size(); i++) {
            int lead = specs.get(i).escortOf();
            if (lead >= 0) ships.get(i).escort(ships.get(lead));
        }
    }

    private static List<Warship> concat(List<Warship> a, List<Warship> b) {
        List<Warship> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private SceneReport report(Loop loop, Trace t, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("hulls-never-pass-through", t.deepestOverlap <= CONTACT_TOLERANCE_CELLS,
                String.format(Locale.ROOT, "deepest overlap at a tick's end %.2f cells, %d ticks with any overlap",
                        t.deepestOverlap, t.overlapTicks)));
        verdicts.add(Verdict.of("friends-keep-clear", t.friendlyOverlapTicks == 0,
                String.format(Locale.ROOT, "least friendly gap %.1f cells, %d ticks with two friends overlapping",
                        t.minFriendlyGap, t.friendlyOverlapTicks)));
        verdicts.add(Verdict.of("the-sides-fight", t.damageToWest > 0f && t.damageToEast > 0f,
                String.format(Locale.ROOT, "west took %.0f, east took %.0f", t.damageToWest, t.damageToEast)));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstHitSeconds", round1(t.firstHitTick < 0 ? -1f : t.firstHitTick * TICK_SECONDS));
        metrics.put("fightSeconds", round1(t.firstHitTick < 0 ? 0f
                : ((t.resolvedTick < 0 ? TICKS : t.resolvedTick) - t.firstHitTick) * TICK_SECONDS));
        metrics.put("resolved", t.resolvedTick < 0 ? 0 : 1);
        metrics.put("damageToWest", Math.round(t.damageToWest));
        metrics.put("damageToEast", Math.round(t.damageToEast));
        metrics.put("westLost", lost(t.west));
        metrics.put("eastLost", lost(t.east));
        metrics.put("westBrokeOff", brokeOff(t.west));
        metrics.put("eastBrokeOff", brokeOff(t.east));
        metrics.put("minFriendlyGapCells", round1(t.minFriendlyGap));
        metrics.put("minHostileGapCells", round1(t.minHostileGap));
        metrics.put("sameTargetPercentWest", t.pairTicksWest == 0 ? -1 : Math.round(100f * t.sameTargetWest / t.pairTicksWest));
        metrics.put("sameTargetPercentEast", t.pairTicksEast == 0 ? -1 : Math.round(100f * t.sameTargetEast / t.pairTicksEast));
        WarshipShipFire fire = sim.warships().shipFire();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("hitSharePercent", fire.roundsFired() == 0 ? 0 : Math.round(100f * fire.roundsOnHull() / fire.roundsFired()));
        int i = 0;
        for (Warship s : concat(t.west, t.east)) {
            String n = (s.faction == Faction.ALLY ? "west" : "east") + (i % 4) + s.hullId;
            metrics.put(n + "HullPercent", Math.round(100f * Math.max(0f, s.hullFraction())));
            float[] range = t.rangeSpan.get(s);
            if (range != null) {
                metrics.put(n + "RangeMin", round1(range[0]));
                metrics.put(n + "RangeMax", round1(range[1]));
            }
            i++;
        }
        return new SceneReport(id(), loop.id(), TICKS, verdicts, metrics);
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static int lost(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) if (s.destroyed()) n++;
        return n;
    }

    private static int brokeOff(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) if (!s.destroyed() && (s.departing() || s.departed())) n++;
        return n;
    }

    private static final class Trace implements TickObserver {
        final List<Warship> west;
        final List<Warship> east;
        final List<Warship> all;
        int firstHitTick = -1;
        int resolvedTick = -1;
        float damageToWest;
        float damageToEast;
        float deepestOverlap;
        int overlapTicks;
        int friendlyOverlapTicks;
        float minFriendlyGap = Float.MAX_VALUE;
        float minHostileGap = Float.MAX_VALUE;
        int sameTargetWest;
        int pairTicksWest;
        int sameTargetEast;
        int pairTicksEast;
        final Map<Warship, Float> lastHp = new IdentityHashMap<>();
        final Map<Warship, Float> lastShield = new IdentityHashMap<>();
        /** Per hull, the least and most range to its hull target once the fight has begun. */
        final Map<Warship, float[]> rangeSpan = new IdentityHashMap<>();

        Trace(List<Warship> west, List<Warship> east) {
            this.west = west;
            this.east = east;
            this.all = concat(west, east);
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "WARSHIP TACTICS / %s / %ds", loop, tick / 30));
            for (Warship w : all) {
                s.append(" / ").append(w.faction == Faction.ALLY ? "W:" : "E:").append(w.hullId).append(' ')
                        .append(w.destroyed() ? "down" : w.departed() ? "gone"
                                : Math.round(100f * Math.max(0f, w.hullFraction())) + "%");
            }
            return s.toString();
        }

        private static boolean live(Warship s) {
            return !s.destroyed() && !s.departed();
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (Warship s : all) {
                float hp = Math.max(0f, s.hitPoints());
                float sh = s.shieldAbsorbedDamage();
                float d = lastHp.getOrDefault(s, hp) - hp + sh - lastShield.getOrDefault(s, sh);
                lastHp.put(s, hp);
                lastShield.put(s, sh);
                if (d <= 0f) continue;
                if (firstHitTick < 0) firstHitTick = tick;
                if (s.faction == Faction.ALLY) damageToWest += d;
                else damageToEast += d;
            }
            boolean anyOverlap = false;
            boolean friendlyOverlap = false;
            for (int i = 0; i < all.size(); i++) {
                Warship a = all.get(i);
                if (!WarshipSpacing.inContact(a)) continue;
                for (int j = i + 1; j < all.size(); j++) {
                    Warship b = all.get(j);
                    if (!WarshipSpacing.inContact(b)) continue;
                    float g = WarshipSpacing.gap(a, b);
                    if (g < 0f) {
                        anyOverlap = true;
                        deepestOverlap = Math.max(deepestOverlap, -g);
                    }
                    if (a.faction == b.faction) {
                        minFriendlyGap = Math.min(minFriendlyGap, g);
                        friendlyOverlap |= g < 0f;
                    } else {
                        minHostileGap = Math.min(minHostileGap, g);
                    }
                }
            }
            if (anyOverlap) overlapTicks++;
            if (friendlyOverlap) friendlyOverlapTicks++;
            sharing(west, true);
            sharing(east, false);
            if (firstHitTick >= 0) {
                for (Warship s : all) {
                    Warship tgt = s.hullTarget();
                    if (!live(s) || tgt == null || !live(tgt)) continue;
                    float r = (float) Math.hypot(tgt.centerX() - s.centerX(), tgt.centerY() - s.centerY());
                    float[] span = rangeSpan.computeIfAbsent(s, k -> new float[]{Float.MAX_VALUE, 0f});
                    span[0] = Math.min(span[0], r);
                    span[1] = Math.max(span[1], r);
                }
            }
            if (resolvedTick < 0 && firstHitTick >= 0) {
                boolean westOver = false;
                boolean eastOver = false;
                for (Warship s : west) westOver |= live(s) && !s.departing();
                for (Warship s : east) eastOver |= live(s) && !s.departing();
                if (!westOver || !eastOver) resolvedTick = tick;
            }
        }

        /** Counts ticks where two live hunters of a side (not escorts) are on the same hull. */
        private void sharing(List<Warship> side, boolean isWest) {
            List<Warship> hunters = new ArrayList<>();
            for (Warship s : side) {
                if (live(s) && !s.departing() && WarshipEscort.liveLead(s) == null && s.hullTarget() != null) hunters.add(s);
            }
            if (hunters.size() < 2) return;
            boolean same = false;
            for (int i = 0; i < hunters.size(); i++) {
                for (int j = i + 1; j < hunters.size(); j++) same |= hunters.get(i).hullTarget() == hunters.get(j).hullTarget();
            }
            if (isWest) {
                pairTicksWest++;
                if (same) sameTargetWest++;
            } else {
                pairTicksEast++;
                if (same) sameTargetEast++;
            }
        }
    }
}
