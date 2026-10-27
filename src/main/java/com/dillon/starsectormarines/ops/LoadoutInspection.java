package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;

import com.dillon.starsectormarines.marine.BilletRoles;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadEquipmentBillet;
import com.dillon.starsectormarines.marine.SquadEquipmentPreview;
import com.dillon.starsectormarines.ops.battleview.ArmoryLoadoutPreviewComposer;
import com.dillon.starsectormarines.ops.battleview.ArmoryMarinePreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.KitBrick;
import com.dillon.starsectormarines.ops.battleview.KitBrickCanvas;
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
 * marine with a portrait of the kit they would wear, their primary, secondary, armour
 * and integral system as issued now with what the selected cards would change it
 * to, and the few numbers that change with it. The primary and secondary stand on
 * kit bricks ({@link KitBrick}), the Mech Lab's and Boat Deck's mounts, coloured by
 * what they throw. Read-only; nothing here issues.
 */
final class LoadoutInspection {
    private LoadoutInspection() { }

    /** The lines of kit each marine's tile lists, in order; the special item is the marine's secondary. */
    static final int WEAPON = 0, SPECIAL = 1, ARMOR = 2, SYSTEM = 3, PARTS = 4;
    /** Each line's kind as its class names it, taking the spec sheet's accent for that kind. */
    private static final String[] PART_KINDS = {"weapon", "special", "armor", "system"};

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
        MarineSoldier leader = roster.squadLeader(squad);
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
                    billets.add(billet(index, soldier, soldier != null && soldier == leader,
                            before, after, differs, index == selected, select));
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
                "title", focused == null ? Strings.get("openingLoadoutVacantBilletPlannedEquipment")
                        : Strings.join("separatorPath", title(focused, focused == leader, current(focused)),
                        String.valueOf(focused.status())),
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

    /** The brick one weapon line of one marine stands its item on, or null for an empty mount. */
    @SuppressWarnings("unchecked")
    static KitBrick partBrick(Map<String, Object> inspection, int index, int part) {
        Map<String, Object> billet = billetAt(inspection, index);
        if (billet == null) return null;
        return (KitBrick) ((List<Map<String, Object>>) billet.get("parts")).get(part).get("brick");
    }

    /**
     * Gives each marine's portrait and kit bricks their producers, reading the live
     * projection so a change of card or billet repaints without rebinding. Idempotent
     * per document.
     */
    static void bindCanvases(UiDocument document, MarkupInstance markup,
                             Supplier<Map<String, Object>> inspection,
                             ArmoryLoadoutPreviewComposer.Assets assets) {
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            int index = billet;
            UiElement canvas = markup.requireElement(portraitId(billet));
            if (document.canvases().producerOf(canvas) == null) {
                document.canvases().set(canvas, new ArmoryMarinePreviewCanvas(
                        () -> shownKit(inspection.get(), index), assets));
            }
            for (int part : new int[]{WEAPON, SPECIAL}) {
                UiElement brick = markup.requireElement(brickId(billet, part));
                if (document.canvases().producerOf(brick) != null) continue;
                document.canvases().set(brick, new KitBrickCanvas(() -> partBrick(inspection.get(), index, part)));
            }
        }
    }

    static String partId(int index, int part) {
        return "loadout-billet:" + index + ":part:" + part;
    }

    static String brickId(int index, int part) {
        return partId(index, part) + ":brick";
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

    private static Map<String, Object> billet(int index, MarineSoldier soldier, boolean leadsSquad,
                                              FireTeamBillet before, FireTeamBillet after, boolean differs,
                                              boolean selected, IntConsumer select) {
        String id = "loadout-billet:" + index;
        Map<String, Object> billet = row(id, "name", "head", "parts", "stats");
        billet.put("index", index);
        billet.put("portraitId", portraitId(index));
        billet.put("kit", after != null ? after : before);
        // A marine is read by place in the squad, not by name: rank and role, and their
        // status only when they are not fit for duty. Whether the kit changes shows on its lines.
        boolean fit = soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE;
        String title = soldier == null ? Strings.get("openingLoadoutVacant")
                : title(soldier, leadsSquad, after != null ? after : before);
        billet.put("name", fit || soldier == null ? title
                : Strings.join("separatorDot", title, String.valueOf(soldier.status())));
        billet.put("nameClasses", "loadout-member-name label " + (soldier == null ? "tone-muted" : fit ? "tone-edge" : "tone-danger"));
        billet.put("classes", "loadout-billet" + (selected ? " selected" : "") + (differs ? " changes" : ""));
        billet.put("select", (Runnable) () -> select.accept(index));
        List<Map<String, Object>> parts = new ArrayList<>();
        for (int part = 0; part < PARTS; part++) parts.add(part(index, part, before, after));
        billet.put("parts", parts);
        billet.put("stats", stats(id, before, after, soldier));
        return billet;
    }

    /** "Cpl. Squad Leader", "Mar. Rifleman": the marine's rank, then what their billet does. */
    private static String title(MarineSoldier soldier, boolean leadsSquad, FireTeamBillet kit) {
        String role = leadsSquad ? Strings.get("openingLoadoutSquadLeader")
                : kit != null ? BilletRoles.label(kit.name()) : null;
        String rank = soldier.enlistedRank().abbreviation();
        return role == null ? soldier.enlistedRank().displayName() : rank + " " + role;
    }

    private static Map<String, Object> part(int index, int part, FireTeamBillet before, FireTeamBillet after) {
        Map<String, Object> line = row(partId(index, part), "tick", "brick", "socket", "icon", "text", "now", "nextRow", "arrow", "next");
        SpecSheet now = sheet(before, part), next = sheet(after, part);
        boolean changes = after != null && !Objects.equals(now, next);
        FireTeamBillet shown = after != null ? after : before;
        String icon = icon(shown, part);
        // A weapon stands on its brick, coloured by what it throws, as Mech Lab hardware does;
        // the suit's lines keep the slot's colour on a tick and the item in a sunk socket.
        // An empty slot fades into the card and a changing one is framed in brass.
        boolean mount = part == WEAPON || part == SPECIAL;
        line.put("classes", "loadout-part loadout-part-" + PART_KINDS[part] + (mount ? " loadout-part-mount" : "")
                + (changes ? " changes" : now == null && next == null ? " empty" : ""));
        line.put("brick", mount ? brick(shown, part) : null);
        line.put("brickClasses", mount ? "loadout-part-brick" : "loadout-hidden");
        line.put("tickClasses", mount ? "loadout-hidden" : "loadout-part-tick");
        line.put("socketClasses", mount ? "loadout-hidden" : "loadout-part-socket");
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
        return part == SYSTEM ? Strings.get("gearSystemNone") : Strings.get("openingLoadoutNoSecondary");
    }

    private static KitBrick brick(FireTeamBillet kit, int part) {
        if (kit == null) return null;
        return part == WEAPON
                ? KitBrick.weapon(kit.primaryDef(), EquipmentIcons.primary(kit.primaryDef(), kit.grade()), EquipmentIcons.PRIMARY_LIE)
                : KitBrick.secondary(kit.specialDef());
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
        if (kind == WEAPON) {
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
