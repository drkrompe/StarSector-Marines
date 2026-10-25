package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.OfficerCareer;
import com.dillon.starsectormarines.marine.OfficerSpecialty;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.FleetArmoryViewModel.DoctrineTile;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.DebugCompanyStage;
import com.dillon.starsectormarines.ops.battleview.LayeredMechAssets;
import com.dillon.starsectormarines.ops.battleview.MechChassisPreviewCanvas;
import com.dillon.starsectormarines.ops.battleview.VehiclePreviewCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The debug briefing's kit picker. The drawer shows each squad's two cards as chips and each
 * bay slot as a picture of what stands in it; pressing one opens a modal laying out every
 * choice the way the game draws it — the Armory's own loadout cards, or the Mech Lab's
 * chassis and hull previews — and picking one issues it and closes the modal.
 *
 * <p>State is only which choice is open; the cards live on the debug company's squads and
 * the bay on the briefing, so a rebuild reads them afresh.
 */
final class DebugKitPicker {

    enum Kind { WEAPONS, ARMOR, BAY, COMMANDER }

    /** The squad target that issues to every line squad at once. */
    static final int ALL_SQUADS = -1;

    private Kind kind;
    private int target;

    void open(Kind kind, int target) {
        this.kind = kind;
        this.target = target;
    }

    void close() { kind = null; }

    Kind kind() { return kind; }

    int target() { return target; }

    /** The squads a squad target names: one squad, or every line squad. */
    static List<MarineSquad> targets(MarineRoster roster, int target) {
        List<MarineSquad> squads = DebugSquadCards.lineSquads(roster);
        if (target == ALL_SQUADS) return squads;
        return target >= 0 && target < squads.size() ? List.of(squads.get(target)) : List.of();
    }

    // ---- drawer --------------------------------------------------------------------------

    /** The drawer's squad rows: every squad together first, then the first {@code shown} squads. */
    static List<SquadRow> squadRows(MarineRoster roster, int shown, Consumer<Kind> openAll,
                                    SquadOpener open) {
        List<SquadRow> rows = new ArrayList<>();
        List<MarineSquad> squads = DebugSquadCards.lineSquads(roster);
        if (squads.isEmpty()) return rows;
        rows.add(squadRow("debug-kit-all", "ALL", roster, squads,
                () -> openAll.accept(Kind.WEAPONS), () -> openAll.accept(Kind.ARMOR)));
        for (int i = 0; i < Math.min(shown, squads.size()); i++) {
            int index = i;
            MarineSquad squad = squads.get(i);
            rows.add(squadRow("debug-kit-squad-" + (i + 1), "SQUAD " + (i + 1), roster, List.of(squad),
                    () -> open.open(Kind.WEAPONS, index), () -> open.open(Kind.ARMOR, index)));
        }
        return rows;
    }

    interface SquadOpener { void open(Kind kind, int target); }

    private static SquadRow squadRow(String id, String label, MarineRoster roster, List<MarineSquad> squads,
                                     Runnable weapons, Runnable armor) {
        String weaponId = shared(squads, true);
        String armorId = shared(squads, false);
        SquadWeaponDoctrine doctrine = weaponId == null ? null : roster.armory().weaponDoctrineById(weaponId);
        SquadArmorPlan plan = armorId == null ? null : SquadEquipmentDoctrines.armorPlanById(armorId);
        Map<String, Object> weaponChip = doctrine == null ? CardChips.empty(id + ":weapon-chip", true)
                : CardChips.weapons(id + ":weapon-chip", doctrine, true);
        Map<String, Object> armorChip = plan == null ? CardChips.empty(id + ":armor-chip", true)
                : CardChips.sheet(id + ":armor-chip", plan, roster.armory().issue(plan), true);
        return new SquadRow(id, label, weaponChip, doctrine == null ? "MIXED WEAPONS" : "",
                armorChip, plan == null ? "MIXED ARMOR" : "", weapons, armor);
    }

    /** The card every squad holds, or null when they differ. */
    private static String shared(List<MarineSquad> squads, boolean weapons) {
        String card = null;
        for (MarineSquad squad : squads) {
            String held = weapons ? squad.weaponDoctrineId() : squad.armorDoctrineId();
            if (card != null && !card.equals(held)) return null;
            card = held;
        }
        return card;
    }

    /** The drawer's four bay slots, each a button showing what stands in it. */
    static List<BayTile> baySlots(DebugBaySlot[] bay, Consumer<Integer> open) {
        List<BayTile> tiles = new ArrayList<>();
        for (int i = 0; i < bay.length; i++) {
            int slot = i;
            tiles.add(new BayTile("debug-kit-bay-" + (i + 1), bay[i], "BAY " + (i + 1) + " · " + name(bay[i]),
                    "", "debug-kit-slot", () -> open.accept(slot)));
        }
        return tiles;
    }

    // ---- modal ---------------------------------------------------------------------------

    /** The modal's props for the open choice, or a closed modal when none is. */
    Map<String, Object> modal(MarineRoster roster, DebugBaySlot[] bay, Consumer<String> issueCard,
                              Consumer<DebugBaySlot> fillSlot, Runnable close) {
        return modal(kind, target, roster, bay, issueCard, fillSlot, close);
    }

    static Map<String, Object> modal(Kind kind, int target, MarineRoster roster, DebugBaySlot[] bay,
                                     Consumer<String> issueCard, Consumer<DebugBaySlot> fillSlot,
                                     Runnable close) {
        Map<String, Object> modal = new LinkedHashMap<>();
        modal.put("open", kind != null);
        modal.put("kind", kind);
        modal.put("close", close);
        modal.put("weaponTiles", List.of());
        modal.put("armorTiles", List.of());
        modal.put("bayTiles", List.of());
        modal.put("specialtyTiles", List.of());
        modal.put("commanderPortrait", DebugCommander.PORTRAIT);
        modal.put("commanderStatus", "");
        modal.put("pointsLabel", "");
        modal.put("toggleLabel", "");
        modal.put("toggleCommander", close);
        modal.put("title", "");
        modal.put("subtitle", "");
        if (kind == null) return modal;
        List<MarineSquad> squads = targets(roster, target);
        String who = target == ALL_SQUADS ? "ALL SQUADS" : "SQUAD " + (target + 1);
        switch (kind) {
            case WEAPONS -> {
                String held = shared(squads, true);
                List<DoctrineTile> tiles = new ArrayList<>();
                for (SquadWeaponDoctrine doctrine : DebugSquadCards.weaponOptions(roster)) {
                    tiles.add(FleetArmoryViewModel.weaponTile("debug-kit-weapon:" + doctrine.id(), doctrine,
                            doctrine.id().equals(held), () -> issueCard.accept(doctrine.id())));
                }
                modal.put("weaponTiles", FleetArmoryViewModel.rankedByRating(tiles));
                modal.put("title", who + " · WEAPON LOADOUT");
                modal.put("subtitle", tiles.size() + " cards, every one unlocked for debug · best first · "
                        + "click a card to issue it");
            }
            case ARMOR -> {
                String held = shared(squads, false);
                List<DoctrineTile> tiles = new ArrayList<>();
                for (SquadArmorPlan plan : DebugSquadCards.armorOptions(roster)) {
                    // The armor accent is read off this prefix.
                    tiles.add(FleetArmoryViewModel.armorTile("armor-doctrine:debug-kit:" + plan.id(), plan,
                            roster.armory().issue(plan), FleetArmoryViewModel.roleComposition(plan, (r, c) -> true),
                            plan.id().equals(held), () -> issueCard.accept(plan.id())));
                }
                modal.put("armorTiles", FleetArmoryViewModel.rankedByRating(tiles));
                modal.put("title", who + " · TACTIC SHEET");
                modal.put("subtitle", tiles.size() + " sheets, every one unlocked for debug · best first · "
                        + "click a card to issue it");
            }
            case COMMANDER -> {
                modal.put("title", "OPERATION COMMANDER  ·  THIS BATTLE ONLY");
                modal.put("subtitle", "A " + DebugCommander.RANK.displayName() + " with every training point the game grants;"
                        + " untrained, it leads exactly as a junior officer would · nothing reaches the campaign");
            }
            case BAY -> {
                List<BayTile> tiles = new ArrayList<>();
                List<DebugBaySlot> options = DebugBaySlot.options();
                for (int i = 0; i < options.size(); i++) {
                    DebugBaySlot option = options.get(i);
                    boolean selected = target >= 0 && target < bay.length && option.sameAs(bay[target]);
                    tiles.add(new BayTile("debug-kit-option-" + i, option, name(option), detail(option),
                            "debug-kit-option" + (selected ? " selected" : ""), () -> fillSlot.accept(option)));
                }
                modal.put("bayTiles", tiles);
                modal.put("title", "BAY SLOT " + (target + 1) + " · MECH OR VEHICLE");
                modal.put("subtitle", "Every chassis and hull the briefing can field · click one to put it in the slot");
            }
        }
        return modal;
    }

    /** The expanded debug briefing with an established four-squad company, and {@code open} picking (null for none). */
    static Map<String, Object> previewProps(Kind open) {
        Map<String, Object> props = BriefingViewModel.previewProps(true, true);
        MarineRoster roster = DebugCompany.seeded(DebugCompanyStage.ESTABLISHED, 4, 7L);
        DebugCompany.unlockEveryCard(roster);
        DebugBaySlot[] bay = { DebugBaySlot.of(MechVariant.HOUND), DebugBaySlot.of(MechVariant.BULWARK),
                DebugBaySlot.of(VehicleType.HEAVY_APC), DebugBaySlot.EMPTY };
        props.put("debugKitRows", squadRows(roster, 4, kind -> { }, (kind, target) -> { }));
        props.put("debugBaySlots", baySlots(bay, slot -> { }));
        Map<String, Object> modal = modal(open, open == Kind.BAY ? 1 : 0, roster, bay, card -> { }, slot -> { }, () -> { });
        DebugCommander commander = new DebugCommander();
        commander.setFielded(true);
        commander.press(OfficerSpecialty.TACTICAL_COVER, 2);
        commander.press(OfficerSpecialty.FIELD_MEDICINE, 2);
        putCommander(modal, commander, () -> { }, (specialty, rank) -> { });
        props.put("kitPicker", modal);
        props.put("debugCommander", commanderRow(commander, () -> { }));
        return props;
    }

    /** The drawer's commander row: who leads, and the button that opens their training. */
    static Map<String, Object> commanderRow(DebugCommander commander, Runnable open) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("summary", commander.summary());
        row.put("portrait", DebugCommander.PORTRAIT);
        row.put("classes", "debug-kit-commander" + (commander.fielded() ? " fielded" : ""));
        row.put("open", open);
        return row;
    }

    /** Fills an open commander modal: the field toggle and one column per specialty, as Barracks draws them. */
    static void putCommander(Map<String, Object> modal, DebugCommander commander, Runnable toggle,
                             BiConsumer<OfficerSpecialty, Integer> press) {
        modal.put("commanderPortrait", DebugCommander.PORTRAIT);
        modal.put("commanderStatus", commander.summary());
        modal.put("toggleLabel", commander.fielded() ? "STAND DOWN" : "FIELD COMMANDER");
        modal.put("toggleCommander", toggle);
        int left = DebugCommander.points() - commander.spent();
        modal.put("pointsLabel", left + (left == 1 ? " POINT LEFT" : " POINTS LEFT"));
        if (!Kind.COMMANDER.equals(modal.get("kind"))) return;
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (OfficerSpecialty specialty : OfficerSpecialty.values()) {
            String id = "debug-kit-specialty-" + specialty.name();
            int level = commander.level(specialty);
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("id", id);
            tile.put("classes", "kit-specialty" + (level > 0 && commander.fielded() ? " trained" : ""));
            tile.put("iconId", id + ":icon");
            tile.put("icon", "graphics/ui/officers/" + specialty.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".svg");
            tile.put("titleId", id + ":title");
            tile.put("title", specialty.label());
            for (int rank = 1; rank <= OfficerCareer.MAX_LEVEL; rank++) {
                int r = rank;
                String state = level >= rank ? "trained" : commander.allows(specialty, rank) ? "available" : "locked";
                String node = id + ":rank-" + rank;
                tile.put("rank" + rank + "Id", node);
                tile.put("rank" + rank + "ImageId", node + ":image");
                tile.put("rank" + rank + "TextId", node + ":text");
                tile.put("rank" + rank + "Icon", "graphics/ui/officers/specialty-node-" + rank + "-" + state + ".svg");
                tile.put("rank" + rank + "Effect", (rank == 1 ? "I  " : "II  ") + specialty.effect(rank));
                tile.put("rank" + rank + "Disabled", state.equals("locked"));
                tile.put("rank" + rank, (Runnable) () -> press.accept(specialty, r));
            }
            tiles.add(tile);
        }
        modal.put("specialtyTiles", tiles);
    }

    static String name(DebugBaySlot slot) {
        if (slot.mech != null) return slot.mech.displayName();
        if (slot.vehicle != null) return CampaignVehicle.typeName(slot.vehicle);
        return "Empty";
    }

    private static String detail(DebugBaySlot slot) {
        if (slot.mech != null) {
            MechVariant mech = slot.mech;
            return "MECH · " + mech.defaultRole.displayName() + " · " + Math.round(mech.maxStructure)
                    + " struct · " + Math.round(mech.armorCapacity) + " armor";
        }
        if (slot.vehicle != null) {
            VehicleType vehicle = slot.vehicle;
            return "VEHICLE · " + (vehicle.capacity > 0 ? vehicle.capacity + " seats · " : "")
                    + Math.round(vehicle.maxStructure) + " struct · " + Math.round(vehicle.maxArmor) + " armor";
        }
        return "Leave the slot unfilled";
    }

    /** Gives every bay picture in the document its chassis or hull drawing. */
    static void wireBayPictures(UiDocument document, MarkupInstance markup, List<?> tiles,
                                Supplier<LayeredMechAssets> mechs) {
        for (Object value : tiles) {
            BayTile tile = (BayTile) value;
            var canvas = markup.requireElement(tile.pictureId());
            if (tile.slot.mech != null) {
                MechVariant variant = tile.slot.mech;
                document.canvases().set(canvas, new MechChassisPreviewCanvas(new CampaignMech(
                        "debug-bay:" + variant.id, variant.displayName(), variant, variant.defaultRole,
                        MissileReplenisherComponent.STANDARD.id()), mechs));
            } else if (tile.slot.vehicle != null) {
                document.canvases().set(canvas, new VehiclePreviewCanvas(tile.slot.vehicle));
            }
        }
    }

    record SquadRow(String id, String label, Map<String, Object> weaponChip, String weaponNote,
                    Map<String, Object> armorChip, String armorNote, Runnable openWeapons,
                    Runnable openArmor) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> id + ":label"; case "label" -> label;
                case "cardsId" -> id + ":cards";
                case "weaponId" -> id + ":weapons"; case "armorId" -> id + ":armor";
                case "weaponChip" -> weaponChip; case "armorChip" -> armorChip;
                case "weaponNoteId" -> id + ":weapon-note"; case "armorNoteId" -> id + ":armor-note";
                case "weaponNote" -> weaponNote; case "armorNote" -> armorNote;
                case "weaponNoteClasses" -> weaponNote.isEmpty() ? "debug-kit-note-absent" : "label debug-kit-note";
                case "armorNoteClasses" -> armorNote.isEmpty() ? "debug-kit-note-absent" : "label debug-kit-note";
                case "openWeapons" -> openWeapons; case "openArmor" -> openArmor;
                default -> null;
            };
        }
    }

    record BayTile(String id, DebugBaySlot slot, String name, String detail, String classes,
                   Runnable select) implements MarkupPropertySource {
        String pictureId() { return id + ":picture"; }

        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "select" -> select;
                case "pictureId" -> pictureId(); case "nameId" -> id + ":name"; case "name" -> name;
                case "detailId" -> id + ":detail"; case "detail" -> detail;
                default -> null;
            };
        }
    }
}
