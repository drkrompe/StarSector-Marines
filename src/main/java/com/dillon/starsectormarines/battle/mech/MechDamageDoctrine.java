package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

/**
 * What section loss does to a mech's doctrine ({@code mechs-nouns.md}, Section loss).
 * Pure decisions over plain inputs; the dispatchers that act on them are
 * {@link ExecuteMechDoctrine} and {@link GoapMechBehavior}'s goal choice.
 *
 * <ul>
 *   <li>A <b>crippled</b> mech limps: it walks on at a crawl ({@link MechSectionModifier}) and fights
 *       under its doctrine and orders as before, but nothing keys its movement to it any more.</li>
 *   <li>A <b>disarmed</b> mech (every mount lost) declines to attack and withdraws, crippled or not.</li>
 *   <li>A lost mount needs no posture of its own: every doctrine reading reads the live mounts,
 *       so the lost one simply is not there to reach for.</li>
 * </ul>
 */
public final class MechDamageDoctrine {

    private MechDamageDoctrine() {}

    /** How a mech serves its order given what section loss has left it. */
    public enum Posture {
        /** Legs up and armed: its doctrine applies unchanged. */
        FIGHT,
        /** Crippled and armed: its doctrine applies, at a crawl. */
        LIMPING,
        /** Disarmed: declines to attack and breaks contact, at whatever pace its legs allow. */
        WITHDRAW;

        /** Whether this posture still engages targets. */
        public boolean attacks() {
            return this == FIGHT || this == LIMPING;
        }

        /**
         * Whether the rest of its lance, or infantry advancing behind it, may key their movement
         * to this mech: a cohesion anchor, a formation leader, a screen to advance behind. Only a
         * mech still fighting on its legs is one; anything else would hold its lance to a body
         * that cannot or will not go where the lance is going.
         */
        public boolean anchorsOthers() {
            return this == FIGHT;
        }
    }

    /** The posture for a mech that is or is not crippled and does or does not have a weapon left. */
    public static Posture posture(boolean crippled, boolean armed) {
        if (!armed) return Posture.WITHDRAW;
        return crippled ? Posture.LIMPING : Posture.FIGHT;
    }

    /** Whether any mount still carries a weapon: lost and never-fitted mounts are both null. */
    public static boolean armed(MechWeaponMount[] mounts) {
        if (mounts == null) return false;
        for (MechWeaponMount mount : mounts) {
            if (mount != null) return true;
        }
        return false;
    }

    /** Whether the legs have gone; a mech nobody has hit has no sections and is whole. */
    public static boolean crippled(MechSections sections) {
        return sections != null && sections.crippled();
    }

    /** {@link #posture(boolean, boolean)} for a live loadout; a non-mech is {@link Posture#FIGHT}. */
    public static Posture postureOf(MechLoadoutComponent loadout) {
        if (loadout == null) return Posture.FIGHT;
        return posture(crippled(loadout.sections()), armed(loadout.mounts()));
    }

    /**
     * Whether a lance declines its attack goals: it has standing mechs and not one of them still
     * carries a weapon. A lance with any armed member keeps them; its disarmed members decline
     * individually through their posture.
     */
    public static boolean lanceDeclinesAttack(int standingMechs, int armedMechs) {
        return standingMechs > 0 && armedMechs == 0;
    }
}
