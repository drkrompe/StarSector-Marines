package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Turns a {@link SquadArmorPlan} into the twelve patterns a squad actually
 * wears, by applying somebody's stock to it ({@code role-and-access.md}).
 *
 * <p>This is where the two axes meet, and it is the only place they do. The
 * plan supplies the role of each billet and the tradition to prefer; the caller
 * supplies what is available. Nothing here reads a tier directly: access is
 * expressed entirely as "which patterns may I use", so a company's ceiling, a
 * player's unlocked cards, and a test's hand-picked set all resolve through one
 * rule.
 *
 * <p><b>Resolution is stable.</b> Candidates are ordered by tier and then by id,
 * so the same plan against the same stock always issues the same suits — a
 * squad's appearance must not shuffle between two runs of the same battle.
 */
public final class ArmorIssueResolver {

    private ArmorIssueResolver() {}

    /**
     * Issues {@code plan} from the patterns {@code available} accepts.
     *
     * <p>For each billet: the best available pattern of that billet's role,
     * preferring the plan's own tradition; failing that, the best available of
     * that role from anyone. A role nobody available fills falls back to the
     * best available line kit and finally to whatever is available at all,
     * because a marine with no suit is worse than a marine in the wrong one —
     * and because a company that has genuinely never bought a recon suit should
     * still be able to field its sections.
     */
    public static SquadArmorDoctrine resolve(SquadArmorPlan plan,
                                             Predicate<MarineArmorCatalogDef> available) {
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        if (catalog == null) {
            throw new IllegalStateException("The armour catalog is not installed");
        }
        List<MarineArmorCatalogDef> stock = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : catalog.all()) {
            if (available == null || available.test(pattern)) stock.add(pattern);
        }
        stock.sort(Comparator.comparingInt(MarineArmorCatalogDef::tier).reversed()
                .thenComparing(MarineArmorCatalogDef::id));
        if (stock.isEmpty()) {
            throw new IllegalStateException("Armour plan '" + plan.id()
                    + "' cannot be issued: nothing at all is available");
        }

        List<String> issued = new ArrayList<>(MarineSquad.CAPACITY);
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            issued.add(bestFor(plan, plan.roleAt(billet), stock).id());
        }
        return SquadArmorDoctrine.fromIds(plan.id(), plan.displayName(),
                plan.description(), issued);
    }

    /** Convenience for "everything the catalog has", used by previews and tests. */
    public static SquadArmorDoctrine resolveUnrestricted(SquadArmorPlan plan) {
        return resolve(plan, pattern -> true);
    }

    private static MarineArmorCatalogDef bestFor(SquadArmorPlan plan, ArmorRole role,
                                                 List<MarineArmorCatalogDef> stock) {
        MarineArmorCatalogDef ownTradition = first(stock,
                pattern -> pattern.role() == role && pattern.tradition() == plan.tradition());
        if (ownTradition != null) return ownTradition;
        MarineArmorCatalogDef anyTradition = first(stock, pattern -> pattern.role() == role);
        if (anyTradition != null) return anyTradition;
        MarineArmorCatalogDef line = first(stock, pattern -> pattern.role() == ArmorRole.LINE);
        return line != null ? line : stock.get(0);
    }

    private static MarineArmorCatalogDef first(List<MarineArmorCatalogDef> stock,
                                               Predicate<MarineArmorCatalogDef> match) {
        for (MarineArmorCatalogDef pattern : stock) {
            if (match.test(pattern)) return pattern;
        }
        return null;
    }
}
