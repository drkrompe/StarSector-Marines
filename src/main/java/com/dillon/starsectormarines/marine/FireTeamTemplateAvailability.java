package com.dillon.starsectormarines.marine;

/** Company-wide usage and uncommitted-stock capacity for one reusable card. */
public record FireTeamTemplateAvailability(int fielded, int readyToIssue,
                                           boolean recipesUnlocked) {
}
