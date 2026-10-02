package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechFabricationCatalog;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MechWorkshop;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Spatial doll projection and command surface for the campaign-authoritative {@link MechBay}. */
public final class MechLabViewModel {

    private final MechBay bay;
    private final FabricationResources resources;
    private final MechWorkshop workshop;
    private final Runnable bayChanged;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<String> selectedMechId;
    private final MutableSignal<Integer> selectedGantry;
    private final MutableSignal<SocketId> selectedSlot;
    private final MutableSignal<Boolean> fittingFocused;
    private final MutableSignal<Boolean> assetPickerOpen;
    private final MutableSignal<String> callSignDraft;
    private final MutableSignal<String> feedbackText;
    private final MutableSignal<String> feedbackClasses;
    private final MutableSignal<CategoryFilter> catalogFilter;
    private final MutableSignal<MechWeaponComponent> hoveredWeapon;
    private final ComputedSignal<List<FilterPill>> categoryFilterPills;
    private final ComputedSignal<String> labSummary;
    private final ComputedSignal<List<SquadRow>> squadRows;
    private final ComputedSignal<List<MechRow>> mechRows;
    private final ComputedSignal<List<GantryRow>> gantryRows;
    private final ComputedSignal<String> activeGantryLabel;
    private final ComputedSignal<String> selectedMechName;
    private final ComputedSignal<String> selectedMechNameClasses;
    private final ComputedSignal<String> callSignEditorClasses;
    private final ComputedSignal<Boolean> callSignRenameDisabled;
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
    private final ComputedSignal<String> fittingHeaderClasses;
    private final ComputedSignal<String> performanceClasses;
    private final ComputedSignal<String> catalogClasses;
    private final ComputedSignal<String> slotRackClasses;
    private final ComputedSignal<String> overviewRailClasses;
    private final ComputedSignal<String> garageTitle;

    public MechLabViewModel(Reactor reactor, MechBay bay) {
        this(reactor, bay, FabricationResources.NONE, () -> { });
    }

    public MechLabViewModel(Reactor reactor, MechBay bay,
                            FabricationResources resources) {
        this(reactor, bay, resources, () -> { });
    }

    public MechLabViewModel(Reactor reactor, MechBay bay,
                            FabricationResources resources, Runnable bayChanged) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (bay == null) throw new IllegalArgumentException("mech bay is required");
        if (resources == null || bayChanged == null) {
            throw new IllegalArgumentException("fabrication resources and change callback are required");
        }
        this.bay = bay;
        this.resources = resources;
        workshop = new MechWorkshop(bay, resources);
        this.bayChanged = bayChanged;
        CampaignMechSquad initialSquad = bay.activeSquad();
        revision = reactor.signal(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedMechId = reactor.signal(null);
        selectedGantry = reactor.signal(0);
        selectedSlot = reactor.signal(SocketId.MINI_FAB);
        fittingFocused = reactor.signal(false);
        assetPickerOpen = reactor.signal(false);
        callSignDraft = reactor.signal("");
        feedbackText = reactor.signal(
                "Select an occupied gantry to refit, or press + on a vacant pad to fabricate a chassis.");
        feedbackClasses = reactor.signal("mech-lab-feedback tone-muted surface-dark");
        catalogFilter = reactor.signal(CategoryFilter.ALL);
        hoveredWeapon = reactor.signal(null);
        categoryFilterPills = reactor.computed(this::buildFilterPills);
        labSummary = reactor.computed(this::buildLabSummary);
        squadRows = reactor.computed(this::buildSquadRows);
        mechRows = reactor.computed(this::buildMechRows);
        gantryRows = reactor.computed(this::buildGantryRows);
        activeGantryLabel = reactor.computed(() -> {
            int index = selectedGantryIndex();
            CampaignMech mech = mechAt(selectedSquad(), index);
            return String.format(Locale.ROOT, "GANTRY %02d / %02d%s", index + 1,
                    CampaignMechSquad.CAPACITY, mech != null ? "" : "  ·  VACANT");
        });
        selectedMechName = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.displayName() : fabricatingChassis()
                    ? String.format(Locale.ROOT, "VACANT GANTRY %02d", selectedGantryIndex() + 1)
                    : "NO ASSET SELECTED";
        });
        selectedMechNameClasses = reactor.computed(() -> selectedMech() == null
                ? "label asset-name tone-accent"
                : "label asset-name tone-accent hidden");
        callSignEditorClasses = reactor.computed(() -> selectedMech() == null
                ? "mech-call-sign-editor hidden" : "mech-call-sign-editor");
        callSignRenameDisabled = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            String candidate = callSignDraft.get();
            if (mech == null || candidate == null) return true;
            String trimmed = candidate.trim();
            return trimmed.isEmpty()
                    || trimmed.length() > CampaignMech.CALLSIGN_MAX_LENGTH
                    || trimmed.equals(mech.displayName());
        });
        selectedMechIdentity = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.variant().displayName + " chassis  ·  WALKER / HEAVY ASSET"
                    : fabricatingChassis()
                    ? "Select a discovered chassis pattern and commit fleet materials."
                    : "Select an assigned heavy asset from the active lance.";
        });
        selectedMechDoctrine = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return fabricatingChassis() ? "CHASSIS FORGE" : "Doctrine unavailable";
            return roleLabel(mech.role()) + "  ·  " + doctrineAdvice(mech.role());
        });
        performanceMeters = reactor.computed(this::buildPerformanceMeters);
        leftSlotRows = reactor.computed(() -> buildSlots(List.of(
                SocketId.CORE, SocketId.ARMS, SocketId.LEFT_SHOULDER)));
        rightSlotRows = reactor.computed(() -> buildSlots(List.of(
                SocketId.RIGHT_SHOULDER, SocketId.AMMO_RESERVE, SocketId.MINI_FAB)));
        slotRows = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return List.of();
            MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
            return buildSlots(layout.sockets().stream().map(SocketDef::id).toList());
        });
        selectedSlotTitle = reactor.computed(() -> {
            if (fabricatingChassis()) return "CHASSIS PATTERNS";
            CampaignMech mech = selectedMech();
            if (mech != null) {
                SocketDef def = MechFittingLayout.forVariant(mech.variant()).socket(selectedSlot.get());
                if (def != null) return def.label();
            }
            return selectedSlot.get().label();
        });
        selectedSlotCopy = reactor.computed(this::buildSelectedSlotCopy);
        selectedSlotRule = reactor.computed(this::buildSelectedSlotRule);
        catalogRows = reactor.computed(this::buildCatalogRows);
        pickerClasses = reactor.computed(() -> assetPickerOpen.get()
                ? "asset-picker panel" : "asset-picker panel hidden");
        workspaceClasses = reactor.computed(() -> assetPickerOpen.get()
                ? "fitting-workspace hidden" : "fitting-workspace");
        fittingHeaderClasses = reactor.computed(() -> fittingFocused.get()
                ? "asset-strip edge-surface" : "asset-strip edge-surface hidden");
        performanceClasses = reactor.computed(() -> fittingFocused.get() && selectedMech() != null
                ? "performance-grid" : "performance-grid hidden");
        catalogClasses = reactor.computed(() -> fittingFocused.get()
                ? "panel catalog-panel" : "panel catalog-panel hidden");
        slotRackClasses = reactor.computed(() -> fittingFocused.get() && selectedMech() != null
                ? "panel slot-panel" : "panel slot-panel hidden");
        overviewRailClasses = reactor.computed(() -> fittingFocused.get()
                ? "overview-rail hidden" : "overview-rail");
        garageTitle = reactor.computed(() -> fittingFocused.get()
                ? activeGantryLabel.get() : "ACTIVE LANCE  //  ALL GANTRIES");
    }

    public Signal<String> labSummary() { return labSummary; }
    public Signal<List<SquadRow>> squadRows() { return squadRows; }
    public Signal<List<MechRow>> mechRows() { return mechRows; }
    public Signal<List<GantryRow>> gantryRows() { return gantryRows; }
    public Signal<String> activeGantryLabel() { return activeGantryLabel; }
    public Signal<String> selectedMechName() { return selectedMechName; }
    public Signal<String> callSignDraft() { return callSignDraft; }
    public Signal<String> selectedMechNameClasses() { return selectedMechNameClasses; }
    public Signal<String> callSignEditorClasses() { return callSignEditorClasses; }
    public Signal<Boolean> callSignRenameDisabled() { return callSignRenameDisabled; }
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
    public Signal<String> fittingHeaderClasses() { return fittingHeaderClasses; }
    public Signal<String> performanceClasses() { return performanceClasses; }
    public Signal<String> catalogClasses() { return catalogClasses; }
    public Signal<String> slotRackClasses() { return slotRackClasses; }
    public Signal<String> overviewRailClasses() { return overviewRailClasses; }
    public Signal<String> garageTitle() { return garageTitle; }
    public Runnable openAssetPickerAction() { return this::openAssetPicker; }
    public Runnable closeAssetPickerAction() { return this::closeAssetPicker; }
    public Runnable previousGantryAction() { return this::previousGantry; }
    public Runnable nextGantryAction() { return this::nextGantry; }
    public Runnable selectGantryAction(int index) { return () -> selectGantry(index); }
    public Runnable overviewAction() { return this::showLanceOverview; }
    public Consumer<String> editCallSignAction() { return this::editCallSign; }
    public Runnable renameCallSignAction() { return this::renameSelectedMech; }
    public Signal<List<FilterPill>> categoryFilterPills() { return categoryFilterPills; }
    public Signal<CategoryFilter> catalogFilter() { return catalogFilter; }
    public void setFilter(CategoryFilter filter) {
        if (filter == null) return;
        catalogFilter.set(filter);
    }
    public CategoryFilter filter() { return catalogFilter.get(); }
    public void hoverWeapon(MechWeaponComponent component) {
        if (hoveredWeapon.get() == component) return;
        hoveredWeapon.set(component);
    }
    public void clearHoverWeapon() {
        if (hoveredWeapon.get() == null) return;
        hoveredWeapon.set(null);
    }
    public MechWeaponComponent hoveredWeapon() { return hoveredWeapon.get(); }
    public Runnable selectSlotAction(SocketId slot) { return () -> selectSlot(slot); }
    public Runnable stripWeaponAction(SocketId slot) { return () -> stripWeapon(slot); }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

    /** Current authored fitting socket; shared with the physical room overlay. */
    public SocketId selectedSocket() { return selectedSlot.get(); }

    /** Whether the garage camera should frame the selected asset for fitting. */
    public boolean fittingFocused() { return fittingFocused.get(); }

    /** Current preview identity; the canvas deliberately reads no mutable battle state. */
    public MechVariant selectedVariant() {
        CampaignMech mech = selectedMech();
        return mech != null ? mech.variant() : null;
    }

    /** Socket geometry currently projected into one fitting-rack row. */
    public SocketDef socketDefinition(SocketId id) {
        CampaignMech mech = selectedMech();
        return mech != null ? MechFittingLayout.forVariant(mech.variant()).socket(id) : null;
    }

    /** Installed weapon currently projected into one fitting-rack row. */
    public MechWeaponComponent installedWeapon(SocketId id) {
        return slotWeapon(selectedMech(), id);
    }

    public MissileReplenisherComponent installedReplenisher(SocketId id) {
        CampaignMech mech = selectedMech();
        return mech != null && id == SocketId.MINI_FAB
                ? MissileReplenisherComponent.resolve(mech.missileReplenisherId()) : null;
    }

    public boolean socketOccupied(SocketId id) {
        CampaignMech mech = selectedMech();
        return mech != null && (installedWeapon(id) != null
                || id == SocketId.CORE || id == SocketId.AMMO_RESERVE
                || id == SocketId.MINI_FAB);
    }

    /** Current lance in authored gantry order for the embedded garage scene. */
    public List<MechVariant> gantryVariants() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        return squad.mechs().stream().map(CampaignMech::variant).toList();
    }

    /** Frozen fitting values used to keep the physical gantries visually honest. */
    public List<MechDeploymentSpec> gantryDeployments() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        return squad.mechs().stream().map(CampaignMech::freezeForDeployment).toList();
    }

    /** Selected vehicle's stable camera target within the current lance. */
    public int selectedGantryIndex() {
        revision.get();
        return Math.max(0, Math.min(CampaignMechSquad.CAPACITY - 1,
                selectedGantry.get()));
    }

    /** Reprojects mutable campaign authority whenever the room is re-entered. */
    public void refresh() {
        CampaignMechSquad squad = bay.squadById(selectedSquadId.get());
        if (squad == null) {
            squad = bay.activeSquad();
            selectedSquadId.set(squad != null ? squad.id() : null);
        }
        selectedMechId.set(null);
        callSignDraft.set("");
        fittingFocused.set(false);
        assetPickerOpen.set(false);
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

    private List<GantryRow> buildGantryRows() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        List<GantryRow> rows = new ArrayList<>();
        for (int index = 0; index < CampaignMechSquad.CAPACITY; index++) {
            CampaignMech mech = mechAt(squad, index);
            String base = "overview-gantry:" + index;
            int gantry = index;
            rows.add(new GantryRow(base, base + ":station", base + ":name",
                    base + ":detail", mech != null ? "overview-gantry occupied"
                            : "overview-gantry vacant",
                    String.format(Locale.ROOT, "GANTRY %02d", index + 1),
                    mech != null ? mech.displayName() : "VACANT",
                    mech != null ? mech.variant().displayName + "  ·  " + roleLabel(mech.role())
                            : "PRESS + ON PAD TO FABRICATE",
                    mech == null, () -> selectGantry(gantry)));
        }
        return List.copyOf(rows);
    }

    private List<PerformanceMeter> buildPerformanceMeters() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return List.of();
        MechVariant v = mech.variant();
        float currentRange = maxWeaponRange(mech);
        int currentMissiles = missileTriggers(mech);

        MechWeaponComponent candidate = hoveredWeapon.get();
        MechMountSlot mount = mountFor(selectedSlot.get());
        float ghostRange = currentRange;
        int ghostMissiles = currentMissiles;
        if (candidate != null && mount != null) {
            ghostRange = prospectiveWeaponRange(mech, mount, candidate);
            ghostMissiles = prospectiveMissileTriggers(mech, mount, candidate);
        }

        return List.of(
                meter("armor", "ARMOR", Math.round(v.armorCapacity) + " PLATE",
                        v.armorCapacity, maximum(x -> x.armorCapacity), v.armorCapacity),
                meter("mobility", "MOBILITY", number(v.moveSpeed) + " CELLS/S",
                        v.moveSpeed, maximum(x -> x.moveSpeed), v.moveSpeed),
                meter("range", "MAX RANGE", number(currentRange) + " CELLS",
                        currentRange, maximumWeaponRange(), ghostRange),
                meter("endurance", "MISSILES", currentMissiles + " TRIGGERS",
                        currentMissiles, maximumMissileTriggers(), ghostMissiles));
    }

    private List<SlotRow> buildSlots(List<SocketId> slots) {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return List.of();
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        List<SlotRow> rows = new ArrayList<>();
        for (SocketId slot : slots) {
            SocketDef definition = layout.socket(slot);
            if (definition == null) continue;
            MechWeaponComponent weapon = slotWeapon(mech, slot);
            boolean locked = definition.factoryLocked();
            boolean armed = weapon != null || slot == SocketId.CORE || slot == SocketId.AMMO_RESERVE;
            String badge = locked ? "LOCKED" : armed ? "ARMED" : "EMPTY";
            String badgeClasses = locked ? "slot-badge locked" : armed ? "slot-badge armed" : "slot-badge empty";

            String telemetry;
            if (weapon != null) {
                telemetry = "DMG " + number(weapon.weaponDef().damage) + " · RNG " + number(weapon.weaponDef().range)
                        + (weapon.ammoCapacity > 0 ? " · AMMO " + weapon.ammoCapacity : "");
            } else if (slot == SocketId.MINI_FAB) {
                telemetry = "SRM " + number(mech.missileReplenisher().srmReplenishmentSeconds()) + "S · LRM "
                        + number(mech.missileReplenisher().lrmReplenishmentSeconds()) + "S";
            } else if (slot == SocketId.CORE) {
                telemetry = "INTEGRAL POWERPLANT";
            } else if (slot == SocketId.AMMO_RESERVE) {
                telemetry = ammoSummary(mech);
            } else {
                telemetry = "UNFITTED HARDPOINT";
            }

            boolean canStrip = !locked && weapon != null;
            String stripClasses = canStrip ? "slot-strip-action" : "slot-strip-action hidden";

            String base = "mech-slot:" + slot.name().toLowerCase(Locale.ROOT);
            rows.add(new SlotRow(base, base + ":content", base + ":select", base + ":strip",
                    base + ":copy", base + ":header", base + ":name",
                    base + ":badge", base + ":component", base + ":telemetry", base + ":type", base + ":grid",
                    slot,
                    slot == selectedSlot.get() ? "doll-slot selected" : "doll-slot",
                    definition.label(), badge, badgeClasses,
                    slotComponent(mech, slot), telemetry, slotType(definition),
                    canStrip, stripClasses,
                    () -> selectSlot(slot),
                    () -> stripWeapon(slot)));
        }
        return List.copyOf(rows);
    }

    private String buildSelectedSlotCopy() {
        CampaignMech mech = selectedMech();
        if (mech == null) return fabricatingChassis()
                ? "Fabricate a complete standard-fit walker here."
                : "No heavy asset selected.";
        return switch (selectedSlot.get()) {
            case MINI_FAB -> "Restocks missile trigger packs during battle.";
            case CORE -> "Chassis-integrated powerplant; future cores can trade output, heat and mass.";
            case AMMO_RESERVE -> ammoSummary(mech) + ". Current bins are integral.";
            case ARMS, LEFT_SHOULDER, RIGHT_SHOULDER ->
                    slotComponent(mech, selectedSlot.get()).equals("Empty hardpoint")
                            ? "Empty mount. Fit compatible missile hardware here."
                            : "Installed weapon assembly. Socket type and capacity gate replacement equipment.";
        };
    }

    private String buildSelectedSlotRule() {
        if (fabricatingChassis()) {
            return "PLAYER CARGO  ·  STANDARD FIT  ·  VACANT GANTRY";
        }
        SocketDef definition = selectedSocketDefinition();
        if (definition == null) return "NO SOCKET DEFINITION";
        String authority = definition.factoryLocked()
                ? "LOCKED" : selectedSlot.get() == SocketId.MINI_FAB
                ? "FINITE STOCK" : "OPEN";
        return definition.type().label() + " SOCKET  ·  "
                + definition.gridColumns() + "×" + definition.gridRows() + " GRID  ·  "
                + authority;
    }

    private List<CatalogRow> buildCatalogRows() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (fabricatingChassis()) return chassisCatalog();
        if (selectedSlot.get() == SocketId.MINI_FAB) return replenisherCatalog(mech);
        MechMountSlot mount = mountFor(selectedSlot.get());
        if (mount != null && mech != null) return weaponCatalog(mech, mount);
        String base = "mech-catalog:installed:" + selectedSlot.get().name().toLowerCase(Locale.ROOT);
        SocketDef definition = selectedSocketDefinition();
        boolean occupied = slotWeapon(mech, selectedSlot.get()) != null
                || selectedSlot.get() == SocketId.CORE
                || selectedSlot.get() == SocketId.AMMO_RESERVE;
        return List.of(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                base + ":stock", base + ":telemetry", base + ":delta",
                base + ":detail", base + ":action", base + ":materials", base + ":body",
                base + ":preview",
                "catalog-preview hidden", null, null, null, "catalog-row selected",
                slotComponent(mech, selectedSlot.get()),
                occupied ? "INSTALLED" : "EMPTY",
                occupied ? "catalog-badge in-cargo" : "catalog-badge fabrication",
                occupied ? "INSTALLED ASSEMBLY" : "EMPTY SOCKET",
                "", "", "catalog-delta hidden",
                buildSelectedSlotRule(), definition != null && definition.factoryLocked()
                        ? "FACTORY LOCKED" : "NO COMPATIBLE STOCK", true, List.of(), "material-costs hidden", () -> { }));
    }

    private List<CatalogRow> weaponCatalog(CampaignMech mech, MechMountSlot mount) {
        List<CatalogRow> rows = new ArrayList<>();
        CategoryFilter filter = catalogFilter.get();
        MechWeaponComponent current = slotWeapon(mech, selectedSlot.get());

        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.weapons()) {
            MechWeaponComponent component = recipe.component();
            SocketDef socket = selectedSocketDefinition();
            if (socket == null || !component.accepts(mount)
                    || socket.type() != MechFittingLayout.SocketType.OMNI
                    && !socket.type().name().equals(component.hardpointType.name())) continue;

            int owned = bay.ownedWeapon(component.id);
            int fielded = bay.installedWeapon(component.id);
            int free = bay.availableWeapon(component.id);

            // Category filter
            if (filter == CategoryFilter.BALLISTIC && component.hardpointType != MechWeaponComponent.HardpointType.BALLISTIC) continue;
            if (filter == CategoryFilter.MISSILE && component.hardpointType != MechWeaponComponent.HardpointType.MISSILE) continue;
            if (filter == CategoryFilter.ENERGY && component.hardpointType != MechWeaponComponent.HardpointType.ENERGY) continue;
            if (filter == CategoryFilter.IN_STOCK && free <= 0) continue;

            boolean fits = socket.accommodates(
                    component.footprintColumns, component.footprintRows);
            boolean installed = mech.weaponAt(mount) == component;
            boolean inCargo = free > 0;
            boolean fabricate = !inCargo;
            boolean affordable = resources.canAfford(recipe.cost());
            boolean disabled = installed || !fits || fabricate && !affordable;

            String badge = inCargo ? "IN CARGO" : "FABRICATION";
            String badgeClasses = inCargo ? "catalog-badge in-cargo" : "catalog-badge fabrication";

            // Telemetry
            WeaponDef def = component.weaponDef();
            String telemetry = "DMG " + number(def.damage) + " · RNG " + number(def.range)
                    + (component.ammoCapacity > 0 ? " · AMMO " + component.ammoCapacity : " · AMMO ∞");

            // Deltas
            String deltaText = "";
            String deltaClasses = "catalog-delta hidden";
            if (current != null && current != component) {
                float deltaRng = def.range - current.weaponDef().range;
                float deltaDmg = def.damage - current.weaponDef().damage;
                StringBuilder sb = new StringBuilder();
                if (Math.abs(deltaRng) > 0.01f) {
                    sb.append("RNG ").append(deltaRng > 0 ? "+" : "").append(number(deltaRng));
                }
                if (Math.abs(deltaDmg) > 0.01f) {
                    if (!sb.isEmpty()) sb.append("  ·  ");
                    sb.append("DMG ").append(deltaDmg > 0 ? "+" : "").append(number(deltaDmg));
                }
                if (!sb.isEmpty()) {
                    deltaText = sb.toString();
                    boolean positive = deltaRng >= 0 && deltaDmg >= 0;
                    boolean negative = deltaRng <= 0 && deltaDmg <= 0;
                    deltaClasses = positive ? "catalog-delta positive" : negative ? "catalog-delta negative" : "catalog-delta neutral";
                }
            }

            String actionLabel = installed ? "INSTALLED" : !fits ? "TOO LARGE"
                    : inCargo ? "INSTALL SPARE" : "FABRICATE + INSTALL";

            String base = "mech-catalog:" + component.id;
            List<MaterialRow> materials = inCargo ? List.of() : materialRows(base, recipe.cost());
            String materialsClasses = inCargo ? "material-costs hidden" : "material-costs";

            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview", null, component, null,
                    installed ? "catalog-row selected" : "catalog-row", component.displayName,
                    badge, badgeClasses,
                    recipe.provenance() + "  ·  OWN " + owned + " / FREE " + free,
                    telemetry, deltaText, deltaClasses,
                    component.hardpointType + "  ·  " + component.footprintColumns
                            + "×" + component.footprintRows + " GRID",
                    actionLabel, disabled, materials, materialsClasses,
                    () -> fitWeapon(mount, component)));
        }
        return List.copyOf(rows);
    }

    private List<CatalogRow> chassisCatalog() {
        CampaignMechSquad squad = selectedSquad();
        boolean hasSpace = squad != null && bay.canAddMech(squad.id());
        List<CatalogRow> rows = new ArrayList<>();
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.chassis()) {
            boolean affordable = resources.canAfford(recipe.cost());
            MechVariant variant = recipe.variant();
            String base = "mech-catalog:" + recipe.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview", variant, null, null, "catalog-row chassis-pattern",
                    recipe.displayName(), "CHASSIS", "catalog-badge chassis",
                    recipe.provenance(),
                    "SPEED " + number(variant.moveSpeed) + " · ARMOR " + Math.round(variant.armorCapacity),
                    "", "catalog-delta hidden",
                    Math.round(variant.armorCapacity) + " ARMOR  ·  "
                            + number(variant.moveSpeed) + " MOBILITY  ·  "
                            + roleLabel(variant.defaultRole),
                    !hasSpace ? "LANCE FULL" : "FABRICATE CHASSIS",
                    !hasSpace || !affordable, materialRows(base, recipe.cost()), "material-costs",
                    () -> fabricateChassis(variant)));
        }
        return List.copyOf(rows);
    }

    private List<CatalogRow> replenisherCatalog(CampaignMech mech) {
        List<CatalogRow> rows = new ArrayList<>();
        for (MissileReplenisherComponent component : MissileReplenisherComponent.catalog()) {
            boolean installed = mech != null && component.id().equals(mech.missileReplenisherId());
            int owned = bay.ownedReplenisher(component.id());
            int fielded = bay.installedReplenisher(component.id());
            int free = bay.availableReplenisher(component.id());
            boolean disabled = mech == null || installed || free <= 0;
            boolean inCargo = free > 0;
            String base = "mech-catalog:" + component.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview", null, null, component,
                    installed ? "catalog-row selected" : "catalog-row", component.displayName(),
                    inCargo ? "IN CARGO" : "FABRICATION", inCargo ? "catalog-badge in-cargo" : "catalog-badge fabrication",
                    "OWN " + owned + "  ·  FIELD " + fielded + "  ·  FREE " + free,
                    "SRM " + number(component.srmReplenishmentSeconds()) + "S · LRM "
                            + number(component.lrmReplenishmentSeconds()) + "S",
                    "", "catalog-delta hidden",
                    "SRM " + number(component.srmReplenishmentSeconds()) + "s  ·  LRM "
                            + number(component.lrmReplenishmentSeconds()) + "s",
                    installed ? "INSTALLED" : inCargo ? "INSTALL SPARE" : "FABRICATE + INSTALL",
                    disabled, List.of(), "material-costs hidden", () -> install(component.id())));
        }
        return List.copyOf(rows);
    }

    private List<MaterialRow> materialRows(String ownerId, FabricationCost cost) {
        List<MaterialRow> rows = new ArrayList<>();
        for (FabricationCost.Line line : cost.lines()) {
            int available = resources.available(line.commodityId());
            String id = ownerId + ":material:" + line.commodityId();
            rows.add(new MaterialRow(id, id + ":icon", id + ":label",
                    available >= line.quantity() ? "material-cost" : "material-cost short",
                    resources.commodityIcon(line.commodityId()),
                    resources.commodityName(line.commodityId()).toUpperCase(Locale.ROOT)
                            + " " + available + " / " + line.quantity()));
        }
        return List.copyOf(rows);
    }

    private void selectSquad(String squadId) {
        if (!bay.selectActiveSquad(squadId)) return;
        CampaignMechSquad squad = bay.squadById(squadId);
        selectedSquadId.set(squadId);
        selectedMechId.set(null);
        callSignDraft.set("");
        selectedGantry.set(0);
        selectedSlot.set(SocketId.MINI_FAB);
        fittingFocused.set(false);
        feedbackText.set(squad.displayName() + " is now the active Mech Support lance.");
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        revision.update(value -> value + 1);
        bayChanged.run();
    }

    private void selectMech(String mechId) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.mechById(mechId) == null) return;
        for (int index = 0; index < squad.mechs().size(); index++) {
            if (squad.mechs().get(index).id().equals(mechId)) {
                selectedGantry.set(index);
                break;
            }
        }
        selectedMechId.set(mechId);
        callSignDraft.set(squad.mechById(mechId).displayName());
        selectedSlot.set(defaultFittingSlot(squad.mechById(mechId)));
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set("Inspecting " + squad.mechById(mechId).displayName()
                + ". No campaign hardware changed.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void previousGantry() {
        selectGantry(selectedGantryIndex() - 1);
    }

    private void nextGantry() {
        selectGantry(selectedGantryIndex() + 1);
    }

    private void selectGantry(int requested) {
        int capacity = CampaignMechSquad.CAPACITY;
        int index = Math.floorMod(requested, capacity);
        CampaignMech mech = mechAt(selectedSquad(), index);
        selectedGantry.set(index);
        selectedMechId.set(mech != null ? mech.id() : null);
        callSignDraft.set(mech != null ? mech.displayName() : "");
        selectedSlot.set(defaultFittingSlot(mech));
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(mech != null
                ? "Gantry " + String.format(Locale.ROOT, "%02d", index + 1)
                        + " selected: " + mech.displayName() + ". No campaign hardware changed."
                : "Gantry " + String.format(Locale.ROOT, "%02d", index + 1)
                        + " is vacant. Select a chassis pattern to fabricate a new heavy asset.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void openAssetPicker() {
        assetPickerOpen.set(true);
        fittingFocused.set(false);
        selectedMechId.set(null);
        callSignDraft.set("");
        feedbackText.set("Choose a support lance and assigned asset to open its gantry.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void closeAssetPicker() {
        assetPickerOpen.set(false);
        fittingFocused.set(false);
        selectedMechId.set(null);
        callSignDraft.set("");
        feedbackText.set("Returned to the lance overview. No campaign hardware changed.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    public void selectSlot(SocketId slot) {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        if (!layout.hasSocket(slot)) return;
        selectedSlot.set(slot);
        fittingFocused.set(true);
        SocketDef def = layout.socket(slot);
        String label = def != null ? def.label() : slot.label();
        feedbackText.set(label + " selected. " + (slot == SocketId.MINI_FAB
                ? "Compatible fleet stock is ready for refit."
                : mountFor(slot) != null
                ? "Compatible owned and fabricable weapon assemblies are shown."
                : "This chassis-integrated hardware is inspection only."));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private static SocketId defaultFittingSlot(CampaignMech mech) {
        if (mech == null) return SocketId.MINI_FAB;
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        if (layout.hasSocket(SocketId.MINI_FAB)) return SocketId.MINI_FAB;
        for (SocketDef def : layout.sockets()) {
            if (!def.factoryLocked()) return def.id();
        }
        return layout.sockets().get(0).id();
    }

    private void showLanceOverview() {
        assetPickerOpen.set(false);
        fittingFocused.set(false);
        selectedMechId.set(null);
        callSignDraft.set("");
        feedbackText.set("Lance overview restored. Select an occupied gantry to refit,"
                + " or press + on a vacant pad to fabricate a chassis.");
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
        if (installed) bayChanged.run();
        revision.update(value -> value + 1);
    }

    private void fitWeapon(MechMountSlot mount, MechWeaponComponent component) {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        MechWorkshop.Result result = workshop.fitWeapon(mech.id(), mount, component);
        feedbackText.set(switch (result.status()) {
            case ALREADY_INSTALLED -> component.displayName + " is already installed.";
            case INSTALLED_FROM_STORES -> component.displayName + " installed from bay stores.";
            case FABRICATED_AND_INSTALLED -> component.displayName
                    + " fabricated from fleet cargo and installed.";
            case INCOMPATIBLE -> "Refit blocked: incompatible socket type or capacity.";
            case INSUFFICIENT_MATERIALS -> "Fabrication blocked: required fleet materials are short.";
            default -> "Refit blocked: campaign hardware was not changed.";
        });
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
    }

    public void stripWeapon(SocketId slot) {
        CampaignMech mech = selectedMech();
        MechMountSlot mount = mountFor(slot);
        if (mech == null || mount == null) return;
        MechWeaponComponent previous = mech.weaponAt(mount);
        MechWorkshop.Result result = workshop.stripWeapon(mech.id(), mount);
        feedbackText.set(switch (result.status()) {
            case STRIPPED -> (previous != null ? previous.displayName : "Hardware")
                    + " stripped and returned to cargo stores.";
            case ALREADY_STRIPPED -> "Hardpoint is already empty.";
            case INCOMPATIBLE -> "Cannot strip this hardpoint: factory locked.";
            default -> "Unequip blocked: campaign hardware was not changed.";
        });
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
    }

    private void fabricateChassis(MechVariant variant) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return;
        MechWorkshop.Result result = workshop.fabricateChassis(squad.id(), variant);
        CampaignMech fabricated = result.mech();
        if (fabricated != null) {
            selectedMechId.set(fabricated.id());
            callSignDraft.set(fabricated.displayName());
            selectedGantry.set(Math.max(0, squad.mechs().size() - 1));
            selectedSlot.set(SocketId.ARMS);
            fittingFocused.set(true);
        }
        feedbackText.set(switch (result.status()) {
            case CHASSIS_FABRICATED -> fabricated.displayName()
                    + " fabricated with its standard roll-out fit and assigned to the lance.";
            case INSUFFICIENT_MATERIALS -> "Chassis fabrication blocked: required fleet materials are short.";
            case LANCE_FULL -> "Chassis fabrication blocked: this lance has no vacant gantry.";
            default -> "Chassis fabrication blocked: campaign assets were not changed.";
        });
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
    }

    private void editCallSign(String value) {
        String draft = value != null ? value : "";
        callSignDraft.set(draft);
        if (draft.trim().isEmpty()) {
            feedbackText.set("A call sign is required; the current mech name is unchanged.");
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
        } else {
            feedbackText.set("Press SET to apply this call sign. Hardware and stores are unchanged.");
            feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
        }
    }

    private void renameSelectedMech() {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        if (!mech.rename(callSignDraft.get())) {
            feedbackText.set("Call sign must contain 1–"
                    + CampaignMech.CALLSIGN_MAX_LENGTH + " characters.");
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        callSignDraft.set(mech.displayName());
        feedbackText.set(mech.displayName() + " renamed. Chassis identity, fit, and stores were unchanged.");
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        revision.update(value -> value + 1);
    }

    private CampaignMechSquad selectedSquad() {
        revision.get();
        return bay.squadById(selectedSquadId.get());
    }

    private CampaignMech selectedMech() {
        if (!fittingFocused.get()) return null;
        CampaignMechSquad squad = selectedSquad();
        String mechId = selectedMechId.get();
        return squad != null && mechId != null ? squad.mechById(mechId) : null;
    }

    private boolean fabricatingChassis() {
        if (!fittingFocused.get() || selectedMechId.get() != null) return false;
        return mechAt(selectedSquad(), selectedGantryIndex()) == null;
    }

    private static CampaignMech mechAt(CampaignMechSquad squad, int index) {
        return squad != null && index >= 0 && index < squad.mechs().size()
                ? squad.mechs().get(index) : null;
    }

    private static String slotComponent(CampaignMech mech, SocketId slot) {
        if (mech == null) return "NO ASSET";
        return switch (slot) {
            case CORE -> mech.variant().displayName + " integrated core";
            case ARMS -> componentName(mech.arms());
            case LEFT_SHOULDER -> componentName(mech.leftShoulder());
            case RIGHT_SHOULDER -> componentName(mech.rightShoulder());
            case AMMO_RESERVE -> ammoSummary(mech);
            case MINI_FAB -> mech.missileReplenisherId().equals(
                    MissileReplenisherComponent.ACCELERATED_FEED.id())
                    ? "Accelerated feed" : "Standard replenisher";
        };
    }

    private SocketDef selectedSocketDefinition() {
        CampaignMech mech = selectedMech();
        return mech != null
                ? MechFittingLayout.forVariant(mech.variant()).socket(selectedSlot.get())
                : null;
    }

    private static String slotType(SocketDef definition) {
        return definition.type().label() + " / " + definition.gridColumns()
                + "×" + definition.gridRows() + " GRID";
    }

    private static String componentName(MechWeaponComponent component) {
        return component != null ? component.displayName : "Empty hardpoint";
    }

    private static String ammoSummary(CampaignMech mech) {
        return missileTriggers(mech) + " missile triggers ready";
    }

    private static int missileTriggers(CampaignMech mech) {
        return finiteAmmo(mech.leftShoulder()) + finiteAmmo(mech.rightShoulder());
    }

    private static int finiteAmmo(MechWeaponComponent component) {
        return component != null && component.ammoCapacity > 0 ? component.ammoCapacity : 0;
    }

    private static float maxWeaponRange(CampaignMech mech) {
        float max = 0f;
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent component = mech.weaponAt(slot);
            if (component != null) max = Math.max(max, component.weaponDef().range);
        }
        return max;
    }

    private static float maximumWeaponRange() {
        float max = 0f;
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            max = Math.max(max, component.weaponDef().range);
        }
        return max;
    }

    private static int maximumMissileTriggers() {
        int max = 0;
        for (MechWeaponComponent left : MechWeaponComponent.values()) {
            if (left.mountFamily != MechWeaponComponent.MountFamily.SHOULDER) continue;
            for (MechWeaponComponent right : MechWeaponComponent.values()) {
                if (right.mountFamily != MechWeaponComponent.MountFamily.SHOULDER) continue;
                max = Math.max(max, finiteAmmo(left) + finiteAmmo(right));
            }
        }
        return max;
    }

    private static MechMountSlot mountFor(SocketId slot) {
        return switch (slot) {
            case ARMS -> MechMountSlot.ARMS;
            case LEFT_SHOULDER -> MechMountSlot.LEFT_SHOULDER;
            case RIGHT_SHOULDER -> MechMountSlot.RIGHT_SHOULDER;
            default -> null;
        };
    }

    private static MechWeaponComponent slotWeapon(CampaignMech mech, SocketId slot) {
        MechMountSlot mount = mountFor(slot);
        return mech != null && mount != null ? mech.weaponAt(mount) : null;
    }

    private float prospectiveWeaponRange(CampaignMech mech, MechMountSlot mount, MechWeaponComponent candidate) {
        float max = 0f;
        for (MechMountSlot s : MechMountSlot.values()) {
            MechWeaponComponent w = s == mount ? candidate : mech.weaponAt(s);
            if (w != null) max = Math.max(max, w.weaponDef().range);
        }
        return max;
    }

    private int prospectiveMissileTriggers(CampaignMech mech, MechMountSlot mount, MechWeaponComponent candidate) {
        MechWeaponComponent left = mount == MechMountSlot.LEFT_SHOULDER ? candidate : mech.leftShoulder();
        MechWeaponComponent right = mount == MechMountSlot.RIGHT_SHOULDER ? candidate : mech.rightShoulder();
        return finiteAmmo(left) + finiteAmmo(right);
    }

    private static String doctrineAdvice(MechRole role) {
        return switch (role) {
            case ARMORED_SUPPORT -> "HEAVY ARMOR · POINT-DEFENSE FIRE";
            case ASSAULT -> "HIGH MOBILITY · DIRECT STRIKE BURSTS";
            case LR_SUPPORT -> "FIRE SUPPORT · GUIDED MISSILE PACKS";
            default -> "BALANCED TACTICAL LOADOUT";
        };
    }

    private List<FilterPill> buildFilterPills() {
        CategoryFilter current = catalogFilter.get();
        List<FilterPill> pills = new ArrayList<>();
        for (CategoryFilter f : CategoryFilter.values()) {
            String id = "mech-filter:" + f.name().toLowerCase(Locale.ROOT);
            String classes = f == current ? "catalog-filter-pill selected" : "catalog-filter-pill";
            pills.add(new FilterPill(id, f.label(), classes, () -> setFilter(f)));
        }
        return List.copyOf(pills);
    }

    private static PerformanceMeter meter(String suffix, String label, String value,
                                          float currentAmount, float maximum, float ghostAmount) {
        int currentPercent = maximum > 0f ? Math.round(currentAmount / maximum * 100f) : 0;
        int ghostPercent = maximum > 0f ? Math.round(ghostAmount / maximum * 100f) : 0;
        currentPercent = Math.max(0, Math.min(100, currentPercent));
        ghostPercent = Math.max(0, Math.min(100, ghostPercent));

        String ghostStyle;
        String ghostClasses;
        if (ghostPercent > currentPercent) {
            ghostStyle = "left: " + currentPercent + "%; width: " + (ghostPercent - currentPercent) + "%;";
            ghostClasses = "meter-ghost gain";
        } else if (ghostPercent < currentPercent) {
            ghostStyle = "left: " + ghostPercent + "%; width: " + (currentPercent - ghostPercent) + "%;";
            ghostClasses = "meter-ghost loss";
        } else {
            ghostStyle = "";
            ghostClasses = "meter-ghost hidden";
        }

        String base = "mech-meter:" + suffix;
        return new PerformanceMeter(base, base + ":label", base + ":value",
                base + ":track", base + ":fill", base + ":ghost", label, value,
                "width: " + currentPercent + "%;", ghostStyle, ghostClasses);
    }

    private static float maximum(VariantMetric metric) {
        float max = 0f;
        for (MechVariant variant : MechVariant.values()) max = Math.max(max, metric.value(variant));
        return max;
    }

    private static String roleLabel(MechRole role) {
        return role.displayName().toUpperCase(Locale.ROOT);
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

    public record GantryRow(String id, String stationId, String nameId, String detailId,
                            String classes, String station, String name, String detail,
                            boolean disabled, Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "stationId" -> stationId; case "nameId" -> nameId;
            case "detailId" -> detailId; case "classes" -> classes;
            case "station" -> station; case "name" -> name; case "detail" -> detail;
            case "disabled" -> disabled; case "select" -> select;
            default -> throw unknown("mech-gantry", p); }; }
    }

    public enum CategoryFilter {
        ALL("ALL"),
        BALLISTIC("BALLISTIC"),
        MISSILE("MISSILE"),
        ENERGY("ENERGY"),
        IN_STOCK("IN STOCK");

        private final String label;
        CategoryFilter(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record FilterPill(String id, String label, String classes, Runnable select)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "label" -> label; case "classes" -> classes;
            case "select" -> select; default -> throw unknown("mech-filter-pill", p);
        }; }
    }

    public record PerformanceMeter(String id, String labelId, String valueId, String trackId,
                                   String fillId, String ghostId, String label, String value,
                                   String fillStyle, String ghostStyle, String ghostClasses)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "labelId" -> labelId; case "valueId" -> valueId;
            case "trackId" -> trackId; case "fillId" -> fillId; case "ghostId" -> ghostId;
            case "label" -> label; case "value" -> value; case "fillStyle" -> fillStyle;
            case "ghostStyle" -> ghostStyle; case "ghostClasses" -> ghostClasses;
            default -> throw unknown("mech-meter", p); }; }
    }

    public record SlotRow(String id, String contentId, String buttonId, String stripId, String copyId,
                          String headerId, String nameId,
                          String badgeId, String componentId, String telemetryId,
                          String typeId, String gridId,
                          SocketId socketId,
                          String classes, String name, String badge, String badgeClasses,
                          String component, String telemetry, String type,
                          boolean canStrip, String stripClasses,
                          Runnable select, Runnable stripAction) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "contentId" -> contentId; case "buttonId" -> buttonId; case "stripId" -> stripId;
            case "copyId" -> copyId; case "headerId" -> headerId; case "nameId" -> nameId; case "badgeId" -> badgeId;
            case "componentId" -> componentId; case "telemetryId" -> telemetryId;
            case "gridId" -> gridId; case "typeId" -> typeId; case "classes" -> classes;
            case "name" -> name; case "badge" -> badge; case "badgeClasses" -> badgeClasses;
            case "component" -> component; case "telemetry" -> telemetry; case "type" -> type;
            case "canStrip" -> canStrip; case "stripClasses" -> stripClasses;
            case "stripDisabled" -> !canStrip;
            case "select" -> select; case "stripAction" -> stripAction;
            default -> throw unknown("mech-slot", p); }; }
    }

    public record CatalogRow(String id, String copyId, String headerId, String nameId, String badgeId,
                             String stockId, String telemetryId, String deltaId,
                             String detailId, String actionId, String materialsId, String bodyId,
                             String previewId, String previewClasses,
                             MechVariant chassisPreview,
                             MechWeaponComponent weaponPreview,
                             MissileReplenisherComponent replenisherPreview,
                             String classes, String name, String badge, String badgeClasses,
                             String stock, String telemetry, String delta, String deltaClasses,
                             String detail, String actionLabel,
                             boolean actionDisabled, List<MaterialRow> materials,
                             String materialsClasses,
                             Runnable action)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "copyId" -> copyId; case "headerId" -> headerId; case "nameId" -> nameId;
            case "badgeId" -> badgeId;
            case "stockId" -> stockId; case "telemetryId" -> telemetryId;
            case "deltaId" -> deltaId; case "detailId" -> detailId; case "actionId" -> actionId;
            case "materialsId" -> materialsId; case "bodyId" -> bodyId;
            case "previewId" -> previewId;
            case "previewClasses" -> previewClasses;
            case "classes" -> classes; case "name" -> name;
            case "badge" -> badge; case "badgeClasses" -> badgeClasses;
            case "stock" -> stock; case "telemetry" -> telemetry;
            case "delta" -> delta; case "deltaClasses" -> deltaClasses;
            case "detail" -> detail; case "actionLabel" -> actionLabel;
            case "actionDisabled" -> actionDisabled; case "materials" -> materials;
            case "materialsClasses" -> materialsClasses;
            case "action" -> action;
            default -> throw unknown("mech-catalog", p); }; }
    }

    public record MaterialRow(String id, String iconId, String labelId,
                              String classes, String icon, String label)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "iconId" -> iconId; case "labelId" -> labelId;
            case "classes" -> classes; case "icon" -> icon; case "label" -> label;
            default -> throw unknown("mech-material", p); }; }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
