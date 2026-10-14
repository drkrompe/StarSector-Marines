package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.EquipmentLayerDef;

/**
 * The kit a squad medic kneels over a patient with ({@code squad-medic.md}).
 *
 * <p>Not a special: a Field Medicine medic keeps whatever special they were
 * issued, so the kit is drawn in place of it for as long as the treatment
 * lasts and nowhere else. The kneel is the satchel's planting clip, but the kit
 * is placed from this definition rather than the clip's hand transform, which is
 * authored for satchel art: open on the ground in front of the medic, toward
 * the patient, under the body so the kneeling figure is over it.
 */
final class TreatmentKitLayer {

    /** The key the kit sprite is registered under beside the specials. */
    static final String ASSET_KEY = "medic.treatment-kit";

    static final String SPRITE = "graphics/ui/armory/system-field-aid.png";

    static final EquipmentLayerDef DEF = new EquipmentLayerDef(SPRITE, 0.75f, 0.75f, 0.5f, 0.5f,
            new EquipmentLayerDef.State(0f, 0f, 0f, EquipmentLayerDef.Occlusion.UNDER_BODY),
            new EquipmentLayerDef.State(0f, 0.55f, 0f, EquipmentLayerDef.Occlusion.UNDER_BODY),
            0f, EquipmentLayerDef.Occlusion.OVER_BODY, false, true);

    private TreatmentKitLayer() {}
}
