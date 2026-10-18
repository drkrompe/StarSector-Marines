package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.FittedBoat;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How fast armor reaches the ground: is the pace the call cooldown, or the carrier's round trip?
 *
 * <p>A player who calls the moment the picker allows keeps a four-gantry bay coming down as quickly as the rules
 * permit. Each loop is one lift tier and one landing-zone depth (cells from the nearest map edge, which is where the
 * carrier enters and leaves; the map is square so no other edge is nearer): the starter company boat with a light cradle, a heavy cradle, and a ship's Valkyrie. The
 * script calls the first callable machine each time the cooldown has lapsed and the picker allows it
 * (one machine per call for the cradled boats and the {@code split} Valkyrie loop, which is where the cooldown alone
 * paces the calls; the default selection, all four machines at once, for the other Valkyrie loop), and records when each call
 * was made, when its machine stood on the ground, and when its carrier left the air.
 *
 * <p>Readings, not outcomes: {@code gapSeconds} is the time between the first two calls, {@code roundTripSeconds}
 * the first carrier's launch-to-gone time, and {@code gate} is 1 when the carrier (not the cooldown) set the second
 * call. Armor figures are structure plus armor capacity of what stood on the ground by the stated time. The one
 * mechanic asserted is that no call was made inside the cooldown. Rerun with
 * {@code -Dbattle.power.armorDropCooldownSeconds=N} for the control.
 */
public final class ArmorDropPaceScene implements BehaviorScene {

    private static final int TICKS = 9000;
    private static final int FIRST_CALL_TICK = 30;
    private static final float TICK_SECONDS = 1f / 30f;
    private static final String RIFLE = "weapon.smg";

    private enum Tier { STARTER_LIGHT_CRADLE, MID_HEAVY_CRADLE, LATE_VALKYRIE, LATE_VALKYRIE_SPLIT }

    @Override public String id() { return "armor-drop-pace"; }

    @Override public String label() {
        return "Armor drop pace: calls made the moment they are allowed, per lift tier and landing depth; is the "
                + "cooldown or the carrier the real gate";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (Tier tier : Tier.values()) {
            for (int depth : new int[]{30, 80, 150}) {
                String loop = tier.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-d" + depth;
                reports.add(report(loop, playOne(tier, depth)));
            }
        }
        return reports;
    }

    private static MechDeploymentSpec named(MechVariant variant, String callsign) {
        return new MechDeploymentSpec(variant, variant.defaultRole, MissileReplenisherComponent.STANDARD,
                variant.arms, variant.leftShoulder, variant.rightShoulder, callsign);
    }

    private static FittedBoat boat(BoatFitting lift) {
        return new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, lift,
                "buffalo-1");
    }

    private Trace playOne(Tier tier, int depth) {
        int width = depth * 2 + 40;
        int height = Math.max(68, width);
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, width - 4, height - 4,
                spec -> spec.moveSpeed(0f));
        builder.squad("company").faction(Faction.MARINE).size(4).at(depth - 8, height / 2).primary(RIFLE).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261018L);

        MechSupport power;
        switch (tier) {
            case STARTER_LIGHT_CRADLE -> power = MechSupport.configured(
                    List.of(named(MechVariant.HOUND, "Ironside"), named(MechVariant.HOUND, "Pike")),
                    List.of(new VehicleDeploymentSpec("v1", "Mule", VehicleType.HEAVY_APC),
                            new VehicleDeploymentSpec("v2", "Hauler", VehicleType.HEAVY_APC)),
                    boat(BoatFitting.LIFT_CRADLE), false);
            case MID_HEAVY_CRADLE -> power = MechSupport.configured(
                    List.of(named(MechVariant.SIROCCO, "Ridge"), named(MechVariant.HOUND, "Pike")),
                    List.of(new VehicleDeploymentSpec("v1", "Warden", VehicleType.ESCORT_APC),
                            new VehicleDeploymentSpec("v2", "Plate", VehicleType.UP_ARMORED_APC)),
                    boat(BoatFitting.HEAVY_CRADLE), false);
            default -> power = MechSupport.configured(
                    List.of(named(MechVariant.BULWARK, "Anvil"), named(MechVariant.BULWARK, "Forge")),
                    List.of(new VehicleDeploymentSpec("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK),
                            new VehicleDeploymentSpec("v2", "Gun", VehicleType.HEAVY_GUN_TANK)),
                    null, true);
        }
        sim.setCommandPowers(List.of(power));

        boolean oneAtATime = tier != Tier.LATE_VALKYRIE;
        Trace trace = new Trace(power, depth, depth, height / 2, oneAtATime);
        SceneRun.of(world).observe(trace).run(TICKS);
        return trace;
    }

    private SceneReport report(String loop, Trace t) {
        float cooldown = MechSupport.CALL_COOLDOWN_SECONDS;
        float minGap = Float.MAX_VALUE;
        for (int i = 1; i < t.callTicks.size(); i++) {
            minGap = Math.min(minGap, (t.callTicks.get(i) - t.callTicks.get(i - 1)) * TICK_SECONDS);
        }
        boolean none = t.callTicks.size() < 2;
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("no-call-inside-cooldown", none || minGap >= cooldown - 0.1f,
                "calls at ticks " + t.callTicks + ", shortest gap " + (none ? "n/a" : fmt(minGap))
                        + "s against a cooldown of " + fmt(cooldown) + "s"));

        Map<String, Number> m = new LinkedHashMap<>();
        m.put("depthCells", t.depth);
        m.put("cooldownSeconds", cooldown);
        m.put("calls", t.callTicks.size());
        float gap = none ? -1f : (t.callTicks.get(1) - t.callTicks.get(0)) * TICK_SECONDS;
        m.put("gapSeconds", gap);
        float roundTrip = t.firstHomeTick < 0 ? -1f : (t.firstHomeTick - t.firstLaunchTick) * TICK_SECONDS;
        m.put("roundTripSeconds", roundTrip);
        m.put("firstLandingSeconds", t.firstLandTick < 0 ? -1f : (t.firstLandTick - t.callTicks.get(0)) * TICK_SECONDS);
        m.put("gate", none ? -1 : (gap > cooldown + 1.5f ? 1 : 0));
        m.put("machinesLanded", t.landed.size());
        m.put("lastLandingSeconds", t.lastLandTick < 0 ? -1f : (t.lastLandTick - t.callTicks.get(0)) * TICK_SECONDS);
        for (int seconds : new int[]{60, 120, 240, 300}) {
            int tick = t.callTicks.get(0) + Math.round(seconds / TICK_SECONDS);
            m.put("machinesBy" + seconds + "s", t.landedBy(tick));
            m.put("armorBy" + seconds + "s", t.armorBy(tick));
        }
        m.put("liftRemaining", t.liftRemaining);
        m.put("aboardAtEnd", t.aboardAtEnd);
        return new SceneReport(id(), loop, TICKS, verdicts, m);
    }

    private static String fmt(float seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    private static final class Trace implements TickObserver {
        final MechSupport power;
        final int depth;
        final int zoneX;
        final int zoneY;
        final boolean oneAtATime;
        final List<Integer> callTicks = new ArrayList<>();
        final Set<Long> seenAir = new HashSet<>();
        final Set<Long> seenUnits = new HashSet<>();
        final List<Integer> landTicks = new ArrayList<>();
        final List<Float> landArmor = new ArrayList<>();
        final List<Object> landed = new ArrayList<>();
        int firstLaunchTick = -1;
        long firstSortie;
        int firstHomeTick = -1;
        int firstLandTick = -1;
        int lastLandTick = -1;
        int liftRemaining;
        int aboardAtEnd;

        Trace(MechSupport power, int depth, int zoneX, int zoneY, boolean oneAtATime) {
            this.power = power;
            this.depth = depth;
            this.zoneX = zoneX;
            this.zoneY = zoneY;
            this.oneAtATime = oneAtATime;
        }

        int landedBy(int tick) {
            int n = 0;
            for (int landTick : landTicks) if (landTick <= tick) n++;
            return n;
        }

        float armorBy(int tick) {
            float sum = 0f;
            for (int i = 0; i < landTicks.size(); i++) if (landTicks.get(i) <= tick) sum += landArmor.get(i);
            return sum;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            CommandPowerService service = sim.getCommandPowerService();
            boolean settled = callTicks.isEmpty() || tick - callTicks.get(callTicks.size() - 1) > 3;
            if (tick >= FIRST_CALL_TICK && settled && service.getCooldownRemaining(power.id) <= 0f) {
                List<Integer> pick = choose(sim);
                if (!pick.isEmpty()) {
                    service.requestActivation(power.id, zoneX, zoneY, pick);
                    callTicks.add(tick);
                }
            }
            long[] air = sim.getAirEntityIds();
            boolean firstAlive = false;
            for (long id : air) {
                if (id == firstSortie && firstSortie != 0L) firstAlive = true;
                if (seenAir.add(id) && firstSortie == 0L) {
                    firstSortie = id;
                    firstLaunchTick = tick;
                    firstAlive = true;
                }
            }
            if (firstSortie != 0L && !firstAlive && firstHomeTick < 0) firstHomeTick = tick;

            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE) continue;
                MechVariant variant = sim.identity().mechVariant(unit);
                if (variant == null || !seenUnits.add(unit)) continue;
                land(tick, variant, variant.maxStructure + variant.armorCapacity);
            }
            for (long vehicle : sim.getConvoyVehicleIds()) {
                if (sim.convoy().faction(vehicle) != Faction.MARINE || !seenUnits.add(vehicle)) continue;
                VehicleType type = sim.convoy().vehicleType(vehicle);
                if (type == null) continue;
                land(tick, type, type.maxStructure + type.maxArmor);
            }
            liftRemaining = power.budget().remaining();
            aboardAtEnd = power.availableCalls();
        }

        private void land(int tick, Object what, float armor) {
            landed.add(what);
            landTicks.add(tick);
            landArmor.add(armor);
            if (firstLandTick < 0) firstLandTick = tick;
            lastLandTick = tick;
        }

        private List<Integer> choose(BattleSimulation sim) {
            if (!oneAtATime) {
                List<Integer> all = power.defaultSelection(sim);
                return all;
            }
            for (int i = 0; i < power.manifest().size(); i++) {
                if (power.picker(sim, List.of(i)).get(i).callable()) return List.of(i);
            }
            return List.of();
        }
    }
}
