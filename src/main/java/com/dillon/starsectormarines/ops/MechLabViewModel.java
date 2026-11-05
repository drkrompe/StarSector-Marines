package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.CompanyResources.Row;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.refit.RefitText;
import com.dillon.starsectormarines.campaign.refit.ShipCondition;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRefit;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.HullFitEffect;
import com.dillon.starsectormarines.battle.vehicle.HullReservoir;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignMachine;
import com.dillon.starsectormarines.ops.battleview.VehicleDoll;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.MechPatternCredits;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechCompartment;
import com.dillon.starsectormarines.marine.MechFabricationCatalog;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MechDamage;
import com.dillon.starsectormarines.marine.MechWorkshop;
import com.dillon.starsectormarines.marine.VehicleRack;
import com.dillon.starsectormarines.marine.MachineCapabilities;
import com.dillon.starsectormarines.marine.ReserveHangar;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.ops.fitting.FittedBadge;
import com.dillon.starsectormarines.ops.fitting.FittingCard;
import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.ops.fitting.FittingMeter;
import com.dillon.starsectormarines.ops.fitting.MachineFigures;
import com.dillon.starsectormarines.ops.fitting.FittingSlotCard;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Spatial doll projection and command surface for the campaign-authoritative {@link MechBay}. */
public final class MechLabViewModel {

    private final Reactor reactor;
    private final MechBay bay;
    private final FabricationResources resources;
    private final MechWorkshop workshop;
    private final Runnable bayChanged;
    private final DoubleSupplier campaignDay;
    private final MutableSignal<Integer> revision;
    /** The stores card whose RECYCLE was pressed once and waits for its confirming press; null for none. */
    private String armedRecycle;
    /** The campaign day the room last read; repair clocks and readiness are measured against it. */
    private final MutableSignal<Float> day;
    private final MutableSignal<Integer> dismissedLesson;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<String> selectedMechId;
    /** A gantry holds a mech or a vehicle; at most one of the two selections is set. */
    private final MutableSignal<String> selectedVehicleId;
    private final MutableSignal<Integer> selectedGantry;
    private final MutableSignal<SocketId> selectedSlot;
    private final MutableSignal<Boolean> fittingFocused;
    private final MutableSignal<Boolean> assetPickerOpen;
    private final MutableSignal<String> callSignDraft;
    private final MutableSignal<String> feedbackText;
    private final MutableSignal<String> feedbackClasses;
    private final MutableSignal<CategoryFilter> catalogFilter;
    /** A vacant pad's weight class, the second filter beside walkers and vehicles; null shows all. */
    private final MutableSignal<MechPattern.WeightClass> weightFilter;
    /** The machine a first press of SCRAP armed; any other selection disarms it. */
    private final MutableSignal<String> scrapArmedId;
    /** The machine a first press of STORE armed; any other selection disarms it. */
    private final MutableSignal<String> storeArmedId;
    /** The company's reserve hangar and what the home ship's refit lets it hold; null until the screen attaches it. */
    private ReserveHangar reserve;
    private IntSupplier reserveCapacity = () -> 0;
    /** The pattern selected on a vacant pad: previewed on the pad, built only by BUILD. */
    private final MutableSignal<String> previewRecipeId;
    private final MutableSignal<MechWeaponComponent> hoveredWeapon;
    /** A catalog internal under the pointer, previewed on the meters and the budget. */
    private final MutableSignal<MechInternal> hoveredInternal;
    /** Stores list every part rather than what fits the selected mount. */
    private final MutableSignal<Boolean> browsingStores;
    private final ComputedSignal<String> budgetText;
    private final ComputedSignal<String> budgetClasses;
    private final ComputedSignal<String> shipRefitText;
    /** The company ship's refits, read live here; a launch freezes them into each deployment. */
    private final Supplier<ShipCondition> shipCondition;
    private final ComputedSignal<List<FittingFilterPill>> categoryFilterPills;
    private final ComputedSignal<String> categoryFilterClasses;
    private final ComputedSignal<String> allPartsClasses;
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
    private final ComputedSignal<List<FittingMeter>> performanceMeters;
    private final ComputedSignal<List<SlotRow>> leftSlotRows;
    private final ComputedSignal<List<SlotRow>> rightSlotRows;
    private final ComputedSignal<List<SlotRow>> slotRows;
    private final ComputedSignal<List<SlotRow>> selectedSlotRows;
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
    private final ComputedSignal<Repair> repair;
    private final ComputedSignal<String> garageStatus;
    private final ComputedSignal<String> dragNoteTitle;
    private final ComputedSignal<String> dragNoteCopy;
    private final ComputedSignal<String> scrapLabel;
    private final ComputedSignal<String> scrapClasses;

    private int physicalGantries = CampaignMechSquad.CAPACITY;

    public void physicalGantries(int count) {
        physicalGantries = Math.max(0, Math.min(CampaignMechSquad.CAPACITY, count));
        if (physicalGantries > 0) bay.squads().forEach(squad -> squad.fitPadsTo(physicalGantries));
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
        this(reactor, bay, resources, bayChanged, CampaignClock::dayFloat);
    }

    /** @param campaignDay the campaign day source; repair clocks and readiness are read against it */
    public MechLabViewModel(Reactor reactor, MechBay bay, FabricationResources resources,
                            Runnable bayChanged, DoubleSupplier campaignDay) {
        this(reactor, bay, resources, bayChanged, campaignDay, () -> ShipCondition.NONE);
    }

    /** @param shipCondition the company ship's refit tree, whose Mech Lab tracks the sheet shows */
    public MechLabViewModel(Reactor reactor, MechBay bay, FabricationResources resources,
                            Runnable bayChanged, DoubleSupplier campaignDay,
                            Supplier<ShipCondition> shipCondition) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (bay == null) throw new IllegalArgumentException("mech bay is required");
        if (resources == null || bayChanged == null || campaignDay == null || shipCondition == null) {
            throw new IllegalArgumentException(
                    "fabrication resources, change callback and campaign day are required");
        }
        this.campaignDay = campaignDay;
        this.shipCondition = shipCondition;
        this.reactor = reactor;
        this.bay = bay;
        this.resources = resources;
        workshop = new MechWorkshop(bay, resources, () -> physicalGantries,
                () -> shipCondition.get().mechFieldRepair());
        this.bayChanged = bayChanged;
        CampaignMechSquad initialSquad = bay.activeSquad();
        revision = reactor.signal(0);
        day = reactor.signal(currentDay());
        workshop.startPendingRepairs(day.get());
        dismissedLesson = reactor.signal(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedMechId = reactor.signal(null);
        selectedVehicleId = reactor.signal(null);
        selectedGantry = reactor.signal(0);
        selectedSlot = reactor.signal(SocketId.AUXILIARY);
        fittingFocused = reactor.signal(false);
        assetPickerOpen = reactor.signal(false);
        callSignDraft = reactor.signal("");
        feedbackText = reactor.signal(
                Strings.get("openingMechLabSelectAnOccupiedGantryToRefitOr"));
        feedbackClasses = reactor.signal("mech-lab-feedback tone-muted surface-dark");
        catalogFilter = reactor.signal(CategoryFilter.ALL);
        weightFilter = reactor.signal(null);
        scrapArmedId = reactor.signal(null);
        storeArmedId = reactor.signal(null);
        previewRecipeId = reactor.signal(null);
        hoveredWeapon = reactor.signal(null);
        hoveredInternal = reactor.signal(null);
        browsingStores = reactor.signal(false);
        categoryFilterPills = reactor.computed(this::buildFilterPills);
        categoryFilterClasses = reactor.computed(() -> categoryFilterPills.get().isEmpty()
                ? "category-filters hidden" : "category-filters");
        // Shown while one mount narrows the list, so the player can get back to every part.
        allPartsClasses = reactor.computed(() -> selectedMachine() != null
                && !browsingStores.get() ? "catalog-all-parts console-action" : "hidden");
        labSummary = reactor.computed(this::buildLabSummary);
        squadRows = reactor.computed(this::buildSquadRows);
        mechRows = reactor.computed(this::buildMechRows);
        gantryRows = reactor.computed(this::buildGantryRows);
        activeGantryLabel = reactor.computed(() -> {
            int index = selectedGantryIndex();
            boolean occupied = selectedMachine() != null
                    || gantryOccupied(selectedSquad(), index);
            return String.format(Strings.locale(), Strings.get("openingMechLabGantryDDS"), padLetter(index),
                    occupied ? "" : Strings.get("openingMechLabVacant"));
        });
        selectedMechName = reactor.computed(() -> {
            CampaignMachine machine = selectedMachine();
            return machine != null ? machine.displayName() : fabricatingChassis()
                    ? String.format(Strings.locale(), Strings.get("openingMechLabVacantGantryD"), padLetter(selectedGantryIndex()))
                    : Strings.get("openingMechLabNoAssetSelected");
        });
        selectedMechNameClasses = reactor.computed(() -> selectedAssetName() == null
                ? "label asset-name tone-accent"
                : "label asset-name tone-accent hidden");
        callSignEditorClasses = reactor.computed(() -> selectedAssetName() == null
                ? "mech-call-sign-editor hidden" : "mech-call-sign-editor");
        callSignRenameDisabled = reactor.computed(() -> {
            String current = selectedAssetName();
            String candidate = callSignDraft.get();
            if (current == null || candidate == null) return true;
            String normalized = CampaignMech.normalizeCallSign(candidate);
            return normalized.isEmpty()
                    || normalized.length() > CampaignMech.CALLSIGN_MAX_LENGTH
                    || normalized.equals(current);
        });
        selectedMechIdentity = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            CampaignVehicle vehicle = selectedVehicle();
            if (vehicle != null) return vehicleIdentity(vehicle.type());
            return mech != null ? Strings.format("openingMechLabChassisWalkerHeavyAsset", mech.variant().displayName())
                    : fabricatingChassis()
                    ? Strings.get("openingMechLabSelectADiscoveredChassisPatternAndCommit")
                    : Strings.get("openingMechLabSelectAnAssignedHeavyAssetFromThe");
        });
        selectedMechDoctrine = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            CampaignVehicle vehicle = selectedVehicle();
            if (vehicle != null) return vehicleArmament(vehicle);
            if (mech == null) return fabricatingChassis() ? Strings.get("openingMechLabChassisForge") : Strings.get("openingMechLabDoctrineUnavailable");
            return Strings.join("separatorDot", roleLabel(mech.role()), doctrineAdvice(mech.role()));
        });
        performanceMeters = reactor.computed(this::buildFittingMeters);
        budgetText = reactor.computed(this::buildBudgetText);
        budgetClasses = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return "label heading mech-budget hidden";
            MechFrameBudget.Fit candidate = candidateFit(mech);
            boolean over = mech.fit().overBudget() || candidate != null && candidate.overBudget();
            return over ? "label heading mech-budget tone-danger" : "label heading mech-budget tone-edge";
        });
        shipRefitText = reactor.computed(() -> {
            revision.get();
            return selectedMech() == null ? "" : RefitText.mechLine(shipCondition.get());
        });
        leftSlotRows = reactor.computed(() -> buildSlots(List.of(
                SocketId.CORE, SocketId.ARMS, SocketId.LEFT_ARM, SocketId.LEFT_SHOULDER)));
        rightSlotRows = reactor.computed(() -> buildSlots(List.of(
                SocketId.RIGHT_ARM, SocketId.RIGHT_SHOULDER, SocketId.AUXILIARY)));
        slotRows = reactor.computed(() -> {
            CampaignMachine machine = selectedMachine();
            if (machine == null) return List.of();
            return buildSlots(machineSockets(machine).stream().filter(socket -> !socket.factoryLocked())
                    .map(SocketDef::id).toList());
        });
        selectedSlotRows = reactor.computed(() -> browsingStores.get() ? List.<SlotRow>of()
                : slotRows.get().stream().filter(row -> row.socketId() == selectedSlot.get()).toList());
        selectedSlotTitle = reactor.computed(() -> {
            if (fabricatingChassis()) return Strings.get("openingMechLabChassisPatterns");
            CampaignMachine machine = selectedMachine();
            if (machine != null && browsingStores.get()) return Strings.get("openingMechLabAllParts");
            SocketDef def = selectedSocketDefinition();
            return def != null ? def.label() : selectedSlot.get().label();
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
                ? "garage-back console-action" : "hidden");
        performanceClasses = reactor.computed(() -> fittingFocused.get()
                && selectedMachine() != null
                ? "performance-grid" : "performance-grid hidden");
        catalogClasses = reactor.computed(() -> fittingFocused.get()
                ? "panel catalog-panel" : "panel catalog-panel hidden");
        slotRackClasses = reactor.computed(() -> fittingFocused.get() && selectedMachine() != null
                ? "panel slot-panel" : "panel slot-panel hidden");
        overviewRailClasses = reactor.computed(() -> fittingFocused.get()
                ? "overview-rail hidden" : "overview-rail");
        garageTitle = reactor.computed(() -> fittingFocused.get()
                ? activeGantryLabel.get() : String.format(Strings.locale(),
                        Strings.get("openingMechLabActiveLanceAllGantries"), padLetter(Math.max(0, physicalGantries - 1))));
        repair = reactor.computed(this::buildRepair);
        garageStatus = reactor.computed(() -> {
            revision.get();
            if (!fittingFocused.get()) return Strings.get("openingMechLabStatusSelectAPad");
            if (fabricatingChassis()) return Strings.get("openingMechLabStatusChooseAMachine");
            return Strings.get("openingUiMechLabSelectMountDragToFit");
        });
        dragNoteTitle = reactor.computed(() -> {
            revision.get();
            if (fabricatingChassis()) return Strings.get("openingMechLabBuildAMachine");
            return Strings.get("openingUiMechLabDragHardwareToAMount");
        });
        dragNoteCopy = reactor.computed(() -> {
            revision.get();
            if (fabricatingChassis()) return Strings.get("openingMechLabBuildAMachineCopy");
            return Strings.get("openingUiMechLabDropFittedWeaponsHereToReturnTo");
        });
        scrapLabel = reactor.computed(() -> {
            revision.get();
            String armed = scrapArmedId.get();
            return armed != null && armed.equals(selectedMachineId())
                    ? Strings.get("openingMechLabScrapConfirm") : Strings.get("openingMechLabScrap");
        });
        scrapClasses = reactor.computed(() -> {
            revision.get();
            if (!fittingFocused.get() || selectedMachineId() == null) return "hidden";
            String armed = scrapArmedId.get();
            return armed != null && armed.equals(selectedMachineId())
                    ? "garage-scrap console-action armed" : "garage-scrap console-action";
        });
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
        var lesson = CampaignLessons.lesson(step - 1, "lessonGantryTitle", "lessonGantry" + step,
                step == 1 ? List.of("mech-overview-gantries", "mech-overview-browse")
                        : List.of("mech-doll-canvas", "mech-slot-rack", "mech-component-catalog"),
                step == 1 ? "lessonOpenWayward" : null, step == 1 ? this::guideLabLesson : null);
        // At the gantry, Castle stands in the empty dark above the slot graph, clear of stores and the hull.
        String dock = step == 1 ? null : "mech-doll-canvas";
        return new PreparationGuide.Lesson(lesson.milestone(), lesson.title(), lesson.copy(), lesson.targets(),
                lesson.actionLabel(), lesson.action(), lesson.height(), this::skipLabLesson,
                LessonCopy.text("lessonSkip"), dock);
    }

    void skipLabLesson() {
        bay.skipLabLesson();
        revision.update(value -> value + 1);
    }

    public void putTutorialProps(Map<String, Object> props) {
        props.put("lessonClasses", reactor.computed(() -> {
            revision.get();
            return labGuideVisible() ? "lesson-backdrop" : "lesson-backdrop-hidden";
        }));
        props.put("lessonTitle", reactor.computed(() -> { revision.get(); return LessonCopy.text("lessonGantryTitle"); }));
        props.put("lessonCopy", reactor.computed(() -> { revision.get(); return LessonCopy.text("lessonGantry" + Math.min(2, Math.max(1, bay.labLesson()))); }));
        props.put("lessonActionLabel", reactor.computed(() -> {
            revision.get();
            return LessonCopy.text("lessonOpenWayward");
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
    public Signal<List<FittingMeter>> performanceMeters() { return performanceMeters; }
    public Signal<List<SlotRow>> leftSlotRows() { return leftSlotRows; }
    public Signal<List<SlotRow>> rightSlotRows() { return rightSlotRows; }
    public Signal<List<SlotRow>> slotRows() { return slotRows; }
    /** Only the mount selected on the doll: the right column describes that one socket. */
    public Signal<List<SlotRow>> selectedSlotRows() { return selectedSlotRows; }
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
    public Signal<String> garageStatus() { return garageStatus; }
    public Signal<String> dragNoteTitle() { return dragNoteTitle; }
    public Signal<String> dragNoteCopy() { return dragNoteCopy; }
    public Signal<String> scrapLabel() { return scrapLabel; }
    public Signal<String> scrapClasses() { return scrapClasses; }
    public Runnable scrapAction() { return this::scrapSelected; }

    /*
     * A vacant pad works in two steps: selecting a pattern previews it standing on the pad with
     * its lore and bill in the build panel, and only BUILD spends materials.
     */

    /** The previewed pattern, or the first one the filter shows when nothing is selected yet. */
    private MechFabricationCatalog.Recipe previewRecipe() {
        revision.get();
        if (!fabricatingChassis()) return null;
        List<MechFabricationCatalog.Recipe> shown = shownPatterns();
        if (shown.isEmpty()) return null;
        String id = previewRecipeId.get();
        for (MechFabricationCatalog.Recipe recipe : shown) {
            if (recipe.id().equals(id)) return recipe;
        }
        return shown.get(0);
    }

    private List<MechFabricationCatalog.Recipe> shownPatterns() {
        CategoryFilter filter = effectiveFilter();
        MechPattern.WeightClass weight = weightFilter.get();
        List<MechFabricationCatalog.Recipe> shown = new ArrayList<>();
        if (filter != CategoryFilter.VEHICLES) {
            for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.chassis()) {
                if (bay.canFabricate(recipe.variant())
                        && (weight == null || MechPattern.of(recipe.variant()).weightClass() == weight)) shown.add(recipe);
            }
        }
        if (filter != CategoryFilter.WALKERS) {
            for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.vehicles()) {
                if (bay.canFabricate(recipe.vehicle())
                        && (weight == null || MechPattern.weightClass(recipe.vehicle()) == weight)) shown.add(recipe);
            }
        }
        return shown;
    }

    /** Whether the pad has room, the fleet is at a port and the company has the credits for this pattern. */
    private boolean canBuild(MechFabricationCatalog.Recipe recipe) {
        return padHasSpace() && resources.canBuy(resources.quote(recipe.cost()));
    }

    private void build(MechFabricationCatalog.Recipe recipe) {
        previewRecipeId.set(null);
        if (recipe.variant() != null) fabricateChassis(recipe.variant());
        else fabricateVehicle(recipe.vehicle());
    }

    private String patternClasses(MechFabricationCatalog.Recipe recipe) {
        MechFabricationCatalog.Recipe previewed = previewRecipe();
        return previewed != null && previewed.id().equals(recipe.id())
                ? "catalog-row chassis-pattern selected" : "catalog-row chassis-pattern";
    }

    /** What stands on the vacant pad as a preview: a {@link MechVariant}, a {@link VehicleType}, or null. */
    public Object padPreview() {
        MechFabricationCatalog.Recipe recipe = previewRecipe();
        if (recipe == null) return null;
        return recipe.variant() != null ? recipe.variant() : recipe.vehicle();
    }

    public void previewPattern(String recipeId) { previewRecipeId.set(recipeId); }

    private boolean padHasSpace() {
        CampaignMechSquad squad = selectedSquad();
        return squad != null && squad.occupied() < physicalGantries && bay.canAddMech(squad.id());
    }

    /** The build panel's props, read by the screen and both test prop builders. */
    public void putBuildPanelProps(Map<String, Object> props) {
        // The pattern card stands in the right-hand column, where a mech's specifications do.
        props.put("buildPanelClasses", reactor.computed(() -> fittingFocused.get() && fabricatingChassis()
                ? "panel slot-panel build-column" : "hidden"));
        props.put("buildName", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            return recipe != null ? recipe.displayName() : Strings.get("openingMechLabSelectAPattern");
        }));
        // A chassis's default role; a hull has none, and comes with no gun to name in its place.
        props.put("buildRole", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            return recipe != null && recipe.variant() != null
                    ? Strings.upper(roleLabel(recipe.variant().defaultRole)) : "";
        }));
        props.put("buildLore", reactor.computed(() -> lore(previewRecipe())));
        props.put("buildWeightIcon", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            MechPattern.WeightClass weight = recipe != null ? weightOf(recipe.variant(), recipe.vehicle()) : null;
            return weight != null ? weightIcon(weight) : weightIcon(MechPattern.WeightClass.LIGHT);
        }));
        props.put("buildWeightClasses", reactor.computed(() -> previewRecipe() != null ? "weight-slot" : "hidden"));
        props.put("buildWeightStyle", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            MechPattern.WeightClass weight = recipe != null ? weightOf(recipe.variant(), recipe.vehicle()) : null;
            return weight != null ? "background-color: " + weightTone(weight) + ";" : "";
        }));
        props.put("buildMeters", reactor.computed(() -> patternMeters(previewRecipe())));
        // A vacant pad's catalog needs no socket rule: every pattern goes onto the same pad.
        props.put("catalogRuleClasses", reactor.computed(() -> fabricatingChassis()
                ? "hidden" : "label heading surface-dark catalog-rule"));
        props.put("buildCredit", reactor.computed(() -> credit(previewRecipe())));
        props.put("buildCrest", reactor.computed(() -> {
            MechPatternCredits.Credit credit = creditOf(previewRecipe());
            return credit != null ? credit.crest() : NO_CREST;
        }));
        props.put("buildCrestClasses", reactor.computed(() -> creditOf(previewRecipe()) != null
                ? "build-crest" : "hidden"));
        props.put("buildStats", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            if (recipe == null) return "";
            return patternStats(recipe);
        }));
        props.put("buildMaterials", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            return recipe != null ? materialRows("mech-build", resources.quote(recipe.cost())) : List.<Row>of();
        }));
        props.put("buildLabel", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            if (recipe == null) return Strings.get("openingMechLabBuildAction");
            if (!padHasSpace()) return Strings.get("openingMechLabLanceFull");
            return resources.canBuy(resources.quote(recipe.cost()))
                    ? Strings.format("openingMechLabBuildOnPad", padLetter(selectedGantryIndex()))
                    : !resources.atMarket() ? Strings.get("fabricationNoPortShort")
                    : Strings.get("openingMechLabMaterialsShort");
        }));
        props.put("buildDisabled", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            return recipe == null || !padHasSpace() || !resources.canBuy(resources.quote(recipe.cost()));
        }));
        props.put("buildAction", buildAction());
    }

    public Runnable buildAction() { return this::buildPreviewed; }

    /**
     * The pattern card's bars: the machine as it is bought, with nothing fitted, each against the standard fit's mark so
     * the gap to fill reads as a shortfall. The same rows as the specifications, on the same scales.
     */
    private List<FittingMeter> patternMeters(MechFabricationCatalog.Recipe recipe) {
        if (recipe == null) return List.of();
        if (recipe.vehicle() != null) {
            VehicleType type = recipe.vehicle();
            MachineFigures bare = vehicleFigures(type, null, null, List.of(), null);
            return MachineFigures.meters(METER_ID + "build-", bare, bare, stockFigures(type), ceiling(), seats(type.capacity));
        }
        MechVariant variant = recipe.variant();
        MachineFigures bare = mechFigures(variant, MechFrameBudget.Fit.frame(variant), new Gaps(true, true, true));
        return MachineFigures.meters(METER_ID + "build-", bare, bare, stockFigures(variant), ceiling(), seats(0));
    }

    private void buildPreviewed() {
        MechFabricationCatalog.Recipe recipe = previewRecipe();
        if (recipe != null) build(recipe);
    }

    /** A pattern nobody has credited draws no crest; the image is hidden, so any path will do. */
    private static final String NO_CREST = "graphics/factions/neutral_traders.png";

    private static MechPatternCredits.Credit creditOf(MechFabricationCatalog.Recipe recipe) {
        if (recipe == null) return null;
        return MechPatternCredits.of(recipe.variant() != null ? MechPattern.of(recipe.variant())
                : recipe.vehicle() != null ? MechPattern.of(recipe.vehicle()) : null);
    }

    /** The procurement line a pattern comes off, in the loadout cards' voice; its provenance if uncredited. */
    private static String credit(MechFabricationCatalog.Recipe recipe) {
        MechPatternCredits.Credit credit = creditOf(recipe);
        if (credit != null) return Strings.get(credit.provenanceKey());
        return recipe != null ? recipe.provenance() : "";
    }

    /** Chassis lore is the gear catalogue's; vehicle lore is the lab's own. */
    private static String lore(MechFabricationCatalog.Recipe recipe) {
        if (recipe == null) return Strings.get("openingMechLabSelectAPatternCopy");
        if (recipe.vehicle() != null) return Strings.get("vehicleLore" + recipe.vehicle().name());
        return switch (recipe.variant()) {
            case HOUND -> Strings.get("gearCatalogHoundDescription");
            case WARDEN -> Strings.get("gearCatalogWardenDescription");
            case BULWARK -> Strings.get("gearCatalogBulwarkDescription");
            default -> "";
        };
    }
    public Runnable openAssetPickerAction() { return this::openAssetPicker; }
    public Runnable closeAssetPickerAction() { return this::closeAssetPicker; }
    public Runnable previousGantryAction() { return this::previousGantry; }
    public Runnable nextGantryAction() { return this::nextGantry; }
    public Runnable selectGantryAction(int index) { return () -> selectGantry(index); }
    public Runnable overviewAction() { return this::showLanceOverview; }
    public Consumer<String> editCallSignAction() { return this::editCallSign; }
    public Runnable renameCallSignAction() { return this::renameSelectedMech; }
    public Signal<String> categoryFilterClasses() { return categoryFilterClasses; }
    public Signal<String> allPartsClasses() { return allPartsClasses; }
    public Signal<List<FittingFilterPill>> categoryFilterPills() { return categoryFilterPills; }
    public Signal<CategoryFilter> catalogFilter() { return catalogFilter; }
    /** Chooses a category, or clears it when it is already the one chosen. */
    public void setFilter(CategoryFilter filter) {
        if (filter == null) return;
        catalogFilter.set(filter == catalogFilter.get() ? CategoryFilter.ALL : filter);
    }

    /** Whether stores list every part rather than what fits the selected mount. */
    public Signal<Boolean> browsingStores() { return browsingStores; }

    /** Leaves the selected mount and lists every part in stores. */
    public void showAllParts() {
        if (selectedMachine() == null) return;
        browsingStores.set(true);
        catalogFilter.set(CategoryFilter.ALL);
        feedbackText.set(Strings.get("openingMechLabAllPartsFeedback"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
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
    /** The ship's Mech Lab refits as the selected mech will deploy with them; empty when neutral. */
    public Signal<String> shipRefitText() { return shipRefitText; }
    /** Whether the machine's armor point is the selected socket, so the catalog lists plating. */
    public boolean platingSelected() { return selectedMachine() != null && selectedSlot.get() == SocketId.PLATING; }

    /** Fitting-budget and plating bindings, shared by the screen and its evidence. */
    public void putInternalProps(Map<String, Object> props) {
        props.put("budgetText", budgetText);
        props.put("budgetClasses", budgetClasses);
        props.put("shipRefitText", shipRefitText);
    }

    /** Battle-damage repair bindings, shared by the screen and its evidence. */
    public void putRepairProps(Map<String, Object> props) {
        props.put("repair", repair);
    }

    public Signal<Repair> repair() { return repair; }

    /** The selected mech's current fit, or null without one. */
    public MechFrameBudget.Fit selectedFit() {
        CampaignMech mech = selectedMech();
        return mech != null ? mech.fit() : null;
    }

    /** Flux reservoir a weapon sheet reads against: the selected machine's, or the stock one. */
    public float selectedFluxCapacity() {
        CampaignVehicle vehicle = selectedVehicle();
        if (vehicle != null) return figures(vehicle, null, null).flux();
        MechFrameBudget.Fit fit = selectedFit();
        return fit != null ? fit.fluxCapacity() : MechInternal.FACTORY_CAPACITOR_BANK.fluxCapacity;
    }

    /** Dissipation a weapon sheet reads against: the selected machine's, or the stock frame's. */
    public float selectedDissipationPerSecond() {
        CampaignVehicle vehicle = selectedVehicle();
        if (vehicle != null) return figures(vehicle, null, null).dissipation();
        MechFrameBudget.Fit fit = selectedFit();
        float base = fit != null ? fit.dissipationPerSecond() : MechInternal.FACTORY_CAPACITOR_BANK.dissipation;
        return base * refit().dissipationMult();
    }

    private MechRefit refit() {
        return shipCondition.get().mechRefit();
    }

    /**
     * The internal shown in one socket well: the engine in the core. Null for weapon mounts, an
     * empty core, and the auxiliary compartment, which shows {@link #installedCompartment}.
     */
    public MechInternal installedInternal(SocketId id) {
        CampaignMachine machine = selectedMachine();
        MechInternal.Role role = id != null ? roleFor(id) : null;
        return machine != null && role != null ? machine.internal(role) : null;
    }

    /** What the selected machine's auxiliary compartment holds, in fitted order. */
    public List<MechInternal> installedCompartment() {
        CampaignMachine machine = selectedMachine();
        return machine != null ? machine.compartment() : List.of();
    }

    /** The selected machine's compartment at its packed places, across every bay; empty if it somehow does not pack. */
    public List<MechCompartment.Placement> compartmentPlacements() {
        CampaignMachine machine = selectedMachine();
        if (machine == null) return List.of();
        List<MechCompartment.Placement> placed = MechCompartment.pack(machine.compartmentGrids(), machine.compartment());
        return placed != null ? placed : List.of();
    }

    /** The selected machine's compartment parts that pack into one bay, at their places there. */
    public List<MechCompartment.Placement> compartmentPlacements(SocketId bay) {
        if (bay == null || !bay.compartment()) return List.of();
        return MechCompartment.in(compartmentPlacements(), bay);
    }
    public Runnable selectSlotAction(SocketId slot) { return () -> selectSlot(slot); }
    public Runnable stripWeaponAction(SocketId slot) { return () -> stripWeapon(slot); }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

    /** Current authored fitting socket; shared with the physical room overlay. */
    public SocketId selectedSocket() { return browsingStores.get() ? null : selectedSlot.get(); }

    /** Whether the garage camera should frame the selected asset for fitting. */
    public boolean fittingFocused() { return fittingFocused.get(); }

    /** Current preview identity; the canvas deliberately reads no mutable battle state. */
    public MechVariant selectedVariant() {
        CampaignMech mech = selectedMech();
        return mech != null ? mech.variant() : null;
    }

    /** Socket geometry currently projected into one fitting-rack row. */
    public SocketDef socketDefinition(SocketId id) {
        CampaignMachine machine = selectedMachine();
        if (machine == null) return null;
        for (SocketDef socket : machineSockets(machine)) if (socket.id() == id) return socket;
        return null;
    }

    /** Every socket a machine's doll has: a chassis's authored layout, or what a hull's type gives it. */
    private static List<SocketDef> machineSockets(CampaignMachine machine) {
        return machine instanceof CampaignMech mech ? MechFittingLayout.forVariant(mech.variant()).sockets()
                : VehicleDoll.sockets(((CampaignVehicle) machine).type());
    }

    /** Installed weapon currently projected into one fitting-rack row. */
    public MechWeaponComponent installedWeapon(SocketId id) {
        return slotWeapon(selectedMachine(), id);
    }

    public boolean socketOccupied(SocketId id) {
        return selectedMachine() != null && (installedWeapon(id) != null
                || id != null && roleFor(id) != null && installedInternal(id) != null
                || id != null && id.compartment() && !compartmentPlacements(id).isEmpty());
    }

    /** Selects a socket directly from the physical fitting doll. */
    public void selectSocket(SocketId id) {
        if (id != null) selectSlot(id);
    }

    /**
     * Compatibility and availability used by the drag hover preview. Only a spare in stores
     * can be dragged: fabrication is its own step, from the row's FAB button.
     */
    public boolean canDropCatalogWeapon(MechWeaponComponent component, SocketId target) {
        CampaignMachine machine = selectedMachine();
        MechMountSlot mount = mountFor(target);
        if (machine == null || component == null || mount == null
                || bay.availableWeapon(component.id) <= 0
                || installedWeapon(target) == component) return false;
        return weaponGoes(machine, mount, component);
    }

    /** Whether a weapon goes at a mount: by a mech's mount, size and engine budget, or by a hull's one gun card. */
    private boolean weaponGoes(CampaignMachine machine, MechMountSlot mount, MechWeaponComponent component) {
        if (machine instanceof CampaignMech mech) {
            return bay.canInstallWeapon(mech.id(), mount, component.id)
                    && bay.withinBudget(mech.id(), mech.fit().withWeapon(mount, component));
        }
        return mount == MechMountSlot.ARMS && bay.canFitGun(machine.id(), component);
    }

    /** True when the bay holds a spare of this part, so its row can be picked up. */
    public boolean inStores(MechWeaponComponent component) {
        return component != null && bay.availableWeapon(component.id) > 0;
    }

    public boolean inStores(MechInternal internal) {
        return internal != null && bay.availableInternal(internal.id) > 0;
    }

    /**
     * An internal drops only on its own role's mount (an engine on the core, every other part in
     * the compartment), from a spare, where it packs, within the engine budget.
     */
    public boolean canDropCatalogInternal(MechInternal internal, SocketId target) {
        CampaignMachine machine = selectedMachine();
        if (machine == null || internal == null || target == null
                || (internal.role.compartment() ? !target.compartment() : internal.role.socket() != target)
                || MechCompartment.limit(internal.role) == 1 && machine.internal(internal.role) == internal
                || !inStores(internal)) return false;
        return partGoes(machine, internal);
    }

    /** Whether a spare would go on the machine, room and requirements met; on a mech, within its engine budget too. */
    private boolean partGoes(CampaignMachine machine, MechInternal internal) {
        if (machine instanceof CampaignMech mech) {
            return bay.canInstallInternal(mech.id(), internal)
                    && bay.withinBudget(mech.id(), mech.fit().withInternal(internal.role, internal));
        }
        return switch (internal.role) {
            case ENGINE -> bay.canFitEngine(machine.id(), internal);
            case PLATING -> bay.canFitPlating(machine.id(), internal);
            default -> bay.canFitRackPart(machine.id(), internal);
        };
    }

    public boolean dropCatalogInternal(MechInternal internal, SocketId target) {
        if (!canDropCatalogInternal(internal, target)) return false;
        selectedSlot.set(target);
        return fitInternal(internal);
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
        CampaignMachine machine = selectedMachine();
        MechMountSlot mount = mountFor(target);
        if (machine == null || component == null || mount == null
                || bay.availableWeapon(component.id) <= 0) return false;
        if (machine instanceof CampaignVehicle && mount != MechMountSlot.ARMS) return false;
        selectedSlot.set(target);
        MechFrameBudget.Fit proposed = machine instanceof CampaignMech mech ? mech.fit().withWeapon(mount, component) : null;
        return applyWeaponResult(workshop.fitWeapon(machine.id(), mount, component), component, proposed);
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
        CampaignMachine machine = selectedMachine();
        MechMountSlot mount = mountFor(source);
        MechWeaponComponent component = installedWeapon(source);
        if (machine == null || mount == null || component == null) return false;
        MechWorkshop.Result result = workshop.stripWeapon(machine.id(), mount);
        feedbackText.set(result.succeeded()
                ? Strings.format("openingMechLabReturnedToBayStoresIsEmpty", component.displayName(), source.label())
                : Strings.get("openingMechLabRemovalCancelledCampaignHardwareWasNotChanged"));
        if (result.succeeded()) selectedSlot.set(source);
        finishWeaponCommand(result);
        return result.succeeded();
    }

    /**
     * Drags the engine or plate off the doll back to stores, leaving the socket empty. That is allowed: with no engine
     * the machine reads incomplete and stays off the drop manifest until one is fitted.
     */
    public boolean removeInstalledInternal(SocketId source) {
        CampaignMachine machine = selectedMachine();
        MechInternal.Role role = source != null ? roleFor(source) : null;
        if (machine == null || role == null) return false;
        selectedSlot.set(source);
        MechInternal previous = machine.internal(role);
        return finishPartStrip(machine, previous, workshop.stripInternal(machine.id(), role));
    }

    /**
     * Takes one part out of the compartment back to stores, from its X or a drag off the doll.
     * Taking out the capacitor bank of a machine with a gun leaves it incomplete.
     */
    public boolean removeCompartmentPart(MechInternal part) {
        CampaignMachine machine = selectedMachine();
        if (machine == null || part == null) return false;
        selectedSlot.set(SocketId.AUXILIARY);
        return finishPartStrip(machine, part, workshop.stripPart(machine.id(), part));
    }

    /** Says what taking {@code part} off did: only a part the machine cannot go without leaves it unable to drop. */
    private boolean finishPartStrip(CampaignMachine machine, MechInternal part, MechWorkshop.Result result) {
        feedbackText.set(switch (result.status()) {
            case STRIPPED -> Strings.format(machine.missingInternals().contains(part.role)
                    ? "openingMechLabInternalStripped" : "openingMechLabPartReturned", part.displayName());
            case ALREADY_STRIPPED -> Strings.get("openingMechLabSocketAlreadyEmpty");
            default -> Strings.get("openingMechLabUnequipBlockedCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
        return result.status() == MechWorkshop.Status.STRIPPED;
    }

    public void cancelWeaponDrag() {
        feedbackText.set(Strings.get("openingMechLabRefitDragCancelledCampaignHardwareWasNot"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    /** Current lance pad by pad for the embedded garage scene; null where no mech stands. */
    public List<MechVariant> gantryVariants() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        if (physicalGantries == 1 && selectedMech() != null) return List.of(selectedMech().variant());
        return byPad(pad -> {
            CampaignMech mech = squad.mechOnPad(pad);
            return mech != null ? mech.variant() : null;
        });
    }

    /** One entry a working pad, null where the pad holds nothing of this kind, without the empty tail. */
    private <T> List<T> byPad(IntFunction<T> onPad) {
        List<T> pads = new ArrayList<>();
        for (int pad = 0; pad < physicalGantries; pad++) pads.add(onPad.apply(pad));
        while (!pads.isEmpty() && pads.get(pads.size() - 1) == null) pads.remove(pads.size() - 1);
        return pads;
    }

    /** Frozen fitting values used to keep the physical gantries visually honest. */
    public List<MechDeploymentSpec> gantryDeployments() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        if (physicalGantries == 1 && selectedMech() != null)
            return List.of(selectedMech().freezeForDeployment());
        return byPad(pad -> {
            CampaignMech mech = squad.mechOnPad(pad);
            return mech != null ? mech.freezeForDeployment() : null;
        });
    }

    /**
     * The vehicles pad by pad, as fitted, null where no vehicle stands; none while one pad stands
     * for the selected mech alone.
     */
    public List<VehicleDeploymentSpec> gantryVehicles() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || physicalGantries == 1) return List.of();
        return byPad(pad -> {
            CampaignVehicle vehicle = squad.vehicleOnPad(pad);
            return vehicle != null ? vehicle.freezeForDeployment() : null;
        });
    }

    /** Selected vehicle's stable camera target within the current lance. */
    public int selectedGantryIndex() {
        revision.get();
        return Math.max(0, Math.min(physicalGantries - 1,
                selectedGantry.get()));
    }

    /** Whether this pad of the selected lance holds a mech or a vehicle. */
    public boolean padOccupied(int pad) {
        revision.get();
        return gantryOccupied(bay.squadById(selectedSquadId.get()), pad);
    }

    /** Reprojects mutable campaign authority whenever the room is re-entered. */
    public void refresh() {
        CampaignMechSquad squad = bay.squadById(selectedSquadId.get());
        if (squad == null) {
            squad = bay.activeSquad();
            selectedSquadId.set(squad != null ? squad.id() : null);
        }
        selectedMechId.set(null);
        selectedVehicleId.set(null);
        callSignDraft.set("");
        fittingFocused.set(false);
        assetPickerOpen.set(false);
        day.set(currentDay());
        workshop.startPendingRepairs(day.get());
        revision.update(value -> value + 1);
    }

    /**
     * Re-reads the campaign day: a repair clock runs on campaign time, so gantry readiness
     * follows it without the room having to be re-entered. Cheap when the day has not moved.
     */
    public void refreshTime() {
        float now = currentDay();
        if (now != day.get()) day.set(now);
    }

    private float currentDay() {
        double now = campaignDay.getAsDouble();
        return Double.isFinite(now) ? (float) now : 0f;
    }

    private String buildLabSummary() {
        revision.get();
        CampaignMechSquad active = bay.activeSquad();
        int chassis = active != null ? active.occupied() : 0;
        return Strings.format("mechLabBuildSummary", bay.squads().size(), chassis, CampaignMechSquad.CAPACITY);
    }

    private List<SquadRow> buildSquadRows() {
        revision.get();
        String selected = selectedSquadId.get();
        CampaignMechSquad active = bay.activeSquad();
        List<SquadRow> rows = new ArrayList<>();
        for (CampaignMechSquad squad : bay.squads()) {
            boolean activeSupport = active != null && squad.id().equals(active.id());
            String base = "mech-squad:" + squad.id();
            List<LanceSlot> slots = new ArrayList<>();
            for (int index = 0; index < physicalGantries; index++) {
                CampaignMech mech = mechAt(squad, index);
                CampaignVehicle vehicle = mech == null ? vehicleAt(squad, index) : null;
                String occupant = mech != null ? mech.id() : vehicle != null ? vehicle.id() : "vacant";
                slots.add(new LanceSlot(base + ":pad:" + index + ":" + occupant, padLetter(index),
                        mech, vehicle));
            }
            rows.add(new SquadRow(base, base + ":name", base + ":status", base + ":strength", base + ":slots",
                    squad.id().equals(selected) ? "mech-squad-row selected" : "mech-squad-row",
                    squad.displayName(), activeSupport ? Strings.get("openingMechLabActiveSupport") : Strings.get("openingMechLabAvailable"),
                    Strings.format("openingMechLabAssets", squad.occupied(), physicalGantries),
                    List.copyOf(slots), () -> selectSquad(squad.id())));
        }
        return List.copyOf(rows);
    }

    private List<MechRow> buildMechRows() {
        revision.get();
        String selected = selectedMechId.get();
        CampaignMechSquad squad = selectedSquad();
        if (squad == null) return List.of();
        List<MechRow> rows = new ArrayList<>();
        String selectedVehicle = selectedVehicleId.get();
        for (int index = 0; index < CampaignMechSquad.CAPACITY; index++) {
            CampaignMech mech = squad.mechOnPad(index);
            CampaignVehicle vehicle = squad.vehicleOnPad(index);
            if (mech != null) {
                String base = "mech:" + mech.id();
                rows.add(new MechRow(base, base + ":name", base + ":chassis", base + ":subsystem",
                        base + ":action", base + ":preview", base + ":pad", padLabel(index), padLetter(index), mech, null,
                        mech.id().equals(selected) ? "mech-row selected" : "mech-row",
                        mech.displayName(), Strings.join("separatorDot", mech.variant().displayName(), roleLabel(mech.role())),
                        mech.missileReplenisher().displayName(), readinessText(mech),
                        readinessClasses(mech, "label heading mech-card-action"),
                        () -> selectMech(mech.id())));
                continue;
            }
            if (vehicle != null) {
                String base = "vehicle:" + vehicle.id();
                rows.add(new MechRow(base, base + ":name", base + ":chassis", base + ":subsystem",
                        base + ":action", base + ":preview", base + ":pad", padLabel(index), padLetter(index), null, vehicle,
                        vehicle.id().equals(selectedVehicle) ? "mech-row selected" : "mech-row",
                        vehicle.displayName(), vehicleIdentity(vehicle.type()),
                        vehicleArmament(vehicle), Strings.get("openingUiMechLabOpenGantry"),
                        "label heading mech-card-action", () -> selectVehicle(vehicle.id())));
                continue;
            }
            if (index >= physicalGantries) continue;
            String base = "vacant-pad:" + squad.id() + ":" + index;
            int gantry = index;
            rows.add(new MechRow(base, base + ":name", base + ":chassis", base + ":subsystem",
                    base + ":action", base + ":preview", base + ":pad", padLabel(index), padLetter(index), null, null,
                    "mech-row vacant", Strings.get("openingMechLabVacant2"),
                    Strings.get("openingMechLabPressOnPadToFabricate"), "",
                    Strings.get("openingMechLabBuildAction"), "label heading mech-card-action tone-accent",
                    () -> selectGantry(gantry)));
        }
        return List.copyOf(rows);
    }

    private List<GantryRow> buildGantryRows() {
        revision.get();
        CampaignMechSquad squad = selectedSquad();
        List<GantryRow> rows = new ArrayList<>();
        for (int index = 0; index < physicalGantries; index++) {
            CampaignMech mech = mechAt(squad, index);
            CampaignVehicle vehicle = mech == null ? vehicleAt(squad, index) : null;
            boolean occupied = mech != null || vehicle != null;
            String base = "overview-gantry:" + index;
            int gantry = index;
            rows.add(new GantryRow(base, base + ":station", base + ":name",
                    base + ":detail", mech != null && mech.readiness(day.get()) == CampaignMech.Readiness.INCOMPLETE
                            || vehicle != null && !vehicle.complete()
                            ? "overview-gantry occupied held-back"
                            : occupied ? "overview-gantry occupied" : "overview-gantry vacant",
                    String.format(Strings.locale(), Strings.get("openingMechLabGantryD"), padLetter(index)),
                    mech != null ? mech.displayName() : vehicle != null ? vehicle.displayName()
                            : Strings.get("openingMechLabVacant2"),
                    mech != null && heldBack(mech) ? readinessText(mech)
                            : mech != null ? Strings.join("separatorDot", mech.variant().displayName(), roleLabel(mech.role()))
                            : vehicle != null && !vehicle.complete() ? incompleteText(vehicle)
                            : vehicle != null ? vehicleIdentity(vehicle.type())
                            : Strings.get("openingMechLabPressOnPadToFabricate"),
                    mech != null ? readinessClasses(mech, "label overview-detail")
                            : vehicle != null && !vehicle.complete() ? "label tone-danger overview-detail"
                            : "label tone-muted overview-detail",
                    !occupied, () -> selectGantry(gantry)));
        }
        return List.copyOf(rows);
    }

    private List<FittingMeter> buildFittingMeters() {
        revision.get();
        CampaignMachine machine = selectedMachine();
        if (machine == null) return List.of();
        MachineFigures stock = machine instanceof CampaignMech mech ? stockFigures(mech.variant())
                : stockFigures(((CampaignVehicle) machine).type());
        int seats = machine instanceof CampaignVehicle vehicle ? vehicle.type().capacity : 0;
        return MachineFigures.meters(METER_ID, figures(machine, null, null),
                figures(machine, hoveredInternal.get(), hoveredWeapon.get()), stock, ceiling(), seats(seats));
    }

    /**
     * What a machine would land with as it stands, or with {@code part} or {@code weapon} put on it: the one reading
     * the specifications, the stores' deltas and the slot lines all take.
     */
    private MachineFigures figures(CampaignMachine machine, MechInternal part, MechWeaponComponent weapon) {
        if (machine instanceof CampaignMech mech) {
            MechFrameBudget.Fit fit = mech.fit();
            MechMountSlot mount = mountFor(selectedSlot.get());
            MechFrameBudget.Fit with = part != null ? fit.withInternal(part.role, part)
                    : weapon != null && mount != null ? fit.withWeapon(mount, weapon) : fit;
            return mechFigures(mech.variant(), with, Gaps.of(mech, part));
        }
        CampaignVehicle vehicle = (CampaignVehicle) machine;
        List<MechInternal> rack = new ArrayList<>(vehicle.compartment());
        if (part != null && part.role.compartment()) {
            if (MechCompartment.limit(part.role) == 1) rack.removeIf(fitted -> fitted.role == part.role);
            rack.add(part);
        }
        return vehicleFigures(vehicle.type(),
                part != null && part.role == MechInternal.Role.PLATING ? part : vehicle.plating(),
                part != null && part.role == MechInternal.Role.ENGINE ? part : vehicle.engine(),
                rack, weapon != null ? weapon : standingGun(vehicle));
    }

    /** The prefix of the Mech Lab's bars. */
    private static final String METER_ID = "mech-meter:";

    /** Every bar's full scale in the Mech Lab: the most any mech or vehicle can reach, so they read against each other. */
    private MachineFigures ceiling() {
        MechRefit refit = refit();
        return new MachineFigures(
                Math.max(maximum(MechFrameBudget::refitArmorCapacity) * refit.armorMult(),
                        vehicleMaximum(t -> t.maxArmor) * VehicleType.REFIT_ARMOR_FACTOR),
                Math.max(maximum(v -> v.armorRating),
                        vehicleMaximum(t -> t.armorRating + VehiclePlating.HEAVY.armorRatingBonus(t))),
                Math.max(maximum(v -> v.maxStructure), vehicleMaximum(t -> t.maxStructure)),
                Math.max(maximum(MechFrameBudget::refitMoveSpeed),
                        vehicleMaximum(t -> t.maxSpeed) * VehicleType.REFIT_SPEED_FACTOR),
                maximumFluxCapacity(), maximumDissipation() * refit.dissipationMult(),
                maximumWeaponRange(), maximumMissileTriggers());
    }

    private static MachineFigures.Seats seats(int count) {
        return count > 0 ? MachineFigures.Seats.of(count, Math.round(vehicleMaximum(t -> t.capacity)))
                : MachineFigures.Seats.NONE;
    }

    /** A mech's figures: the fit, less what {@code gaps} says is really not there, under the ship's refit. */
    private MachineFigures mechFigures(MechVariant variant, MechFrameBudget.Fit fit, Gaps gaps) {
        MechRefit refit = refit();
        float armor = gaps.armor(fit) * refit.armorMult();
        return new MachineFigures(armor, armor > 0f ? variant.armorRating : 0f,
                variant.maxStructure * fit.frame().systems().structureFactor(), gaps.speed(fit), gaps.flux(fit),
                gaps.dissipation(fit) * refit.dissipationMult(), weaponRange(fit),
                finiteAmmo(fit.leftShoulder()) + finiteAmmo(fit.rightShoulder()));
    }

    /** The stock frame: the chassis's roll-out weapons on its stock internals. */
    private MachineFigures stockFigures(MechVariant variant) {
        return mechFigures(variant, MechFrameBudget.Fit.stock(variant), new Gaps(false, false, false));
    }

    /**
     * A hull's figures, by the mech's rules: no plate stops nothing and has no rating, no engine moves it nowhere and
     * powers no vents, and no bank holds no flux.
     */
    private static MachineFigures vehicleFigures(VehicleType type, MechInternal plating, MechInternal engine,
                                          List<MechInternal> rack, MechWeaponComponent gun) {
        HullFitEffect fit = HullFitEffect.of(VehiclePlating.of(plating), type).with(MechCompartment.systems(rack));
        float armor = Math.round(type.maxArmor * fit.armorFactor());
        HullReservoir.Frame reservoir = VehicleRack.frame(rack);
        VehicleEngine drive = VehicleEngine.of(engine);
        return new MachineFigures(armor, armor > 0f ? type.armorRating + fit.armorRatingBonus() : 0f,
                Math.round(type.maxStructure * fit.structureFactor()),
                engine == null ? 0f : type.maxSpeed * drive.speedFactor(),
                reservoir != null ? reservoir.capacity() : 0f,
                reservoir == null || engine == null ? 0f : Math.max(0f, reservoir.dissipationPerSecond() + drive.dissipation()),
                gun != null ? gun.weaponDef().range : 0f, finiteAmmo(gun));
    }

    /** The gun at the card; on a hull from an older save, its own until a weapon replaces it. */
    private static MechWeaponComponent standingGun(CampaignVehicle vehicle) {
        return vehicle.gun() != null ? vehicle.gun() : vehicle.hasOwnGun() ? vehicle.type().ownGun() : null;
    }

    /** A hull's stock fit: the mark its bars read against. */
    private static MachineFigures stockFigures(VehicleType type) {
        return vehicleFigures(type, MechInternal.FACTORY_PLATING, MechInternal.INDUSTRIAL_ENGINE,
                VehicleRack.stockFit(type), type.ownGun());
    }

    /**
     * Which required internals a fit is really missing. {@link MechFrameBudget.Fit} fills an
     * empty socket with the stock part, which is right for the budget check and wrong for what
     * the panel reports. The panel leans into the gap: no engine moves the mech nowhere and
     * powers no vents, no plate stops nothing, and no bank holds no flux and so has none to vent.
     */
    private record Gaps(boolean engine, boolean plating, boolean bank) {
        /** The mech's gaps, less the one a hovered part of that role would fill. */
        static Gaps of(CampaignMech mech, MechInternal hovered) {
            boolean engine = mech.internal(MechInternal.Role.ENGINE) == null
                    && (hovered == null || hovered.role != MechInternal.Role.ENGINE);
            boolean bank = mech.internal(MechInternal.Role.CAPACITOR_BANK) == null
                    && (hovered == null || hovered.role != MechInternal.Role.CAPACITOR_BANK);
            boolean plating = mech.internal(MechInternal.Role.PLATING) == null
                    && (hovered == null || hovered.role != MechInternal.Role.PLATING);
            return new Gaps(engine, plating, bank);
        }

        float armor(MechFrameBudget.Fit fit) { return plating ? 0f : fit.armorCapacity(); }

        float speed(MechFrameBudget.Fit fit) { return engine ? 0f : fit.moveSpeed(); }

        float flux(MechFrameBudget.Fit fit) { return bank ? 0f : fit.fluxCapacity(); }

        float dissipation(MechFrameBudget.Fit fit) { return engine || bank ? 0f : fit.dissipationPerSecond(); }
    }

    /** The fit a hovered catalog candidate would produce, or null when nothing is hovered. */
    private MechFrameBudget.Fit candidateFit(CampaignMech mech) {
        MechInternal internal = hoveredInternal.get();
        if (internal != null) return mech.fit().withInternal(internal.role, internal);
        MechWeaponComponent weapon = hoveredWeapon.get();
        MechMountSlot mount = mountFor(selectedSlot.get());
        return weapon != null && mount != null ? mech.fit().withWeapon(mount, weapon) : null;
    }

    private String buildBudgetText() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return "";
        MechFrameBudget.Fit fit = mech.fit();
        // No engine, no output: the stand-in's figure would read as a budget the mech has not got.
        if (mech.internal(MechInternal.Role.ENGINE) == null) {
            return Strings.format("openingMechLabMassBudget", number(fit.fittedMass()), number(0f));
        }
        MechFrameBudget.Fit candidate = candidateFit(mech);
        if (candidate != null && (Math.abs(candidate.fittedMass() - fit.fittedMass()) > 0.01f
                || Math.abs(candidate.output() - fit.output()) > 0.01f)) {
            return Strings.format("openingMechLabMassBudgetPreview", number(fit.fittedMass()),
                    number(fit.output()), number(candidate.fittedMass()), number(candidate.output()));
        }
        return Strings.format(fit.overBudget() ? "openingMechLabMassBudgetOver" : "openingMechLabMassBudget",
                number(fit.fittedMass()), number(fit.output()));
    }

    private static float maximumFluxCapacity() {
        float max = 0f;
        for (MechInternal internal : MechInternal.values()) {
            if (internal.role == MechInternal.Role.CAPACITOR_BANK) max = Math.max(max, internal.fluxCapacity);
        }
        return max;
    }

    /** Best capacitor bank and vent array, with the engine that costs the least flux. */
    private static float maximumDissipation() {
        float capacitors = 0f;
        float ventArray = 0f;
        float engine = Float.NEGATIVE_INFINITY;
        for (MechInternal internal : MechInternal.values()) {
            switch (internal.role) {
                case CAPACITOR_BANK -> capacitors = Math.max(capacitors, internal.dissipation);
                case VENT_ARRAY -> ventArray = Math.max(ventArray, internal.dissipation);
                case ENGINE -> engine = Math.max(engine, internal.dissipation);
                default -> { }
            }
        }
        return capacitors + ventArray + Math.max(0f, engine);
    }

    private List<SlotRow> buildSlots(List<SocketId> slots) {
        revision.get();
        CampaignMachine machine = selectedMachine();
        if (machine == null) return List.of();
        List<MechInternal.Role> lacking = machine.missingInternals();
        List<SlotRow> rows = new ArrayList<>();
        for (SocketId slot : slots) {
            SocketDef definition = socketDefinition(slot);
            if (definition == null) continue;
            MechWeaponComponent weapon = slotWeapon(machine, slot);
            boolean locked = definition.factoryLocked();
            MechInternal.Role role = roleFor(slot);
            MechInternal internal = role != null ? machine.internal(role) : null;
            boolean compartment = slot.compartment();
            // A part the machine cannot go without, left out, keeps it off the drop manifest.
            boolean missing = compartment ? lacking.contains(MechInternal.Role.CAPACITOR_BANK)
                    : role != null && lacking.contains(role);
            boolean armed = weapon != null || internal != null || compartment && !machine.compartment().isEmpty();
            String badge = locked ? Strings.get("openingMechLabLocked") : missing ? Strings.get("mechLabSlotRequired")
                    : FittedBadge.label(armed);
            String badgeClasses = locked ? "slot-badge locked" : missing ? "slot-badge required"
                    : FittedBadge.slotClasses(armed);

            String telemetry;
            if (weapon != null) {
                telemetry = Strings.format("openingMechLabDmgRng", number(weapon.weaponDef().damage), number(weapon.weaponDef().range), (weapon.ammoCapacity > 0 ? Strings.format("openingMechLabAmmo", weapon.readyRounds()) : ""));
            } else if (internal != null) {
                telemetry = internalTelemetry(machine, internal);
            } else if (compartment && !missing) {
                telemetry = compartmentTelemetry(machine);
            } else if (missing) {
                telemetry = Strings.get("mechLabSlotRequiredTelemetry");
            } else {
                telemetry = Strings.get("openingMechLabUnfittedHardpoint");
            }

            boolean canStrip = !locked && (weapon != null || internal != null);
            String stripClasses = canStrip ? "slot-strip-action" : "slot-strip-action hidden";

            String base = "mech-slot:" + slot.name().toLowerCase(Locale.ROOT);
            rows.add(new SlotRow(new FittingSlotCard(base,
                    slot == selectedSlot.get() && !browsingStores.get()
                            ? "doll-slot selected" : "doll-slot",
                    definition.label(), badge, badgeClasses,
                    slotComponent(machine, slot), telemetry, slotType(definition),
                    canStrip, stripClasses,
                    () -> selectSlot(slot),
                    () -> stripWeapon(slot)), slot));
        }
        return List.copyOf(rows);
    }

    private String buildSelectedSlotCopy() {
        CampaignMachine machine = selectedMachine();
        if (machine == null) return fabricatingChassis()
                ? Strings.get("openingMechLabFabricateACompleteStandardFitWalkerHere")
                : Strings.get("openingMechLabNoHeavyAssetSelected");
        if (browsingStores.get()) return Strings.get("openingMechLabAllPartsCopy");
        // A hull has no mass budget and one gun card, so its engine, plate and gun say so.
        boolean hull = machine instanceof CampaignVehicle;
        return switch (selectedSlot.get()) {
            case CORE -> Strings.get(hull ? "openingMechLabVehicleEngineCopy" : "openingMechLabEngineCopy");
            case PLATING -> Strings.get(hull ? "openingMechLabVehiclePlatingCopy" : "openingMechLabPlatingCopy");
            case AUXILIARY, AUXILIARY_2 -> Strings.get(machine.capabilities().armed()
                    ? "openingMechLabCompartmentCopy" : "openingMechLabCompartmentCopyUnarmed");
            case ARMS, LEFT_ARM, RIGHT_ARM, LEFT_SHOULDER, RIGHT_SHOULDER -> hull
                    ? Strings.get("openingMechLabVehicleGunCopy")
                    : slotWeapon(machine, selectedSlot.get()) == null
                            ? Strings.get("openingMechLabEmptyMountFitCompatibleMissileHardwareHere")
                            : Strings.get("openingMechLabInstalledWeaponAssemblySocketTypeAndCapacity");
        };
    }

    private String buildSelectedSlotRule() {
        if (fabricatingChassis()) {
            return Strings.get("openingMechLabPlayerCargoStandardFitVacantGantry");
        }
        if (selectedMachine() != null && browsingStores.get()) return Strings.get("openingMechLabAllPartsRule");
        SocketDef definition = selectedSocketDefinition();
        if (definition == null) return Strings.get("openingMechLabNoSocketDefinition");
        String authority = definition.factoryLocked()
                ? Strings.get("openingMechLabLocked") : Strings.get("openingMechLabOpen");
        return Strings.format("openingMechLabSocketGrid", definition.type().label(), definition.gridColumns(), definition.gridRows(), authority);
    }

    /** The roles of the auxiliary compartment's parts, in the order stores list them. */
    private static final List<MechInternal.Role> COMPARTMENT_ROLES = List.of(MechInternal.Role.CAPACITOR_BANK,
            MechInternal.Role.VENT_ARRAY, MechInternal.Role.LOADER, MechInternal.Role.LINER, MechInternal.Role.READY_RACK,
            MechInternal.Role.DRONE_BAY, MechInternal.Role.ACTIVE_PROTECTION);

    private List<CatalogRow> buildCatalogRows() {
        revision.get();
        if (fabricatingChassis()) return chassisCatalog();
        CampaignMachine machine = selectedMachine();
        if (machine != null && browsingStores.get()) return allPartsCatalog(machine);
        if (selectedSlot.get().compartment() && machine != null) {
            List<CatalogRow> rows = new ArrayList<>();
            for (MechInternal.Role role : COMPARTMENT_ROLES) rows.addAll(internalCatalog(machine, role));
            return List.copyOf(rows);
        }
        MechInternal.Role role = roleFor(selectedSlot.get());
        if (role != null && machine != null) return internalCatalog(machine, role);
        MechMountSlot mount = mountFor(selectedSlot.get());
        if (mount != null && machine != null) return weaponCatalog(machine, mount);
        String base = "mech-catalog:installed:" + selectedSlot.get().name().toLowerCase(Locale.ROOT);
        SocketDef definition = selectedSocketDefinition();
        boolean occupied = slotWeapon(machine, selectedSlot.get()) != null
                || selectedSlot.get() == SocketId.CORE
                || selectedSlot.get().compartment();
        return List.of(catalogRow(base, "catalog-preview hidden", null, null, null, null, "catalog-row selected",
                slotComponent(machine, selectedSlot.get()),
                FittedBadge.label(occupied), FittedBadge.classes(occupied),
                occupied ? Strings.get("openingMechLabInstalledAssembly") : Strings.get("openingMechLabEmptySocket"),
                "", "", "catalog-delta hidden",
                buildSelectedSlotRule(), definition != null && definition.factoryLocked()
                        ? Strings.get("openingMechLabFactoryLocked") : Strings.get("openingMechLabNoCompatibleStock"),
                definition != null && definition.factoryLocked()
                        ? Strings.get("openingMechLabIntegralChassisEquipmentCannotBeReplaced")
                        : Strings.get("openingMechLabNoCompatibleFittingIsAvailableForThis"), true, List.of(), "material-costs hidden", () -> { }));
    }

    /**
     * Every part the bay holds or can build, whichever mount it would go on, narrowed by the
     * chosen category. Weapons drag onto any mount that takes them; internals onto their own.
     */
    private List<CatalogRow> allPartsCatalog(CampaignMachine machine) {
        CategoryFilter filter = effectiveFilter();
        boolean all = filter == CategoryFilter.ALL;
        List<CatalogRow> rows = new ArrayList<>();
        if (all || CategoryFilter.WEAPONS.contains(filter)) rows.addAll(weaponCatalog(machine, null));
        if (all || filter == CategoryFilter.ENGINE) rows.addAll(internalCatalog(machine, MechInternal.Role.ENGINE));
        for (MechInternal.Role role : COMPARTMENT_ROLES) {
            boolean bank = role == MechInternal.Role.CAPACITOR_BANK;
            if (all || filter == (bank ? CategoryFilter.CAPACITORS : CategoryFilter.UTILITY)) {
                rows.addAll(internalCatalog(machine, role));
            }
        }
        if (all || filter == CategoryFilter.PLATING) rows.addAll(internalCatalog(machine, MechInternal.Role.PLATING));
        return List.copyOf(rows);
    }

    /**
     * The weapons that go at one mount, compared against what is fitted there; without a mount, every weapon the machine
     * could take, compared against nothing. A mech's mounts take by size and hardpoint, a hull's gun card by the type and
     * size of the gun its hull was built around.
     */
    private List<CatalogRow> weaponCatalog(CampaignMachine machine, MechMountSlot mount) {
        MechWeaponComponent current = mount != null ? slotWeapon(machine, selectedSlot.get()) : null;
        if (machine instanceof CampaignVehicle vehicle) {
            return weaponRows(current, component -> CampaignVehicle.takesGun(vehicle.type(), component));
        }
        SocketDef socket = mount != null ? selectedSocketDefinition() : null;
        return weaponRows(current, component -> mount == null || socket != null && component.accepts(mount)
                && socket.accommodates(component.footprintColumns, component.footprintRows)
                && (socket.type() == MechFittingLayout.SocketType.OMNI
                || socket.type().name().equals(component.hardpointType.name())));
    }

    private List<CatalogRow> weaponRows(MechWeaponComponent current, Predicate<MechWeaponComponent> fits) {
        List<CatalogRow> rows = new ArrayList<>();
        CategoryFilter filter = effectiveFilter();

        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.weapons()) {
            MechWeaponComponent component = recipe.component();
            // Stores, not the mech: a fitted assembly is on the doll, and a row with no spare
            // appears only while its pattern can build one.
            if (!bay.canFabricate(component) && bay.availableWeapon(component.id) == 0) continue;
            if (!fits.test(component)) continue;

            int free = bay.availableWeapon(component.id);

            // Category filter
            if (filter == CategoryFilter.BALLISTIC && component.hardpointType != MechWeaponComponent.HardpointType.BALLISTIC) continue;
            if (filter == CategoryFilter.MISSILE && component.hardpointType != MechWeaponComponent.HardpointType.MISSILE) continue;
            if (filter == CategoryFilter.ENERGY && component.hardpointType != MechWeaponComponent.HardpointType.ENERGY) continue;

            boolean inCargo = free > 0;
            FabricationCost bill = workshop.weaponQuote(component);
            boolean affordable = bill != null && resources.canBuy(bill);
            boolean locked = !bay.canFabricate(component);
            String badge = FittedBadge.label(component == current);
            String badgeClasses = FittedBadge.classes(component == current);

            // Telemetry
            WeaponDef def = component.weaponDef();
            String telemetry = Strings.format("openingMechLabDmgRng", number(def.damage), number(def.range), (component.ammoCapacity > 0 ? Strings.format("openingMechLabAmmo", component.readyRounds()) : Strings.get("openingMechLabAmmo2")));

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
                    if (!sb.isEmpty()) sb.append(Strings.get("separatorDot"));
                    sb.append(Strings.get("openingMechLabDmg")).append(deltaDmg > 0 ? "+" : "").append(number(deltaDmg));
                }
                if (!sb.isEmpty()) {
                    deltaText = sb.toString();
                    boolean positive = deltaRng >= 0 && deltaDmg >= 0;
                    boolean negative = deltaRng <= 0 && deltaDmg <= 0;
                    deltaClasses = positive ? "catalog-delta positive" : negative ? "catalog-delta negative" : "catalog-delta neutral";
                }
            }

            String base = "mech-catalog:" + component.id;
            // Building is offered only when there is no spare to drag: a stocked row is a part,
            // not an order, and stripping one back to stores takes the offer away again.
            boolean showMaterials = !locked && !inCargo;
            FabricationCost back = inCargo ? workshop.weaponRecovery(component) : null;
            List<Row> materials = showMaterials ? materialRows(base, bill) : recoveryRows(base, back);
            String materialsClasses = showMaterials ? "material-costs" : recoveryClasses(back);

            rows.add(catalogRow(base, "catalog-preview", null, component, null, null,
                    showMaterials ? "catalog-row requires-fabrication" : "catalog-row", component.displayName(),
                    badge, badgeClasses,
                    Strings.format("openingMechLabOwnFree", recipe.provenance(), spareCount(free)),
                    telemetry, deltaText, deltaClasses,
                    Strings.format("openingMechLabGrid", component.hardpointType, component.footprintColumns, component.footprintRows),
                    showMaterials ? Strings.get("openingMechLabFab") : "",
                    affordable ? Strings.get("openingMechLabFabHint")
                            : shortHint(),
                    !showMaterials || !affordable, materials, materialsClasses,
                    () -> fabricateWeapon(component), null,
                    recycleButton(base, back, () -> workshop.recycleWeapon(component), component.displayName())));
        }
        return List.copyOf(rows);
    }

    /*
     * A pattern row reads like a part to buy: its bill and a BUY in the build strip, which builds
     * the machine straight onto the pad. Clicking the card itself previews it there first.
     */
    private List<CatalogRow> chassisCatalog() {
        List<CatalogRow> rows = new ArrayList<>();
        for (MechFabricationCatalog.Recipe recipe : shownPatterns()) {
            MechVariant variant = recipe.variant();
            VehicleType type = recipe.vehicle();
            String base = "mech-catalog:" + recipe.id();
            rows.add(catalogRow(base, "catalog-preview", variant, null, null, type, patternClasses(recipe),
                    recipe.displayName(), "", "catalog-badge hidden",
                    Strings.upper(credit(recipe)),
                    patternStats(recipe), "", "catalog-delta hidden",
                    variant != null ? roleLabel(variant.defaultRole) : patternArmament(type),
                    Strings.get("openingMechLabFab"), buildHint(recipe),
                    !canBuild(recipe), materialRows(base, resources.quote(recipe.cost())), "material-costs",
                    () -> build(recipe), () -> previewRecipeId.set(recipe.id())));
        }
        return List.copyOf(rows);
    }

    /** A pattern's armor and structure, on any machine. */
    private static String patternStats(MechFabricationCatalog.Recipe recipe) {
        MechVariant variant = recipe.variant();
        VehicleType type = recipe.vehicle();
        return Strings.format("openingMechLabFrameStats",
                Math.round(variant != null ? variant.armorCapacity : type.maxArmor),
                Math.round(variant != null ? variant.maxStructure : type.maxStructure));
    }

    private String buildHint(MechFabricationCatalog.Recipe recipe) {
        if (!padHasSpace()) return Strings.get("openingMechLabLanceFull");
        return resources.canBuy(resources.quote(recipe.cost()))
                ? Strings.get("openingMechLabPatternBuyHint")
                : shortHint();
    }

    /**
     * Internals for one role on any machine: a part appears when its pattern is known or a copy is owned, the way
     * weapons do. Room, the engine budget, stores and materials decide whether the row can commit;
     * {@link MechWorkshop#fitInternal} rechecks all of it. A part whose requirements the machine does not meet
     * ({@link MechInternal#unmet}) is listed disabled, its hint saying why.
     */
    private List<CatalogRow> internalCatalog(CampaignMachine machine, MechInternal.Role role) {
        List<CatalogRow> rows = new ArrayList<>();
        MachineCapabilities capabilities = machine.capabilities();
        MechInternal current = machine.internal(role);
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.internals()) {
            MechInternal internal = recipe.internal();
            if (internal.role != role) continue;
            if (!bay.canFabricate(internal) && bay.availableInternal(internal.id) == 0) continue;

            int free = bay.availableInternal(internal.id);
            // Vent arrays stack, so one fitted does not make the row's part "installed".
            boolean installed = current == internal && MechCompartment.limit(role) <= 1;
            boolean inCargo = free > 0;
            boolean locked = !bay.canFabricate(internal);
            boolean affordable = resources.canBuy(resources.quote(recipe.cost()));
            String badge = FittedBadge.label(installed);
            String badgeClasses = FittedBadge.classes(installed);

            Delta delta = installed ? new Delta("", "catalog-delta hidden") : internalDelta(machine, internal);

            // Every part is dragged onto its mount, the armor point's plate as the engine onto the core.
            boolean offerFab = !locked && !inCargo;
            String actionLabel = offerFab ? Strings.get("openingMechLabFab") : "";
            MechInternal.Requirement unmet = internal.unmet(capabilities);
            String actionHint = unmet != null ? unmet.reason()
                    : installed ? Strings.get(role.compartment() ? "openingMechLabPartInCompartment" : "openingMechLabPartOnMachine")
                    : inCargo && !partGoes(machine, internal) ? refusalHint(machine, internal)
                    : inCargo ? Strings.get(role == MechInternal.Role.ENGINE ? "openingMechLabEngineSpareHint"
                            : role == MechInternal.Role.PLATING ? "openingMechLabPlatingSpareHint" : "openingMechLabRackPartSpareHint")
                    : affordable ? Strings.get("openingMechLabFabHint") : shortHint();
            boolean disabled = unmet != null || !offerFab || !affordable;

            String base = "mech-catalog:" + internal.id;
            FabricationCost back = inCargo ? workshop.internalRecovery(internal) : null;
            List<Row> materials = offerFab ? materialRows(base, resources.quote(recipe.cost())) : recoveryRows(base, back);
            rows.add(catalogRow(base, "catalog-preview",
                    null, null, internal, null,
                    offerFab ? "catalog-row requires-fabrication" : "catalog-row", internal.displayName(),
                    badge, badgeClasses,
                    Strings.format("openingMechLabOwnFree", recipe.provenance(), spareCount(free)),
                    internalTelemetry(machine, internal), delta.text(), delta.classes(),
                    partDetail(machine, internal), actionLabel, actionHint,
                    disabled, materials, offerFab ? "material-costs" : recoveryClasses(back),
                    () -> fabricateInternal(internal), null,
                    recycleButton(base, back, () -> workshop.recycleInternal(internal), internal.displayName())));
        }
        return List.copyOf(rows);
    }

    /** Why a spare will not go: a full compartment, or on a mech the engine budget it would break. */
    private String refusalHint(CampaignMachine machine, MechInternal internal) {
        if (machine instanceof CampaignMech mech && bay.canInstallInternal(mech.id(), internal)) {
            return overBudgetHint(mech.fit().withInternal(internal.role, internal));
        }
        return Strings.get("openingMechLabCompartmentFull");
    }

    /** What a part takes up: its room, and on a mech the mass it spends of the engine budget. A hull has no such budget. */
    private static String partDetail(CampaignMachine machine, MechInternal internal) {
        boolean plating = internal.role == MechInternal.Role.PLATING;
        if (machine instanceof CampaignMech mech) {
            String mass = number(MechFrameBudget.mass(internal, mech.variant()));
            return plating ? Strings.format("openingMechLabInternalDetailFrameWide", mass)
                    : Strings.format("openingMechLabInternalDetail", mass, internal.footprintColumns, internal.footprintRows);
        }
        return plating ? Strings.get("openingMechLabPartFrameWide")
                : Strings.format("openingMechLabPartGrid", internal.footprintColumns, internal.footprintRows);
    }

    /** Whether a part on the doll can be lifted off it: any part that is there, on a mech or a vehicle. */
    public boolean liftable(MechInternal internal) {
        return internal != null;
    }

    /**
     * Whether the selected machine can carry a weapon and has none anywhere: it can still go, so this warns in yellow. A
     * hull with no gun card is not unarmed, it is built that way.
     */
    public boolean selectedMachineUnarmed() {
        CampaignVehicle vehicle = selectedVehicle();
        if (vehicle != null) return vehicle.type().hasTurretWeapon() && vehicle.gun() == null && !vehicle.hasOwnGun();
        CampaignMech mech = selectedMech();
        if (mech == null) return false;
        for (MechMountSlot slot : MechMountSlot.values()) {
            if (mech.weaponAt(slot) != null) return false;
        }
        return true;
    }

    /** Whether the selected machine carries a gun, so a compartment with no capacitor bank keeps it off the drop. */
    public boolean selectedMachineArmed() {
        CampaignMachine machine = selectedMachine();
        return machine != null && machine.capabilities().armed();
    }

    /** Whether the selected vehicle's empty gun card still fires the gun its hull was built with (an older save). */
    public boolean selectedVehicleHasOwnGun() {
        CampaignVehicle vehicle = selectedVehicle();
        return vehicle != null && vehicle.hasOwnGun();
    }

    /** The selected vehicle's hull, for the sealed doll; null when a mech or nothing is selected. */
    public VehicleType selectedVehicleType() {
        CampaignVehicle vehicle = selectedVehicle();
        return vehicle != null ? vehicle.type() : null;
    }

    /** Spares in stores, or that the row is a known pattern with none built. */
    /** A spare in stores or a pattern to build; how many spares is the row's x-count. */
    /** Spares in stores as a compact x-count, x0 to x99; a larger store still reads x99. */
    static String spareCount(int free) {
        return Strings.format("openingMechLabSpareCount", Math.max(0, Math.min(99, free)));
    }

    /** A spare's RECYCLE while the machine shop can break it down; null otherwise. */
    private FittingCard.Recycle recycleButton(String cardId, FabricationCost back,
                                              Supplier<MechWorkshop.Result> recycle, String name) {
        if (back == null) return null;
        return new FittingCard.Recycle(cardId.equals(armedRecycle), () -> {
            if (!cardId.equals(armedRecycle)) {
                armedRecycle = cardId;
                feedbackText.set(Strings.format("fittingRecycleReview", name, recoveryLine(back)));
                feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
                revision.update(value -> value + 1);
                return;
            }
            armedRecycle = null;
            MechWorkshop.Result result = recycle.get();
            feedbackText.set(switch (result.status()) {
                case RECYCLED -> Strings.format("fittingRecycled", name);
                case WORKSHOP_IDLE -> Strings.get("fittingRecycleWorkshopIdle");
                default -> Strings.get("openingMechLabRefitBlockedCampaignHardwareWasNotChanged");
            });
            finishWeaponCommand(result);
        });
    }

    /** What a recycle would pay back, as "1,250 credits". */
    private static String recoveryLine(FabricationCost back) {
        return back == null ? "" : CompanyResources.creditAmount(back.credits());
    }

    private static List<Row> recoveryRows(String ownerId, FabricationCost back) {
        return back == null ? List.of() : List.of(CompanyResources.recoveredCredits(ownerId + ":recovered:credits", back.credits()));
    }

    /** Why a BUY is refused: no port to buy at, or not the credits. */
    private String shortHint() {
        return resources.atMarket() ? Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor")
                : Strings.get("fabricationNoPort");
    }

    private static String recoveryClasses(FabricationCost back) {
        return back == null ? "material-costs hidden" : "material-costs recovery";
    }

    private void fabricateWeapon(MechWeaponComponent component) {
        applyFabricated(workshop.fabricateWeapon(component), component.displayName());
    }

    private void fabricateInternal(MechInternal internal) {
        applyFabricated(workshop.fabricateInternal(internal), internal.displayName());
    }

    private void applyFabricated(MechWorkshop.Result result, String name) {
        feedbackText.set(switch (result.status()) {
            case FABRICATED -> Strings.format("openingMechLabFabricatedIntoStores", name);
            case PATTERN_LOCKED -> Strings.get("openingMechLabThisFabricationPatternHasNotBeenAcquired");
            case NO_MARKET -> Strings.get("fabricationNoPort");
            case INSUFFICIENT_MATERIALS -> Strings.get("openingMechLabFabricationBlockedRequiredFleetMaterialsAreShort");
            default -> Strings.get("openingMechLabRefitBlockedCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
    }

    private record Delta(String text, String classes) { }

    /** The role's own figures, signed against what the machine has now; on a mech, the mass and engine output too. */
    private Delta internalDelta(CampaignMachine machine, MechInternal internal) {
        MachineFigures now = figures(machine, null, null);
        MachineFigures with = figures(machine, internal, null);
        Map<String, Float> stats = new LinkedHashMap<>();
        if (machine instanceof CampaignMech mech) {
            MechFrameBudget.Fit current = mech.fit();
            MechFrameBudget.Fit proposed = current.withInternal(internal.role, internal);
            stats.put("openingMechLabDeltaMass", -(proposed.fittedMass() - current.fittedMass()));
            if (internal.role == MechInternal.Role.ENGINE) {
                stats.put("openingMechLabDeltaOutput", proposed.output() - current.output());
            }
        }
        switch (internal.role) {
            case ENGINE -> stats.put("openingMechLabDeltaSpeed", with.speed() - now.speed());
            case PLATING -> stats.put("openingMechLabDeltaArmor", with.armor() - now.armor());
            case CAPACITOR_BANK -> {
                stats.put("openingMechLabDeltaFlux", with.flux() - now.flux());
                stats.put("openingMechLabDeltaDissipation", with.dissipation() - now.dissipation());
            }
            case VENT_ARRAY -> stats.put("openingMechLabDeltaDissipation", with.dissipation() - now.dissipation());
            case LOADER, LINER, READY_RACK, DRONE_BAY, ACTIVE_PROTECTION -> { }
        }
        StringBuilder text = new StringBuilder();
        int gains = 0;
        int losses = 0;
        for (Map.Entry<String, Float> stat : stats.entrySet()) {
            // Mass reads as a cost: more of it is the loss, though it shows with its true sign.
            boolean mass = stat.getKey().equals("openingMechLabDeltaMass");
            float value = stat.getValue();
            if (Math.abs(value) <= 0.01f) continue;
            if (!text.isEmpty()) text.append(Strings.get("separatorDot"));
            text.append(Strings.get(stat.getKey())).append(signed(mass ? -value : value));
            if (value > 0) gains++; else losses++;
        }
        if (text.isEmpty()) return new Delta("", "catalog-delta hidden");
        return new Delta(text.toString(), losses == 0 ? "catalog-delta positive"
                : gains == 0 ? "catalog-delta negative" : "catalog-delta neutral");
    }

    /** Signed, and finer than {@link #number} so a speed step of 0.05 does not read as 0. */
    private static String signed(float value) {
        String text = Math.abs(value) >= 1f ? number(value)
                : String.format(Strings.locale(), "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (value > 0 ? "+" : "") + text;
    }

    /** What one internal makes of the machine it goes on; a bank or vent array, its own figures. */
    private String internalTelemetry(CampaignMachine machine, MechInternal internal) {
        MachineFigures with = figures(machine, internal, null);
        return switch (internal.role) {
            case ENGINE -> machine instanceof CampaignMech mech
                    ? Strings.format("openingMechLabInternalEngineTelemetry",
                            number(mech.fit().withInternal(internal.role, internal).output()), number(with.speed()))
                    : Strings.format("openingMechLabInternalEngineSpeed", number(with.speed()));
            case PLATING -> Strings.format("openingMechLabInternalPlatingTelemetry", Math.round(with.armor()));
            case CAPACITOR_BANK -> Strings.format("openingMechLabInternalCapacitorBankTelemetry",
                    number(internal.fluxCapacity), number(internal.dissipation));
            case VENT_ARRAY -> Strings.format("openingMechLabInternalVentArrayTelemetry",
                    number(internal.dissipation));
            case LOADER -> Strings.format("openingMechLabSrmSLrmS",
                    number(internal.replenisher().srmReplenishmentSeconds()),
                    number(internal.replenisher().lrmReplenishmentSeconds()),
                    number(internal.replenisher().loaderFactor()));
            case LINER -> Strings.format("openingMechLabInternalLinerTelemetry", number(internal.structureFactor()));
            case READY_RACK -> Strings.format("openingMechLabInternalReadyRackTelemetry", number(internal.ammoFactor()));
            case DRONE_BAY -> Strings.format("openingMechLabInternalSensorPairTelemetry", internal.droneBay().size());
            case ACTIVE_PROTECTION -> Strings.format("openingMechLabInternalActiveProtectionTelemetry",
                    number(internal.speedFactor()));
        };
    }

    /**
     * The compartment's fill across every bay and what its parts make of the frame: cells used,
     * flux and venting.
     */
    private String compartmentTelemetry(CampaignMachine machine) {
        int used = 0;
        for (MechInternal part : machine.compartment()) used += part.footprintColumns * part.footprintRows;
        MachineFigures figures = figures(machine, null, null);
        return Strings.format("openingMechLabCompartmentTelemetry", used,
                MechCompartment.capacity(machine.compartmentGrids()),
                number(figures.flux()), number(figures.dissipation()));
    }

    private static String overBudgetHint(MechFrameBudget.Fit proposed) {
        return Strings.format("openingMechLabInternalOverBudgetHint",
                number(proposed.fittedMass()), number(proposed.output()));
    }

    /**
     * The one internal role a socket holds: the engine for the core. Null for weapon mounts and for
     * the compartment, which holds several roles at once.
     */
    static MechInternal.Role roleFor(SocketId socket) {
        return switch (socket) {
            case CORE -> MechInternal.Role.ENGINE;
            case PLATING -> MechInternal.Role.PLATING;
            default -> null;
        };
    }

    public Signal<List<Row>> materialStock() { return materialStock; }

    private static Repair hiddenRepairCard() {
        return new Repair("mech-repair-card surface-dark hidden", "", "", "", "", "mech-repair-detail hidden", "");
    }

    /** A machine ready to go with no plate or no weapon: recommended, not required, so yellow; else no card. */
    private Repair recommendedCard(boolean noPlate) {
        boolean unarmed = selectedMachineUnarmed();
        if (!noPlate && !unarmed) return hiddenRepairCard();
        return new Repair("mech-repair-card surface-dark recommended", "height: 50px;",
                Strings.get(noPlate && unarmed ? "mechLabReadinessNoPlateNoWeapons"
                        : noPlate ? "mechLabReadinessNoPlate" : "mechLabReadinessNoWeapons"),
                "label heading tone-recommended mech-repair-status incomplete", "", "mech-repair-detail hidden", "");
    }

    /** The selected mech's battle damage and its free repair clock, or a hidden card. */
    private Repair buildRepair() {
        revision.get();
        float now = day.get();
        CampaignMachine machine = selectedMachine();
        if (machine == null) return hiddenRepairCard();
        if (!machine.complete()) {
            return new Repair("mech-repair-card surface-dark", "height: 50px;", incompleteText(machine),
                    "label heading tone-danger mech-repair-status incomplete", "", "mech-repair-detail hidden", "");
        }
        // Only a mech carries wear between battles: a hull comes back whole or does not come back.
        if (!(machine instanceof CampaignMech mech) || mech.readiness(now) == CampaignMech.Readiness.READY) {
            return recommendedCard(machine.internal(MechInternal.Role.PLATING) == null);
        }
        String status = readinessText(mech);
        String statusClasses = readinessClasses(mech, "label heading mech-repair-status");
        MechDamage damage = mech.damageOn(now);
        int condition = Math.round((1f - damage.wear(mech.variant().bodyPlan())) * 100f);
        return new Repair("mech-repair-card surface-dark", "height: 110px;", status, statusClasses,
                sectionWear(damage), "mech-repair-detail",
                Strings.format("mechLabRepairCondition", String.valueOf(condition)));
    }

    /** Compact per-section wear: each damaged section and how much of it is gone. */
    private static String sectionWear(MechDamage damage) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<MechSection, Float> entry : damage.wearBySection().entrySet()) {
            if (text.length() > 0) text.append(Strings.get("separatorDot"));
            text.append(Strings.format("mechLabRepairSection",
                    Strings.get("mechLabSection" + entry.getKey().name()),
                    String.valueOf(Math.max(1, Math.round(entry.getValue() * 100f)))));
        }
        return text.toString();
    }

    private boolean heldBack(CampaignMech mech) {
        return mech.readiness(day.get()) != CampaignMech.Readiness.READY;
    }

    /** Why a mech is off the armor-drop manifest, or the gantry prompt when it is not. */
    private String readinessText(CampaignMech mech) {
        float now = day.get();
        return switch (mech.readiness(now)) {
            case UNDER_REPAIR -> Strings.format("mechLabReadinessUnderRepair", tenths(mech.repairDaysRemaining(now)));
            case INCOMPLETE -> Strings.format("mechLabReadinessIncomplete", missingNames(mech.missingInternals()));
            case READY -> Strings.get("openingUiMechLabOpenGantry");
        };
    }

    private String readinessClasses(CampaignMech mech, String base) {
        return switch (mech.readiness(day.get())) {
            case UNDER_REPAIR -> base + " tone-accent";
            case INCOMPLETE -> base + " tone-danger";
            case READY -> base;
        };
    }

    /** The required internals a mech is missing, named for its readiness line. */
    private static String missingNames(List<MechInternal.Role> missing) {
        List<String> names = new ArrayList<>();
        for (MechInternal.Role role : missing) {
            names.add(Strings.get(role == MechInternal.Role.ENGINE ? "mechLabMissingEngine" : "mechLabMissingCapacitors"));
        }
        return String.join(Strings.get("separatorDot"), names);
    }

    /** Why a machine stays off the drop: the parts it lacks. */
    private static String incompleteText(CampaignMachine machine) {
        return Strings.format("mechLabReadinessIncomplete", missingNames(machine.missingInternals()));
    }

    /** Days rounded up to the tenth, so a clock never reads 0.0 while it still holds a mech. */
    private static double tenths(float days) {
        return Math.ceil(days * 10d) / 10d;
    }

    /** The company's credits, and whether the fleet is at a port that will sell. */
    private List<Row> buildMaterialStock() {
        revision.get();
        return List.of(CompanyResources.stock("mech-material-stock:credits", Strings.get("companyResourceCredits"),
                        CompanyResources.CREDITS_ICON, resources.credits()),
                CompanyResources.port("mech-material-stock:port", resources.atMarket()));
    }

    /** A price as its one credits row, against what the company holds. */
    private List<Row> materialRows(String ownerId, FabricationCost cost) {
        if (cost == null) return List.of();
        return CompanyResources.credits(ownerId + ":material:credits", resources.credits(), cost.credits());
    }

    private void selectSquad(String squadId) {
        if (!bay.selectActiveSquad(squadId)) return;
        CampaignMechSquad squad = bay.squadById(squadId);
        selectedSquadId.set(squadId);
        selectedMechId.set(null);
        selectedVehicleId.set(null);
        callSignDraft.set("");
        selectedGantry.set(0);
        selectedSlot.set(SocketId.AUXILIARY);
        fittingFocused.set(false);
        feedbackText.set(Strings.format("openingMechLabIsNowTheActiveMechSupportLance", squad.displayName()));
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        revision.update(value -> value + 1);
        bayChanged.run();
    }

    private void selectMech(String mechId) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.mechById(mechId) == null) return;
        selectedGantry.set(squad.padOf(mechId));
        selectedMechId.set(mechId);
        selectedVehicleId.set(null);
        callSignDraft.set(squad.mechById(mechId).displayName());
        selectedSlot.set(defaultFittingSlot(squad.mechById(mechId)));
        browsingStores.set(true);
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(Strings.format("openingMechLabInspectingNoCampaignHardwareChanged", squad.mechById(mechId).displayName()));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void selectVehicle(String vehicleId) {
        CampaignMechSquad squad = selectedSquad();
        CampaignVehicle vehicle = squad != null ? squad.vehicleById(vehicleId) : null;
        if (vehicle == null) return;
        selectedGantry.set(squad.padOf(vehicleId));
        selectedMechId.set(null);
        selectedVehicleId.set(vehicleId);
        callSignDraft.set(vehicle.displayName());
        selectedSlot.set(SocketId.AUXILIARY);
        browsingStores.set(true);
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(Strings.format("openingMechLabInspectingNoCampaignHardwareChanged", vehicle.displayName()));
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
        CampaignVehicle vehicle = mech == null ? vehicleAt(selectedSquad(), index) : null;
        String occupant = mech != null ? mech.displayName() : vehicle != null ? vehicle.displayName() : null;
        selectedGantry.set(index);
        selectedMechId.set(mech != null ? mech.id() : null);
        selectedVehicleId.set(vehicle != null ? vehicle.id() : null);
        callSignDraft.set(occupant != null ? occupant : "");
        selectedSlot.set(defaultFittingSlot(mech));
        browsingStores.set(occupant != null);
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(occupant != null
                ? Strings.format("openingMechLabGantrySelectedNoCampaignHardwareChanged", padLetter(index), occupant)
                : Strings.format("openingMechLabGantryIsVacantSelectAChassisPattern", padLetter(index)));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void openAssetPicker() {
        assetPickerOpen.set(true);
        fittingFocused.set(false);
        selectedMechId.set(null);
        selectedVehicleId.set(null);
        callSignDraft.set("");
        feedbackText.set(Strings.get("openingMechLabChooseASupportLanceAndAssignedAsset"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void closeAssetPicker() {
        assetPickerOpen.set(false);
        fittingFocused.set(false);
        selectedMechId.set(null);
        selectedVehicleId.set(null);
        callSignDraft.set("");
        feedbackText.set(Strings.get("openingMechLabReturnedToTheLanceOverviewNoCampaign"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    public void selectSlot(SocketId slot) {
        if (selectedMachine() == null) return;
        // A locked card takes no part, so there is nothing there to fit.
        SocketDef def = socketDefinition(slot);
        if (def == null || def.factoryLocked()) return;
        selectedSlot.set(slot);
        browsingStores.set(false);
        fittingFocused.set(true);
        feedbackText.set(Strings.format("openingMechLabSelected", def.label(), (mountFor(slot) != null
                ? Strings.get("openingMechLabCompatibleOwnedAndFabricableWeaponAssembliesAre")
                : Strings.get("openingMechLabInternalPartsReady"))));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    /** Selects the armor point, whose catalog lists the plating the frame can wear. */
    public void selectPlating() {
        selectSlot(SocketId.PLATING);
    }

    private static SocketId defaultFittingSlot(CampaignMech mech) {
        if (mech == null) return SocketId.AUXILIARY;
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        if (layout.hasSocket(SocketId.AUXILIARY)) return SocketId.AUXILIARY;
        for (SocketDef def : layout.sockets()) {
            if (!def.factoryLocked() && mountFor(def.id()) != null) return def.id();
        }
        for (SocketDef def : layout.sockets()) {
            if (!def.factoryLocked()) return def.id();
        }
        return layout.sockets().get(0).id();
    }

    /** Pads are lettered A to D, the stencil on each pad and the label on its card. */
    static String padLetter(int index) {
        return String.valueOf((char) ('A' + Math.max(0, index)));
    }

    private static String padLabel(int index) {
        return String.format(Strings.locale(), Strings.get("openingMechLabGantryD"), padLetter(index));
    }

    private String selectedMachineId() {
        CampaignMachine machine = selectedMachine();
        return machine != null ? machine.id() : null;
    }

    /**
     * SCRAP takes two presses: the first arms it and says what comes back, the second breaks
     * the machine up through the workshop and returns to the lance with its pad vacant.
     */
    /** Attaches the company's reserve hangar: STORE beside SCRAP and the Reserve strip in the lance rail. */
    public void reserve(ReserveHangar hangar, IntSupplier capacity) {
        reserve = hangar;
        reserveCapacity = capacity;
        revision.update(value -> value + 1);
    }

    /** The template's STORE and Reserve strip properties. */
    public void putReserveProps(Map<String, Object> props) {
        props.put("storeLabel", reactor.computed(() -> {
            revision.get();
            String armed = storeArmedId.get();
            return armed != null && armed.equals(selectedMachineId())
                    ? Strings.get("reserveStoreConfirm") : Strings.get("reserveStore");
        }));
        props.put("storeClasses", reactor.computed(() -> {
            revision.get();
            if (!fittingFocused.get() || selectedMachineId() == null || reserve == null
                    || reserveCapacity.getAsInt() <= 0) return "hidden";
            String armed = storeArmedId.get();
            return armed != null && armed.equals(selectedMachineId())
                    ? "reserve-store console-action armed" : "reserve-store console-action";
        }));
        props.put("storeAction", (Runnable) this::storeSelected);
        props.put("reserveClasses", reactor.computed(() -> {
            revision.get();
            return fittingFocused.get() || reserve == null
                    || reserveCapacity.getAsInt() <= 0 && reserve.mechs().isEmpty() && reserve.vehicles().isEmpty()
                    ? "hidden" : "reserve-strip";
        }));
        props.put("reserveTitle", reactor.computed(() -> {
            revision.get();
            return reserve == null ? "" : ReserveRow.title(reserve.stored(), reserveCapacity.getAsInt());
        }));
        props.put("reserveRows", reactor.computed(() -> {
            revision.get();
            selectedSquadId.get();
            return reserveRows();
        }));
    }

    List<ReserveRow> reserveRows() {
        if (reserve == null) return List.of();
        CampaignMechSquad squad = bay.squadById(selectedSquadId.get());
        boolean vacant = squad != null && squad.occupied() < physicalGantries && bay.canAddMech(squad.id());
        List<ReserveRow> rows = new ArrayList<>();
        for (CampaignMech mech : reserve.mechs()) {
            rows.add(new ReserveRow("mech-reserve-" + mech.id(), mech.displayName(), mech.variant().displayName(),
                    vacant, () -> recall(mech.id(), mech.displayName())));
        }
        for (CampaignVehicle vehicle : reserve.vehicles()) {
            rows.add(new ReserveRow("mech-reserve-" + vehicle.id(), vehicle.displayName(),
                    CampaignVehicle.className(vehicle.type()), vacant, () -> recall(vehicle.id(), vehicle.displayName())));
        }
        return rows;
    }

    /**
     * STORE takes two presses, as SCRAP does: the first arms it, the second takes the machine off
     * its gantry into the reserve hangar, fit and repair clock and all, and returns to the lance.
     */
    private void storeSelected() {
        String machineId = selectedMachineId();
        if (machineId == null || reserve == null) return;
        String name = selectedMachine().displayName();
        scrapArmedId.set(null);
        if (!machineId.equals(storeArmedId.get())) {
            storeArmedId.set(machineId);
            feedbackText.set(Strings.format("openingMechLabStoreArmed", name));
            feedbackClasses.set("mech-lab-feedback tone-accent surface-dark");
            return;
        }
        storeArmedId.set(null);
        String pad = padLetter(selectedGantryIndex());
        int capacity = reserveCapacity.getAsInt();
        ReserveHangar.Status status = reserve.storeMachine(bay, machineId, capacity);
        if (status != ReserveHangar.Status.STORED) {
            feedbackText.set(status == ReserveHangar.Status.HANGAR_FULL
                    ? Strings.format("reserveHangarFull", capacity)
                    : Strings.get("openingMechLabRefitBlockedCampaignHardwareWasNotChanged"));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        showLanceOverview();
        feedbackText.set(Strings.format("openingMechLabStored", name, pad));
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        bayChanged.run();
        revision.update(value -> value + 1);
    }

    /** RECALL puts a stored machine on the shown lance's first vacant working gantry. */
    private void recall(String machineId, String name) {
        if (reserve == null) return;
        CampaignMechSquad squad = bay.squadById(selectedSquadId.get());
        ReserveHangar.Status status = squad == null ? ReserveHangar.Status.NO_VACANT_PLACE
                : reserve.recallMachine(bay, machineId, squad.id(), physicalGantries);
        boolean recalled = status == ReserveHangar.Status.RECALLED;
        feedbackText.set(recalled ? Strings.format("openingMechLabRecalled", name, squad.displayName())
                : Strings.format("openingMechLabRecallNoGantry", name));
        feedbackClasses.set(recalled ? "mech-lab-feedback tone-good surface-dark" : "mech-lab-feedback tone-danger surface-dark");
        if (recalled) bayChanged.run();
        revision.update(value -> value + 1);
    }

    private void scrapSelected() {
        String machineId = selectedMachineId();
        if (machineId == null) return;
        storeArmedId.set(null);
        CampaignMech mech = selectedMech();
        String name = mech != null ? mech.displayName() : selectedVehicle().displayName();
        FabricationCost salvage = mech != null ? workshop.mechSalvage(machineId) : workshop.vehicleSalvage(machineId);
        if (!machineId.equals(scrapArmedId.get())) {
            scrapArmedId.set(machineId);
            feedbackText.set(Strings.format("openingMechLabScrapArmed", name, salvageText(salvage)));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        String pad = padLetter(selectedGantryIndex());
        MechWorkshop.Result result = mech != null ? workshop.scrapMech(machineId) : workshop.scrapVehicle(machineId);
        scrapArmedId.set(null);
        if (!result.succeeded()) {
            feedbackText.set(Strings.get("openingMechLabScrapBlocked"));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        showLanceOverview();
        feedbackText.set(Strings.format("openingMechLabScrapped", name, pad, salvageText(salvage)));
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        bayChanged.run();
        revision.update(value -> value + 1);
    }

    private static String salvageText(FabricationCost salvage) {
        return salvage == null ? Strings.get("openingMechLabNoSalvage") : CompanyResources.creditAmount(salvage.credits());
    }

    private void showLanceOverview() {
        assetPickerOpen.set(false);
        fittingFocused.set(false);
        selectedMechId.set(null);
        selectedVehicleId.set(null);
        callSignDraft.set("");
        feedbackText.set(Strings.get("openingMechLabLanceOverviewRestoredSelectAnOccupiedGantry"));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
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
        CampaignMachine machine = selectedMachine();
        if (machine == null || internal == null) return false;
        MechFrameBudget.Fit proposed = machine instanceof CampaignMech mech
                ? mech.fit().withInternal(internal.role, internal) : null;
        MechWorkshop.Result result = workshop.fitInternal(machine.id(), internal);
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
            case NO_MARKET -> Strings.get("fabricationNoPort");
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
        armedRecycle = null;
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
    }

    public void stripWeapon(SocketId slot) {
        // The compartment's parts come out one at a time (removeCompartmentPart), never as a well.
        if (slot != null && slot.compartment()) return;
        if (slot != null && roleFor(slot) != null) {
            removeInstalledInternal(slot);
            return;
        }
        CampaignMachine machine = selectedMachine();
        MechMountSlot mount = mountFor(slot);
        if (machine == null || mount == null) return;
        MechWeaponComponent previous = installedWeapon(slot);
        MechWorkshop.Result result = workshop.stripWeapon(machine.id(), mount);
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

    private void fabricateChassis(MechVariant variant) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.occupied() >= physicalGantries) return;
        int pad = selectedGantryIndex();
        MechWorkshop.Result result = workshop.fabricateChassis(squad.id(), variant);
        CampaignMech fabricated = result.mech();
        if (fabricated != null) {
            // Built on the pad pressed, whichever vacant pad the bay would have reached first.
            squad.standOn(fabricated.id(), pad);
            selectedMechId.set(fabricated.id());
            selectedVehicleId.set(null);
            callSignDraft.set(fabricated.displayName());
            selectedGantry.set(squad.padOf(fabricated.id()));
            selectedSlot.set(fabricated.variant().twoArmed() ? SocketId.LEFT_ARM : SocketId.ARMS);
            fittingFocused.set(true);
        }
        feedbackText.set(switch (result.status()) {
            case CHASSIS_FABRICATED -> Strings.format("openingMechLabFabricatedWithItsStandardRollOutFit", fabricated.displayName());
            case PATTERN_LOCKED -> Strings.get("openingMechLabThisFabricationPatternHasNotBeenAcquired");
            case NO_MARKET -> Strings.get("fabricationNoPort");
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

    /**
     * Builds a vehicle onto the selected vacant gantry through the workshop,
     * which owns the pattern, berth and material refusals.
     */
    boolean fabricateVehicle(VehicleType type) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || type == null) return false;
        int pad = selectedGantryIndex();
        MechWorkshop.Result result = workshop.fabricateVehicle(squad.id(), type);
        CampaignVehicle fabricated = result.vehicle();
        if (fabricated != null) {
            squad.standOn(fabricated.id(), pad);
            selectedMechId.set(null);
            selectedVehicleId.set(fabricated.id());
            callSignDraft.set(fabricated.displayName());
            selectedGantry.set(squad.padOf(fabricated.id()));
            fittingFocused.set(true);
        }
        feedbackText.set(switch (result.status()) {
            case VEHICLE_FABRICATED -> Strings.format("openingMechLabVehicleFabricated", fabricated.displayName());
            case PATTERN_LOCKED -> Strings.get("openingMechLabThisFabricationPatternHasNotBeenAcquired");
            case NO_MARKET -> Strings.get("fabricationNoPort");
            case INSUFFICIENT_MATERIALS -> Strings.get("openingMechLabVehicleFabricationBlockedMaterials");
            case LANCE_FULL -> Strings.get("openingMechLabVehicleFabricationBlockedLanceFull");
            default -> Strings.get("openingMechLabVehicleFabricationBlocked");
        });
        feedbackClasses.set(result.succeeded()
                ? "mech-lab-feedback tone-good surface-dark"
                : "mech-lab-feedback tone-danger surface-dark");
        if (result.succeeded()) bayChanged.run();
        revision.update(value -> value + 1);
        return result.succeeded();
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
        CampaignMachine machine = selectedMachine();
        if (machine == null) return;
        if (!machine.rename(callSignDraft.get())) {
            feedbackText.set(Strings.format("openingMechLabCallSignMustContainCharacters", CampaignMech.CALLSIGN_MAX_LENGTH));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        callSignDraft.set(machine.displayName());
        feedbackText.set(Strings.format("openingMechLabRenamedChassisIdentityFitAndStoresWere", machine.displayName()));
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

    /** The machine being fitted, whichever kind it is; null when neither is selected. */
    private CampaignMachine selectedMachine() {
        CampaignMech mech = selectedMech();
        return mech != null ? mech : selectedVehicle();
    }

    private CampaignVehicle selectedVehicle() {
        if (!fittingFocused.get()) return null;
        CampaignMechSquad squad = selectedSquad();
        String vehicleId = selectedVehicleId.get();
        return squad != null && vehicleId != null ? squad.vehicleById(vehicleId) : null;
    }

    /** Callsign of the selected mech or vehicle, or null when neither is selected. */
    private String selectedAssetName() {
        CampaignMachine machine = selectedMachine();
        return machine != null ? machine.displayName() : null;
    }

    /** True on a vacant gantry, where chassis and vehicle patterns are offered. */
    private boolean fabricatingChassis() {
        if (!fittingFocused.get() || selectedMechId.get() != null
                || selectedVehicleId.get() != null) return false;
        return !gantryOccupied(selectedSquad(), selectedGantryIndex());
    }

    private static boolean gantryOccupied(CampaignMechSquad squad, int index) {
        return squad != null && squad.padHeld(index);
    }

    private static CampaignVehicle vehicleAt(CampaignMechSquad squad, int index) {
        return squad != null ? squad.vehicleOnPad(index) : null;
    }

    private static String vehicleIdentity(VehicleType type) {
        return Strings.format("openingMechLabVehicleIdentity", CampaignVehicle.typeName(type), weightLabel(type));
    }

    /** Weight and armament, for places that already show the vehicle's name above it. */
    /** The weight class's symbol: a weight block sized by class, as in the filter strip. */
    static String weightIcon(MechPattern.WeightClass weight) {
        return "graphics/ui/mech-hardware/weight-" + weight.name().toLowerCase(Locale.ROOT) + ".svg";
    }

    /** The weight class's tone, on a danger ramp: pale, amber, red. */
    static String weightTone(MechPattern.WeightClass weight) {
        return switch (weight) {
            case LIGHT -> "#c9c3a0";
            case MEDIUM -> "#e2a248";
            case HEAVY -> "#d8604c";
        };
    }

    /** A pattern's weight class, or null for anything that is not a chassis or vehicle. */
    static MechPattern.WeightClass weightOf(MechVariant variant, VehicleType vehicle) {
        if (variant != null) return MechPattern.of(variant).weightClass();
        return vehicle != null ? MechPattern.weightClass(vehicle) : null;
    }

    private static String weightLabel(VehicleType type) {
        return switch (MechPattern.weightClass(type)) {
            case LIGHT -> Strings.get("openingMechLabWeightClassLIGHT");
            case MEDIUM -> Strings.get("openingMechLabWeightClassMEDIUM");
            case HEAVY -> Strings.get("openingMechLabWeightClassHEAVY");
        };
    }

    /** A pattern card's armament line: a hull is bought with no gun, so it only says when one could never take a gun. */
    private static String patternArmament(VehicleType type) {
        return type.hasTurretWeapon() ? "" : Strings.get("openingMechLabVehicleUnarmed");
    }

    private static String vehicleArmament(VehicleType type) {
        String turret = turretName(type);
        return turret != null ? Strings.format("openingMechLabVehicleTurret", turret)
                : Strings.get("openingMechLabVehicleUnarmed");
    }

    /** What a campaign hull carries now: the weapon in its gun card, or its own gun only on a hull from an older save. */
    private static String vehicleArmament(CampaignVehicle vehicle) {
        if (vehicle.gun() != null) return Strings.format("openingMechLabVehicleTurret", vehicle.gun().displayName());
        if (vehicle.hasOwnGun() || !vehicle.type().hasTurretWeapon()) return vehicleArmament(vehicle.type());
        return Strings.format("openingMechLabVehicleTurret", Strings.get("vehicleDollNoGun"));
    }

    /** The turret's catalog name; a generic label when the catalog is not loaded. */
    private static String turretName(VehicleType type) {
        return SpecSheets.vehicleGunName(type);
    }

    private static float vehicleMaximum(VehicleMetric metric) {
        float max = 0f;
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.vehicles()) {
            max = Math.max(max, metric.value(recipe.vehicle()));
        }
        return max;
    }

    private static CampaignMech mechAt(CampaignMechSquad squad, int index) {
        return squad != null ? squad.mechOnPad(index) : null;
    }

    private static String slotComponent(CampaignMachine machine, SocketId slot) {
        if (machine == null) return Strings.get("openingMechLabNoAsset");
        return switch (slot) {
            case CORE -> internalName(machine, MechInternal.Role.ENGINE);
            case PLATING -> internalName(machine, MechInternal.Role.PLATING);
            case AUXILIARY, AUXILIARY_2 -> compartmentNames(machine, slot);
            case ARMS, LEFT_ARM, RIGHT_ARM, LEFT_SHOULDER, RIGHT_SHOULDER -> componentName(slotWeapon(machine, slot));
        };
    }

    /** The parts packed into one compartment bay by name, or that it is empty. */
    private static String compartmentNames(CampaignMachine machine, SocketId bay) {
        List<String> names = new ArrayList<>();
        List<MechCompartment.Placement> placed = MechCompartment.pack(machine.compartmentGrids(), machine.compartment());
        for (MechCompartment.Placement part : MechCompartment.in(placed, bay)) names.add(part.part().displayName());
        return names.isEmpty() ? Strings.get("openingMechLabEmptySocket")
                : String.join(Strings.get("separatorDot"), names);
    }

    private static String internalName(CampaignMachine machine, MechInternal.Role role) {
        MechInternal internal = machine.internal(role);
        return internal != null ? internal.displayName()
                : Strings.get(machine.missingInternals().contains(role) ? "mechLabSocketRequired" : "openingMechLabEmptySocket");
    }

    private SocketDef selectedSocketDefinition() {
        return socketDefinition(selectedSlot.get());
    }

    private static String slotType(SocketDef definition) {
        return Strings.format("openingMechLabGrid2", definition.type().label(), definition.gridColumns(), definition.gridRows());
    }

    private static String componentName(MechWeaponComponent component) {
        return component != null ? component.displayName() : Strings.get("openingMechLabEmptyHardpoint");
    }

    /** The missiles a shoulder weapon holds ready, its rack's buffer; zero for one with no limit. */
    private static int finiteAmmo(MechWeaponComponent component) {
        return component != null && component.ammoCapacity > 0 ? component.readyRounds() : 0;
    }

    private static float weaponRange(MechFrameBudget.Fit fit) {
        float max = 0f;
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent component = fit.weaponAt(slot);
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
            case LEFT_ARM -> MechMountSlot.LEFT_ARM;
            case RIGHT_ARM -> MechMountSlot.RIGHT_ARM;
            case LEFT_SHOULDER -> MechMountSlot.LEFT_SHOULDER;
            case RIGHT_SHOULDER -> MechMountSlot.RIGHT_SHOULDER;
            default -> null;
        };
    }

    /** The weapon a socket holds: a mech's at any mount, a hull's in its one gun card. */
    private static MechWeaponComponent slotWeapon(CampaignMachine machine, SocketId slot) {
        MechMountSlot mount = mountFor(slot);
        if (machine == null || mount == null) return null;
        if (machine instanceof CampaignMech mech) return mech.weaponAt(mount);
        return mount == MechMountSlot.ARMS ? ((CampaignVehicle) machine).gun() : null;
    }

    private static String doctrineAdvice(MechRole role) {
        return switch (role) {
            case ARMORED_SUPPORT -> Strings.get("openingMechLabHeavyArmorPointDefenseFire");
            case ASSAULT -> Strings.get("openingMechLabHighMobilityDirectStrikeBursts");
            case LR_SUPPORT -> Strings.get("openingMechLabFireSupportGuidedMissilePacks");
            default -> Strings.get("openingMechLabBalancedTacticalLoadout");
        };
    }

    private List<FittingFilterPill> buildFilterPills() {
        if (!fabricatingChassis() && selectedMachine() == null) return List.of();
        CategoryFilter current = effectiveFilter();
        List<FittingFilterPill> pills = new ArrayList<>();
        for (CategoryFilter f : offeredFilters()) {
            String id = "mech-filter:" + f.name().toLowerCase(Locale.ROOT);
            String classes = f == current ? "catalog-filter-pill selected" : "catalog-filter-pill";
            pills.add(new FittingFilterPill(id, f.label(), f.icon(), f.tone(), classes, () -> setFilter(f)));
        }
        if (fabricatingChassis()) pills.addAll(weightPills());
        return List.copyOf(pills);
    }

    /*
     * A vacant pad filters on two axes at once: what the machine is, and how heavy. The weight
     * symbols are a weight block drawn small, middling and large on a danger ramp, pale to amber
     * to red, because weight is how much a machine can take and deal.
     */
    private List<FittingFilterPill> weightPills() {
        MechPattern.WeightClass current = weightFilter.get();
        List<FittingFilterPill> pills = new ArrayList<>();
        for (MechPattern.WeightClass weight : MechPattern.WeightClass.values()) {
            String name = weight.name().toLowerCase(Locale.ROOT);
            String label = switch (weight) {
                case LIGHT -> Strings.get("openingMechLabWeightClassLIGHT");
                case MEDIUM -> Strings.get("openingMechLabWeightClassMEDIUM");
                case HEAVY -> Strings.get("openingMechLabWeightClassHEAVY");
            };
            pills.add(new FittingFilterPill("mech-weight:" + name, label, weightIcon(weight), weightTone(weight),
                    weight == current ? "catalog-filter-pill selected" : "catalog-filter-pill",
                    () -> setWeightFilter(weight)));
        }
        return pills;
    }

    /** Chooses a weight class, or clears it when it is already chosen. */
    public void setWeightFilter(MechPattern.WeightClass weight) {
        weightFilter.set(weight == weightFilter.get() ? null : weight);
    }

    /**
     * The categories worth offering here: walkers and vehicles on a vacant pad, every part kind
     * while browsing stores, the weapon kinds on a mount that takes more than one, and none where
     * the mount decides the kind.
     */
    private List<CategoryFilter> offeredFilters() {
        if (fabricatingChassis()) return CategoryFilter.PATTERNS;
        if (selectedMachine() == null) return List.of();
        if (browsingStores.get()) return CategoryFilter.PARTS;
        if (mountFor(selectedSlot.get()) == null) return List.of();
        SocketDef socket = selectedSocketDefinition();
        return socket != null && socket.type() == MechFittingLayout.SocketType.OMNI
                ? CategoryFilter.WEAPONS : List.of();
    }

    /** The chosen category where this view offers it; anything else reads as no filter. */
    private CategoryFilter effectiveFilter() {
        CategoryFilter current = catalogFilter.get();
        return offeredFilters().contains(current) ? current : CategoryFilter.ALL;
    }


    private static float maximum(VariantMetric metric) {
        float max = 0f;
        for (MechVariant variant : MechVariant.values()) max = Math.max(max, metric.value(variant));
        return max;
    }

    private static String roleLabel(MechRole role) {
        return Strings.upper(role.displayName());
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) return Integer.toString(Math.round(value));
        return String.format(Strings.locale(), "%.1f", value);
    }

    private interface VariantMetric { float value(MechVariant variant); }

    private interface VehicleMetric { float value(VehicleType type); }

    public record SquadRow(String id, String nameId, String statusId, String strengthId, String slotsId,
                           String classes, String name, String status, String strength,
                           List<LanceSlot> slots, Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "nameId" -> nameId; case "statusId" -> statusId;
            case "strengthId" -> strengthId; case "slotsId" -> slotsId; case "classes" -> classes; case "name" -> name;
            case "status" -> status; case "strength" -> strength; case "slots" -> slots; case "select" -> select;
            default -> throw unknown("mech-squad", p); }; }
    }

    /** One pad of a lance in the picker: the machine on it, or neither for a vacant pad. */
    public record LanceSlot(String id, String letter, CampaignMech mech, CampaignVehicle vehicle)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; default -> throw unknown("mech-lance-slot", p); }; }
    }

    public record MechRow(String id, String nameId, String chassisId, String subsystemId,
                          String actionId, String previewId, String padId, String pad, String letter,
                          CampaignMech mech, CampaignVehicle vehicle,
                          String classes, String name, String chassis, String subsystem,
                          String status, String statusClasses,
                          Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "nameId" -> nameId; case "chassisId" -> chassisId;
            case "subsystemId" -> subsystemId; case "actionId" -> actionId;
            case "previewId" -> previewId; case "padId" -> padId; case "pad" -> pad;
            case "copyId" -> id + ":copy";
            case "classes" -> classes; case "name" -> name;
            case "chassis" -> chassis; case "subsystem" -> subsystem;
            case "status" -> status; case "statusClasses" -> statusClasses; case "select" -> select;
            default -> throw unknown("mech", p); }; }
    }

    public record GantryRow(String id, String stationId, String nameId, String detailId,
                            String classes, String station, String name, String detail,
                            String detailClasses,
                            boolean disabled, Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; case "stationId" -> stationId; case "nameId" -> nameId;
            case "detailId" -> detailId; case "classes" -> classes;
            case "station" -> station; case "name" -> name; case "detail" -> detail;
            case "detailClasses" -> detailClasses;
            case "disabled" -> disabled; case "select" -> select;
            default -> throw unknown("mech-gantry", p); }; }
    }

    /** The selected mech's damage card: readiness, section wear and the condition it drops at. */
    public record Repair(String classes, String style, String status, String statusClasses,
                         String sections, String detailClasses, String condition) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "classes" -> classes; case "style" -> style; case "status" -> status;
            case "statusClasses" -> statusClasses; case "sections" -> sections;
            case "detailClasses" -> detailClasses; case "condition" -> condition;
            default -> throw unknown("mech-repair", p); }; }
    }

    /**
     * Stores categories. They are toggles, drawn as symbols: one chosen narrows the list to it,
     * and choosing it again clears the filter. {@link #ALL} is that cleared state and has no pill.
     */
    public enum CategoryFilter {
        // Each symbol is a part's detail art on its brick colour, the tones the doll draws
        // (HardpointBricks): the art alone is only the dark detail layer.
        ALL(null, null, null),
        BALLISTIC("openingMechLabBallistic", "graphics/ui/mech-hardware/chaingun.svg", "#e2c248"),
        MISSILE("openingMechLabMissile", "graphics/ui/mech-hardware/srm-5.svg", "#6cc45e"),
        ENERGY("openingMechLabEnergy", "graphics/ui/mech-hardware/pl-2-ap.svg", "#e06a3c"),
        ENGINE("openingMechLabFilterEngine", "graphics/ui/mech-hardware/engine-industrial.svg", "#5e96d2"),
        CAPACITORS("openingMechLabFilterCapacitors", "graphics/ui/mech-hardware/capacitor-factory.svg", "#a07edc"),
        UTILITY("openingMechLabFilterUtility", "graphics/ui/mech-hardware/loader-standard.svg", "#4cb4a6"),
        PLATING("openingMechLabFilterPlating", "graphics/ui/mech-hardware/plating-composite.svg", "#a4a8a0"),
        // A vacant pad's patterns, on the plate of a walker and the olive of a hull.
        WALKERS("openingMechLabFilterWalkers", "graphics/ui/mech-hardware/pattern-walker.svg", "#b9b6a2"),
        VEHICLES("openingMechLabFilterVehicles", "graphics/ui/mech-hardware/pattern-vehicle.svg", "#8fa06a");

        private final String labelKey;
        private final String icon;
        private final String tone;
        CategoryFilter(String labelKey, String icon, String tone) {
            this.labelKey = labelKey; this.icon = icon; this.tone = tone;
        }
        public String label() { return labelKey == null ? "" : Strings.get(labelKey); }
        public String icon() { return icon; }
        public String tone() { return tone; }

        static final List<CategoryFilter> WEAPONS = List.of(BALLISTIC, MISSILE, ENERGY);
        static final List<CategoryFilter> PARTS = List.of(BALLISTIC, MISSILE, ENERGY, ENGINE, CAPACITORS, UTILITY, PLATING);
        static final List<CategoryFilter> PATTERNS = List.of(WALKERS, VEHICLES);
    }

    /** One category symbol; {@code label} names it for its hover sheet. */
    /** A socket in the selected-mount list: the kit's slot card, and which socket it is. */
    public record SlotRow(FittingSlotCard card, SocketId socketId) implements MarkupPropertySource {
        public String id() { return card.id(); }
        public String gridId() { return card.gridId(); }
        public String classes() { return card.classes(); }
        public String name() { return card.name(); }
        public String badge() { return card.badge(); }
        public String badgeClasses() { return card.badgeClasses(); }
        public String component() { return card.component(); }
        public String telemetry() { return card.telemetry(); }
        public String type() { return card.type(); }
        public boolean canStrip() { return card.canStrip(); }
        public String stripClasses() { return card.stripClasses(); }
        public Runnable select() { return card.select(); }
        public Runnable stripAction() { return card.stripAction(); }

        @Override public Object markupProperty(String p) { return card.markupProperty(p); }
    }

    /**
     * A part in the stores: the card the fitting kit draws, and which of the lab's things it
     * is, which the screen reads to draw the card's picture and to know what a drag carries.
     */
    public record CatalogRow(FittingCard card, MechVariant chassisPreview,
                             MechWeaponComponent weaponPreview,
                             MechInternal internalPreview, VehicleType vehiclePreview, Runnable select)
            implements MarkupPropertySource {
        public String id() { return card.id(); }
        public String previewId() { return card.previewId(); }
        public String actionId() { return card.actionId(); }
        public String nameId() { return card.id() + ":name"; }
        public String classes() { return card.classes(); }
        public String previewClasses() { return card.previewClasses(); }
        public String name() { return card.name(); }
        public String badge() { return card.badge(); }
        public String badgeClasses() { return card.badgeClasses(); }
        public String stock() { return card.stock(); }
        public String telemetry() { return card.telemetry(); }
        public String delta() { return card.delta(); }
        public String deltaClasses() { return card.deltaClasses(); }
        public String detail() { return card.detail(); }
        public String actionLabel() { return card.actionLabel(); }
        public String actionHint() { return card.actionHint(); }
        public boolean actionDisabled() { return card.actionDisabled(); }
        public List<Row> materials() { return card.materials(); }
        public String materialsClasses() { return card.materialsClasses(); }
        public Runnable action() { return card.action(); }

        /** A chassis or vehicle pattern previews on the pad when its card is clicked. */
        public boolean selectsOnClick() { return select != null; }

        @Override public Object markupProperty(String p) { return card.markupProperty(p); }
    }

    /** A row whose card does nothing when clicked; only its button acts. */
    private static CatalogRow catalogRow(String base, String previewClasses, MechVariant chassis,
                                         MechWeaponComponent weapon,
                                         MechInternal internal, VehicleType vehicle,
                                         String classes, String name, String badge, String badgeClasses,
                                         String stock, String telemetry, String delta, String deltaClasses,
                                         String detail, String actionLabel, String actionHint,
                                         boolean actionDisabled, List<Row> materials, String materialsClasses,
                                         Runnable action) {
        return catalogRow(base, previewClasses, chassis, weapon, internal, vehicle, classes, name,
                badge, badgeClasses, stock, telemetry, delta, deltaClasses, detail, actionLabel, actionHint,
                actionDisabled, materials, materialsClasses, action, null);
    }

    /*
     * A chassis or vehicle's weight class rides on its card as the weight block on the class
     * tone, and its button builds a spare when it reads FAB, which wears the fabricator's cog.
     */
    private static CatalogRow catalogRow(String base, String previewClasses, MechVariant chassis,
                                         MechWeaponComponent weapon,
                                         MechInternal internal, VehicleType vehicle,
                                         String classes, String name, String badge, String badgeClasses,
                                         String stock, String telemetry, String delta, String deltaClasses,
                                         String detail, String actionLabel, String actionHint,
                                         boolean actionDisabled, List<Row> materials, String materialsClasses,
                                         Runnable action, Runnable select) {
        return catalogRow(base, previewClasses, chassis, weapon, internal, vehicle, classes, name,
                badge, badgeClasses, stock, telemetry, delta, deltaClasses, detail, actionLabel, actionHint,
                actionDisabled, materials, materialsClasses, action, select, null);
    }

    private static CatalogRow catalogRow(String base, String previewClasses, MechVariant chassis,
                                         MechWeaponComponent weapon,
                                         MechInternal internal, VehicleType vehicle,
                                         String classes, String name, String badge, String badgeClasses,
                                         String stock, String telemetry, String delta, String deltaClasses,
                                         String detail, String actionLabel, String actionHint,
                                         boolean actionDisabled, List<Row> materials, String materialsClasses,
                                         Runnable action, Runnable select, FittingCard.Recycle recycle) {
        MechPattern.WeightClass weight = weightOf(chassis, vehicle);
        FittingCard card = new FittingCard(base, classes, previewClasses, name, badge, badgeClasses,
                stock, telemetry, delta, deltaClasses, detail, actionLabel, actionHint, actionDisabled,
                Strings.get("openingMechLabFab").equals(actionLabel), materials, materialsClasses, action,
                weight != null ? weightIcon(weight) : null, weight != null ? weightTone(weight) : null, recycle);
        return new CatalogRow(card, chassis, weapon, internal, vehicle, select);
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
