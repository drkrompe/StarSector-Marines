package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.evacuation.HeartSequence;
import com.dillon.starsectormarines.battle.power.WalkingBarrage;
import com.dillon.starsectormarines.battle.power.WalkingBarrage.Line;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Burn's barrage in the shared mission picture (`heart-nouns.md`, Barrage plan): the battery's timetable read
 * off the plan frozen at the briefing, so the row counts down to the opening shoot, then to each line's lift, and
 * says when the next incendiary line lights the burn. Beaconed at the line firing (or about to) and at the battery,
 * which is the row's own loss: every gun dead before the heart is down.
 */
public final class HeartChecklist {
    private HeartChecklist() { }

    public static Item barrage(BattleSimulation sim, HeartSequence heart, BatteryObjective battery) {
        WalkingBarrage barrage = heart.barrage();
        long[] guns = barrage.guns();
        int standing = 0;
        float gx = 0f, gy = 0f;
        for (long gun : guns) {
            if (sim.resolveUnit(gun) == 0L) continue;
            standing++;
            gx += sim.world().x(gun);
            gy += sim.world().y(gun);
        }
        int[] batteryCell = standing == 0 ? null : new int[]{Math.round(gx / standing), Math.round(gy / standing)};
        List<Line> lines = barrage.plan().lines();
        List<int[]> lineCells = new ArrayList<>();
        for (Line line : lines) lineCells.add(heart.form().cellAt(line.depth(), 0.5f));
        State state = battery.isComplete() ? State.COMPLETE : battery.isFailed() ? State.FAILED : State.ACTIVE;
        return barrage(lines, sim.getSimTickIndex() * BattleSimulation.TICK_DT, standing, guns.length, state,
                lineCells, batteryCell);
    }

    /**
     * The barrage row at {@code seconds} into the battle: before the first line opens it counts down to the opening
     * shoot, while a line fires it counts down to that line's lift, and its tally counts down to the next incendiary
     * line. {@code lineCells} is each line's middle, {@code batteryCell} the living guns' middle or null.
     */
    static Item barrage(List<Line> lines, float seconds, int standing, int guns, State state,
                        List<int[]> lineCells, int[] batteryCell) {
        int firing = -1, next = -1;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (seconds < line.openSeconds()) { next = i; break; }
            if (seconds < line.openSeconds() + line.fireSeconds()) { firing = i; break; }
        }
        boolean ended = firing < 0 && next < 0;
        String phase;
        if (state == State.FAILED) phase = Strings.get("heartHudBarrageOverrun");
        else if (firing >= 0) {
            Line line = lines.get(firing);
            phase = Strings.format(line.heat() > 0 ? "heartHudBarrageFiringIncendiary" : "heartHudBarrageFiring",
                    firing + 1, clock(line.openSeconds() + line.fireSeconds() - seconds));
        } else if (next >= 0) {
            phase = Strings.format(lines.get(next).heat() > 0 ? "heartHudBarrageOpensIncendiary" : "heartHudBarrageOpens",
                    clock(lines.get(next).openSeconds() - seconds));
        } else phase = Strings.get("heartHudBarrageEnded");
        // The next incendiary line still to open, past the one firing: when the burn is next lit.
        int incendiary = -1;
        for (int i = Math.max(0, firing + 1); i < lines.size() && !ended; i++) {
            if (lines.get(i).heat() > 0 && seconds < lines.get(i).openSeconds()) { incendiary = i; break; }
        }
        String gunTally = Strings.format("heartHudBarrageGuns", standing, guns);
        String tally = incendiary < 0 || state != State.ACTIVE ? gunTally
                : Strings.format("heartHudBarrageNextIncendiary", gunTally, incendiary + 1,
                        clock(lines.get(incendiary).openSeconds() - seconds));
        List<Marker> markers = new ArrayList<>();
        String focus = "";
        int shown = firing >= 0 ? firing : next;
        if (state == State.ACTIVE && shown >= 0 && shown < lineCells.size()) {
            int[] at = lineCells.get(shown);
            focus = "heart-barrage-line";
            markers.add(new Marker(focus, Strings.format("heartHudBarrageLine", shown + 1), at[0], at[1], phase, State.ACTIVE));
        }
        if (batteryCell != null) markers.add(new Marker("heart-battery", Strings.get("heartHudBattery"),
                batteryCell[0], batteryCell[1], gunTally, state == State.FAILED ? State.FAILED : State.ACTIVE));
        float end = lines.isEmpty() ? 0f : lines.get(lines.size() - 1).openSeconds() + lines.get(lines.size() - 1).fireSeconds();
        String blocker = state == State.ACTIVE && standing > 0 && standing < guns && standing <= guns / 2
                ? Strings.format("heartHudBarrageBlockerGuns", standing) : "";
        return new Item("heart-0-barrage", Strings.get("heartHudBarrageName"), state, phase,
                Strings.get("heartHudBarrageSteps"),
                Strings.get(ended ? "heartHudBarrageInstructionEnded" : "heartHudBarrageInstruction"),
                tally, end <= 0f ? -1 : Math.min(1f, seconds / end), blocker, markers, focus);
    }

    /** Seconds as m:ss, never below zero. */
    static String clock(float seconds) {
        int whole = Math.max(0, (int) Math.ceil(seconds - 1e-3f));
        return Strings.format("cordonHudClock", whole / 60, String.format(Locale.ROOT, "%02d", whole % 60));
    }
}
