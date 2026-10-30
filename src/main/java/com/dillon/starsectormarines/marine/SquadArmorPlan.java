package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

import java.io.Serializable;
import java.util.List;

/**
 * A squad's armour intent: <b>what jobs it is organised around, and whose kit it
 * draws on</b> — but not which patterns, and therefore not how good they are
 * ({@code role-and-access.md}).
 *
 * <p>This is the authored thing. {@link SquadArmorDoctrine} — twelve concrete
 * pattern ids — is what a plan becomes once somebody's actual stock is applied
 * to it, and is produced by {@link ArmorIssueResolver} rather than written by
 * hand.
 *
 * <p>The distinction is the whole point of the model. A doctrine that names
 * patterns cannot say "a scout, four breachers and seven riflemen" without also
 * fixing exactly how well equipped each of them is, so a company that grew rich
 * could only express it by adopting a different doctrine — which is how a fully
 * equipped force ended up organised as twelve identical breachers. A plan says
 * the composition once and survives the company getting richer.
 *
 * <p>A built-in tactic sheet whose card the owner seated suit by suit carries {@code seatSuits}: the
 * twelve patterns in billet order, as the Kit Ladder page shows them ({@code kit-ladder.md}, Data).
 * The resolver issues each seat its own suit while it is available, and a seat's class and job are
 * its suit's. A plan without them is dealt by role, class and tier as above.
 */
public record SquadArmorPlan(
        String id,
        String displayName,
        String description,
        ArmorTradition tradition,
        SquadRoleMix mix,
        ArmorClass armorClass,
        List<ArmorClass> billetClasses,
        List<String> seatSuits) implements Serializable {

    public SquadArmorPlan(String id, String displayName, String description,
            ArmorTradition tradition, SquadRoleMix mix, ArmorClass armorClass,
            List<ArmorClass> billetClasses) {
        this(id, displayName, description, tradition, mix, armorClass, billetClasses, null);
    }

    public SquadArmorPlan(String id, String displayName, String description,
            ArmorTradition tradition, SquadRoleMix mix, ArmorClass armorClass) {
        this(id, displayName, description, tradition, mix, armorClass, null);
    }

    public SquadArmorPlan(String id, String displayName, String description,
            ArmorTradition tradition, SquadRoleMix mix) {
        this(id, displayName, description, tradition, mix, ArmorClass.STANDARD);
    }

    public SquadArmorPlan {
        // Older serialized record components arrive as null.
        if (armorClass == null) armorClass = ArmorClass.STANDARD;
        if (billetClasses != null) {
            if (billetClasses.size() != MarineSquad.CAPACITY) {
                throw new IllegalArgumentException("An armor plan needs twelve billet classes");
            }
            billetClasses = List.copyOf(billetClasses);
        }
        if (seatSuits != null) {
            if (seatSuits.size() != MarineSquad.CAPACITY) {
                throw new IllegalArgumentException("An armor plan's seat suits name twelve patterns");
            }
            seatSuits = List.copyOf(seatSuits);
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Armour plan id is required");
        }
        if (tradition == null) {
            throw new IllegalArgumentException("Armour plan '" + id + "' needs a tradition");
        }
        if (mix == null) {
            throw new IllegalArgumentException("Armour plan '" + id + "' needs a role mix");
        }
    }

    public String displayName() { return Strings.resolve(displayName); }
    public String description() { return Strings.resolve(description); }

    /** Uniform legacy plans retain their class; explicit mixed plans declare every seat. */
    public ArmorClass armorClassAt(int index) {
        return billetClasses == null ? armorClass : billetClasses.get(index);
    }

    public boolean isMixedClass() {
        return billetClasses != null && billetClasses.stream().distinct().count() > 1;
    }

    public String armorClassDisplayName() {
        return isMixedClass()
                ? ArmorClass.STANDARD.displayName() + " / " + ArmorClass.POWER_SUIT.displayName()
                : armorClassAt(0).displayName();
    }

    /** The suit seated at billet {@code index}, or null for a plan dealt by role. */
    public String seatSuitAt(int index) {
        return seatSuits == null ? null : seatSuits.get(index);
    }

    /** The job billet {@code index} does: its seated suit's role, else the role mix's. */
    public ArmorRole roleAt(int index) {
        String suit = seatSuitAt(index);
        MarineArmorCatalogRegistry catalog = suit != null ? MarineArmorCatalogRegistry.installed() : null;
        MarineArmorCatalogDef pattern = catalog != null ? catalog.get(suit) : null;
        return pattern != null ? pattern.role() : mix.roleAt(index);
    }
}
