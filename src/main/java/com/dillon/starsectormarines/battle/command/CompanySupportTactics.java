package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFrame.CompanyReport;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Militia screen the company's actual progress rather than independently racing the route. */
final class CompanySupportTactics {
    record Intent(int leadId, int x, int y, Reason reason) { }
    private final Map<Integer, Intent> screens = new HashMap<>();

    Intent intent(int squadId, int x, int y, List<CompanyReport> company) {
        Intent previous = screens.get(squadId);
        // Wait for infantry assembly even if a faster heavy unit is already available.
        boolean infantryPresent = company.stream().anyMatch(CompanyReport::infantry);
        List<CompanyReport> ready = company.stream()
                .filter(row -> row.ready() && row.infantry() == infantryPresent).toList();
        CompanyReport lead = ready.stream()
                .filter(row -> previous != null && row.squadId() == previous.leadId()).findFirst()
                .orElseGet(() -> ready.stream().min(Comparator
                        .comparingInt((CompanyReport row) -> distanceSquared(x, y, row.cellX(), row.cellY()))
                        .thenComparingInt(CompanyReport::squadId)).orElse(null));
        Intent next;
        if (lead == null) {
            // Keep the last secured screen when the company disappears; never continue the assault alone.
            next = new Intent(-1, previous == null ? x : previous.x(), previous == null ? y : previous.y(),
                    Reason.DEPOT_WAIT_FOR_COMPANY);
        } else if (previous != null && previous.leadId() == lead.squadId()
                && distanceSquared(previous.x(), previous.y(), lead.cellX(), lead.cellY()) < 36) {
            next = previous;
        } else {
            next = new Intent(lead.squadId(), lead.cellX(), lead.cellY(), Reason.DEPOT_SCREEN_COMPANY);
        }
        screens.put(squadId, next);
        return next;
    }

    private static int distanceSquared(int x, int y, int otherX, int otherY) {
        int dx = x - otherX, dy = y - otherY;
        return dx * dx + dy * dy;
    }
}
