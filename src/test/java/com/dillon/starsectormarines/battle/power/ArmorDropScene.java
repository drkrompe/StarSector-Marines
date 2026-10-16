package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ArmorLoad;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.BoatFitting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calling armor from the bay manifest: the player picks which machines come down, a cooldown spaces the calls, and
 * the mission's lift decides what can be picked at all.
 *
 * <p>One loop, {@code pick-and-pace}. The bay holds a Hound ("Ironside"), a Bulwark ("Anvil") and a Heavy APC
 * ("Mule"); the only lift is the company's cradled Buffalo, no ship lift. The script, on the player's own surface:
 * <ol>
 *   <li>tick {@value #BULWARK_TICK}: the Bulwark alone is asked for. The cradle is light lift, so the picker refuses
 *       it ({@code NO_LIFT}) and the request starts no cooldown and flies nothing;</li>
 *   <li>tick {@value #HOUND_TICK}: the Hound alone. Only the Hound should fly, the rest stay aboard;</li>
 *   <li>tick {@value #EARLY_VEHICLE_TICK}: the APC, inside the cooldown. Refused, nothing flies;</li>
 *   <li>once the cooldown has lapsed and the picker says the APC is callable (the Buffalo home): the APC, which
 *       should land as a vehicle.</li>
 * </ol>
 */
public final class ArmorDropScene implements BehaviorScene {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 68;
    private static final int TICKS = 2100;
    private static final int ZONE_X = 40;
    private static final int ZONE_Y = 34;
    static final int BULWARK_TICK = 30;
    static final int HOUND_TICK = 60;
    static final int EARLY_VEHICLE_TICK = 600;
    static final int FRAME_EVERY = 30;
    private static final String RIFLE = "weapon.smg";
    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");
    private static final int HOUND = 0;
    private static final int BULWARK = 1;
    private static final int MULE = 2;

    @Override public String id() { return "armor-drop"; }

    @Override public String label() {
        return "Armor drop: the player picks a Hound off the bay manifest, a Bulwark is refused for want of heavy "
                + "lift, the cooldown holds an early call, and the APC lands once it lapses";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        Trace trace = playOne(frames);
        return List.of(report(trace));
    }

    private static MechDeploymentSpec named(MechVariant variant, String callsign) {
        return new MechDeploymentSpec(variant, variant.defaultRole, MissileReplenisherComponent.STANDARD,
                variant.arms, variant.leftShoulder, variant.rightShoulder, callsign);
    }

    private Trace playOne(FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 4, HEIGHT - 4,
                spec -> spec.moveSpeed(0f));
        builder.squad("company").faction(Faction.MARINE).size(4).at(ZONE_X - 8, ZONE_Y).primary(RIFLE).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);

        MechSupport power = MechSupport.configured(
                List.of(named(MechVariant.HOUND, "Ironside"), named(MechVariant.BULWARK, "Anvil")),
                List.of(new VehicleDeploymentSpec("vehicle-mule", "Mule", VehicleType.HEAVY_APC)),
                CRADLED_BUFFALO, false);
        sim.setCommandPowers(List.of(power));

        Trace trace = new Trace(power);
        SceneRun.of(world).observe(trace)
                .frames(frames, "pick-and-pace", FRAME_EVERY, tick -> String.format(Locale.ROOT,
                        "armor drop / tick %d / %s", tick, trace.caption()))
                .run(TICKS);
        trace.finish(sim);
        return trace;
    }

    private SceneReport report(Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("bulwark-refused-no-lift",
                t.bulwarkRefusal == LiftBudget.Refusal.NO_LIFT && !t.bulwarkCanTarget
                        && t.cooldownAfterBulwark <= 0f && t.airAfterBulwark == 0 && !t.bulwarkCalledAfterRequest,
                "picker refusal " + t.bulwarkRefusal + ", canTarget " + t.bulwarkCanTarget
                        + ", cooldown after " + fmt(t.cooldownAfterBulwark) + "s, carriers in the air "
                        + t.airAfterBulwark + ", called " + t.bulwarkCalledAfterRequest));
        verdicts.add(Verdict.of("hound-landed", t.firstMechVariant == MechVariant.HOUND,
                "first marine mech " + t.firstMechVariant + " at tick " + t.firstMechTick
                        + " from carrier " + t.houndCarrier));
        verdicts.add(Verdict.of("only-picked-dispatched",
                t.houndCarrierLoads.equals(List.of("Ironside")) && !t.calledAfterHound[BULWARK]
                        && !t.calledAfterHound[MULE] && t.calledAfterHound[HOUND],
                "first sortie aboard " + t.houndCarrierLoads + "; called after the Hound call: hound "
                        + t.calledAfterHound[HOUND] + ", bulwark " + t.calledAfterHound[BULWARK] + ", mule "
                        + t.calledAfterHound[MULE]));
        verdicts.add(Verdict.of("cooldown-held",
                t.cooldownAtEarlyRequest > 0f && !t.muleCalledAfterEarly && t.sortiesAfterEarly == 1,
                "cooldown at the early request " + fmt(t.cooldownAtEarlyRequest) + "s, mule called "
                        + t.muleCalledAfterEarly + ", sorties launched by then " + t.sortiesAfterEarly));
        verdicts.add(Verdict.of("vehicle-landed-after-cooldown",
                t.muleRequestTick > 0 && t.cooldownAtMuleRequest <= 0f && t.vehicleLandedTick > t.muleRequestTick
                        && t.vehicleType == VehicleType.HEAVY_APC && t.muleCarrierLoads.equals(List.of("Mule")),
                "APC requested at tick " + t.muleRequestTick + " (cooldown " + fmt(t.cooldownAtMuleRequest)
                        + "s, carriers in the air " + t.airAtMuleRequest + "), sortie aboard " + t.muleCarrierLoads
                        + ", marine vehicle " + t.vehicleType + " on the ground at tick " + t.vehicleLandedTick));
        verdicts.add(Verdict.of("bulwark-still-aboard",
                !t.bulwarkCalledAtEnd && t.bulwarkRefusalAtEnd == LiftBudget.Refusal.NO_LIFT && t.bulwarksSeen == 0,
                "called " + t.bulwarkCalledAtEnd + ", picker refusal at the end " + t.bulwarkRefusalAtEnd
                        + ", bulwarks seen on the field " + t.bulwarksSeen));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("houndCallTick", HOUND_TICK);
        metrics.put("houndCarrierTick", t.houndCarrierTick);
        metrics.put("firstMechTick", t.firstMechTick);
        metrics.put("earlyVehicleTick", EARLY_VEHICLE_TICK);
        metrics.put("cooldownAtEarlyRequest", t.cooldownAtEarlyRequest);
        metrics.put("buffaloHomeTick", t.buffaloHomeTick);
        metrics.put("vehicleRequestTick", t.muleRequestTick);
        metrics.put("vehicleLandedTick", t.vehicleLandedTick);
        metrics.put("sorties", t.sortieIds.size());
        metrics.put("liftRemaining", t.liftRemaining);
        metrics.put("aboardAtEnd", t.aboardAtEnd);
        return new SceneReport(id(), "pick-and-pace", TICKS, verdicts, metrics);
    }

    private static String fmt(float seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    private static final class Trace implements TickObserver {
        final MechSupport power;
        LiftBudget.Refusal bulwarkRefusal;
        boolean bulwarkCanTarget;
        float cooldownAfterBulwark = -1f;
        int airAfterBulwark = -1;
        boolean bulwarkCalledAfterRequest;

        final Set<Long> sortieIds = new LinkedHashSet<>();
        long houndSortie;
        String houndCarrier = "none";
        int houndCarrierTick = -1;
        List<String> houndCarrierLoads = List.of();
        final boolean[] calledAfterHound = new boolean[3];

        float cooldownAtEarlyRequest = -1f;
        boolean muleCalledAfterEarly;
        int sortiesAfterEarly = -1;

        int buffaloHomeTick = -1;
        int muleRequestTick = -1;
        float cooldownAtMuleRequest = -1f;
        int airAtMuleRequest = -1;
        List<String> muleCarrierLoads = List.of();
        int vehicleLandedTick = -1;
        VehicleType vehicleType;

        int firstMechTick = -1;
        MechVariant firstMechVariant;
        int bulwarksSeen;

        boolean bulwarkCalledAtEnd;
        LiftBudget.Refusal bulwarkRefusalAtEnd;
        int liftRemaining;
        int aboardAtEnd;

        Trace(MechSupport power) {
            this.power = power;
        }

        String caption() {
            if (vehicleLandedTick >= 0) return "Mule (APC) down; Anvil (Bulwark) still aboard, no heavy lift";
            if (muleRequestTick >= 0) return "Mule (APC) inbound on the Buffalo";
            if (cooldownAtEarlyRequest > 0f) return "Mule refused: call cooldown " + fmt(cooldownAtEarlyRequest) + "s";
            if (firstMechTick >= 0) return "Ironside (Hound) down";
            if (houndCarrierTick >= 0) return "Ironside (Hound) inbound on the Buffalo";
            if (bulwarkRefusal != null) return "Anvil (Bulwark) refused: " + bulwarkRefusal;
            return "bay manifest: Hound, Bulwark, APC";
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            CommandPowerService service = sim.getCommandPowerService();
            float cooldown = service.getCooldownRemaining(power.id);

            if (tick == BULWARK_TICK) {
                List<Integer> pick = List.of(BULWARK);
                bulwarkRefusal = power.picker(sim, pick).get(BULWARK).refusal();
                bulwarkCanTarget = power.canTarget(ZONE_X, ZONE_Y, sim, pick);
                service.requestActivation(power.id, ZONE_X, ZONE_Y, pick);
            } else if (tick == BULWARK_TICK + 2) {
                cooldownAfterBulwark = cooldown;
                airAfterBulwark = sim.getAirEntityIds().length;
                bulwarkCalledAfterRequest = power.called(BULWARK);
            }

            if (tick == HOUND_TICK) {
                service.requestActivation(power.id, ZONE_X, ZONE_Y, List.of(HOUND));
            } else if (tick == HOUND_TICK + 2) {
                for (int i = 0; i < 3; i++) calledAfterHound[i] = power.called(i);
            }

            if (tick == EARLY_VEHICLE_TICK) {
                cooldownAtEarlyRequest = cooldown;
                service.requestActivation(power.id, ZONE_X, ZONE_Y, List.of(MULE));
            } else if (tick == EARLY_VEHICLE_TICK + 2) {
                muleCalledAfterEarly = power.called(MULE);
                sortiesAfterEarly = sortieIds.size();
            }

            if (tick > EARLY_VEHICLE_TICK + 2 && muleRequestTick < 0 && cooldown <= 0f
                    && power.picker(sim, List.of(MULE)).get(MULE).callable()) {
                muleRequestTick = tick;
                cooldownAtMuleRequest = cooldown;
                airAtMuleRequest = sim.getAirEntityIds().length;
                service.requestActivation(power.id, ZONE_X, ZONE_Y, List.of(MULE));
            }

            long[] air = sim.getAirEntityIds();
            boolean houndSortieAlive = false;
            for (long id : air) {
                if (id == houndSortie) houndSortieAlive = true;
                if (!sortieIds.add(id)) continue;
                ShuttleMission mission = sim.world().mission(id);
                List<String> loads = loadsOf(mission);
                if (houndSortie == 0L) {
                    houndSortie = id;
                    houndSortieAlive = true;
                    houndCarrier = String.valueOf(sim.world().airframe(id));
                    houndCarrierTick = tick;
                    houndCarrierLoads = loads;
                } else if (muleRequestTick >= 0 && muleCarrierLoads.isEmpty()) {
                    muleCarrierLoads = loads;
                }
            }
            if (houndSortie != 0L && !houndSortieAlive && buffaloHomeTick < 0) buffaloHomeTick = tick;

            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE) continue;
                MechVariant variant = sim.identity().mechVariant(unit);
                if (variant == null) continue;
                if (variant == MechVariant.BULWARK) bulwarksSeen++;
                if (firstMechTick < 0) {
                    firstMechTick = tick;
                    firstMechVariant = variant;
                }
            }
            if (vehicleLandedTick < 0) {
                for (long vehicle : sim.getConvoyVehicleIds()) {
                    if (sim.convoy().faction(vehicle) != Faction.MARINE) continue;
                    vehicleLandedTick = tick;
                    vehicleType = sim.convoy().vehicleType(vehicle);
                    break;
                }
            }
        }

        private static List<String> loadsOf(ShuttleMission mission) {
            if (mission == null || mission.armorDrop == null) return List.of();
            List<String> names = new ArrayList<>();
            for (ArmorLoad load : mission.armorDrop) {
                names.add(load.isVehicle() ? load.vehicle().callsign() : load.mech().displayName());
            }
            return names;
        }

        void finish(BattleSimulation sim) {
            bulwarkCalledAtEnd = power.called(BULWARK);
            bulwarkRefusalAtEnd = power.picker(sim, List.of(BULWARK)).get(BULWARK).refusal();
            liftRemaining = power.budget().remaining();
            aboardAtEnd = power.availableCalls();
        }
    }
}
