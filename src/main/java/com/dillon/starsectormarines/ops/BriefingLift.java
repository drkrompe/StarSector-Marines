package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.power.ArmorDropPower;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.LiftBudget;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.battleview.DamageDollCanvas;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The briefing's account of the company's armor before launch: what lift the company's own
 * boats bring, what lift the mission brings, and for each machine in the bay whether it can
 * come down, drawn as the battle's damage doll with the wear it carries.
 *
 * <p>Reads the resolved drop powers; it decides nothing. Who may call is
 * {@code PowerCatalog}; how much comes down is {@link LiftBudget}, asked here exactly as the
 * drop power will ask it in battle, machine by machine in manifest order.
 */
public final class BriefingLift {

    private BriefingLift() { }

    /** Whether one machine can come down this mission, and if not, why. */
    public enum Status {
        /** A drop call is selected and lift admits it. */
        DROPS(true),
        /** Lift admits it, but the drop call that would bring it is not on the command deck. */
        NOT_CALLED(false),
        /** No carrier this mission lifts a machine of its class. */
        NO_LIFT(false),
        /** Carriers that could lift it spend their lift on machines ahead of it. */
        LIFT_SPENT(false),
        /** No ship, employer or story brings a drop call for it. */
        NO_DROP_CALL(false),
        /** Missing a required internal: it stays in its gantry. */
        HELD_BACK(false);

        public final boolean drops;

        Status(boolean drops) {
            this.drops = drops;
        }
    }

    /** One bay machine and its verdict. */
    public record Machine(ArmorLoad load, String name, Status status, String powerName) {

        public String liftName() {
            return LiftReadout.ratingName(LiftRating.of(load.liftClass()));
        }
    }

    /**
     * The lift the company's boats bring and the lift the mission brings, each as a rating and
     * the craft behind it.
     *
     * @param companyCarrier the cradled company boat a drop call flies with, or {@code null}
     * @param companyBest    the best rating among the company's boats
     * @param cradled        how many company boats carry a cradle
     * @param missionLift    whether a mission, employer, hull or ship supplies heavy lift
     */
    public record Readout(List<FittedBoat> boats, FittedBoat companyCarrier, LiftRating companyBest,
                          int cradled, boolean missionLift, boolean dropCall, List<Machine> machines) {

        public int dropping() {
            int count = 0;
            for (Machine machine : machines) if (machine.status.drops) count++;
            return count;
        }
    }

    /**
     * Reads the bay against the resolved powers.
     *
     * @param boats    the company boats committed (every boat aboard)
     * @param powers   the powers this mission resolved
     * @param selected ids of the powers on the command deck
     * @param mechs    the active lance as it would launch
     * @param vehicles the bay's vehicles
     * @param heldBack the lance's mechs left out of the manifest
     */
    public static Readout read(List<FittedBoat> boats, List<CommandPower> powers, Collection<String> selected,
                               List<MechDeploymentSpec> mechs, List<VehicleDeploymentSpec> vehicles,
                               List<MechDeploymentSpec> heldBack) {
        List<FittedBoat> fleet = boats == null ? List.of() : boats;
        LiftRating best = LiftRating.NONE;
        int cradled = 0;
        for (FittedBoat boat : fleet) {
            if (boat == null || !boat.cradled()) continue;
            cradled++;
            best = LiftRating.max(best, boat.liftRating());
        }
        List<Machine> machines = new ArrayList<>();
        FittedBoat companyCarrier = null;
        boolean missionLift = false;
        boolean mechsCarried = false;
        boolean vehiclesCarried = false;
        if (powers != null) {
            for (CommandPower power : powers) {
                if (!(power instanceof ArmorDropPower drop)) continue;
                boolean called = selected != null && selected.contains(power.id);
                if (companyCarrier == null) companyCarrier = drop.companyCarrier();
                missionLift |= drop.syntheticLift();
                // The same budget the power builds, spent in the order the bay lists its machines.
                LiftBudget budget = LiftBudget.forMission(drop.companyCarrier(), drop.syntheticLift(),
                        ShuttleType.VALKYRIE, drop.manifest().size());
                for (BayManifest.Entry entry : drop.manifest().entries()) {
                    mechsCarried |= !entry.isVehicle();
                    vehiclesCarried |= entry.isVehicle();
                    LiftBudget.Carrier carrier = budget.allocate(entry.weightClass());
                    Status status;
                    if (carrier == null) {
                        status = budget.refusal(entry.weightClass()) == LiftBudget.Refusal.LIFT_SPENT
                                ? Status.LIFT_SPENT : Status.NO_LIFT;
                    } else {
                        budget.spend(carrier);
                        status = called ? Status.DROPS : Status.NOT_CALLED;
                    }
                    machines.add(new Machine(entry.load(), name(entry), status, power.displayName));
                }
            }
        }
        if (!mechsCarried && mechs != null) {
            for (MechDeploymentSpec mech : mechs) {
                if (mech != null) machines.add(uncalled(ArmorLoad.of(mech)));
            }
        }
        if (!vehiclesCarried && vehicles != null) {
            for (VehicleDeploymentSpec vehicle : vehicles) {
                if (vehicle != null) machines.add(uncalled(ArmorLoad.of(vehicle)));
            }
        }
        if (heldBack != null) {
            for (MechDeploymentSpec mech : heldBack) {
                if (mech == null) continue;
                ArmorLoad load = ArmorLoad.of(mech);
                machines.add(new Machine(load, name(load), Status.HELD_BACK, null));
            }
        }
        // What stays aboard comes first, so a full row never hides it; otherwise bay order.
        List<Machine> ordered = new ArrayList<>(machines.size());
        for (Machine machine : machines) if (!machine.status.drops) ordered.add(machine);
        for (Machine machine : machines) if (machine.status.drops) ordered.add(machine);
        return new Readout(fleet, companyCarrier, best, cradled, missionLift,
                mechsCarried || vehiclesCarried, List.copyOf(ordered));
    }

    private static Machine uncalled(ArmorLoad load) {
        return new Machine(load, name(load), Status.NO_DROP_CALL, null);
    }

    private static String name(BayManifest.Entry entry) {
        String callsign = entry.callsign();
        return callsign == null ? entry.typeName() : callsign;
    }

    private static String name(ArmorLoad load) {
        return name(new BayManifest.Entry(0, load));
    }

    // ---- presentation ----

    /** One machine card: its doll, its name and lift class, and its verdict. */
    record Card(String id, Machine machine, String type, String status, String statusClasses)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "dollId" -> id + ":doll";
                case "copyId" -> id + ":copy";
                case "nameId" -> id + ":name";
                case "typeId" -> id + ":type";
                case "statusId" -> id + ":status";
                case "name" -> machine.name();
                case "type" -> type;
                case "status" -> status;
                case "statusClasses" -> statusClasses;
                default -> null;
            };
        }
    }

    /** Fills the briefing's lift section; hides it when the company has no armor to bring. */
    static void put(Map<String, Object> props, Readout readout) {
        boolean shown = readout != null && !readout.machines().isEmpty();
        props.put("liftClasses", shown ? "briefing-section lift-block" : "briefing-section lift-block lift-hidden");
        props.put("liftHeader", Strings.get("briefingLiftHeader"));
        if (!shown) {
            props.put("liftSummary", "");
            props.put("liftSummaryClasses", "label heading");
            props.put("liftCompany", "");
            props.put("liftCompanyClasses", "label lift-source-value");
            props.put("liftMission", "");
            props.put("liftMissionClasses", "label lift-source-value");
            props.put("liftMachines", List.of());
            return;
        }
        int dropping = readout.dropping();
        int total = readout.machines().size();
        props.put("liftSummary", Strings.format("briefingLiftSummary", dropping, total));
        props.put("liftSummaryClasses", "label heading " + (dropping == total ? "tone-good"
                : dropping == 0 ? "tone-danger" : "tone-accent"));
        boolean companyLifts = readout.companyBest() != LiftRating.NONE;
        props.put("liftCompany", companyLine(readout));
        // A source with no lift is only a danger when the other brings none either.
        props.put("liftCompanyClasses", "label lift-source-value "
                + sourceTone(companyLifts, readout.missionLift()));
        props.put("liftMission", missionLine(readout));
        props.put("liftMissionClasses", "label lift-source-value "
                + sourceTone(readout.missionLift(), companyLifts));
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < readout.machines().size(); i++) {
            Machine machine = readout.machines().get(i);
            String type = machine.load().isVehicle()
                    ? Strings.format("briefingLiftMachineClass", typeName(machine.load()), machine.liftName())
                    : Strings.format("briefingLiftMachineClass", machine.load().mech().variant().displayName(),
                    machine.liftName());
            cards.add(new Card("briefing-lift-machine-" + i, machine, type, statusLine(machine),
                    "label lift-machine-status " + tone(machine.status())));
        }
        props.put("liftMachines", List.copyOf(cards));
    }

    /** Draws each machine card's doll: wear for a mech, whole for a vehicle. */
    static void install(UiDocument document, MarkupInstance markup, Map<String, Object> props,
                        Function<String, String> copy) {
        if (!(props.get("liftMachines") instanceof List<?> cards)) return;
        for (Object value : cards) {
            Card card = (Card) value;
            DamageDollCanvas.View view = new DamageDollCanvas.View();
            ArmorLoad load = card.machine().load();
            if (load.isVehicle()) view.readStock(load.vehicle().type());
            else view.readDeployment(load.mech());
            document.canvases().set(markup.requireElement(card.id() + ":doll"), new DamageDollCanvas(view, copy));
        }
    }

    static String companyLine(Readout readout) {
        if (readout.boats().isEmpty()) return Strings.get("briefingLiftCompanyNoBoats");
        String rating = LiftReadout.ratingName(readout.companyBest());
        if (readout.companyCarrier() != null) {
            return Strings.format("briefingLiftCompanyCarrier", rating,
                    LiftReadout.carrierName(readout.companyCarrier()),
                    LiftBudget.liftsPerCompanyBoat(readout.companyCarrier().liftRating()));
        }
        if (readout.cradled() == 0) {
            return Strings.format("briefingLiftCompanyNoCradle", readout.boats().size(),
                    readout.boats().get(0).pattern().displayName());
        }
        return Strings.format("briefingLiftCompanyCradled", rating, readout.cradled());
    }

    static String missionLine(Readout readout) {
        if (readout.missionLift()) {
            return Strings.format("briefingLiftMissionHeavy",
                    LiftReadout.ratingName(ShuttleType.VALKYRIE.liftRating()), ShuttleType.VALKYRIE.displayName());
        }
        return Strings.get(readout.dropCall() ? "briefingLiftMissionNone" : "briefingLiftMissionNoCall");
    }

    static String statusLine(Machine machine) {
        return switch (machine.status()) {
            case DROPS -> Strings.get("briefingLiftDrops");
            case NOT_CALLED -> Strings.format("briefingLiftNotCalled", Strings.upper(machine.powerName()));
            case NO_LIFT -> Strings.format("briefingLiftNoLift", Strings.upper(machine.liftName()));
            case LIFT_SPENT -> Strings.get("briefingLiftSpent");
            case NO_DROP_CALL -> Strings.get("briefingLiftNoDropCall");
            case HELD_BACK -> Strings.get("briefingLiftHeldBack");
        };
    }

    private static String sourceTone(boolean lifts, boolean otherLifts) {
        return lifts ? "tone-good" : otherLifts ? "tone-muted" : "tone-danger";
    }

    private static String tone(Status status) {
        return switch (status) {
            case DROPS -> "tone-good";
            case NOT_CALLED -> "tone-accent";
            default -> "tone-danger";
        };
    }

    private static String typeName(ArmorLoad load) {
        return new BayManifest.Entry(0, load).typeName();
    }
}
