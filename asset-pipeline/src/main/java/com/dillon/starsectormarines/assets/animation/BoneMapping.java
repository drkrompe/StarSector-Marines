package com.dillon.starsectormarines.assets.animation;

import java.util.HashMap;
import java.util.Map;

/**
 * Bone name mappings between skeleton conventions for animation retargeting.
 */
public final class BoneMapping {

    private BoneMapping() {}

    /**
     * Returns the bone name mapping from Blender MakeHuman (B-*) convention
     * to Mixamo/standard humanoid convention.
     */
    public static Map<String, String> blenderToMixamo() {
        Map<String, String> map = new HashMap<>();
        map.put("B-hips", "Hips");
        map.put("B-spine", "Spine");
        map.put("B-neck", "Neck");
        map.put("B-head", "Head");
        map.put("B-shoulder.L", "LeftShoulder");
        map.put("B-upperArm.L", "LeftArm");
        map.put("B-forearm.L", "LeftForeArm");
        map.put("B-hand.L", "LeftHand");
        map.put("B-indexFinger01.L", "LeftHandIndex1");
        map.put("B-indexFinger02.L", "LeftHandIndex2");
        map.put("B-indexFinger03.L", "LeftHandIndex3");
        map.put("B-middleFinger01.L", "LeftHandMiddle1");
        map.put("B-middleFinger02.L", "LeftHandMiddle2");
        map.put("B-middleFinger03.L", "LeftHandMiddle3");
        map.put("B-ringFinger01.L", "LeftHandRing1");
        map.put("B-ringFinger02.L", "LeftHandRing2");
        map.put("B-ringFinger03.L", "LeftHandRing3");
        map.put("B-pinky01.L", "LeftHandPinky1");
        map.put("B-pinky02.L", "LeftHandPinky2");
        map.put("B-pinky03.L", "LeftHandPinky3");
        map.put("B-thumb01.L", "LeftHandThumb1");
        map.put("B-thumb02.L", "LeftHandThumb2");
        map.put("B-thumb03.L", "LeftHandThumb3");
        map.put("B-shoulder.R", "RightShoulder");
        map.put("B-upperArm.R", "RightArm");
        map.put("B-forearm.R", "RightForeArm");
        map.put("B-hand.R", "RightHand");
        map.put("B-indexFinger01.R", "RightHandIndex1");
        map.put("B-indexFinger02.R", "RightHandIndex2");
        map.put("B-indexFinger03.R", "RightHandIndex3");
        map.put("B-middleFinger01.R", "RightHandMiddle1");
        map.put("B-middleFinger02.R", "RightHandMiddle2");
        map.put("B-middleFinger03.R", "RightHandMiddle3");
        map.put("B-ringFinger01.R", "RightHandRing1");
        map.put("B-ringFinger02.R", "RightHandRing2");
        map.put("B-ringFinger03.R", "RightHandRing3");
        map.put("B-pinky01.R", "RightHandPinky1");
        map.put("B-pinky02.R", "RightHandPinky2");
        map.put("B-pinky03.R", "RightHandPinky3");
        map.put("B-thumb01.R", "RightHandThumb1");
        map.put("B-thumb02.R", "RightHandThumb2");
        map.put("B-thumb03.R", "RightHandThumb3");
        map.put("B-thigh.L", "LeftUpLeg");
        map.put("B-shin.L", "LeftLeg");
        map.put("B-foot.L", "LeftFoot");
        map.put("B-toe.L", "LeftToeBase");
        map.put("B-thigh.R", "RightUpLeg");
        map.put("B-shin.R", "RightLeg");
        map.put("B-foot.R", "RightFoot");
        map.put("B-toe.R", "RightToeBase");
        return map;
    }

    /**
     * Returns the split chain mapping for bones that map one source bone
     * to multiple target bones (e.g. B-chest → Spine1 + Spine2).
     */
    public static Map<String, String[]> blenderSplitChains() {
        return Map.of("B-chest", new String[]{"Spine1", "Spine2"});
    }

    /**
     * Returns the bone name mapping from the SOMA mocap skeleton (used by
     * Kimodo BVH exports) to the engine's HumanIK target convention.
     *
     * <p>SOMA's spine has 4 segments (Spine1 → Spine2 → Chest → Neck1), one
     * more than HumanIK's (Spine → Spine1 → Spine2). The chain shifts down
     * by one slot: SOMA Spine1→Spine, Spine2→Spine1, Chest→Spine2.
     *
     * <p>SOMA's two-segment neck (Neck1+Neck2) collapses to a single Neck;
     * Neck2 is dropped (most clips concentrate motion in Neck1). SOMA's
     * 4-phalanx fingers drop the 4th segment to match HumanIK's 3.
     *
     * <p>SOMA's leg chain uses {@code LeftLeg}/{@code LeftShin} where the
     * upper leg is named {@code LeftLeg} — this is a renaming pitfall, not
     * a structural mismatch.
     */
    public static Map<String, String> somaToHumanIK() {
        Map<String, String> map = new HashMap<>();

        // Spine: shift SOMA's 4 segments down to HumanIK's 3.
        map.put("Hips", "Hips");
        map.put("Spine1", "Spine");
        map.put("Spine2", "Spine1");
        map.put("Chest", "Spine2");

        // Neck/head: SOMA Neck1+Neck2 collapse to a single Neck (Neck2 dropped).
        map.put("Neck1", "Neck");
        map.put("Head", "Head");

        // Arms
        map.put("LeftShoulder", "LeftShoulder");
        map.put("LeftArm", "LeftArm");
        map.put("LeftForeArm", "LeftForeArm");
        map.put("LeftHand", "LeftHand");
        map.put("RightShoulder", "RightShoulder");
        map.put("RightArm", "RightArm");
        map.put("RightForeArm", "RightForeArm");
        map.put("RightHand", "RightHand");

        // Fingers (SOMA's 4th phalanx and *End markers dropped)
        for (String side : new String[]{"Left", "Right"}) {
            for (String finger : new String[]{"Thumb", "Index", "Middle", "Ring", "Pinky"}) {
                for (int i = 1; i <= 3; i++) {
                    String name = side + "Hand" + finger + i;
                    map.put(name, name);
                }
            }
        }

        // Legs: SOMA "LeftLeg" is the upper leg; "LeftShin" is the lower leg.
        map.put("LeftLeg", "LeftUpLeg");
        map.put("LeftShin", "LeftLeg");
        map.put("LeftFoot", "LeftFoot");
        map.put("LeftToeBase", "LeftToeBase");
        map.put("RightLeg", "RightUpLeg");
        map.put("RightShin", "RightLeg");
        map.put("RightFoot", "RightFoot");
        map.put("RightToeBase", "RightToeBase");

        return map;
    }

    /**
     * SOMA has no split-chain bones — every source bone maps 1:1 (or is
     * dropped). Returned for symmetry with {@link #blenderSplitChains()}.
     */
    public static Map<String, String[]> somaSplitChains() {
        return Map.of();
    }
}
