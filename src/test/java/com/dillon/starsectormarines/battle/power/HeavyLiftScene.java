package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
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
import com.dillon.starsectormarines.marine.BoatFitting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Who lifts a drop: the company's own cradled Buffalo, a ship's synthetic Valkyrie, or nobody.
 *
 * <p>The question is about the carrier and its lift rating, so the world is as small as it can be: open ground, a
 * marine section at the drop zone, and the far lone defender each scene keeps so neither side is ever absent. Mech
 * Support's one machine is ordered by the player's own surface ({@code requestActivation} with its pick) on a fixed
 * tick; the order joins the lift queue and the first ferry craft that admits it flies it.
 *
 * <ul>
 *   <li>{@code cradled-hound}: one Hound and the cradled Buffalo, no ship lift. The company boat itself should fly,
 *       the Hound should land, and one lift should be spent.</li>
 *   <li>{@code bulwark-refused}: the same boat and one Bulwark, no ship lift. A cradle is light lift, so the power
 *       should refuse its target: nothing flies, no mech appears, the lift is kept.</li>
 *   <li>{@code bulwark-ship-lift}: the same as the refusal with ship lift behind it. A Valkyrie should fly the
 *       Bulwark down, proving the refusal is about the boat's rating and not about the ground.</li>
 * </ul>
 */
public final class HeavyLiftScene implements BehaviorScene {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 68;
    private static final int TICKS = 900;
    private static final int ZONE_X = 40;
    private static final int ZONE_Y = 34;
    private static final int CALL_TICK = 60;
    private static final String RIFLE = "weapon.smg";
    private static final List<Integer> PICK = List.of(0);
    private static final FittedBoat CRADLED_BUFFALO = new FittedBoat(ShuttleType.BUFFALO,
            BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");

    @Override public String id() { return "heavy-lift"; }

    @Override public String label() {
        return "Heavy lift: a cradled company Buffalo delivers a Hound, refuses a Bulwark without heavy lift, and a "
                + "ship's Valkyrie lifts the same Bulwark";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("cradled-hound", "bulwark-refused", "bulwark-ship-lift")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private Trace playOne(String loop, FrameSink frames) {
        MechVariant chassis = loop.equals("cradled-hound") ? MechVariant.HOUND : MechVariant.BULWARK;
        boolean shipLift = loop.equals("bulwark-ship-lift");

        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, WIDTH - 4, HEIGHT - 4,
                spec -> spec.moveSpeed(0f));
        builder.squad("company").faction(Faction.MARINE).size(4).at(ZONE_X - 8, ZONE_Y).primary(RIFLE).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);

        MechSupport power = MechSupport.configured(
                List.of(MechDeploymentSpec.standard(chassis)), CRADLED_BUFFALO, shipLift);
        sim.setCommandPowers(List.of(power));

        Trace trace = new Trace(power, chassis);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 15, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                        loop, tick, trace.caption()))
                .run(TICKS);
        trace.liftAfter = power.budget().remaining();
        return trace;
    }

    private SceneReport report(String loop, Trace t) {
        List<Verdict> verdicts = new ArrayList<>();
        String carrier = t.carrier == null ? "none" : t.carrier.toString();
        switch (loop) {
            case "cradled-hound" -> {
                verdicts.add(Verdict.of("call-accepted", t.canTargetAtCall,
                        "canTarget at the call: " + t.canTargetAtCall));
                verdicts.add(Verdict.of("company-boat-flies", CRADLED_BUFFALO.equals(t.carrier),
                        "carrier in the air: " + carrier));
                verdicts.add(Verdict.of("hound-lands", t.landedVariant == MechVariant.HOUND,
                        "first marine mech " + t.landedVariant + " at tick " + t.firstMechTick));
                verdicts.add(Verdict.of("lift-spent", t.liftAfter == t.liftBefore - 1,
                        "lift " + t.liftBefore + " -> " + t.liftAfter));
            }
            case "bulwark-refused" -> {
                verdicts.add(Verdict.of("call-refused", !t.canTargetAtCall,
                        "canTarget at the call: " + t.canTargetAtCall));
                verdicts.add(Verdict.of("nothing-flies", t.carrier == null, "carrier seen: " + carrier));
                verdicts.add(Verdict.of("no-mech-appears", t.mechsAtMost == 0, t.mechsAtMost + " marine mechs"));
                verdicts.add(Verdict.of("lift-kept", t.liftAfter == t.liftBefore,
                        "lift " + t.liftBefore + " -> " + t.liftAfter));
            }
            default -> {
                verdicts.add(Verdict.of("call-accepted", t.canTargetAtCall,
                        "canTarget at the call: " + t.canTargetAtCall));
                verdicts.add(Verdict.of("ship-valkyrie-flies", t.carrier == ShuttleType.VALKYRIE,
                        "carrier in the air: " + carrier));
                verdicts.add(Verdict.of("bulwark-lands", t.landedVariant == MechVariant.BULWARK,
                        "first marine mech " + t.landedVariant + " at tick " + t.firstMechTick));
                verdicts.add(Verdict.of("lift-spent", t.liftAfter == t.liftBefore - 1,
                        "lift " + t.liftBefore + " -> " + t.liftAfter));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("callTick", CALL_TICK);
        metrics.put("carrierTick", t.carrierTick);
        metrics.put("firstMechTick", t.firstMechTick);
        metrics.put("mechs", t.mechsAtMost);
        metrics.put("liftBefore", t.liftBefore);
        metrics.put("liftAfter", t.liftAfter);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        final MechSupport power;
        final MechVariant chassis;
        boolean canTargetAtCall;
        int liftBefore = -1;
        int liftAfter = -1;
        Airframe carrier;
        int carrierTick = -1;
        int firstMechTick = -1;
        MechVariant landedVariant;
        int mechsAtMost;

        Trace(MechSupport power, MechVariant chassis) {
            this.power = power;
            this.chassis = chassis;
        }

        String caption() {
            String what = chassis.name().toLowerCase(Locale.ROOT);
            if (firstMechTick >= 0) return what + " down from " + carrier;
            if (carrier != null) return what + " inbound on " + carrier;
            if (liftBefore >= 0 && !canTargetAtCall) return what + " refused: no lift for it";
            return what + " on call";
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (tick == CALL_TICK) {
                CommandPowerService service = sim.getCommandPowerService();
                liftBefore = power.budget().remaining();
                canTargetAtCall = power.canTarget(ZONE_X, ZONE_Y, sim, PICK);
                service.requestActivation(power.id, ZONE_X, ZONE_Y, PICK);
            }
            if (carrier == null) {
                // Standby ferry craft wait off-map; the carrier is the one that took the machine.
                for (long craft : sim.getAirEntityIds()) {
                    ShuttleMission mission = sim.world().mission(craft);
                    if (mission == null || mission.machineDropId == 0L) continue;
                    carrier = sim.world().airframe(craft);
                    carrierTick = tick;
                    break;
                }
            }
            int mechs = 0;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE) continue;
                MechVariant variant = sim.identity().mechVariant(unit);
                if (variant == null) continue;
                mechs++;
                if (firstMechTick < 0) {
                    firstMechTick = tick;
                    landedVariant = variant;
                }
            }
            mechsAtMost = Math.max(mechsAtMost, mechs);
        }
    }
}
