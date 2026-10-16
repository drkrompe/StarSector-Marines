package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the stat sheet for every loadout card: what each one carries seat by seat, the numbers a
 * design pass scales against, and which factions sell it through which channel. Read from the
 * shipped data, so it cannot drift from it. Evidence rather than a test: it asserts nothing.
 *
 * <p>Strength is the game's own measure, {@link LoadoutEffectiveness} (the numbers behind the Armory's
 * FIRE and GUARD ratings), plus matchup columns against three fixed targets and three fixed fire
 * penetrations, so a weapon that only works on bare marines, or a suit that only stops light fire,
 * shows. Reach, specials, mobility and systems are separate columns because folding them into one
 * scalar would be an invention.
 */
@Tag("loadout-card-sheet")
class LoadoutCardSheetEvidence {

    /** Armour ratings a weapon is scored against: a bare marine, standard line plate, a power suit. */
    private static final float[] TARGET_RATINGS = {0f, 8f, 12f};
    private static final String[] TARGET_NAMES = {"bare", "line", "suit"};
    /** Penetrations a suit is scored against: submachine-gun fire, rifle fire, marksman/rail fire. */
    private static final float[] FIRE_PENETRATIONS = {1f, 7f, 14f};
    private static final String[] FIRE_NAMES = {"pen 1", "pen 7", "pen 14"};

    private static final String[] CSV_HEADER = {
            "card", "kind", "name", "access_tier", "unlock", "templates", "sources", "rating",
            "reference_total", "vs_a", "vs_b", "vs_c", "power_suits", "armor_capacity_total",
            "mean_speed", "range_min", "range_avg", "range_max", "specials", "mix"};

    @Test
    void writesTheCardSheet() throws Exception {
        Path out = Path.of(System.getProperty("loadoutCardSheet.dir", "build/reports/loadout-cards"));
        Files.createDirectories(out);
        StringBuilder md = new StringBuilder();
        List<String[]> csv = new ArrayList<>();
        md.append("# Loadout card stat sheet\n\n")
                .append("Generated from the shipped data by `gradlew loadoutCardSheet`.\n\n")
                .append("**Strength** is `LoadoutEffectiveness`, the measure behind the Armory's FIRE and GUARD ratings. ")
                .append("Effective DPS is aimed output after grade, times hit chance at half the weapon's own range, times the ")
                .append("share that gets through the target's plate. A rating is a percentage of the best twelve billets the ")
                .append("catalog could field, so it falls as better items are added. Guard is damage absorbed before a wearer ")
                .append("is wounded, with evasion folded in. Both leave out cover, morale, terrain and orders.\n\n")
                .append("**Matchups.** Weapons are scored against ").append(describe(TARGET_NAMES, TARGET_RATINGS, "armor rating"))
                .append(". Suits are scored against ").append(describe(FIRE_NAMES, FIRE_PENETRATIONS, "penetration"))
                .append(". A large gap across a row is a card that works against one opponent and not another.\n\n")
                .append("**Not in the ratings, shown separately:** reach (range), specials, mobility (speed) and integral systems.\n\n")
                .append("Access: ").append(unlockLine(EquipmentAccessTier.COMMON)).append("; ")
                .append(unlockLine(EquipmentAccessTier.ADVANCED)).append("; ")
                .append(unlockLine(EquipmentAccessTier.PRESTIGE)).append(".\n\n");

        List<LoadoutCard> weapons = new ArrayList<>(), sheets = new ArrayList<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            (card.kind() == LoadoutCard.Kind.WEAPON ? weapons : sheets).add(card);
        }

        md.append("## Weapon loadouts: summary\n\n| Card | Tier | FIRE rating | Eff. DPS | vs bare | vs line | vs suit | Range min/avg/max | Specials |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        for (LoadoutCard card : weapons) {
            WeaponStats s = weaponStats(card);
            md.append("| ").append(card.displayName()).append(" | ").append(tier(card)).append(" | ").append(s.rating)
                    .append(" | ").append(f0(s.reference)).append(" | ").append(f0(s.vs[0])).append(" | ")
                    .append(f0(s.vs[1])).append(" | ").append(f0(s.vs[2])).append(" | ")
                    .append(num(s.rangeMin)).append("/").append(num(s.rangeAvg)).append("/").append(num(s.rangeMax))
                    .append(" | ").append(s.specials).append(" |\n");
        }
        md.append("\n## Armor / equipment sheets: summary\n\n| Card | Tier | GUARD rating | Absorbs | ")
                .append(FIRE_NAMES[0]).append(" | ").append(FIRE_NAMES[1]).append(" | ").append(FIRE_NAMES[2])
                .append(" | Power suits | Armor capacity | Mean speed | Systems |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (LoadoutCard card : sheets) {
            SheetStats s = sheetStats(card);
            md.append("| ").append(card.displayName()).append(" | ").append(tier(card)).append(" | ").append(s.rating)
                    .append(" | ").append(f0(s.reference)).append(" | ").append(f0(s.vs[0])).append(" | ")
                    .append(f0(s.vs[1])).append(" | ").append(f0(s.vs[2])).append(" | ").append(s.suits).append("/12 | ")
                    .append(num(s.capacity)).append(" | ").append(String.format(Locale.ROOT, "%.2f", s.meanSpeed))
                    .append(" | ").append(s.systems).append(" |\n");
        }

        md.append("\n## Weapon loadouts\n");
        for (LoadoutCard card : weapons) weaponSection(md, csv, card);
        md.append("\n## Armor / equipment sheets\n");
        for (LoadoutCard card : sheets) sheetSection(md, csv, card);

        md.append("\n## Armor pattern reference\n\n")
                .append("| Pattern | Class | Role | Tradition | Tier | Capacity | Rating | Speed | Hit mult | Absorbs | ")
                .append(FIRE_NAMES[0]).append(" | ").append(FIRE_NAMES[1]).append(" | ").append(FIRE_NAMES[2])
                .append(" | System |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        List<MarineArmorCatalogDef> patterns = new ArrayList<>(MarineArmorCatalogRegistry.installed().all());
        patterns.sort((a, b) -> a.tier() != b.tier() ? Integer.compare(a.tier(), b.tier()) : a.id().compareTo(b.id()));
        for (MarineArmorCatalogDef p : patterns) {
            md.append("| ").append(p.id()).append(" | ").append(p.armorClass().displayName()).append(" | ")
                    .append(p.role().key).append(" | ").append(p.tradition()).append(" | ").append(p.tier())
                    .append(" | ").append(num(p.armorCapacity())).append(" | ").append(num(p.armorRating()))
                    .append(" | ").append(num(p.moveSpeedMult())).append(" | ").append(num(p.incomingAccuracyMult()))
                    .append(" | ").append(f0(LoadoutEffectiveness.patternResilience(p)));
            for (float pen : FIRE_PENETRATIONS) md.append(" | ").append(f0(LoadoutEffectiveness.patternResilience(p, pen)));
            md.append(" | ").append(p.integralSystem() == null ? "" : p.integralSystem().id()
                    + " (" + p.integralSystem().grade().name().toLowerCase(Locale.ROOT) + ")").append(" |\n");
        }
        md.append("\n## Primary weapon reference\n\n| Weapon | Grade | Range | Damage | Accuracy | Cooldown | Pen | Eff. DPS | vs bare | vs line | vs suit |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String id : List.of("weapon.field-rifle", "weapon.pulse-rifle", "weapon.smg",
                "weapon.squad-automatic", "weapon.dmr")) {
            WeaponDef w = com.dillon.starsectormarines.battle.weapon.WeaponRegistry.require(id);
            for (EquipmentGrade g : EquipmentGrade.values()) {
                md.append("| ").append(id).append(" | ").append(g.name().toLowerCase(Locale.ROOT)).append(" | ")
                        .append(num(w.range * g.rangeMult)).append(" | ").append(num(w.damage * g.damageMult))
                        .append(" | ").append(num(w.accuracy * g.accuracyMult)).append(" | ")
                        .append(num(w.cooldown * g.cooldownMult)).append(" | ").append(num(w.penetration))
                        .append(" | ").append(f1(LoadoutEffectiveness.effectiveDps(w, g)));
                for (float armor : TARGET_RATINGS) {
                    md.append(" | ").append(f1(LoadoutEffectiveness.effectiveDps(w, g, armor)));
                }
                md.append(" |\n");
            }
        }

        Files.writeString(out.resolve("cards.md"), md.toString());
        StringBuilder csvText = new StringBuilder(String.join(",", CSV_HEADER)).append('\n');
        for (String[] row : csv) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) csvText.append(',');
                csvText.append('"').append(row[i].replace("\"", "\"\"")).append('"');
            }
            csvText.append('\n');
        }
        Files.writeString(out.resolve("cards.csv"), csvText.toString());
        System.out.println("Loadout card sheet: " + out.toAbsolutePath() + " (" + csv.size() + " cards)");
    }

    private static final class WeaponStats {
        int rating;
        float reference;
        final float[] vs = new float[TARGET_RATINGS.length];
        float rangeMin = Float.MAX_VALUE, rangeMax, rangeAvg;
        String specials = "";
    }

    private static final class SheetStats {
        int rating;
        float reference, capacity, meanSpeed;
        final float[] vs = new float[FIRE_PENETRATIONS.length];
        int suits;
        String systems = "";
    }

    private static WeaponStats weaponStats(LoadoutCard card) {
        WeaponStats s = new WeaponStats();
        SquadWeaponDoctrine doctrine = new SquadWeaponDoctrine(card.definitionId(), card.displayName(), "", issues(card));
        s.rating = LoadoutEffectiveness.weaponRating(doctrine);
        s.reference = LoadoutEffectiveness.firepower(doctrine);
        Map<String, Integer> specials = new LinkedHashMap<>();
        float range = 0f;
        for (SquadWeaponIssue issue : doctrine.issues()) {
            WeaponDef w = issue.primaryDef();
            float r = w.range * issue.grade().rangeMult;
            s.rangeMin = Math.min(s.rangeMin, r);
            s.rangeMax = Math.max(s.rangeMax, r);
            range += r;
            for (int i = 0; i < TARGET_RATINGS.length; i++) {
                s.vs[i] += LoadoutEffectiveness.effectiveDps(w, issue.grade(), TARGET_RATINGS[i]);
            }
            if (issue.specialEquipmentId() != null) {
                specials.merge(issue.specialEquipmentId().replace("special.", ""), 1, Integer::sum);
            }
        }
        s.rangeAvg = range / doctrine.issues().size();
        s.specials = specials.isEmpty() ? "none" : specials.toString();
        return s;
    }

    private static SheetStats sheetStats(LoadoutCard card) {
        SheetStats s = new SheetStats();
        SquadArmorDoctrine issued = ArmorIssueResolver.issueSheet(plan(card));
        s.rating = LoadoutEffectiveness.armorRating(issued);
        s.reference = LoadoutEffectiveness.protection(issued);
        Map<String, Integer> systems = new LinkedHashMap<>();
        for (String id : issued.issueIds()) {
            MarineArmorCatalogDef d = MarineArmorCatalogRegistry.require(id);
            s.capacity += d.armorCapacity();
            s.meanSpeed += d.moveSpeedMult() / issued.issueIds().size();
            if (d.armorClass() == ArmorClass.POWER_SUIT) s.suits++;
            for (int i = 0; i < FIRE_PENETRATIONS.length; i++) {
                s.vs[i] += LoadoutEffectiveness.patternResilience(d, FIRE_PENETRATIONS[i]);
            }
            if (d.integralSystem() != null) systems.merge(d.integralSystem().id().replace("system.", ""), 1, Integer::sum);
        }
        s.systems = systems.isEmpty() ? "none" : systems.toString();
        return s;
    }

    private static void weaponSection(StringBuilder md, List<String[]> csv, LoadoutCard card) {
        List<SquadWeaponIssue> issues = issues(card);
        WeaponStats s = weaponStats(card);
        md.append("\n### ").append(card.displayName()).append("\n\n`").append(card.id()).append("`  \n");
        md.append(header(card));
        md.append("\n| Seat | Role | Weapon | Grade | Range | Damage | Accuracy | Cooldown | Pen | Eff. DPS | vs bare | vs line | vs suit | Rating | Special |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < issues.size(); i++) {
            SquadWeaponIssue issue = issues.get(i);
            WeaponDef w = issue.primaryDef();
            EquipmentGrade g = issue.grade();
            counts.merge(w.id.replace("weapon.", "") + ":" + g.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
            md.append("| ").append(i + 1).append(" | ").append(issue.role()).append(" | ").append(w.id)
                    .append(" | ").append(g.name().toLowerCase(Locale.ROOT)).append(" | ")
                    .append(num(w.range * g.rangeMult)).append(" | ").append(num(w.damage * g.damageMult))
                    .append(" | ").append(num(w.accuracy * g.accuracyMult)).append(" | ")
                    .append(num(w.cooldown * g.cooldownMult)).append(" | ").append(num(w.penetration))
                    .append(" | ").append(f1(LoadoutEffectiveness.effectiveDps(w, g)));
            for (float armor : TARGET_RATINGS) md.append(" | ").append(f1(LoadoutEffectiveness.effectiveDps(w, g, armor)));
            md.append(" | ").append(LoadoutEffectiveness.billetWeaponRating(w, g)).append(" | ")
                    .append(special(issue)).append(" |\n");
        }
        md.append("\nSquad FIRE rating **").append(s.rating).append("**, effective DPS **").append(f0(s.reference))
                .append("** (bare ").append(f0(s.vs[0])).append(", line ").append(f0(s.vs[1])).append(", suit ")
                .append(f0(s.vs[2])).append("; suit/bare ").append(pct(s.vs[2], s.vs[0])).append("). Range ")
                .append(num(s.rangeMin)).append("/").append(num(s.rangeAvg)).append("/").append(num(s.rangeMax))
                .append(". Specials: ").append(s.specials).append(". Mix: ").append(counts).append("\n");
        csv.add(new String[] {card.id(), "weapon", card.displayName(), card.accessTier().name(),
                unlockLine(card.accessTier()), String.join(" ", card.templateIds()), sources(card),
                String.valueOf(s.rating), f1(s.reference), f1(s.vs[0]), f1(s.vs[1]), f1(s.vs[2]), "", "", "",
                num(s.rangeMin), num(s.rangeAvg), num(s.rangeMax), s.specials, counts.toString()});
    }

    private static void sheetSection(StringBuilder md, List<String[]> csv, LoadoutCard card) {
        SquadArmorPlan plan = plan(card);
        SquadArmorDoctrine issued = ArmorIssueResolver.issueSheet(plan);
        SheetStats s = sheetStats(card);
        md.append("\n### ").append(card.displayName()).append("\n\n`").append(card.id()).append("`  \n");
        md.append(header(card));
        md.append("\n| Seat | Job | Class | Pattern | Tier | Capacity | Rating | Speed | Hit mult | Absorbs | ")
                .append(FIRE_NAMES[0]).append(" | ").append(FIRE_NAMES[1]).append(" | ").append(FIRE_NAMES[2])
                .append(" | Rating | System |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < issued.issueIds().size(); i++) {
            MarineArmorCatalogDef d = MarineArmorCatalogRegistry.require(issued.issueId(i));
            counts.merge(d.id().replace("armor.", ""), 1, Integer::sum);
            md.append("| ").append(i + 1).append(" | ").append(plan.roleAt(i).key).append(" | ")
                    .append(d.armorClass().displayName()).append(" | ").append(d.id()).append(" | ")
                    .append(d.tier()).append(" | ").append(num(d.armorCapacity())).append(" | ")
                    .append(num(d.armorRating())).append(" | ").append(num(d.moveSpeedMult())).append(" | ")
                    .append(num(d.incomingAccuracyMult())).append(" | ")
                    .append(f0(LoadoutEffectiveness.patternResilience(d)));
            for (float pen : FIRE_PENETRATIONS) md.append(" | ").append(f0(LoadoutEffectiveness.patternResilience(d, pen)));
            md.append(" | ").append(LoadoutEffectiveness.billetArmorRating(d)).append(" | ")
                    .append(d.integralSystem() == null ? "" : d.integralSystem().id()).append(" |\n");
        }
        md.append("\nSquad GUARD rating **").append(s.rating).append("**, absorbs **").append(f0(s.reference))
                .append("** (").append(FIRE_NAMES[0]).append(" ").append(f0(s.vs[0])).append(", ").append(FIRE_NAMES[1])
                .append(" ").append(f0(s.vs[1])).append(", ").append(FIRE_NAMES[2]).append(" ").append(f0(s.vs[2]))
                .append("). Power suits **").append(s.suits).append("/12**, armor capacity ").append(num(s.capacity))
                .append(", mean speed ").append(String.format(Locale.ROOT, "%.2f", s.meanSpeed))
                .append(". Systems: ").append(s.systems).append(". Mix: ").append(counts).append("\n");
        csv.add(new String[] {card.id(), "sheet", card.displayName(), card.accessTier().name(),
                unlockLine(card.accessTier()), String.join(" ", card.templateIds()), sources(card),
                String.valueOf(s.rating), f1(s.reference), f1(s.vs[0]), f1(s.vs[1]), f1(s.vs[2]),
                String.valueOf(s.suits), num(s.capacity), String.format(Locale.ROOT, "%.2f", s.meanSpeed),
                "", "", "", s.systems, counts.toString()});
    }

    private static String header(LoadoutCard card) {
        return "Tier: **" + tier(card) + "** (" + unlockLine(card.accessTier())
                + ")  \nTemplates: " + card.templateIds() + "  \nSold by: " + sources(card) + "\n";
    }

    /** Every faction channel that offers the card: any bundled template fielded there, at its heaviest weight. */
    private static String sources(LoadoutCard card) {
        Map<String, Integer> found = new TreeMap<>();
        List<String> templates = card.templateIds();
        for (FactionEquipmentPool pool : FactionEquipmentCatalog.installed().entries()) {
            EnumMap<FactionEquipmentSource, Integer> best = new EnumMap<>(FactionEquipmentSource.class);
            for (FactionEquipmentOffer offer : pool.offers()) {
                if (!templates.contains(offer.templateId())) continue;
                for (FactionEquipmentSource source : offer.sources()) {
                    // A market only ever sells common cards, whatever its bundled templates' own tiers.
                    if (source == FactionEquipmentSource.MARKET && card.accessTier() != EquipmentAccessTier.COMMON) {
                        continue;
                    }
                    best.merge(source, offer.weight(source), Math::max);
                }
            }
            for (Map.Entry<FactionEquipmentSource, Integer> e : best.entrySet()) {
                found.put(pool.factionId() + ":" + e.getKey().name().toLowerCase(Locale.ROOT), e.getValue());
            }
        }
        return found.isEmpty() ? "story / not sold" : found.toString();
    }

    private static String unlockLine(EquipmentAccessTier tier) {
        return switch (tier) {
            case COMMON -> "common: market";
            case ADVANCED -> "advanced: MRB rep " + EquipmentAcquisitionEligibility.requiredMrb(tier)
                    + " (licence/patron) or " + EquipmentAcquisitionEligibility.requiredRecoveryVictories(tier)
                    + " victories (recovery)";
            case PRESTIGE -> "prestige: MRB rep " + EquipmentAcquisitionEligibility.requiredMrb(tier)
                    + " (licence/patron) or " + EquipmentAcquisitionEligibility.requiredRecoveryVictories(tier)
                    + " victories (recovery)";
            case RESTRICTED -> "restricted: military warrant from its issuing tradition (issuer licence/patron only)";
        };
    }

    private static String tier(LoadoutCard card) {
        return card.accessTier().name().toLowerCase(Locale.ROOT);
    }

    private static String special(SquadWeaponIssue issue) {
        if (issue.specialEquipmentId() == null) return "";
        SpecialEquipmentDef def = issue.specialDef();
        return def.id().replace("special.", "") + (def.usesAmmunition() ? " x" + def.startingAmmo() : "");
    }

    private static String describe(String[] names, float[] values, String unit) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) text.append(i == names.length - 1 ? " and " : ", ");
            text.append(names[i]).append(" (").append(unit).append(' ').append(num(values[i])).append(')');
        }
        return text.toString();
    }

    private static List<SquadWeaponIssue> issues(LoadoutCard card) {
        return LoadoutCardIssue.weapons(card.definitionId());
    }

    private static SquadArmorPlan plan(LoadoutCard card) {
        return LoadoutCardIssue.plan(card.definitionId());
    }

    private static String pct(float a, float b) {
        return b <= 0f ? "n/a" : Math.round(a / b * 100f) + "%";
    }

    private static String f0(float value) { return String.format(Locale.ROOT, "%.0f", value); }

    private static String f1(float value) { return String.format(Locale.ROOT, "%.1f", value); }

    private static String num(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
