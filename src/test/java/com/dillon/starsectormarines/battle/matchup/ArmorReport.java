package com.dillon.starsectormarines.battle.matchup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns armor-matchup fights into report rows and text. No simulation here: a row is a pure function of the fights
 * it summarises, so it is unit-tested from hand-made results.
 */
final class ArmorReport {

    private ArmorReport() {}

    /** How many weapons a row names per side in the damage column. */
    static final int TOP_WEAPONS = 3;

    /** One pairing in one scenario, over its seeds. Means are over every fight unless the name says otherwise. */
    record Row(String pairing, String scenario, String attacker, String defender, int fights,
               int attackerWins, int defenderWins, int mutual, int stalemate, int unresolved,
               /** Mean seconds from first hit to decision over decided fights; -1 when none was decided. */
               float meanTtkSeconds,
               /** Mean over fights of each fight's median range from the first hit; -1 when nobody was hit. */
               float meanFightRange,
               float attackerLeft, float defenderLeft,
               float attackerIntegrity, float defenderIntegrity,
               float attackerHitRate, float defenderHitRate,
               float attackerShots, float defenderShots,
               float attackerMountsLost, float defenderMountsLost,
               float attackerLegsCrippled, float defenderLegsCrippled,
               float attackerFluxSeconds, float defenderFluxSeconds,
               String attackerWeapons, String defenderWeapons, String gif) {

        static Row of(String pairing, List<ArmorPlay.Result> fights, String gif) {
            if (fights.isEmpty()) throw new IllegalArgumentException("A row needs at least one fight");
            int aw = 0, dw = 0, both = 0, stale = 0, open = 0, decided = 0;
            float ttk = 0f;
            float range = 0f;
            int ranged = 0;
            float aLeft = 0, dLeft = 0, aInt = 0, dInt = 0, aShots = 0, dShots = 0, aHits = 0, dHits = 0;
            float aMounts = 0, dMounts = 0, aLegs = 0, dLegs = 0, aFlux = 0, dFlux = 0;
            Map<String, ArmorPlay.WeaponStats> aWeapons = new TreeMap<>();
            Map<String, ArmorPlay.WeaponStats> dWeapons = new TreeMap<>();
            for (ArmorPlay.Result r : fights) {
                switch (r.winner()) {
                    case "ATTACKERS" -> aw++;
                    case "DEFENDERS" -> dw++;
                    case "BOTH_DESTROYED" -> both++;
                    case "STALEMATE" -> stale++;
                    default -> open++;
                }
                if (r.timeToKillSeconds() >= 0f) {
                    ttk += r.timeToKillSeconds();
                    decided++;
                }
                if (r.fightRange() >= 0f) {
                    range += r.fightRange();
                    ranged++;
                }
                ArmorPlay.SideStats a = r.attackers();
                ArmorPlay.SideStats d = r.defenders();
                aLeft += a.left();
                dLeft += d.left();
                aInt += a.integrityFraction();
                dInt += d.integrityFraction();
                aShots += a.shots();
                dShots += d.shots();
                aHits += a.hits();
                dHits += d.hits();
                aMounts += a.mountsLost();
                dMounts += d.mountsLost();
                aLegs += a.legsCrippled();
                dLegs += d.legsCrippled();
                aFlux += a.fluxLimitedSeconds();
                dFlux += d.fluxLimitedSeconds();
                a.weapons().forEach((k, v) -> aWeapons.merge(k, v, ArmorPlay.WeaponStats::plus));
                d.weapons().forEach((k, v) -> dWeapons.merge(k, v, ArmorPlay.WeaponStats::plus));
            }
            int n = fights.size();
            ArmorPlay.Result first = fights.get(0);
            return new Row(pairing, first.scenario(), first.attacker(), first.defender(), n, aw, dw, both, stale,
                    open, decided == 0 ? -1f : ttk / decided, ranged == 0 ? -1f : range / ranged,
                    aLeft / n, dLeft / n, aInt / n, dInt / n,
                    aShots == 0 ? 0f : aHits / aShots, dShots == 0 ? 0f : dHits / dShots,
                    aShots / n, dShots / n, aMounts / n, dMounts / n, aLegs / n, dLegs / n, aFlux / n, dFlux / n,
                    weaponText(aWeapons), weaponText(dWeapons), gif == null ? "" : gif);
        }
    }

    /**
     * The weapons that did the most damage, each with its share of the side's damage and its hit rate:
     * {@code mech-srm 61% (h 48%), mech-chaingun 39% (h 22%)}. A weapon that fired and never hit still appears.
     */
    static String weaponText(Map<String, ArmorPlay.WeaponStats> weapons) {
        float total = 0f;
        for (ArmorPlay.WeaponStats w : weapons.values()) total += w.damage();
        List<Map.Entry<String, ArmorPlay.WeaponStats>> sorted = new ArrayList<>(weapons.entrySet());
        sorted.sort((x, y) -> {
            int byDamage = Float.compare(y.getValue().damage(), x.getValue().damage());
            if (byDamage != 0) return byDamage;
            int byShots = Integer.compare(y.getValue().shots(), x.getValue().shots());
            return byShots != 0 ? byShots : x.getKey().compareTo(y.getKey());
        });
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_WEAPONS, sorted.size()); i++) {
            ArmorPlay.WeaponStats w = sorted.get(i).getValue();
            int share = total <= 0f ? 0 : Math.round(100f * w.damage() / total);
            int hit = w.shots() == 0 ? 0 : Math.round(100f * w.hits() / w.shots());
            parts.add(sorted.get(i).getKey() + " " + share + "% (h " + hit + "%)");
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    static String markdownHeader() {
        return "| Matchup | Att wins | Def wins | Both dead | Stalemate | Timeout | TTK s | Range | Att left | Def left "
                + "| Att integrity | Def integrity | Att hit | Def hit | Mounts lost A/D | Legs crippled A/D "
                + "| Flux-limited s A/D | Att damage by weapon | Def damage by weapon | Replay |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n";
    }

    static String markdownRow(Row r) {
        return "| " + r.pairing + " | " + r.attackerWins + "/" + r.fights + " | " + r.defenderWins + "/" + r.fights
                + " | " + r.mutual + " | " + r.stalemate + " | " + r.unresolved
                + " | " + (r.meanTtkSeconds < 0f ? "-" : f1(r.meanTtkSeconds))
                + " | " + (r.meanFightRange < 0f ? "-" : f1(r.meanFightRange))
                + " | " + f1(r.attackerLeft) + " | " + f1(r.defenderLeft)
                + " | " + pct(r.attackerIntegrity) + " | " + pct(r.defenderIntegrity)
                + " | " + pct(r.attackerHitRate) + " | " + pct(r.defenderHitRate)
                + " | " + f1(r.attackerMountsLost) + "/" + f1(r.defenderMountsLost)
                + " | " + f1(r.attackerLegsCrippled) + "/" + f1(r.defenderLegsCrippled)
                + " | " + f1(r.attackerFluxSeconds) + "/" + f1(r.defenderFluxSeconds)
                + " | " + r.attackerWeapons + " | " + r.defenderWeapons
                + " | " + (r.gif.isEmpty() ? "" : "[gif](" + r.gif.replace(" ", "%20") + ")") + " |\n";
    }

    static final String CSV_HEADER = "pairing,scenario,attacker,defender,fights,attacker_wins,defender_wins,"
            + "both_destroyed,stalemate,timeout,mean_ttk_s,fight_range,attacker_left,defender_left,attacker_integrity,"
            + "defender_integrity,attacker_hit_rate,defender_hit_rate,attacker_shots,defender_shots,"
            + "attacker_mounts_lost,defender_mounts_lost,attacker_legs_crippled,defender_legs_crippled,"
            + "attacker_flux_limited_s,defender_flux_limited_s,attacker_weapons,defender_weapons\n";

    static String csvRow(Row r) {
        return q(r.pairing) + ',' + r.scenario + ',' + q(r.attacker) + ',' + q(r.defender) + ',' + r.fights + ','
                + r.attackerWins + ',' + r.defenderWins + ',' + r.mutual + ',' + r.stalemate + ',' + r.unresolved + ','
                + f1(r.meanTtkSeconds) + ',' + f1(r.meanFightRange) + ',' + f1(r.attackerLeft) + ',' + f1(r.defenderLeft) + ','
                + f2(r.attackerIntegrity) + ',' + f2(r.defenderIntegrity) + ','
                + f2(r.attackerHitRate) + ',' + f2(r.defenderHitRate) + ','
                + f1(r.attackerShots) + ',' + f1(r.defenderShots) + ','
                + f1(r.attackerMountsLost) + ',' + f1(r.defenderMountsLost) + ','
                + f1(r.attackerLegsCrippled) + ',' + f1(r.defenderLegsCrippled) + ','
                + f1(r.attackerFluxSeconds) + ',' + f1(r.defenderFluxSeconds) + ','
                + q(r.attackerWeapons) + ',' + q(r.defenderWeapons) + '\n';
    }

    static final String RUNS_HEADER = "scenario,attacker,defender,seed,winner,ticks,first_damage_tick,ttk_s,fight_range,"
            + "attacker_left,defender_left,attacker_integrity,defender_integrity,attacker_damage_dealt,"
            + "defender_damage_dealt,attacker_shots,attacker_hits,defender_shots,defender_hits,"
            + "attacker_mounts_lost,defender_mounts_lost,attacker_legs_crippled,defender_legs_crippled,"
            + "attacker_flux_limited_s,defender_flux_limited_s,attacker_survivors,defender_survivors,"
            + "attacker_weapons,defender_weapons,attacker_rounds_engaged,attacker_rounds_stopped,"
            + "defender_rounds_engaged,defender_rounds_stopped,gif\n";

    static String runRow(ArmorPlay.Result r, String gif) {
        ArmorPlay.SideStats a = r.attackers();
        ArmorPlay.SideStats d = r.defenders();
        return r.scenario() + ',' + q(r.attacker()) + ',' + q(r.defender()) + ',' + r.seed() + ',' + r.winner() + ','
                + r.ticks() + ',' + r.firstDamageTick() + ',' + f1(r.timeToKillSeconds()) + ',' + f1(r.fightRange()) + ','
                + a.left() + ',' + d.left() + ',' + f2(a.integrityFraction()) + ',' + f2(d.integrityFraction()) + ','
                + f1(a.damageDealt()) + ',' + f1(d.damageDealt()) + ','
                + a.shots() + ',' + a.hits() + ',' + d.shots() + ',' + d.hits() + ','
                + a.mountsLost() + ',' + d.mountsLost() + ',' + a.legsCrippled() + ',' + d.legsCrippled() + ','
                + f1(a.fluxLimitedSeconds()) + ',' + f1(d.fluxLimitedSeconds()) + ','
                + q(a.survivors()) + ',' + q(d.survivors()) + ','
                + q(weaponText(a.weapons())) + ',' + q(weaponText(d.weapons())) + ','
                + a.roundsEngaged() + ',' + a.roundsStopped() + ',' + d.roundsEngaged() + ',' + d.roundsStopped() + ','
                + (gif == null ? "" : gif) + '\n';
    }

    static String q(String s) {
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    static String f1(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static String f2(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String pct(float fraction) {
        return Math.round(100f * fraction) + "%";
    }
}
