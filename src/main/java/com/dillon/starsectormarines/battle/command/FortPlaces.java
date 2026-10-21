package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * A fort a side built, as a {@link PlaceCounterattack} place: one case of a place worth retaking, whose loss is the loss
 * of its most critical aspect and whose retake is the massed commit at that aspect
 * ({@link DefenceStructureTactics#planMassed}). Own-force facts about the side's own forts only.
 */
final class FortPlaces {

    /** Believed movers within this many cells of a fort's centre are the strength it is believed held by. */
    static final int RADIUS_CELLS = AssaultMuster.CONTACT_RADIUS_CELLS;
    /** What a fort is worth to its side by itself: its guns cover the approach to everything behind it. UNTUNED. */
    static final float WORTH = 1f;

    private FortPlaces() {}

    /** The place key of a fort. */
    static String key(int fortId) {
        return "fort " + fortId;
    }

    /** The forts {@code side} built, by fort id. */
    static Map<Integer, List<DefenceStructureFact>> ownForts(Faction side, List<DefenceStructureFact> structures) {
        Map<Integer, List<DefenceStructureFact>> forts = new TreeMap<>();
        for (DefenceStructureFact fact : structures) {
            if (fact.ofFort() && fact.originalHolder() == side) {
                forts.computeIfAbsent(fact.fortId(), id -> new ArrayList<>()).add(fact);
            }
        }
        return forts;
    }

    /** The aspect whose loss turns a fort: the bunker of a battery fort (the guns' command), the plant of a shield fort. */
    static DefenceStructureFact critical(List<DefenceStructureFact> fort) {
        if (fort.isEmpty()) return null;
        Kind kind = fort.get(0).family() == Family.SHIELD_FORT ? Kind.FORT_POWER : Kind.FIRE_CONTROL;
        for (DefenceStructureFact aspect : fort) if (aspect.kind() == kind) return aspect;
        return null;
    }

    static Status status(Faction side, List<DefenceStructureFact> fort) {
        boolean allLost = !fort.isEmpty();
        for (DefenceStructureFact aspect : fort) if (!side.hostileTo(aspect.holder())) allLost = false;
        if (allLost) return Status.LOST;
        DefenceStructureFact critical = critical(fort);
        return critical != null && side.hostileTo(critical.holder()) ? Status.LOSING : Status.HELD;
    }

    /**
     * The structure facts single-squad structure work may see: every fact, less the enemy-held aspects of the side's own
     * forts that are losing or lost. Those are the counter-attack's, whether or not it judges them worth it, so a fort is
     * never retaken piecemeal; standing to at the aspects still held is left alone.
     */
    static List<DefenceStructureFact> withheld(Faction side, List<DefenceStructureFact> structures) {
        return withheld(side, structures, fortId -> true);
    }

    /**
     * As {@link #withheld(Faction, List)}, for the forts the counter-attack still claims: one it has given up, or stood
     * down from as outmatched, is ordinary structure work again.
     */
    static List<DefenceStructureFact> withheld(Faction side, List<DefenceStructureFact> structures, IntPredicate claimed) {
        Map<Integer, List<DefenceStructureFact>> forts = ownForts(side, structures);
        Set<Integer> turned = new TreeSet<>();
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : forts.entrySet()) {
            if (status(side, entry.getValue()) != Status.HELD && claimed.test(entry.getKey())) turned.add(entry.getKey());
        }
        if (turned.isEmpty()) return structures;
        List<DefenceStructureFact> out = new ArrayList<>(structures.size());
        for (DefenceStructureFact fact : structures) {
            if (fact.ofFort() && fact.originalHolder() == side && turned.contains(fact.fortId())
                    && side.hostileTo(fact.holder())) continue;
            out.add(fact);
        }
        return out;
    }

    /** The side's forts as places. */
    static List<Place> places(Faction side, List<DefenceStructureFact> structures, IntPredicate alarmed) {
        List<Place> out = new ArrayList<>();
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : ownForts(side, structures).entrySet()) {
            List<DefenceStructureFact> fort = entry.getValue();
            int[] centre = centre(fort);
            DefenceStructureFact critical = critical(fort);
            // Its critical aspect, held by the enemy, is being taken back: its fact says its holder is pressed, which on
            // a fort the side has lost is the side's own capture under way.
            boolean capturing = critical != null && critical.threatened();
            out.add(new Place(key(entry.getKey()), centre[0], centre[1], RADIUS_CELLS, status(side, fort), WORTH,
                    alarmed.test(entry.getKey()), capturing, (members, topology) -> massed(side, fort, members, topology)));
        }
        return out;
    }

    private static Map<Integer, ObjectiveAssignment> massed(Faction side, List<DefenceStructureFact> fort,
                                                            List<Body> members, CommandTopology topology) {
        List<DefenceStructureTactics.Candidate> candidates = new ArrayList<>();
        for (Body body : members) {
            candidates.add(new DefenceStructureTactics.Candidate(body.squadId(), body.x(), body.y(), body.incumbent(),
                    body.demolition(), body.machine()));
        }
        Map<Integer, ObjectiveAssignment> out = new TreeMap<>();
        for (DefenceStructureTactics.Task task : DefenceStructureTactics.planMassed(side, fort, candidates, topology)) {
            out.put(task.squadId(), task.assignment());
        }
        return out;
    }

    static int[] centre(List<DefenceStructureFact> fort) {
        long x = 0;
        long y = 0;
        for (DefenceStructureFact aspect : fort) {
            x += aspect.cellX();
            y += aspect.cellY();
        }
        return new int[] {(int) Math.round((double) x / fort.size()), (int) Math.round((double) y / fort.size())};
    }
}
