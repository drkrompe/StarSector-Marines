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
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignMechSquad;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.MechPatternCredits;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechFabricationCatalog;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MechWorkshop;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.ops.fitting.FittingCard;
import com.dillon.starsectormarines.ops.fitting.FittingFilterPill;
import com.dillon.starsectormarines.ops.fitting.FittingMeter;
import com.dillon.starsectormarines.ops.fitting.FittingSlotCard;
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
import java.util.function.DoubleSupplier;
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
    /** The pattern selected on a vacant pad: previewed on the pad, built only by BUILD. */
    private final MutableSignal<String> previewRecipeId;
    private final MutableSignal<MechWeaponComponent> hoveredWeapon;
    /** A catalog internal under the pointer, previewed on the meters and the budget. */
    private final MutableSignal<MechInternal> hoveredInternal;
    /** Plating has no socket; while true the catalog lists plating instead of a socket. */
    private final MutableSignal<Boolean> platingSelected;
    /** Stores list every part rather than what fits the selected mount. */
    private final MutableSignal<Boolean> browsingStores;
    private final ComputedSignal<String> budgetText;
    private final ComputedSignal<String> budgetClasses;
    private final ComputedSignal<String> platingText;
    private final ComputedSignal<String> platingClasses;
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
        dismissedLesson = reactor.signal(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedMechId = reactor.signal(null);
        selectedVehicleId = reactor.signal(null);
        selectedGantry = reactor.signal(0);
        selectedSlot = reactor.signal(SocketId.MINI_FAB);
        fittingFocused = reactor.signal(false);
        assetPickerOpen = reactor.signal(false);
        callSignDraft = reactor.signal("");
        feedbackText = reactor.signal(
                Strings.get("openingMechLabSelectAnOccupiedGantryToRefitOr"));
        feedbackClasses = reactor.signal("mech-lab-feedback tone-muted surface-dark");
        catalogFilter = reactor.signal(CategoryFilter.ALL);
        weightFilter = reactor.signal(null);
        scrapArmedId = reactor.signal(null);
        previewRecipeId = reactor.signal(null);
        hoveredWeapon = reactor.signal(null);
        hoveredInternal = reactor.signal(null);
        platingSelected = reactor.signal(false);
        browsingStores = reactor.signal(false);
        categoryFilterPills = reactor.computed(this::buildFilterPills);
        categoryFilterClasses = reactor.computed(() -> categoryFilterPills.get().isEmpty()
                ? "category-filters hidden" : "category-filters");
        // Shown while one mount narrows the list, so the player can get back to every part.
        allPartsClasses = reactor.computed(() -> selectedMech() != null && selectedVehicle() == null
                && !browsingStores.get() ? "catalog-all-parts console-action" : "hidden");
        labSummary = reactor.computed(this::buildLabSummary);
        squadRows = reactor.computed(this::buildSquadRows);
        mechRows = reactor.computed(this::buildMechRows);
        gantryRows = reactor.computed(this::buildGantryRows);
        activeGantryLabel = reactor.computed(() -> {
            int index = selectedGantryIndex();
            boolean occupied = selectedMech() != null || selectedVehicle() != null
                    || gantryOccupied(selectedSquad(), index);
            return String.format(Strings.locale(), Strings.get("openingMechLabGantryDDS"), padLetter(index),
                    occupied ? "" : Strings.get("openingMechLabVacant"));
        });
        selectedMechName = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            CampaignVehicle vehicle = selectedVehicle();
            if (vehicle != null) return vehicle.displayName();
            return mech != null ? mech.displayName() : fabricatingChassis()
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
            if (vehicle != null) return vehicleArmament(vehicle.type());
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
        platingText = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? Strings.format("openingMechLabPlatingRow",
                    mech.internal(MechInternal.Role.PLATING).displayName()) : "";
        });
        shipRefitText = reactor.computed(() -> {
            revision.get();
            return selectedMech() == null ? "" : RefitText.mechLine(shipCondition.get());
        });
        platingClasses = reactor.computed(() -> selectedMech() == null ? "plating-select hidden"
                : platingSelected.get() ? "plating-select console-action selected"
                : "plating-select console-action");
        leftSlotRows = reactor.computed(() -> buildSlots(List.of(
                SocketId.CORE, SocketId.ARMS, SocketId.LEFT_ARM, SocketId.LEFT_SHOULDER)));
        rightSlotRows = reactor.computed(() -> buildSlots(List.of(
                SocketId.RIGHT_ARM, SocketId.RIGHT_SHOULDER, SocketId.CAPACITORS, SocketId.MINI_FAB)));
        slotRows = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return List.of();
            MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
            return buildSlots(layout.sockets().stream().map(SocketDef::id).toList());
        });
        selectedSlotRows = reactor.computed(() -> platingSelected.get() || browsingStores.get() ? List.<SlotRow>of()
                : slotRows.get().stream().filter(row -> row.socketId() == selectedSlot.get()).toList());
        selectedSlotTitle = reactor.computed(() -> {
            if (fabricatingChassis()) return Strings.get("openingMechLabChassisPatterns");
            if (selectedVehicle() != null) return Strings.get("openingMechLabVehicleHull");
            CampaignMech mech = selectedMech();
            if (mech != null && platingSelected.get()) return Strings.get("openingMechLabFramePlating");
            if (mech != null && browsingStores.get()) return Strings.get("openingMechLabAllParts");
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
                ? "garage-back console-action" : "hidden");
        performanceClasses = reactor.computed(() -> fittingFocused.get()
                && (selectedMech() != null || selectedVehicle() != null)
                ? "performance-grid" : "performance-grid hidden");
        catalogClasses = reactor.computed(() -> fittingFocused.get()
                ? "panel catalog-panel" : "panel catalog-panel hidden");
        slotRackClasses = reactor.computed(() -> fittingFocused.get() && selectedMech() != null
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
            if (selectedVehicle() != null) return Strings.get("openingMechLabStatusVehicleFit");
            return Strings.get("openingUiMechLabSelectMountDragToFit");
        });
        dragNoteTitle = reactor.computed(() -> {
            revision.get();
            if (fabricatingChassis()) return Strings.get("openingMechLabBuildAMachine");
            if (selectedVehicle() != null) return Strings.get("openingMechLabVehicleNoteTitle");
            return Strings.get("openingUiMechLabDragHardwareToAMount");
        });
        dragNoteCopy = reactor.computed(() -> {
            revision.get();
            if (fabricatingChassis()) return Strings.get("openingMechLabBuildAMachineCopy");
            if (selectedVehicle() != null) return Strings.get("openingMechLabVehicleNoteCopy");
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

    /** Whether the pad has room and the fleet has the materials for this pattern. */
    private boolean canBuild(MechFabricationCatalog.Recipe recipe) {
        return padHasSpace() && resources.canAfford(resources.quote(recipe.cost()));
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
        props.put("buildKind", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            if (recipe == null) return "";
            return recipe.variant() != null ? chassisKind(recipe.variant()) : vehicleKind(recipe.vehicle());
        }));
        props.put("buildLore", reactor.computed(() -> lore(previewRecipe())));
        props.put("buildWeightIcon", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            MechPattern.WeightClass weight = recipe != null ? weightOf(recipe.variant(), recipe.vehicle()) : null;
            return weight != null ? weightIcon(weight) : weightIcon(MechPattern.WeightClass.LIGHT);
        }));
        props.put("buildWeightClasses", reactor.computed(() -> previewRecipe() != null ? "weight-chip" : "hidden"));
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
            if (recipe.variant() != null) {
                return Strings.format("openingMechLabFrameStats", Math.round(recipe.variant().armorCapacity),
                        Math.round(recipe.variant().maxStructure));
            }
            VehicleType type = recipe.vehicle();
            return Strings.format("openingMechLabVehicleStats", number(type.maxStructure), number(type.maxArmor),
                    number(type.maxSpeed), type.capacity);
        }));
        props.put("buildMaterials", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            return recipe != null ? materialRows("mech-build", resources.quote(recipe.cost())) : List.<Row>of();
        }));
        props.put("buildLabel", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            if (recipe == null) return Strings.get("openingMechLabBuildAction");
            if (!padHasSpace()) return Strings.get("openingMechLabLanceFull");
            return resources.canAfford(resources.quote(recipe.cost()))
                    ? Strings.format("openingMechLabBuildOnPad", padLetter(selectedGantryIndex()))
                    : Strings.get("openingMechLabMaterialsShort");
        }));
        props.put("buildDisabled", reactor.computed(() -> {
            MechFabricationCatalog.Recipe recipe = previewRecipe();
            return recipe == null || !padHasSpace() || !resources.canAfford(resources.quote(recipe.cost()));
        }));
        props.put("buildAction", buildAction());
    }

    public Runnable buildAction() { return this::buildPreviewed; }

    /**
     * The pattern card's bars: a chassis's bare frame read on the specifications' own scales,
     * each against the standard fit's mark so the gap to fill reads as a shortfall, or a
     * vehicle's hull figures.
     */
    private List<FittingMeter> patternMeters(MechFabricationCatalog.Recipe recipe) {
        if (recipe == null) return List.of();
        if (recipe.vehicle() != null) return vehicleMeters(recipe.vehicle(), "build-");
        // A bought chassis is a bare frame: no engine moves it and no bank holds flux, as on a
        // fitted mech with those sockets empty, and only factory-locked guns give it reach.
        MechFrameBudget.Fit frame = MechFrameBudget.Fit.frame(recipe.variant());
        MechFrameBudget.Fit stock = MechFrameBudget.Fit.stock(recipe.variant());
        MechRefit refit = refit();
        float armor = frame.armorCapacity() * refit.armorMult();
        float dissipation = 0f;
        float range = weaponRange(frame);
        int missiles = finiteAmmo(frame.leftShoulder()) + finiteAmmo(frame.rightShoulder());
        return List.of(
                meter("build-armor", Strings.get("openingMechLabArmor"), Strings.format("openingMechLabPlate", Math.round(armor)),
                        armor, maximum(MechFrameBudget::refitArmorCapacity) * refit.armorMult(), armor, armor, FittingMeter.PLATE_TONE),
                meter("build-mobility", Strings.get("openingMechLabMobility"), Strings.format("openingMechLabCellsS", number(0f)),
                        0f, maximum(MechFrameBudget::refitMoveSpeed), 0f, stock.moveSpeed(), FittingMeter.ENGINE_TONE),
                meter("build-flux", Strings.get("openingMechLabCapacitorBank"), Strings.format("openingMechLabFluxValue", number(0f)),
                        0f, maximumFluxCapacity(), 0f, stock.fluxCapacity(), FittingMeter.FLUX_TONE),
                meter("build-dissipation", Strings.get("openingMechLabDissipation"), Strings.format("openingMechLabDissipationValue", number(dissipation)),
                        dissipation, maximumDissipation() * refit.dissipationMult(), dissipation,
                        stock.dissipationPerSecond() * refit.dissipationMult(), FittingMeter.FLUX_TONE),
                meter("build-range", Strings.get("openingMechLabMaxRange"), Strings.format("openingMechLabCells", number(range)),
                        range, maximumWeaponRange(), range, weaponRange(stock), FittingMeter.WEAPON_TONE),
                meter("build-endurance", Strings.get("openingMechLabMissiles"), Strings.format("openingMechLabTriggers", missiles),
                        missiles, maximumMissileTriggers(), missiles,
                        finiteAmmo(stock.leftShoulder()) + finiteAmmo(stock.rightShoulder()), FittingMeter.MISSILE_TONE));
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

    /** Kind and role; the weight class is the weight block drawn beside it, not a word. */
    private static String chassisKind(MechVariant variant) {
        return Strings.join("separatorDot", Strings.get("openingMechLabChassis"), roleLabel(variant.defaultRole));
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
        if (selectedMech() == null) return;
        browsingStores.set(true);
        platingSelected.set(false);
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
    public Signal<String> platingText() { return platingText; }
    public Signal<String> platingClasses() { return platingClasses; }
    /** The ship's Mech Lab refits as the selected mech will deploy with them; empty when neutral. */
    public Signal<String> shipRefitText() { return shipRefitText; }
    public Runnable selectPlatingAction() { return this::selectPlating; }
    public boolean platingSelected() { return platingSelected.get(); }

    /** Fitting-budget and plating bindings, shared by the screen and its evidence. */
    public void putInternalProps(Map<String, Object> props) {
        props.put("budgetText", budgetText);
        props.put("budgetClasses", budgetClasses);
        props.put("platingText", platingText);
        props.put("platingClasses", platingClasses);
        props.put("shipRefitText", shipRefitText);
        props.put("selectPlating", selectPlatingAction());
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

    /** Flux reservoir a weapon sheet reads against: the selected mech's, or the stock one. */
    public float selectedFluxCapacity() {
        MechFrameBudget.Fit fit = selectedFit();
        return fit != null ? fit.fluxCapacity() : MechInternal.FACTORY_CAPACITOR_BANK.fluxCapacity;
    }

    /** Dissipation a weapon sheet reads against: the selected mech's, or the stock frame's. */
    public float selectedDissipationPerSecond() {
        MechFrameBudget.Fit fit = selectedFit();
        float base = fit != null ? fit.dissipationPerSecond() : MechInternal.FACTORY_CAPACITOR_BANK.dissipation;
        return base * refit().dissipationMult();
    }

    private MechRefit refit() {
        return shipCondition.get().mechRefit();
    }

    /**
     * The internal shown in one socket well: the engine, the capacitor bank, or the vent array
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
    public SocketId selectedSocket() { return platingSelected.get() || browsingStores.get() ? null : selectedSlot.get(); }

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
                || (id == SocketId.CORE || id == SocketId.CAPACITORS) && installedInternal(id) != null
                || id == SocketId.MINI_FAB);
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
        CampaignMech mech = selectedMech();
        MechMountSlot mount = mountFor(target);
        if (mech == null || component == null || mount == null
                || bay.availableWeapon(component.id) <= 0
                || mech.weaponAt(mount) == component
                || !bay.canInstallWeapon(mech.id(), mount, component.id)) return false;
        return bay.withinBudget(mech.id(), mech.fit().withWeapon(mount, component));
    }

    /** True when the bay holds a spare of this part, so its row can be picked up. */
    public boolean inStores(MechWeaponComponent component) {
        return component != null && bay.availableWeapon(component.id) > 0;
    }

    public boolean inStores(MechInternal internal) {
        return internal != null && bay.availableInternal(internal.id) > 0;
    }

    public boolean inStores(MissileReplenisherComponent loader) {
        return loader != null && bay.availableReplenisher(loader.id()) > 0;
    }

    /** An internal drops only on its own role's mount, from a spare, within the engine budget. */
    public boolean canDropCatalogInternal(MechInternal internal, SocketId target) {
        CampaignMech mech = selectedMech();
        if (mech == null || internal == null || target == null || internal.role.socket() != target
                || mech.internal(internal.role) == internal || !inStores(internal)
                || !bay.canInstallInternal(mech.id(), internal)) return false;
        return bay.withinBudget(mech.id(), mech.fit().withInternal(internal.role, internal));
    }

    public boolean dropCatalogInternal(MechInternal internal, SocketId target) {
        if (!canDropCatalogInternal(internal, target)) return false;
        selectedSlot.set(target);
        platingSelected.set(false);
        return fitInternal(internal);
    }

    /** A loader drops only on the mini-fab, from a spare, and not over a fitted vent array. */
    public boolean canDropCatalogLoader(MissileReplenisherComponent loader, SocketId target) {
        CampaignMech mech = selectedMech();
        if (mech == null || loader == null || target != SocketId.MINI_FAB || !inStores(loader)
                || loader.id().equals(mech.missileReplenisherId())) return false;
        return mech.internal(MechInternal.Role.VENT_ARRAY) == null
                || MissileReplenisherComponent.STANDARD.id().equals(loader.id());
    }

    public boolean dropCatalogLoader(MissileReplenisherComponent loader, SocketId target) {
        if (!canDropCatalogLoader(loader, target)) return false;
        selectedSlot.set(target);
        platingSelected.set(false);
        install(loader.id());
        return true;
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
        if (mech == null || component == null || mount == null
                || bay.availableWeapon(component.id) <= 0) return false;
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

    /**
     * Drags an internal off the doll back to stores, leaving its socket empty. An empty engine
     * or capacitor socket is allowed: the mech reads {@link CampaignMech.Readiness#INCOMPLETE}
     * and stays off the drop manifest until one is fitted.
     */
    public boolean removeInstalledInternal(SocketId source) {
        CampaignMech mech = selectedMech();
        MechInternal.Role role = source != null ? roleFor(source) : null;
        if (mech == null || role == null) return false;
        selectedSlot.set(source);
        platingSelected.set(false);
        if (role == MechInternal.Role.VENT_ARRAY) return stripVentArray();
        MechInternal previous = mech.internal(role);
        MechWorkshop.Result result = workshop.stripInternal(mech.id(), role);
        feedbackText.set(switch (result.status()) {
            case STRIPPED -> Strings.format("openingMechLabInternalStripped", previous.displayName());
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

    /** Gantries in the selected lance holding a mech or a vehicle; the rest are vacant pads. */
    public int occupiedGantries() {
        revision.get();
        CampaignMechSquad squad = bay.squadById(selectedSquadId.get());
        return squad != null ? squad.occupied() : 0;
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
                        mech, vehicle != null ? vehicle.type() : null));
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
        int pad = 0;
        for (CampaignMech mech : squad.mechs()) {
            String base = "mech:" + mech.id();
            rows.add(new MechRow(base, base + ":name", base + ":chassis", base + ":subsystem",
                    base + ":action", base + ":preview", base + ":pad", padLabel(pad), padLetter(pad++), mech, null,
                    mech.id().equals(selected) ? "mech-row selected" : "mech-row",
                    mech.displayName(), Strings.join("separatorDot", mech.variant().displayName(), roleLabel(mech.role())),
                    mech.missileReplenisher().displayName(), readinessText(mech),
                    readinessClasses(mech, "label heading mech-card-action"),
                    () -> selectMech(mech.id())));
        }
        String selectedVehicle = selectedVehicleId.get();
        for (CampaignVehicle vehicle : squad.vehicles()) {
            String base = "vehicle:" + vehicle.id();
            rows.add(new MechRow(base, base + ":name", base + ":chassis", base + ":subsystem",
                    base + ":action", base + ":preview", base + ":pad", padLabel(pad), padLetter(pad++), null, vehicle.type(),
                    vehicle.id().equals(selectedVehicle) ? "mech-row selected" : "mech-row",
                    vehicle.displayName(), vehicleIdentity(vehicle.type()),
                    vehicleArmament(vehicle.type()), Strings.get("openingUiMechLabOpenGantry"),
                    "label heading mech-card-action", () -> selectVehicle(vehicle.id())));
        }
        for (int index = pad; index < physicalGantries; index++) {
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
                    base + ":detail", mech != null && heldBack(mech)
                            ? "overview-gantry occupied held-back"
                            : occupied ? "overview-gantry occupied" : "overview-gantry vacant",
                    String.format(Strings.locale(), Strings.get("openingMechLabGantryD"), padLetter(index)),
                    mech != null ? mech.displayName() : vehicle != null ? vehicle.displayName()
                            : Strings.get("openingMechLabVacant2"),
                    mech != null && heldBack(mech) ? readinessText(mech)
                            : mech != null ? Strings.join("separatorDot", mech.variant().displayName(), roleLabel(mech.role()))
                            : vehicle != null ? vehicleIdentity(vehicle.type())
                            : Strings.get("openingMechLabPressOnPadToFabricate"),
                    mech != null ? readinessClasses(mech, "label overview-detail")
                            : "label tone-muted overview-detail",
                    !occupied, () -> selectGantry(gantry)));
        }
        return List.copyOf(rows);
    }

    private List<FittingMeter> buildFittingMeters() {
        revision.get();
        CampaignVehicle vehicle = selectedVehicle();
        if (vehicle != null) return vehicleMeters(vehicle.type(), "");
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
        // An empty engine or capacitor socket stands in as the stock part for the budget check
        // only; the panel reads its figures as zero. A hovered part of that role fills the ghost.
        Gaps now = Gaps.of(mech, null);
        Gaps then = Gaps.of(mech, hoveredInternal.get());
        // What the mech deploys on: the armor shop's plate and the coolant plant over the fit.
        MechRefit refit = refit();
        float armor = fit.armorCapacity() * refit.armorMult();
        float speed = now.speed(fit);
        float flux = now.flux(fit);
        float dissipation = now.dissipation(fit) * refit.dissipationMult();
        // The stock frame: the chassis's roll-out weapons on its stock internals. A bar shows that
        // much in its part's shade and what the fit adds past it (or falls short of it) beyond.
        MechVariant variant = mech.variant();
        MechFrameBudget.Fit stock = MechFrameBudget.Fit.stock(variant);

        return List.of(
                meter("armor", Strings.get("openingMechLabArmor"), Strings.format("openingMechLabPlate", Math.round(armor)),
                        armor, maximum(MechFrameBudget::refitArmorCapacity) * refit.armorMult(),
                        ghost.armorCapacity() * refit.armorMult(), stock.armorCapacity() * refit.armorMult(), FittingMeter.PLATE_TONE),
                meter("mobility", Strings.get("openingMechLabMobility"), Strings.format("openingMechLabCellsS", number(speed)),
                        speed, maximum(MechFrameBudget::refitMoveSpeed), then.speed(ghost),
                        stock.moveSpeed(), FittingMeter.ENGINE_TONE),
                meter("flux", Strings.get("openingMechLabCapacitorBank"), Strings.format("openingMechLabFluxValue", number(flux)),
                        flux, maximumFluxCapacity(), then.flux(ghost), stock.fluxCapacity(), FittingMeter.FLUX_TONE),
                meter("dissipation", Strings.get("openingMechLabDissipation"), Strings.format("openingMechLabDissipationValue", number(dissipation)),
                        dissipation, maximumDissipation() * refit.dissipationMult(),
                        then.dissipation(ghost) * refit.dissipationMult(),
                        stock.dissipationPerSecond() * refit.dissipationMult(), FittingMeter.FLUX_TONE),
                meter("range", Strings.get("openingMechLabMaxRange"), Strings.format("openingMechLabCells", number(currentRange)),
                        currentRange, maximumWeaponRange(), ghostRange, weaponRange(stock), FittingMeter.WEAPON_TONE),
                meter("endurance", Strings.get("openingMechLabMissiles"), Strings.format("openingMechLabTriggers", currentMissiles),
                        currentMissiles, maximumMissileTriggers(), ghostMissiles,
                        finiteAmmo(stock.leftShoulder()) + finiteAmmo(stock.rightShoulder()), FittingMeter.MISSILE_TONE));
    }

    /**
     * Which required internals a fit is really missing. {@link MechFrameBudget.Fit} fills an
     * empty socket with the stock part, which is right for the budget check and wrong for what
     * the panel reports. The panel leans into the gap: no engine moves the mech nowhere and
     * powers no vents, and no bank holds no flux and so has none to vent.
     */
    private record Gaps(boolean engine, boolean bank) {
        /** The mech's gaps, less the one a hovered part of that role would fill. */
        static Gaps of(CampaignMech mech, MechInternal hovered) {
            boolean engine = mech.internal(MechInternal.Role.ENGINE) == null
                    && (hovered == null || hovered.role != MechInternal.Role.ENGINE);
            boolean bank = mech.internal(MechInternal.Role.CAPACITOR_BANK) == null
                    && (hovered == null || hovered.role != MechInternal.Role.CAPACITOR_BANK);
            return new Gaps(engine, bank);
        }

        float speed(MechFrameBudget.Fit fit) { return engine ? 0f : fit.moveSpeed(); }

        float flux(MechFrameBudget.Fit fit) { return bank ? 0f : fit.fluxCapacity(); }

        float dissipation(MechFrameBudget.Fit fit) { return engine || bank ? 0f : fit.dissipationPerSecond(); }
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
            // An empty engine or capacitor well keeps the mech off the drop manifest.
            boolean missing = internalSocket && internal == null && slot != SocketId.MINI_FAB;
            boolean armed = weapon != null || internalSocket && !missing;
            String badge = locked ? Strings.get("openingMechLabLocked") : missing ? Strings.get("mechLabSlotRequired")
                    : internalSocket ? Strings.get("openingMechLabFitted") : armed ? Strings.get("openingMechLabArmed") : Strings.get("openingMechLabEmpty");
            String badgeClasses = locked ? "slot-badge locked" : missing ? "slot-badge required"
                    : armed ? "slot-badge armed" : "slot-badge empty";

            String telemetry;
            if (weapon != null) {
                telemetry = Strings.format("openingMechLabDmgRng", number(weapon.weaponDef().damage), number(weapon.weaponDef().range), (weapon.ammoCapacity > 0 ? Strings.format("openingMechLabAmmo", weapon.ammoCapacity) : ""));
            } else if (internal != null) {
                telemetry = internalTelemetry(mech.fit(), internal);
            } else if (slot == SocketId.MINI_FAB) {
                telemetry = Strings.format("openingMechLabSrmSLrmS", number(mech.missileReplenisher().srmReplenishmentSeconds()), number(mech.missileReplenisher().lrmReplenishmentSeconds()));
            } else if (missing) {
                telemetry = Strings.get("mechLabSlotRequiredTelemetry");
            } else {
                telemetry = Strings.get("openingMechLabUnfittedHardpoint");
            }

            boolean canStrip = !locked && (weapon != null || internal != null);
            String stripClasses = canStrip ? "slot-strip-action" : "slot-strip-action hidden";

            String base = "mech-slot:" + slot.name().toLowerCase(Locale.ROOT);
            rows.add(new SlotRow(new FittingSlotCard(base,
                    slot == selectedSlot.get() && !platingSelected.get() && !browsingStores.get()
                            ? "doll-slot selected" : "doll-slot",
                    definition.label(), badge, badgeClasses,
                    slotComponent(mech, slot), telemetry, slotType(definition),
                    canStrip, stripClasses,
                    () -> selectSlot(slot),
                    () -> stripWeapon(slot)), slot));
        }
        return List.copyOf(rows);
    }

    private String buildSelectedSlotCopy() {
        CampaignVehicle vehicle = selectedVehicle();
        if (vehicle != null) return vehicleSummary(vehicle.type());
        CampaignMech mech = selectedMech();
        if (mech == null) return fabricatingChassis()
                ? Strings.get("openingMechLabFabricateACompleteStandardFitWalkerHere")
                : Strings.get("openingMechLabNoHeavyAssetSelected");
        if (platingSelected.get()) return Strings.get("openingMechLabPlatingCopy");
        if (browsingStores.get()) return Strings.get("openingMechLabAllPartsCopy");
        return switch (selectedSlot.get()) {
            case MINI_FAB -> Strings.get("openingMechLabUtilityCopy");
            case CORE -> Strings.get("openingMechLabEngineCopy");
            case CAPACITORS -> Strings.get("openingMechLabCapacitorBankCopy");
            case ARMS, LEFT_ARM, RIGHT_ARM, LEFT_SHOULDER, RIGHT_SHOULDER ->
                    slotComponent(mech, selectedSlot.get()).equals(Strings.get("openingMechLabEmptyHardpoint"))
                            ? Strings.get("openingMechLabEmptyMountFitCompatibleMissileHardwareHere")
                            : Strings.get("openingMechLabInstalledWeaponAssemblySocketTypeAndCapacity");
        };
    }

    private String buildSelectedSlotRule() {
        if (fabricatingChassis()) {
            return Strings.get("openingMechLabPlayerCargoStandardFitVacantGantry");
        }
        if (selectedVehicle() != null) return Strings.get("openingMechLabVehicleNoFitting");
        if (selectedMech() != null && platingSelected.get()) return Strings.get("openingMechLabPlatingRule");
        if (selectedMech() != null && browsingStores.get()) return Strings.get("openingMechLabAllPartsRule");
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
        // A vehicle's hull and turret are its type's: nothing on it is fitted.
        if (selectedVehicle() != null) return List.of();
        if (mech != null && platingSelected.get()) return internalCatalog(mech, MechInternal.Role.PLATING);
        if (mech != null && browsingStores.get()) return allPartsCatalog(mech);
        if (selectedSlot.get() == SocketId.MINI_FAB) {
            List<CatalogRow> rows = new ArrayList<>(replenisherCatalog(mech));
            if (mech != null) rows.addAll(internalCatalog(mech, MechInternal.Role.VENT_ARRAY));
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
                || selectedSlot.get() == SocketId.CAPACITORS;
        return List.of(catalogRow(base, "catalog-preview hidden", null, null, null, null, null, "catalog-row selected",
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

    /**
     * Every part the bay holds or can build, whichever mount it would go on, narrowed by the
     * chosen category. Weapons drag onto any mount that takes them; internals onto their own.
     */
    private List<CatalogRow> allPartsCatalog(CampaignMech mech) {
        CategoryFilter filter = effectiveFilter();
        boolean all = filter == CategoryFilter.ALL;
        List<CatalogRow> rows = new ArrayList<>();
        if (all || CategoryFilter.WEAPONS.contains(filter)) rows.addAll(weaponCatalog(mech, null));
        if (all || filter == CategoryFilter.ENGINE) rows.addAll(internalCatalog(mech, MechInternal.Role.ENGINE));
        if (all || filter == CategoryFilter.CAPACITORS) {
            rows.addAll(internalCatalog(mech, MechInternal.Role.CAPACITOR_BANK));
        }
        if (all || filter == CategoryFilter.UTILITY) {
            rows.addAll(replenisherCatalog(mech));
            rows.addAll(internalCatalog(mech, MechInternal.Role.VENT_ARRAY));
        }
        return List.copyOf(rows);
    }

    private List<CatalogRow> weaponCatalog(CampaignMech mech, MechMountSlot mount) {
        List<CatalogRow> rows = new ArrayList<>();
        CategoryFilter filter = effectiveFilter();
        // Without a mount the list is every weapon in stores, compared against nothing.
        MechWeaponComponent current = mount != null ? slotWeapon(mech, selectedSlot.get()) : null;

        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.weapons()) {
            MechWeaponComponent component = recipe.component();
            // Stores, not the mech: a fitted assembly is on the doll, and a row with no spare
            // appears only while its pattern can build one.
            if (!bay.canFabricate(component) && bay.availableWeapon(component.id) == 0) continue;
            SocketDef socket = mount != null ? selectedSocketDefinition() : null;
            if (mount != null && (socket == null || !component.accepts(mount)
                    || socket.type() != MechFittingLayout.SocketType.OMNI
                    && !socket.type().name().equals(component.hardpointType.name()))) continue;

            int free = bay.availableWeapon(component.id);

            // Category filter
            if (filter == CategoryFilter.BALLISTIC && component.hardpointType != MechWeaponComponent.HardpointType.BALLISTIC) continue;
            if (filter == CategoryFilter.MISSILE && component.hardpointType != MechWeaponComponent.HardpointType.MISSILE) continue;
            if (filter == CategoryFilter.ENERGY && component.hardpointType != MechWeaponComponent.HardpointType.ENERGY) continue;

            boolean inCargo = free > 0;
            FabricationCost bill = workshop.weaponQuote(component);
            boolean affordable = bill != null && resources.canAfford(bill);
            boolean locked = !bay.canFabricate(component);
            String badge = stockBadge(free);
            String badgeClasses = inCargo ? "catalog-badge in-cargo" : "catalog-badge fabrication";

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
            List<Row> materials = showMaterials ? materialRows(base, bill) : List.of();
            String materialsClasses = showMaterials ? "material-costs" : "material-costs hidden";

            rows.add(catalogRow(base, "catalog-preview", null, component, null, null, null,
                    showMaterials ? "catalog-row requires-fabrication" : "catalog-row", component.displayName(),
                    badge, badgeClasses,
                    Strings.format("openingMechLabOwnFree", recipe.provenance(), spareCount(free)),
                    telemetry, deltaText, deltaClasses,
                    Strings.format("openingMechLabGrid", component.hardpointType, component.footprintColumns, component.footprintRows),
                    showMaterials ? Strings.get("openingMechLabFab") : "",
                    affordable ? Strings.get("openingMechLabFabHint")
                            : Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor"),
                    !showMaterials || !affordable, materials, materialsClasses,
                    () -> fabricateWeapon(component)));
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
            if (recipe.variant() == null) continue;
            MechVariant variant = recipe.variant();
            String base = "mech-catalog:" + recipe.id();
            rows.add(catalogRow(base, "catalog-preview", variant, null, null, null, null, patternClasses(recipe),
                    recipe.displayName(), Strings.get("openingMechLabChassis"), "catalog-badge fabrication",
                    credit(recipe),
                    Strings.format("openingMechLabFrameStats", Math.round(variant.armorCapacity), Math.round(variant.maxStructure)),
                    "", "catalog-delta hidden",
                    chassisKind(variant),
                    Strings.get("openingMechLabFab"), buildHint(recipe),
                    !canBuild(recipe), materialRows(base, resources.quote(recipe.cost())), "material-costs",
                    () -> build(recipe), () -> previewRecipeId.set(recipe.id())));
        }
        rows.addAll(vehicleCatalog());
        return List.copyOf(rows);
    }

    private String buildHint(MechFabricationCatalog.Recipe recipe) {
        if (!padHasSpace()) return Strings.get("openingMechLabLanceFull");
        return resources.canAfford(resources.quote(recipe.cost()))
                ? Strings.get("openingMechLabPatternBuyHint")
                : Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor");
    }

    /** Vehicle patterns share the vacant gantry with chassis, gated by pattern the same way. */
    private List<CatalogRow> vehicleCatalog() {
        List<CatalogRow> rows = new ArrayList<>();
        for (MechFabricationCatalog.Recipe recipe : shownPatterns()) {
            VehicleType type = recipe.vehicle();
            if (type == null) continue;
            String base = "mech-catalog:" + recipe.id();
            rows.add(catalogRow(base, "catalog-preview", null, null, null, null, type, patternClasses(recipe),
                    recipe.displayName(), Strings.get("openingMechLabVehicle"), "catalog-badge fabrication",
                    credit(recipe),
                    Strings.format("openingMechLabSpeedArmor", number(type.maxSpeed), Math.round(type.maxArmor)),
                    "", "catalog-delta hidden",
                    vehicleKind(type),
                    Strings.get("openingMechLabFab"), buildHint(recipe),
                    !canBuild(recipe), materialRows(base, resources.quote(recipe.cost())), "material-costs",
                    () -> build(recipe), () -> previewRecipeId.set(recipe.id())));
        }
        return rows;
    }

    private List<CatalogRow> replenisherCatalog(CampaignMech mech) {
        List<CatalogRow> rows = new ArrayList<>();
        for (MissileReplenisherComponent component : MissileReplenisherComponent.catalog()) {
            boolean installed = mech != null && component.id().equals(mech.missileReplenisherId());
            int free = bay.availableReplenisher(component.id());
            // Loaders cannot be fabricated, so a row is a spare in stores or nothing.
            if (free <= 0) continue;
            // A faster loader takes the utility socket, so a fitted vent array bars it.
            boolean socketTaken = mech != null && !installed
                    && mech.internal(MechInternal.Role.VENT_ARRAY) != null
                    && !MissileReplenisherComponent.STANDARD.id().equals(component.id());
            boolean disabled = mech == null || installed || free <= 0 || socketTaken;
            boolean inCargo = free > 0;
            String base = "mech-catalog:" + component.id();
            rows.add(catalogRow(base, "catalog-preview", null, null, component, null, null,
                    "catalog-row", component.displayName(),
                    stockBadge(free), inCargo ? "catalog-badge in-cargo" : "catalog-badge unavailable",
                    spareCount(free),
                    Strings.format("openingMechLabSrmSLrmS", number(component.srmReplenishmentSeconds()), number(component.lrmReplenishmentSeconds())),
                    "", "catalog-delta hidden",
                    Strings.get("openingMechLabRestoresOneMissileTriggerPack"),
                    "",
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
            if (!bay.canFabricate(internal) && bay.availableInternal(internal.id) == 0) continue;

            int free = bay.availableInternal(internal.id);
            boolean installed = current == internal;
            boolean fits = bay.canInstallInternal(mech.id(), internal);
            boolean loaderConflict = !fits && role == MechInternal.Role.VENT_ARRAY
                    && !MissileReplenisherComponent.STANDARD.id().equals(mech.missileReplenisherId());
            MechFrameBudget.Fit proposed = fit.withInternal(role, internal);
            boolean withinBudget = installed || bay.withinBudget(mech.id(), proposed);
            boolean inCargo = free > 0;
            boolean locked = !bay.canFabricate(internal);
            boolean affordable = resources.canAfford(resources.quote(recipe.cost()));
            boolean disabled = installed || !fits || !withinBudget || !inCargo && (locked || !affordable);

            boolean plating = role == MechInternal.Role.PLATING;
            String badge = stockBadge(free);
            String badgeClasses = inCargo ? "catalog-badge in-cargo" : "catalog-badge fabrication";

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

            // Plating covers the whole frame and has no mount to drop on, so a spare is fitted
            // from its row; every other part is dragged onto its mount.
            boolean fitFromRow = plating && inCargo;
            boolean offerFab = !locked && !inCargo;
            String actionLabel = fitFromRow ? Strings.get("openingMechLabFit")
                    : offerFab ? Strings.get("openingMechLabFab") : "";
            String actionHint = fitFromRow ? !withinBudget ? overBudgetHint(proposed)
                    : Strings.get("openingMechLabInternalSpareHint")
                    : affordable ? Strings.get("openingMechLabFabHint")
                    : Strings.get("openingMechLabNotEnoughMaterialsHoverACostFor");
            disabled = fitFromRow ? !withinBudget : !offerFab || !affordable;

            String base = "mech-catalog:" + internal.id;
            boolean showMaterials = offerFab;
            List<Row> materials = showMaterials ? materialRows(base, resources.quote(recipe.cost())) : List.of();
            rows.add(catalogRow(base, internal.role == MechInternal.Role.PLATING ? "catalog-preview hidden" : "catalog-preview",
                    null, null, null, internal, null,
                    showMaterials ? "catalog-row requires-fabrication" : "catalog-row", internal.displayName(),
                    badge, badgeClasses,
                    Strings.format("openingMechLabOwnFree", recipe.provenance(), spareCount(free)),
                    internalTelemetry(proposed, internal), deltaText, deltaClasses,
                    detail, actionLabel, actionHint,
                    disabled, materials, showMaterials ? "material-costs" : "material-costs hidden",
                    fitFromRow ? () -> fitInternal(internal) : () -> fabricateInternal(internal)));
        }
        return List.copyOf(rows);
    }

    /** Spares in stores, or that the row is a known pattern with none built. */
    /** A spare in stores or a pattern to build; how many spares is the row's x-count. */
    private static String stockBadge(int free) {
        return free > 0 ? Strings.get("openingMechLabInStores") : Strings.get("openingMechLabPattern");
    }

    /** Spares in stores as a compact x-count, x0 to x99; a larger store still reads x99. */
    static String spareCount(int free) {
        return Strings.format("openingMechLabSpareCount", Math.max(0, Math.min(99, free)));
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
            case INSUFFICIENT_MATERIALS -> Strings.get("openingMechLabFabricationBlockedRequiredFleetMaterialsAreShort");
            default -> Strings.get("openingMechLabRefitBlockedCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
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
            case CAPACITOR_BANK -> new float[]{proposed.fluxCapacity() - current.fluxCapacity(),
                    proposed.dissipationPerSecond() - current.dissipationPerSecond()};
            case VENT_ARRAY -> new float[]{proposed.dissipationPerSecond() - current.dissipationPerSecond()};
        };
        String[] labels = switch (role) {
            case ENGINE -> new String[]{"openingMechLabDeltaOutput", "openingMechLabDeltaSpeed"};
            case PLATING -> new String[]{"openingMechLabDeltaArmor"};
            case CAPACITOR_BANK -> new String[]{"openingMechLabDeltaFlux", "openingMechLabDeltaDissipation"};
            case VENT_ARRAY -> new String[]{"openingMechLabDeltaDissipation"};
        };
        for (int i = 0; i < stats.length; i++) {
            float value = stats[i];
            if (Math.abs(value) <= 0.01f) continue;
            if (!text.isEmpty()) text.append(Strings.get("separatorDot"));
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
                : String.format(Strings.locale(), "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return (value > 0 ? "+" : "") + text;
    }

    /** What one internal contributes, read off the fit it would produce. */
    private static String internalTelemetry(MechFrameBudget.Fit fit, MechInternal internal) {
        return switch (internal.role) {
            case ENGINE -> Strings.format("openingMechLabInternalEngineTelemetry",
                    number(fit.output()), number(fit.moveSpeed()));
            case PLATING -> Strings.format("openingMechLabInternalPlatingTelemetry",
                    Math.round(fit.armorCapacity()));
            case CAPACITOR_BANK -> Strings.format("openingMechLabInternalCapacitorBankTelemetry",
                    number(fit.fluxCapacity()), number(fit.dissipationPerSecond()));
            case VENT_ARRAY -> Strings.format("openingMechLabInternalVentArrayTelemetry",
                    number(internal.dissipation));
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
            case CAPACITORS -> MechInternal.Role.CAPACITOR_BANK;
            case MINI_FAB -> MechInternal.Role.VENT_ARRAY;
            default -> null;
        };
    }

    public Signal<List<Row>> materialStock() { return materialStock; }

    /** The selected mech's battle damage, its repair clock and the order, or a hidden card. */
    private Repair buildRepair() {
        revision.get();
        float now = day.get();
        CampaignMech mech = selectedMech();
        CampaignMech.Readiness readiness = mech != null ? mech.readiness(now) : CampaignMech.Readiness.READY;
        if (readiness == CampaignMech.Readiness.READY) {
            return new Repair("mech-repair-card surface-dark hidden", "", "", "", "",
                    "mech-repair-order hidden", "", List.of(), () -> { });
        }
        String status = readinessText(mech);
        String statusClasses = readinessClasses(mech, "label heading mech-repair-status");
        if (readiness == CampaignMech.Readiness.UNDER_REPAIR || readiness == CampaignMech.Readiness.INCOMPLETE) {
            return new Repair("mech-repair-card surface-dark", "height: 30px;", status, statusClasses,
                    "", "mech-repair-order hidden", "", List.of(), () -> { });
        }
        MechWorkshop.RepairQuote quote = workshop.repairQuote(mech.id());
        List<Row> materials = quote != null ? materialRows("mech-repair", quote.bill()) : List.of();
        return new Repair("mech-repair-card surface-dark",
                "height: " + (144 + materials.size() * 36) + "px;", status, statusClasses,
                sectionWear(mech), "mech-repair-order",
                quote != null ? Strings.format("mechLabRepairOrder", quote.days()) : "",
                materials, this::repairSelected);
    }

    /** Compact per-section wear: each damaged section and how much of it is gone. */
    private static String sectionWear(CampaignMech mech) {
        if (mech.damage() == null) return "";
        StringBuilder text = new StringBuilder();
        for (Map.Entry<MechSection, Float> entry : mech.damage().wearBySection().entrySet()) {
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
            case DAMAGED -> Strings.get("mechLabReadinessDamaged");
            case UNDER_REPAIR -> Strings.format("mechLabReadinessUnderRepair", tenths(mech.repairDaysRemaining(now)));
            case INCOMPLETE -> Strings.format("mechLabReadinessIncomplete", missingNames(mech));
            case READY -> Strings.get("openingUiMechLabOpenGantry");
        };
    }

    private String readinessClasses(CampaignMech mech, String base) {
        return switch (mech.readiness(day.get())) {
            case DAMAGED -> base + " tone-danger";
            case UNDER_REPAIR -> base + " tone-accent";
            case INCOMPLETE -> base + " tone-danger";
            case READY -> base;
        };
    }

    /** The required internals a mech is missing, named for its readiness line. */
    private static String missingNames(CampaignMech mech) {
        List<String> names = new ArrayList<>();
        for (MechInternal.Role role : mech.missingInternals()) {
            names.add(Strings.get(role == MechInternal.Role.ENGINE ? "mechLabMissingEngine" : "mechLabMissingCapacitors"));
        }
        return String.join(Strings.get("separatorDot"), names);
    }

    /** Days rounded up to the tenth, so a clock never reads 0.0 while it still holds a mech. */
    private static double tenths(float days) {
        return Math.ceil(days * 10d) / 10d;
    }

    /** Orders the selected mech's repair: the whole bill is spent, or nothing changes. */
    public void repairSelected() {
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        float now = day.get();
        MechWorkshop.Result result = workshop.repair(mech.id(), now);
        feedbackText.set(switch (result.status()) {
            case REPAIR_STARTED -> Strings.format("mechLabRepairStarted", mech.displayName(),
                    tenths(mech.repairDaysRemaining(now)));
            case INSUFFICIENT_MATERIALS -> Strings.get("mechLabRepairShort");
            case NOT_DAMAGED -> Strings.format("mechLabRepairNotDamaged", mech.displayName());
            default -> Strings.get("mechLabRepairBlocked");
        });
        finishWeaponCommand(result);
    }

    private List<Row> buildMaterialStock() {
        revision.get();
        return List.of(Commodities.HEAVY_MACHINERY, Commodities.METALS, Commodities.RARE_METALS).stream().map(commodity ->
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
        selectedVehicleId.set(null);
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
        selectedVehicleId.set(null);
        callSignDraft.set(squad.mechById(mechId).displayName());
        selectedSlot.set(defaultFittingSlot(squad.mechById(mechId)));
        platingSelected.set(false);
        browsingStores.set(true);
        fittingFocused.set(true);
        assetPickerOpen.set(false);
        feedbackText.set(Strings.format("openingMechLabInspectingNoCampaignHardwareChanged", squad.mechById(mechId).displayName()));
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    /** Vehicles stand on the gantries after the mechs, in bay order. */
    private void selectVehicle(String vehicleId) {
        CampaignMechSquad squad = selectedSquad();
        CampaignVehicle vehicle = squad != null ? squad.vehicleById(vehicleId) : null;
        if (vehicle == null) return;
        selectedGantry.set(squad.mechs().size() + squad.vehicles().indexOf(vehicle));
        selectedMechId.set(null);
        selectedVehicleId.set(vehicleId);
        callSignDraft.set(vehicle.displayName());
        selectedSlot.set(SocketId.MINI_FAB);
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
        platingSelected.set(false);
        browsingStores.set(mech != null);
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
        CampaignMech mech = selectedMech();
        if (mech == null) return;
        MechFittingLayout layout = MechFittingLayout.forVariant(mech.variant());
        if (!layout.hasSocket(slot)) return;
        selectedSlot.set(slot);
        platingSelected.set(false);
        browsingStores.set(false);
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
        browsingStores.set(false);
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

    /** Pads are lettered A to D, the stencil on each pad and the label on its card. */
    static String padLetter(int index) {
        return String.valueOf((char) ('A' + Math.max(0, index)));
    }

    private static String padLabel(int index) {
        return String.format(Strings.locale(), Strings.get("openingMechLabGantryD"), padLetter(index));
    }

    private String selectedMachineId() {
        CampaignMech mech = selectedMech();
        if (mech != null) return mech.id();
        CampaignVehicle vehicle = selectedVehicle();
        return vehicle != null ? vehicle.id() : null;
    }

    /**
     * SCRAP takes two presses: the first arms it and says what comes back, the second breaks
     * the machine up through the workshop and returns to the lance with its pad vacant.
     */
    private void scrapSelected() {
        String machineId = selectedMachineId();
        if (machineId == null) return;
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

    private String salvageText(FabricationCost salvage) {
        if (salvage == null) return Strings.get("openingMechLabNoSalvage");
        List<String> parts = new ArrayList<>();
        for (FabricationCost.Line line : salvage.lines()) {
            parts.add(line.quantity() + " " + resources.commodityName(line.commodityId()));
        }
        return String.join(", ", parts);
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
            stripVentArray();
            return;
        }
        if (slot != null && roleFor(slot) != null) {
            removeInstalledInternal(slot);
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

    /** Empties the utility socket; the vent array is the only internal that may be left out. */
    public boolean stripVentArray() {
        CampaignMech mech = selectedMech();
        if (mech == null) return false;
        MechInternal previous = mech.internal(MechInternal.Role.VENT_ARRAY);
        MechWorkshop.Result result = workshop.stripVentArray(mech.id());
        feedbackText.set(switch (result.status()) {
            case STRIPPED -> Strings.format("openingMechLabVentArrayStripped", previous.displayName());
            case ALREADY_STRIPPED -> Strings.get("openingMechLabUtilitySocketAlreadyEmpty");
            default -> Strings.get("openingMechLabUnequipBlockedCampaignHardwareWasNotChanged");
        });
        finishWeaponCommand(result);
        return result.succeeded();
    }

    private void fabricateChassis(MechVariant variant) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.occupied() >= physicalGantries) return;
        MechWorkshop.Result result = workshop.fabricateChassis(squad.id(), variant);
        CampaignMech fabricated = result.mech();
        if (fabricated != null) {
            selectedMechId.set(fabricated.id());
            selectedVehicleId.set(null);
            callSignDraft.set(fabricated.displayName());
            selectedGantry.set(Math.max(0, squad.mechs().size() - 1));
            selectedSlot.set(fabricated.variant().twoArmed() ? SocketId.LEFT_ARM : SocketId.ARMS);
            platingSelected.set(false);
            fittingFocused.set(true);
        }
        feedbackText.set(switch (result.status()) {
            case CHASSIS_FABRICATED -> Strings.format("openingMechLabFabricatedWithItsStandardRollOutFit", fabricated.displayName());
            case PATTERN_LOCKED -> Strings.get("openingMechLabThisFabricationPatternHasNotBeenAcquired");
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
     * Builds a vehicle onto the selected lance's next vacant gantry through the workshop,
     * which owns the pattern, berth and material refusals.
     */
    boolean fabricateVehicle(VehicleType type) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || type == null) return false;
        MechWorkshop.Result result = workshop.fabricateVehicle(squad.id(), type);
        CampaignVehicle fabricated = result.vehicle();
        if (fabricated != null) {
            selectedMechId.set(null);
            selectedVehicleId.set(fabricated.id());
            callSignDraft.set(fabricated.displayName());
            selectedGantry.set(Math.max(0, squad.occupied() - 1));
            fittingFocused.set(true);
        }
        feedbackText.set(switch (result.status()) {
            case VEHICLE_FABRICATED -> Strings.format("openingMechLabVehicleFabricated", fabricated.displayName());
            case PATTERN_LOCKED -> Strings.get("openingMechLabThisFabricationPatternHasNotBeenAcquired");
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
        CampaignMech mech = selectedMech();
        CampaignVehicle vehicle = selectedVehicle();
        if (mech == null && vehicle != null) {
            renameSelectedVehicle(vehicle);
            return;
        }
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

    private void renameSelectedVehicle(CampaignVehicle vehicle) {
        if (!vehicle.rename(callSignDraft.get())) {
            feedbackText.set(Strings.format("openingMechLabCallSignMustContainCharacters", CampaignMech.CALLSIGN_MAX_LENGTH));
            feedbackClasses.set("mech-lab-feedback tone-danger surface-dark");
            return;
        }
        callSignDraft.set(vehicle.displayName());
        feedbackText.set(Strings.format("openingMechLabRenamedVehicle", vehicle.displayName()));
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

    private CampaignVehicle selectedVehicle() {
        if (!fittingFocused.get()) return null;
        CampaignMechSquad squad = selectedSquad();
        String vehicleId = selectedVehicleId.get();
        return squad != null && vehicleId != null ? squad.vehicleById(vehicleId) : null;
    }

    /** Callsign of the selected mech or vehicle, or null when neither is selected. */
    private String selectedAssetName() {
        CampaignMech mech = selectedMech();
        if (mech != null) return mech.displayName();
        CampaignVehicle vehicle = selectedVehicle();
        return vehicle != null ? vehicle.displayName() : null;
    }

    /** True on a vacant gantry, where chassis and vehicle patterns are offered. */
    private boolean fabricatingChassis() {
        if (!fittingFocused.get() || selectedMechId.get() != null
                || selectedVehicleId.get() != null) return false;
        return !gantryOccupied(selectedSquad(), selectedGantryIndex());
    }

    private static boolean gantryOccupied(CampaignMechSquad squad, int index) {
        return squad != null && index >= 0 && index < squad.occupied();
    }

    /** Gantries hold the mechs first, then the vehicles. */
    private static CampaignVehicle vehicleAt(CampaignMechSquad squad, int index) {
        if (squad == null) return null;
        int vehicleIndex = index - squad.mechs().size();
        return vehicleIndex >= 0 && vehicleIndex < squad.vehicles().size()
                ? squad.vehicles().get(vehicleIndex) : null;
    }

    private static String vehicleIdentity(VehicleType type) {
        return Strings.format("openingMechLabVehicleIdentity", CampaignVehicle.typeName(type), weightLabel(type));
    }

    /** Weight and armament, for places that already show the vehicle's name above it. */
    private static String vehicleKind(VehicleType type) {
        return Strings.join("separatorDot", Strings.get("openingMechLabVehicle"), vehicleArmament(type));
    }

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

    private static String vehicleArmament(VehicleType type) {
        String turret = turretName(type);
        return turret != null ? Strings.format("openingMechLabVehicleTurret", turret)
                : Strings.get("openingMechLabVehicleUnarmed");
    }

    /** The turret's catalog name; a generic label when the catalog is not loaded. */
    private static String turretName(VehicleType type) {
        if (!type.hasTurretWeapon()) return null;
        TurretCatalogRegistry registry = TurretCatalogRegistry.installed();
        StructureDef structure = registry != null ? registry.getStructure(type.turretStructureId) : null;
        return structure != null ? structure.displayName : Strings.get("openingMechLabVehicleTurretGeneric");
    }

    private static String vehicleSummary(VehicleType type) {
        return Strings.format("openingMechLabVehicleSummary", number(type.maxStructure),
                number(type.maxArmor), number(type.armorRating), number(type.maxSpeed), type.capacity);
    }

    /** Read-only hull figures, scaled against the largest fabricable vehicle of each. */
    /** @param prefix keeps the pattern card's bars apart from the specifications' own ids */
    private static List<FittingMeter> vehicleMeters(VehicleType type, String prefix) {
        return List.of(
                meter(prefix + "structure", Strings.get("openingMechLabStructure"), number(type.maxStructure),
                        type.maxStructure, vehicleMaximum(t -> t.maxStructure), type.maxStructure),
                meter(prefix + "armor", Strings.get("openingMechLabArmor"),
                        Strings.format("openingMechLabVehicleArmor", number(type.maxArmor), number(type.armorRating)),
                        type.maxArmor, vehicleMaximum(t -> t.maxArmor), type.maxArmor),
                meter(prefix + "mobility", Strings.get("openingMechLabMobility"), Strings.format("openingMechLabCellsS", number(type.maxSpeed)),
                        type.maxSpeed, vehicleMaximum(t -> t.maxSpeed), type.maxSpeed),
                meter(prefix + "capacity", Strings.get("openingMechLabPassengers"), Strings.format("openingMechLabSeats", type.capacity),
                        type.capacity, vehicleMaximum(t -> t.capacity), type.capacity));
    }

    private static float vehicleMaximum(VehicleMetric metric) {
        float max = 0f;
        for (MechFabricationCatalog.Recipe recipe : MechFabricationCatalog.vehicles()) {
            max = Math.max(max, metric.value(recipe.vehicle()));
        }
        return max;
    }

    private static CampaignMech mechAt(CampaignMechSquad squad, int index) {
        return squad != null && index >= 0 && index < squad.mechs().size()
                ? squad.mechs().get(index) : null;
    }

    private static String slotComponent(CampaignMech mech, SocketId slot) {
        if (mech == null) return Strings.get("openingMechLabNoAsset");
        return switch (slot) {
            case CORE -> internalName(mech, MechInternal.Role.ENGINE);
            case ARMS -> componentName(mech.arms());
            case LEFT_ARM -> componentName(mech.leftArm());
            case RIGHT_ARM -> componentName(mech.rightArm());
            case LEFT_SHOULDER -> componentName(mech.leftShoulder());
            case RIGHT_SHOULDER -> componentName(mech.rightShoulder());
            case CAPACITORS -> internalName(mech, MechInternal.Role.CAPACITOR_BANK);
            case MINI_FAB -> mech.internal(MechInternal.Role.VENT_ARRAY) != null
                    ? mech.internal(MechInternal.Role.VENT_ARRAY).displayName()
                    : mech.missileReplenisherId().equals(MissileReplenisherComponent.ACCELERATED_FEED.id())
                    ? Strings.get("openingMechLabAcceleratedFeed") : Strings.get("openingMechLabStandardReplenisher");
        };
    }

    private static String internalName(CampaignMech mech, MechInternal.Role role) {
        MechInternal internal = mech.internal(role);
        return internal != null ? internal.displayName() : Strings.get("mechLabSocketRequired");
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

    private static float weaponRange(MechFrameBudget.Fit fit) {
        float max = 0f;
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent component = fit.weaponAt(slot);
            if (component != null) max = Math.max(max, component.weaponDef().range);
        }
        return max;
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
            case LEFT_ARM -> MechMountSlot.LEFT_ARM;
            case RIGHT_ARM -> MechMountSlot.RIGHT_ARM;
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

    private List<FittingFilterPill> buildFilterPills() {
        if (!fabricatingChassis() && (selectedMech() == null || selectedVehicle() != null)) return List.of();
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
        if (selectedMech() == null) return List.of();
        if (browsingStores.get()) return CategoryFilter.PARTS;
        if (platingSelected.get() || mountFor(selectedSlot.get()) == null) return List.of();
        SocketDef socket = selectedSocketDefinition();
        return socket != null && socket.type() == MechFittingLayout.SocketType.OMNI
                ? CategoryFilter.WEAPONS : List.of();
    }

    /** The chosen category where this view offers it; anything else reads as no filter. */
    private CategoryFilter effectiveFilter() {
        CategoryFilter current = catalogFilter.get();
        return offeredFilters().contains(current) ? current : CategoryFilter.ALL;
    }


    /** A bar with no stock reference: a vehicle's read-only hull figures. */
    private static FittingMeter meter(String suffix, String label, String value,
                                          float currentAmount, float maximum, float ghostAmount) {
        return meter(suffix, label, value, currentAmount, maximum, ghostAmount, currentAmount, FittingMeter.NEUTRAL_TONE);
    }

    /**
     * One stat bar, built of positioned segments on its track: the stock share in a dim shade of
     * the part's colour, any gain past stock in the full colour, any shortfall below stock as a
     * deficit, a tick where stock stands, and the hovered candidate's change as a ghost on top.
     */
    private static FittingMeter meter(String suffix, String label, String value,
                                          float currentAmount, float maximum, float ghostAmount,
                                          float stockAmount, String tone) {
        return FittingMeter.of("mech-meter:" + suffix, label, value, currentAmount, maximum,
                ghostAmount, stockAmount, tone);
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
    public record LanceSlot(String id, String letter, CampaignMech mech, VehicleType vehicle)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "id" -> id; default -> throw unknown("mech-lance-slot", p); }; }
    }

    public record MechRow(String id, String nameId, String chassisId, String subsystemId,
                          String actionId, String previewId, String padId, String pad, String letter,
                          CampaignMech mech, VehicleType vehicle,
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

    /** The selected mech's damage card: readiness, section wear and the repair order. */
    public record Repair(String classes, String style, String status, String statusClasses,
                         String sections, String orderClasses, String order, List<Row> materials,
                         Runnable action) implements MarkupPropertySource {
        @Override public Object markupProperty(String p) { return switch (p) {
            case "classes" -> classes; case "style" -> style; case "status" -> status;
            case "statusClasses" -> statusClasses; case "sections" -> sections;
            case "orderClasses" -> orderClasses; case "order" -> order;
            case "materials" -> materials; case "action" -> action;
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
        ENERGY("openingMechLabEnergy", "graphics/ui/mech-hardware/pulse-laser.svg", "#e06a3c"),
        ENGINE("openingMechLabFilterEngine", "graphics/ui/mech-hardware/engine-industrial.svg", "#5e96d2"),
        CAPACITORS("openingMechLabFilterCapacitors", "graphics/ui/mech-hardware/capacitor-factory.svg", "#a07edc"),
        UTILITY("openingMechLabFilterUtility", "graphics/ui/mech-hardware/loader-standard.svg", "#4cb4a6"),
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
        static final List<CategoryFilter> PARTS = List.of(BALLISTIC, MISSILE, ENERGY, ENGINE, CAPACITORS, UTILITY);
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
                             MissileReplenisherComponent replenisherPreview,
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
                                         MechWeaponComponent weapon, MissileReplenisherComponent replenisher,
                                         MechInternal internal, VehicleType vehicle,
                                         String classes, String name, String badge, String badgeClasses,
                                         String stock, String telemetry, String delta, String deltaClasses,
                                         String detail, String actionLabel, String actionHint,
                                         boolean actionDisabled, List<Row> materials, String materialsClasses,
                                         Runnable action) {
        return catalogRow(base, previewClasses, chassis, weapon, replenisher, internal, vehicle, classes, name,
                badge, badgeClasses, stock, telemetry, delta, deltaClasses, detail, actionLabel, actionHint,
                actionDisabled, materials, materialsClasses, action, null);
    }

    /*
     * A chassis or vehicle's weight class rides on its card as the weight block on the class
     * tone, and its button builds a spare when it reads FAB, which wears the fabricator's cog.
     */
    private static CatalogRow catalogRow(String base, String previewClasses, MechVariant chassis,
                                         MechWeaponComponent weapon, MissileReplenisherComponent replenisher,
                                         MechInternal internal, VehicleType vehicle,
                                         String classes, String name, String badge, String badgeClasses,
                                         String stock, String telemetry, String delta, String deltaClasses,
                                         String detail, String actionLabel, String actionHint,
                                         boolean actionDisabled, List<Row> materials, String materialsClasses,
                                         Runnable action, Runnable select) {
        MechPattern.WeightClass weight = weightOf(chassis, vehicle);
        FittingCard card = new FittingCard(base, classes, previewClasses, name, badge, badgeClasses,
                stock, telemetry, delta, deltaClasses, detail, actionLabel, actionHint, actionDisabled,
                Strings.get("openingMechLabFab").equals(actionLabel), materials, materialsClasses, action,
                weight != null ? weightIcon(weight) : null, weight != null ? weightTone(weight) : null);
        return new CatalogRow(card, chassis, weapon, replenisher, internal, vehicle, select);
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
