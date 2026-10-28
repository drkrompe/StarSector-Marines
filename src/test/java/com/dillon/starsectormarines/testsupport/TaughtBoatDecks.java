package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatPartLearning;
import com.dillon.starsectormarines.marine.BoatParts;

/** A boat deck that has learned every part, for fixtures about fitting and building rather than learning. */
public final class TaughtBoatDecks {

    private TaughtBoatDecks() {}

    public static BoatDeck taught(BoatDeck deck) {
        for (String id : BoatParts.offerable()) BoatPartLearning.learn(deck, id);
        return deck;
    }
}
