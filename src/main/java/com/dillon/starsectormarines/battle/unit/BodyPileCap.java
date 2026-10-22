package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.SwarmHarvest;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A safety net on how many bodies lie in one place (`colony-breach-nouns.md`, Body pile cap): where
 * nothing clears the dead — no creep to digest them, no swarm to drag them off — a wave fought out
 * against a defended line leaves its bodies piled outside the wire for the rest of the battle, and
 * every one of them is drawn every frame. When an {@link #AREA_CELLS}-square area holds more than
 * {@link #AREA_LIMIT} bodies, its oldest fade out over {@link #FADE_SECONDS} and are gone, as a body
 * the creep digests is; past {@link #GLOBAL_LIMIT} on the whole field the oldest anywhere go the
 * same way.
 *
 * <p>The limits are generous on purpose: the cap is not a mechanic a player should see. It counts
 * every side's dead, and only bodies still drawn. A body on a carrier — dragged, impaled, being
 * harvested — is never counted or retired, and one taken up mid-fade is spared and lies whole
 * again. A retired body leaves the swarm's recycling with it, so no scavenger drags a body that
 * is no longer there. The corpse entity itself stays, as every hidden body does: the battle's
 * reckoning of the dead reads it.
 *
 * <p>Deterministic: the count is taken every {@link #CHECK_TICKS} ticks, serially, and the oldest
 * are chosen by death tick and then id. {@code -Dbattle.bodyPileCap=false} is the control.
 */
public final class BodyPileCap {

    /** Side of a counted area, cells. */
    public static final int AREA_CELLS = 8;
    /** Bodies an area holds before its oldest begin to fade: about one per cell of it. */
    public static final int AREA_LIMIT = 64;
    /** Bodies the whole field holds before the oldest anywhere begin to fade. */
    public static final int GLOBAL_LIMIT = 1000;
    /** Seconds a retired body takes to fade out. */
    public static final float FADE_SECONDS = 4f;
    /** Ticks between counts: once a second. */
    public static final int CHECK_TICKS = 30;

    /** Whether the cap runs. {@code -Dbattle.bodyPileCap=false} is the control. */
    public static boolean enabledByDefault() {
        return Boolean.parseBoolean(System.getProperty("battle.bodyPileCap", "true"));
    }

    private boolean enabled;
    private final int areaLimit;
    private final int globalLimit;
    /** Bodies fading out, by id, in the order they were retired. */
    private final Map<Long, Float> fading = new LinkedHashMap<>();
    private int retired;
    private int lying;
    private int peakLying;

    private long[] ids = new long[256];
    private int[] ages = new int[256];
    private int[] areas = new int[256];
    private Integer[] order = new Integer[0];

    public BodyPileCap() {
        this(enabledByDefault(), AREA_LIMIT, GLOBAL_LIMIT);
    }

    public BodyPileCap(boolean enabled, int areaLimit, int globalLimit) {
        this.enabled = enabled;
        this.areaLimit = areaLimit;
        this.globalLimit = globalLimit;
    }

    public boolean enabled() { return enabled; }

    /** Switches the cap for this battle, so a scene can play it and its control side by side. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) fading.clear();
    }

    /** How far {@code body} has faded, 0 (lying whole) to 1 (gone). */
    public float faded(long body) {
        Float seconds = fading.isEmpty() ? null : fading.get(body);
        return seconds == null ? 0f : Math.min(1f, seconds / FADE_SECONDS);
    }

    /** Bodies the cap has taken off the field so far. */
    public int retired() { return retired; }

    /** Bodies lying whole at the last count, and the most any count found: readings for evidence. */
    public int lying() { return lying; }

    public int peakLying() { return peakLying; }

    /** Bodies fading out now. */
    public int fadingCount() { return fading.size(); }

    public void tick(BattleSimulation sim) {
        if (!enabled) return;
        BattleComponents c = sim.getBattleComponents();
        EntityWorld world = sim.getEntityWorld();
        SwarmHarvest harvest = sim.swarmHarvest();
        advanceFades(world, c, harvest);
        if (sim.getSimTickIndex() % CHECK_TICKS == 0) count(sim, world, c, harvest);
    }

    private void advanceFades(EntityWorld world, BattleComponents c, SwarmHarvest harvest) {
        if (fading.isEmpty()) return;
        for (Iterator<Map.Entry<Long, Float>> it = fading.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Float> entry = it.next();
            long body = entry.getKey();
            // taken up mid-fade: it is a mechanic's again, and lies whole
            if (harvest.isCarried(body)) {
                it.remove();
                continue;
            }
            // something else had it first: the creep, a fire, a bore
            if (world.getInt(body, c.SPRITE, BattleComponents.SPRITE_INDEX) < 0) {
                it.remove();
                continue;
            }
            float seconds = entry.getValue() + BattleSimulation.TICK_DT;
            if (seconds >= FADE_SECONDS) {
                world.setInt(body, c.SPRITE, BattleComponents.SPRITE_INDEX, -1);
                harvest.forget(body);
                retired++;
                it.remove();
            } else {
                entry.setValue(seconds);
            }
        }
    }

    /** Counts the bodies still lying whole by area and retires the oldest past each limit. */
    private void count(BattleSimulation sim, EntityWorld world, BattleComponents c, SwarmHarvest harvest) {
        int areasAcross = sim.getGrid().getWidth() / AREA_CELLS + 1;
        int n = 0;
        for (ArchetypeTable t : world.matched(c.corpses)) {
            int[] sprite = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            int[] deathTick = t.ints(c.CORPSE, BattleComponents.CORPSE_DEATH_TICK).array();
            float[] x = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] y = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            for (int r = 0, rows = t.rowCount(); r < rows; r++) {
                if (sprite[r] < 0) continue;
                long body = t.entityAt(r);
                if (harvest.isCarried(body) || fading.containsKey(body)) continue;
                if (n == ids.length) grow();
                ids[n] = body;
                ages[n] = deathTick[r];
                areas[n] = Math.floorDiv((int) Math.floor(y[r]), AREA_CELLS) * areasAcross
                        + Math.floorDiv((int) Math.floor(x[r]), AREA_CELLS);
                n++;
            }
        }
        lying = n;
        peakLying = Math.max(peakLying, n);
        retireOldest(n);
    }

    /** Package-visible for the unit test: picks from the first {@code n} gathered bodies. */
    void retireOldest(int n) {
        if (n <= areaLimit && n <= globalLimit) return;
        Map<Integer, int[]> perArea = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) perArea.computeIfAbsent(areas[i], k -> new int[1])[0]++;
        boolean crowded = n > globalLimit;
        for (int[] count : perArea.values()) crowded |= count[0] > areaLimit;
        if (!crowded) return;
        if (order.length < n) order = new Integer[ids.length];
        for (int i = 0; i < n; i++) order[i] = i;
        // oldest first: by death tick, then by id, so the choice repeats run to run
        Arrays.sort(order, 0, n, (a, b) -> ages[a] != ages[b]
                ? Integer.compare(ages[a], ages[b]) : Long.compare(ids[a], ids[b]));
        int lying = n;
        boolean[] taken = new boolean[n];
        for (int k = 0; k < n; k++) {
            int i = order[k];
            int[] count = perArea.get(areas[i]);
            if (count[0] <= areaLimit) continue;
            count[0]--;
            lying--;
            taken[i] = true;
            fading.put(ids[i], 0f);
        }
        for (int k = 0; k < n && lying > globalLimit; k++) {
            int i = order[k];
            if (taken[i]) continue;
            lying--;
            fading.put(ids[i], 0f);
        }
    }

    /** Package-visible for the unit test: one gathered body. */
    void gather(int index, long body, int deathTick, int area) {
        while (index >= ids.length) grow();
        ids[index] = body;
        ages[index] = deathTick;
        areas[index] = area;
    }

    /** Package-visible for the unit test. */
    boolean isFading(long body) { return fading.containsKey(body); }

    private void grow() {
        ids = Arrays.copyOf(ids, ids.length * 2);
        ages = Arrays.copyOf(ages, ages.length * 2);
        areas = Arrays.copyOf(areas, areas.length * 2);
    }
}
