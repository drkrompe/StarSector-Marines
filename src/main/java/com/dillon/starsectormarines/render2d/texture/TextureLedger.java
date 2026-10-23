package com.dillon.starsectormarines.render2d.texture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who holds each texture the mod loaded, so a texture is unloaded only when nobody draws it
 * ({@code battle-textures.md}).
 *
 * <p>The game keeps one texture per path for the whole process: two screens that load the same
 * image share one GL texture, and {@code SettingsAPI.unloadTexture} takes it away from both. So
 * a texture is never unloaded by the screen that is finished with it, but by the ledger, once
 * the last {@link TextureLease} that acquired it has been released. A {@linkplain #session()
 * session} lease is never released, which is how a screen that does not manage its textures
 * keeps them: everything it loads stays as resident as it always was.
 *
 * <p>A texture that was already resident when the ledger first saw it is <em>foreign</em> — the
 * game's own art, another mod's, or a load that went round the ledger — and is never unloaded,
 * because the ledger cannot know who else draws it.
 *
 * <p>Unloading deletes the GL texture as well as forgetting the path. The game's
 * {@code unloadTexture} only drops its own references to the texture and never calls
 * {@code glDeleteTextures}, so on its own it frees no video memory at all.
 *
 * <p>GL thread only, like every load it stands in front of.
 */
public final class TextureLedger {

    /** The game's texture store, or a test's stand-in for it. */
    public interface Backend {
        /** The GL texture already bound to {@code path}, or 0 when none is. */
        int residentId(String path);

        /** Loads {@code path} and returns its GL texture id. */
        int load(String path) throws IOException;

        /** Bytes the texture occupies on the card, padding included. */
        long bytes(String path);

        /** Deletes the GL texture and makes the store forget {@code path}. */
        void unload(String path, int textureId);
    }

    /** One path: who holds it and what it costs. */
    private static final class Entry {
        final Set<TextureLease> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        final boolean foreign;
        final int textureId;
        final long bytes;

        Entry(boolean foreign, int textureId, long bytes) {
            this.foreign = foreign;
            this.textureId = textureId;
            this.bytes = bytes;
        }
    }

    /** What one {@link TextureLease#release()} did. */
    public record Release(int unloaded, long bytesFreed, int kept, List<Integer> deletedIds) { }

    private static TextureLedger shared;
    /** The game settings {@link #shared} was made over; another one means another texture store. */
    private static SettingsAPI sharedSettings;

    private final Backend backend;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final TextureLease session;

    public TextureLedger(Backend backend) {
        this.backend = backend;
        this.session = new TextureLease(this, "session", true);
    }

    /**
     * The ledger over the game's own texture store. The game installs its settings once, so in
     * play this is one ledger for the whole process; a test that installs its own settings has
     * its own store, and gets a ledger of its own rather than one remembering another store's
     * textures.
     */
    public static synchronized TextureLedger shared() {
        SettingsAPI settings = Global.getSettings();
        if (shared == null || settings != sharedSettings) {
            shared = new TextureLedger(new GameTextureBackend());
            sharedSettings = settings;
        }
        return shared;
    }

    /** The lease that is never released: what a screen that does not manage its textures uses. */
    public TextureLease session() {
        return session;
    }

    /** A new lease that releases its textures when it is {@linkplain TextureLease#release() released}. */
    public TextureLease lease(String name) {
        return new TextureLease(this, name, false);
    }

    /** Loads {@code path} if it is not resident and records {@code holder} as drawing it. */
    void acquire(TextureLease holder, String path) throws IOException {
        Entry entry = entries.get(path);
        if (entry == null) {
            int resident = backend.residentId(path);
            boolean foreign = resident != 0;
            int id = foreign ? resident : backend.load(path);
            entry = new Entry(foreign, id, backend.bytes(path));
            entries.put(path, entry);
        }
        entry.holders.add(holder);
    }

    /** Drops {@code holder} from every path it holds, unloading the ones nobody else holds. */
    Release release(TextureLease holder, Iterable<String> paths) {
        int unloaded = 0;
        int kept = 0;
        long freed = 0;
        List<Integer> deleted = new ArrayList<>();
        for (String path : paths) {
            Entry entry = entries.get(path);
            if (entry == null || !entry.holders.remove(holder)) continue;
            if (!entry.holders.isEmpty()) {
                kept++;
                continue;
            }
            entries.remove(path);
            if (entry.foreign) {
                kept++;
                continue;
            }
            backend.unload(path, entry.textureId);
            deleted.add(entry.textureId);
            unloaded++;
            freed += entry.bytes;
        }
        return new Release(unloaded, freed, kept, deleted);
    }

    /** Whether any lease holds {@code path}. */
    public boolean held(String path) {
        Entry entry = entries.get(path);
        return entry != null && !entry.holders.isEmpty();
    }

    /** Bytes of every texture this ledger loaded and has not unloaded; foreign ones excluded. */
    public long residentBytes() {
        long total = 0;
        for (Entry entry : entries.values()) if (!entry.foreign) total += entry.bytes;
        return total;
    }

    /** Bytes held by {@code holder}, counting a texture it shares in full. */
    long bytesHeldBy(TextureLease holder) {
        long total = 0;
        for (Entry entry : entries.values()) {
            if (!entry.foreign && entry.holders.contains(holder)) total += entry.bytes;
        }
        return total;
    }
}
