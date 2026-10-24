package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.GunStock;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * L2, The guns run dry ({@code cordon-nouns.md}, Optional objectives): after the first tide one redoubt's
 * gun towers run low and fire at a quarter of their rate; the base loads an ammo truck, which sets out
 * from the base's gate up that redoubt's supply road. When it reaches the redoubt the guns fire at their
 * own rate again. Fails when the truck is destroyed or the redoubt is lost first. The truck is a
 * {@link SupplyDelivery} whose recipient is the redoubt.
 *
 * <p>Simplified: the truck appears at the base end of the supply road (the dump stands inside the base,
 * where a truck has no room to turn out), and "low" is a rationed rate of fire, not a counted magazine.
 */
final class GunsDryBeat extends CordonBeat {

    static final int DRY_SECONDS = 300;
    /** Rounds a redoubt gun holds once the truck has come: more than the rest of a battle spends. */
    static final int FULL = 1000;
    /** Cells from the redoubt's wall the truck has arrived. */
    static final int ARRIVE = 8;
    private static final long SALT = 0x4755_4E53_4452_59L;

    private CordonRedoubt redoubt;
    private SupplyDelivery delivery;
    private boolean started, arrived;
    /** The redoubt's guns' rounds on hand, drained when they run dry and refilled by the truck. */
    private GunStock stock;

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
            stock = new GunStock("redoubt " + redoubt.number(), FULL);
            for (long gun : redoubt.guns()) stock.add(gun);
            stock.drain();
            int[] base = road.gaps().get(0), gate = road.gaps().get(1);
            if (dist(base, redoubt) < dist(gate, redoubt)) {
                int[] swap = base;
                base = gate;
                gate = swap;
            }
            int[] from = base, to = gate;
            CordonRedoubt r = redoubt;
            delivery = SupplyDelivery.ammunition(new SupplyDelivery.Recipient() {
                @Override public String name() { return "redoubt " + r.number(); }
                @Override public int[] where(BattleSimulation s) { return r.manned() ? to : null; }
                @Override public boolean reached(BattleSimulation s, float x, float y) {
                    return r.contains((int) x, (int) y, ARRIVE);
                }
                @Override public void arrived(BattleSimulation s) {
                    GunsDryBeat.this.arrived = true;
                    stock.refill(s);
                }
            }, s -> from);
        }
        SupplyDelivery.State state = delivery.advance(sim, beats.command());
        if (state == SupplyDelivery.State.WAITING) return Status.LAPSED;
        return rule(arrived, state != SupplyDelivery.State.LOST, redoubt.manned());
    }

    /** The guns stay rationed until the truck comes, whether or not it still can. */
    @Override
    void watch(BattleSimulation sim, int tick) {
        if (stock != null && !arrived && redoubt.manned()) stock.advance(sim);
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

    /** The redoubt the guns ran dry in, once chosen. */
    CordonRedoubt redoubt() { return redoubt; }

    @Override
    String describe(BattleSimulation sim) {
        if (redoubt == null) return "";
        int[] at = delivery == null ? null : delivery.truckCell(sim);
        String truckAt = at != null ? at[0] + "," + at[1] : arrived ? "arrived" : "gone";
        return "redoubt " + redoubt.number() + ", truck " + truckAt;
    }

    @Override
    int[] call(BattleSimulation sim) {
        int[] at = delivery == null ? null : delivery.truckCell(sim);
        return at == null ? null : new int[]{at[0], at[1], 2};
    }
}
