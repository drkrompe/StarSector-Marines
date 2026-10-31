package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** New-company introduction. Null on a roster means a grandfathered company. */
public final class OfficerInduction implements Serializable {
    public static final String SABINE_PORTRAIT = "graphics/portraits/sabine-hale.png";

    /**
     * Derived, never saved. The first lift is not a step: the company rides up to Relief on Wayline's truck, and
     * the first Hermes is repaired in the campaign lesson between Relief and Counterattack.
     */
    public enum Step { INTRODUCTION, SPECIALTY, FORMATION, ASSIGNMENT, READY, COMPLETE, SHIP }

    private final String foundingOfficerId;
    private boolean introduced;
    private boolean complete;
    private boolean companyPreparation;

    public OfficerInduction(String foundingOfficerId) { this.foundingOfficerId = foundingOfficerId; }
    public String foundingOfficerId() { return foundingOfficerId; }
    public boolean complete() { return complete; }
    public boolean companyPreparation() { return companyPreparation; }
    void prepareCompany() { companyPreparation = true; }

    /** The lesson belongs to the founding field commander, who cannot be lost in battle. */
    public MarineCaptain officer(MarineRoster roster) {
        MarineCaptain founder = roster.byId(foundingOfficerId);
        return founder != null && founder.status() != Status.KIA ? founder : null;
    }

    public void introduce(MarineCaptain officer) {
        if (officer != null && officer.status() != Status.KIA) introduced = true;
    }

    public Step step(MarineRoster roster) {
        if (complete) return Step.COMPLETE;
        if (companyPreparation && roster.companyShipId() == null) return Step.SHIP;
        MarineCaptain officer = officer(roster);
        if (!introduced || officer == null) return Step.INTRODUCTION;
        if (OfficerSpecialty.offered().stream().noneMatch(s -> officer.career().level(s) > 0))
            return Step.SPECIALTY;
        var squads = roster.squads().stream()
                .filter(s -> !s.reserve() && !s.stationed()
                        && roster.readyCount(s) >= (companyPreparation ? MarineSquad.CAPACITY : 1)).toList();
        if (squads.isEmpty()) return Step.FORMATION;
        if (squads.stream().noneMatch(s -> officer.id().equals(s.homeCaptainId()))) return Step.ASSIGNMENT;
        return Step.READY;
    }

    /** Acknowledgement cannot replace training or an actual squad assignment. */
    public boolean finish(MarineRoster roster) {
        if (step(roster) != Step.READY) return false;
        complete = true;
        return true;
    }
}
