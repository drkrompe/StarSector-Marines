package com.dillon.starsectormarines.ops.battleview;

/**
 * Worn chitin on a living Rav ({@code colony-breach-nouns.md}, Matriarch and Brute; {@code combat-durability-nouns.md},
 * Worn plate): the organic counterpart of a hull's scuffs. As its plate is stripped the shell is scored, chipped and
 * then bruised dark and cracked; as its health goes it is torn open in weeping wounds over the sinew. Like worn plate
 * it observes and never records: which marks a body shows is a pure function of its current plate and health, laid by
 * {@link VehicleScuffs#place}'s law (slots hashed from the body, the part and the slot, each showing once its channel's
 * wear passes its threshold and never moving after), so marks build up in place and a review frame draws what the
 * screen does.
 *
 * <p>Two channels share one part's mask. The <b>shell</b> channel reads the plate: its first slots are scores and chips,
 * its later ones bruises. The <b>flesh</b> channel reads health: every slot is a wound. A body with no plate left still
 * shows the shell it had stripped, and a moult keeps both fractions (the Matriarch's stage scales her plate and health
 * together), so her marks grow with her rather than clearing.
 */
final class RavWear {

    /**
     * The wear sheet: one {@link #SHEET_CELL} px cell per mark, a row per stage (score, bruise, wound) and a column per
     * variant ({@code art-source/rav-wear/}).
     */
    static final String SHEET = "graphics/battle/swarm/rav-wear.png";
    static final int SHEET_CELL = 128;

    /** The stages, which are the wear sheet's rows. */
    static final int SCORE = 0;
    static final int BRUISE = 1;
    static final int WOUND = 2;

    /** Where a shell slot turns from a score to a bruise, as a share of the channel's slots (taken at the slot's middle). */
    static final float BRUISE_FROM = 0.55f;
    /**
     * A mark's side as a share of its part's narrower extent. Finer than a hull's, so the deep middle of the back is not
     * the only place a mark keeps its reach and the flanks wear too.
     */
    static final float DECAL_SHARE = 0.19f;

    /** Salts keeping the two channels' slots apart on one part. */
    private static final long SHELL_SALT = 0x51A7E5L;
    private static final long FLESH_SALT = 0xF1E5B0L;

    private RavWear() {}

    /** How many marks a part shows when fully worn, by channel. */
    record Slots(int shell, int flesh) {}

    /** The Matriarch's body, her head; the Brute's two halves and its head. */
    static final Slots MATRIARCH_BODY = new Slots(22, 14);
    static final Slots MATRIARCH_HEAD = new Slots(5, 3);
    static final Slots BRUTE_HALF = new Slots(6, 4);
    static final Slots BRUTE_HEAD = new Slots(2, 2);

    /** Wear of the shell: the share of {@code maxArmor} gone, 0 for a body that never had plate. */
    static float shellWear(float armor, float maxArmor) {
        return VehicleScuffs.wear(armor, maxArmor);
    }

    /** Wear of the flesh: the share of {@code maxHp} gone. */
    static float fleshWear(float hp, float maxHp) {
        return maxHp > 0f ? Math.max(0f, Math.min(1f, 1f - hp / maxHp)) : 0f;
    }

    /**
     * Hands {@code sink} the marks one part shows: the shell channel at {@code shellWear}, then the flesh channel at
     * {@code fleshWear}, so wounds lie over the bruised shell. {@code seed} is the body's and the part's. Returns how
     * many were handed over.
     */
    static int place(VehicleScuffs.Mask mask, Slots slots, float shellWear, float fleshWear, long seed,
                     VehicleScuffs.Sink sink) {
        int placed = 0;
        if (shellWear > 0f && slots.shell() > 0) {
            placed += VehicleScuffs.place(mask, 0, slots.shell(), shellWear, seed ^ SHELL_SALT, sink,
                    RavWear::shellStage);
        }
        if (fleshWear > 0f && slots.flesh() > 0) {
            placed += VehicleScuffs.place(mask, 0, slots.flesh(), fleshWear, seed ^ FLESH_SALT, sink,
                    stage -> WOUND);
        }
        return placed;
    }

    /** A shell slot's stage from its place in the channel: scores and chips first, bruises after. */
    static int shellStage(float stage) {
        return stage < BRUISE_FROM ? SCORE : BRUISE;
    }

    /**
     * Called by a Rav composer right after it draws a part that wears ({@link #MATRIARCH_BODY} ...), so the marks lie on
     * that part, turn with it and are covered by whatever is drawn over it.
     */
    interface Marks {
        void part(LayeredSpriteCache part, Slots slots, int index, float centerX, float centerY, float width,
                  float height, float angleDegrees, float alpha);
    }

    /** Lays one body's marks into a draw list from the wear sheet. Reused for every Rav the layer draws. */
    static final class Painter implements Marks {

        private final BattleSprites sprites;
        private final ScuffMark mark = new ScuffMark(SHEET, SHEET_CELL);
        private DrawList out;
        private long seed;
        private float shell;
        private float flesh;

        Painter(BattleSprites sprites) {
            this.sprites = sprites;
        }

        /**
         * Aims at one body: its marks go to {@code out}, at its shell and flesh wear. Returns null for a body showing
         * no wear, so its composer draws it bare.
         */
        Marks begin(DrawList out, long entityId, float shellWear, float fleshWear) {
            if (shellWear <= 0f && fleshWear <= 0f) return null;
            this.out = out;
            this.seed = entityId;
            this.shell = shellWear;
            this.flesh = fleshWear;
            return this;
        }

        @Override
        public void part(LayeredSpriteCache part, Slots slots, int index, float centerX, float centerY, float width,
                         float height, float angleDegrees, float alpha) {
            if (part == null || part.pxWidth <= 0 || part.pxHeight <= 0) return;
            VehicleScuffs.Mask mask = VehicleScuffs.partMask(sprites, part.sourcePath, part.pxWidth, part.pxHeight,
                    false, DECAL_SHARE);
            if (mask == null) return;
            float sx = width / part.pxWidth;
            float sy = height / part.pxHeight;
            mark.begin(out, RenderLayer.UNITS, sprites, centerX, centerY, part.pxWidth, part.pxHeight, sx, sy,
                    angleDegrees, mask.decalPx * Math.min(sx, sy), alpha);
            place(mask, slots, shell, flesh, seed ^ (index + 1L) * 0x632BE59BD9B4E019L, mark);
            mark.out = null;
        }

        /** Reads each wearable part's pixels now, so no frame pays for it on a body's first hurt frame. */
        static void prepare(BattleSprites sprites, LayeredSpriteCache... parts) {
            for (LayeredSpriteCache part : parts) {
                if (part == null || part.pxWidth <= 0 || part.pxHeight <= 0) continue;
                VehicleScuffs.partMask(sprites, part.sourcePath, part.pxWidth, part.pxHeight, false, DECAL_SHARE);
            }
        }
    }
}
