package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * L2, The guns run dry ({@code cordon-nouns.md}, Optional objectives): after the first tide one redoubt's
 * gun towers run low and fire at a quarter of their rate; the base loads an ammo truck, which sets out
 * from the base's gate up that redoubt's supply road. When it reaches the redoubt the guns fire at their
 * own rate again. Fails when the truck is destroyed or the redoubt is lost first.
 *
 * <p>Simplified: the truck appears at the base end of the supply road (the dump stands inside the base,
 * where a truck has no room to turn out), and "low" is a rationed rate of fire, not a counted magazine.
 */
final class GunsDryBeat extends CordonBeat {

    static final int DRY_SECONDS = 300;
    /** A dry gun waits this many times its own cooldown between shots. */
    static final float RATION = 4f;
    /** Cells from the redoubt's wall the truck has arrived. */
    static final int ARRIVE = 8;
    private static final long SALT = 0x4755_4E53_4452_59L;

    private CordonRedoubt redoubt;
    private long truck;
    private boolean started, arrived;
    private final Map<Long, Float> lastTimer = new HashMap<>();

    GunsDryBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: arrived succeeds; a dead truck or a lost redoubt before that fails. */
    static Status rule(boolean arrived, boolean truckAlive, boolean redoubtManned) {
        if (arrived) return Status.SUCCEEDED;
        if (!truckAlive || !redoubtManned) return Status.FAILED;
        return Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (!started) {
            if (tick < CordonSequence.ticks(DRY_SECONDS)) return Status.ACTIVE;
            started = true;
            Laid road = choose();
            if (road == null) return Status.LAPSED;
            int[] base = road.gaps().get(0), gate = road.gaps().get(1);
            if (dist(base, redoubt) < dist(gate, redoubt)) {
                int[] swap = base;
                base = gate;
                gate = swap;
            }
            truck = beats.deploy(sim, VehicleType.SUPPLY_TRUCK, base[0], base[1]);
            if (truck == 0L) return Status.LAPSED;
            beats.command().station(sim, truck, gate[0], gate[1]);
        }
        boolean alive = CordonBeats.alive(sim, truck);
        if (alive) {
            GroundBody body = sim.convoy().body(truck);
            if (redoubt.contains((int) body.x, (int) body.y, ARRIVE)) {
                arrived = true;
                sim.convoy().despawn(truck);
            }
        }
        return rule(arrived, alive, redoubt.manned());
    }

    /** The guns stay rationed until the truck comes, whether or not it still can. */
    @Override
    void watch(BattleSimulation sim, int tick) {
        if (redoubt != null && !arrived && redoubt.manned()) ration(sim);
    }

    /** A manned redoubt with a supply road, drawn from the battle seed; the switch line's redoubt last. */
    private Laid choose() {
        List<CordonRedoubt> open = new ArrayList<>();
        CordonRedoubt switched = SwitchLineBeat.switchRedoubt(beats);
        for (CordonRedoubt r : beats.sequence().redoubts()) {
            Laid road = beats.laid().find(CordonPlan.SUPPLY_ROAD + r.number());
            if (r.manned() && road != null && road.isLaid() && road.gaps().size() >= 2 && r != switched) open.add(r);
        }
        if (open.isEmpty()) return null;
        redoubt = open.get(new Random(beats.battleSeed() ^ SALT).nextInt(open.size()));
        return beats.laid().find(CordonPlan.SUPPLY_ROAD + redoubt.number());
    }

    private static long dist(int[] c, CordonRedoubt r) {
        long dx = c[0] - r.centreX(), dy = c[1] - r.centreY();
        return dx * dx + dy * dy;
    }

    /** Every gun that fired since the last tick waits {@link #RATION} times its cooldown before the next. */
    private void ration(BattleSimulation sim) {
        for (long gun : redoubt.guns()) {
            if (sim.resolveUnit(gun) == 0L || !sim.world().isAlive(gun)) continue;
            float timer = sim.world().cooldownTimer(gun);
            Float last = lastTimer.get(gun);
            if (last != null && timer > last + 1e-4f) {
                timer = sim.combat().attackCooldown(gun) * RATION;
                sim.world().setCooldownTimer(gun, timer);
            }
            lastTimer.put(gun, timer);
        }
    }

    /** The redoubt the guns ran dry in, once chosen. */
    CordonRedoubt redoubt() { return redoubt; }

    @Override
    int[] call(BattleSimulation sim) {
        if (!started || arrived || !CordonBeats.alive(sim, truck)) return null;
        GroundBody body = sim.convoy().body(truck);
        return new int[]{(int) body.x, (int) body.y, 2};
    }
}
