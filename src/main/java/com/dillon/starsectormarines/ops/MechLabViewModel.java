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

/** Retained projection and command surface for the campaign-authoritative {@link MechBay}. */
public final class MechLabViewModel {

    private final MechBay bay;
    private final MutableSignal<Integer> revision;
    private final MutableSignal<String> selectedSquadId;
    private final MutableSignal<String> selectedMechId;
    private final MutableSignal<String> feedbackText;
    private final MutableSignal<String> feedbackClasses;
    private final ComputedSignal<String> labSummary;
    private final ComputedSignal<List<SquadRow>> squadRows;
    private final ComputedSignal<List<MechRow>> mechRows;
    private final ComputedSignal<String> selectedMechName;
    private final ComputedSignal<String> selectedMechIdentity;
    private final ComputedSignal<String> selectedMechDoctrine;
    private final ComputedSignal<List<SpecCard>> specCards;
    private final ComputedSignal<List<MountRow>> mountRows;
    private final ComputedSignal<String> installedSubsystem;
    private final ComputedSignal<String> installedCadence;
    private final ComputedSignal<List<InventoryRow>> inventoryRows;

    public MechLabViewModel(Reactor reactor, MechBay bay) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (bay == null) throw new IllegalArgumentException("mech bay is required");
        this.bay = bay;
        CampaignMechSquad initialSquad = bay.activeSquad();
        CampaignMech initialMech = firstMech(initialSquad);
        revision = reactor.signal(0);
        selectedSquadId = reactor.signal(initialSquad != null ? initialSquad.id() : null);
        selectedMechId = reactor.signal(initialMech != null ? initialMech.id() : null);
        feedbackText = reactor.signal(
                "Select a chassis, compare fleet stock, then commit one subsystem refit.");
        feedbackClasses = reactor.signal("mech-lab-feedback tone-muted surface-dark");

        labSummary = reactor.computed(this::buildLabSummary);
        squadRows = reactor.computed(this::buildSquadRows);
        mechRows = reactor.computed(this::buildMechRows);
        selectedMechName = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.displayName() : "NO CHASSIS SELECTED";
        });
        selectedMechIdentity = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.variant().displayName + " chassis  ·  Persistent support asset"
                    : "Select an assigned chassis from the active lance.";
        });
        selectedMechDoctrine = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? "Doctrine  /  " + roleLabel(mech.role()) : "Doctrine unavailable";
        });
        specCards = reactor.computed(this::buildSpecCards);
        mountRows = reactor.computed(this::buildMountRows);
        installedSubsystem = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            return mech != null ? mech.missileReplenisher().displayName()
                    : "No subsystem selected";
        });
        installedCadence = reactor.computed(() -> {
            CampaignMech mech = selectedMech();
            if (mech == null) return "Cadence unavailable";
            MissileReplenisherComponent component = mech.missileReplenisher();
            return "SRM " + number(component.srmReplenishmentSeconds())
                    + "s / trigger  ·  LRM " + number(component.lrmReplenishmentSeconds())
                    + "s / trigger";
        });
        inventoryRows = reactor.computed(this::buildInventoryRows);
    }

    public Signal<String> labSummary() { return labSummary; }
    public Signal<List<SquadRow>> squadRows() { return squadRows; }
    public Signal<List<MechRow>> mechRows() { return mechRows; }
    public Signal<String> selectedMechName() { return selectedMechName; }
    public Signal<String> selectedMechIdentity() { return selectedMechIdentity; }
    public Signal<String> selectedMechDoctrine() { return selectedMechDoctrine; }
    public Signal<List<SpecCard>> specCards() { return specCards; }
    public Signal<List<MountRow>> mountRows() { return mountRows; }
    public Signal<String> installedSubsystem() { return installedSubsystem; }
    public Signal<String> installedCadence() { return installedCadence; }
    public Signal<List<InventoryRow>> inventoryRows() { return inventoryRows; }
    public Signal<String> feedbackText() { return feedbackText; }
    public Signal<String> feedbackClasses() { return feedbackClasses; }

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
                ? " support squad" : " support squads") + "  ·  "
                + chassis + " / " + CampaignMechSquad.CAPACITY
                + " chassis in active lance  ·  deployment values freeze at mission commit";
    }

    private List<SquadRow> buildSquadRows() {
        revision.get();
        String selected = selectedSquadId.get();
        CampaignMechSquad active = bay.activeSquad();
        List<SquadRow> rows = new ArrayList<>();
        for (CampaignMechSquad squad : bay.squads()) {
            boolean current = squad.id().equals(selected);
            boolean activeSupport = active != null && squad.id().equals(active.id());
            String base = "mech-squad:" + squad.id();
            rows.add(new SquadRow(base, base + ":name", base + ":status",
                    base + ":strength", current
                    ? "mech-squad-row selected" : "mech-squad-row",
                    squad.displayName(), activeSupport ? "ACTIVE SUPPORT" : "AVAILABLE",
                    squad.mechs().size() + " / " + CampaignMechSquad.CAPACITY + " chassis",
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
            boolean current = mech.id().equals(selected);
            rows.add(new MechRow(base, base + ":name", base + ":chassis",
                    base + ":subsystem", current
                    ? "mech-row selected" : "mech-row", mech.displayName(),
                    mech.variant().displayName + "  ·  " + roleLabel(mech.role()),
                    mech.missileReplenisher().displayName(),
                    () -> selectMech(mech.id())));
        }
        return List.copyOf(rows);
    }

    private List<SpecCard> buildSpecCards() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return List.of();
        MechVariant variant = mech.variant();
        return List.of(
                new SpecCard("mech-spec-structure", "mech-spec-structure:label",
                        "mech-spec-structure:value", "mech-spec-structure:caption", "STRUCTURE",
                        Integer.toString(Math.round(variant.maxStructure)), "internal frame"),
                new SpecCard("mech-spec-armor", "mech-spec-armor:label",
                        "mech-spec-armor:value", "mech-spec-armor:caption", "ARMOR",
                        Integer.toString(Math.round(variant.armorPool)), "ablative protection"),
                new SpecCard("mech-spec-rating", "mech-spec-rating:label",
                        "mech-spec-rating:value", "mech-spec-rating:caption", "RATING",
                        Integer.toString(Math.round(variant.armorRating)), "penetration resistance"),
                new SpecCard("mech-spec-speed", "mech-spec-speed:label",
                        "mech-spec-speed:value", "mech-spec-speed:caption", "SPEED",
                        number(variant.moveSpeed), "cells / second"));
    }

    private List<MountRow> buildMountRows() {
        revision.get();
        CampaignMech mech = selectedMech();
        if (mech == null) return List.of();
        MechVariant variant = mech.variant();
        return List.of(
                mountRow("mech-mount-arms", "ARMS", variant.arms),
                mountRow("mech-mount-left", "LEFT SHOULDER", variant.leftShoulder),
                mountRow("mech-mount-right", "RIGHT SHOULDER", variant.rightShoulder));
    }

    private List<InventoryRow> buildInventoryRows() {
        revision.get();
        CampaignMech mech = selectedMech();
        List<InventoryRow> rows = new ArrayList<>();
        for (MissileReplenisherComponent component : MissileReplenisherComponent.catalog()) {
            boolean installed = mech != null
                    && component.id().equals(mech.missileReplenisherId());
            int owned = bay.ownedReplenisher(component.id());
            int fielded = bay.installedReplenisher(component.id());
            int free = bay.availableReplenisher(component.id());
            boolean disabled = mech == null || installed || free <= 0;
            String base = "mech-component:" + component.id();
            rows.add(new InventoryRow(base, base + ":copy", base + ":name", base + ":stock",
                    base + ":cadence", base + ":install",
                    installed ? "mech-component-row selected" : "mech-component-row",
                    component.displayName(), "OWNED " + owned + "  ·  INSTALLED "
                    + fielded + "  ·  FREE " + free,
                    "SRM " + number(component.srmReplenishmentSeconds()) + "s  ·  LRM "
                            + number(component.lrmReplenishmentSeconds()) + "s",
                    installed ? "INSTALLED" : free > 0 ? "INSTALL" : "STOCK COMMITTED",
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
        feedbackText.set(squad.displayName() + " is now the active Mech Support lance.");
        feedbackClasses.set("mech-lab-feedback tone-good surface-dark");
        revision.update(value -> value + 1);
    }

    private void selectMech(String mechId) {
        CampaignMechSquad squad = selectedSquad();
        if (squad == null || squad.mechById(mechId) == null) return;
        selectedMechId.set(mechId);
        feedbackText.set("Inspecting " + squad.mechById(mechId).displayName()
                + ". No campaign hardware changed.");
        feedbackClasses.set("mech-lab-feedback tone-muted surface-dark");
    }

    private void install(String componentId) {
        CampaignMech mech = selectedMech();
        MissileReplenisherComponent component =
                MissileReplenisherComponent.findById(componentId);
        if (mech == null || component == null) return;
        boolean installed = bay.installReplenisher(mech.id(), component.id());
        feedbackText.set(installed
                ? component.displayName() + " installed on " + mech.displayName() + "."
                : "Refit blocked: no unassigned component is available.");
        feedbackClasses.set(installed
                ? "mech-lab-feedback tone-good surface-dark"
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

    private static MountRow mountRow(String id, String slot, MechWeaponComponent component) {
        if (component == null) {
            return new MountRow(id, id + ":slot", id + ":data", id + ":component",
                    id + ":detail", id + ":fixed", slot, "EMPTY HARDPOINT",
                    "No weapon component installed", "mount-row empty-mount");
        }
        String ammunition = component.ammoCapacity < 0
                ? "unlimited ammunition" : component.ammoCapacity + " triggers ready";
        return new MountRow(id, id + ":slot", id + ":data", id + ":component",
                id + ":detail", id + ":fixed", slot, component.displayName,
                number(component.weapon.range) + "-cell band  ·  " + ammunition,
                "mount-row");
    }

    private static String roleLabel(MechRole role) {
        return switch (role) {
            case LR_SUPPORT -> "Long-range support";
            case ARMORED_SUPPORT -> "Armored support";
            case ASSAULT -> "Assault";
        };
    }

    private static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public record SquadRow(String id, String nameId, String statusId, String strengthId,
                           String classes, String name, String status, String strength,
                           Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "nameId" -> nameId; case "statusId" -> statusId;
                case "strengthId" -> strengthId; case "classes" -> classes;
                case "name" -> name; case "status" -> status; case "strength" -> strength;
                case "select" -> select;
                default -> throw unknown("mech-squad", property);
            };
        }
    }

    public record MechRow(String id, String nameId, String chassisId, String subsystemId,
                          String classes, String name, String chassis, String subsystem,
                          Runnable select) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "nameId" -> nameId; case "chassisId" -> chassisId;
                case "subsystemId" -> subsystemId; case "classes" -> classes;
                case "name" -> name; case "chassis" -> chassis;
                case "subsystem" -> subsystem; case "select" -> select;
                default -> throw unknown("mech", property);
            };
        }
    }

    public record SpecCard(String id, String labelId, String valueId, String captionId,
                           String label, String value, String caption)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> labelId; case "valueId" -> valueId;
                case "captionId" -> captionId; case "label" -> label; case "value" -> value;
                case "caption" -> caption;
                default -> throw unknown("mech-spec", property);
            };
        }
    }

    public record MountRow(String id, String slotId, String dataId, String componentId,
                           String detailId, String fixedId, String slot, String component,
                           String detail, String classes) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "slotId" -> slotId; case "dataId" -> dataId;
                case "componentId" -> componentId; case "detailId" -> detailId;
                case "fixedId" -> fixedId; case "slot" -> slot; case "component" -> component;
                case "detail" -> detail; case "classes" -> classes;
                default -> throw unknown("mech-mount", property);
            };
        }
    }

    public record InventoryRow(String id, String copyId, String nameId, String stockId,
                               String cadenceId, String installId, String classes,
                               String name, String stock, String cadence,
                               String installLabel, boolean installDisabled,
                               Runnable install) implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "copyId" -> copyId; case "nameId" -> nameId;
                case "stockId" -> stockId;
                case "cadenceId" -> cadenceId; case "installId" -> installId;
                case "classes" -> classes; case "name" -> name; case "stock" -> stock;
                case "cadence" -> cadence; case "installLabel" -> installLabel;
                case "installDisabled" -> installDisabled; case "install" -> install;
                default -> throw unknown("mech-component", property);
            };
        }
    }

    private static IllegalArgumentException unknown(String owner, String property) {
        return new IllegalArgumentException("Unknown " + owner + " property: " + property);
    }
}
