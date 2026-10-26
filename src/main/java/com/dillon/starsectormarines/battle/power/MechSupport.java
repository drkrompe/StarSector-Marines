package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ArmorDropPayload;
import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the company's armor down from its ship's bay: the player picks which machines of
 * the frozen {@link BayManifest} come down on this call, and a shootable carrier flies them in.
 *
 * <p><b>Pacing.</b> A cooldown spaces the calls ({@link #CALL_COOLDOWN_SECONDS}); there are
 * no charges. The mission's {@link LiftBudget} bounds how much armor reaches the ground: a
 * machine no carrier can lift, or whose lift is spent, cannot be picked, and the picker
 * says why. A machine already called (landed, lost, or still in the air) is not aboard and
 * cannot be called again. {@link #availableCalls} is the count still aboard, so a bay with
 * nothing left reads as spent.
 *
 * <p><b>Who flies.</b> Each picked machine spends a lift from the lowest-rated carrier that
 * admits it: the committed cradled company boat, one machine per trip, or the synthetic
 * Valkyrie a mission, employer or fleet hull supplies, which unloads its share at once. A
 * call split across both puts both in the air.
 *
 * <p>The AI commander's drop ({@link MechDropService}) is not this power: it shares only
 * {@link #configureLance} and is paid from its own allowance.
 */
public final class MechSupport extends AirDeliveryPower {

    public static final String ID = "mech_support";
    public static final int LANCE_SIZE = 4;
    /**
     * Seconds between calls. {@code -Dbattle.power.armorDropCooldownSeconds=N} replaces the default for a control run;
     * this is the only place it is read.
     */
    public static final float CALL_COOLDOWN_SECONDS = Math.max(0f,
            Float.parseFloat(System.getProperty("battle.power.armorDropCooldownSeconds", "30")));

    private final List<MechDeploymentSpec> deployments;
    private final List<VehicleDeploymentSpec> vehicles;
    private final BayManifest manifest;
    private final LiftBudget budget;
    /** Manifest entries already called this mission. */
    private final boolean[] called;

    public MechSupport() {
        this(List.of(MechVariant.BULWARK));
    }

    /** Debug/playtest constructor: the given chassis on the synthetic Valkyrie. */
    public MechSupport(List<MechVariant> variants) {
        this(standardDeployments(variants), List.of(), null, true);
    }

    /** Campaign constructor: preserves installed components across the seam. */
    public static MechSupport configured(List<MechDeploymentSpec> deployments) {
        return new MechSupport(deployments, List.of(), null, true);
    }

    /**
     * Campaign constructor with a committed company boat that may fly the calls.
     *
     * @param syntheticLift whether a mission, employer, hull or ship also
     *     supplies a heavy carrier for calls the boat cannot lift
     */
    public static MechSupport configured(List<MechDeploymentSpec> deployments,
                                         FittedBoat companyCarrier, boolean syntheticLift) {
        return new MechSupport(deployments, List.of(), companyCarrier, syntheticLift);
    }

    /** As above, with the bay's vehicles in the manifest beside its mechs. */
    public static MechSupport configured(List<MechDeploymentSpec> deployments,
                                         List<VehicleDeploymentSpec> vehicles,
                                         FittedBoat companyCarrier, boolean syntheticLift) {
        return new MechSupport(deployments, vehicles, companyCarrier, syntheticLift);
    }

    private MechSupport(List<MechDeploymentSpec> deployments,
                        List<VehicleDeploymentSpec> vehicles,
                        FittedBoat companyCarrier, boolean syntheticLift) {
        super(ID, "@battlePowerMechSupport", 4f, CALL_COOLDOWN_SECONDS, 0, 0, 3,
                ShuttleType.VALKYRIE, ArmorDropPayload.INSTANCE, 5,
                companyCarrier, syntheticLift);
        List<VehicleDeploymentSpec> validVehicles = new ArrayList<>();
        if (vehicles != null) {
            for (VehicleDeploymentSpec vehicle : vehicles) {
                if (vehicle != null) validVehicles.add(vehicle);
            }
        }
        this.vehicles = Collections.unmodifiableList(validVehicles);
        this.deployments = Collections.unmodifiableList(new ArrayList<>(
                validVehicles.isEmpty() ? validDeployments(deployments)
                        : nonNull(deployments)));
        this.manifest = BayManifest.of(this.deployments, this.vehicles);
        this.budget = LiftBudget.forMission(companyCarrier, syntheticLift,
                ShuttleType.VALKYRIE, manifest.size());
        this.called = new boolean[manifest.size()];
    }

    /** Frozen, ordered mech payload in the manifest. */
    public List<MechDeploymentSpec> deployments() {
        return deployments;
    }

    /** Frozen bay vehicles in the manifest, after the mechs. */
    public List<VehicleDeploymentSpec> vehicles() {
        return vehicles;
    }

    public BayManifest manifest() {
        return manifest;
    }

    /** This mission's lift: what has been spent, and what is left. */
    public LiftBudget budget() {
        return budget;
    }

    /** Whether manifest entry {@code index} has already been called this mission. */
    public boolean called(int index) {
        return index >= 0 && index < called.length && called[index];
    }

    @Override
    public int availableCalls() {
        int aboard = 0;
        for (boolean c : called) if (!c) aboard++;
        return aboard;
    }

    /**
     * One manifest entry as the picker shows it.
     *
     * @param refusal why the entry cannot be in this call ({@code null} when it can); for a
     *     selected entry, why it cannot stay with the rest of the pick
     */
    public record PickerRow(BayManifest.Entry entry, boolean called, boolean selected,
                            LiftBudget.Refusal refusal) {
        /** Aboard, and lift allows it alongside the rest of the pick. */
        public boolean callable() {
            return !called && refusal == null;
        }
    }

    /** Every manifest entry against {@code selection} (null: the default pick). */
    public List<PickerRow> picker(BattleView battle, List<Integer> selection) {
        List<Integer> picks = picks(selection, battle);
        boolean home = battle == null || companyAvailable(battle);
        List<PickerRow> rows = new ArrayList<>(manifest.size());
        for (BayManifest.Entry entry : manifest.entries()) {
            if (called[entry.index()]) {
                rows.add(new PickerRow(entry, true, false, null));
                continue;
            }
            boolean selected = picks.contains(entry.index());
            List<LiftClass> others = new ArrayList<>();
            for (int pick : picks) {
                if (pick != entry.index()) others.add(manifest.get(pick).weightClass());
            }
            rows.add(new PickerRow(entry, false, selected,
                    budget.refusal(entry.weightClass(), others, home)));
        }
        return List.copyOf(rows);
    }

    /**
     * What a call brings when the player has not picked: every machine still aboard that
     * lift allows, in manifest order, up to a lance.
     */
    public List<Integer> defaultSelection(BattleView battle) {
        boolean home = battle == null || companyAvailable(battle);
        List<Integer> picks = new ArrayList<>();
        List<LiftClass> load = new ArrayList<>();
        for (BayManifest.Entry entry : manifest.entries()) {
            if (picks.size() >= LANCE_SIZE) break;
            if (called[entry.index()]) continue;
            load.add(entry.weightClass());
            if (budget.allocate(load, home) != null) {
                picks.add(entry.index());
            } else {
                load.remove(load.size() - 1);
            }
        }
        return List.copyOf(picks);
    }

    /** {@code selection} reduced to distinct entries still aboard, in manifest order. */
    private List<Integer> picks(List<Integer> selection, BattleView battle) {
        if (selection == null) return defaultSelection(battle);
        List<Integer> picks = new ArrayList<>();
        for (BayManifest.Entry entry : manifest.entries()) {
            if (!called[entry.index()] && selection.contains(entry.index())) {
                picks.add(entry.index());
            }
        }
        return picks;
    }

    private List<LiftClass> load(List<Integer> picks) {
        List<LiftClass> load = new ArrayList<>(picks.size());
        for (int pick : picks) load.add(manifest.get(pick).weightClass());
        return load;
    }

    /** A machine still aboard that some remaining lift admits, counting the boat home again. */
    @Override
    public boolean canStillField(BattleView battle) {
        boolean company = companyCarrierHeld(battle);
        for (int pick = 0; pick < called.length; pick++) {
            if (!called[pick] && budget.allocate(load(List.of(pick)), company) != null) return true;
        }
        return false;
    }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return canTarget(cellX, cellY, battle, null);
    }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle, List<Integer> selection) {
        if (battle == null) return false;
        List<Integer> picks = picks(selection, battle);
        if (picks.isEmpty()) return false;
        if (budget.allocate(load(picks), companyAvailable(battle)) == null) return false;
        return landingZoneNear(cellX, cellY, battle) != null;
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        resolve(cellX, cellY, service, battle, null);
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service,
                        BattleControl battle, List<Integer> selection) {
        int[] lz = landingZoneNear(cellX, cellY, battle);
        if (lz == null) return;
        Map<LiftBudget.Carrier, List<ArmorLoad>> sorties =
                commitCall(picks(selection, battle), companyAvailable(battle));
        for (Map.Entry<LiftBudget.Carrier, List<ArmorLoad>> sortie : sorties.entrySet()) {
            LiftBudget.Carrier carrier = sortie.getKey();
            ShuttleMission mission = launch(carrier.frame(), lz, ArmorDropPayload.INSTANCE, battle);
            loadArmor(mission, sortie.getValue(), carrier.company());
        }
    }

    /**
     * Commits a call of {@code picks}: spends their lift, marks them called, and returns what
     * each carrier flies, in allocation order. Empty, with nothing spent, when there is no
     * pick, a pick has already been called, or lift cannot carry all of it.
     */
    Map<LiftBudget.Carrier, List<ArmorLoad>> commitCall(List<Integer> picks,
                                                        boolean companyAvailable) {
        if (picks.isEmpty()) return Map.of();
        for (int pick : picks) if (called[pick]) return Map.of();
        List<LiftBudget.Carrier> allocation = budget.allocate(load(picks), companyAvailable);
        if (allocation == null) return Map.of();
        Map<LiftBudget.Carrier, List<ArmorLoad>> sorties = new LinkedHashMap<>();
        for (int i = 0; i < picks.size(); i++) {
            sorties.computeIfAbsent(allocation.get(i), carrier -> new ArrayList<>())
                    .add(manifest.get(picks.get(i)).load());
        }
        budget.spend(allocation);
        for (int pick : picks) called[pick] = true;
        return sorties;
    }

    /**
     * Puts {@code loads} aboard. A cradled company boat carries one machine and nobody else,
     * so it flies a trip per machine; the synthetic carrier unloads them all at once.
     */
    static void loadArmor(ShuttleMission mission, List<ArmorLoad> loads, boolean cradled) {
        mission.armorDrop = loads.toArray(new ArmorLoad[0]);
        List<MechDeploymentSpec> mechs = new ArrayList<>();
        for (ArmorLoad load : loads) {
            if (load.isVehicle()) {
                if (mission.vehicleDeployment == null) mission.vehicleDeployment = load.vehicle();
            } else {
                mechs.add(load.mech());
            }
        }
        // Threat pictures read these to know a carrier holds a mech.
        if (!mechs.isEmpty()) {
            mission.mechDeployments = mechs.toArray(new MechDeploymentSpec[0]);
            mission.mechVariants = mechs.stream()
                    .map(MechDeploymentSpec::variant).toArray(MechVariant[]::new);
            mission.mechVariant = mechs.get(0).variant();
        }
        if (cradled) {
            mission.totalCycles = loads.size();
            mission.marinesRemaining = 1;
        } else {
            mission.marinesRemaining = loads.size();
        }
    }

    /**
     * Loads {@code lance} into a carrier that is already in the air. Shared by the player's
     * power and a commander's call: what is unloaded is one set of mechs either way.
     */
    static void configureLance(ShuttleMission mission, List<MechDeploymentSpec> lance) {
        mission.mechDeployments = lance.toArray(new MechDeploymentSpec[0]);
        mission.mechVariants = lance.stream()
                .map(MechDeploymentSpec::variant).toArray(MechVariant[]::new);
        mission.mechVariant = lance.get(0).variant();
        mission.marinesRemaining = lance.size();
    }

    private static List<MechDeploymentSpec> standardDeployments(List<MechVariant> variants) {
        List<MechDeploymentSpec> deployments = new ArrayList<>();
        if (variants != null) {
            for (MechVariant variant : variants) {
                if (variant != null) deployments.add(MechDeploymentSpec.standard(variant));
            }
        }
        return validDeployments(deployments);
    }

    private static List<MechDeploymentSpec> nonNull(List<MechDeploymentSpec> deployments) {
        List<MechDeploymentSpec> valid = new ArrayList<>();
        if (deployments != null) {
            for (MechDeploymentSpec deployment : deployments) {
                if (deployment != null) valid.add(deployment);
            }
        }
        return valid;
    }

    private static List<MechDeploymentSpec> validDeployments(
            List<MechDeploymentSpec> deployments) {
        List<MechDeploymentSpec> valid = nonNull(deployments);
        return valid.isEmpty()
                ? List.of(MechDeploymentSpec.standard(MechVariant.BULWARK)) : valid;
    }
}
