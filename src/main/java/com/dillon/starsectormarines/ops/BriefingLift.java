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
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     * @param berths         boat berths the company ship has, flown or not
     * @param missionLift    whether a mission, employer, hull or ship supplies heavy lift
     */
    public record Readout(List<FittedBoat> boats, int berths, FittedBoat companyCarrier, LiftRating companyBest,
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
     * @param berths   boat berths the company ship has, flown or not
     * @param powers   the powers this mission resolved
     * @param selected ids of the powers on the command deck
     * @param mechs    the active lance as it would launch
     * @param vehicles the bay's vehicles
     * @param heldBack the lance's mechs left out of the manifest
     */
    public static Readout read(List<FittedBoat> boats, int berths, List<CommandPower> powers, Collection<String> selected,
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
        return new Readout(fleet, Math.max(berths, fleet.size()), companyCarrier, best, cradled, missionLift,
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

    /** The symbol for a lift class or rating: a weight block sized by class, struck through for none. */
    static String weightSymbol(LiftRating rating) {
        return rating == null || rating == LiftRating.NONE ? "graphics/ui/briefing/weight-none.svg"
                : "graphics/ui/mech-hardware/weight-" + rating.name().toLowerCase(Locale.ROOT) + ".svg";
    }

    static String weightChipClasses(LiftRating rating) {
        return "weight-chip weight-" + (rating == null ? LiftRating.NONE : rating).name().toLowerCase(Locale.ROOT);
    }

    /** One lift carrier: a boat berth, or the mission's heavy lift. */
    record Carrier(String id, String classes, String image, String role, String name, LiftRating rating,
                   SpecSheet hint) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> classes;
                case "imageId" -> id + ":image";
                case "copyId" -> id + ":copy";
                case "roleId" -> id + ":role";
                case "nameId" -> id + ":name";
                case "chipId" -> id + ":chip";
                case "weightId" -> id + ":weight";
                case "image" -> image;
                case "role" -> role;
                case "name" -> name;
                case "chipClasses" -> weightChipClasses(rating);
                case "weight" -> weightSymbol(rating);
                default -> null;
            };
        }
    }

    /** One machine card: its doll, its weight, a warning when lift falls short, and its verdict. */
    /**
     * One machine tile in the field inset: a small doll and two bricks, the weight brick lit red when lift
     * falls short of its class and the warning brick lit when it will not drop. Its words are its hover.
     */
    record Card(String id, Machine machine, SpecSheet hint) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "classes" -> "field-tile console-readout" + (machine.status().drops ? "" : " field-tile-refused");
                case "musterId" -> id + ":muster";
                case "musterClasses" -> "field-absent";
                case "dollId" -> id + ":doll";
                case "dollClasses" -> "field-doll";
                case "bricksId" -> id + ":bricks";
                case "bricksClasses" -> "field-bricks";
                case "liftId" -> id + ":lift";
                case "weightId" -> id + ":weight";
                case "warnId" -> id + ":warn";
                case "warnImageId" -> id + ":warn-image";
                // Lit, the brick drops its class colour for the alarm; the symbol still names the class.
                case "liftClasses" -> liftShort(machine.status()) ? "brick brick-lift brick-lit"
                        : "brick brick-lift " + weightChipClasses(LiftRating.of(machine.load().liftClass()))
                        .replace("weight-chip ", "");
                case "weight" -> weightSymbol(LiftRating.of(machine.load().liftClass()));
                case "warnClasses" -> "brick brick-warn" + (machine.status().drops ? "" : " brick-lit");
                default -> null;
            };
        }
    }

    /** Whether a verdict is lift falling short, which the card marks with a warning. */
    static boolean liftShort(Status status) {
        return status == Status.NO_LIFT || status == Status.LIFT_SPENT || status == Status.NO_DROP_CALL;
    }

    /** Fills the briefing's armor and lift section; hides it when there is no lift and no armor to show. */
    static void put(Map<String, Object> props, Readout readout) {
        boolean shown = readout != null && (readout.berths() > 0 || !readout.machines().isEmpty());
        props.put("liftClasses", shown ? "briefing-section lift-block" : "briefing-section lift-block lift-hidden");
        props.put("liftHeader", Strings.get("briefingLiftHeader"));
        if (!shown) {
            props.put("liftSummary", "");
            props.put("liftSummaryClasses", "label heading");
            props.put("liftCarriers", List.of());
            props.put("liftMachines", List.of());
            BriefingViewModel.putFieldTiles(props);
            return;
        }
        int dropping = readout.dropping();
        int total = readout.machines().size();
        props.put("liftSummary", total == 0 ? "" : Strings.format("briefingLiftSummary", dropping, total));
        props.put("liftSummaryClasses", "label heading " + (dropping == total ? "tone-good"
                : dropping == 0 ? "tone-danger" : "tone-accent"));
        props.put("liftCarriers", carriers(readout));
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < readout.machines().size(); i++) {
            Machine machine = readout.machines().get(i);
            cards.add(new Card("briefing-lift-machine-" + i, machine, machineHint(machine)));
        }
        props.put("liftMachines", List.copyOf(cards));
        BriefingViewModel.putFieldTiles(props);
    }

    /** One slot per berth the ship has, filled or not, then the mission's heavy lift. */
    static List<Carrier> carriers(Readout readout) {
        List<Carrier> carriers = new ArrayList<>();
        String company = companyLine(readout);
        for (int i = 0; i < readout.berths(); i++) {
            String role = Strings.format("briefingLiftBerth", i + 1);
            String id = "briefing-lift-berth-" + i;
            if (i < readout.boats().size()) {
                FittedBoat boat = readout.boats().get(i);
                carriers.add(new Carrier(id, "lift-carrier console-readout", boat.pattern().spritePath, role,
                        boatName(boat), boat.liftRating(), new SpecSheet(boatName(boat), role,
                        boat.pattern().spritePath, "command", List.of(), List.of(
                        Strings.format("briefingLiftBoatRating", LiftReadout.ratingName(boat.liftRating())),
                        company))));
            } else {
                carriers.add(new Carrier(id, "lift-carrier console-readout lift-carrier-empty", null, role,
                        Strings.get("briefingLiftBerthEmpty"), LiftRating.NONE, new SpecSheet(role,
                        Strings.get("briefingLiftBerthEmpty"), null, "command", List.of(),
                        List.of(Strings.get("briefingLiftBerthEmptyNote")))));
            }
        }
        LiftRating mission = readout.missionLift() ? ShuttleType.VALKYRIE.liftRating() : LiftRating.NONE;
        String role = Strings.get("briefingLiftMissionLabel");
        carriers.add(new Carrier("briefing-lift-mission", "lift-carrier console-readout"
                + (readout.missionLift() ? "" : " lift-carrier-empty"), ShuttleType.VALKYRIE.spritePath, role,
                readout.missionLift() ? ShuttleType.VALKYRIE.displayName() : Strings.get("briefingLiftMissionNone"),
                mission, new SpecSheet(role, LiftReadout.ratingName(mission), ShuttleType.VALKYRIE.spritePath,
                "command", List.of(), List.of(missionLine(readout)))));
        return List.copyOf(carriers);
    }

    private static String boatName(FittedBoat boat) {
        return boat.cradled() ? LiftReadout.carrierName(boat) : boat.pattern().displayName();
    }

    private static SpecSheet machineHint(Machine machine) {
        String type = machine.load().isVehicle() ? typeName(machine.load())
                : machine.load().mech().variant().displayName();
        return new SpecSheet(machine.name(), Strings.format("briefingLiftMachineClass", type, machine.liftName()),
                null, "command", List.of(), List.of(statusLine(machine),
                Strings.get("briefingLiftWhy" + machine.status().name())));
    }

    /** Draws each machine card's doll: wear for a mech, whole for a vehicle. */
    static void install(UiDocument document, MarkupInstance markup, Map<String, Object> props) {
        if (!(props.get("liftMachines") instanceof List<?> cards)) return;
        for (Object value : cards) {
            Card card = (Card) value;
            DamageDollCanvas.View view = new DamageDollCanvas.View();
            ArmorLoad load = card.machine().load();
            if (load.isVehicle()) view.readStock(load.vehicle().type());
            else view.readDeployment(load.mech());
            document.canvases().set(markup.requireElement(card.id() + ":doll"), DamageDollCanvas.bare(view));
        }
    }

    /** Hovers on each berth, the mission lift and each machine. */
    static void bindHints(SpecSheetBinder binder, MarkupInstance markup, Map<String, Object> props) {
        for (Object value : (List<?>) props.getOrDefault("liftCarriers", List.of())) {
            Carrier carrier = (Carrier) value;
            binder.bind(markup.requireElement(carrier.id()), carrier.hint());
        }
        for (Object value : (List<?>) props.getOrDefault("liftMachines", List.of())) {
            Card card = (Card) value;
            binder.bind(markup.requireElement(card.id()), card.hint());
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
        return Strings.get(readout.dropCall() ? "briefingLiftMissionNoHeavy" : "briefingLiftMissionNoCall");
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

    private static String typeName(ArmorLoad load) {
        return new BayManifest.Entry(0, load).typeName();
    }
}
