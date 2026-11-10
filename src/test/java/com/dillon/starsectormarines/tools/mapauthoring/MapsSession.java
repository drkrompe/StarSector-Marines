package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Maps page's domain, shared by the workbench page and the headless tool so both lay, check and draw a map
 * the same way: one opened map, the edits over it, and the world it is shown as.
 */
public final class MapsSession implements AutoCloseable {

    /** A built map under one skin: the map, its cells, and every check that failed (empty when all pass). */
    public record Built(SketchApplier.Applied applied, List<String> problems) { }

    private final Path projectRoot;
    private final MapsRender render;
    private final HeadlessStrings strings;

    public MapsSession(Path projectRoot) throws Exception {
        this.projectRoot = projectRoot;
        DiskRegistries.installMapGeneration(projectRoot);
        strings = HeadlessBattles.install(projectRoot);
        render = new MapsRender(projectRoot.resolve("mod"));
    }

    public Path projectRoot() { return projectRoot; }

    /** The base regenerated fresh, the sketch laid on it under {@code skin}, and the checks run. */
    public Built build(MapBases.Base base, MapSketch sketch, BiomeSkin skin) {
        MapResult map = base.generate();
        SketchApplier.Applied applied = SketchApplier.apply(map, sketch, base.builtAs(), skin);
        List<String> problems = new ArrayList<>(applied.problems());
        problems.addAll(MapChecks.run(map.grid, applied.cut(), applied.roads(), sketch));
        return new Built(applied, problems);
    }

    /**
     * {@code sketch} with a crossing wherever one of its drops cuts a road of the opened map: one per run of road
     * cells along the drop, as wide as the road there and two cells more. Crossings already drawn are kept.
     */
    public MapSketch bridgeRoads(MapBases.Base base, MapSketch sketch) {
        MapResult map = base.generate();
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        boolean[] street = new boolean[w * h];
        for (int i = 0; i < street.length; i++) street[i] = map.topology.getGroundKind(i % w, i / w) == GroundKind.STREET;
        return sketch.withDrops(drops -> {
            List<MapSketch.Drop> out = new ArrayList<>();
            for (MapSketch.Drop d : drops) out.add(d.withCrossings(RoadCrossings.add(d, street, w, h)));
            return out;
        });
    }

    /** The map as it plays, whole, with the page's marks over it when {@code marked}. */
    public BufferedImage picture(Built built, MapSketch sketch, boolean marked, String selected) throws Exception {
        MapsRender.Frame frame = render.whole(built.applied().map(), MapsRender.WIDTH, MapsRender.HEIGHT);
        if (marked) {
            Graphics2D g = frame.image().createGraphics();
            MapsRender.mark(g, frame, sketch, built.applied().skin(), selected);
            MapsRender.problems(g, frame.image().getHeight(), built.problems());
            g.dispose();
        }
        return frame.image();
    }

    /** The frame and its cell mapping, for an editor that needs to turn a click into a cell. */
    public MapsRender.Frame frame(Built built) throws Exception {
        return render.whole(built.applied().map(), MapsRender.WIDTH, MapsRender.HEIGHT);
    }

    /** The same layout under every skin, marked, side by side. */
    public BufferedImage contactSheet(MapBases.Base base, MapSketch sketch) throws Exception {
        List<MapsRender.Tile> tiles = new ArrayList<>();
        for (BiomeSkin skin : BiomeSkin.values()) {
            Built built = build(base, sketch, skin);
            BufferedImage image = crop(frame(built));
            String adaptation = sketch.settlements().isEmpty() ? skin.adaptation(MapSketch.Program.TOWN)
                    : skin.adaptation(sketch.settlements().get(0).program());
            String drop = built.applied().map().topology.naturalVoid() == null ? "none"
                    : built.applied().map().topology.naturalVoid().style().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            tiles.add(new MapsRender.Tile(skin, image, drop, adaptation, built.problems().size()));
        }
        return MapsRender.contactSheet(base.label() + " · every world", tiles);
    }

    /** Just the map's own cells of a frame, without the letterbox round it. */
    static BufferedImage crop(MapsRender.Frame f) {
        int x0 = Math.max(0, Math.round(f.sx(0))), y0 = Math.max(0, Math.round(f.sy(f.mapH())));
        int x1 = Math.min(f.image().getWidth(), Math.round(f.sx(f.mapW()))), y1 = Math.min(f.image().getHeight(), Math.round(f.sy(0)));
        BufferedImage view = f.image().getSubimage(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
        // A copy, so the tile does not hold the whole frame's raster.
        BufferedImage out = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(view, 0, 0, null);
        g.dispose();
        return out;
    }

    @Override
    public void close() {
        if (strings != null) strings.close();
    }
}
