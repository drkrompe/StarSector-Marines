package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.GunStock;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * L2, The guns run dry ({@code cordon-nouns.md}, Optional objectives): after the first tide one redoubt's
 * gun towers run out and fire rationed, a quarter of their rate. The beat only empties the towers' rounds in the
 * redoubt's ammunition ({@link CordonLineCommand#stock}); the allied line command sees dry guns and asks its base for an
 * ammunition truck by the ordinary request ({@code cordon-allied-command.md}, The producer), which drives up
 * the redoubt's supply road with an escort out of the armour's surplus. When it reaches the redoubt the guns
 * fire at their own rate again. Fails when a truck bound there is lost or the redoubt is lost first.
 *
 * <p>Simplified: the truck appears at the base's end of the supply road, or of its way through a city's streets (the
 * dump stands inside the base, where a truck has no room to turn out), and "dry" is a rationed rate of fire, not an
 * empty magazine.
 */
final class GunsDryBeat extends CordonBeat {

    static final int DRY_SECONDS = 300;
    private static final long SALT = 0x4755_4E53_4452_59L;

    private CordonRedoubt redoubt;
    private GunStock stock;
    private boolean started;
    /** Refills and trucks lost the stock had when it was drained: a later one is this beat's. */
    private int refillsAtDrain, lostAtDrain;

    GunsDryBeat(CordonBeats beats) {
        super(beats);
    }

    /** The rule: a truck's arrival succeeds; a truck lost or the redoubt lost before that fails. */
    static Status rule(boolean arrived, boolean truckLost, boolean redoubtManned) {
        if (arrived) return Status.SUCCEEDED;
        if (truckLost || !redoubtManned) return Status.FAILED;
        return Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        CordonLineCommand line = beats.sequence().line();
        if (!started) {
            if (tick < CordonSequence.ticks(DRY_SECONDS)) return Status.ACTIVE;
            started = true;
            if (line == null || line.shed() == null || choose(line) == null) return Status.LAPSED;
            stock = line.stock(redoubt);
            stock.drain(redoubt.guns());
            refillsAtDrain = stock.refills();
            lostAtDrain = line.lostTo(redoubt);
        }
        return rule(stock.refills() > refillsAtDrain, line.lostTo(redoubt) > lostAtDrain, redoubt.manned());
    }

    /**
     * A manned redoubt a truck can be sent to, drawn from the battle seed; never the switch line's. A truck drives
     * its supply road, or in a city, which lays none, its way through the streets
     * ({@link CordonLineCommand#supplied}); with neither anywhere the beat lapses.
     */
    private CordonRedoubt choose(CordonLineCommand line) {
        List<CordonRedoubt> open = new ArrayList<>();
        CordonRedoubt switched = SwitchLineBeat.switchRedoubt(beats);
        for (CordonRedoubt r : beats.sequence().redoubts()) {
            if (r.manned() && line.supplied(r) && r != switched) open.add(r);
        }
        if (open.isEmpty()) return null;
        redoubt = open.get(new Random(beats.battleSeed() ^ SALT).nextInt(open.size()));
        return redoubt;
    }

    /** The redoubt the guns ran dry in, once chosen. */
    CordonRedoubt redoubt() { return redoubt; }

    /** The truck bound for the dry redoubt now, or {@code 0}. */
    long truck() {
        CordonLineCommand line = beats.sequence().line();
        return redoubt == null || line == null ? 0L : line.truckTo(redoubt);
    }

    @Override
    String describe(BattleSimulation sim) {
        if (redoubt == null) return "";
        long truck = truck();
        String truckAt;
        if (truck != 0L) {
            GroundBody body = sim.convoy().body(truck);
            truckAt = (int) body.x + "," + (int) body.y + (beats.escorted(truck) ? " escorted" : " unescorted");
        } else {
            truckAt = stock != null && stock.refills() > refillsAtDrain ? "arrived" : "none on the road";
        }
        return "redoubt " + redoubt.number() + ", truck " + truckAt;
    }

    /** The company walks with the truck only where the armour had no hull to spare for it. */
    @Override
    int[] call(BattleSimulation sim) {
        long truck = truck();
        if (truck == 0L || beats.covered(truck)) return null;
        GroundBody body = sim.convoy().body(truck);
        return new int[]{(int) body.x, (int) body.y, 2};
    }
}
