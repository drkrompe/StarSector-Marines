package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.spec.SpecSheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Read-only comparison of actual equipment against a squad-wide draft. */
final class LoadoutInspection {
    private LoadoutInspection() { }

    static Map<String, Object> project(MarineRoster roster, MarineSquad squad,
                                       SquadEquipmentPreview preview, int selected,
                                       IntConsumer select) {
        List<String> members = squad == null ? List.of() : roster.manningMemberIds(squad);
        List<Map<String, Object>> teams = new ArrayList<>();
        MarineSoldier focused = selected < members.size() ? roster.soldierById(members.get(selected)) : null;
        FireTeamBillet current = current(focused), proposed = proposed(preview, selected);
        int changed = 0;
        for (int team = 0; team < 3; team++) {
            List<Map<String, Object>> billets = new ArrayList<>();
            for (int slot = 0; slot < 4; slot++) {
                int index = team * 4 + slot;
                MarineSoldier soldier = index < members.size() ? roster.soldierById(members.get(index)) : null;
                FireTeamBillet before = current(soldier), after = proposed(preview, index);
                boolean differs = !same(before, after);
                if (differs) changed++;
                String id = "loadout-billet:" + index;
                Map<String, Object> billet = row(id, "name", "art", "armorIcon", "weaponIcon", "status", "change");
                billet.put("name", (slot + 1) + " / " + (soldier == null ? Strings.get("openingLoadoutVacant") : soldier.name()));
                billet.put("status", soldier == null ? Strings.get("openingLoadoutVacantPlanOnly2") : soldier.status().toString());
                billet.put("change", after == null ? Strings.get("openingLoadoutChooseALoadout") : differs ? Strings.get("openingLoadoutEquipmentChanges") : Strings.get("openingLoadoutKeepCurrentKit"));
                billet.put("armor", before == null ? "graphics/ui/formation/marine-cutout-vacant.svg" : before.armorDef().iconPath());
                billet.put("weapon", before == null ? "" : EquipmentIcons.primary(before.primaryDef(), before.grade()));
                billet.put("classes", "loadout-billet" + (index == selected ? " selected" : ""));
                billet.put("select", (Runnable) () -> select.accept(index));
                billets.add(billet);
            }
            Map<String, Object> group = row("loadout-team:" + team, "heading", "members");
            group.put("label", new String[]{Strings.get("openingLoadoutAlpha"), Strings.get("openingLoadoutBravo"), Strings.get("openingLoadoutCharlie")}[team]);
            group.put("billets", billets); teams.add(group);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int kind = 0; kind < 3; kind++) items.add(item(kind, current, proposed, focused));
        return Map.of("teams", teams, "items", items,
                "title", focused == null ? Strings.get("openingLoadoutVacantBilletPlannedEquipment") : focused.name() + " / " + focused.status(),
                "summary", Strings.format("openingLoadoutChangedBillets", changed),
                "scope", Strings.get("openingLoadoutCurrentKitInTheGridSelectABillet"));
    }

    private static Map<String, Object> row(String id, String... children) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        for (String child : children) row.put(child + "Id", id + ":" + child);
        return row;
    }

    private static Map<String, Object> item(int kind, FireTeamBillet before, FireTeamBillet after, MarineSoldier soldier) {
        String id = "loadout-item:" + kind;
        SpecSheet current = sheet(before, kind), proposed = sheet(after, kind);
        Map<String, Object> item = row(id, "headingRow", "kind", "change", "pair", "stats", "note");
        item.put("kind", new String[]{Strings.get("openingLoadoutPrimaryWeapon"), Strings.get("openingLoadoutArmorSuitSystem"), Strings.get("openingLoadoutSpecialEquipment")}[kind]);
        item.put("change", before == null ? Strings.get("openingLoadoutPlanOnly") : Objects.equals(current, proposed) ? Strings.get("openingLoadoutUnchanged") : proposed == null ? Strings.get("openingLoadoutRemove") : Strings.get("openingLoadoutReplace"));
        item.put("classes", "loadout-item panel" + (kind == 2 ? " loadout-special" : kind == 1 ? " loadout-armor" : ""));
        item.put("current", subject(id + ":current", before, current, kind));
        item.put("proposed", subject(id + ":proposed", after, proposed, kind));
        List<SpecSheet.Stat> oldStats = stats(before, kind, soldier);
        List<SpecSheet.Stat> newStats = stats(after, kind, soldier);
        Map<String, SpecSheet.Stat> oldByLabel = new LinkedHashMap<>(), newByLabel = new LinkedHashMap<>();
        for (var stat : oldStats) oldByLabel.put(stat.label(), stat);
        for (var stat : newStats) newByLabel.put(stat.label(), stat);
        Map<String, SpecSheet.Stat> union = new LinkedHashMap<>(oldByLabel);
        union.putAll(newByLabel);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String label : union.keySet()) {
            SpecSheet.Stat old = oldByLabel.get(label), next = newByLabel.get(label);
            Map<String, Object> stat = row(id + ":stat:" + label, "icon", "label", "oldTrack", "oldFill", "oldValue", "arrow", "newTrack", "newFill", "newValue");
            stat.put("label", statLabel(label));
            stat.put("icon", "graphics/ui/equipment/" + statIcon(label) + ".svg");
            stat.put("before", old == null ? "—" : old.value());
            stat.put("after", next == null ? "—" : next.value());
            stat.put("oldStyle", fill(old)); stat.put("newStyle", fill(next));
            stat.put("change", old == null || next == null ? "" : old.value().equals(next.value()) ? "=" : "→");
            rows.add(stat);
        }
        item.put("stats", rows);
        item.put("style", "height: " + (kind == 2 ? 164 + 24 * rows.size() : kind == 1 ? 282 : 274) + "px;");
        item.put("statsStyle", "height: " + Math.max(0, 24 * rows.size() - 3) + "px;");
        item.put("notes", kind == 0 ? Strings.get("openingLoadoutPenetrationCountersResistanceAccuracyAndDpsUseThis")
                : kind == 1 ? Strings.get("openingLoadoutArmorIsCapacityResistanceReducesPenetrationMovementIs")
                : Strings.get("openingLoadoutSpecialEquipmentFollowsTheWeaponLoadout"));
        return item;
    }

    private static Map<String, Object> subject(String id, FireTeamBillet kit, SpecSheet sheet, int kind) {
        Map<String, Object> subject = row(id, "top", "icon", "text", "title", "subtitle", "system");
        subject.put("title", sheet == null ? kit == null ? Strings.get("openingLoadoutNoEquipment") : Strings.get("openingLoadoutNoneCarried") : sheet.title());
        subject.put("subtitle", sheet == null ? kit == null ? Strings.get("openingLoadoutVacantBillet") : Strings.get("openingLoadoutNoSpecialItem") : sheet.subtitle());
        subject.put("icon", kit == null ? "" : switch (kind) {
            case 0 -> EquipmentIcons.primary(kit.primaryDef(), kit.grade());
            case 1 -> kit.armorDef().iconPath();
            default -> kit.specialDef() == null ? "" : kit.specialDef().armoryIconPath();
        });
        subject.put("system", kind == 1 && kit != null ? IntegralSystemCopy.tile(kit.armorDef()) : "");
        subject.put("systemClasses", kind == 1 ? "loadout-system" : "loadout-hidden");
        subject.put("sheet", sheet);
        return subject;
    }

    private static List<SpecSheet.Stat> stats(FireTeamBillet kit, int kind, MarineSoldier soldier) {
        if (kit == null) return List.of();
        if (kind == 0) {
            var rows = new ArrayList<>(SpecSheets.weaponStats(kit.primaryDef(), kit.grade(), soldier == null ? null : soldier.profile()));
            rows.add(SpecSheets.weaponPenetration(kit.primaryDef()));
            return rows;
        }
        SpecSheet sheet = sheet(kit, kind);
        return sheet == null ? List.of() : sheet.stats();
    }

    private static String fill(SpecSheet.Stat stat) {
        return String.format(Locale.ROOT, "width: %.2f%%;", stat == null || !stat.hasMeter() ? 0f : stat.fill() * 100);
    }

    private static String statLabel(String label) {
        return switch (label) {
            case "DMG" -> Strings.get("openingLoadoutDamage"); case "RNG" -> Strings.get("openingLoadoutRange"); case "ACC" -> Strings.get("openingLoadoutAccuracy");
            case "DPS" -> Strings.get("openingLoadoutSustainedDps"); case "RESIST" -> Strings.get("openingLoadoutResistance"); case "PEN" -> Strings.get("openingLoadoutPenetration");
            case "MOVE" -> Strings.get("openingLoadoutMovement"); case "EVASION" -> Strings.get("openingLoadoutEvasion"); case "ARMOR" -> Strings.get("openingLoadoutArmor");
            default -> label;
        };
    }

    private static String statIcon(String label) {
        return switch (label) {
            case "DMG", "DPS" -> "firepower"; case "RNG", "ACC", "EVASION" -> "target";
            case "MOVE" -> "movement"; default -> "protection";
        };
    }

    private static SpecSheet sheet(FireTeamBillet kit, int kind) {
        if (kit == null) return null;
        return switch (kind) {
            case 0 -> SpecSheets.weapon(kit.primaryDef(), kit.grade());
            case 1 -> SpecSheets.armor(kit.armorDef());
            default -> kit.specialDef() == null ? null : SpecSheets.special(kit.specialDef());
        };
    }

    private static FireTeamBillet current(MarineSoldier soldier) {
        return soldier == null ? null : new FireTeamBillet(soldier.name(), soldier.primaryDef().id,
                soldier.primaryGrade(), soldier.specialEquipmentDef() == null ? null : soldier.specialEquipmentDef().id(), soldier.armorDef().id());
    }

    private static FireTeamBillet proposed(SquadEquipmentPreview preview, int index) {
        if (preview == null || index >= preview.billets().size()) return null;
        SquadEquipmentBillet kit = preview.billet(index);
        return new FireTeamBillet(kit.role(), kit.primaryId(), kit.grade(), kit.specialEquipmentId(), kit.armorId());
    }

    private static boolean same(FireTeamBillet a, FireTeamBillet b) {
        return a == null ? b == null : b != null && a.primaryId().equals(b.primaryId()) && a.grade() == b.grade()
                && a.armorId().equals(b.armorId()) && Objects.equals(a.specialEquipmentId(), b.specialEquipmentId());
    }
}
