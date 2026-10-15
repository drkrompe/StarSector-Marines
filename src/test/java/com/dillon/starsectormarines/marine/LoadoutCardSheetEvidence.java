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
 */
@Tag("loadout-card-sheet")
class LoadoutCardSheetEvidence {

    private static final String[] CSV_HEADER = {
            "card", "kind", "name", "access_tier", "unlock", "templates", "sources",
            "armor_capacity_total", "power_suits", "paper_dps_total", "armor_patterns", "weapons"};

    @Test
    void writesTheCardSheet() throws Exception {
        Path out = Path.of(System.getProperty("loadoutCardSheet.dir",
                "build/reports/loadout-cards"));
        Files.createDirectories(out);
        StringBuilder md = new StringBuilder();
        List<String[]> csv = new ArrayList<>();
        md.append("# Loadout card stat sheet\n\n")
                .append("Generated from the shipped data by `gradlew loadoutCardSheet`. Paper DPS is damage x ")
                .append("projectiles x burst / cooldown after grade, ignoring accuracy, range and penetration. ")
                .append("Armor capacity / rating are the catalog values; their battle effect is not modelled here.\n\n")
                .append("Access: ").append(unlockLine(EquipmentAccessTier.COMMON)).append("; ")
                .append(unlockLine(EquipmentAccessTier.ADVANCED)).append("; ")
                .append(unlockLine(EquipmentAccessTier.PRESTIGE)).append(".\n\n");

        List<LoadoutCard> weapons = new ArrayList<>(), sheets = new ArrayList<>();
        for (LoadoutCard card : LoadoutCards.all()) {
            (card.kind() == LoadoutCard.Kind.WEAPON ? weapons : sheets).add(card);
        }
        md.append("## Summary\n\n| Card | Kind | Tier | Power suits | Armor cap (squad) | Paper DPS (squad) |\n")
                .append("|---|---|---|---|---|---|\n");
        List<Summary> rows = new ArrayList<>();
        for (LoadoutCard card : weapons) rows.add(summarise(card));
        for (LoadoutCard card : sheets) rows.add(summarise(card));
        for (Summary row : rows) {
            md.append("| ").append(row.name).append(" | ").append(row.kind).append(" | ").append(row.tier)
                    .append(" | ").append(row.suits).append(" | ").append(row.armor).append(" | ")
                    .append(row.dps).append(" |\n");
        }

        md.append("\n## Weapon loadouts\n");
        for (LoadoutCard card : weapons) weaponSection(md, csv, card);
        md.append("\n## Armor / equipment sheets\n");
        for (LoadoutCard card : sheets) sheetSection(md, csv, card);

        md.append("\n## Armor pattern reference\n\n")
                .append("| Pattern | Class | Role | Tradition | Tier | Capacity | Rating | Speed | Incoming-hit mult | System |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|\n");
        List<MarineArmorCatalogDef> patterns = new ArrayList<>(MarineArmorCatalogRegistry.installed().all());
        patterns.sort((a, b) -> a.tier() != b.tier() ? Integer.compare(a.tier(), b.tier()) : a.id().compareTo(b.id()));
        for (MarineArmorCatalogDef p : patterns) {
            md.append("| ").append(p.id()).append(" | ").append(p.armorClass().displayName()).append(" | ")
                    .append(p.role().key).append(" | ").append(p.tradition()).append(" | ").append(p.tier())
                    .append(" | ").append(num(p.armorCapacity())).append(" | ").append(num(p.armorRating()))
                    .append(" | ").append(num(p.moveSpeedMult())).append(" | ").append(num(p.incomingAccuracyMult()))
                    .append(" | ").append(p.integralSystem() == null ? "" : p.integralSystem().id()
                            + " (" + p.integralSystem().grade().name().toLowerCase(Locale.ROOT) + ")")
                    .append(" |\n");
        }
        md.append("\n## Weapon grade multipliers\n\n| Grade | Range | Damage | Accuracy | Cooldown | Spread |\n|---|---|---|---|---|---|\n");
        for (EquipmentGrade g : EquipmentGrade.values()) {
            md.append("| ").append(g.name().toLowerCase(Locale.ROOT)).append(" | ").append(num(g.rangeMult))
                    .append(" | ").append(num(g.damageMult)).append(" | ").append(num(g.accuracyMult))
                    .append(" | ").append(num(g.cooldownMult)).append(" | ").append(num(g.spreadMult)).append(" |\n");
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

    private record Summary(String name, String kind, String tier, String suits, String armor, String dps) {}

    private static Summary summarise(LoadoutCard card) {
        if (card.kind() == LoadoutCard.Kind.WEAPON) {
            double dps = 0;
            for (SquadWeaponIssue issue : issues(card)) dps += paperDps(issue.primaryDef(), issue.grade());
            return new Summary(card.displayName(), "weapon", card.accessTier().name().toLowerCase(Locale.ROOT),
                    "", "", String.format(Locale.ROOT, "%.0f", dps));
        }
        SquadArmorDoctrine issued = ArmorIssueResolver.issueSheet(plan(card));
        float capacity = 0;
        int suits = 0;
        for (String id : issued.issueIds()) {
            MarineArmorCatalogDef def = MarineArmorCatalogRegistry.require(id);
            capacity += def.armorCapacity();
            if (def.armorClass() == ArmorClass.POWER_SUIT) suits++;
        }
        return new Summary(card.displayName(), "sheet", card.accessTier().name().toLowerCase(Locale.ROOT),
                suits + "/12", num(capacity), "");
    }

    private static void weaponSection(StringBuilder md, List<String[]> csv, LoadoutCard card) {
        List<SquadWeaponIssue> issues = issues(card);
        md.append("\n### ").append(card.displayName()).append("\n\n`").append(card.id()).append("`  \n");
        md.append(header(card));
        md.append("\n| Seat | Role | Weapon | Grade | Range | Damage | Accuracy | Cooldown | Pen | Paper DPS | Special |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        double total = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < issues.size(); i++) {
            SquadWeaponIssue issue = issues.get(i);
            WeaponDef w = issue.primaryDef();
            EquipmentGrade g = issue.grade();
            double dps = paperDps(w, g);
            total += dps;
            counts.merge(w.id.replace("weapon.", "") + ":" + g.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
            md.append("| ").append(i + 1).append(" | ").append(issue.role()).append(" | ").append(w.id)
                    .append(" | ").append(g.name().toLowerCase(Locale.ROOT)).append(" | ")
                    .append(num(w.range * g.rangeMult)).append(" | ").append(num(w.damage * g.damageMult))
                    .append(" | ").append(num(w.accuracy * g.accuracyMult)).append(" | ")
                    .append(num(w.cooldown * g.cooldownMult)).append(" | ").append(num(w.penetration))
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f", dps)).append(" | ")
                    .append(special(issue)).append(" |\n");
        }
        md.append("\nSquad paper DPS: **").append(String.format(Locale.ROOT, "%.0f", total)).append("**. Mix: ")
                .append(counts).append("\n");
        csv.add(new String[] {card.id(), "weapon", card.displayName(), card.accessTier().name(),
                unlockLine(card.accessTier()), String.join(" ", card.templateIds()), sources(card), "", "",
                String.format(Locale.ROOT, "%.0f", total), "", counts.toString()});
    }

    private static void sheetSection(StringBuilder md, List<String[]> csv, LoadoutCard card) {
        SquadArmorPlan plan = plan(card);
        SquadArmorDoctrine issued = ArmorIssueResolver.issueSheet(plan);
        md.append("\n### ").append(card.displayName()).append("\n\n`").append(card.id()).append("`  \n");
        md.append(header(card));
        md.append("\n| Seat | Job | Class | Pattern | Tier | Capacity | Rating | Speed | Hit mult | System |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|\n");
        float capacity = 0;
        int suits = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < issued.issueIds().size(); i++) {
            MarineArmorCatalogDef d = MarineArmorCatalogRegistry.require(issued.issueId(i));
            capacity += d.armorCapacity();
            if (d.armorClass() == ArmorClass.POWER_SUIT) suits++;
            counts.merge(d.id().replace("armor.", ""), 1, Integer::sum);
            md.append("| ").append(i + 1).append(" | ").append(plan.roleAt(i).key).append(" | ")
                    .append(d.armorClass().displayName()).append(" | ").append(d.id()).append(" | ")
                    .append(d.tier()).append(" | ").append(num(d.armorCapacity())).append(" | ")
                    .append(num(d.armorRating())).append(" | ").append(num(d.moveSpeedMult())).append(" | ")
                    .append(num(d.incomingAccuracyMult())).append(" | ")
                    .append(d.integralSystem() == null ? "" : d.integralSystem().id()).append(" |\n");
        }
        md.append("\nPower suits: **").append(suits).append("/12**. Squad armor capacity: **").append(num(capacity))
                .append("**. Mix: ").append(counts).append("\n");
        csv.add(new String[] {card.id(), "sheet", card.displayName(), card.accessTier().name(),
                unlockLine(card.accessTier()), String.join(" ", card.templateIds()), sources(card),
                num(capacity), String.valueOf(suits), "", counts.toString(), ""});
    }

    private static String header(LoadoutCard card) {
        return "Tier: **" + card.accessTier().name().toLowerCase(Locale.ROOT) + "** (" + unlockLine(card.accessTier())
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
        };
    }

    private static String special(SquadWeaponIssue issue) {
        if (issue.specialEquipmentId() == null) return "";
        SpecialEquipmentDef def = issue.specialDef();
        return def.id().replace("special.", "") + (def.usesAmmunition() ? " x" + def.startingAmmo() : "");
    }

    private static double paperDps(WeaponDef w, EquipmentGrade g) {
        double perCycle = w.damage * g.damageMult * Math.max(1, w.projectilesPerShot) * Math.max(1, w.burstCount);
        return perCycle / (w.cooldown * g.cooldownMult);
    }

    private static List<SquadWeaponIssue> issues(LoadoutCard card) {
        SquadWeaponDoctrine builtIn = SquadEquipmentDoctrines.weaponById(card.definitionId());
        if (builtIn != null) return builtIn.issues();
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            if (path.weapons.equals(card.definitionId())) return FirstRefit.weapons(path);
        }
        throw new IllegalStateException("No weapon definition for " + card.id());
    }

    private static SquadArmorPlan plan(LoadoutCard card) {
        return SquadEquipmentDoctrines.armorPlanById(card.definitionId());
    }

    private static String num(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
