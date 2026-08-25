package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Spatial doll projection and command surface for the campaign-authoritative {@link MechBay}. */
public final class MechLabViewModel {

    public enum SlotId {
        CORE("ENGINE CORE"), ARMS("ARM ASSEMBLY"), LEFT_SHOULDER("L. SHOULDER"),
        RIGHT_SHOULDER("R. SHOULDER"), AMMO_RESERVE("AMMO RESERVE"),
        MINI_FAB("MINI-FAB");

        private final String label;
        SlotId(String label) { this.label = label; }
        String label() { return label; }
    }

    private final MechBay bay;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<String> selectedMechId;
    private final MutableSignal<SlotId> selectedSlot;
    private final MutableSignal<Boolean> assetPickerOpen;
    private final MutableSignal<String> feedbackText;
    private final MutableSignal<String> feedbackClasses;
    private final ComputedSignal<String> labSummary;
    private final ComputedSignal<List<SquadRow>> squadRows;
    private final ComputedSignal<List<MechRow>> mechRows;
    private final ComputedSignal<String> selectedMechName;
    private final ComputedSignal<String> selectedMechIdentity;
    private final ComputedSignal<String> selectedMechDoctrine;
    private final ComputedSignal<List<PerformanceMeter>> performanceMeters;
    private final ComputedSignal<List<SlotRow>> leftSlotRows;
    private final ComputedSignal<List<SlotRow>> rightSlotRows;
    private final ComputedSignal<List<SlotRow>> slotRows;
    private final ComputedSignal<String> selectedSlotTitle;
    private final ComputedSignal<String> selectedSlotCopy;
    private final ComputedSignal<String> selectedSlotRule;
    private final ComputedSignal<List<CatalogRow>> catalogRows;
    private final ComputedSignal<String> pickerClasses;
    private final ComputedSignal<String> workspaceClasses;

    public MechLabViewModel(Reactor reactor, MechBay bay) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (bay == null) throw new IllegalArgumentException("mech bay is required");
        this.bay = bay;
        CampaignMechSquad initialSquad = bay.activeSquad();
        CampaignMech initialMech = firstMech(initialSquad);
        revision = reactor.signal(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedMechId = reactor.signal(initialMech != null ? initialMech.id() : null);
        selectedSlot = reactor.signal(SlotId.MINI_FAB);
        assetPickerOpen = reactor.signal(false);
        feedbackText = reactor.signal(
                "Select a location on the doll. Only stocked bay hardware can be committed.");
        feedbackClasses = reactor.signal("mech-lab-feedback tone-muted surface-dark");
        labSummary = reactor.computed(this::buildLabSummary);
        squadRows = reactor.computed(this::buildSquadRows);
        mechRows = reactor.computed(this::buildMechRows);
        selectedMechName = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.displayName() : "NO ASSET SELECTED";
        });
        selectedMechIdentity = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.variant().displayName + " chassis  ·  WALKER / HEAVY ASSET"
                    : "Select an assigned heavy asset from the active lance.";
        });
        selectedMechDoctrine = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? roleLabel(mech.role()) : "Doctrine unavailable";
        });
        performanceMeters = reactor.computed(this::buildPerformanceMeters);
        leftSlotRows = reactor.computed(() -> buildSlots(List.of(
                SlotId.CORE, SlotId.ARMS, SlotId.LEFT_SHOULDER)));
        rightSlotRows = reactor.computed(() -> buildSlots(List.of(
                SlotId.RIGHT_SHOULDER, SlotId.AMMO_RESERVE, SlotId.MINI_FAB)));
        slotRows = reactor.computed(() -> buildSlots(List.of(SlotId.values())));
        selectedSlotTitle = reactor.computed(() -> selectedSlot.get().label());
        selectedSlotCopy = reactor.computed(this::buildSelectedSlotCopy);
        selectedSlotRule = reactor.computed(this::buildSelectedSlotRule);
        catalogRows = reactor.computed(this::buildCatalogRows);
        pickerClasses = reactor.computed(() -> assetPickerOpen.get()
                ? "asset-picker panel" : "asset-picker panel hidden");
        workspaceClasses = reactor.computed(() -> assetPickerOpen.get()
                ? "fitting-workspace hidden" : "fitting-workspace");
    }

    public Signal<String> labSummary() { return labSummary; }
    public Signal<List<SquadRow>> squadRows() { return squadRows; }
    public Signal<List<MechRow>> mechRows() { return mechRows; }
    public Signal<String> selectedMechName() { return selectedMechName; }
    public Signal<String> selectedMechIdentity() { return selectedMechIdentity; }
    public Signal<String> selectedMechDoctrine() { return selectedMechDoctrine; }
    public Signal<List<PerformanceMeter>> performanceMeters() { return performanceMeters; }
    public Signal<List<SlotRow>> leftSlotRows() { return leftSlotRows; }
    public Signal<List<SlotRow>> rightSlotRows() { return rightSlotRows; }
    public Signal<List<SlotRow>> slotRows() { return slotRows; }
    public Signal<String> selectedSlotTitle() { return selectedSlotTitle; }
    public Signal<String> selectedSlotCopy() { return selectedSlotCopy; }
    public Signal<String> selectedSlotRule() { return selectedSlotRule; }
    public Signal<List<CatalogRow>> catalogRows() { return catalogRows; }
    public Signal<String> pickerClasses() { return pickerClasses; }
    public Signal<String> workspaceClasses() { return workspaceClasses; }
    public Runnable openAssetPickerAction() { return this::openAssetPicker; }
    public Runnable closeAssetPickerAction() { return this::closeAssetPicker; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

    /** Current preview identity; the canvas deliberately reads no mutable battle state. */
    public MechVariant selectedVariant() {
        CampaignMech mech = selectedMech();
        return mech != null ? mech.variant() : null;
    }

    /** Reprojects mutable campaign authority whenever the room is re-entered. */
    public void refresh() {
        CampaignMechSquad squad = bay.squadById(selectedSquadId.get());
        if (squad == null) {
            squad = bay.activeSquad();
            selectedSquadId.set(squad != null ? squad.id() : null);
        }
        if (squad == null || squad.mechById(selectedMechId.get()) == null) {
            CampaignMech mech = firstMech(squad);
            selectedMechId.set(mech != null ? mech.id() : null);
        }
        revision.update(value -> value + 1);
    }

    private String buildLabSummary() {
        revision.get();
        CampaignMechSquad active = bay.activeSquad();
        int chassis = active != null ? active.mechs().size() : 0;
        return bay.squads().size() + (bay.squads().size() == 1
                ? " support lance" : " support lances") + "  ·  "
                + chassis + " / " + CampaignMechSquad.CAPACITY
                + " heavy assets assigned  ·  loadout freezes at mission commit";
    }

    private List<SquadRow> buildSquadRows() {
        revision.get();
        String selected = selectedSquadId.get();
        CampaignMechSquad active = bay.activeSquad();
        List<SquadRow> rows = new ArrayList<>();
        for (CampaignMechSquad squad : bay.squads()) {
            boolean activeSupport = active != null && squad.id().equals(active.id());
            String base = "mech-squad:" + squad.id();
            rows.add(new SquadRow(base, base + ":name", base + ":status", base + ":strength",
                    squad.id().equals(selected) ? "mech-squad-row selected" : "mech-squad-row",
                    squad.displayName(), activeSupport ? "ACTIVE SUPPORT" : "AVAILABLE",
                    squad.mechs().size() + " / " + CampaignMechSquad.CAPACITY + " assets",
                    () -> selectSquad(squad.id())));
        }
        return List.copyOf(rows);
    }

    private List<MechRow> buildMechRows() {
        revision.get();
        String selected = selectedMechId.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        List<MechRow> rows = new ArrayList<>();
        for (CampaignMech mech : squad.mechs()) {
            String base = "mech:" + mech.id();
            rows.add(new MechRow(base, base + ":name", base + ":chassis", base + ":subsystem",
                    base + ":action",
                    mech.id().equals(selected) ? "mech-row selected" : "mech-row",
                    mech.displayName(), mech.variant().displayName + "  ·  " + roleLabel(mech.role()),
                    mech.missileReplenisher().displayName(), () -> selectMech(mech.id())));
        }
        return List.copyOf(rows);
    }

    private List<PerformanceMeter> buildPerformanceMeters() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return List.of();
        MechVariant v = mech.variant();
        return List.of(
                meter("armor", "ARMOR", Math.round(v.armorPool) + " PLATE",
                        v.armorPool, maximum(x -> x.armorPool)),
                meter("mobility", "MOBILITY", number(v.moveSpeed) + " CELLS/S",
                        v.moveSpeed, maximum(x -> x.moveSpeed)),
                meter("range", "MAX RANGE", number(v.maxWeaponRange()) + " CELLS",
                        v.maxWeaponRange(), maximum(MechVariant::maxWeaponRange)),
                meter("endurance", "MISSILES", missileTriggers(v) + " TRIGGERS",
                        missileTriggers(v), maximum(MechLabViewModel::missileTriggers)));
    }

    private List<SlotRow> buildSlots(List<SlotId> slots) {
        revision.get();
        CampaignMech mech = selectedMech();
        List<SlotRow> rows = new ArrayList<>();
        for (SlotId slot : slots) {
            String base = "mech-slot:" + slot.name().toLowerCase(Locale.ROOT);
            rows.add(new SlotRow(base, base + ":name", base + ":component", base + ":type",
                    slot == selectedSlot.get() ? "doll-slot selected" : "doll-slot",
                    slot.label(), slotComponent(mech, slot), slotType(slot),
                    () -> selectSlot(slot)));
        }
        return List.copyOf(rows);
    }

    private String buildSelectedSlotCopy() {
        CampaignMech mech = selectedMech();
        if (mech == null) return "No heavy asset selected.";
        return switch (selectedSlot.get()) {
            case MINI_FAB -> "Restocks missile trigger packs during battle.";
            case CORE -> "Chassis-integrated powerplant; future cores can trade output, heat and mass.";
            case AMMO_RESERVE -> ammoSummary(mech.variant()) + ". Current bins are integral.";
            case ARMS, LEFT_SHOULDER, RIGHT_SHOULDER ->
                    "Installed weapon assembly. Typed sockets will gate equipment and slot use.";
        };
    }

    private String buildSelectedSlotRule() {
        return switch (selectedSlot.get()) {
            case MINI_FAB -> "UTILITY  ·  1 SLOT  ·  FINITE STOCK";
            case CORE -> "CORE SOCKET  ·  FACTORY LOCKED IN CURRENT CAMPAIGN MODEL";
            case AMMO_RESERVE -> "AMMO BAYS  ·  FACTORY LOCKED IN CURRENT CAMPAIGN MODEL";
            case ARMS -> "BALLISTIC SOCKET  ·  SIZED-SLOT SCHEMA PENDING";
            case LEFT_SHOULDER, RIGHT_SHOULDER -> "MISSILE SOCKET  ·  SIZED-SLOT SCHEMA PENDING";
        };
    }

    private List<CatalogRow> buildCatalogRows() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (selectedSlot.get() == SlotId.MINI_FAB) return replenisherCatalog(mech);
        String base = "mech-catalog:installed:" + selectedSlot.get().name().toLowerCase(Locale.ROOT);
        return List.of(new CatalogRow(base, base + ":copy", base + ":name", base + ":stock",
                base + ":detail", base + ":action", "catalog-row selected",
                slotComponent(mech, selectedSlot.get()), "INSTALLED ASSEMBLY",
                buildSelectedSlotRule(), "FACTORY LOCKED", true, () -> { }));
    }

    private List<CatalogRow> replenisherCatalog(CampaignMech mech) {
        List<CatalogRow> rows = new ArrayList<>();
        for (MissileReplenisherComponent component : MissileReplenisherComponent.catalog()) {
            boolean installed = mech != null && component.id().equals(mech.missileReplenisherId());
            int owned = bay.ownedReplenisher(component.id());
            int fielded = bay.installedReplenisher(component.id());
            int free = bay.availableReplenisher(component.id());
            boolean disabled = mech == null || installed || free <= 0;
            String base = "mech-catalog:" + component.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":name", base + ":stock",
                    base + ":detail", base + ":action",
                    installed ? "catalog-row selected" : "catalog-row", component.displayName(),
                    "OWN " + owned + "  ·  FIELD " + fielded + "  ·  FREE " + free,
                    "SRM " + number(component.srmReplenishmentSeconds()) + "s  ·  LRM "
                            + number(component.lrmReplenishmentSeconds()) + "s",
                    installed ? "INSTALLED" : free > 0 ? "INSTALL" : "COMMITTED",
                    disabled, () -> install(component.id())));
        }
        return List.copyOf(rows);
    }

    private void selectSquad(String squadId) {
        if (!bay.selectActiveSquad(squadId)) return;
        CampaignMechSquad squad = bay.squadById(squadId);
        selectedSquadId.set(squadId);
        CampaignMech mech = firstMech(squad);
        selectedMechId.set(mech != null ? mech.id() : null);
        selectedSlot.set(SlotId.MINI_FAB);
        feedbackText.set(squad.displayName() + " is now the active Mech Support lance.");
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        revision.update(value -> value + 1);
    }

    private void selectMech(String mechId) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.mechById(mechId) == null) return;
        selectedMechId.set(mechId);
        selectedSlot.set(SlotId.MINI_FAB);
        assetPickerOpen.set(false);
        feedbackText.set("Inspecting " + squad.mechById(mechId).displayName()
                + ". No campaign hardware changed.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void openAssetPicker() {
        assetPickerOpen.set(true);
        feedbackText.set("Choose a support lance and heavy asset for the fabrication gantry.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void closeAssetPicker() {
        assetPickerOpen.set(false);
        feedbackText.set("Returned to the fitting gantry. No campaign hardware changed.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void selectSlot(SlotId slot) {
        selectedSlot.set(slot);
        feedbackText.set(slot.label() + " selected. " + (slot == SlotId.MINI_FAB
                ? "Compatible fleet stock is ready for refit."
                : "Inspection only; this hardware has no campaign refit authority yet."));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void install(String componentId) {
        CampaignMech mech = selectedMech();
        MissileReplenisherComponent component = MissileReplenisherComponent.findById(componentId);
        if (mech == null || component == null) return;
        boolean installed = bay.installReplenisher(mech.id(), component.id());
        feedbackText.set(installed
                ? component.displayName() + " installed on " + mech.displayName() + "."
                : "Refit blocked: no unassigned component is available.");
        feedbackClasses.set(installed ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        revision.update(value -> value + 1);
    }

    private CampaignMechSquad selectedSquad() {
        revision.get();
        return bay.squadById(selectedSquadId.get());
    }

    private CampaignMech selectedMech() {
        CampaignMechSquad squad = selectedSquad();
        return squad != null ? squad.mechById(selectedMechId.get()) : null;
    }

    private static CampaignMech firstMech(CampaignMechSquad squad) {
        return squad == null || squad.mechs().isEmpty() ? null : squad.mechs().get(0);
    }

    private static String slotComponent(CampaignMech mech, SlotId slot) {
        if (mech == null) return "NO ASSET";
        MechVariant variant = mech.variant();
        return switch (slot) {
            case CORE -> variant.displayName + " integrated core";
            case ARMS -> componentName(variant.arms);
            case LEFT_SHOULDER -> componentName(variant.leftShoulder);
            case RIGHT_SHOULDER -> componentName(variant.rightShoulder);
            case AMMO_RESERVE -> ammoSummary(variant);
            case MINI_FAB -> mech.missileReplenisherId().equals(
                    MissileReplenisherComponent.ACCELERATED_FEED.id())
                    ? "Accelerated feed" : "Standard replenisher";
        };
    }

    private static String slotType(SlotId slot) {
        return switch (slot) {
            case CORE -> "CORE / FIXED";
            case ARMS -> "BALLISTIC / FIXED";
            case LEFT_SHOULDER, RIGHT_SHOULDER -> "MISSILE / FIXED";
            case AMMO_RESERVE -> "AMMO / INTEGRAL";
            case MINI_FAB -> "UTILITY / SWAP";
        };
    }

    private static String componentName(MechWeaponComponent component) {
        return component != null ? component.displayName : "Empty hardpoint";
    }

    private static String ammoSummary(MechVariant variant) {
        return missileTriggers(variant) + " missile triggers ready";
    }

    private static int missileTriggers(MechVariant variant) {
        return finiteAmmo(variant.leftShoulder) + finiteAmmo(variant.rightShoulder);
    }

    private static int finiteAmmo(MechWeaponComponent component) {
        return component != null && component.ammoCapacity > 0 ? component.ammoCapacity : 0;
    }

    private static PerformanceMeter meter(String suffix, String label, String value,
                                          float amount, float maximum) {
        int percent = maximum > 0f ? Math.round(amount / maximum * 100f) : 0;
        String base = "mech-meter:" + suffix;
        return new PerformanceMeter(base, base + ":label", base + ":value",
                base + ":track", base + ":fill", label, value,
                "width: " + Math.max(0, Math.min(100, percent)) + "%;");
    }

    private static float maximum(VariantMetric metric) {
        float max = 0f;
        for (MechVariant variant : MechVariant.values()) max = Math.max(max, metric.value(variant));
        return max;
    }

    private static String roleLabel(MechRole role) {
        return switch (role) {
            case LR_SUPPORT -> "LONG-RANGE SUPPORT";
            case ARMORED_SUPPORT -> "ARMORED SUPPORT";
            case ASSAULT -> "ASSAULT";
        };
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return Integer.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private interface VariantMetric { float value(MechVariant variant); }

    public record SquadRow(String id, String nameId, String statusId, String strengthId,
                           String classes, String name, String status, String strength,
                           Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "nameId" -> nameId; case "statusId" -> statusId;
            case "strengthId" -> strengthId; case "classes" -> classes; case "name" -> name;
            case "status" -> status; case "strength" -> strength; case "select" -> select;
            default -> throw unknown("mech-squad", p); }; }
    }

    public record MechRow(String id, String nameId, String chassisId, String subsystemId,
                          String actionId,
                          String classes, String name, String chassis, String subsystem,
                          Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "nameId" -> nameId; case "chassisId" -> chassisId;
            case "subsystemId" -> subsystemId; case "actionId" -> actionId;
            case "classes" -> classes; case "name" -> name;
            case "chassis" -> chassis; case "subsystem" -> subsystem; case "select" -> select;
            default -> throw unknown("mech", p); }; }
    }

    public record PerformanceMeter(String id, String labelId, String valueId, String trackId,
                                   String fillId, String label, String value, String fillStyle)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "labelId" -> labelId; case "valueId" -> valueId;
            case "trackId" -> trackId; case "fillId" -> fillId; case "label" -> label;
            case "value" -> value; case "fillStyle" -> fillStyle;
            default -> throw unknown("mech-meter", p); }; }
    }

    public record SlotRow(String id, String nameId, String componentId, String typeId,
                          String classes, String name, String component, String type,
                          Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "nameId" -> nameId; case "componentId" -> componentId;
            case "typeId" -> typeId; case "classes" -> classes; case "name" -> name;
            case "component" -> component; case "type" -> type; case "select" -> select;
            default -> throw unknown("mech-slot", p); }; }
    }

    public record CatalogRow(String id, String copyId, String nameId, String stockId,
                             String detailId, String actionId, String classes, String name,
                             String stock, String detail, String actionLabel,
                             boolean actionDisabled, Runnable action)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "copyId" -> copyId; case "nameId" -> nameId;
            case "stockId" -> stockId; case "detailId" -> detailId; case "actionId" -> actionId;
            case "classes" -> classes; case "name" -> name; case "stock" -> stock;
            case "detail" -> detail; case "actionLabel" -> actionLabel;
            case "actionDisabled" -> actionDisabled; case "action" -> action;
            default -> throw unknown("mech-catalog", p); }; }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
