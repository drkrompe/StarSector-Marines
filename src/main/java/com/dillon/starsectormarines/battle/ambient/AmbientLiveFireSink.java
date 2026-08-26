package com.dillon.starsectormarines.battle.ambient;

/** Executes an authored ambient range trigger through the owning battle simulation. */
@FunctionalInterface
public interface AmbientLiveFireSink {

    AmbientLiveFireSink NONE = (actorId, targetId) -> { };

    void firePrimary(long actorId, long targetId);
}
