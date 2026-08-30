package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <p><b>Resolution is stable.</b> Candidates are ordered by what they are worth
 * and then by id, so the same plan against the same stock always issues the same
 * suits — a squad's appearance must not shuffle between two runs of the same
 * battle.
 */
public final class ArmorIssueResolver {

    private ArmorIssueResolver() {}

    /**
     * Issues {@code plan} from the patterns {@code available} accepts.
     *
     * <p>For each billet: the best available pattern of that billet's role,
     * preferring the plan's own tradition; failing that, the best available of
     * that role from anyone. A role nobody available fills falls back to line
     * kit — the plan's own tradition again first — and finally to whatever is
     * available at all, because a marine with no suit is worse than a marine in
     * the wrong one, and because a company that has genuinely never bought a
     * recon suit should still be able to field its sections.
     *
     * <p><b>A role with several billets does not put all of them in the same
     * suit.</b> Where the top band for a role holds more than one pattern they
     * are dealt across that role's billets, so a section with two weapons
     * carriers offered a missile pod and a corpsman's satchel fields one of
     * each. Issuing only the single best pattern is what made the corpsman
     * unwearable at any company that owned the arbalest — same tier, same role,
     * same tradition, and the alphabet decided which capability existed. It is
     * also what made a contributed pattern unreachable the moment a core one
     * occupied its cell, which is a submod contract that add-only ids cannot fix
     * from the outside ({@code submod-catalog-contract.md}).
     *
     * <p>Only the top band spreads. A rifleman is not handed frontier kit
     * because there happened to be some in the hold when a battlesuit was
     * available; a tier is a price band, and the spread is between patterns that
     * cost the same.
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
        if (stock.isEmpty()) {
            throw new IllegalStateException("Armour plan '" + plan.id()
                    + "' cannot be issued: nothing at all is available");
        }

        Map<ArmorRole, List<MarineArmorCatalogDef>> bands = new EnumMap<>(ArmorRole.class);
        Map<ArmorRole, Integer> dealt = new EnumMap<>(ArmorRole.class);
        List<String> issued = new ArrayList<>(MarineSquad.CAPACITY);
        for (int billet = 0; billet < MarineSquad.CAPACITY; billet++) {
            ArmorRole role = plan.roleAt(billet);
            List<MarineArmorCatalogDef> band = bands.computeIfAbsent(
                    role, key -> topBand(plan, key, stock));
            int next = dealt.merge(role, 1, Integer::sum) - 1;
            issued.add(band.get(next % band.size()).id());
        }
        return SquadArmorDoctrine.fromIds(plan.id(), plan.displayName(),
                plan.description(), issued);
    }

    /** Convenience for "everything the catalog has", used by previews and tests. */
    public static SquadArmorDoctrine resolveUnrestricted(SquadArmorPlan plan) {
        return resolve(plan, pattern -> true);
    }

    /**
     * The patterns this role's billets are dealt from: the best tier the role
     * can reach, and everything else available at that same tier, worth-first.
     */
    private static List<MarineArmorCatalogDef> topBand(
            SquadArmorPlan plan, ArmorRole role, List<MarineArmorCatalogDef> stock) {
        List<MarineArmorCatalogDef> candidates = matching(stock,
                pattern -> pattern.role() == role && pattern.tradition() == plan.tradition());
        if (candidates.isEmpty()) {
            candidates = matching(stock, pattern -> pattern.role() == role);
        }
        if (candidates.isEmpty()) {
            candidates = matching(stock, pattern -> pattern.role() == ArmorRole.LINE
                    && pattern.tradition() == plan.tradition());
        }
        if (candidates.isEmpty()) {
            candidates = matching(stock, pattern -> pattern.role() == ArmorRole.LINE);
        }
        if (candidates.isEmpty()) candidates = new ArrayList<>(stock);

        int ceiling = candidates.stream()
                .mapToInt(MarineArmorCatalogDef::tier).max().orElseThrow();
        candidates.removeIf(pattern -> pattern.tier() != ceiling);
        candidates.sort(Comparator.comparingDouble(
                        (MarineArmorCatalogDef pattern) ->
                                LoadoutEffectiveness.patternResilience(pattern))
                .reversed()
                .thenComparing(MarineArmorCatalogDef::id));
        return List.copyOf(candidates);
    }

    private static List<MarineArmorCatalogDef> matching(
            List<MarineArmorCatalogDef> stock, Predicate<MarineArmorCatalogDef> match) {
        List<MarineArmorCatalogDef> result = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : stock) {
            if (match.test(pattern)) result.add(pattern);
        }
        return result;
    }
}
