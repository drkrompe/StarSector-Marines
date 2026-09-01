package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.Reflex;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The marine's reflex order, written down.
 *
 * <p><b>This is a law, not a formatting preference.</b> Each entry pre-empts
 * every entry after it and the plan step itself, so the list below <em>is</em>
 * the priority statement: a committed aim finishes before anything else is
 * considered, grenade evasion outranks a shot of opportunity, and a broken fire
 * team outranks the step. Reordering it changes what a marine does under fire.
 * If this test fails because the chain moved, the fix is not to re-type the
 * expected list: it is to measure the change — the behaviour scenes and the
 * commander matrices are what say whether the new order is better — and only
 * then to restate the law here with the reason.
 *
 * <p>Adding a reflex is the ordinary case and does belong here: insert it at the
 * rank its priority earns and add its name at that index.
 */
public class InfantryReflexOrderTest {

    @Test
    public void theChainIsDeclaredInPriorityOrder() {
        assertEquals(
                List.of("COMMITTED_AIM",
                        "COOLDOWNS",
                        "FRIENDLY_CHARGE",
                        "KNOWN_GRENADE",
                        "REJOIN",
                        "OPPORTUNITY_SPECIAL",
                        "HARDENED_OPPORTUNITY",
                        "ONSET_SCREEN",
                        "BROKEN_FIRE_TEAM",
                        "LANE_SIDESTEP"),
                InfantryReflexes.CHAIN.stream().map(Reflex::name).toList(),
                "the marine's reflex order is a behaviour statement - see this test's "
                        + "javadoc before changing it");
    }

    /**
     * A move-only coordinated role withholds the general special-equipment path,
     * and the reflex answers that from the context alone. Passing a null sim is
     * the assertion: reaching the world at all would fail here, so a pass proves
     * the suppression is decided before anything is looked up rather than after.
     */
    @Test
    public void theGeneralOpportunityShotIsWithheldWithoutConsultingTheWorld() {
        assertFalse(InfantryReflexes.OPPORTUNITY_SPECIAL.interrupt(
                        1L, null, new ReflexContext(false), null),
                "a step that forbids opportunity fire suppresses the general special "
                        + "path outright");
    }

    /**
     * The other side of the same split: the two narrowing reflexes exist only
     * for the withheld case, and a permitted step must not reach them at all —
     * or an ordinary marine would fire a rocket and raise a screen through the
     * seam that was meant to keep an advance moving.
     */
    @Test
    public void theNarrowingReflexesRunOnlyWhileOpportunityFireIsWithheld() {
        ReflexContext permitted = new ReflexContext(true);
        assertFalse(InfantryReflexes.HARDENED_OPPORTUNITY.interrupt(
                        1L, null, permitted, null),
                "the hardened-target rocket is the withheld case's answer, not an "
                        + "always-on second opportunity path");
        assertFalse(InfantryReflexes.ONSET_SCREEN.interrupt(1L, null, permitted, null),
                "so is the onset screen");
    }
}
