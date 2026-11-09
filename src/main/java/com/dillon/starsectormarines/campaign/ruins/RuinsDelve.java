package com.dillon.starsectormarines.campaign.ruins;

/**
 * What a ruins grade makes of the expedition ({@code ruins-expedition.md}, The grade sets the delve): how many layers
 * the route crosses, how long the vault takes to breach with and without its seal powered, and how many field caches
 * the ground offers. Larger ruins are a longer, harder delve for a larger haul. The numbers are starting points, tuned
 * against a player giving orders.
 *
 * @param layers               bands of ruined ground between the landing and the vault
 * @param breachSeconds        the vault's breach with its seal unpowered
 * @param poweredBreachSeconds the vault's breach once power is routed to its seal
 * @param fieldCaches          optional finds on the ground
 */
public record RuinsDelve(int layers, float breachSeconds, float poweredBreachSeconds, int fieldCaches) {

    public RuinsDelve {
        if (layers < 1 || fieldCaches < 0 || poweredBreachSeconds <= 0f || breachSeconds < poweredBreachSeconds) {
            throw new IllegalArgumentException("a delve has a layer and a breach no faster unpowered");
        }
    }

    public static RuinsDelve of(RuinsGrade grade) {
        if (grade == null) throw new IllegalArgumentException("a delve needs a grade");
        return switch (grade) {
            case SCATTERED -> new RuinsDelve(1, 30f, 10f, 2);
            case WIDESPREAD -> new RuinsDelve(2, 45f, 15f, 3);
            case EXTENSIVE -> new RuinsDelve(3, 60f, 20f, 4);
            case VAST -> new RuinsDelve(4, 90f, 30f, 5);
        };
    }
}
