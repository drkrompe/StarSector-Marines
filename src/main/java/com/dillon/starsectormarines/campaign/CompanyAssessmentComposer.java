package com.dillon.starsectormarines.campaign;

import java.util.Random;

/** Renders the adjutant's daily Company HQ assessment from authored mood voice. */
public final class CompanyAssessmentComposer {
    private static final long SEED_MIXER = 0xD1B54A32D192ED03L;

    private CompanyAssessmentComposer() { }

    public static String render(OfficerMood mood, int currentDay,
                                CompanyRating rating, int ready, int strength,
                                int squads, int wounded, int stationed) {
        OfficerMood safeMood = mood != null ? mood : OfficerMood.STEADY;
        CompanyRating safeRating = rating != null ? rating : CompanyRating.UNPROVEN;
        String[] pool = CommsOfficerVoice.forMood(safeMood).companyAssessment;
        long seed = (long) currentDay * 0x9E3779B97F4A7C15L + SEED_MIXER;
        String template = pool[Math.floorMod(new Random(seed).nextInt(), pool.length)];
        return template
                .replace("{rating}", safeRating.name())
                .replace("{ready}", Integer.toString(ready))
                .replace("{strength}", Integer.toString(strength))
                .replace("{squads}", Integer.toString(squads))
                .replace("{wounded}", Integer.toString(wounded))
                .replace("{stationed}", Integer.toString(stationed));
    }
}
