package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Headless assets for a weapon's tooltip scene: the Armory preview's marine and mod art, plus the
 * base game's effect and projectile textures a weapon may name, read from the game's core folder.
 */
public final class HeadlessWeaponPreviewAssets implements ArmoryLoadoutPreviewComposer.Assets {

    private final ArmoryLoadoutPreviewComposer.Assets armory;
    private final Path modRoot;
    private final Path coreRoot;
    private final Map<String, LayeredSpriteCache> core = new HashMap<>();

    public HeadlessWeaponPreviewAssets(Path modRoot, Path coreRoot) {
        this.armory = new HeadlessArmoryPreviewRenderer(modRoot).assets();
        this.modRoot = modRoot;
        this.coreRoot = coreRoot;
    }

    @Override
    public LayeredUnitAssets layered(LayeredArmorFamily armor) {
        return armory.layered(armor);
    }

    @Override
    public LayeredSpriteCache icon(String path) {
        if (path == null) return null;
        if (Files.exists(modRoot.resolve(path))) return armory.icon(path);
        return core.computeIfAbsent(path, key -> {
            Path file = coreRoot.resolve(key);
            if (!Files.exists(file)) return null;
            try {
                BufferedImage image = ImageIO.read(file.toFile());
                return image == null ? null : LayeredSpriteCache.headless(key, image.getWidth(), image.getHeight());
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read " + key, failure);
            }
        });
    }

    @Override
    public UnitLayerLayouts unitLayerLayouts() {
        return armory.unitLayerLayouts();
    }
}
