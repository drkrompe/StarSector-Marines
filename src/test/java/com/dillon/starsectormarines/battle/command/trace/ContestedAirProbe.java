package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.air.AirToAirFire;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.airsupport.AirMission;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.CommsOperators;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Whether the sky over a trial was contested, read off the simulation: what each side lost in the air and to what, how
 * many bombers were downed before they dropped, and where and against what the defenders' strikes fell.
 *
 * <p>Bomber fates are read from the aircraft themselves (a stick is begun once its rounds-left falls below the load), the
 * losses from the append-only air-loss ledger, and the defender strikes from the pool's own event log. The exposure of a
 * strike is a heuristic: the marines within {@link #EXPOSURE_CELLS} of the aim at the call, and again
 * {@link #AFTER_TICKS} later. It is not a damage ledger and is labelled so wherever it is printed.
 */
final class ContestedAirProbe {

    /** Cells about a defender strike's aim within which a marine is counted as exposed to it. */
    private static final float EXPOSURE_CELLS = 12f;
    /** Ticks after the launch at which the exposed marines are counted again. */
    private static final int AFTER_TICKS = 900;
    /** Cells of a landing pad's centre within which an aim point is "the landing zone". */
    private static final float LANDING_ZONE_CELLS = 30f;

    /** How a bomber ended relative to its stick. */
    enum Fate { DOWN_BEFORE_DROP, DOWN_DURING_DROP, DOWN_AFTER_DROP }

    private record Strike(int tick, float x, float y, String zone, int exposedAtCall) {}

    private final Map<Long, Boolean> started = new HashMap<>();
    private final Map<Long, Faction> bomberSide = new HashMap<>();
    private final Map<Long, Fate> fates = new HashMap<>();
    private final List<Strike> defenderStrikes = new ArrayList<>();
    private final List<int[]> pendingAfter = new ArrayList<>();
    private final List<Integer> exposedAfter = new ArrayList<>();
    private int eventsSeen;
    private int operatorsAtStart = -1;
    private final int fortCentreX;
    private final int fortCentreY;
    private final float fortReach;

    ContestedAirProbe(int fortCentreX, int fortCentreY, float fortReach) {
        this.fortCentreX = fortCentreX;
        this.fortCentreY = fortCentreY;
        this.fortReach = fortReach;
    }

    void sample(BattleSimulation sim) {
        if (operatorsAtStart < 0) operatorsAtStart = CommsOperators.available(sim, Faction.DEFENDER).size();
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission m = world.mission(id);
            if (m == null || !m.poolSortie) continue;
            AirOrdnance load = m.ordnance;
            if (load == null || load.firesContinuously()) continue;
            bomberSide.putIfAbsent(id, world.airFaction(id));
            if (m.state == ShuttleState.ATTACK_RUN && m.roundsLeftThisPass < load.roundsPerPass && !m.aborted) started.put(id, true);
            if (m.destroyed && !fates.containsKey(id)) {
                fates.put(id, !started.getOrDefault(id, false) ? Fate.DOWN_BEFORE_DROP
                        : m.roundsLeftThisPass > 0 ? Fate.DOWN_DURING_DROP : Fate.DOWN_AFTER_DROP);
            }
        }
        AirPoolService pool = sim.airPool();
        List<AirPoolService.Event> events = pool.events();
        while (eventsSeen < events.size()) {
            AirPoolService.Event e = events.get(eventsSeen++);
            if (e.side() != Faction.DEFENDER || e.type() != AirPoolService.EventType.LAUNCHED) continue;
            int exposed = marinesWithin(sim, e.x(), e.y());
            defenderStrikes.add(new Strike(e.tick(), e.x(), e.y(), zoneOf(sim, e.x(), e.y()), exposed));
            pendingAfter.add(new int[]{e.tick() + AFTER_TICKS, defenderStrikes.size() - 1});
        }
        for (int i = pendingAfter.size() - 1; i >= 0; i--) {
            int[] due = pendingAfter.get(i);
            if (sim.getSimTickIndex() < due[0]) continue;
            Strike s = defenderStrikes.get(due[1]);
            while (exposedAfter.size() <= due[1]) exposedAfter.add(-1);
            exposedAfter.set(due[1], marinesWithin(sim, s.x, s.y));
            pendingAfter.remove(i);
        }
    }

    private String zoneOf(BattleSimulation sim, float x, float y) {
        for (LandingPad pad : sim.getMarineLandingPads()) {
            if (Math.hypot(pad.centerX - x, pad.centerY - y) <= LANDING_ZONE_CELLS) return "landing zone";
        }
        if (Math.hypot(fortCentreX - x, fortCentreY - y) <= fortReach) return "fort";
        return "open ground";
    }

    private static int marinesWithin(BattleSimulation sim, float x, float y) {
        int n = 0;
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().faction(id) != Faction.MARINE || !sim.identity().type(id).combatant) continue;
            if (Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y) <= EXPOSURE_CELLS) n++;
        }
        return n;
    }

    private static String causeOf(String cause) {
        if (cause == null) return "other";
        if (AirToAirFire.KILL_LABEL.equals(cause)) return "fighters";
        if (cause.endsWith("AA post(s)")) return "AA bubble";
        if ("ground fire".equals(cause)) return "ground fire";
        return "other";
    }

    /** Pool airframes lost, by side and cause (transports are counted apart). */
    private static Map<Faction, Map<String, Integer>> poolLosses(BattleSimulation sim) {
        Map<Faction, Map<String, Integer>> out = new EnumMap<>(Faction.class);
        for (AirLoss loss : sim.getAirLosses()) {
            if (!(loss.frame() instanceof FighterProfile)) continue;
            out.computeIfAbsent(loss.faction(), f -> new HashMap<>()).merge(causeOf(loss.cause()), 1, Integer::sum);
        }
        return out;
    }

    private int bombers(Faction side, Fate fate) {
        int n = 0;
        for (Map.Entry<Long, Fate> e : fates.entrySet()) {
            if (e.getValue() == fate && bomberSide.get(e.getKey()) == side) n++;
        }
        return n;
    }

    private static int lifts(BattleSimulation sim, Faction side) {
        int n = 0;
        for (AirLoss loss : sim.getAirLosses()) {
            if (loss.faction() == side && !(loss.frame() instanceof FighterProfile)) n++;
        }
        return n;
    }

    /** Whether any anti-air yard mount of a fort the defenders hold is still standing, and whether a console is wrecked. */
    private static String yardState(BattleSimulation sim) {
        StringBuilder out = new StringBuilder();
        for (Fort fort : sim.forts().forts()) {
            if (fort.airMountIds().isEmpty()) continue;
            int alive = 0;
            for (long m : fort.airMountIds()) if (sim.world().isAlive(m)) alive++;
            out.append(String.format(Locale.ROOT, "yard mounts standing %d of %d, console wrecked %s", alive,
                    fort.airMountIds().size(), fort.airDefenceWrecked()));
        }
        return out.length() == 0 ? "no anti-air yard" : out.toString();
    }

    String describe(BattleSimulation sim) {
        StringBuilder out = new StringBuilder();
        Map<Faction, Map<String, Integer>> losses = poolLosses(sim);
        for (Faction side : new Faction[]{Faction.MARINE, Faction.DEFENDER}) {
            AirPoolService pool = sim.airPool();
            Map<String, Integer> byCause = losses.getOrDefault(side, Map.of());
            out.append(String.format(Locale.ROOT, "- %s pool airframes lost: %d (%s); transports lost %d%n", side,
                    byCause.values().stream().mapToInt(Integer::intValue).sum(), byCause, lifts(sim, side)));
            for (AirMission job : AirMission.values()) {
                AirPoolService.MissionTally t = pool.tally(side, job);
                if (t.called() == 0 && t.launched() == 0) continue;
                out.append(String.format(Locale.ROOT, "  - %s %s: called %d, launched %d, returned %d, aborted %d, lost %d%n",
                        side, job, t.called(), t.launched(), t.returned(), t.aborted(), t.lost()));
            }
            out.append(String.format(Locale.ROOT, "  - %s bombers: downed before dropping %d, mid-drop %d, after the drop %d%n", side,
                    bombers(side, Fate.DOWN_BEFORE_DROP), bombers(side, Fate.DOWN_DURING_DROP), bombers(side, Fate.DOWN_AFTER_DROP)));
        }
        out.append(String.format(Locale.ROOT, "- defender radio operators: %d at the start, %d at the end (default kind %s); marine: %d%n",
                operatorsAtStart, CommsOperators.available(sim, Faction.DEFENDER).size(), sim.airPool().defaultKind(Faction.DEFENDER),
                CommsOperators.available(sim, Faction.MARINE).size()));
        out.append(String.format(Locale.ROOT, "- %s%n", yardState(sim)));
        out.append(String.format(Locale.ROOT, "- defender sorties launched, any job (zone and exposure read at the launch point): %d%n", defenderStrikes.size()));
        for (int i = 0; i < defenderStrikes.size(); i++) {
            Strike s = defenderStrikes.get(i);
            out.append(String.format(Locale.ROOT, "  - tick %d at (%.0f, %.0f), %s, marines within %.0f cells: %d at the call, %s 30 s on "
                            + "(a heuristic, not a damage ledger)%n", s.tick, s.x, s.y, s.zone, EXPOSURE_CELLS, s.exposedAtCall,
                    i < exposedAfter.size() && exposedAfter.get(i) >= 0 ? Integer.toString(exposedAfter.get(i)) : "-"));
        }
        return out.toString();
    }

    JSONObject json(BattleSimulation sim) throws JSONException {
        JSONObject out = new JSONObject();
        Map<Faction, Map<String, Integer>> losses = poolLosses(sim);
        for (Faction side : new Faction[]{Faction.MARINE, Faction.DEFENDER}) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Integer> e : losses.getOrDefault(side, Map.of()).entrySet()) o.put("lostTo_" + e.getKey(), e.getValue());
            o.put("poolLost", losses.getOrDefault(side, Map.of()).values().stream().mapToInt(Integer::intValue).sum());
            o.put("transportsLost", lifts(sim, side));
            o.put("bombersDownBeforeDrop", bombers(side, Fate.DOWN_BEFORE_DROP));
            o.put("bombersDownDuringDrop", bombers(side, Fate.DOWN_DURING_DROP));
            o.put("bombersDownAfterDrop", bombers(side, Fate.DOWN_AFTER_DROP));
            for (AirMission job : AirMission.values()) {
                AirPoolService.MissionTally t = sim.airPool().tally(side, job);
                o.put(job.name().toLowerCase(Locale.ROOT), new JSONObject().put("called", t.called()).put("launched", t.launched())
                        .put("returned", t.returned()).put("aborted", t.aborted()).put("lost", t.lost()));
            }
            out.put(side.name().toLowerCase(Locale.ROOT), o);
        }
        int landing = 0;
        int fort = 0;
        int open = 0;
        int exposedCall = 0;
        int exposedAfterSum = 0;
        for (int i = 0; i < defenderStrikes.size(); i++) {
            Strike s = defenderStrikes.get(i);
            switch (s.zone) {
                case "landing zone" -> landing++;
                case "fort" -> fort++;
                default -> open++;
            }
            exposedCall += s.exposedAtCall;
            if (i < exposedAfter.size() && exposedAfter.get(i) >= 0) exposedAfterSum += exposedAfter.get(i);
        }
        out.put("defenderSorties", new JSONObject().put("launched", defenderStrikes.size()).put("onLandingZone", landing)
                .put("onFort", fort).put("onOpenGround", open).put("marinesExposedAtCall", exposedCall)
                .put("marinesStillThereAfter30s", exposedAfterSum));
        out.put("yard", yardState(sim));
        return out;
    }
}
