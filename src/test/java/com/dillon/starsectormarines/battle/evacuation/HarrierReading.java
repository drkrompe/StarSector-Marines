package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Locale;

/**
 * What a battle's Harriers did, as one evidence line (`colony-breach-nouns.md`, Brood spire): the spires grown and
 * still standing, the packs launched off spires and as broods, the Harriers released and shot down, the strikes and
 * lifts they landed, and the anti-air standing at the end. A reading, never an assertion.
 */
public final class HarrierReading {

    private HarrierReading() {}

    public static String line(BattleSimulation sim) {
        BroodSpires spires = sim.broodSpires();
        int standing = 0;
        for (BroodSpires.Spire s : spires.spires()) {
            if (sim.resolveUnit(s.entityId) != 0L && sim.world().isAlive(s.entityId)) standing++;
        }
        int fromSpires = 0, broods = 0;
        for (BroodSpires.Launch l : spires.launches()) {
            if (l.spire() != 0L) fromSpires++;
            else broods++;
        }
        SwarmFliers fliers = sim.swarmFliers();
        int alive = 0;
        for (HarrierPack p : fliers.packs()) alive += p.members.size();
        int strikes = 0, lifts = 0;
        for (SwarmFliers.Event e : fliers.events()) {
            if (e.kind() == SwarmFliers.Event.Kind.STRIKE) strikes++;
            else if (e.kind() == SwarmFliers.Event.Kind.LIFT) lifts++;
        }
        int antiAir = 0;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            Faction f = sim.identity().faction(u);
            if ((f == Faction.MARINE || f == Faction.ALLY) && sim.turretState().isTurret(u)
                    && sim.turretState().structure(u).targetRole == TurretRole.AA) antiAir++;
        }
        return String.format(Locale.ROOT, "spires %d grown, %d standing; packs %d off spires, %d broods; harriers %d released,"
                        + " %d shot down, %d aloft; %d strikes, %d lifts; anti-air standing %d",
                spires.spires().size(), standing, fromSpires, broods, fliers.released(),
                fliers.released() - alive, alive, strikes, lifts, antiAir);
    }
}
