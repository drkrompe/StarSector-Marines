package com.dillon.starsectormarines.marine;

import java.io.Serializable;

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
 */
public record SquadArmorPlan(
        String id,
        String displayName,
        String description,
        ArmorTradition tradition,
        SquadRoleMix mix) implements Serializable {

    public SquadArmorPlan {
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

    /** The job billet {@code index} does. */
    public ArmorRole roleAt(int index) {
        return mix.roleAt(index);
    }
}
