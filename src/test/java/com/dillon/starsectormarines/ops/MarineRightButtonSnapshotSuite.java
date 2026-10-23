package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.combat.FireTrigger;
import com.dillon.starsectormarines.battle.control.ManualIntent;
import com.dillon.starsectormarines.battle.control.MarineRightTrigger;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.ops.AimReticleSnapshotSuite.Aim;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import java.util.ArrayList;
import java.util.List;

import static com.dillon.starsectormarines.ops.AimReticleSnapshotSuite.require;

/**
 * A Marine's right button (`direct-control-nouns.md`, Mouse buttons and weapon groups): one Marine carrying smoke and
 * one carrying frag grenades, both in a shield suit so E has something to cycle to. Each is shown with its throw
 * picked, the frag also past its reach (a red, dotted tick bloomed to the burst), then mid-throw in the committed
 * channel, and the frag once more with the grenade in the air. Frames come from {@link AimReticleSnapshotSuite}'s
 * renderer: production marks, strokes and HUD. A look study, so it is left out of {@code all}.
 */
public final class MarineRightButtonSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "marine-right-button"; }
    @Override public String label() { return "Direct-control Marine right button: smoke, grenade, shield"; }
    @Override public boolean inAll() { return false; }

    /** A suit whose integral system is a directional shield. */
    private static final String SHIELD_SUIT = "armor.foundry-breaker";
    /** East, the sim's facing convention (0 is up the screen). */
    private static final float EAST = -90f;

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install(); InstalledVanillaData.install();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        DiskRegistries.installMapGeneration(context.projectRoot());
        SettingsAPI previous = Global.getSettings();
        Global.setSettings(AimReticleSnapshotSuite.settings(context, previous));
        List<SnapshotArtifact> frames = new ArrayList<>();
        try {
            IntegralSystemDef shield = MarineArmorCatalogRegistry.require(SHIELD_SUIT).integralSystem();
            require(shield != null && shield.grantsMitigation(), SHIELD_SUIT + " carries a shield");
            var world = SceneBuilder.openGround(120, 90).missionCompletion(false)
                    .squad("smoke").size(2).primary("weapon.field-rifle").special("special.smoke-grenade", 2, 2)
                            .each(spec -> spec.integralSystem(shield)).inFile(40, 30, 3, 0).done()
                    .squad("frag").size(2).primary("weapon.field-rifle").special("special.frag-grenade", 2, 3)
                            .each(spec -> spec.integralSystem(shield)).inFile(40, 60, 3, 0).done()
                    .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 116, 87).build();
            try (BattleSimulation sim = world.sim()) {
                for (int y = 0; y < 90; y++) for (int x = 0; x < 120; x++) {
                    sim.getTopology().setGroundKind(x, y, GroundKind.DIRT);
                }
                for (int tick = 0; tick < 30; tick++) sim.advance(BattleSimulation.TICK_DT);
                Selection selection = new Selection();
                int index = 1;

                long smoker = world.members("smoke")[0];
                selection.selectUnit(world.squadId("smoke"), smoker);
                require(sim.directControl().enter(smoker), "The smoke Marine can take control");
                require(sim.directControl().marineRightPick() == MarineRightTrigger.Item.SMOKE, "smoke is the first pick");
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, smoker,
                        new Aim("smoke picked", 6f, EAST), name(index++, "smoke-picked")));
                throwAndHold(sim, smoker, 6f, .4f);
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, smoker,
                        new Aim("smoke mid-throw", 6f, EAST), name(index++, "smoke-mid-throw")));
                finishChannel(sim, smoker);
                require(sim.directControl().cycle(FireTrigger.RIGHT), "E moves smoke to the shield");
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, smoker,
                        new Aim("shield picked (E)", 6f, EAST), name(index++, "smoke-marine-shield-picked")));
                sim.directControl().exit();

                long grenadier = world.members("frag")[0];
                selection.selectUnit(world.squadId("frag"), grenadier);
                require(sim.directControl().enter(grenadier), "The grenadier can take control");
                require(sim.directControl().marineRightPick() == MarineRightTrigger.Item.GRENADE, "the frag is the first pick");
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, grenadier,
                        new Aim("frag picked", 6f, EAST), name(index++, "frag-picked")));
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, grenadier,
                        new Aim("frag past reach", 13f, EAST), name(index++, "frag-past-reach")));
                throwAndHold(sim, grenadier, 6f, .4f);
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, grenadier,
                        new Aim("frag mid-throw", 6f, EAST), name(index++, "frag-mid-throw")));
                advanceFraction(sim, grenadier, .25f);
                require(sim.directControl().manualThrowActive(), "the follow-through after the release keeps control");
                frames.add(AimReticleSnapshotSuite.frame(context, sim, selection, grenadier,
                        new Aim("frag released", 6f, EAST), name(index, "frag-released")));
                sim.directControl().exit();
            }
        } finally { Global.setSettings(previous); }
        return frames;
    }

    private static String name(int index, String what) { return String.format("%02d-%s", index, what); }

    /** Presses the right button at a cursor {@code cells} east, then holds still until {@code fraction} of the channel has run. */
    private static void throwAndHold(BattleSimulation sim, long id, float cells, float fraction) {
        float x = sim.world().renderX(id) + cells;
        float y = sim.world().renderY(id);
        sim.directControl().submit(new ManualIntent(0f, 0f, x, y, false, false, true));
        sim.advance(BattleSimulation.TICK_DT);
        sim.directControl().submit(new ManualIntent(0f, 0f, x, y, false));
        require(sim.world().secondaryActionTimer(id) > 0f, "the press started the committed throw");
        advanceFraction(sim, id, fraction - BattleSimulation.TICK_DT / channel(sim, id));
    }

    private static void advanceFraction(BattleSimulation sim, long id, float fraction) {
        int ticks = Math.round(fraction * channel(sim, id) / BattleSimulation.TICK_DT);
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static void finishChannel(BattleSimulation sim, long id) {
        for (int i = 0; i < 120 && sim.world().secondaryActionTimer(id) > 0f; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static float channel(BattleSimulation sim, long id) { return sim.world().specialEquipment(id).aimDuration(); }
}
