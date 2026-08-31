package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleMapRenderer;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Locale;

/**
 * What a settlement actually looks like once the campaign chooses its shape —
 * drawn through the production sprite path rather than in debug colours,
 * because the question here is whether the maps are good enough to adopt and a
 * flat-colour diagram cannot answer that.
 *
 * <p>One row per market size, so the ladder from an off-grid outpost to a
 * city is visible in the art, beside a stock map as the control.
 */
class CampaignSettlementRenderTest {

    private static final Path OUT_DIR = Paths.get("build/map-previews");
    private static final int GRID = 80;
    private static final int CELL_PX = 8;
    private static final long[] SEEDS = { 42L, 777L };

    /** Market sizes worth looking at: an outpost, a town, a city. */
    private static final int[] SIZES = { 2, 5, 9 };

    private static HeadlessBattleMapRenderer battleMaps;

    @BeforeAll
    static void installRegistry() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod/" + path))));
        }
        reg.validateReferences();
        TileRegistry.install(reg);
        battleMaps = new HeadlessBattleMapRenderer(Paths.get("mod"));
    }

    @Test
    void renderCampaignDrivenSettlements() throws Exception {
        Files.createDirectories(OUT_DIR);
        for (SurfacePalette palette : new SurfacePalette[]{ SurfacePalette.ROCK, SurfacePalette.ARID }) {
            renderLadder(palette);
        }
    }

    /** One row per market size on one world surface, beside a stock control. */
    private void renderLadder(SurfacePalette palette) throws Exception {
        for (long seed : SEEDS) {
            BufferedImage[] tiles = new BufferedImage[SIZES.length + 1];
            tiles[0] = tile(new BspCityGenerator().generate(GRID, GRID, seed),
                    seed, "stock (no market)");
            for (int i = 0; i < SIZES.length; i++) {
                TargetProfile profile = market(SIZES[i], palette);
                // The override, because production is deliberately still on
                // the stock recipe -- these renders are the evidence for
                // whether that should change.
                MapResult map = new BspCityGenerator()
                        .useGrownRoads(GrownTrunkPlan.Profile.of(
                                SettlementZoning.densityFor(profile.marketSize()), profile.link()))
                        .generate(GRID, GRID, seed, null, profile);
                tiles[i + 1] = tile(map, seed, caption(profile));
            }
            BufferedImage sheet = row(tiles);
            Path out = OUT_DIR.resolve(String.format("campaign-settlement-%s-%04d.png",
                    palette.name().toLowerCase(Locale.ROOT), (int) seed));
            ImageIO.write(sheet, "PNG", out.toFile());
            System.out.println("  wrote " + out.toAbsolutePath());
        }
    }

    private static TargetProfile market(int size, SurfacePalette palette) {
        return new TargetProfile(size, 5, 1, size >= 5 ? 1 : 0, "independent",
                EnumSet.noneOf(EconomicFunction.class), palette,
                SettlementZoning.linkFor(size, false));
    }

    private static String caption(TargetProfile p) {
        SettlementLink link = p.link();
        return String.format("size %d  %s  %s  density %.2f", p.marketSize(), p.surface(), link,
                SettlementZoning.densityFor(p.marketSize()));
    }

    private static BufferedImage tile(MapResult map, long seed, String caption) throws Exception {
        BufferedImage body = battleMaps.render(map, seed, CELL_PX);
        BufferedImage out = new BufferedImage(body.getWidth(), body.getHeight() + 22,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(20, 25, 32));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(body, 0, 0, null);
        g.setColor(new Color(0xE0, 0xE8, 0xF4));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("seed " + seed + "   " + caption, 6, body.getHeight() + 15);
        g.dispose();
        return out;
    }

    private static BufferedImage row(BufferedImage[] tiles) {
        int gap = 8;
        int w = 0, h = 0;
        for (BufferedImage t : tiles) {
            w += t.getWidth() + gap;
            h = Math.max(h, t.getHeight());
        }
        BufferedImage out = new BufferedImage(w + gap, h + 2 * gap, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(20, 25, 32));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        int x = gap;
        for (BufferedImage t : tiles) {
            g.drawImage(t, x, gap, null);
            x += t.getWidth() + gap;
        }
        g.dispose();
        return out;
    }
}
