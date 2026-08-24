package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java2D backend for the exact Armory preview recipe; requires no game process. */
public final class HeadlessArmoryPreviewRenderer {

    private static final String MODULAR_ROOT =
            "graphics/battle/marine-modular-topdown/variants/";
    private static final String FOOT =
            "graphics/battle/marine-modular-topdown/marine-foot.png";
    private static final String FLASH =
            "graphics/battle/marine-modular-topdown/marine-muzzle-flash.png";

    private final RasterAssets assets;

    public HeadlessArmoryPreviewRenderer(Path modRoot) {
        try {
            installCatalogs(modRoot);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Could not load catalogs for headless Armory rendering", failure);
        }
        assets = new RasterAssets(modRoot);
    }

    public BufferedImage render(FireTeamBillet billet) {
        BufferedImage output = new BufferedImage(
                ArmoryLoadoutPreviewComposer.SURFACE_WIDTH,
                ArmoryLoadoutPreviewComposer.SURFACE_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        configure(graphics);
        ArmoryLoadoutPreviewComposer.compose(
                new RasterSink(graphics, assets.images, 0f, 0f), assets, billet,
                output.getWidth(), output.getHeight());
        graphics.dispose();
        return output;
    }

    /** Asset source shared by full retained-view previews using the live canvas producer. */
    public ArmoryLoadoutPreviewComposer.Assets assets() {
        return assets;
    }

    static void installCatalogs(Path modRoot) throws Exception {
        if (WeaponRegistry.installed() == null) {
            WeaponRegistry weapons = new WeaponRegistry();
            for (String path : WeaponRegistry.BUILTIN_CATALOGS) {
                weapons.ingest(new JSONObject(Files.readString(modRoot.resolve(path))));
            }
            WeaponRegistry.install(weapons);
        }
        if (SpecialEquipmentRegistry.installed() == null) {
            SpecialEquipmentRegistry equipment = new SpecialEquipmentRegistry();
            for (String path : SpecialEquipmentRegistry.BUILTIN_CATALOGS) {
                equipment.ingest(new JSONObject(Files.readString(modRoot.resolve(path))));
            }
            equipment.validateReferences();
            SpecialEquipmentRegistry.install(equipment);
        }
    }

    static List<PreviewCase> previewCases() {
        return List.of(
                new PreviewCase("rocket-launcher", "Rocket launcher · Army-green line kit",
                        new FireTeamBillet("Rocketeer", MarineWeapon.PULSE_RIFLE,
                                EquipmentGrade.MILSPEC, MarineSecondary.ROCKET_LAUNCHER,
                                MarineArmorPattern.ARMY_GREEN)),
                new PreviewCase("anti-materiel-rifle", "Anti-materiel rifle · Navy scout kit",
                        new FireTeamBillet("Anti-armor", MarineWeapon.DMR,
                                EquipmentGrade.MASTERWORK, MarineSecondary.ANTI_MATERIEL_RIFLE,
                                MarineArmorPattern.BLUE_SCOUT)),
                new PreviewCase("smoke-grenades", "Smoke grenades · Charcoal screen kit",
                        new FireTeamBillet("Screen", MarineWeapon.SMG,
                                EquipmentGrade.SERVICE, MarineSecondary.SMOKE_GRENADE,
                                MarineArmorPattern.CHARCOAL)),
                new PreviewCase("satchel-charge", "Satchel charge · Outlaw breach kit",
                        new FireTeamBillet("Breacher", MarineWeapon.FIELD_RIFLE,
                                EquipmentGrade.SURPLUS, MarineSecondary.SATCHEL_CHARGE,
                                MarineArmorPattern.OUTLAW)));
    }

    public record PreviewCase(String slug, String label, FireTeamBillet billet) {}

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static final class RasterAssets implements ArmoryLoadoutPreviewComposer.Assets {
        private final Path modRoot;
        private final Map<String, BufferedImage> images = new LinkedHashMap<>();
        private final Map<String, LayeredSpriteCache> sprites = new LinkedHashMap<>();
        private final EnumMap<LayeredArmorFamily, LayeredUnitAssets> families =
                new EnumMap<>(LayeredArmorFamily.class);

        private RasterAssets(Path modRoot) {
            this.modRoot = modRoot;
        }

        @Override
        public LayeredUnitAssets layered(MarineArmorPattern armor) {
            LayeredArmorFamily family = ArmoryLoadoutPreviewComposer.armorFamily(armor);
            return families.computeIfAbsent(family, this::loadFamily);
        }

        @Override
        public LayeredSpriteCache icon(String path) {
            return path != null ? sprite(path) : null;
        }

        private LayeredUnitAssets loadFamily(LayeredArmorFamily family) {
            String armorRoot = MODULAR_ROOT + "armor/" + familyDirectory(family) + "/";
            LayeredUnitAssets loaded = new LayeredUnitAssets(
                    sprite(armorRoot + "body.png"), sprite(armorRoot + "head.png"),
                    sprite(FOOT), null,
                    sprite(MODULAR_ROOT + "weapons/rifle.png"),
                    sprite(MODULAR_ROOT + "weapons/laser-gun.png"),
                    sprite(MODULAR_ROOT + "weapons/smg.png"),
                    sprite(MODULAR_ROOT + "weapons/dmr.png"),
                    sprite(MODULAR_ROOT + "weapons/rocket-launcher.png"),
                    sprite(MODULAR_ROOT + "weapons/anti-materiel-rifle.png"),
                    sprite(FLASH),
                    sprite(MODULAR_ROOT + "weapons/grades/surplus/rifle.png"),
                    sprite(MODULAR_ROOT + "weapons/grades/masterwork/dmr.png"));
            for (SpecialEquipmentDef def : SpecialEquipmentRegistry.installed().all()) {
                EquipmentLayerDef carrier = def.presentation().carrierLayer();
                if (carrier != null) {
                    loaded.registerSpecialEquipment(def.id(), sprite(carrier.spritePath()));
                }
            }
            return loaded;
        }

        private LayeredSpriteCache sprite(String path) {
            return sprites.computeIfAbsent(path, key -> {
                try {
                    BufferedImage image = ImageIO.read(modRoot.resolve(key).toFile());
                    if (image == null) throw new IOException("Unsupported image " + key);
                    images.put(key, image);
                    return LayeredSpriteCache.headless(key, image.getWidth(), image.getHeight());
                } catch (IOException failure) {
                    throw new IllegalStateException("Could not load preview asset " + key, failure);
                }
            });
        }

        private static String familyDirectory(LayeredArmorFamily family) {
            return switch (family) {
                case ARMORLESS -> "armorless";
                case CHARCOAL -> "charcoal";
                case BLUE_SCOUT -> "blue-scout";
                case RED_ELITE -> "red-heavy";
                case OUTLAW -> "outlaw";
                case ARMY_GREEN -> "army-green";
                case MILITIA -> "militia";
                default -> throw new IllegalArgumentException(
                        "Campaign Armory preview does not support " + family);
            };
        }
    }

    private record RasterSink(Graphics2D graphics,
                              Map<String, BufferedImage> images,
                              float offsetX, float offsetY)
            implements ArmoryLoadoutPreviewComposer.Sink {
        @Override
        public void fillRect(float x, float y, float width, float height, Color color) {
            graphics.setColor(color);
            graphics.fill(new Rectangle2D.Float(offsetX + x, offsetY + y, width, height));
        }

        @Override
        public void strokeRect(float x, float y, float width, float height,
                               Color color, float strokeWidth) {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(strokeWidth));
            graphics.draw(new Rectangle2D.Float(offsetX + x, offsetY + y, width, height));
        }

        @Override
        public void line(float x1, float y1, float x2, float y2,
                         Color color, float strokeWidth) {
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(strokeWidth));
            graphics.drawLine(Math.round(offsetX + x1), Math.round(offsetY + y1),
                    Math.round(offsetX + x2), Math.round(offsetY + y2));
        }

        @Override
        public void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                           float width, float height, float angleDegrees, Color tint) {
            BufferedImage image = images.get(sprite.sourcePath);
            if (image == null) {
                throw new IllegalStateException("No raster image for " + sprite.sourcePath);
            }
            AffineTransform transform = graphics.getTransform();
            var composite = graphics.getComposite();
            graphics.translate(offsetX + centerX, offsetY + centerY);
            graphics.rotate(Math.toRadians(-angleDegrees));
            graphics.scale(width / image.getWidth(), height / image.getHeight());
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    tint.getAlpha() / 255f));
            graphics.drawImage(image, -image.getWidth() / 2, -image.getHeight() / 2, null);
            graphics.setComposite(composite);
            graphics.setTransform(transform);
        }
    }
}
