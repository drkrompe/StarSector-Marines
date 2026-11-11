package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.List;
import java.util.function.Consumer;

/**
 * A Raid's pressure over time ({@code raid-demolish-the-works.md}, slice 8; owner pick {@code raid-pressure} b, and
 * "Also consider having forces coming from off map. We can't limit ourselves to 'keeping it even'"): the colony's
 * wider garrison drives in from off the map, whatever the raid has taken.
 *
 * <ul>
 *   <li>Each machine the raid wrecks (a hall brought down) brings a wave at once, its size by the market's security.</li>
 *   <li>Garrison columns come on a clock from the battle's start, every {@link Clock#routineSeconds} while the market
 *       is quiet and every {@link Clock#alarmSeconds} once its alarm is up; each is larger than the one before.</li>
 * </ul>
 *
 * <p>From column {@link Clock#armourFromColumn} on, a hull of the market's own line drives in with each column (pick
 * {@code raid-columns-armour} a): the first its gate hull, every later one its yard tank.
 *
 * <p>Both are {@link ReinforcementRequest.Reason#GARRISON_COLUMN} requests, prepaid: they cost the market no ticket and
 * a convoy brings them whether or not it still holds an armory ({@link ConvoyMeans}). The next column's tick is
 * published on the objective for the player's countdown ({@link RaidObjective#columnDueTick}).
 */
public final class RaidPressureTrigger implements ReinforcementTrigger {

    /**
     * How hard a market presses a raid: seconds between garrison columns while quiet and once the alarm is up, the
     * size of the wave a fallen machine brings, the size of the first column, and the column (from 0) armour first
     * joins, negative for never.
     */
    public record Clock(int routineSeconds, int alarmSeconds, ReinforcementRequest.Strength hallWave,
                        ReinforcementRequest.Strength firstColumn, int armourFromColumn) { }

    private final Clock clock;
    private final List<VehicleType> armour;
    private int startTick = -1;
    private int lastColumnTick = -1;
    private int columns;
    private int machinesSeen;

    /** {@code armour}: the hulls that join the columns, in the order they first join; the last repeats. */
    public RaidPressureTrigger(Clock clock, List<VehicleType> armour) {
        this.clock = clock;
        this.armour = List.copyOf(armour);
    }

    @Override
    public void check(BattleView sim, Consumer<ReinforcementRequest> out) {
        RaidObjective raid = null;
        for (Objective objective : sim.getObjectives()) if (objective instanceof RaidObjective r) raid = r;
        if (raid == null) return;
        int tick = sim.getSimTickIndex();
        if (startTick < 0) startTick = tick;
        int x = raid.targetCellX(), y = raid.targetCellY();
        while (machinesSeen < raid.sitesDone()) {
            machinesSeen++;
            out.accept(request(clock.hallWave(), x, y));
        }
        int due = due(lastColumnTick < 0 ? startTick : lastColumnTick, raid.defenderAlarm().raisedTick(), clock);
        if (tick >= due) {
            ReinforcementRequest column = request(columnStrength(columns, clock.firstColumn()), x, y);
            VehicleType hull = armour(columns, clock.armourFromColumn(), armour);
            out.accept(hull == null ? column : column.withEscort(hull));
            columns++;
            lastColumnTick = tick;
            due = due(tick, raid.defenderAlarm().raisedTick(), clock);
        }
        raid.columnDue(due);
    }

    private static ReinforcementRequest request(ReinforcementRequest.Strength strength, int x, int y) {
        return new ReinforcementRequest(Faction.DEFENDER, ReinforcementRequest.Reason.GARRISON_COLUMN, strength,
                x, y, x, y, true);
    }

    /**
     * The tick the next garrison column is due, the last having come at {@code lastTick} (or the battle having started
     * then): the quiet clock from it, or, once the alarm was raised at {@code alarmTick} (negative while quiet), the
     * alarm clock from the later of the two, whichever comes first.
     */
    static int due(int lastTick, int alarmTick, Clock clock) {
        int quiet = lastTick + seconds(clock.routineSeconds());
        if (alarmTick < 0) return quiet;
        return Math.min(quiet, Math.max(lastTick, alarmTick) + seconds(clock.alarmSeconds()));
    }

    /** The size of column {@code n} (from 0): {@code first}, one size larger every second column, at most LARGE. */
    static ReinforcementRequest.Strength columnStrength(int n, ReinforcementRequest.Strength first) {
        ReinforcementRequest.Strength[] sizes = ReinforcementRequest.Strength.values();
        return sizes[Math.min(sizes.length - 1, first.ordinal() + n / 2)];
    }

    /**
     * The hull that drives in with column {@code n} (from 0), or null: none before column {@code from} (nor ever for
     * a negative {@code from}, or no hulls), then {@code hulls} in turn, the last repeating.
     */
    static VehicleType armour(int n, int from, List<VehicleType> hulls) {
        if (from < 0 || n < from || hulls.isEmpty()) return null;
        return hulls.get(Math.min(n - from, hulls.size() - 1));
    }

    private static int seconds(int seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }
}
