package com.dillon.starsectormarines.intel;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * An intel event still open to the company, read for Company HQ's action board: what it is,
 * where, and how long it has. A projection of the event row; it decides nothing.
 *
 * @param eventId  the event row's identity, stable while the event is open
 * @param title    the intel entry's name
 * @param place    the market's name, or its id when the market is gone
 * @param market   the market, or null when it cannot be resolved
 * @param daysLeft days until the event's deadline, floored at zero
 */
public record SideJobLead(long eventId, String title, String place, MarketAPI market, int daysLeft) {
}
