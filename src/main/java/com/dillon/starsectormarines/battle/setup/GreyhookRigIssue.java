package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import java.util.Objects;

/**
 * Berth Four's crude powered rigs in Terms of Protection: Greyhook's own rebuilt Boarding Jacks,
 * worn by individual raiders inside mixed squads. Every combat value is the catalog pattern's —
 * capacity, rating, handling, incoming accuracy and the surplus Latch Assist — so the rig the
 * company meets is the rig it is later paid with, not a stronger enemy copy.
 *
 * <p>Enemy use needs no award gate, but it needs something drawable. The rig draws its own
 * {@code BOARDING_JACK} family. Should the pattern ever declare its presentation pending again,
 * the rig falls back to the {@link #STAND_IN} shell — the registration the Boarding Jack's own
 * layers are cut to — rather than the legacy sheet, which draws a powered rig as one more
 * rifleman. The stand-in is presentation only; it changes no number.
 */
public final class GreyhookRigIssue {
    public static final String PATTERN = "armor.boarding-jack";
    /** Powered shell the pending pattern is drawn on; its layers share the Boarding Jack's registration. */
    public static final LayeredArmorFamily STAND_IN = LayeredArmorFamily.SPECTER_HEAVY;

    private GreyhookRigIssue() {}

    public static EntitySpec boardingJack(EntitySpec member, MarineArmorCatalogDef armor) {
        Objects.requireNonNull(armor, "Boarding Jack catalog must be installed");
        if (!PATTERN.equals(armor.id())) throw new IllegalArgumentException("Berth Four's rigs are the Boarding Jack pattern");
        return member.armorClass(armor.armorClass()).layeredArmorFamily(drawnFamily(armor))
                .armor(armor.armorCapacity(), armor.armorRating(), armor.moveSpeedMult(), armor.incomingAccuracyMult())
                .integralSystem(armor.integralSystem());
    }

    /** The family the rig is drawn with: its own once its art is declared, the shared shell until then. */
    public static LayeredArmorFamily drawnFamily(MarineArmorCatalogDef armor) {
        return armor.presentationDeclared() ? armor.appearanceFamily() : STAND_IN;
    }
}
