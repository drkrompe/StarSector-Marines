package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveManningServiceTest {

    @Test
    void theStanceIsOptInAndToggledOverTheSelection() {
        ReactiveManningService stance = new ReactiveManningService();
        assertFalse(stance.isEnabled(4), "never a default");
        assertEquals(Status.OFF, stance.view(4).status());

        stance.toggle(List.of(4, 7));
        assertTrue(stance.isEnabled(4) && stance.isEnabled(7));
        assertEquals(Status.WATCHING, stance.view(7).status());

        stance.setEnabled(7, false);
        stance.toggle(List.of(4, 7));
        assertTrue(stance.isEnabled(4) && stance.isEnabled(7), "a mixed selection turns everyone on");

        stance.toggle(List.of(4, 7));
        assertFalse(stance.isEnabled(4) || stance.isEnabled(7), "an all-on selection turns everyone off");
        assertEquals(Status.OFF, stance.view(4).status());
    }
}
