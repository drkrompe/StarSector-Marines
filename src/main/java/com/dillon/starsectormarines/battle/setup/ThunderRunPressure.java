package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Thunder Run's rising pressure (`early-operation-nouns.md`, Thunder Run): from the column's first release, raider
 * waves keep coming in down the side arms and the outbound road, each sooner and larger than the last
 * ({@link #gapSeconds}, {@link #waveSize}), until {@link #WAVES} have gone.
 *
 * <p>While the company holds, a wave goes for the militia's back lines: the held posts behind the company, oldest
 * first in turn, so the staging, Dispatch and the gun bays all come under attack and the company must choose whether
 * to send help back. Once the final run is released, every wave goes for the convoy: it is sent at the road ahead of
 * the leading hauler still running, carrying scrap rockets.
 */
public final class ThunderRunPressure {
    static final String ISSUER = "thunder-run-pressure";
    public static final int WAVES = 12;
    static final int FIRST_GAP_SECONDS = 40, LAST_GAP_SECONDS = 16, GAP_STEP_SECONDS = 4;
    static final int FIRST_SIZE = 4, LAST_SIZE = 8;
    /** A post still counts as a militia line while an ally stands within this of it. */
    private static final float LINE_RADIUS = 12f;
    /** Where waves come in: the crossroad's two side arms and the outbound road's, north and south. */
    private static final int[][] ENTRIES = {{165, 2}, {165, 140}, {275, 2}, {275, 140}};

    private final Random rng;
    private int startTick = -1, sent, sentMembers;
    private final List<Integer> squads = new ArrayList<>();

    ThunderRunPressure(long seed) { this.rng = new Random(seed * 31 + 7); }

    /** Seconds between wave {@code n} and the one before it (or the column's first release). */
    public static int gapSeconds(int n) { return Math.max(LAST_GAP_SECONDS, FIRST_GAP_SECONDS - GAP_STEP_SECONDS * n); }
    /** Raiders in wave {@code n}. */
    public static int waveSize(int n) { return Math.min(LAST_SIZE, FIRST_SIZE + n / 2); }
    /** Seconds after the column's first release that wave {@code n} goes. */
    public static int waveSecond(int n) {
        int second = 0;
        for (int i = 0; i <= n; i++) second += gapSeconds(i);
        return second;
    }
    public static int totalMembers() {
        int total = 0;
        for (int n = 0; n < WAVES; n++) total += waveSize(n);
        return total;
    }

    public int sent() { return sent; }
    public List<Integer> squads() { return List.copyOf(squads); }
    public int pendingMembers() { return totalMembers() - sentMembers; }

    /**
     * @param phase   the sequence's phase: 0 before the column moves, 3 on the final run
     * @param leadCargoX the x of the leading hauler still running, or -1 for none
     */
    void tick(BattleSimulation sim, int phase, float leadCargoX) {
        if (phase < 1 || sent >= WAVES) return;
        int now = sim.getSimTickIndex();
        if (startTick < 0) startTick = now;
        if (now - startTick < waveSecond(sent) * ThunderRunPacing.TICKS_PER_SECOND) return;
        int[] target = phase >= 3 ? convoyTarget(leadCargoX) : backLine(sim, phase);
        if (target == null) return;
        int[] entry = nearestEntry(target[0]);
        int size = waveSize(sent);
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        int member = 0;
        for (int[] cell : BattleSetup.pickSpawnCluster(sim.getGrid(), entry[0], entry[1], size)) {
            var raider = BattleSetup.makeOpeningMilitia("thunder-run-pressure-" + sent + "-" + member, Faction.DEFENDER,
                    cell[0], cell[1], rng).role(UnitRole.PATROL);
            if (member < (phase >= 3 ? 2 : 1))
                raider.specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SCRAP_ROCKET_ID), 2);
            sim.spawn(raider.squad(squad));
            member++;
        }
        sim.getSquad(squad).originalSize = member;
        sim.assignSquadCommand(phase >= 3
                        ? ObjectiveAssignment.attackMove(squad, target[0], target[1])
                        : ObjectiveAssignment.defendArea(squad, target[0], target[1], 6),
                CommandAuthority.SCRIPTED, ISSUER, phase >= 3 ? "cut the convoy" : "hit the militia's back line");
        squads.add(squad);
        sentMembers += size;
        sent++;
    }

    /** The road ahead of the leading hauler, where a wave meets the convoy; null once no hauler runs. */
    private static int[] convoyTarget(float leadCargoX) {
        if (leadCargoX < 0) return null;
        return new int[]{Math.min(ThunderRunCompound.WIDTH - 20, Math.round(leadCargoX) + 25), 75};
    }

    /** The held militia posts behind the company, taken in turn; null when the militia hold none. */
    private int[] backLine(BattleSimulation sim, int phase) {
        List<ThunderRunCompound.Cell> lines = new ArrayList<>();
        for (int site = 0; site <= Math.min(phase, 2); site++) lines.addAll(ThunderRunCompound.militiaPosts(site));
        lines.addAll(ThunderRunCompound.SUPPORT_POSTS);
        lines.removeIf(post -> !alliesNear(sim, post));
        if (lines.isEmpty()) return null;
        var post = lines.get(sent % lines.size());
        return new int[]{post.x(), post.y()};
    }

    private int[] nearestEntry(int x) {
        // North and south in turn, from whichever arm is nearer the target.
        int best = 0;
        for (int i = 0; i < ENTRIES.length; i += 2)
            if (Math.abs(ENTRIES[i][0] - x) < Math.abs(ENTRIES[best][0] - x)) best = i;
        return ENTRIES[best + sent % 2];
    }

    private static boolean alliesNear(BattleSimulation sim, ThunderRunCompound.Cell post) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.ALLY) continue;
            float dx = sim.world().x(unit) - post.x(), dy = sim.world().y(unit) - post.y();
            if (dx * dx + dy * dy <= LINE_RADIUS * LINE_RADIUS) return true;
        }
        return false;
    }
}
