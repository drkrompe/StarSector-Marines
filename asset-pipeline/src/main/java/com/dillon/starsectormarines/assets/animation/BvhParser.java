package com.dillon.starsectormarines.assets.animation;

import com.dillon.starsectormarines.assets.animation.Animation.KeyFrame;
import com.dillon.starsectormarines.assets.animation.Animation.NodeAnimation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Parses BioVision Hierarchy (.bvh) animation files into the engine's
 * {@link Skeleton} + {@link Animation} pair. Mocap exporters (e.g. Kimodo
 * with the SOMA skeleton) emit BVH directly; this parser is the entry point
 * for retargeting that data onto a HumanIK-named target skeleton via
 * {@link AnimationRetargeter}.
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li>OFFSET and per-frame position values are passed through in their
 *       raw BVH units (typically cm). The codebase's FBX-extracted
 *       skeletons keep the FBX scale baked into bone rest transforms (also
 *       cm for our human assets), so the retargeter's rest-pose offset math
 *       works as long as source and target share units. Downstream skinning
 *       handles the cm→m conversion via mesh-space transforms.</li>
 *   <li>Rotation channels are composed in the order listed in the CHANNELS
 *       declaration (BVH spec: each channel value is a rotation about the
 *       named axis, applied in the order written; the resulting local
 *       rotation is {@code q1 * q2 * q3}).</li>
 *   <li>Position keys, when present, replace the joint's rest-pose translation
 *       per frame (matching the Assimp convention that
 *       {@link AnimationRetargeter} expects).</li>
 *   <li>End Site nodes are kept in the {@link Skeleton} hierarchy (they hold
 *       their parent's tail position) but produce no animation channels.</li>
 * </ul>
 */
public class BvhParser {

    public record ParsedBvh(Skeleton skeleton, Animation animation) {}

    private enum Channel {
        XPOS, YPOS, ZPOS, XROT, YROT, ZROT;

        boolean isPosition() {
            return this == XPOS || this == YPOS || this == ZPOS;
        }

        boolean isRotation() {
            return this == XROT || this == YROT || this == ZROT;
        }

        static Channel parse(String token) {
            return switch (token) {
                case "Xposition" -> XPOS;
                case "Yposition" -> YPOS;
                case "Zposition" -> ZPOS;
                case "Xrotation" -> XROT;
                case "Yrotation" -> YROT;
                case "Zrotation" -> ZROT;
                default -> throw new IllegalArgumentException("Unknown BVH channel: " + token);
            };
        }
    }

    private static final class JointDef {
        final String name;
        final Vector3f offset; // in meters
        final List<Channel> channels = new ArrayList<>();
        final List<JointDef> children = new ArrayList<>();
        final boolean endSite;

        JointDef(String name, Vector3f offset, boolean endSite) {
            this.name = name;
            this.offset = offset;
            this.endSite = endSite;
        }
    }

    /**
     * Loads and parses a BVH file from the classpath.
     *
     * @param resourcePath classpath-relative path (e.g. {@code "animations/Kimodo/spear-thrust.bvh"}).
     */
    public static ParsedBvh parse(String resourcePath) {
        try (InputStream is = BvhParser.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("BVH not found on classpath: " + resourcePath);
            return parse(is, resourcePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ParsedBvh parse(InputStream input, String name) throws IOException {
        Tokenizer tk = new Tokenizer(input);

        tk.expect("HIERARCHY");
        if (!"ROOT".equals(tk.next())) {
            throw new IllegalStateException("Expected ROOT at start of HIERARCHY");
        }
        JointDef root = readJoint(tk, false);

        tk.expect("MOTION");
        tk.expect("Frames:");
        int numFrames = Integer.parseInt(tk.next());
        tk.expect("Frame");
        tk.expect("Time:");
        double frameTime = Double.parseDouble(tk.next());

        // DFS-flatten joints in the order they appear in the file. Channels
        // in MOTION rows are concatenated in this same order.
        List<JointDef> all = new ArrayList<>();
        flatten(root, all);

        int totalChannels = 0;
        for (JointDef j : all) totalChannels += j.channels.size();

        double[][] frames = new double[numFrames][totalChannels];
        for (int f = 0; f < numFrames; f++) {
            for (int c = 0; c < totalChannels; c++) {
                frames[f][c] = Double.parseDouble(tk.next());
            }
        }

        // Build Skeleton. End Sites are included as bones; the retargeter
        // doesn't iterate them as channels but keeping them preserves the
        // hierarchy for any downstream visualization.
        List<Bone> bones = new ArrayList<>();
        Bone rootBone = buildBoneTree(root, null, bones);

        // Slice channels per joint and produce NodeAnimations.
        List<NodeAnimation> nodeAnims = new ArrayList<>();
        int channelOffset = 0;
        for (JointDef j : all) {
            int nChan = j.channels.size();
            if (nChan == 0) {
                continue;
            }
            nodeAnims.add(buildNodeAnimation(j, frames, channelOffset));
            channelOffset += nChan;
        }

        // Tick = frame index; ticksPerSecond converts to wall time.
        double duration = numFrames - 1;
        double ticksPerSecond = 1.0 / frameTime;

        Animation animation = new Animation(name, duration, ticksPerSecond, nodeAnims);
        return new ParsedBvh(new Skeleton(bones, rootBone), animation);
    }

    private static NodeAnimation buildNodeAnimation(JointDef j, double[][] frames, int channelOffset) {
        int nChan = j.channels.size();
        boolean hasPos = j.channels.stream().anyMatch(Channel::isPosition);
        boolean hasRot = j.channels.stream().anyMatch(Channel::isRotation);

        List<KeyFrame<Vector3f>> posKeys = hasPos ? new ArrayList<>(frames.length) : List.of();
        List<KeyFrame<Quaternionf>> rotKeys = hasRot ? new ArrayList<>(frames.length) : List.of();

        for (int f = 0; f < frames.length; f++) {
            double time = f;
            if (hasPos) {
                float px = 0f, py = 0f, pz = 0f;
                for (int ci = 0; ci < nChan; ci++) {
                    double v = frames[f][channelOffset + ci];
                    switch (j.channels.get(ci)) {
                        case XPOS -> px = (float) v;
                        case YPOS -> py = (float) v;
                        case ZPOS -> pz = (float) v;
                        default -> { /* rotation handled below */ }
                    }
                }
                posKeys.add(new KeyFrame<>(time, new Vector3f(px, py, pz)));
            }
            if (hasRot) {
                Quaternionf q = new Quaternionf();
                for (int ci = 0; ci < nChan; ci++) {
                    Channel ch = j.channels.get(ci);
                    if (!ch.isRotation()) continue;
                    float radians = (float) Math.toRadians(frames[f][channelOffset + ci]);
                    Quaternionf step = switch (ch) {
                        case XROT -> new Quaternionf().rotationX(radians);
                        case YROT -> new Quaternionf().rotationY(radians);
                        case ZROT -> new Quaternionf().rotationZ(radians);
                        default -> new Quaternionf();
                    };
                    q.mul(step);
                }
                rotKeys.add(new KeyFrame<>(time, q));
            }
        }

        return new NodeAnimation(j.name, posKeys, rotKeys, List.of());
    }

    private static JointDef readJoint(Tokenizer tk, boolean endSite) throws IOException {
        // Caller has consumed the ROOT/JOINT/End keyword. For End Site, the
        // next two tokens are "Site {"; for ROOT/JOINT it's "<name> {".
        String name;
        if (endSite) {
            tk.expect("Site");
            name = "EndSite"; // overwritten below to make end-site names unique-ish
        } else {
            name = tk.next();
        }
        tk.expect("{");
        tk.expect("OFFSET");
        Vector3f offset = new Vector3f(
            Float.parseFloat(tk.next()),
            Float.parseFloat(tk.next()),
            Float.parseFloat(tk.next())
        );

        JointDef joint = new JointDef(name, offset, endSite);

        if (endSite) {
            tk.expect("}");
            return joint;
        }

        tk.expect("CHANNELS");
        int nChan = Integer.parseInt(tk.next());
        for (int i = 0; i < nChan; i++) {
            joint.channels.add(Channel.parse(tk.next()));
        }

        while (true) {
            String t = tk.next();
            switch (t) {
                case "JOINT" -> joint.children.add(readJoint(tk, false));
                case "End" -> {
                    JointDef child = readJoint(tk, true);
                    // Disambiguate end-site names by parent so the Skeleton
                    // bone list keeps unique names (Bone uses name as a key
                    // in retargeting maps).
                    joint.children.add(new JointDef(joint.name + "_End", child.offset, true));
                }
                case "}" -> { return joint; }
                default -> throw new IllegalStateException("Unexpected token in joint body: " + t);
            }
        }
    }

    private static void flatten(JointDef joint, List<JointDef> out) {
        out.add(joint);
        for (JointDef c : joint.children) flatten(c, out);
    }

    private static Bone buildBoneTree(JointDef joint, Bone parent, List<Bone> bones) {
        Matrix4f local = new Matrix4f().translation(joint.offset);
        // BVH carries no skinning data; offsetMatrix (inverse bind) is
        // unused by the retargeter for source skeletons, so identity is fine.
        Matrix4f offsetMatrix = new Matrix4f();
        Bone bone = new Bone(bones.size(), joint.name, offsetMatrix, local);
        bones.add(bone);
        if (parent != null) parent.addChild(bone);
        for (JointDef c : joint.children) buildBoneTree(c, bone, bones);
        return bone;
    }

    /** Whitespace-separated tokenizer that streams the file lazily. */
    private static final class Tokenizer {
        private final BufferedReader reader;
        private final Deque<String> queue = new ArrayDeque<>();

        Tokenizer(InputStream is) {
            this.reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        }

        String next() throws IOException {
            while (queue.isEmpty()) {
                String line = reader.readLine();
                if (line == null) throw new IllegalStateException("Unexpected end of BVH file");
                for (String tok : line.trim().split("\\s+")) {
                    if (!tok.isEmpty()) queue.add(tok);
                }
            }
            return queue.removeFirst();
        }

        void expect(String s) throws IOException {
            String got = next();
            if (!got.equals(s)) {
                throw new IllegalStateException("Expected '" + s + "' but got '" + got + "'");
            }
        }
    }
}
