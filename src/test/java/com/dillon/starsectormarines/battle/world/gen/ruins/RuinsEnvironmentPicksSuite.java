package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Picture options for the owner's {@code ruins-themed-environments} pick ({@code ruins-expedition.md}, Themed halls
 * are environments): a themed site's halls laid with the game's own climate ground (surface skins, shallows) instead
 * of bare slab. Each frame is a real render through the look preset at 1920x1080, roofs off; the environment is laid
 * here on the built map's cells by coordinate noise, as a sketch of what the ground would lay. Opt-in:
 * {@code -Psnapshot=ruins-environment-picks}.
 */
public final class RuinsEnvironmentPicksSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final long SEED = 7L;

    /** How far an option lets the theme's ground reach. */
    private enum Reach { HALLS, DEEPENING, SPILLING }

    @Override public String id() { return "ruins-environment-picks"; }

    @Override public String label() { return "Ruins themed environments: picture options"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        RuinsVaultTheme cryo = RuinsVaultTheme.CRYOARITHMETIC_ENGINE;
        out.add(frame(context, cryo, null, "vault", "today-cryo-vault.png",
                "Today: the cryo vault is a concrete room with themed props"));
        out.add(frame(context, cryo, Reach.HALLS, "vault", "a-cryo-vault.png",
                "a. Every themed hall is its ground (recommended): the cryo store frozen over"));
        out.add(frame(context, cryo, Reach.HALLS, "line", "a-cryo-line-hall.png",
                "a. The same in a cryo line hall: rime banked on the walls, frost across the floor"));
        out.add(frame(context, cryo, Reach.DEEPENING, "inside", "b-cryo-deepening.png",
                "b. The theme deepens: bare at the entrances, frozen solid by the secure store"));
        out.add(frame(context, cryo, Reach.SPILLING, "site", "c-cryo-spills-out.png",
                "c. The theme spills out: frost runs from the plant into the dead town"));
        out.add(frame(context, RuinsVaultTheme.SOIL_NANITES, Reach.HALLS, "line", "a-growth-line-hall.png",
                "a. A soil-nanite plant: earth, growth and standing water across the halls"));
        out.add(frame(context, RuinsVaultTheme.FUSION_LAMP, Reach.HALLS, "line", "a-reactor-line-hall.png",
                "a. A lamp works: coolant shallows and scorched, crusted floor"));
        return out;
    }

    private SnapshotArtifact frame(SnapshotContext context, RuinsVaultTheme theme, Reach reach, String view,
                                   String file, String caption) throws Exception {
        RuinsVaultPrize prize = new RuinsVaultPrize(theme.family, theme,
                theme.family == RuinsVaultPrize.Family.AI_CORE ? RuinsVaultPrize.BETA_CORE : theme.itemId, 2);
        RuinsBattleFixture fixture = RuinsBattleFixture.fromFactoryInputs(SEED, BattleSetup.defaultManifest(), false,
                OperationTier.VETERAN, RiskLevel.MEDIUM, RuinsGroundEvidence.profile(SurfacePalette.ARID),
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, RuinsGrade.EXTENSIVE, RuinsOccupant.SCAVENGERS, List.of(), prize);
        try (BattleSimulation sim = fixture.build()) {
            RuinsDelve delve = sim.getRuinsWorks().delve();
            if (reach != null) lay(sim, delve, theme, reach);
            RuinsDelve.Area at = switch (view) {
                case "vault" -> delve.vault().footprint();
                case "line" -> line(delve);
                case "inside" -> delve.interior().footprint();
                default -> new RuinsDelve.Area(delve.interior().footprint().left() - 60, 0,
                        delve.interior().footprint().left() + 60, sim.getGrid().getHeight() - 1);
            };
            float width = switch (view) {
                case "vault" -> at.width() + 16f;
                case "line" -> 62f;
                default -> at.width() * 1.05f;
            };
            BufferedImage image = BattleReviewFrameRenderer.look(context.modRoot(), W, H, at.centreX() + 0.5f,
                    at.centreY() + 0.5f, width).withoutRoofs().render(sim, "");
            caption(image, caption);
            return new SnapshotArtifact(file, image);
        }
    }

    private static RuinsDelve.Area line(RuinsDelve delve) {
        for (RuinsDelve.PlantHall h : delve.interior().halls()) {
            if (h.stage() == RuinsDelve.PlantStage.PRODUCTION) return h.footprint();
        }
        return delve.vault().footprint();
    }

    /** Lays the theme's ground over the site's halls, as far as the option reaches. */
    private static void lay(BattleSimulation sim, RuinsDelve delve, RuinsVaultTheme theme, Reach reach) {
        CellTopology topo = sim.getTopology();
        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
        RuinsDelve.Area in = delve.interior().footprint();
        RuinsDelve.Area vault = delve.vault().footprint();
        int base, heavy, wet;
        switch (theme) {
            case SOIL_NANITES -> {
                base = topo.surfaceIndex("floors.dirt");
                heavy = topo.surfaceIndex("floors.grass");
                wet = 1;
            }
            case FUSION_LAMP -> {
                base = topo.surfaceIndex("toxic-ground.crust");
                heavy = topo.surfaceIndex("volcanic.ash");
                wet = 1;
            }
            default -> {
                base = topo.surfaceIndex("floors.snow");
                heavy = base;
                wet = 0;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (topo.isWall(x, y)) continue;
                boolean inside = in.contains(x, y) || vault.contains(x, y);
                float share;
                if (inside) {
                    share = switch (reach) {
                        case HALLS, SPILLING -> .48f;
                        case DEEPENING -> .1f + .85f * Math.max(0f, (x - in.left()) / (float) Math.max(1, vault.left() - in.left()));
                    };
                } else if (reach == Reach.SPILLING) {
                    int out = in.left() - x;
                    if (out < 0 || out > 50) continue;
                    share = .55f * (1f - out / 50f);
                } else continue;
                // Rime banks against the walls: the share rises within two cells of one.
                if (wallNear(topo, x, y, w, h)) share += .25f;
                float n = noise(x, y);
                if (n < share) topo.setSurface(x, y, n < share * .3f ? heavy : base);
                if (wet == 1 && inside && n > .8f) topo.setTag(x, y, CellTopology.Tag.SHALLOW_WATER, true);
            }
        }
    }

    private static boolean wallNear(CellTopology topo, int x, int y, int w, int h) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && topo.isWall(nx, ny)) return true;
            }
        }
        return false;
    }

    /** Smooth value noise in 0..1 over two octaves, drawn from the cell alone. */
    private static float noise(int x, int y) {
        return .65f * value(x / 7f, y / 7f, 11) + .35f * value(x / 2.5f, y / 2.5f, 23);
    }

    private static float value(float x, float y, int salt) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(x0, y0, salt), b = hash(x0 + 1, y0, salt), c = hash(x0, y0 + 1, salt), d = hash(x0 + 1, y0 + 1, salt);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float hash(int x, int y, int salt) {
        long v = x * 73_856_093L ^ y * 19_349_663L ^ (SEED + salt) * 83_492_791L;
        v ^= v >>> 13;
        v *= 0x5bd1e995L;
        v ^= v >>> 15;
        return Math.floorMod(v, 10_000L) / 10_000f;
    }

    private static void caption(BufferedImage image, String text) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        int tw = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(24, 18, tw + 40, 52, 14, 14);
        g.setColor(Color.WHITE);
        g.drawString(text, 44, 53);
        g.dispose();
    }
}
