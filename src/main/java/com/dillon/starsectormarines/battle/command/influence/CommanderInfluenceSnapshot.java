package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Immutable read window over one faction's two-channel tactical influence field. */
public final class CommanderInfluenceSnapshot {

    private final Faction faction;
    private final int updatedTick;
    private final int blockSize;
    private final int width;
    private final int height;
    private final int worldWidth;
    private final int worldHeight;
    private final float[] friendly;
    private final float[] hostile;
    /** Where this faction has recently lost people; see {@link CasualtyMemory}. */
    private final float[] losses;
    private final List<CommanderContact> contacts;
    private final float maxFriendly;
    private final float maxHostile;

    CommanderInfluenceSnapshot(Faction faction, int updatedTick, int blockSize,
                               int width, int height, int worldWidth, int worldHeight,
                               float[] friendly, float[] hostile, float[] losses,
                               List<CommanderContact> contacts) {
        this.faction = faction;
        this.updatedTick = updatedTick;
        this.blockSize = blockSize;
        this.width = width;
        this.height = height;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.friendly = friendly;
        this.hostile = hostile;
        this.losses = losses;
        this.contacts = List.copyOf(contacts);
        this.maxFriendly = max(friendly);
        this.maxHostile = max(hostile);
    }

    public Faction faction() { return faction; }
    public int updatedTick() { return updatedTick; }
    public int blockSize() { return blockSize; }
    public int width() { return width; }
    public int height() { return height; }
    public int worldWidth() { return worldWidth; }
    public int worldHeight() { return worldHeight; }
    public List<CommanderContact> contacts() { return contacts; }
    public float maxFriendly() { return maxFriendly; }
    public float maxHostile() { return maxHostile; }

    public float friendlyAt(int blockX, int blockY) {
        return inBounds(blockX, blockY) ? friendly[index(blockX, blockY)] : 0f;
    }

    public float lossesAt(int blockX, int blockY) {
        return inBounds(blockX, blockY) ? losses[index(blockX, blockY)] : 0f;
    }

    public float lossesAtWorld(int cellX, int cellY) {
        if (!worldInBounds(cellX, cellY)) return 0f;
        return lossesAt(cellX / blockSize, cellY / blockSize);
    }

    public float hostileAt(int blockX, int blockY) {
        return inBounds(blockX, blockY) ? hostile[index(blockX, blockY)] : 0f;
    }

    public float friendlyAtWorld(int cellX, int cellY) {
        if (!worldInBounds(cellX, cellY)) return 0f;
        return friendlyAt(cellX / blockSize, cellY / blockSize);
    }

    public float hostileAtWorld(int cellX, int cellY) {
        if (!worldInBounds(cellX, cellY)) return 0f;
        return hostileAt(cellX / blockSize, cellY / blockSize);
    }

    public int blockWorldX(int blockX) { return blockX * blockSize; }
    public int blockWorldY(int blockY) { return blockY * blockSize; }
    public int blockWorldWidth(int blockX) {
        return Math.max(0, Math.min(blockSize, worldWidth - blockWorldX(blockX)));
    }
    public int blockWorldHeight(int blockY) {
        return Math.max(0, Math.min(blockSize, worldHeight - blockWorldY(blockY)));
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private boolean worldInBounds(int x, int y) {
        return x >= 0 && x < worldWidth && y >= 0 && y < worldHeight;
    }

    private int index(int x, int y) { return y * width + x; }

    private static float max(float[] values) {
        float result = 0f;
        for (float value : values) result = Math.max(result, value);
        return result;
    }
}
