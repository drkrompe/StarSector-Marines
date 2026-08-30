package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where a faction has recently lost people, at influence-block resolution, so a
 * commander can decline to send the next squad up the lane the last one died
 * in.
 *
 * <p><b>This is a side's memory of its own casualties, not intelligence about
 * the enemy.</b> That is what makes it legal to read: a faction knows who it
 * lost and roughly where, the same class of own-force fact the friendly
 * influence field already carries. It says nothing about who did the killing or
 * where they are now — a lane can stay expensive long after the ambush has
 * moved on, which is the honest shape of the knowledge rather than a defect.
 *
 * <p>Weight decays with a half-life rather than expiring, because ground does
 * not become safe on a deadline. A place bought with a squad stays discouraging
 * for a while, then stops mattering, and a second squad lost in the same place
 * pushes it back up.
 *
 * <p>Only combatant losses count. A civilian caught in the open says something
 * about the map but nothing about whether a route is survivable for a fire
 * team, and letting it weigh would make evacuation corridors read as killing
 * grounds.
 */
public final class CasualtyMemory {

    /**
     * Ticks for a remembered loss to decay to half weight - thirty seconds at
     * the simulation's thirty ticks a second. Expressed in ticks rather than
     * seconds so this package stays tick-native like the refresh interval
     * beside it, and does not have to reach into the sim for a frame duration.
     */
    public static final float HALF_LIFE_TICKS = 900f;

    private final UnitRosterService roster;
    private final int blockSize;
    private final int blockWidth;
    private final int blockHeight;
    private final Map<Faction, float[]> byFaction = new EnumMap<>(Faction.class);

    public CasualtyMemory(UnitRosterService roster, int blockSize,
                          int blockWidth, int blockHeight) {
        this.roster = roster;
        this.blockSize = blockSize;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
    }

    /**
     * Records one loss against the faction that suffered it. Subscribed to the
     * death dispatcher, so it sees every death exactly once and never has to
     * scan the roster for corpses.
     */
    public void onDeath(DeathEvent event) {
        long unit = event.unitId();
        if (!roster.identity().has(unit)) return;
        if (!roster.identity().type(unit).combatant) return;
        Faction faction = roster.identity().faction(unit);
        if (faction == null) return;
        // floorDiv, not /: integer division truncates toward zero, so a death
        // one cell off the west edge divides to block 0 and is recorded as
        // having happened in the corner of the map.
        int bx = Math.floorDiv(event.cellX(), blockSize);
        int by = Math.floorDiv(event.cellY(), blockSize);
        if (bx < 0 || by < 0 || bx >= blockWidth || by >= blockHeight) return;
        field(faction)[by * blockWidth + bx] += 1f;
    }

    /**
     * Decays every faction's memory toward nothing. Called on the influence
     * refresh cadence rather than per tick, so the decay step is sized from the
     * interval it actually runs at.
     */
    public void decay(int elapsedTicks) {
        if (elapsedTicks <= 0) return;
        float factor = (float) Math.pow(0.5, elapsedTicks / HALF_LIFE_TICKS);
        for (float[] field : byFaction.values()) {
            for (int i = 0; i < field.length; i++) {
                float value = field[i] * factor;
                field[i] = value < 0.01f ? 0f : value;
            }
        }
    }

    /** An immutable copy for the frozen snapshot a commander plans against. */
    public float[] copyFor(Faction faction) {
        float[] field = byFaction.get(faction);
        return field == null ? new float[blockWidth * blockHeight] : field.clone();
    }

    private float[] field(Faction faction) {
        return byFaction.computeIfAbsent(faction,
                f -> new float[blockWidth * blockHeight]);
    }
}
