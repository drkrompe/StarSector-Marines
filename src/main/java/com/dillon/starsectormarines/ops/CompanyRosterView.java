package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SoldierCareer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The company roster: every named marine on the books on one comparable row, so the player can
 * rank the company by quality without arithmetic ({@code progression-nouns.md}, Presentation
 * law). Each axis the simulation reads is shown on an absolute four-step scale (aptitude,
 * the band the issued armour sets, the primary's grade) rather than relative to the company,
 * because a relative bar makes a company of Limited marines look uniformly excellent. The step
 * holding the company's median aptitude is marked so a marine reads against the company too.
 *
 * <p>The fallen are not listed: the roster is the people the company can still field.
 */
final class CompanyRosterView {

    /** What the roster is ordered by. Quality columns sort best first; names sort alphabetically. */
    enum Sort { NAME, SQUAD, STATUS, APTITUDE, BAND, WEAPON, ARMOR, SERVICE }

    static final int PAGE_SIZE = 16;
    private static final int STEPS = 4;

    private CompanyRosterView() {}

    /** Marines on the books, KIA excluded, in {@code sort} order with the name as tie-break. */
    static List<MarineSoldier> sorted(MarineRoster roster, Sort sort) {
        List<MarineSoldier> marines = new ArrayList<>();
        for (MarineSoldier soldier : roster.soldiers()) {
            if (soldier.status() != MarineSoldierStatus.KIA) marines.add(soldier);
        }
        Comparator<MarineSoldier> byName = Comparator.comparing(MarineSoldier::name);
        Comparator<MarineSoldier> order = switch (sort) {
            case NAME -> byName;
            case SQUAD -> Comparator.comparing((MarineSoldier s) -> squadName(roster, s));
            case STATUS -> Comparator.comparing((MarineSoldier s) -> s.status().ordinal());
            case APTITUDE -> Comparator.comparing((MarineSoldier s) -> s.aptitude().ordinal()).reversed();
            case BAND -> Comparator.comparing((MarineSoldier s) -> s.profile().experienceTier().ordinal()).reversed();
            case WEAPON -> Comparator.comparing((MarineSoldier s) -> s.primaryGrade().ordinal()).reversed();
            case ARMOR -> Comparator.comparing((MarineSoldier s) -> s.armorDef().tier()).reversed();
            case SERVICE -> Comparator.comparing((MarineSoldier s) -> s.career().missionsDeployed()).reversed();
        };
        marines.sort(sort == Sort.NAME ? byName : order.thenComparing(byName));
        return marines;
    }

    /** The aptitude step at the middle of the company's fieldable marines; null for an empty company. */
    static SoldierAptitude medianAptitude(List<MarineSoldier> marines) {
        List<SoldierAptitude> aptitudes = new ArrayList<>();
        for (MarineSoldier soldier : marines) {
            if (soldier.status() != MarineSoldierStatus.MIA) aptitudes.add(soldier.aptitude());
        }
        if (aptitudes.isEmpty()) return null;
        aptitudes.sort(Comparator.naturalOrder());
        return aptitudes.get(aptitudes.size() / 2);
    }

    static int pages(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** One page of rows, ready for the roster template. */
    static List<Map<String, Object>> rows(MarineRoster roster, Sort sort, int page, double day) {
        List<MarineSoldier> marines = sorted(roster, sort);
        SoldierAptitude median = medianAptitude(marines);
        int from = Math.max(0, Math.min(page, pages(marines.size()) - 1)) * PAGE_SIZE;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = from; i < Math.min(marines.size(), from + PAGE_SIZE); i++) {
            MarineSoldier soldier = marines.get(i);
            String id = "roster-" + soldier.id();
            SoldierCareer career = soldier.career();
            rows.add(map(
                    "id", id,
                    "classes", i % 2 == 0 ? "roster-row" : "roster-row roster-row-alt",
                    "nameId", id + ":name", "name", soldier.name(),
                    "squadId", id + ":squad", "squad", squadName(roster, soldier),
                    "statusId", id + ":status", "status", status(soldier, day),
                    "statusClasses", "label roster-status " + (soldier.status() == MarineSoldierStatus.ACTIVE
                            ? "tone-good" : "tone-danger"),
                    "aptitudeId", id + ":aptitude", "aptitude", soldier.aptitude().displayName,
                    "aptitudeSteps", steps(id + ":apt", soldier.aptitude().ordinal(),
                            median == null ? -1 : median.ordinal()),
                    "aptitudeStepsId", id + ":apt-steps", "aptitudeCellId", id + ":aptitude-cell",
                    "bandId", id + ":band", "band", soldier.profile().experienceTier().displayName,
                    "bandSteps", steps(id + ":band", soldier.profile().experienceTier().ordinal(), -1),
                    "bandStepsId", id + ":band-steps", "bandCellId", id + ":band-cell",
                    "weaponId", id + ":weapon", "weapon", Strings.format("companyRosterWeaponFmt",
                            soldier.primaryGrade().displayName(), soldier.primaryDef().displayName()),
                    "gradeSteps", steps(id + ":grade", soldier.primaryGrade().ordinal(), -1),
                    "gradeStepsId", id + ":grade-steps", "gradeCellId", id + ":grade-cell",
                    "armorId", id + ":armor", "armor", soldier.armorDef().displayName(),
                    "serviceId", id + ":service", "service", Strings.format("companyRosterServiceFmt",
                            career.missionsDeployed(), career.kills(), career.timesWounded())));
        }
        return rows;
    }

    /** Four segments, lit up to {@code step}; the {@code marked} step (the company median) carries a border. */
    static List<Map<String, Object>> steps(String prefix, int step, int marked) {
        List<Map<String, Object>> segments = new ArrayList<>();
        for (int i = 0; i < STEPS; i++) {
            String classes = "roster-step " + (i <= step ? "roster-step-on" : "roster-step-off")
                    + (i == marked ? " roster-step-median" : "");
            segments.add(map("id", prefix + ":" + i, "classes", classes));
        }
        return segments;
    }

    private static String status(MarineSoldier soldier, double day) {
        return switch (soldier.status()) {
            case ACTIVE -> Strings.get("companyRosterReady");
            case WIA -> Strings.format("companyRosterWoundedFmt",
                    Math.max(0, (int) Math.ceil(soldier.unavailableUntilDay() - day)));
            case MIA -> Strings.get("companyRosterMissing");
            case KIA -> Strings.get("companyRosterKilled");
        };
    }

    private static String squadName(MarineRoster roster, MarineSoldier soldier) {
        MarineSquad squad = roster.squadForSoldier(soldier.id());
        return squad == null ? Strings.get("companyRosterUnassigned") : squad.name();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
