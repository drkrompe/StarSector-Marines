package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.command.HeartLane;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import com.dillon.starsectormarines.battle.evacuation.HeartSequence;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the Rav Bloom battles' roving observer teams watch from ({@link RovingObservers}), ranked from what the allies
 * know: the Cordon's redoubts by their radio calls, Broad Front's lanes by their squads in contact. Read off the battle as
 * it stands, so a battle that has neither a cordon nor a heart offers nowhere and sends no team.
 */
public final class BloomVantages {

    private BloomVantages() {}

    /** Cells out past a redoubt's side wall a team watches the gap from. */
    static final int FLANK_CELLS = 6;
    /** Cells behind a redoubt's centre, toward home, a team stands. */
    static final int BACK_CELLS = 6;
    /** Cells behind a lane's foremost team a team stands. */
    static final int BEHIND_LEAD_CELLS = 10;

    /** The vantages of whichever Bloom battle {@code sim} is, home being the allied rear at {@code (homeX, homeY)}. */
    public static RovingObservers.Vantages of(float homeX, float homeY) {
        return sim -> {
            if (sim.getCordon() != null) return cordon(sim, sim.getCordon(), homeY);
            HeartSequence heart = sim.getHeart();
            if (heart != null && heart.lanes() != null) return lanes(sim, heart.lanes());
            return List.of();
        };
    }

    /**
     * Each manned redoubt's two gaps, watched from just past its side walls a little behind it: the redoubts calling for
     * relief first, then the one with least garrison standing. A call is radio, so the side knows it.
     */
    static List<int[]> cordon(BattleSimulation sim, CordonSequence cordon, float homeY) {
        List<CordonRedoubt> manned = new ArrayList<>();
        for (CordonRedoubt r : cordon.redoubts()) if (r.manned()) manned.add(r);
        manned.sort(Comparator.comparingInt((CordonRedoubt r) -> r.state() == CordonRedoubt.State.CALLING ? 0 : 1)
                .thenComparingInt(CordonRedoubt::garrison));
        List<int[]> out = new ArrayList<>();
        for (CordonRedoubt r : manned) {
            int y = r.centreY() + Integer.signum(Math.round(homeY) - r.centreY()) * BACK_CELLS;
            addOpen(sim, out, r.left() - FLANK_CELLS, y);
            addOpen(sim, out, r.right() + FLANK_CELLS, y);
        }
        return out;
    }

    /**
     * Behind each lane's foremost team, on the way back to its foot: the lanes with the most squads in contact first. A
     * lane's own squads report contact, so the side knows it.
     */
    static List<int[]> lanes(BattleSimulation sim, HeartLanes lanes) {
        record Lead(int[] at, int engaged) {}
        List<Lead> leads = new ArrayList<>();
        List<HeartLane> grounds = lanes.grounds();
        for (int i = 0; i < grounds.size(); i++) {
            HeartLane lane = grounds.get(i);
            int[] foot = lanes.entry(i + 1);
            if (foot == null) continue;
            float bestDepth = -1f, leadX = 0f, leadY = 0f;
            int engaged = 0;
            for (Squad squad : sim.getSquads()) {
                if (squad.faction != Faction.ALLY || !sim.world().isAlive(squad.leaderId)) continue;
                float x = sim.world().x(squad.leaderId), y = sim.world().y(squad.leaderId);
                float across = lane.advancesAlongY() ? x : y;
                if (Math.abs(across - lane.across()) > lane.halfWidth()) continue;
                if (squad.alertLevel == SquadAlertLevel.ENGAGED) engaged++;
                float depth = lane.advancesAlongY() ? Math.abs(y - foot[1]) : Math.abs(x - foot[0]);
                if (depth > bestDepth) {
                    bestDepth = depth;
                    leadX = x;
                    leadY = y;
                }
            }
            if (bestDepth < 0f) continue;
            float back = Math.min(BEHIND_LEAD_CELLS, bestDepth);
            int x = Math.round(lane.advancesAlongY() ? leadX : leadX + Math.signum(foot[0] - leadX) * back);
            int y = Math.round(lane.advancesAlongY() ? leadY + Math.signum(foot[1] - leadY) * back : leadY);
            List<int[]> cell = RovingObservers.openCells(sim.getGrid(), x, y, 1);
            if (!cell.isEmpty()) leads.add(new Lead(cell.get(0), engaged));
        }
        leads.sort(Comparator.comparingInt((Lead l) -> -l.engaged));
        List<int[]> out = new ArrayList<>();
        for (Lead l : leads) out.add(l.at);
        return out;
    }

    private static void addOpen(BattleSimulation sim, List<int[]> out, int x, int y) {
        List<int[]> cell = RovingObservers.openCells(sim.getGrid(), x, y, 1);
        if (!cell.isEmpty()) out.add(cell.get(0));
    }
}
