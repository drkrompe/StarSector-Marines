package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyRatingTest {

    @Test
    void bandsAreAStableReadingOfMrbCredibility() {
        assertEquals(CompanyRating.COMPROMISED, CompanyRating.fromMrbCredibility(-1));
        assertEquals(CompanyRating.UNPROVEN, CompanyRating.fromMrbCredibility(0));
        assertEquals(CompanyRating.PROVISIONAL, CompanyRating.fromMrbCredibility(1));
        assertEquals(CompanyRating.PROVISIONAL, CompanyRating.fromMrbCredibility(9));
        assertEquals(CompanyRating.RECOGNIZED, CompanyRating.fromMrbCredibility(10));
        assertEquals(CompanyRating.ESTABLISHED, CompanyRating.fromMrbCredibility(30));
        assertEquals(CompanyRating.PREMIER, CompanyRating.fromMrbCredibility(60));
        assertEquals(CompanyRating.PREMIER,
                CompanyRating.fromMrbCredibility(Integer.MAX_VALUE));
    }
}
