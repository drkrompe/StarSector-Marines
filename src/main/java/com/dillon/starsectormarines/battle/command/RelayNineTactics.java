package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import java.util.ArrayList;
import java.util.List;

/** Public territory history. A recapture never creates another force or hold slot. */
public final class RelayNineTactics {
    private final List<Integer> gains = new ArrayList<>();
    private int reserveTarget = -1;
    public void observe(List<CompoundState> states) {
        if (states.size() != 4) throw new IllegalArgumentException("Relay Nine requires four sites");
        for (int i = 1; i < 4; i++) if (states.get(i) == CompoundState.MARINE_HELD && !gains.contains(i)) gains.add(i);
        if (reserveTarget < 0) for (int site : gains) if (site == 1 || site == 2) { reserveTarget = site; break; }
    }
    public int firstRoadLoss() { return reserveTarget; }
    public int assaultTarget(List<CompoundState> states) {
        if (states.get(0) != CompoundState.MARINE_HELD) return 0;
        for (int site : gains) if (states.get(site) == CompoundState.DEFENDER_HELD) return site;
        for (int site = 1; site < 4; site++) if (!gains.contains(site) && states.get(site) != CompoundState.MARINE_HELD) return site;
        for (int site = 1; site < 4; site++) if (states.get(site) != CompoundState.MARINE_HELD) return site;
        return 3;
    }
    public int militiaTarget(int detachment) {
        if (detachment < 0) throw new IllegalArgumentException("Negative detachment");
        return gains.isEmpty() ? 0 : gains.get(Math.min(detachment, gains.size() - 1));
    }
    public int counterTarget(List<CompoundState> states) {
        if (reserveTarget < 0) return 3;
        if (states.get(reserveTarget) != CompoundState.DEFENDER_HELD) return reserveTarget;
        for (int site : gains) if (states.get(site) != CompoundState.DEFENDER_HELD) return site;
        return reserveTarget;
    }
}
