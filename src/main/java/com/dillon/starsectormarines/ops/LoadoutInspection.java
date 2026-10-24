package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;

import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.ops.battleview.ArmoryLoadoutPreviewComposer;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.spec.SpecSheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The squad as the Loadout screen shows it: three fire teams of two by two, each
 * marine with a portrait of the kit they would wear, their weapon, armour, special
 * and integral system as issued now with what the selected cards would change it
 * to, and the few numbers that change with it. Read-only; nothing here issues.
 */
final class LoadoutInspection {
    private LoadoutInspection() { }

    /** The lines of kit each marine's tile lists, in order. */
    static final int WEAPON = 0, ARMOR = 1, SPECIAL = 2, SYSTEM = 3, PARTS = 4;
    /** Each line's kind as its class names it, taking the spec sheet's accent for that kind. */
    private static final String[] PART_KINDS = {"weapon", "armor", "special", "system"};

    /** The numbers a tile compares: the weapon's three that matter most, and the suit's two. */
    private static final List<String> STATS = List.of("DPS", "RNG", "PEN", "ARMOR", "MOVE");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String BETTER = "graphics/ui/equipment/stat-better.svg";
    private static final String WORSE = "graphics/ui/equipment/stat-worse.svg";
    private static final String CHANGE = "graphics/ui/equipment/kit-change.svg";

    static Map<String, Object> project(MarineRoster roster, MarineSquad squad,
                                       SquadEquipmentPreview preview, int selected,
                                       IntConsumer select) {
        List<String> members = squad == null ? List.of() : roster.manningMemberIds(squad);
        List<Map<String, Object>> teams = new ArrayList<>();
        MarineSoldier focused = selected < members.size() ? roster.soldierById(members.get(selected)) : null;
        String[] labels = {Strings.get("openingLoadoutAlpha"), Strings.get("openingLoadoutBravo"), Strings.get("openingLoadoutCharlie")};
        int changed = 0;
        for (int team = 0; team < 3; team++) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int row = 0; row < 2; row++) {
                List<Map<String, Object>> billets = new ArrayList<>();
                for (int column = 0; column < 2; column++) {
                    int slot = row * 2 + column, index = team * 4 + slot;
                    MarineSoldier soldier = index < members.size() ? roster.soldierById(members.get(index)) : null;
                    FireTeamBillet before = current(soldier), after = proposed(preview, index);
                    boolean differs = after != null && !same(before, after);
                    if (differs) changed++;
                    billets.add(billet(index, slot, soldier, before, after, differs, index == selected, select));
                }
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("id", "loadout-team:" + team + ":row:" + row);
                line.put("billets", billets);
                rows.add(line);
            }
            Map<String, Object> group = row("loadout-team:" + team, "heading", "rows");
            group.put("label", labels[team]);
            group.put("rows", rows);
            teams.add(group);
        }
        return Map.of("teams", teams,
                "title", focused == null ? Strings.get("openingLoadoutVacantBilletPlannedEquipment") : Strings.join("separatorPath", focused.name(), String.valueOf(focused.status())),
                "scope", Strings.format("openingLoadoutSquadScope", changed));
    }

    /** The kit a marine's portrait shows: what the selected cards would issue, or what they wear. */
    static FireTeamBillet shownKit(Map<String, Object> inspection, int index) {
        Map<String, Object> billet = billetAt(inspection, index);
        return billet == null ? null : (FireTeamBillet) billet.get("kit");
    }

    /** The sheet one kit line of one marine opens on hover. */
    @SuppressWarnings("unchecked")
    static SpecSheet partSheet(Map<String, Object> inspection, int index, int part) {
        Map<String, Object> billet = billetAt(inspection, index);
        if (billet == null) return null;
        return (SpecSheet) ((List<Map<String, Object>>) billet.get("parts")).get(part).get("sheet");
    }

    /**
     * Gives each marine's portrait its producer, reading the live projection so a
     * change of card or billet repaints without rebinding. Idempotent per document.
     */
    static void bindPortraits(UiDocument document, MarkupInstance markup,
                              Supplier<Map<String, Object>> inspection,
                              ArmoryLoadoutPreviewComposer.Assets assets) {
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            UiElement canvas = markup.requireElement(portraitId(billet));
            if (document.canvases().producerOf(canvas) != null) continue;
            int index = billet;
            document.canvases().set(canvas, new ArmoryMarinePreviewCanvas(
                    () -> shownKit(inspection.get(), index), assets));
        }
    }

    static String partId(int index, int part) {
        return "loadout-billet:" + index + ":part:" + part;
    }

    static String portraitId(int index) {
        return "loadout-billet:" + index + ":portrait";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> billetAt(Map<String, Object> inspection, int index) {
        for (Object team : (List<?>) inspection.get("teams")) {
            for (Object line : (List<?>) ((Map<String, Object>) team).get("rows")) {
                for (Object billet : (List<?>) ((Map<String, Object>) line).get("billets")) {
                    Map<String, Object> shown = (Map<String, Object>) billet;
                    if ((Integer) shown.get("index") == index) return shown;
                }
            }
        }
        return null;
    }

    private static Map<String, Object> billet(int index, int slot, MarineSoldier soldier, FireTeamBillet before,
                                              FireTeamBillet after, boolean differs, boolean selected,
                                              IntConsumer select) {
        String id = "loadout-billet:" + index;
        Map<String, Object> billet = row(id, "name", "state", "parts", "stats");
        billet.put("index", index);
        billet.put("portraitId", portraitId(index));
        billet.put("kit", after != null ? after : before);
        billet.put("name", Strings.join("separatorPath", String.valueOf(slot + 1), soldier == null ? Strings.get("openingLoadoutVacant") : soldier.name()));
        billet.put("state", Strings.join("separatorDot",
                soldier == null ? Strings.get("openingLoadoutVacantPlanOnly2") : soldier.status().toString(),
                after == null ? Strings.get("openingLoadoutChooseALoadout") : differs ? Strings.get("openingLoadoutEquipmentChanges") : Strings.get("openingLoadoutKeepCurrentKit")));
        billet.put("stateClasses", "loadout-member-state label " + (differs ? "tone-accent" : "tone-muted"));
        billet.put("classes", "loadout-billet" + (selected ? " selected" : ""));
        billet.put("select", (Runnable) () -> select.accept(index));
        List<Map<String, Object>> parts = new ArrayList<>();
        for (int part = 0; part < PARTS; part++) parts.add(part(index, part, before, after));
        billet.put("parts", parts);
        billet.put("stats", stats(id, before, after, soldier));
        return billet;
    }

    private static Map<String, Object> part(int index, int part, FireTeamBillet before, FireTeamBillet after) {
        Map<String, Object> line = row(partId(index, part), "tick", "socket", "icon", "text", "now", "nextRow", "arrow", "next");
        SpecSheet now = sheet(before, part), next = sheet(after, part);
        boolean changes = after != null && !Objects.equals(now, next);
        String icon = icon(after != null ? after : before, part);
        // Drawn as a Mech Lab mount: the slot's colour on a tick, the item in a sunk socket,
        // an empty slot faded into the card and a changing one framed in brass.
        line.put("classes", "loadout-part loadout-part-" + PART_KINDS[part]
                + (changes ? " changes" : now == null && next == null ? " empty" : ""));
        line.put("icon", icon);
        line.put("iconClasses", icon.isEmpty() ? "loadout-part-icon loadout-part-icon-absent" : "loadout-part-icon");
        line.put("now", name(before, now, part));
        // A change reads as the old item dimmed with the new one under an arrow.
        line.put("nowClasses", "loadout-part-now label " + (now == null || changes ? "tone-muted" : "tone-edge"));
        line.put("arrow", CHANGE);
        line.put("next", changes ? name(after, next, part) : "");
        line.put("nextRowClasses", changes ? "loadout-part-next" : "loadout-hidden");
        line.put("sheet", changes && next != null ? next : now != null ? now : next);
        return line;
    }

    private static List<Map<String, Object>> stats(String id, FireTeamBillet before, FireTeamBillet after,
                                                   MarineSoldier soldier) {
        Map<String, SpecSheet.Stat> now = statsById(before, soldier), next = statsById(after, soldier);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String stat : STATS) {
            SpecSheet.Stat old = now.get(stat), fresh = next.get(stat);
            boolean changes = after != null && fresh != null && (old == null || !old.value().equals(fresh.value()));
            Map<String, Object> line = row(id + ":stat:" + stat, "label", "before", "arrow", "after");
            line.put("label", statLabel(stat));
            line.put("before", old == null ? "—" : old.value());
            line.put("after", changes ? fresh.value() : "");
            // Every number compared here reads better the higher it is.
            int direction = changes ? Float.compare(number(fresh.value()), old == null ? 0f : number(old.value())) : 0;
            line.put("arrow", direction > 0 ? BETTER : direction < 0 ? WORSE : "");
            line.put("arrowClasses", direction == 0 ? "loadout-stat-arrow loadout-stat-arrow-absent" : "loadout-stat-arrow");
            line.put("afterClasses", "loadout-stat-after label " + (direction > 0 ? "tone-good" : direction < 0 ? "tone-danger" : "tone-edge"));
            rows.add(line);
        }
        return rows;
    }

    /** The leading number of a stat's printed value: {@code "+12%"} reads 12, {@code "26.4"} 26.4. */
    static float number(String value) {
        Matcher match = NUMBER.matcher(value);
        return match.find() ? Float.parseFloat(match.group()) : 0f;
    }

    private static Map<String, SpecSheet.Stat> statsById(FireTeamBillet kit, MarineSoldier soldier) {
        Map<String, SpecSheet.Stat> byId = new LinkedHashMap<>();
        for (var stat : stats(kit, WEAPON, soldier)) byId.put(stat.id(), stat);
        for (var stat : stats(kit, ARMOR, soldier)) byId.put(stat.id(), stat);
        return byId;
    }

    private static String statLabel(String stat) {
        return switch (stat) {
            case "DPS" -> Strings.get("openingLoadoutStatDPS");
            case "RNG" -> Strings.get("openingLoadoutStatRNG");
            case "PEN" -> Strings.get("openingLoadoutStatPEN");
            case "ARMOR" -> Strings.get("openingLoadoutStatARMOR");
            default -> Strings.get("openingLoadoutStatMOVE");
        };
    }

    private static String name(FireTeamBillet kit, SpecSheet sheet, int part) {
        if (sheet != null) return sheet.title();
        if (kit == null) return Strings.get("openingLoadoutVacant");
        return part == SYSTEM ? Strings.get("gearSystemNone") : Strings.get("openingLoadoutNoSpecialItem");
    }

    private static String icon(FireTeamBillet kit, int part) {
        if (kit == null) return "";
        return switch (part) {
            case WEAPON -> EquipmentIcons.primary(kit.primaryDef(), kit.grade());
            case ARMOR -> kit.armorDef().iconPath();
            case SPECIAL -> kit.specialDef() == null ? "" : kit.specialDef().armoryIconPath();
            default -> IntegralSystemCopy.carried(kit.armorDef()) ? IntegralSystemCopy.iconPath(kit.armorDef()) : "";
        };
    }

    private static Map<String, Object> row(String id, String... children) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        for (String child : children) row.put(child + "Id", id + ":" + child);
        return row;
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

    private static SpecSheet sheet(FireTeamBillet kit, int kind) {
        if (kit == null) return null;
        return switch (kind) {
            case WEAPON -> SpecSheets.weapon(kit.primaryDef(), kit.grade());
            case ARMOR -> SpecSheets.armor(kit.armorDef());
            case SPECIAL -> kit.specialDef() == null ? null : SpecSheets.special(kit.specialDef());
            default -> IntegralSystemCopy.carried(kit.armorDef()) ? SpecSheets.integralSystem(kit.armorDef()) : null;
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
