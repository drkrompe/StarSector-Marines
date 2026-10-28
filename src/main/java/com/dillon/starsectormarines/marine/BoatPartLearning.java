package com.dillon.starsectormarines.marine;

/** Pure transition that teaches a {@link BoatDeck} one boat part; writes only to the deck. */
public final class BoatPartLearning {

    public enum Result {
        LEARNED,
        ALREADY_KNOWN,
        UNKNOWN_PART,
        DECK_UNAVAILABLE
    }

    private BoatPartLearning() {}

    public static Result status(BoatDeck deck, String partId) {
        if (deck == null) return Result.DECK_UNAVAILABLE;
        if (!BoatParts.exists(partId)) return Result.UNKNOWN_PART;
        return deck.knowsPart(partId) ? Result.ALREADY_KNOWN : Result.LEARNED;
    }

    public static Result learn(BoatDeck deck, String partId) {
        Result status = status(deck, partId);
        if (status == Result.LEARNED) deck.learnPart(partId);
        return status;
    }
}
