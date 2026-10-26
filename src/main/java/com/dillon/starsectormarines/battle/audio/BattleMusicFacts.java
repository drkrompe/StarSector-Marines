package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Reads a {@link BattleMusicDirector.Facts} frame from a battle after it advances. Reads only. */
public final class BattleMusicFacts {

    private BattleMusicFacts() { }

    public static BattleMusicDirector.Facts of(BattleSimulation sim) {
        return new BattleMusicDirector.Facts(declared(sim), contact(sim.getShotsThisFrame()), sim.isComplete());
    }

    /** The mission's declared music state, or {@code null} when it leaves the choice to the heat rule. */
    static MusicState declared(BattleSimulation sim) {
        DustLedgerSequence dustLedger = sim.getDustLedger();
        return dustLedger != null ? dustLedger.progress().musicState() : null;
    }

    /** True when the company's side fired, or someone hostile to it did: a civilian's shot is not contact. */
    static boolean contact(List<ShotEvent> shots) {
        for (ShotEvent shot : shots) {
            Faction shooter = shot.shooterFaction;
            if (shooter == null) continue;
            if (shooter.friendlyTo(Faction.MARINE) || shooter.hostileTo(Faction.MARINE)) return true;
        }
        return false;
    }
}
