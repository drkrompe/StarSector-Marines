package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.QuadBatch;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Every prop the current map places, cut out of its catalog sheet and
 * composited into one GL texture, so the {@code DOODADS} layer is a run of one
 * sheet rather than a flush at every change of sheet.
 *
 * <p><b>Why.</b> {@code renderEvidence} on main (2026-10-23, 1920x1080): with the
 * ground and the bodies atlased, {@code DOODADS} was the layer left over — at the
 * whole-map framing 405 texture binds on Conquest and 720 on the colony, and 102
 * at a single lane of The Burn. A map's props come from a dozen catalog sheets
 * and are drawn in placement order, because a room lays its deck covering
 * before its fixtures; so every neighbouring pair from different sheets is a
 * flush, and nothing else interrupts the run.
 *
 * <p><b>Regions of the sheets the map uses, not the sheets.</b> The catalog is
 * some forty megatexels across ninety sheets, and one forest sheet alone is
 * 3584 wide; a map places a few hundred distinct cuts of a few of them. So the
 * atlas is planned at battle setup from the placements themselves — every
 * placed prop's source rectangle — plus the rectangles a battle can swap a
 * placement to: each placed definition's family in the catalog (its
 * {@code -damaged}, {@code -destroyed} and blight stages, found by id prefix).
 * Anything a battle draws that is not here keeps its own sheet: the same
 * picture, one more bind.
 *
 * <p>{@link SpriteAtlas} owns the mechanism, including the apron of real
 * neighbouring texels that keeps a cut's edge sampling what it sampled on its
 * sheet. What is here is which rectangles go in, and the switch that takes them
 * back out for a control run: {@code -Dbattle.render.doodadAtlas=false}.
 */
public final class DecorationAtlas extends SpriteAtlas {

    /** Control-run switch; see the class note. On by default. */
    public static final String PROPERTY = "battle.render.doodadAtlas";

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    private static final int[] SIDES = {1024, 2048, 4096};

    /**
     * Texels of the sheet copied around each cut.
     *
     * <p>Bilinear reaches one texel past the rectangle a quad samples, and on
     * the sheet that texel is the neighbouring cell's art. Two keeps that
     * picture with a texel to spare.
     */
    static final int APRON_PX = 2;

    /**
     * The longest side a cut may have and still get a slot.
     *
     * <p>Above every prop a battle map places today — the largest are the
     * 960-texel planet formations — and below a size that would let one piece
     * take a quarter of the largest atlas.
     */
    static final int MAX_REGION_PX = 1024;

    /** Distinct cuts offered from placements and from their families, and texels by sheet; for {@link #detail}. */
    private final Set<String> placedCuts = new HashSet<>();
    private final Set<String> familyCuts = new HashSet<>();
    private final Map<String, Long> texelsBySheet = new HashMap<>();

    public DecorationAtlas() {
        super("decoration atlas", ENABLED, SIDES);
    }

    /** Whether the atlas is armed at all — for evidence that reports which run it was. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Settles the layout from a map's placements.
     *
     * @param sheets the sheet a placement draws from, by its catalog path
     * @param batches each sheet's own batch, whose content size is the one the
     *                control run's UVs are divided by
     * @return whether a layout exists at all
     */
    public boolean plan(Iterable<Doodad> placed, TileRegistry registry,
                        Function<Doodad, SpriteAPI> sheets, Function<SpriteAPI, QuadBatch> batches) {
        return plan(placed, registry, sheets, sheets, batches);
    }

    /**
     * {@link #plan(Iterable, TileRegistry, Function, Function)}, asking {@code familySheets} for the
     * sheet of a placed prop's family state rather than {@code sheets}. A host that loads only what
     * its battle draws answers it with sheets already loaded, so the family's wide net (every
     * sibling under a placed id's last {@code -}) packs the states the battle loaded and never
     * loads a sheet for a sibling nobody placed ({@code battle-textures.md}).
     */
    public boolean plan(Iterable<Doodad> placed, TileRegistry registry, Function<Doodad, SpriteAPI> sheets,
                        Function<Doodad, SpriteAPI> familySheets, Function<SpriteAPI, QuadBatch> batches) {
        if (placed == null) return isPlanned();
        List<Region> candidates = new ArrayList<>();
        Set<String> placedIds = new HashSet<>();
        for (Doodad d : placed) {
            offer(candidates, d, sheets, batches, placedCuts);
            if (d.definitionId() != null) placedIds.add(d.definitionId());
        }
        if (registry != null && !placedIds.isEmpty()) {
            for (DoodadDef def : registry.doodads()) {
                if (def == null || def.id == null || placedIds.contains(def.id)
                        || !inPlacedFamily(def.id, placedIds)) continue;
                offer(candidates, new Doodad(0, 0, def), familySheets, batches, familyCuts);
            }
        }
        // One cut is already one bind; a map with almost no props has nothing to gain.
        return planRegions(candidates, APRON_PX, 2);
    }

    /**
     * Whether {@code id} is a state of something placed: a placed id followed
     * by {@code -suffix} ({@code crate-damaged}, {@code oak-blight-2}), or a
     * sibling under a placed id's last {@code -} ({@code sandbag-damaged-straight}
     * beside {@code sandbag-straight}).
     */
    static boolean inPlacedFamily(String id, Set<String> placedIds) {
        for (int dash = id.lastIndexOf('-'); dash > 0; dash = id.lastIndexOf('-', dash - 1)) {
            if (placedIds.contains(id.substring(0, dash))) return true;
        }
        int stem = id.lastIndexOf('-');
        while (stem > 0) {
            String prefix = id.substring(0, stem + 1);
            for (String placedId : placedIds) {
                if (placedId.startsWith(prefix) && placedId.indexOf('-', prefix.length()) < 0) return true;
            }
            stem = id.lastIndexOf('-', stem - 1);
        }
        return false;
    }

    /**
     * How many cuts came from placements and how many from their families, and
     * the sheets that hold the most texels: what to look at when an atlas is
     * larger than expected.
     */
    @Override
    protected String detail() {
        if (placedCuts.isEmpty() && familyCuts.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        out.append(placedCuts.size()).append(" cuts placed, ").append(familyCuts.size())
                .append(" from their families; most texels from ");
        List<Map.Entry<String, Long>> heaviest = new ArrayList<>(texelsBySheet.entrySet());
        heaviest.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < Math.min(3, heaviest.size()); i++) {
            if (i > 0) out.append(", ");
            String sheet = heaviest.get(i).getKey();
            out.append(sheet.substring(sheet.lastIndexOf('/') + 1)).append(' ')
                    .append(heaviest.get(i).getValue() / 1000).append('k');
        }
        return out.toString();
    }

    private void offer(List<Region> into, Doodad d, Function<Doodad, SpriteAPI> sheets,
                       Function<SpriteAPI, QuadBatch> batches, Set<String> cuts) {
        SpriteAPI sheet = sheets.apply(d);
        if (sheet == null) return;
        String name = (d.definitionId() != null ? d.definitionId() : d.sheetPath)
                + " " + DoodadRenderSystem.sourceWidth(d) + "x" + DoodadRenderSystem.sourceHeight(d);
        QuadBatch batch = batches.apply(sheet);
        if (batch == null) {
            exclude(Exclusion.NO_SIZE, name + " (no batch for " + d.sheetPath + ")");
            return;
        }
        int w = DoodadRenderSystem.sourceWidth(d);
        int h = DoodadRenderSystem.sourceHeight(d);
        if (w > MAX_REGION_PX || h > MAX_REGION_PX) {
            exclude(Exclusion.OVER_CAP, name);
            return;
        }
        int x = DoodadRenderSystem.sourceX(d);
        int y = DoodadRenderSystem.sourceY(d);
        String cut = d.sheetPath + " " + x + "," + y + " " + w + "x" + h;
        if (!placedCuts.contains(cut) && !familyCuts.contains(cut) && cuts.add(cut)) {
            texelsBySheet.merge(d.sheetPath, (long) (w + 2 * APRON_PX) * (h + 2 * APRON_PX), Long::sum);
        }
        into.add(new Region(sheet, batch.sheetPxW(), batch.sheetPxH(), x, y, w, h, name));
    }
}
