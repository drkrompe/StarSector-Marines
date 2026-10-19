package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.Verdict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * What the warship scenes read off {@link MissileOpenings}: each ship's launches per opening, the openings it used and
 * the side's salvos, and the mechanical verdict that no ship spent more than its budget into one opening and that a
 * ship with more to spend than one opening allows spread it over several.
 */
public final class MissileOpeningReadings {

    private MissileOpeningReadings() {}

    /** One ship's launches into each opening it used, in order. */
    public static Map<Warship, List<MissileOpenings.Spend>> byShip(WarshipShipFire fire) {
        Map<Warship, List<MissileOpenings.Spend>> out = new LinkedHashMap<>();
        for (MissileOpenings.Spend s : fire.openings().spends()) {
            out.computeIfAbsent(s.ship(), k -> new ArrayList<>()).add(s);
        }
        return out;
    }

    /** The budget verdict, judged only where the restraint is on; {@code name} labels a ship. */
    public static Verdict verdict(WarshipShipFire fire, Function<Warship, String> name) {
        int over = 0;
        int unspread = 0;
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Warship, List<MissileOpenings.Spend>> e : byShip(fire).entrySet()) {
            int total = 0;
            int biggestBudget = 0;
            for (MissileOpenings.Spend s : e.getValue()) {
                if (s.launches() > s.budget()) over++;
                total += s.launches();
                biggestBudget = Math.max(biggestBudget, s.budget());
            }
            if (total > biggestBudget && e.getValue().size() < 2) unspread++;
            if (b.length() > 0) b.append("; ");
            b.append(name.apply(e.getKey())).append(' ').append(perOpening(e.getValue()));
        }
        String detail = String.format(Locale.ROOT, "%d openings over budget, %d ships with more than one opening's "
                + "worth in a single opening; launches per opening %s", over, unspread, b.length() == 0 ? "none" : b);
        return Verdict.of("missiles-keep-to-the-opening-budget", over == 0 && unspread == 0, detail);
    }

    /** {@code "2,2,1 of 2,2,2"}: launches into each opening against the budget there. */
    public static String perOpening(List<MissileOpenings.Spend> spends) {
        StringBuilder l = new StringBuilder();
        StringBuilder g = new StringBuilder();
        for (MissileOpenings.Spend s : spends) {
            if (l.length() > 0) {
                l.append(',');
                g.append(',');
            }
            l.append(s.launches()).append(s.kind() == MissileWindow.Kind.OVERLOAD || s.kind() == MissileWindow.Kind.WORN
                    ? "*" : "");
            g.append(s.budget());
        }
        return l + " of " + g;
    }

    /** Readings per ship under {@code prefix + name}: openings used, launches into them and the most into one. */
    public static void metrics(WarshipShipFire fire, Function<Warship, String> name, Map<String, Number> metrics) {
        metrics.put("missileOpenings", fire.openings().opened());
        metrics.put("salvosOpened", fire.salvosOpened());
        metrics.put("coordinatedLaunches", fire.coordinatedLaunches());
        for (Map.Entry<Warship, List<MissileOpenings.Spend>> e : byShip(fire).entrySet()) {
            String who = name.apply(e.getKey());
            int total = 0;
            int most = 0;
            for (MissileOpenings.Spend s : e.getValue()) {
                total += s.launches();
                most = Math.max(most, s.launches());
            }
            metrics.merge(who + "OpeningsUsed", e.getValue().size(), (a, c) -> a.intValue() + c.intValue());
            metrics.merge(who + "OpeningLaunches", total, (a, c) -> a.intValue() + c.intValue());
            metrics.merge(who + "MostIntoOneOpening", most, (a, c) -> Math.max(a.intValue(), c.intValue()));
        }
    }
}
