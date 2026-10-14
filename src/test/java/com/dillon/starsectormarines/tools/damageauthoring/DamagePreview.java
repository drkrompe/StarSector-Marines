package com.dillon.starsectormarines.tools.damageauthoring;

import com.dillon.starsectormarines.battle.world.gen.ReliefCheckpoint;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.damage.DamageCatalog;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Set;

/** Fresh production recipe for every revision: hiding damage cannot accumulate mutations. */
public final class DamagePreview {
    private final HeadlessUiRenderer renderer;
    public DamagePreview(Path root) {
        renderer = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(root.resolve("mod")), root.resolve("mod"));
    }
    public static MapResult prepare(DamagePlan plan, long seed, boolean intact) {
        DamageCatalog.validate(plan);
        TargetProfile profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        return ReliefCheckpoint.generate(plan.width(), plan.height(), seed, profile).map().withInitialDamage(intact ? null : plan);
    }
    public BufferedImage render(DamagePlan plan, boolean intact) {
        try (var scene = new ShipDeckBattleScene(prepare(plan, 8101, intact), 0)) {
            return renderer.renderHostPass(scene.pass(ShipDeckBattleScene.DeckView.fitting(0, 0, plan.width(), plan.height(), 1920, 1080)), 1920, 1080);
        }
    }
}
