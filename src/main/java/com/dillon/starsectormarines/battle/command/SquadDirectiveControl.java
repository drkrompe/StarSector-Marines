package com.dillon.starsectormarines.battle.command;

/** Serial battle mutation boundary for externally owned squad directives. */
public interface SquadDirectiveControl {

    void claimSquadCommand(int squadId, CommandAuthority authority,
                           String issuer, String reason);

    void assignSquadCommand(ObjectiveAssignment assignment,
                            CommandAuthority authority,
                            String issuer, String reason);

    boolean handoffSquadCommand(int squadId, String currentIssuer,
                                CommandAuthority nextAuthority,
                                String nextIssuer,
                                ObjectiveAssignment nextAssignment,
                                String reason);

    boolean releaseSquadCommand(int squadId, String issuer, String reason);
}
