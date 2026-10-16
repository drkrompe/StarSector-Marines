package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.CompanyResources.Row;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
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
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MechWorkshop;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Spatial doll projection and command surface for the campaign-authoritative {@link MechBay}. */
public final class MechLabViewModel {

    private final Reactor reactor;
    private final MechBay bay;
    private final FabricationResources resources;
    private final MechWorkshop workshop;
    private final Runnable bayChanged;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<Integer> dismissedLesson;
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
    /** A catalog internal under the pointer, previewed on the meters and the budget. */
    private final MutableSignal<MechInternal> hoveredInternal;
    /** Plating has no socket; while true the catalog lists plating instead of a socket. */
    private final MutableSignal<Boolean> platingSelected;
    private final ComputedSignal<String> budgetText;
    private final ComputedSignal<String> budgetClasses;
    private final ComputedSignal<String> platingText;
    private final ComputedSignal<String> platingClasses;
    private final ComputedSignal<List<FilterPill>> categoryFilterPills;
    private final ComputedSignal<String> categoryFilterClasses;
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
    private final ComputedSignal<List<Row>> materialStock;
    private final ComputedSignal<String> pickerClasses;
    private final ComputedSignal<String> workspaceClasses;
    private final ComputedSignal<String> fittingHeaderClasses;
    private final ComputedSignal<String> performanceClasses;
    private final ComputedSignal<String> catalogClasses;
    private final ComputedSignal<String> slotRackClasses;
    private final ComputedSignal<String> overviewRailClasses;
    private final ComputedSignal<String> garageTitle;

    private int physicalGantries = CampaignMechSquad.CAPACITY;

    public void physicalGantries(int count) {
        physicalGantries = Math.max(0, Math.min(CampaignMechSquad.CAPACITY, count));
        revision.update(value -> value + 1);
    }

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
        this.reactor = reactor;
        this.bay = bay;
        this.resources = resources;
        workshop = new MechWorkshop(bay, resources, () -> physicalGantries);
        this.bayChanged = bayChanged;
        CampaignMechSquad initialSquad = bay.activeSquad();
        revision = reactor.signal(0);
        dismissedLesson = reactor.signal(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedMechId = reactor.signal(null);
        selectedGantry = reactor.signal(0);
        selectedSlot = reactor.signal(SocketId.MINI_FAB);
        fittingFocused = reactor.signal(false);
        assetPickerOpen = reactor.signal(false);
        callSignDraft = reactor.signal("");
        feedbackText = reactor.signal(
                Strings.get("openingMechLabSelectAnOccupiedGantryToRefitOr"));
        feedbackClasses = reactor.signal("mech-lab-feedback tone-muted surface-dark");
        catalogFilter = reactor.signal(CategoryFilter.ALL);
        hoveredWeapon = reactor.signal(null);
        hoveredInternal = reactor.signal(null);
        platingSelected = reactor.signal(false);
        categoryFilterPills = reactor.computed(this::buildFilterPills);
        categoryFilterClasses = reactor.computed(() -> categoryFilterPills.get().isEmpty()
                ? "category-filters hidden" : "category-filters");
        labSummary = reactor.computed(this::buildLabSummary);
        squadRows = reactor.computed(this::buildSquadRows);
        mechRows = reactor.computed(this::buildMechRows);
        gantryRows = reactor.computed(this::buildGantryRows);
        activeGantryLabel = reactor.computed(() -> {
            int index = selectedGantryIndex();
            CampaignMech mech = selectedMech() != null ? selectedMech() : mechAt(selectedSquad(), index);
            return String.format(Locale.ROOT, Strings.get("openingMechLabGantryDDS"), index + 1,
                    physicalGantries, mech != null ? "" : Strings.get("openingMechLabVacant"));
        });
        selectedMechName = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.displayName() : fabricatingChassis()
                    ? String.format(Locale.ROOT, Strings.get("openingMechLabVacantGantryD"), selectedGantryIndex() + 1)
                    : Strings.get("openingMechLabNoAssetSelected");
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
            String normalized = CampaignMech.normalizeCallSign(candidate);
            return normalized.isEmpty()
                    || normalized.length() > CampaignMech.CALLSIGN_MAX_LENGTH
                    || normalized.equals(mech.displayName());
        });
        selectedMechIdentity = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? Strings.format("openingMechLabChassisWalkerHeavyAsset", mech.variant().displayName())
                    : fabricatingChassis()
                    ? Strings.get("openingMechLabSelectADiscoveredChassisPatternAndCommit")
                    : Strings.get("openingMechLabSelectAnAssignedHeavyAssetFromThe");
        });
        selectedMechDoctrine = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return fabricatingChassis() ? Strings.get("openingMechLabChassisForge") : Strings.get("openingMechLabDoctrineUnavailable");
            return roleLabel(mech.role()) + "  ·  " + doctrineAdvice(mech.role());
        });
        performanceMeters = reactor.computed(this::buildPerformanceMeters);
        budgetText = reactor.computed(this::buildBudgetText);
        budgetClasses = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return "label heading mech-budget hidden";
            MechFrameBudget.Fit candidate = candidateFit(mech);
            boolean over = mech.fit().overBudget() || candidate != null && candidate.overBudget();
            return over ? "label heading mech-budget tone-danger" : "label heading mech-budget tone-edge";
        });
        platingText = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? Strings.format("openingMechLabPlatingRow",
                    mech.internal(MechInternal.Role.PLATING).displayName()) : "";
        });
        platingClasses = reactor.computed(() -> selectedMech() == null ? "plating-select hidden"
                : platingSelected.get() ? "plating-select console-action selected"
                : "plating-select console-action");
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
            if (fabricatingChassis()) return Strings.get("openingMechLabChassisPatterns");
            CampaignMech mech = selectedMech();
            if (mech != null && platingSelected.get()) return Strings.get("openingMechLabFramePlating");
            if (mech != null) {
                SocketDef def = MechFittingLayout.forVariant(mech.variant()).socket(selectedSlot.get());
                if (def != null) return def.label();
            }
            return selectedSlot.get().label();
        });
        selectedSlotCopy = reactor.computed(this::buildSelectedSlotCopy);
        selectedSlotRule = reactor.computed(this::buildSelectedSlotRule);
        catalogRows = reactor.computed(this::buildCatalogRows);
        materialStock = reactor.computed(this::buildMaterialStock);
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
                ? activeGantryLabel.get() : Strings.get("openingMechLabActiveLanceAllGantries"));
    }

    public void resumeCampaignLesson() {
        if (bay.labLesson() <= 0 || bay.labLesson() >= 5) return;
        bay.pauseLabLesson(false);
        dismissedLesson.set(0);
        if (bay.labLesson() > 1 && bay.mechById(WaylineHound.ID) != null) {
            for (var squad : bay.squads()) if (squad.mechById(WaylineHound.ID) != null) {
                selectSquad(squad.id()); selectMech(WaylineHound.ID);
                selectSlot(SocketId.LEFT_SHOULDER); break;
            }
        }
        revision.update(value -> value + 1);
    }

    PreparationGuide.Lesson campaignLesson() {
        int step = bay.labLesson();
        if (step <= 0 || step >= 5 || bay.mechById(WaylineHound.ID) == null) return null;
        return CampaignLessons.lesson(step - 1, "lessonGantryTitle", "lessonGantry" + step,
                step == 1 ? List.of("mech-overview-gantries", "mech-overview-browse")
                        : List.of("mech-doll-canvas", "mech-slot-rack", "mech-component-catalog"),
                step == 1 ? "lessonOpenWayward" : step == 4 ? "lessonReady" : null,
                step == 1 || step == 4 ? this::guideLabLesson : null);
    }

    public void putTutorialProps(Map<String, Object> props) {
        props.put("lessonClasses", reactor.computed(() -> {
            revision.get();
            return labGuideVisible() ? "lesson-backdrop" : "lesson-backdrop-hidden";
        }));
        props.put("lessonTitle", reactor.computed(() -> { revision.get(); return LessonCopy.text("lessonGantryTitle"); }));
        props.put("lessonCopy", reactor.computed(() -> { revision.get(); return LessonCopy.text("lessonGantry" + Math.min(4, Math.max(1, bay.labLesson()))); }));
        props.put("lessonActionLabel", reactor.computed(() -> {
            revision.get();
            return LessonCopy.text(bay.labLesson() == 1 ? "lessonOpenWayward" : "lessonReady");
        }));
        props.put("lessonAction", (Runnable) this::guideLabLesson);
        props.put("lessonPause", (Runnable) () -> { bay.pauseLabLesson(true); revision.update(value -> value + 1); });
        props.put("lessonResumeClasses", reactor.computed(() -> {
            revision.get();
            return bay.labLesson() > 0 && bay.labLesson() < 5 && !labGuideVisible()
                    ? "compact-action console-action" : "hidden";
        }));
        props.put("lessonResume", (Runnable) () -> { bay.pauseLabLesson(false); dismissedLesson.set(0); revision.update(value -> value + 1); });
    }

    public boolean labGuideVisible() {
        revision.get();
        return bay.labLesson() > 0 && bay.labLesson() < 5 && !bay.labLessonPaused()
                && dismissedLesson.get() != bay.labLesson();
    }

    public void dismissLabPrompt() { dismissedLesson.set(bay.labLesson()); }

    public void guideLabLesson() {
        if (bay.labLesson() == 4) { bay.advanceLabLesson(); revision.update(value -> value + 1); return; }
        for (CampaignMechSquad squad : bay.squads()) {
            if (squad.mechById(WaylineHound.ID) == null) continue;
            selectSquad(squad.id());
            selectMech(WaylineHound.ID);
            if (bay.labLesson() == 1) bay.advanceLabLesson();
            else dismissLabPrompt();
            selectSlot(SocketId.LEFT_SHOULDER);
            revision.update(value -> value + 1);
            return;
        }
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
    public Signal<String> categoryFilterClasses() { return categoryFilterClasses; }
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
    public void hoverInternal(MechInternal internal) {
        if (hoveredInternal.get() == internal) return;
        hoveredInternal.set(internal);
    }
    public void clearHoverInternal() {
        if (hoveredInternal.get() == null) return;
        hoveredInternal.set(null);
    }
    public MechInternal hoveredInternal() { return hoveredInternal.get(); }
    /** True while any catalog candidate, weapon or internal, is previewed. */
    public boolean hasHoverCandidate() {
        return hoveredWeapon.get() != null || hoveredInternal.get() != null;
    }
    public void clearHoverCandidates() {
        clearHoverWeapon();
        clearHoverInternal();
    }
    public Signal<String> budgetText() { return budgetText; }
    public Signal<String> budgetClasses() { return budgetClasses; }
    public Signal<String> platingText() { return platingText; }
    public Signal<String> platingClasses() { return platingClasses; }
    public Runnable selectPlatingAction() { return this::selectPlating; }
    public boolean platingSelected() { return platingSelected.get(); }

    /** Fitting-budget and plating bindings, shared by the screen and its evidence. */
    public void putInternalProps(Map<String, Object> props) {
        props.put("budgetText", budgetText);
        props.put("budgetClasses", budgetClasses);
        props.put("platingText", platingText);
        props.put("platingClasses", platingClasses);
        props.put("selectPlating", selectPlatingAction());
    }

    /** The selected mech's current fit, or null without one. */
    public MechFrameBudget.Fit selectedFit() {
        CampaignMech mech = selectedMech();
        return mech != null ? mech.fit() : null;
    }

    /** Heat reservoir a weapon sheet reads against: the selected mech's, or the stock one. */
    public float selectedHeatCapacity() {
        MechFrameBudget.Fit fit = selectedFit();
        return fit != null ? fit.heatCapacity() : MechInternal.FACTORY_HEAT_SINK.heatCapacity;
    }

    /** Cooling a weapon sheet reads against: the selected mech's, or the stock frame's. */
    public float selectedCoolingPerSecond() {
        MechFrameBudget.Fit fit = selectedFit();
        return fit != null ? fit.coolingPerSecond() : MechInternal.FACTORY_HEAT_SINK.cooling;
    }

    /**
     * The internal shown in one socket well: the engine, the heat sink, or the cooler
     * holding the utility socket. Null for weapon mounts and an empty utility socket.
     */
    public MechInternal installedInternal(SocketId id) {
        CampaignMech mech = selectedMech();
        MechInternal.Role role = id != null ? roleFor(id) : null;
        return mech != null && role != null ? mech.internal(role) : null;
    }
    public Runnable selectSlotAction(SocketId slot) { return () -> selectSlot(slot); }
    public Runnable stripWeaponAction(SocketId slot) { return () -> stripWeapon(slot); }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

    /** Current authored fitting socket; shared with the physical room overlay. */
    public SocketId selectedSocket() { return platingSelected.get() ? null : selectedSlot.get(); }

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

    /** Selects a socket directly from the physical fitting doll. */
    public void selectSocket(SocketId id) {
        if (id != null) selectSlot(id);
    }

    /** Compatibility and availability used by the drag hover preview. */
    public boolean canDropCatalogWeapon(MechWeaponComponent component, SocketId target) {
        CampaignMech mech = selectedMech();
        MechMountSlot mount = mountFor(target);
        if (mech == null || component == null || mount == null
                || !bay.canInstallWeapon(mech.id(), mount, component.id)) return false;
        if (mech.weaponAt(mount) != component
                && !bay.withinBudget(mech.id(), mech.fit().withWeapon(mount, component))) return false;
        if (mech.weaponAt(mount) == component || bay.availableWeapon(component.id) > 0) {
            return true;
        }
        MechFabricationCatalog.Recipe recipe = MechFabricationCatalog.weapon(component);
        return bay.canFabricate(component) && recipe != null && resources.canAfford(resources.quote(recipe.cost()));
    }

    /** Installed assemblies can move only to vacant, compatible hardpoints. */
    public boolean canMoveInstalledWeapon(SocketId source, SocketId target) {
        CampaignMech mech = selectedMech();
        MechMountSlot sourceMount = mountFor(source);
        MechMountSlot targetMount = mountFor(target);
        if (mech == null || sourceMount == null || targetMount == null
                || sourceMount == targetMount || mech.weaponAt(sourceMount) == null
                || mech.weaponAt(targetMount) != null) return false;
        return bay.canInstallWeapon(mech.id(), sourceMount, mech.weaponAt(sourceMount).id)
                && bay.canInstallWeapon(mech.id(), targetMount, mech.weaponAt(sourceMount).id);
    }

    public boolean dropCatalogWeapon(MechWeaponComponent component, SocketId target) {
        CampaignMech mech = selectedMech();
        MechMountSlot mount = mountFor(target);
        if (mech == null || component == null || mount == null) return false;
        selectedSlot.set(target);
        platingSelected.set(false);
        MechFrameBudget.Fit proposed = mech.fit().withWeapon(mount, component);
        return applyWeaponResult(workshop.fitWeapon(mech.id(), mount, component), component, proposed);
    }

    public boolean moveInstalledWeapon(SocketId source, SocketId target) {
        CampaignMech mech = selectedMech();
        MechMountSlot sourceMount = mountFor(source);
        MechMountSlot targetMount = mountFor(target);
        if (mech == null || sourceMount == null || targetMount == null) return false;
        MechWeaponComponent component = mech.weaponAt(sourceMount);
        MechWorkshop.Result result = workshop.moveWeapon(mech.id(), sourceMount, targetMount);
        if (result.succeeded()) selectedSlot.set(target);
        feedbackText.set(switch (result.status()) {
            case MOVED -> Strings.format("openingMechLabMovedTo", component.displayName(), target.label());
            case TARGET_OCCUPIED -> Strings.get("openingMechLabMoveBlockedTheTargetHardpointIsOccupied");
            case INCOMPATIBLE -> Strings.get("openingMechLabMoveBlockedIncompatibleSocketTypeOrCapacity");
            default -> Strings.get("openingMechLabMoveCancelledCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
        return result.succeeded();
    }

    public boolean removeInstalledWeapon(SocketId source) {
        CampaignMech mech = selectedMech();
        MechMountSlot mount = mountFor(source);
        if (mech == null || mount == null || mech.weaponAt(mount) == null) return false;
        MechWeaponComponent component = mech.weaponAt(mount);
        MechWorkshop.Result result = workshop.stripWeapon(mech.id(), mount);
        feedbackText.set(result.succeeded()
                ? Strings.format("openingMechLabReturnedToBayStoresIsEmpty", component.displayName(), source.label())
                : Strings.get("openingMechLabRemovalCancelledCampaignHardwareWasNotChanged"));
        if (result.succeeded()) selectedSlot.set(source);
        finishWeaponCommand(result);
        return result.succeeded();
    }

    public void cancelWeaponDrag() {
        feedbackText.set(Strings.get("openingMechLabRefitDragCancelledCampaignHardwareWasNot"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    /** Current lance in authored gantry order for the embedded garage scene. */
    public List<MechVariant> gantryVariants() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        if (physicalGantries == 1 && selectedMech() != null) return List.of(selectedMech().variant());
        return squad.mechs().stream().limit(physicalGantries).map(CampaignMech::variant).toList();
    }

    /** Frozen fitting values used to keep the physical gantries visually honest. */
    public List<MechDeploymentSpec> gantryDeployments() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        if (physicalGantries == 1 && selectedMech() != null)
            return List.of(selectedMech().freezeForDeployment());
        return squad.mechs().stream().limit(physicalGantries).map(CampaignMech::freezeForDeployment).toList();
    }

    /** Selected vehicle's stable camera target within the current lance. */
    public int selectedGantryIndex() {
        revision.get();
        return Math.max(0, Math.min(physicalGantries - 1,
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
        return Strings.format("openingMechLabHeavyAssetsAssignedLoadoutFreezesAtMission", bay.squads().size() + (bay.squads().size() == 1
                ? Strings.get("openingMechLabSupportLance") : Strings.get("openingMechLabSupportLances")), chassis, CampaignMechSquad.CAPACITY);
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
                    squad.displayName(), activeSupport ? Strings.get("openingMechLabActiveSupport") : Strings.get("openingMechLabAvailable"),
                    Strings.format("openingMechLabAssets", squad.mechs().size(), CampaignMechSquad.CAPACITY),
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
                    mech.displayName(), mech.variant().displayName() + "  ·  " + roleLabel(mech.role()),
                    mech.missileReplenisher().displayName(), () -> selectMech(mech.id())));
        }
        return List.copyOf(rows);
    }

    private List<GantryRow> buildGantryRows() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        List<GantryRow> rows = new ArrayList<>();
        for (int index = 0; index < physicalGantries; index++) {
            CampaignMech mech = mechAt(squad, index);
            String base = "overview-gantry:" + index;
            int gantry = index;
            rows.add(new GantryRow(base, base + ":station", base + ":name",
                    base + ":detail", mech != null ? "overview-gantry occupied"
                            : "overview-gantry vacant",
                    String.format(Locale.ROOT, Strings.get("openingMechLabGantryD"), index + 1),
                    mech != null ? mech.displayName() : Strings.get("openingMechLabVacant2"),
                    mech != null ? mech.variant().displayName() + "  ·  " + roleLabel(mech.role())
                            : Strings.get("openingMechLabPressOnPadToFabricate"),
                    mech == null, () -> selectGantry(gantry)));
        }
        return List.copyOf(rows);
    }

    private List<PerformanceMeter> buildPerformanceMeters() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return List.of();
        MechFrameBudget.Fit fit = mech.fit();
        float currentRange = maxWeaponRange(mech);
        int currentMissiles = missileTriggers(mech);

        MechWeaponComponent candidate = hoveredWeapon.get();
        MechMountSlot mount = platingSelected.get() ? null : mountFor(selectedSlot.get());
        float ghostRange = currentRange;
        int ghostMissiles = currentMissiles;
        if (candidate != null && mount != null) {
            ghostRange = prospectiveWeaponRange(mech, mount, candidate);
            ghostMissiles = prospectiveMissileTriggers(mech, mount, candidate);
        }
        MechFrameBudget.Fit ghost = candidateFit(mech);
        if (ghost == null) ghost = fit;

        return List.of(
                meter("armor", Strings.get("openingMechLabArmor"), Strings.format("openingMechLabPlate", Math.round(fit.armorCapacity())),
                        fit.armorCapacity(), maximum(MechFrameBudget::refitArmorCapacity), ghost.armorCapacity()),
                meter("mobility", Strings.get("openingMechLabMobility"), Strings.format("openingMechLabCellsS", number(fit.moveSpeed())),
                        fit.moveSpeed(), maximum(MechFrameBudget::refitMoveSpeed), ghost.moveSpeed()),
                meter("heat", Strings.get("openingMechLabHeatSink"), Strings.format("openingMechLabHeatValue", number(fit.heatCapacity())),
                        fit.heatCapacity(), maximumHeatCapacity(), ghost.heatCapacity()),
                meter("cooling", Strings.get("openingMechLabCooling"), Strings.format("openingMechLabCoolingValue", number(fit.coolingPerSecond())),
                        fit.coolingPerSecond(), maximumCooling(), ghost.coolingPerSecond()),
                meter("range", Strings.get("openingMechLabMaxRange"), Strings.format("openingMechLabCells", number(currentRange)),
                        currentRange, maximumWeaponRange(), ghostRange),
                meter("endurance", Strings.get("openingMechLabMissiles"), Strings.format("openingMechLabTriggers", currentMissiles),
                        currentMissiles, maximumMissileTriggers(), ghostMissiles));
    }

    /** The fit a hovered catalog candidate would produce, or null when nothing is hovered. */
    private MechFrameBudget.Fit candidateFit(CampaignMech mech) {
        MechInternal internal = hoveredInternal.get();
        if (internal != null) return mech.fit().withInternal(internal.role, internal);
        MechWeaponComponent weapon = hoveredWeapon.get();
        MechMountSlot mount = platingSelected.get() ? null : mountFor(selectedSlot.get());
        return weapon != null && mount != null ? mech.fit().withWeapon(mount, weapon) : null;
    }

    private String buildBudgetText() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return "";
        MechFrameBudget.Fit fit = mech.fit();
        MechFrameBudget.Fit candidate = candidateFit(mech);
        if (candidate != null && (Math.abs(candidate.fittedMass() - fit.fittedMass()) > 0.01f
                || Math.abs(candidate.output() - fit.output()) > 0.01f)) {
            return Strings.format("openingMechLabMassBudgetPreview", number(fit.fittedMass()),
                    number(fit.output()), number(candidate.fittedMass()), number(candidate.output()));
        }
        return Strings.format(fit.overBudget() ? "openingMechLabMassBudgetOver" : "openingMechLabMassBudget",
                number(fit.fittedMass()), number(fit.output()));
    }

    private static float maximumHeatCapacity() {
        float max = 0f;
        for (MechInternal internal : MechInternal.values()) {
            if (internal.role == MechInternal.Role.HEAT_SINK) max = Math.max(max, internal.heatCapacity);
        }
        return max;
    }

    /** Best heat sink and cooler, with the engine that runs coolest. */
    private static float maximumCooling() {
        float sink = 0f;
        float cooler = 0f;
        float engine = Float.NEGATIVE_INFINITY;
        for (MechInternal internal : MechInternal.values()) {
            switch (internal.role) {
                case HEAT_SINK -> sink = Math.max(sink, internal.cooling);
                case COOLER -> cooler = Math.max(cooler, internal.cooling);
                case ENGINE -> engine = Math.max(engine, internal.cooling);
                default -> { }
            }
        }
        return sink + cooler + Math.max(0f, engine);
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
            MechInternal internal = roleFor(slot) != null ? mech.internal(roleFor(slot)) : null;
            boolean internalSocket = roleFor(slot) != null;
            boolean armed = weapon != null || internalSocket;
            String badge = locked ? Strings.get("openingMechLabLocked") : internalSocket ? Strings.get("openingMechLabFitted") : armed ? Strings.get("openingMechLabArmed") : Strings.get("openingMechLabEmpty");
            String badgeClasses = locked ? "slot-badge locked" : armed ? "slot-badge armed" : "slot-badge empty";

            String telemetry;
            if (weapon != null) {
                telemetry = Strings.format("openingMechLabDmgRng", number(weapon.weaponDef().damage), number(weapon.weaponDef().range), (weapon.ammoCapacity > 0 ? Strings.format("openingMechLabAmmo", weapon.ammoCapacity) : ""));
            } else if (internal != null) {
                telemetry = internalTelemetry(mech.fit(), internal);
            } else if (slot == SocketId.MINI_FAB) {
                telemetry = Strings.format("openingMechLabSrmSLrmS", number(mech.missileReplenisher().srmReplenishmentSeconds()), number(mech.missileReplenisher().lrmReplenishmentSeconds()));
            } else {
                telemetry = Strings.get("openingMechLabUnfittedHardpoint");
            }

            boolean canStrip = !locked && (weapon != null
                    || slot == SocketId.MINI_FAB && internal != null);
            String stripClasses = canStrip ? "slot-strip-action" : "slot-strip-action hidden";

            String base = "mech-slot:" + slot.name().toLowerCase(Locale.ROOT);
            rows.add(new SlotRow(base, base + ":content", base + ":select", base + ":strip",
                    base + ":copy", base + ":header", base + ":name",
                    base + ":badge", base + ":component", base + ":telemetry", base + ":type", base + ":grid",
                    slot,
                    slot == selectedSlot.get() && !platingSelected.get() ? "doll-slot selected" : "doll-slot",
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
                ? Strings.get("openingMechLabFabricateACompleteStandardFitWalkerHere")
                : Strings.get("openingMechLabNoHeavyAssetSelected");
        if (platingSelected.get()) return Strings.get("openingMechLabPlatingCopy");
        return switch (selectedSlot.get()) {
            case MINI_FAB -> Strings.get("openingMechLabUtilityCopy");
            case CORE -> Strings.get("openingMechLabEngineCopy");
            case AMMO_RESERVE -> Strings.get("openingMechLabHeatSinkCopy");
            case ARMS, LEFT_SHOULDER, RIGHT_SHOULDER ->
                    slotComponent(mech, selectedSlot.get()).equals(Strings.get("openingMechLabEmptyHardpoint"))
                            ? Strings.get("openingMechLabEmptyMountFitCompatibleMissileHardwareHere")
                            : Strings.get("openingMechLabInstalledWeaponAssemblySocketTypeAndCapacity");
        };
    }

    private String buildSelectedSlotRule() {
        if (fabricatingChassis()) {
            return Strings.get("openingMechLabPlayerCargoStandardFitVacantGantry");
        }
        if (selectedMech() != null && platingSelected.get()) return Strings.get("openingMechLabPlatingRule");
        SocketDef definition = selectedSocketDefinition();
        if (definition == null) return Strings.get("openingMechLabNoSocketDefinition");
        String authority = definition.factoryLocked()
                ? Strings.get("openingMechLabLocked") : Strings.get("openingMechLabOpen");
        return Strings.format("openingMechLabSocketGrid", definition.type().label(), definition.gridColumns(), definition.gridRows(), authority);
    }

    private List<CatalogRow> buildCatalogRows() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (fabricatingChassis()) return chassisCatalog();
        if (mech != null && platingSelected.get()) return internalCatalog(mech, MechInternal.Role.PLATING);
        if (selectedSlot.get() == SocketId.MINI_FAB) {
            List<CatalogRow> rows = new ArrayList<>(replenisherCatalog(mech));
            if (mech != null) rows.addAll(internalCatalog(mech, MechInternal.Role.COOLER));
            return List.copyOf(rows);
        }
        MechInternal.Role role = roleFor(selectedSlot.get());
        if (role != null && mech != null) return internalCatalog(mech, role);
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
                "catalog-preview hidden", null, null, null, null, "catalog-row selected",
                slotComponent(mech, selectedSlot.get()),
                occupied ? Strings.get("openingMechLabInstalled") : Strings.get("openingMechLabEmpty"),
                occupied ? "catalog-badge in-cargo" : "catalog-badge fabrication",
                occupied ? Strings.get("openingMechLabInstalledAssembly") : Strings.get("openingMechLabEmptySocket"),
                "", "", "catalog-delta hidden",
                buildSelectedSlotRule(), definition != null && definition.factoryLocked()
                        ? Strings.get("openingMechLabFactoryLocked") : Strings.get("openingMechLabNoCompatibleStock"),
                definition != null && definition.factoryLocked()
                        ? Strings.get("openingMechLabIntegralChassisEquipmentCannotBeReplaced")
                        : Strings.get("openingMechLabNoCompatibleFittingIsAvailableForThis"), true, List.of(), "material-costs hidden", () -> { }));
    }

    private List<CatalogRow> weaponCatalog(CampaignMech mech, MechMountSlot mount) {
        List<CatalogRow> rows = new ArrayList<>();
        CategoryFilter filter = catalogFilter.get();
        MechWeaponComponent current = slotWeapon(mech, selectedSlot.get());

        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.weapons()) {
            MechWeaponComponent component = recipe.component();
            if (!bay.canFabricate(component) && bay.ownedWeapon(component.id) == 0) continue;
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
            boolean affordable = resources.canAfford(resources.quote(recipe.cost()));
            boolean locked = !bay.canFabricate(component);
            MechFrameBudget.Fit proposed = mech.fit().withWeapon(mount, component);
            boolean withinBudget = installed || bay.withinBudget(mech.id(), proposed);
            boolean disabled = installed || !fits || !withinBudget || fabricate && (locked || !affordable);

            String badge = installed ? Strings.get("openingMechLabFitted") : inCargo ? Strings.get("openingMechLabInCargo") : Strings.get("openingMechLabFabrication");
            String badgeClasses = installed || inCargo ? "catalog-badge in-cargo" : "catalog-badge fabrication";

            // Telemetry
            WeaponDef def = component.weaponDef();
            String telemetry = Strings.format("openingMechLabDmgRng", number(def.damage), number(def.range), (component.ammoCapacity > 0 ? Strings.format("openingMechLabAmmo", component.ammoCapacity) : Strings.get("openingMechLabAmmo2")));

            // Deltas
            String deltaText = "";
            String deltaClasses = "catalog-delta hidden";
            if (current != null && current != component) {
                float deltaRng = def.range - current.weaponDef().range;
                float deltaDmg = def.damage - current.weaponDef().damage;
                StringBuilder sb = new StringBuilder();
                if (Math.abs(deltaRng) > 0.01f) {
                    sb.append(Strings.get("openingMechLabRng")).append(deltaRng > 0 ? "+" : "").append(number(deltaRng));
                }
                if (Math.abs(deltaDmg) > 0.01f) {
                    if (!sb.isEmpty()) sb.append("  ·  ");
                    sb.append(Strings.get("openingMechLabDmg")).append(deltaDmg > 0 ? "+" : "").append(number(deltaDmg));
                }
                if (!sb.isEmpty()) {
                    deltaText = sb.toString();
                    boolean positive = deltaRng >= 0 && deltaDmg >= 0;
                    boolean negative = deltaRng <= 0 && deltaDmg <= 0;
                    deltaClasses = positive ? "catalog-delta positive" : negative ? "catalog-delta negative" : "catalog-delta neutral";
                }
            }

            String actionLabel = installed ? Strings.get("openingMechLabInstalled") : !fits ? Strings.get("openingMechLabTooLarge")
                    : !withinBudget ? Strings.get("openingMechLabOverBudget")
                    : inCargo ? Strings.get("openingMechLabInstallSpare") : locked ? Strings.get("openingMechLabPatternLocked") : !affordable ? Strings.get("openingMechLabMaterialsShort") : Strings.get("openingMechLabFabricateInstall");

            String base = "mech-catalog:" + component.id;
            boolean showMaterials = !inCargo && !installed && fits && !locked;
            List<Row> materials = showMaterials ? materialRows(base, resources.quote(recipe.cost())) : List.of();
            String materialsClasses = showMaterials ? "material-costs" : "material-costs hidden";

            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview", null, component, null, null,
                    installed ? "catalog-row selected" : !showMaterials ? "catalog-row"
                            : "catalog-row requires-fabrication", component.displayName(),
                    badge, badgeClasses,
                    Strings.format("openingMechLabOwnFree", recipe.provenance(), owned, free),
                    telemetry, deltaText, deltaClasses,
                    Strings.format("openingMechLabGrid", component.hardpointType, component.footprintColumns, component.footprintRows),
                    actionLabel,
                    installed ? Strings.get("openingMechLabAlreadyInstalledInThisMount")
                            : !fits ? Strings.get("openingMechLabThisAssemblyExceedsTheSelectedFittingGrid")
                            : !withinBudget ? overBudgetHint(proposed)
                            : !inCargo && locked ? Strings.get("openingMechLabOwnedHardwareCanBeRefittedItsFabrication")
                            : !inCargo && !affordable ? Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor")
                            : inCargo ? Strings.get("openingMechLabInstallASpareReturnTheCurrentAssembly")
                            : Strings.get("openingMechLabBuildAndInstallThisAssemblyUsingThe"),
                    disabled, materials, materialsClasses,
                    () -> fitWeapon(mount, component)));
        }
        return List.copyOf(rows);
    }

    private List<CatalogRow> chassisCatalog() {
        CampaignMechSquad squad = selectedSquad();
        boolean hasSpace = squad != null && squad.mechs().size() < physicalGantries && bay.canAddMech(squad.id());
        List<CatalogRow> rows = new ArrayList<>();
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.chassis()) {
            if (!bay.canFabricate(recipe.variant())) continue;
            boolean affordable = resources.canAfford(resources.quote(recipe.cost()));
            MechVariant variant = recipe.variant();
            String base = "mech-catalog:" + recipe.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview", variant, null, null, null, "catalog-row chassis-pattern",
                    recipe.displayName(), Strings.get("openingMechLabChassis"), "catalog-badge chassis",
                    recipe.provenance(),
                    Strings.format("openingMechLabSpeedArmor", number(variant.moveSpeed), Math.round(variant.armorCapacity)),
                    "", "catalog-delta hidden",
                    roleLabel(variant.defaultRole),
                    !hasSpace ? Strings.get("openingMechLabLanceFull") : !affordable ? Strings.get("openingMechLabMaterialsShort") : Strings.get("openingMechLabFabricateChassis"),
                    !hasSpace ? Strings.get("openingMechLabThisWorkshopHasNoVacantGantry")
                            : !affordable ? Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor")
                            : Strings.get("openingMechLabBuildThisChassisIntoTheVacantGantry"),
                    !hasSpace || !affordable, materialRows(base, resources.quote(recipe.cost())), "material-costs",
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
            // A faster loader takes the utility socket, so a fitted cooler bars it.
            boolean socketTaken = mech != null && !installed
                    && mech.internal(MechInternal.Role.COOLER) != null
                    && !MissileReplenisherComponent.STANDARD.id().equals(component.id());
            boolean disabled = mech == null || installed || free <= 0 || socketTaken;
            boolean inCargo = free > 0;
            String base = "mech-catalog:" + component.id();
            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview", null, null, component, null,
                    installed ? "catalog-row selected" : "catalog-row", component.displayName(),
                    installed ? Strings.get("openingMechLabFitted") : inCargo ? Strings.get("openingMechLabInCargo") : Strings.get("openingMechLabNoSpare"),
                    installed || inCargo ? "catalog-badge in-cargo" : "catalog-badge unavailable",
                    Strings.format("openingMechLabOwnFieldFree", owned, fielded, free),
                    Strings.format("openingMechLabSrmSLrmS", number(component.srmReplenishmentSeconds()), number(component.lrmReplenishmentSeconds())),
                    "", "catalog-delta hidden",
                    Strings.get("openingMechLabRestoresOneMissileTriggerPack"),
                    installed ? Strings.get("openingMechLabInstalled") : socketTaken ? Strings.get("openingMechLabSocketInUse")
                            : inCargo ? Strings.get("openingMechLabInstallSpare") : Strings.get("openingMechLabNoSpareInStores"),
                    installed ? Strings.get("openingMechLabAlreadyInstalledInThisMech")
                            : mech == null ? Strings.get("openingMechLabSelectAMechBeforeFittingAReplenisher")
                            : socketTaken ? Strings.get("openingMechLabUtilityCopy")
                            : !inCargo ? Strings.get("openingMechLabNoSpareAvailableReplenishersCannotBeFabricated")
                            : Strings.get("openingMechLabInstallThisSpareAndReturnTheCurrent"),
                    disabled, List.of(), "material-costs hidden", () -> install(component.id())));
        }
        return List.copyOf(rows);
    }

    /**
     * Internals for one role: a part appears when its pattern is known or a copy is owned,
     * the way weapons do. Socket fit, the engine budget, stores and materials decide whether
     * the row can commit; {@link MechWorkshop#fitInternal} rechecks all of it.
     */
    private List<CatalogRow> internalCatalog(CampaignMech mech, MechInternal.Role role) {
        List<CatalogRow> rows = new ArrayList<>();
        MechFrameBudget.Fit fit = mech.fit();
        MechInternal current = mech.internal(role);
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.internals()) {
            MechInternal internal = recipe.internal();
            if (internal.role != role) continue;
            if (!bay.canFabricate(internal) && bay.ownedInternal(internal.id) == 0) continue;

            int owned = bay.ownedInternal(internal.id);
            int free = bay.availableInternal(internal.id);
            boolean installed = current == internal;
            boolean fits = bay.canInstallInternal(mech.id(), internal);
            boolean loaderConflict = !fits && role == MechInternal.Role.COOLER
                    && !MissileReplenisherComponent.STANDARD.id().equals(mech.missileReplenisherId());
            MechFrameBudget.Fit proposed = fit.withInternal(role, internal);
            boolean withinBudget = installed || bay.withinBudget(mech.id(), proposed);
            boolean inCargo = free > 0;
            boolean locked = !bay.canFabricate(internal);
            boolean affordable = resources.canAfford(resources.quote(recipe.cost()));
            boolean disabled = installed || !fits || !withinBudget || !inCargo && (locked || !affordable);

            String badge = installed ? Strings.get("openingMechLabFitted") : inCargo
                    ? Strings.get("openingMechLabInCargo") : Strings.get("openingMechLabFabrication");
            String badgeClasses = installed || inCargo ? "catalog-badge in-cargo" : "catalog-badge fabrication";

            float mass = MechFrameBudget.mass(internal, mech.variant());
            String detail = role.socket() == null
                    ? Strings.format("openingMechLabInternalDetailFrameWide", number(mass))
                    : Strings.format("openingMechLabInternalDetail", number(mass),
                            internal.footprintColumns, internal.footprintRows);

            String deltaText = "";
            String deltaClasses = "catalog-delta hidden";
            if (!installed) {
                Delta delta = internalDelta(fit, proposed, role);
                deltaText = delta.text();
                deltaClasses = delta.classes();
            }

            String actionLabel = installed ? Strings.get("openingMechLabInstalled")
                    : loaderConflict ? Strings.get("openingMechLabSocketInUse")
                    : !fits ? Strings.get("openingMechLabTooLarge")
                    : !withinBudget ? Strings.get("openingMechLabOverBudget")
                    : inCargo ? Strings.get("openingMechLabInstallSpare")
                    : locked ? Strings.get("openingMechLabPatternLocked")
                    : !affordable ? Strings.get("openingMechLabMaterialsShort")
                    : Strings.get("openingMechLabFabricateInstall");
            String actionHint = installed ? Strings.get("openingMechLabAlreadyInstalledInThisMech")
                    : loaderConflict ? Strings.get("openingMechLabCoolerLoaderHint")
                    : !fits ? Strings.get("openingMechLabInternalTooLargeHint")
                    : !withinBudget ? overBudgetHint(proposed)
                    : inCargo ? Strings.get("openingMechLabInternalSpareHint")
                    : locked ? Strings.get("openingMechLabInternalLockedHint")
                    : !affordable ? Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor")
                    : Strings.get("openingMechLabInternalFabricateHint");

            String base = "mech-catalog:" + internal.id;
            boolean showMaterials = !inCargo && !installed && fits && withinBudget && !locked;
            List<Row> materials = showMaterials ? materialRows(base, resources.quote(recipe.cost())) : List.of();
            rows.add(new CatalogRow(base, base + ":copy", base + ":header", base + ":name", base + ":badge",
                    base + ":stock", base + ":telemetry", base + ":delta",
                    base + ":detail", base + ":action", base + ":materials", base + ":body",
                    base + ":preview",
                    "catalog-preview hidden", null, null, null, internal,
                    installed ? "catalog-row selected" : !showMaterials ? "catalog-row"
                            : "catalog-row requires-fabrication", internal.displayName(),
                    badge, badgeClasses,
                    Strings.format("openingMechLabOwnFree", recipe.provenance(), owned, free),
                    internalTelemetry(proposed, internal), deltaText, deltaClasses,
                    detail, actionLabel, actionHint,
                    disabled, materials, showMaterials ? "material-costs" : "material-costs hidden",
                    () -> fitInternal(internal)));
        }
        return List.copyOf(rows);
    }

    private record Delta(String text, String classes) { }

    /** Mass and the role's own frame value, signed against what is fitted now. */
    private static Delta internalDelta(MechFrameBudget.Fit current, MechFrameBudget.Fit proposed,
                                       MechInternal.Role role) {
        StringBuilder text = new StringBuilder();
        int gains = 0;
        int losses = 0;
        float mass = proposed.fittedMass() - current.fittedMass();
        if (Math.abs(mass) > 0.01f) {
            text.append(Strings.get("openingMechLabDeltaMass")).append(signed(mass));
            if (mass > 0) losses++; else gains++;
        }
        float[] stats = switch (role) {
            case ENGINE -> new float[]{proposed.output() - current.output(),
                    proposed.moveSpeed() - current.moveSpeed()};
            case PLATING -> new float[]{proposed.armorCapacity() - current.armorCapacity()};
            case HEAT_SINK -> new float[]{proposed.heatCapacity() - current.heatCapacity(),
                    proposed.coolingPerSecond() - current.coolingPerSecond()};
            case COOLER -> new float[]{proposed.coolingPerSecond() - current.coolingPerSecond()};
        };
        String[] labels = switch (role) {
            case ENGINE -> new String[]{"openingMechLabDeltaOutput", "openingMechLabDeltaSpeed"};
            case PLATING -> new String[]{"openingMechLabDeltaArmor"};
            case HEAT_SINK -> new String[]{"openingMechLabDeltaHeat", "openingMechLabDeltaCool"};
            case COOLER -> new String[]{"openingMechLabDeltaCool"};
        };
        for (int i = 0; i < stats.length; i++) {
            float value = stats[i];
            if (Math.abs(value) <= 0.01f) continue;
            if (!text.isEmpty()) text.append("  ·  ");
            text.append(Strings.get(labels[i])).append(signed(value));
            if (value > 0) gains++; else losses++;
        }
        if (text.isEmpty()) return new Delta("", "catalog-delta hidden");
        return new Delta(text.toString(), losses == 0 ? "catalog-delta positive"
                : gains == 0 ? "catalog-delta negative" : "catalog-delta neutral");
    }

    /** Signed, and finer than {@link #number} so a speed step of 0.05 does not read as 0. */
    private static String signed(float value) {
        String text = Math.abs(value) >= 1f ? number(value)
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (value > 0 ? "+" : "") + text;
    }

    /** What one internal contributes, read off the fit it would produce. */
    private static String internalTelemetry(MechFrameBudget.Fit fit, MechInternal internal) {
        return switch (internal.role) {
            case ENGINE -> Strings.format("openingMechLabInternalEngineTelemetry",
                    number(fit.output()), number(fit.moveSpeed()));
            case PLATING -> Strings.format("openingMechLabInternalPlatingTelemetry",
                    Math.round(fit.armorCapacity()));
            case HEAT_SINK -> Strings.format("openingMechLabInternalHeatSinkTelemetry",
                    number(fit.heatCapacity()), number(fit.coolingPerSecond()));
            case COOLER -> Strings.format("openingMechLabInternalCoolerTelemetry",
                    number(internal.cooling));
        };
    }

    private static String overBudgetHint(MechFrameBudget.Fit proposed) {
        return Strings.format("openingMechLabInternalOverBudgetHint",
                number(proposed.fittedMass()), number(proposed.output()));
    }

    /** The internal role a socket holds; null for weapon mounts. */
    static MechInternal.Role roleFor(SocketId socket) {
        return switch (socket) {
            case CORE -> MechInternal.Role.ENGINE;
            case AMMO_RESERVE -> MechInternal.Role.HEAT_SINK;
            case MINI_FAB -> MechInternal.Role.COOLER;
            default -> null;
        };
    }

    public Signal<List<Row>> materialStock() { return materialStock; }

    private List<Row> buildMaterialStock() {
        revision.get();
        return List.of(Commodities.SUPPLIES, Commodities.HEAVY_MACHINERY,
                Commodities.METALS, Commodities.RARE_METALS).stream().map(commodity ->
                CompanyResources.stock("mech-material-stock:" + commodity, resources.commodityName(commodity),
                        resources.commodityIcon(commodity), resources.available(commodity))).toList();
    }

    private List<Row> materialRows(String ownerId, FabricationCost cost) {
        if (cost == null) return List.of();
        return cost.lines().stream().sorted(Comparator.comparingInt(line -> CompanyResources.order(line.commodityId()))).map(line -> CompanyResources.cost(
                ownerId + ":material:" + line.commodityId(), resources.commodityName(line.commodityId()),
                resources.commodityIcon(line.commodityId()), resources.available(line.commodityId()), line.quantity())).toList();
    }

    private void selectSquad(String squadId) {
        if (!bay.selectActiveSquad(squadId)) return;
        CampaignMechSquad squad = bay.squadById(squadId);
        selectedSquadId.set(squadId);
        selectedMechId.set(null);
        callSignDraft.set("");
        selectedGantry.set(0);
        selectedSlot.set(SocketId.MINI_FAB);
        platingSelected.set(false);
        fittingFocused.set(false);
        feedbackText.set(Strings.format("openingMechLabIsNowTheActiveMechSupportLance", squad.displayName()));
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
        platingSelected.set(false);
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(Strings.format("openingMechLabInspectingNoCampaignHardwareChanged", squad.mechById(mechId).displayName()));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void previousGantry() {
        selectGantry(selectedGantryIndex() - 1);
    }

    private void nextGantry() {
        selectGantry(selectedGantryIndex() + 1);
    }

    private void selectGantry(int requested) {
        int capacity = physicalGantries;
        if (capacity == 0) return;
        int index = Math.floorMod(requested, capacity);
        CampaignMech mech = mechAt(selectedSquad(), index);
        selectedGantry.set(index);
        selectedMechId.set(mech != null ? mech.id() : null);
        callSignDraft.set(mech != null ? mech.displayName() : "");
        selectedSlot.set(defaultFittingSlot(mech));
        platingSelected.set(false);
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(mech != null
                ? Strings.format("openingMechLabGantrySelectedNoCampaignHardwareChanged", String.format(Locale.ROOT, "%02d", index + 1), mech.displayName())
                : Strings.format("openingMechLabGantryIsVacantSelectAChassisPattern", String.format(Locale.ROOT, "%02d", index + 1)));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void openAssetPicker() {
        assetPickerOpen.set(true);
        fittingFocused.set(false);
        selectedMechId.set(null);
        callSignDraft.set("");
        feedbackText.set(Strings.get("openingMechLabChooseASupportLanceAndAssignedAsset"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void closeAssetPicker() {
        assetPickerOpen.set(false);
        fittingFocused.set(false);
        selectedMechId.set(null);
        callSignDraft.set("");
        feedbackText.set(Strings.get("openingMechLabReturnedToTheLanceOverviewNoCampaign"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    public void selectSlot(SocketId slot) {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        if (!layout.hasSocket(slot)) return;
        selectedSlot.set(slot);
        platingSelected.set(false);
        fittingFocused.set(true);
        SocketDef def = layout.socket(slot);
        String label = def != null ? def.label() : slot.label();
        feedbackText.set(Strings.format("openingMechLabSelected", label, (roleFor(slot) != null
                ? Strings.get("openingMechLabInternalPartsReady")
                : mountFor(slot) != null
                ? Strings.get("openingMechLabCompatibleOwnedAndFabricableWeaponAssembliesAre")
                : Strings.get("openingMechLabThisChassisIntegratedHardwareIsInspectionOnly"))));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    /** Plating covers the whole frame, so it is chosen beside the sockets rather than in one. */
    public void selectPlating() {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        platingSelected.set(true);
        clearHoverCandidates();
        feedbackText.set(Strings.format("openingMechLabSelected", Strings.get("openingMechLabFramePlating"),
                Strings.get("openingMechLabInternalPartsReady")));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private static SocketId defaultFittingSlot(CampaignMech mech) {
        if (mech == null) return SocketId.MINI_FAB;
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        if (layout.hasSocket(SocketId.MINI_FAB)) return SocketId.MINI_FAB;
        for (SocketDef def : layout.sockets()) {
            if (!def.factoryLocked() && mountFor(def.id()) != null) return def.id();
        }
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
        feedbackText.set(Strings.get("openingMechLabLanceOverviewRestoredSelectAnOccupiedGantry"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void install(String componentId) {
        CampaignMech mech = selectedMech();
        MissileReplenisherComponent component = MissileReplenisherComponent.findById(componentId);
        if (mech == null || component == null) return;
        boolean installed = bay.installReplenisher(mech.id(), component.id());
        feedbackText.set(installed
                ? Strings.format("openingMechLabInstalledOn", component.displayName(), mech.displayName())
                : Strings.get("openingMechLabRefitBlockedNoUnassignedComponentIsAvailable"));
        feedbackClasses.set(installed ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (installed) bayChanged.run();
        revision.update(value -> value + 1);
    }

    private void fitWeapon(MechMountSlot mount, MechWeaponComponent component) {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        MechFrameBudget.Fit proposed = mech.fit().withWeapon(mount, component);
        applyWeaponResult(workshop.fitWeapon(mech.id(), mount, component), component, proposed);
    }

    /**
     * Fits one internal to the selected mech through the same workshop authority and
     * feedback path weapons use; the catalog row is only a request.
     */
    public boolean fitInternal(MechInternal internal) {
        CampaignMech mech = selectedMech();
        if (mech == null || internal == null) return false;
        MechFrameBudget.Fit proposed = mech.fit().withInternal(internal.role, internal);
        MechWorkshop.Result result = workshop.fitInternal(mech.id(), internal);
        clearHoverInternal();
        return applyFitResult(result, internal.displayName(), proposed);
    }

    private boolean applyWeaponResult(MechWorkshop.Result result,
                                      MechWeaponComponent component,
                                      MechFrameBudget.Fit proposed) {
        return applyFitResult(result, component.displayName(), proposed);
    }

    private boolean applyFitResult(MechWorkshop.Result result, String name,
                                   MechFrameBudget.Fit proposed) {
        feedbackText.set(switch (result.status()) {
            case ALREADY_INSTALLED -> Strings.format("openingMechLabIsAlreadyInstalled", name);
            case INSTALLED_FROM_STORES -> Strings.format("openingMechLabInstalledFromBayStores", name);
            case FABRICATED_AND_INSTALLED -> Strings.format("openingMechLabFabricatedFromFleetCargoAndInstalled", name);
            case PATTERN_LOCKED -> Strings.get("openingMechLabThisFabricationPatternHasNotBeenAcquired");
            case INCOMPATIBLE -> Strings.get("openingMechLabRefitBlockedIncompatibleSocketTypeOrCapacity");
            case OVER_BUDGET -> overBudgetFeedback(proposed);
            case INSUFFICIENT_MATERIALS -> Strings.get("openingMechLabFabricationBlockedRequiredFleetMaterialsAreShort");
            default -> Strings.get("openingMechLabRefitBlockedCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
        return result.succeeded();
    }

    /** The refusal names both sides of the budget, so the player knows how far over it was. */
    static String overBudgetFeedback(MechFrameBudget.Fit proposed) {
        return proposed == null ? Strings.get("openingMechLabOverBudget")
                : Strings.format("openingMechLabRefitBlockedOverBudget",
                        number(proposed.fittedMass()), number(proposed.output()));
    }

    private void finishWeaponCommand(MechWorkshop.Result result) {
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
    }

    public void stripWeapon(SocketId slot) {
        if (slot == SocketId.MINI_FAB) {
            stripCooler();
            return;
        }
        CampaignMech mech = selectedMech();
        MechMountSlot mount = mountFor(slot);
        if (mech == null || mount == null) return;
        MechWeaponComponent previous = mech.weaponAt(mount);
        MechWorkshop.Result result = workshop.stripWeapon(mech.id(), mount);
        feedbackText.set(switch (result.status()) {
            case STRIPPED -> Strings.format("openingMechLabStrippedAndReturnedToCargoStores", (previous != null ? previous.displayName() : Strings.get("openingMechLabHardware")));
            case ALREADY_STRIPPED -> Strings.get("openingMechLabHardpointIsAlreadyEmpty");
            case INCOMPATIBLE -> Strings.get("openingMechLabCannotStripThisHardpointFactoryLocked");
            default -> Strings.get("openingMechLabUnequipBlockedCampaignHardwareWasNotChanged");
        });
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
    }

    /** Empties the utility socket; the cooler is the only internal that may be left out. */
    public boolean stripCooler() {
        CampaignMech mech = selectedMech();
        if (mech == null) return false;
        MechInternal previous = mech.internal(MechInternal.Role.COOLER);
        MechWorkshop.Result result = workshop.stripCooler(mech.id());
        feedbackText.set(switch (result.status()) {
            case STRIPPED -> Strings.format("openingMechLabCoolerStripped", previous.displayName());
            case ALREADY_STRIPPED -> Strings.get("openingMechLabUtilitySocketAlreadyEmpty");
            default -> Strings.get("openingMechLabUnequipBlockedCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
        return result.succeeded();
    }

    private void fabricateChassis(MechVariant variant) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.mechs().size() >= physicalGantries) return;
        MechWorkshop.Result result = workshop.fabricateChassis(squad.id(), variant);
        CampaignMech fabricated = result.mech();
        if (fabricated != null) {
            selectedMechId.set(fabricated.id());
            callSignDraft.set(fabricated.displayName());
            selectedGantry.set(Math.max(0, squad.mechs().size() - 1));
            selectedSlot.set(SocketId.ARMS);
            platingSelected.set(false);
            fittingFocused.set(true);
        }
        feedbackText.set(switch (result.status()) {
            case CHASSIS_FABRICATED -> Strings.format("openingMechLabFabricatedWithItsStandardRollOutFit", fabricated.displayName());
            case INSUFFICIENT_MATERIALS -> Strings.get("openingMechLabChassisFabricationBlockedRequiredFleetMaterialsAre");
            case LANCE_FULL -> Strings.get("openingMechLabChassisFabricationBlockedThisLanceHasNo");
            default -> Strings.get("openingMechLabChassisFabricationBlockedCampaignAssetsWereNot");
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
        if (CampaignMech.normalizeCallSign(draft).isEmpty()) {
            feedbackText.set(Strings.get("openingMechLabACallSignIsRequiredTheCurrent"));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
        } else {
            feedbackText.set(Strings.get("openingMechLabPressSetToApplyThisCallSign"));
            feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
        }
    }

    private void renameSelectedMech() {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        if (!mech.rename(callSignDraft.get())) {
            feedbackText.set(Strings.format("openingMechLabCallSignMustContainCharacters", CampaignMech.CALLSIGN_MAX_LENGTH));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        callSignDraft.set(mech.displayName());
        feedbackText.set(Strings.format("openingMechLabRenamedChassisIdentityFitAndStoresWere", mech.displayName()));
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
        if (mech == null) return Strings.get("openingMechLabNoAsset");
        return switch (slot) {
            case CORE -> mech.internal(MechInternal.Role.ENGINE).displayName();
            case ARMS -> componentName(mech.arms());
            case LEFT_SHOULDER -> componentName(mech.leftShoulder());
            case RIGHT_SHOULDER -> componentName(mech.rightShoulder());
            case AMMO_RESERVE -> mech.internal(MechInternal.Role.HEAT_SINK).displayName();
            case MINI_FAB -> mech.internal(MechInternal.Role.COOLER) != null
                    ? mech.internal(MechInternal.Role.COOLER).displayName()
                    : mech.missileReplenisherId().equals(MissileReplenisherComponent.ACCELERATED_FEED.id())
                    ? Strings.get("openingMechLabAcceleratedFeed") : Strings.get("openingMechLabStandardReplenisher");
        };
    }

    private SocketDef selectedSocketDefinition() {
        CampaignMech mech = selectedMech();
        return mech != null
                ? MechFittingLayout.forVariant(mech.variant()).socket(selectedSlot.get())
                : null;
    }

    private static String slotType(SocketDef definition) {
        return Strings.format("openingMechLabGrid2", definition.type().label(), definition.gridColumns(), definition.gridRows());
    }

    private static String componentName(MechWeaponComponent component) {
        return component != null ? component.displayName() : Strings.get("openingMechLabEmptyHardpoint");
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
            case ARMORED_SUPPORT -> Strings.get("openingMechLabHeavyArmorPointDefenseFire");
            case ASSAULT -> Strings.get("openingMechLabHighMobilityDirectStrikeBursts");
            case LR_SUPPORT -> Strings.get("openingMechLabFireSupportGuidedMissilePacks");
            default -> Strings.get("openingMechLabBalancedTacticalLoadout");
        };
    }

    private List<FilterPill> buildFilterPills() {
        if (selectedMech() == null || platingSelected.get() || mountFor(selectedSlot.get()) == null) return List.of();
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
        ALL(Strings.get("openingMechLabAll")),
        BALLISTIC(Strings.get("openingMechLabBallistic")),
        MISSILE(Strings.get("openingMechLabMissile")),
        ENERGY(Strings.get("openingMechLabEnergy")),
        IN_STOCK(Strings.get("openingMechLabInStock"));

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
            case "hardwareId" -> id + ":hardware";
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
                             MechInternal internalPreview,
                             String classes, String name, String badge, String badgeClasses,
                             String stock, String telemetry, String delta, String deltaClasses,
                             String detail, String actionLabel, String actionHint,
                             boolean actionDisabled, List<Row> materials,
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
            case "materialsStyle" -> CompanyResources.billHeight(materials);
            case "cardStyle" -> "height: " + (140 + materials.size() * 36) + "px;";
            case "materialsClasses" -> materialsClasses;
            case "action" -> action;
            default -> throw unknown("mech-catalog", p); }; }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
