package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What jobs a squad is organised around, one role per billet
 * ({@code role-and-access.md}).
 *
 * <p>This is half of an armour plan and it is the half that <b>does not move
 * when a company gets richer</b>. A section that needed a scout last year still
 * needs one; what changes is the suit the scout is issued. Keeping the mix
 * separate from the kit is what stopped tier from silently deciding
 * composition — the doctrine model this replaced named twelve concrete patterns,
 * so "what jobs are in this squad" and "how good is their equipment" were one
 * number, and buying up the ladder reshaped the squad.
 *
 * <p>The mixes here are shared across traditions on purpose. A Hegemony line
 * section and a League one are organised much the same way and differ in whose
 * kit fills the slots, which is exactly the split
 * {@link SquadArmorPlan} expresses.
 */
public final class SquadRoleMix {

    /** Billets in a fire team, so a mix can be written a team at a time. */
    private static final int TEAM = MarineSquad.TEAM_SIZE;

    /**
     * The ordinary section: a scout out front, a weapons carrier in each of two
     * teams, a breach pair, and riflemen for the rest. Most doctrines are this,
     * because most sections are.
     */
    public static final SquadRoleMix BALANCED = mix("balanced", "Balanced section",
            team(ArmorRole.RECON, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.SUPPORT),
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.ASSAULT),
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.ASSAULT, ArmorRole.SUPPORT));

    /**
     * Built to get through a door: two whole teams in assault kit, a support
     * marine to make the hole bigger, and one scout to find it. The lopsided
     * end of the spectrum, and a real choice rather than the only thing the top
     * of the ladder can mean.
     */
    public static final SquadRoleMix BREACH = mix("breach", "Breach section",
            team(ArmorRole.RECON, ArmorRole.ASSAULT, ArmorRole.ASSAULT, ArmorRole.SUPPORT),
            team(ArmorRole.ASSAULT, ArmorRole.ASSAULT, ArmorRole.ASSAULT, ArmorRole.ASSAULT),
            team(ArmorRole.ASSAULT, ArmorRole.ASSAULT, ArmorRole.LINE, ArmorRole.SUPPORT));

    /** Eyes rather than weight: a whole team scouting, the rest carrying the net. */
    public static final SquadRoleMix RECONNAISSANCE = mix("reconnaissance", "Recon section",
            team(ArmorRole.RECON, ArmorRole.RECON, ArmorRole.RECON, ArmorRole.RECON),
            team(ArmorRole.RECON, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.SUPPORT),
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.SUPPORT));

    /** Holds ground and does not go looking: riflemen, weight of fire, one pair of eyes. */
    public static final SquadRoleMix LINE_HOLD = mix("line-hold", "Line section",
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.SUPPORT),
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.SUPPORT),
            team(ArmorRole.RECON, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE));

    /**
     * What a garrison or an irregular band actually looks like: nobody is a
     * specialist because nobody was trained as one, and the two who carry
     * something heavy carry it because they are strong.
     */
    public static final SquadRoleMix IRREGULAR = mix("irregular", "Irregular band",
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE),
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.LINE, ArmorRole.SUPPORT),
            team(ArmorRole.LINE, ArmorRole.LINE, ArmorRole.RECON, ArmorRole.ASSAULT));

    private final String id;
    private final String displayName;
    private final List<ArmorRole> billets;

    private SquadRoleMix(String id, String displayName, List<ArmorRole> billets) {
        this.id = id;
        this.displayName = displayName;
        this.billets = List.copyOf(billets);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }

    /** The role each billet fills, in squad order. Always {@link MarineSquad#CAPACITY} long. */
    public List<ArmorRole> billets() { return Collections.unmodifiableList(billets); }

    public ArmorRole roleAt(int billet) { return billets.get(billet); }

    private static List<ArmorRole> team(ArmorRole... roles) {
        if (roles.length != TEAM) {
            throw new IllegalArgumentException("A fire team is " + TEAM + " billets");
        }
        return List.of(roles);
    }

    @SafeVarargs
    private static SquadRoleMix mix(String id, String displayName, List<ArmorRole>... teams) {
        List<ArmorRole> billets = new ArrayList<>();
        for (List<ArmorRole> team : teams) billets.addAll(team);
        if (billets.size() != MarineSquad.CAPACITY) {
            throw new IllegalArgumentException("A squad is " + MarineSquad.CAPACITY + " billets");
        }
        return new SquadRoleMix(id, displayName, billets);
    }
}
