package com.dillon.starsectormarines.campaign.bloom;

/** Where one Rav Bloom outbreak stands ({@code colony-breach-nouns.md}, Campaign: blooms). Append only. */
public enum BloomStage {
    /** Seepage: deniable signs, no market effect; the player hears a rumour. */
    STIRRING,
    /** Bores are open inside the colony; the distress call is out. */
    ERUPTED,
    /** Nobody lifted enough of them out: the colony has gone quiet and is being eaten down. */
    DARK,
    /** Half or more of the cohort was lifted out: the bloom has too little to eat and is burning out. */
    STARVED,
    /** The outbreak is over. The weapon is not. */
    ENDED
}
