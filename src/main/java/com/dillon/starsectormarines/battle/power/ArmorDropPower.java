package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.ArmorDropPayload;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.LiftQueue;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A drop power that puts the company's machines down one at a time from its frozen
 * {@link BayManifest}, on the player's ferry ({@code mechs-nouns.md}, Armor drop).
 *
 * <p><b>One machine per order.</b> The player picks a machine still aboard and places it
 * on the landing ground; the order joins the {@link LiftQueue} behind whatever is already
 * waiting, and the first ferry craft whose lift rating admits it flies it in. Each landed
 * machine is its own squad. Orders are free: no command points and no cooldown. The
 * mission's {@link LiftBudget} bounds how much comes down; a machine whose lift is spent,
 * that no carrier this mission lifts, or that no surviving ferry craft can fly, cannot be
 * picked, and the picker says why.
 *
 * <p><b>Cancel.</b> A drop still waiting on the queue can be cancelled: the machine goes
 * back aboard and its lift is given back. A drop a craft has taken is in the air and is
 * the craft's.
 *
 * <p><b>Who flies.</b> Only ferry craft. A committed cradled company boat, or the heavy
 * lift a mission, employer or hull supplies, joins the ferry as a craft of its own when no
 * ferry craft already lifts its class. Standing by, a company boat lands squad sorties the
 * ferry still owes before it flies any machine ({@link LiftQueue#takeSquadSortie}); supplied
 * lift flies machines only, so the company's marines land in the company's own boats.
 */
public abstract class ArmorDropPower extends AirDeliveryPower {

    /** Open neighbours a dropped machine's landing cell needs. */
    static final int DROP_CLEARANCE = 5;

    /** What a manifest entry is doing now. */
    public enum Status {
        /** In the bay, free to order down. */
        ABOARD,
        /** Ordered and waiting on the lift queue; can still be cancelled. */
        QUEUED,
        /** In the air, landed or lost: gone from the bay for the mission. */
        CALLED
    }

    /**
     * One manifest entry as the picker shows it.
     *
     * @param selected whether it is the machine the player has picked to place
     * @param refusal  why an aboard entry cannot be ordered now ({@code null} when it can)
     */
    public record PickerRow(BayManifest.Entry entry, Status status, boolean selected,
                            LiftBudget.Refusal refusal) {
        /** Aboard, and lift allows it. */
        public boolean callable() {
            return status == Status.ABOARD && refusal == null;
        }
    }

    private final BayManifest manifest;
    private final LiftBudget budget;
    private final FittedBoat companyCarrier;
    private final boolean heavyLift;
    /** Manifest entries ordered this mission (queued or beyond). */
    private final boolean[] called;
    /** The budget carrier each waiting job spent, for a cancel's refund. */
    private final Map<Long, LiftBudget.Carrier> charged = new LinkedHashMap<>();
    private boolean prepared;

    /**
     * @param companyCarrier a committed cradled company boat whose lift counts in the
     *     budget, or {@code null}
     * @param heavyLift whether heavy lift stands behind the company boat (Conquest only); with
     *     neither, every machine is refused for want of lift
     */
    protected ArmorDropPower(String id, String displayName, int slotWeight,
                             List<MechDeploymentSpec> mechs, List<VehicleDeploymentSpec> vehicles,
                             FittedBoat companyCarrier, boolean heavyLift) {
        super(id, displayName, 0f, 0f, 0, 0, slotWeight, ShuttleType.VALKYRIE,
                ArmorDropPayload.INSTANCE, DROP_CLEARANCE);
        this.manifest = BayManifest.of(mechs, vehicles);
        this.companyCarrier = companyCarrier;
        this.heavyLift = heavyLift;
        this.budget = LiftBudget.forMission(companyCarrier, heavyLift,
                ShuttleType.VALKYRIE, manifest.size());
        this.called = new boolean[manifest.size()];
    }

    public BayManifest manifest() {
        return manifest;
    }

    /** This mission's lift: what has been spent, and what is left. */
    public LiftBudget budget() {
        return budget;
    }

    /** The committed company boat whose lift counts in the budget, or {@code null}. */
    public FittedBoat companyCarrier() {
        return companyCarrier;
    }

    /** Whether heavy lift stands behind the company's own. */
    public boolean syntheticLift() {
        return heavyLift;
    }

    /** Whether manifest entry {@code index} has been ordered this mission (queued or beyond). */
    public boolean called(int index) {
        return index >= 0 && index < called.length && called[index];
    }

    @Override
    public int availableCalls() {
        int aboard = 0;
        for (boolean c : called) if (!c) aboard++;
        return aboard;
    }

    /** What entry {@code index} is doing, reading the queue for an order still waiting. */
    public Status status(int index, LiftQueue queue) {
        if (!called(index)) return Status.ABOARD;
        return queue != null && queue.isQueued(id, index) ? Status.QUEUED : Status.CALLED;
    }

    /** Every manifest entry, with {@code pick} (a manifest index, or null) marked selected. */
    public List<PickerRow> picker(BattleView battle, Integer pick) {
        LiftQueue queue = battle == null ? null : battle.liftQueue();
        List<PickerRow> rows = new ArrayList<>(manifest.size());
        for (BayManifest.Entry entry : manifest.entries()) {
            Status status = status(entry.index(), queue);
            LiftBudget.Refusal refusal = status == Status.ABOARD
                    ? refusal(entry.weightClass(), battle) : null;
            rows.add(new PickerRow(entry, status, pick != null && pick == entry.index(), refusal));
        }
        return List.copyOf(rows);
    }

    /**
     * A machine still aboard that lift and a live ferry craft can bring down. A drop already
     * queued is the lift queue's to count ({@code ObjectiveFactionPresence}), since it is no
     * longer aboard.
     */
    @Override
    public boolean canStillField(BattleView battle) {
        for (BayManifest.Entry entry : manifest.entries()) {
            if (!called[entry.index()] && refusal(entry.weightClass(), battle) == null) return true;
        }
        return false;
    }

    /** The first machine still aboard that lift allows, or {@code null} when there is none. */
    public Integer defaultPick(BattleView battle) {
        for (BayManifest.Entry entry : manifest.entries()) {
            if (!called[entry.index()] && refusal(entry.weightClass(), battle) == null) {
                return entry.index();
            }
        }
        return null;
    }

    /** Why a machine of {@code payload}'s class cannot be ordered now, or {@code null}. */
    LiftBudget.Refusal refusal(LiftClass payload, BattleView battle) {
        LiftBudget.Refusal refusal = budget.refusal(payload);
        if (refusal != null) return refusal;
        if (prepared && battle != null && battle.world() != null
                && !LiftQueue.ferryAdmits(battle.world(), battle.getAirEntityIds(), payload)) {
            return LiftBudget.Refusal.NO_CRAFT;
        }
        return null;
    }

    /** The one machine a pick names, or {@code null} when it names none or several. */
    static Integer single(List<Integer> selection) {
        return selection != null && selection.size() == 1 ? selection.get(0) : null;
    }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle) {
        return false;
    }

    @Override
    public boolean canTarget(int cellX, int cellY, BattleView battle, List<Integer> selection) {
        Integer pick = single(selection);
        if (battle == null || pick == null || pick < 0 || pick >= manifest.size()) return false;
        if (called[pick] || refusal(manifest.get(pick).weightClass(), battle) != null) return false;
        return landingZoneNear(cellX, cellY, battle) != null;
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service, BattleControl battle) {
        // An armor drop always names its machine; there is no default order.
    }

    @Override
    public void resolve(int cellX, int cellY, CommandPowerService service,
                        BattleControl battle, List<Integer> selection) {
        Integer pick = single(selection);
        int[] lz = landingZoneNear(cellX, cellY, battle);
        if (pick == null || lz == null || battle.liftQueue() == null) return;
        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        order(pick, lzX, lzY, routeFromNearestEdge(lzX, lzY, battle.getGrid()),
                battle.playerSupportCommandClaim(), battle.liftQueue());
    }

    /**
     * Orders manifest entry {@code index} down at {@code (lzX, lzY)}: spends its lift, marks
     * it called and queues it. {@code null}, with nothing spent, when it is not aboard or lift
     * refuses it.
     */
    LiftQueue.MachineDrop order(int index, float lzX, float lzY, float[] route,
                                SquadCommandClaim claim, LiftQueue queue) {
        if (index < 0 || index >= manifest.size() || called[index]) return null;
        LiftBudget.Carrier carrier = budget.allocate(manifest.get(index).weightClass());
        if (carrier == null) return null;
        budget.spend(carrier);
        called[index] = true;
        queue.activate();
        LiftQueue.MachineDrop job = queue.enqueue(id, index, manifest.get(index).load(),
                lzX, lzY, route, claim);
        charged.put(job.id(), carrier);
        return job;
    }

    /**
     * Cancels a drop of this power still waiting on {@code queue}: the machine goes back
     * aboard and its lift is given back. False when no craft-free job of this power has
     * that id (already taken, or never this power's).
     */
    public boolean cancel(long jobId, LiftQueue queue) {
        LiftQueue.MachineDrop job = queue.find(jobId);
        if (job == null || !id.equals(job.powerId())) return false;
        queue.cancel(jobId);
        called[job.manifestIndex()] = false;
        LiftBudget.Carrier carrier = charged.remove(jobId);
        if (carrier != null) budget.refund(carrier);
        return true;
    }

    /**
     * Readies the ferry for this power on the battle's first tick: turns the queue on, marks
     * the transports landing the company as ferry, and puts each budget carrier no ferry
     * craft already matches into the ferry as a standby craft of its own.
     */
    void prepare(BattleControl battle) {
        if (prepared || battle == null || battle.liftQueue() == null) return;
        prepared = true;
        LiftQueue queue = battle.liftQueue();
        queue.activate();
        LiftQueue.enlist(battle.world(), battle.getAirEntityIds());
        for (LiftBudget.Carrier carrier : budget.carriers()) {
            if (LiftQueue.ferryRated(battle.world(), battle.getAirEntityIds(), carrier.rating())) {
                continue;
            }
            joinFerry(battle, carrier.frame(), !carrier.company());
        }
    }

    /**
     * Puts {@code frame} in the ferry as a standby craft waiting off-map for a job; lift the
     * company does not own ({@code machinesOnly}) flies machines and never a squad.
     */
    private static void joinFerry(BattleControl battle, Airframe frame, boolean machinesOnly) {
        ShuttleType pattern = ShuttleType.fromAirframe(frame);
        if (pattern == null) return;
        float x = battle.getGrid().getWidth() * 0.5f;
        float[] route = routeFromNearestEdge(x, 0.5f, battle.getGrid());
        long craft = battle.spawnShuttle(pattern, frame, Faction.MARINE, x, 0.5f,
                route[0], route[1], route[2], route[3], 0f, 1);
        ShuttleMission mission = battle.world().mission(craft);
        mission.ferry = true;
        mission.machinesOnly = machinesOnly;
        mission.payload = ArmorDropPayload.INSTANCE;
        LiftQueue.standBy(mission, 0f);
    }

    /** Entries in {@code deployments} that are not null. */
    static <T> List<T> nonNull(List<T> values) {
        List<T> valid = new ArrayList<>();
        if (values != null) {
            for (T value : values) if (value != null) valid.add(value);
        }
        return valid;
    }
}
