package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.systems.StationingLapseSystem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.dillon.starsectormarines.campaign.PlayerEventInboxTest.addGarrison;
import static com.dillon.starsectormarines.campaign.PlayerEventInboxTest.armDefense;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEventAckTest {

    private static final int ARMED = 40;
    private static final int DEADLINE =
            ARMED + StationingLapseSystem.GARRISON_RESPONSE_DAYS;
    /** First day inside the reminder window. */
    private static final int REMINDER_DAY = DEADLINE - PlayerEventInbox.REMINDER_LEAD_DAYS;

    @Test
    void aNoticePopsOnceOnArrival() {
        CampaignState state = pendingDefense();

        PlayerEventNotice notice = PlayerEventInbox.nextToPresent(state, null, ARMED);
        assertNotNull(notice);
        assertTrue(PlayerEventInbox.acknowledge(state, notice, ARMED));

        assertNull(PlayerEventInbox.nextToPresent(state, null, ARMED));
        assertNull(PlayerEventInbox.nextToPresent(state, null, ARMED + 1));
        assertTrue(PlayerEventInbox.isAcknowledged(state, notice));
    }

    @Test
    void aDeferredNoticeFiresExactlyOneReminderAsTheDeadlineCloses() {
        CampaignState state = pendingDefense();
        PlayerEventInbox.acknowledge(state,
                PlayerEventInbox.nextToPresent(state, null, ARMED), ARMED);

        assertNull(PlayerEventInbox.nextToPresent(state, null, REMINDER_DAY - 1));

        PlayerEventNotice reminder = PlayerEventInbox.nextToPresent(state, null, REMINDER_DAY);
        assertNotNull(reminder);
        assertTrue(PlayerEventInbox.acknowledge(state, reminder, REMINDER_DAY));

        // Terminal — no third pop on any later day inside the window.
        assertNull(PlayerEventInbox.nextToPresent(state, null, REMINDER_DAY + 1));
        assertNull(PlayerEventInbox.nextToPresent(state, null, DEADLINE));
    }

    @Test
    void aNoticeArmedInsideTheReminderWindowPopsOnlyOnce() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, ARMED, 900L);
        // One day of window left: no room for an arrival pop plus a reminder.
        state.contractResponseDeadlineTick[0] = ARMED + 1;

        PlayerEventNotice notice = PlayerEventInbox.nextToPresent(state, null, ARMED);
        assertNotNull(notice);
        assertTrue(PlayerEventInbox.acknowledge(state, notice, ARMED));

        assertNull(PlayerEventInbox.nextToPresent(state, null, ARMED));
        assertNull(PlayerEventInbox.nextToPresent(state, null, ARMED + 1));
    }

    @Test
    void acknowledgingTwiceAtTheSameStageIsANoOp() {
        CampaignState state = pendingDefense();
        PlayerEventNotice notice = PlayerEventInbox.nextToPresent(state, null, ARMED);

        assertTrue(PlayerEventInbox.acknowledge(state, notice, ARMED));
        assertFalse(PlayerEventInbox.acknowledge(state, notice, ARMED));
        assertEquals(PlayerEventInbox.STAGE_ARRIVAL, state.contractNoticeAckStage[0]);
    }

    @Test
    void aNewEventOnTheSameContractIsNotCoveredByTheOldAcknowledgement() {
        CampaignState state = pendingDefense();
        PlayerEventInbox.acknowledge(state,
                PlayerEventInbox.nextToPresent(state, null, ARMED), ARMED);
        assertNull(PlayerEventInbox.nextToPresent(state, null, ARMED));

        // The first defense resolves; a fresh one arms with a different event key.
        armDefense(state, 0, ARMED + 20, 901L);
        state.contractResponseDeadlineTick[0] = -1;

        PlayerEventNotice fresh = PlayerEventInbox.nextToPresent(state, null, ARMED + 20);
        assertNotNull(fresh);
        assertEquals(901L, fresh.sourceKey);
        assertFalse(PlayerEventInbox.isAcknowledged(state, fresh));
    }

    @Test
    void acknowledgementSurvivesSaveAndLoad() throws Exception {
        CampaignState state = pendingDefense();
        PlayerEventNotice notice = PlayerEventInbox.nextToPresent(state, null, ARMED);
        PlayerEventInbox.acknowledge(state, notice, ARMED);

        Method readResolve = CampaignState.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(state);

        assertNull(PlayerEventInbox.nextToPresent(state, null, ARMED));
        assertTrue(PlayerEventInbox.isAcknowledged(state,
                PlayerEventInbox.pending(state, null, ARMED).get(0)));
    }

    @Test
    void acknowledgingSomethingNotInTheTableChangesNothing() {
        CampaignState state = pendingDefense();
        PlayerEventNotice stale = new PlayerEventNotice(
                PlayerEventNotice.Kind.GARRISON_DEFENSE, 9_999L, 900L, 12,
                ARMED, DEADLINE, null, 40, 40, "garrisonDefensePending",
                "garrisonDefenseVanillaRaid");

        assertFalse(PlayerEventInbox.acknowledge(state, stale, ARMED));
        assertFalse(PlayerEventInbox.acknowledge(null, stale, ARMED));
        assertFalse(PlayerEventInbox.acknowledge(state, null, ARMED));
        assertFalse(PlayerEventInbox.isAcknowledged(state, stale));
        // The real row is untouched by the stale acknowledgement.
        assertEquals(0, state.contractNoticeAckStage[0]);
    }

    private static CampaignState pendingDefense() {
        CampaignState state = new CampaignState();
        addGarrison(state, 1L, 12, 500);
        armDefense(state, 0, ARMED, 900L);
        state.contractResponseDeadlineTick[0] = DEADLINE;
        return state;
    }
}
