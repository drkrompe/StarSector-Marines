package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Failure;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Observation;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Phase;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Route;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The handover's facts are separate: delivery, signature, commissioning, resolved attacks and ratification. */
class TermsOfProtectionProgressTest {
    private static final float DT = 1f / 30f;

    @Test void proximitySignsNothingAndEndingAnAttackRatifiesNothing() {
        var p = new TermsOfProtectionProgress(Route.HOUSE_CUSTODY);
        run(p, 2, quiet().carrierAtHandover(false));
        assertEquals(Phase.ESCORT, p.phase(), "company standing at dispatch does not deliver the party");
        run(p, 1, quiet());
        assertEquals(Phase.SIGNING, p.phase());
        run(p, 20, quiet().repAtHandover(false));
        assertFalse(p.signed(), "a signature needs a representative at the bay");
        run(p, TermsOfProtectionProgress.SIGNING_SECONDS - 1, quiet());
        run(p, 2, quiet().dispatchContested(true));
        assertEquals(0f, p.signing(), "a contested dispatch restarts the signing");
        run(p, TermsOfProtectionProgress.SIGNING_SECONDS + 1, quiet());
        assertEquals(Phase.COMMISSIONING, p.phase());
        run(p, TermsOfProtectionProgress.WORK_SECONDS + 5, quiet());
        assertEquals(Phase.DEFENCE, p.phase(), "commissioning alone does not ratify");
        assertFalse(p.ratified());
    }

    @Test void commissioningPausesUnderContestAndNeedsALivingTechnicianAtTheWork() {
        var p = signed(Route.YARD_COMPACT);
        run(p, 30, quiet());
        float before = p.work();
        run(p, 20, quiet().hallContested(true));
        assertEquals(before, p.work(), 1e-3f, "hostiles in the hall pause the work without discarding it");
        run(p, 20, quiet().companyInHall(false));
        assertEquals(before, p.work(), 1e-3f, "the hall must be covered by company infantry");
        run(p, 20, quiet().techAtWork(false));
        assertEquals(before, p.work(), 1e-3f);
        run(p, TermsOfProtectionProgress.WORK_SECONDS, quiet());
        assertTrue(p.commissioned());
        run(p, 5, quiet().technicians(0));
        assertNotEquals(Phase.FAILED, p.phase(), "technicians lost after the work is done do not fail it");
        run(p, 1, quiet().resolved(true));
        assertEquals(Phase.COMPLETE, p.phase());
        var report = p.report(false, 0, 2, 1, 0, 0, true);
        assertTrue(report.complete());
        assertEquals(Route.YARD_COMPACT, report.route());
    }

    @Test void eachStoryFailureEndsTheAttemptWithItsCause() {
        assertFailure(quiet().representatives(0), Failure.REPRESENTATIVES_LOST);
        assertFailure(quiet().carrier(false), Failure.CARRIER_LOST);
        assertFailure(quiet().technicians(0), Failure.TECHNICIANS_LOST);
        assertFailure(quiet().company(false), Failure.COMPANY_LOST);
        var p = signed(Route.HOUSE_CUSTODY);
        p.fail(Failure.NONE);
        assertEquals(Phase.FAILED, p.phase());
        run(p, 100, quiet().resolved(true));
        assertFalse(p.ratified(), "a failed attempt never ratifies");
        assertThrows(IllegalArgumentException.class, () -> new TermsOfProtectionProgress(null));
    }

    @Test void aReportIsCompleteOnlyWithEveryFact() {
        var full = new TermsOfProtectionProgress.Report(Route.HOUSE_CUSTODY, true, true, true, true, true, true, true, false, 1, 1, 0, 4, 4);
        assertTrue(full.complete(), "individual casualties are recorded without failing the handover");
        assertFalse(new TermsOfProtectionProgress.Report(Route.HOUSE_CUSTODY, true, true, true, true, false, true, true, false, 0, 0, 0, 0, 0).complete());
        assertFalse(new TermsOfProtectionProgress.Report(Route.HOUSE_CUSTODY, true, true, true, true, true, true, true, true, 0, 0, 0, 0, 0).complete());
        assertFalse(new TermsOfProtectionProgress.Report(Route.HOUSE_CUSTODY, true, true, true, true, true, true, true, false, 2, 0, 0, 0, 0).complete());
    }

    private static void assertFailure(Obs o, Failure expected) {
        var p = new TermsOfProtectionProgress(Route.HOUSE_CUSTODY);
        run(p, 1, o);
        assertEquals(Phase.FAILED, p.phase());
        assertEquals(expected, p.failure());
    }

    private static TermsOfProtectionProgress signed(Route route) {
        var p = new TermsOfProtectionProgress(route);
        run(p, TermsOfProtectionProgress.SIGNING_SECONDS + 1, quiet());
        assertTrue(p.signed());
        return p;
    }

    private static void run(TermsOfProtectionProgress p, float seconds, Obs o) {
        for (int i = 0; i < Math.round(seconds / DT); i++) p.advance(DT, o.build());
    }

    private static Obs quiet() { return new Obs(); }

    /** A tick with the company present, everyone alive and in place, and nothing contested. */
    private static final class Obs {
        boolean company = true, carrier = true, carrierAtHandover = true, repAtHandover = true, techAtWork = true;
        boolean companyAtDispatch = true, dispatchContested, companyInHall = true, hallContested, resolved;
        int representatives = 2, technicians = 2;
        Obs company(boolean v) { company = v; return this; }
        Obs carrier(boolean v) { carrier = v; return this; }
        Obs carrierAtHandover(boolean v) { carrierAtHandover = v; return this; }
        Obs repAtHandover(boolean v) { repAtHandover = v; return this; }
        Obs techAtWork(boolean v) { techAtWork = v; return this; }
        Obs dispatchContested(boolean v) { dispatchContested = v; return this; }
        Obs companyInHall(boolean v) { companyInHall = v; return this; }
        Obs hallContested(boolean v) { hallContested = v; return this; }
        Obs resolved(boolean v) { resolved = v; return this; }
        Obs representatives(int v) { representatives = v; return this; }
        Obs technicians(int v) { technicians = v; return this; }
        Observation build() {
            return new Observation(company, carrier, carrierAtHandover, representatives, repAtHandover, technicians,
                    techAtWork, companyAtDispatch, dispatchContested, companyInHall, hallContested, resolved);
        }
    }
}
