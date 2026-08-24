package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.ComputedSignal;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;

import java.util.List;

/** Read-only retained projection for the owned-company Armory landing view. */
public final class FleetArmoryOverviewViewModel {

    private static final String PRIMARY_COMPANY_ID = "primary";

    private final MarineRoster roster;
    private final Runnable openPrimaryCompany;
    private final MutableSignal<Integer> revision;
    private final ComputedSignal<List<CompanyCard>> companyCards;
    private final ComputedSignal<String> fleetSummary;

    public FleetArmoryOverviewViewModel(
            Reactor reactor, MarineRoster roster, Runnable openPrimaryCompany) {
        if (reactor == null) throw new IllegalArgumentException("reactor is required");
        if (roster == null) throw new IllegalArgumentException("roster is required");
        if (openPrimaryCompany == null) {
            throw new IllegalArgumentException("openPrimaryCompany is required");
        }
        this.roster = roster;
        this.openPrimaryCompany = openPrimaryCompany;
        revision = reactor.signal(0);
        companyCards = reactor.computed(() -> {
            revision.get();
            return List.of(buildPrimaryCompany());
        });
        fleetSummary = reactor.computed(() -> {
            revision.get();
            CompanyCounts counts = counts();
            return "1 owned company  ·  " + counts.lineSquads + " marine squads  ·  "
                    + counts.mechSquads + " mech squads";
        });
    }

    public Signal<List<CompanyCard>> companyCards() { return companyCards; }
    public Signal<String> fleetSummary() { return fleetSummary; }

    /** Reprojects mutable campaign authorities whenever the screen is re-entered. */
    public void refresh() {
        revision.update(value -> value + 1);
    }

    private CompanyCard buildPrimaryCompany() {
        CompanyCounts counts = counts();
        Readiness readiness = readiness(counts.readyMarines, counts.authorizedMarines);
        String stationed = counts.stationedSquads == 0
                ? "All formations aboard"
                : counts.stationedSquads + (counts.stationedSquads == 1
                ? " squad stationed" : " squads stationed");
        String base = "company:" + PRIMARY_COMPANY_ID;
        return new CompanyCard(
                base, base + ":designation", base + ":name", base + ":status",
                base + ":marine-squads", base + ":mech-squads", base + ":readiness",
                base + ":stationing", base + ":open",
                "company-card " + readiness.cardClass,
                "company-status heading " + readiness.toneClass,
                "PRIMARY FORMATION", "Fleet Marine Company", readiness.label,
                counts.lineSquads + (counts.lineSquads == 1
                        ? " marine squad" : " marine squads"),
                counts.mechSquads + (counts.mechSquads == 1
                        ? " mech squad" : " mech squads"),
                counts.readyMarines + " / " + counts.authorizedMarines + " marines RTD",
                stationed, "Open company armory", openPrimaryCompany);
    }

    private CompanyCounts counts() {
        int lineSquads = 0;
        int readyMarines = 0;
        int stationedSquads = 0;
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            lineSquads++;
            readyMarines += roster.readyCount(squad);
            if (squad.stationed()) stationedSquads++;
        }
        return new CompanyCounts(lineSquads, roster.mechBay().squads().size(),
                readyMarines, lineSquads * MarineSquad.CAPACITY, stationedSquads);
    }

    private static Readiness readiness(int ready, int authorized) {
        if (authorized <= 0 || ready <= 0) {
            return new Readiness("STANDING DOWN", "company-card-unready", "tone-danger");
        }
        float ratio = ready / (float) authorized;
        if (ratio >= 1f) return new Readiness("READY", "company-card-ready", "tone-good");
        if (ratio >= 0.75f) {
            return new Readiness("OPERATIONAL", "company-card-operational", "tone-accent");
        }
        return new Readiness("RECONSTITUTING", "company-card-unready", "tone-danger");
    }

    private record CompanyCounts(int lineSquads, int mechSquads, int readyMarines,
                                 int authorizedMarines, int stationedSquads) { }

    private record Readiness(String label, String cardClass, String toneClass) { }

    /** One literal selectable presentation card; not an equipment-template noun. */
    public record CompanyCard(
            String id, String designationId, String nameId, String statusId,
            String marineSquadsId, String mechSquadsId, String readinessId,
            String stationingId, String openId, String classes, String statusClasses,
            String designation, String name, String status, String marineSquads,
            String mechSquads, String readiness, String stationing, String openLabel,
            Runnable open) implements MarkupPropertySource {

        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "designationId" -> designationId;
                case "nameId" -> nameId;
                case "statusId" -> statusId;
                case "marineSquadsId" -> marineSquadsId;
                case "mechSquadsId" -> mechSquadsId;
                case "readinessId" -> readinessId;
                case "stationingId" -> stationingId;
                case "openId" -> openId;
                case "classes" -> classes;
                case "statusClasses" -> statusClasses;
                case "designation" -> designation;
                case "name" -> name;
                case "status" -> status;
                case "marineSquads" -> marineSquads;
                case "mechSquads" -> mechSquads;
                case "readiness" -> readiness;
                case "stationing" -> stationing;
                case "openLabel" -> openLabel;
                case "open" -> open;
                default -> throw new IllegalArgumentException(
                        "Unknown company-card property: " + property);
            };
        }
    }
}
