package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;

/**
 * Draws a layered mech's parts and the worn plate on each ({@code combat-durability-nouns.md}): every part sprite goes
 * to the draw list as {@link LayeredMechComposer} emits it, followed at once by the marks of the section it belongs to,
 * laid in that part's own frame. The torso, hips and arms turn independently, so a mark rides the part it is on, and a
 * part drawn over another covers that one's marks as it covers its paint.
 *
 * <p>A section's wear is mostly its armour and partly its structure, so a stripped section is battered and a
 * section losing structure goes on to its last gouges. The torso is split as a hull is: its rear quarter wears with
 * the rear armour, the rest with the front. Reused for every mech the layer draws.
 */
final class MechScuffSink implements LayeredMechComposer.Sink {

    /**
     * Marks a fully worn torso quarter shows, and a fully worn other part: a mech's parts are small beside a hull's
     * plates, and a tank plate's nine crowd them.
     */
    static final int TORSO_SLOTS = 4;
    static final int PART_SLOTS = 5;
    /** The share of a section's wear that is its armour; the rest is its structure. */
    static final float ARMOR_SHARE = 0.75f;

    private final BattleSprites sprites;
    private final ScuffMark mark = new ScuffMark();
    private DrawList out;
    private MechSections sections;
    private long seed;
    private MechSection section;
    private int part;

    /**
     * Builds the masks of every part of {@code assets} that can carry marks, so no frame reads a part's pixels the
     * first time it is drawn damaged: the chassis split into plates as the torso, the arms, pods and foot whole.
     * Leg bones and muzzle flashes carry none. Called where the battle's sheets are registered.
     */
    static void prepare(BattleSprites sprites, LayeredMechAssets assets) {
        if (assets == null) return;
        for (LayeredSpriteCache torso : new LayeredSpriteCache[] {assets.chassis, assets.socketedChassis,
                assets.houndChassis, assets.wardenChassis}) {
            prepare(sprites, torso, true);
        }
        for (LayeredSpriteCache part : new LayeredSpriteCache[] {assets.foot, assets.chaingunArm,
                assets.hc10, assets.hc20, assets.srmPod, assets.lrmPod, assets.ll3,
                assets.pl2Ap, assets.ac6, assets.dc5, assets.thermalLance,
                assets.ac6Qs, assets.dc4Qs, assets.srm3QsPod,
                assets.lightMachineGun, assets.bl1,
                assets.pl3, assets.bl2, assets.pl3Sweep, assets.pl2,
                assets.pl1}) {
            prepare(sprites, part, false);
        }
    }

    private static void prepare(BattleSprites sprites, LayeredSpriteCache part, boolean torso) {
        if (part == null || part.pxWidth <= 0 || part.pxHeight <= 0) return;
        VehicleScuffs.partMask(sprites, part.sourcePath, part.pxWidth, part.pxHeight, torso);
    }

    MechScuffSink(BattleSprites sprites) {
        this.sprites = sprites;
    }

    /** Aims the sink at one mech: its parts go to {@code out}, its marks from {@code sections} (null for none). */
    MechScuffSink begin(DrawList out, MechSections sections, long entityId) {
        this.out = out;
        this.sections = sections;
        this.seed = entityId;
        this.section = null;
        return this;
    }

    @Override
    public void section(MechSection section) {
        this.section = section;
        this.part = 0;
    }

    @Override
    public void sprite(LayeredSpriteCache sprite, float centerX, float centerY, float width, float height,
                       float angleDegrees, float alpha) {
        out.addSprite(RenderLayer.UNITS, sprite.sprite, centerX, centerY, width, height, angleDegrees,
                1f, 1f, 1f, alpha);
        int index = part++;
        if (section == null || sections == null || !sections.fitted(section) || sections.destroyed(section)) return;
        if (sprite.pxWidth <= 0 || sprite.pxHeight <= 0) return;
        float structureWear = VehicleScuffs.wear(sections.structure(section), sections.maxStructure(section));
        float frontWear = wear(VehicleScuffs.wear(sections.armor(section), sections.maxArmor(section)), structureWear);
        boolean torso = section == MechSection.TORSO;
        float rearWear = torso ? wear(VehicleScuffs.wear(sections.rearArmor(), sections.maxRearArmor()), structureWear)
                : frontWear;
        if (frontWear <= 0f && rearWear <= 0f) return;
        VehicleScuffs.Mask mask = VehicleScuffs.partMask(sprites, sprite.sourcePath, sprite.pxWidth, sprite.pxHeight,
                torso);
        if (mask == null) return;
        float sx = width / sprite.pxWidth;
        float sy = height / sprite.pxHeight;
        // The smaller scale keeps a mark inside its inset on a part drawn squashed (a narrow arm barrel).
        mark.begin(out, RenderLayer.UNITS, sprites, centerX, centerY, sprite.pxWidth, sprite.pxHeight, sx, sy,
                angleDegrees, mask.decalPx * Math.min(sx, sy), alpha);
        long partSeed = seed ^ (section.ordinal() + 1L) * 0x632BE59BD9B4E019L ^ (index + 1L) * 0x9E3779B97F4A7C15L;
        if (torso) {
            for (HullLocations.Plate plate : HullLocations.Plate.values()) {
                float w = plate == HullLocations.Plate.REAR ? rearWear : frontWear;
                if (w > 0f) VehicleScuffs.place(mask, plate.ordinal(), TORSO_SLOTS, w, partSeed, mark);
            }
        } else {
            VehicleScuffs.place(mask, 0, PART_SLOTS, frontWear, partSeed, mark);
        }
        mark.out = null;
    }

    /** A section's wear from its armour's and its structure's. */
    static float wear(float armorWear, float structureWear) {
        return ARMOR_SHARE * armorWear + (1f - ARMOR_SHARE) * structureWear;
    }
}
