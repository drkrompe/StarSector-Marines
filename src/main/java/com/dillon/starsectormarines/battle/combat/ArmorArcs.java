package com.dillon.starsectormarines.battle.combat;

/**
 * Facing-dependent armor: which arc of a machine a hit arrives on, and what the plate on that arc is worth.
 *
 * <p>An arc is read from the bearing of the hit's origin against the facing of the part it lands on, in the battle's
 * facing convention (0° = +Y, positive counter-clockwise, so forward is {@code (-sin, cos)} and the machine's right
 * is {@code (cos, sin)}; the frame {@code MechSections.fromLeft} reads arm hits in). The front arc spans 45° either
 * side of dead ahead, the rear 45° either side of dead astern, and each side the ninety degrees between them. A hit
 * from the body's own position meets the side; a hit with no origin at all is the caller's to place (a ground
 * hull's meets its nominal plate, {@link Profile#FLAT}).
 *
 * <p>A {@link Profile} gives each arc two numbers. The <b>rating</b> multiplies the body's armor rating, which is
 * what a round's penetration is read against ({@link DurabilityModel#armorEfficiency}); it only bites on rounds
 * that do not already out-penetrate the plate. The <b>plate</b> is how much each point of the body's one armor pool
 * counts for on that arc ({@link DurabilityModel#resolvePlatedInto}): thick, sloped front plate takes longer to
 * strip, thin rear plate less, whatever the round. The pool itself stays one column, so whole-body readers still
 * see one machine.
 */
public final class ArmorArcs {

    private ArmorArcs() {
    }

    /** The three arcs. */
    public enum Arc { FRONT, SIDE, REAR }

    /** One body's arcs: the rating and plate multipliers on each. */
    public record Profile(float frontRating, float frontPlate,
                          float sideRating, float sidePlate,
                          float rearRating, float rearPlate) {

        /** Every arc the same plate: the control, and every body that authors none. */
        public static final Profile FLAT = new Profile(1f, 1f, 1f, 1f, 1f, 1f);

        public Profile {
            float[] all = {frontRating, frontPlate, sideRating, sidePlate, rearRating, rearPlate};
            for (float v : all) {
                if (!(v > 0f) || !Float.isFinite(v)) throw new IllegalArgumentException("arc multipliers must be positive");
            }
        }

        public float rating(Arc arc) {
            return switch (arc) {
                case FRONT -> frontRating;
                case SIDE -> sideRating;
                case REAR -> rearRating;
            };
        }

        public float plate(Arc arc) {
            return switch (arc) {
                case FRONT -> frontPlate;
                case SIDE -> sidePlate;
                case REAR -> rearPlate;
            };
        }
    }

    /**
     * The arc a hit from {@code (dx, dy)} away (origin minus body) arrives on, for a part facing
     * {@code facingDegrees}. A zero offset carries no bearing and meets the side.
     */
    public static Arc classify(float facingDegrees, float dx, float dy) {
        if (dx == 0f && dy == 0f) return Arc.SIDE;
        double radians = Math.toRadians(facingDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double forward = -dx * sin + dy * cos;
        double lateral = Math.abs(dx * cos + dy * sin);
        if (forward >= lateral) return Arc.FRONT;
        if (-forward >= lateral) return Arc.REAR;
        return Arc.SIDE;
    }
}
