package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepVegetation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Trees the creep is overgrowing, drawn (`colony-breach-nouns.md`, Creep): each
 * standing tree is shown at its stage of the {@code forest-blight} sheet, read
 * from the creep's coverage on its crown, the same age the field keeps for a
 * wall. Stages cross-fade, so a tree turns rather than pops: veins from the
 * first claim, the crust rolling in from {@link #CRUST_AGE}, the withering
 * crown from {@link #WITHER_AGE}, and the dead tree once the simulation has
 * killed it ({@link CreepVegetation}), over {@link #DEATH_FADE_TICKS}. A
 * felled tree the creep had killed lies as dead wood, bone-pale and bare
 * ({@code forest-blight-felled}), shown at once since the felling is the change;
 * one felled alive keeps its green felled art. Presentation only: what the tree
 * blocks is the simulation's.
 */
final class CreepTreeTwins implements DoodadRenderSystem.Twins {

    /** Crown age (0..1) at which each stage is fully shown: veins, crust, withering. */
    static final float VEIN_AGE = 0.10f, CRUST_AGE = 0.40f, WITHER_AGE = 0.72f;
    /** Ticks a dead tree takes to turn from withering to dead. */
    static final int DEATH_FADE_TICKS = 90;
    /** The suffix on a destroyed tree's id naming its felled dead wood. */
    static final String FELLED = "-blight";

    private final CreepField field;
    private final CreepVegetation vegetation;
    private final TileRegistry tiles;
    private final int tick;
    private final int width, height;
    /** Each placement's crown cells and stage doodads, made once. */
    private final Map<Doodad, Entry> entries;

    private Doodad last;
    private float lastStage;

    private CreepTreeTwins(BattleSimulation sim, CreepField field, CreepVegetation vegetation, TileRegistry tiles,
                           Map<Doodad, Entry> entries) {
        this.field = field;
        this.vegetation = vegetation;
        this.tiles = tiles;
        this.entries = entries;
        // The field's own clock, which a tree's death tick was read against: a review advancing the creep alone
        // leaves the battle's clock standing.
        this.tick = field.now();
        this.width = sim.getGrid().getWidth();
        this.height = sim.getGrid().getHeight();
    }

    /** The battle's tree twins, or null where no creep has spread or the sheet is not installed. */
    static CreepTreeTwins of(BattleSimulation sim, Map<Doodad, Entry> cache) {
        CreepField field = sim.creepFieldIfPresent();
        TileRegistry tiles = TileRegistry.installed();
        if (field == null || field.isEmpty() || tiles == null || tiles.doodad("forest.oak-blight-1") == null) return null;
        return new CreepTreeTwins(sim, field, sim.creepVegetationIfPresent(), tiles, cache);
    }

    /** One placement's crown cells and its stage doodads, 1..4 (null where the sheet has none); a felled tree's
     *  only stage is its dead wood, at 4. */
    static final class Entry {
        final int[] crown;
        final Doodad[] stages = new Doodad[5];

        Entry(int[] crown) { this.crown = crown; }
    }

    /** A fresh per-renderer cache of crowns and stage doodads. */
    static Map<Doodad, Entry> cache() { return new WeakHashMap<>(); }

    @Override
    public Doodad base(Doodad placed) {
        float s = stage(placed);
        Doodad shown = s < 1f ? null : at(placed, (int) s);
        return shown == null ? placed : shown;
    }

    @Override
    public Doodad twin(Doodad placed) {
        float s = stage(placed);
        int next = (int) s + 1;
        Doodad shown = s <= 0f || next > 4 ? null : at(placed, next);
        return shown == null ? placed : shown;
    }

    /** The next stage comes in over the first half of the step and the last one leaves over the second, so
     *  neither silhouette pops and the crown is never see-through between them. */
    @Override
    public float weight(Doodad placed) {
        float s = stage(placed);
        return s >= 4f ? 0f : Math.min(1f, 2f * (s - (int) s));
    }

    @Override
    public float baseWeight(Doodad placed) {
        float s = stage(placed);
        return s >= 4f ? 1f : Math.min(1f, 2f * (1f - (s - (int) s)));
    }

    /** The stage this placement shows at, 0 (untouched) to 4 (dead), fractional between. */
    float stage(Doodad placed) {
        if (placed == last) return lastStage;
        last = placed;
        lastStage = computeStage(placed);
        return lastStage;
    }

    private float computeStage(Doodad placed) {
        TreeCanopy canopy = CreepVegetation.canopy(placed.definitionId());
        if (canopy == null) return 0f;
        int died = vegetation == null ? -1 : vegetation.diedAt(placed.cellX, placed.cellY);
        if (felled(placed)) return died >= 0 && at(placed, 4) != null ? 4f : 0f;
        float age = crownAge(field, entry(placed, canopy).crown);
        float s = stageForAge(age);
        if (died >= 0) s = Math.max(s, 3f + Math.min(1f, Math.max(0f, (tick - died) / (float) DEATH_FADE_TICKS)));
        else s = Math.min(s, 3f);
        if (at(placed, 1) == null) return 0f;
        return s;
    }

    /** The stage a crown age shows while the tree lives: 0..3, linear between the stage ages. */
    static float stageForAge(float age) {
        if (age <= 0f) return 0f;
        if (age < VEIN_AGE) return age / VEIN_AGE;
        if (age < CRUST_AGE) return 1f + (age - VEIN_AGE) / (CRUST_AGE - VEIN_AGE);
        if (age < WITHER_AGE) return 2f + (age - CRUST_AGE) / (WITHER_AGE - CRUST_AGE);
        return 3f;
    }

    /** Mean creep coverage over the crown, 0..1: how long the creep has held it. */
    static float crownAge(CreepField field, int[] cells) {
        if (cells.length == 0) return 0f;
        long sum = 0;
        for (int cell : cells) sum += field.coverageAt(cell);
        return sum / (255f * cells.length);
    }

    private static boolean felled(Doodad placed) {
        return placed.definitionId().endsWith("-destroyed");
    }

    private Doodad at(Doodad placed, int stage) {
        TreeCanopy canopy = CreepVegetation.canopy(placed.definitionId());
        return canopy == null ? null : entry(placed, canopy).stages[stage];
    }

    private Entry entry(Doodad placed, TreeCanopy canopy) {
        Entry entry = entries.get(placed);
        if (entry == null) {
            boolean felled = felled(placed);
            entry = new Entry(felled ? new int[0] : CreepVegetation.crownCells(placed, canopy, width, height));
            for (int k = felled ? 4 : 1; k <= 4; k++) {
                DoodadDef def = tiles.doodad(placed.definitionId() + (felled ? FELLED : "-blight-" + k));
                if (def != null) {
                    entry.stages[k] = new Doodad(placed.cellX, placed.cellY, def, placed.quarterTurns, placed.mirrored);
                }
            }
            entries.put(placed, entry);
        }
        return entry;
    }
}
