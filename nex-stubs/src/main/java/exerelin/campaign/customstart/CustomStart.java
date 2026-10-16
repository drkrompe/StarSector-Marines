package exerelin.campaign.customstart;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.Map;

/** Compile-only stand-in; Nexerelin supplies the real class. See {@code nex-stubs/build.gradle}. */
public abstract class CustomStart {

    public abstract void execute(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap);
}
