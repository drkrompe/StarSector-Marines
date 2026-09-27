package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opt-in input script, plus post-tick population evidence. Neither runs inside the tick timer. */
final class TailMechSupport {
    final Schedule schedule;
    final Census census = new Census();
    private final int warmupTicks;
    private final JSONArray events = new JSONArray();
    private final List<ShuttleMission> deliveries = new ArrayList<>();
    private final Set<Long> previousAir = new HashSet<>();
    private final Set<Long> observedSupport = new HashSet<>();
    private final Set<Integer> supportSquads = new HashSet<>();
    private JSONObject pendingEvent;
    private JSONArray frozenPayload = new JSONArray();
    private int payloadCount;
    private int initialCharges;
    private int expectedDelivered;
    private int delivered;
    private int readinessDeferredTicks;
    private int firstSupportTick = -1;

    TailMechSupport(int activations, int firstTick, int intervalTicks, int warmupTicks) {
        schedule = new Schedule(activations, firstTick, intervalTicks);
        this.warmupTicks = warmupTicks;
    }

    static TailMechSupport configured(int warmupTicks) {
        return new TailMechSupport(Integer.getInteger("battle.tail.mechSupportActivations", 0),
                Integer.getInteger("battle.tail.mechSupportFirstTick", warmupTicks + 1),
                Integer.getInteger("battle.tail.mechSupportIntervalTicks", 240), warmupTicks);
    }

    void initialize(BattleSimulation sim) throws Exception {
        CommandPower power = sim.getCommandPowerService().getPower(MechSupport.ID);
        initialCharges = sim.getCommandPowerService().getChargesRemaining(MechSupport.ID);
        if (power instanceof MechSupport support) {
            payloadCount = support.deployments().size();
            for (MechDeploymentSpec spec : support.deployments()) {
                frozenPayload.put(new JSONObject().put("variant", spec.variant().id)
                        .put("role", spec.role().name()));
            }
        }
    }

    void beforeTick(BattleSimulation sim, int tick) throws Exception {
        if (!schedule.due(tick)) return;
        CommandPowerService service = sim.getCommandPowerService();
        CommandPower power = service.getPower(MechSupport.ID);
        if (!(power instanceof MechSupport)) {
            rejected(tick, "MISSING_FROZEN_POWER");
            return;
        }
        if (!service.canActivate(power)) {
            // No repeated failed UI requests: wait for ordinary CP/cooldown readiness.
            readinessDeferredTicks++;
            return;
        }
        List<LandingPad> pads = sim.getMarineLandingPads();
        LandingPad target = null;
        for (int offset = 0; offset < pads.size(); offset++) {
            LandingPad pad = pads.get((schedule.attempted + offset) % pads.size());
            if (power.canTarget(pad.centerX, pad.centerY, sim)) { target = pad; break; }
        }
        if (target == null) {
            rejected(tick, "NO_LEGAL_SETUP_PAD");
            return;
        }
        previousAir.clear();
        for (long id : sim.getAirEntityIds()) previousAir.add(id);
        schedule.submit(tick, service.getChargesRemaining(MechSupport.ID));
        pendingEvent = new JSONObject().put("tick", tick).put("targetX", target.centerX)
                .put("targetY", target.centerY).put("chargesBefore", schedule.chargesBefore);
        service.requestActivation(MechSupport.ID, target.centerX, target.centerY);
    }

    private void rejected(int tick, String reason) throws Exception {
        schedule.reject(tick);
        events.put(new JSONObject().put("tick", tick).put("status", reason));
    }

    void afterTick(BattleSimulation sim, int tick) throws Exception {
        if (schedule.pending) {
            int charges = sim.getCommandPowerService().getChargesRemaining(MechSupport.ID);
            boolean committed = schedule.resolve(charges);
            pendingEvent.put("chargesAfter", charges)
                    .put("status", committed ? "COMMITTED" : "REJECTED_IN_TICK");
            int carriers = 0;
            if (committed) {
                for (long id : sim.getAirEntityIds()) {
                    if (previousAir.contains(id)) continue;
                    ShuttleMission mission = sim.world().mission(id);
                    if (mission == null || mission.payload != MechSupportPayload.INSTANCE
                            || mission.rescuePickupMechTransport) continue;
                    deliveries.add(mission);
                    expectedDelivered += mission.mechDeployments == null ? 0 : mission.mechDeployments.length;
                    carriers++;
                }
            }
            pendingEvent.put("observedNewCarriers", carriers);
            events.put(pendingEvent);
            pendingEvent = null;
        }
        supportSquads.clear();
        int totalDeboarded = 0;
        for (ShuttleMission mission : deliveries) {
            totalDeboarded += mission.deboardedThisSortie;
            if (mission.squadId != Squad.NO_SQUAD) supportSquads.add(mission.squadId);
        }
        // A one-sortie support carrier can disappear after delivery; retain its mission witness.
        delivered = Math.max(delivered, totalDeboarded);
        census.beginTick();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().type(id) != UnitType.HEAVY_MECH || !sim.world().isAlive(id)) continue;
            MechLoadoutComponent loadout = sim.world().mechLoadout(id);
            if (loadout == null) continue; // Unfinished gantry frames are not doctrine coverage.
            Squad squad = sim.squadOf(id);
            boolean support = squad != null && supportSquads.contains(squad.id);
            if (support && observedSupport.add(id) && firstSupportTick < 0) firstSupportTick = tick;
            census.observe(id, sim.identity().faction(id).name(), loadout.variant.id,
                    loadout.effectiveRole().name(), support, tick > warmupTicks);
        }
        census.endTick();
    }

    String insufficientCoverage() {
        if (schedule.requested == 0) return "";
        if (schedule.committed != schedule.requested) return "Requested " + schedule.requested
                + " support activations, but only " + schedule.committed + " committed";
        if (delivered == 0 || observedSupport.isEmpty()) return "Support was requested but no landed combat mech was observed";
        return "";
    }

    JSONObject json() throws Exception {
        return new JSONObject().put("requestedActivations", schedule.requested)
                .put("firstActivationTick", schedule.firstTick).put("minimumIntervalTicks", schedule.interval)
                .put("attemptedActivations", schedule.attempted).put("committedActivations", schedule.committed)
                .put("failedAttempts", schedule.attempted - schedule.committed)
                .put("unattemptedActivations", schedule.requested - schedule.attempted)
                .put("readinessDeferredTicks", readinessDeferredTicks)
                .put("initialCharges", initialCharges).put("frozenPayloadCount", payloadCount)
                .put("frozenPayload", frozenPayload).put("activationEvents", events)
                .put("observedSupportCarriers", deliveries.size()).put("expectedDeliveredMechs", expectedDelivered)
                .put("deboardedSupportMechs", delivered).put("uniqueObservedSupportMechs", observedSupport.size())
                .put("firstObservedSupportTick", firstSupportTick)
                .put("coverageFailure", insufficientCoverage()).put("population", census.json())
                .put("semantics", "Default zero activations is unchanged no-input playback. Requests use the frozen power and normal CP, charge, targeting and physical delivery rules; each activation delivers the next up-to-four payload entries, not the whole payload. Targets rotate among legal setup landing pads. Census runs outside tick timing; peaks/unique identities include warmup, unitTicks and supportUnitTicks exclude warmup. Population is not proof that a role action executed: consult actionTotals separately. Deboarded counts use tracked new support carrier missions, not marine works output. Partial delivery is reported, not inferred from committed calls.");
    }

    /** Scheduling/bookkeeping seam with no simulation dependencies. Failed attempts consume a script slot. */
    static final class Schedule {
        final int requested, firstTick, interval;
        int attempted, committed, chargesBefore;
        long nextTick;
        boolean pending;
        Schedule(int requested, int firstTick, int interval) {
            if (requested < 0 || firstTick < 1 || interval < 1) throw new IllegalArgumentException("Invalid support schedule");
            this.requested = requested; this.firstTick = firstTick; this.interval = interval; nextTick = firstTick;
        }
        boolean due(int tick) { return !pending && attempted < requested && tick >= nextTick; }
        void submit(int tick, int charges) { reject(tick); chargesBefore = charges; pending = true; }
        void reject(int tick) { attempted++; nextTick = (long) tick + interval; }
        boolean resolve(int chargesAfter) {
            if (!pending) throw new IllegalStateException("No queued activation");
            pending = false;
            boolean accepted = chargesBefore > 0 && chargesAfter == chargesBefore - 1;
            if (accepted) committed++;
            return accepted;
        }
    }

    /** Joined-thread census: actual body identity, not an action-name heuristic. */
    static final class Census {
        private final List<Population> rows = new ArrayList<>();
        void beginTick() { for (Population row : rows) row.current = 0; }
        void observe(long id, String faction, String variant, String role, boolean support, boolean measured) {
            Population row = null;
            for (Population candidate : rows) {
                if (candidate.faction.equals(faction) && candidate.variant.equals(variant)
                        && candidate.role.equals(role)) { row = candidate; break; }
            }
            if (row == null) { row = new Population(faction, variant, role); rows.add(row); }
            row.current++; row.ids.add(id);
            if (measured) { row.unitTicks++; if (support) row.supportUnitTicks++; }
        }
        void endTick() { for (Population row : rows) row.peak = Math.max(row.peak, row.current); }
        JSONArray json() throws Exception {
            JSONArray result = new JSONArray();
            for (Population row : rows) result.put(new JSONObject().put("faction", row.faction)
                    .put("variant", row.variant).put("effectiveRole", row.role)
                    .put("peakLive", row.peak).put("liveAtEnd", row.current).put("uniqueObserved", row.ids.size())
                    .put("unitTicks", row.unitTicks).put("supportUnitTicks", row.supportUnitTicks));
            return result;
        }
    }
    private static final class Population {
        final String faction, variant, role;
        final Set<Long> ids = new HashSet<>();
        int current, peak;
        long unitTicks, supportUnitTicks;
        Population(String faction, String variant, String role) {
            this.faction = faction; this.variant = variant; this.role = role;
        }
    }
}
