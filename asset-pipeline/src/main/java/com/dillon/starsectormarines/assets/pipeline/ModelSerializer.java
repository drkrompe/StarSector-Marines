package com.dillon.starsectormarines.assets.pipeline;

import com.dillon.starsectormarines.assets.LoadedModel;
import com.dillon.starsectormarines.assets.animation.Animation;
import com.dillon.starsectormarines.assets.animation.Bone;
import com.dillon.starsectormarines.assets.animation.Skeleton;
import com.dillon.starsectormarines.assets.material.MaterialInfo;
import com.dillon.starsectormarines.assets.mesh.MeshData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes and deserializes {@link LoadedModel} to a compact binary format (.mlmodel).
 * <p>
 * The format uses a magic number and version header for forward compatibility.
 * This class lives in core so both the asset-pipeline (write) and runtime (read) can use it.
 */
public final class ModelSerializer {

    private static final int MAGIC = 0x4D4C4D44; // "MLMD"
    private static final int VERSION = 2;

    private ModelSerializer() {}

    // ==================== WRITE ====================

    public static void write(LoadedModel model, OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        // Header
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);

        // Materials
        writeMaterials(dos, model.materials());

        // Mesh parts
        writeMeshParts(dos, model.meshDataParts());

        // Skeleton
        writeSkeleton(dos, model.skeleton());

        // Animations
        writeAnimations(dos, model.animations());

        dos.flush();
    }

    private static void writeMaterials(DataOutputStream dos, List<MaterialInfo> materials) throws IOException {
        dos.writeInt(materials.size());
        for (MaterialInfo mat : materials) {
            writeNullableString(dos, mat.name());
            writeNullableString(dos, mat.texturePath());
            dos.writeFloat(mat.r());
            dos.writeFloat(mat.g());
            dos.writeFloat(mat.b());
            dos.writeFloat(mat.a());
            writeNullableByteArray(dos, mat.textureData());
            dos.writeInt(mat.width());
            dos.writeInt(mat.height());
        }
    }

    private static void writeMeshParts(DataOutputStream dos, Map<Integer, MeshData> parts) throws IOException {
        dos.writeInt(parts.size());
        for (Map.Entry<Integer, MeshData> entry : parts.entrySet()) {
            dos.writeInt(entry.getKey()); // instancePayload index
            MeshData mesh = entry.getValue();
            dos.writeInt(mesh.getVertexStride());
            writeFloatArray(dos, mesh.getVertices());
            writeIntArray(dos, mesh.getIndices());
            writeNullableFloatArray(dos, mesh.getWeights());
            writeNullableIntArray(dos, mesh.getJointIndices());
        }
    }

    private static void writeSkeleton(DataOutputStream dos, Skeleton skeleton) throws IOException {
        if (skeleton == null) {
            dos.writeInt(0);
            dos.writeInt(-1);
            return;
        }

        List<Bone> bones = skeleton.getBones();
        dos.writeInt(bones.size());
        for (Bone bone : bones) {
            dos.writeInt(bone.getIndex());
            dos.writeUTF(bone.getName());
            writeMatrix4f(dos, bone.getOffsetMatrix());
            writeMatrix4f(dos, bone.getLocalRestTransform());
            dos.writeInt(bone.getParent() != null ? bone.getParent().getIndex() : -1);
            dos.writeInt(bone.getChildren().size());
            for (Bone child : bone.getChildren()) {
                dos.writeInt(child.getIndex());
            }
        }
        dos.writeInt(skeleton.getRootBone().getIndex());
    }

    private static void writeAnimations(DataOutputStream dos, List<Animation> animations) throws IOException {
        dos.writeInt(animations != null ? animations.size() : 0);
        if (animations == null) return;

        for (Animation anim : animations) {
            dos.writeUTF(anim.getName());
            dos.writeDouble(anim.getDuration());
            dos.writeDouble(anim.getTicksPerSecond());

            List<Animation.NodeAnimation> nodeAnims = anim.getNodeAnimations();
            dos.writeInt(nodeAnims.size());
            for (Animation.NodeAnimation na : nodeAnims) {
                dos.writeUTF(na.getNodeName());

                // Position keys
                dos.writeInt(na.getPositionKeys().size());
                for (Animation.KeyFrame<Vector3f> kf : na.getPositionKeys()) {
                    dos.writeDouble(kf.time());
                    dos.writeFloat(kf.value().x);
                    dos.writeFloat(kf.value().y);
                    dos.writeFloat(kf.value().z);
                }

                // Rotation keys
                dos.writeInt(na.getRotationKeys().size());
                for (Animation.KeyFrame<Quaternionf> kf : na.getRotationKeys()) {
                    dos.writeDouble(kf.time());
                    dos.writeFloat(kf.value().x);
                    dos.writeFloat(kf.value().y);
                    dos.writeFloat(kf.value().z);
                    dos.writeFloat(kf.value().w);
                }

                // Scaling keys
                dos.writeInt(na.getScalingKeys().size());
                for (Animation.KeyFrame<Vector3f> kf : na.getScalingKeys()) {
                    dos.writeDouble(kf.time());
                    dos.writeFloat(kf.value().x);
                    dos.writeFloat(kf.value().y);
                    dos.writeFloat(kf.value().z);
                }
            }
        }
    }

    // ==================== READ ====================

    public static LoadedModel read(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);

        // Header
        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new IOException("Invalid mlmodel file: bad magic 0x" + Integer.toHexString(magic));
        }
        int version = dis.readInt();
        if (version > VERSION) {
            throw new IOException("Unsupported mlmodel version: " + version + " (max supported: " + VERSION + ")");
        }

        // Materials
        List<MaterialInfo> materials = readMaterials(dis);

        // Mesh parts
        Map<Integer, MeshData> meshParts = readMeshParts(dis);

        // Skeleton
        Skeleton skeleton = readSkeleton(dis);

        // Animations
        List<Animation> animations = readAnimations(dis);

        return new LoadedModel(meshParts, materials, skeleton, animations);
    }

    private static List<MaterialInfo> readMaterials(DataInputStream dis) throws IOException {
        int count = dis.readInt();
        List<MaterialInfo> materials = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = readNullableString(dis);
            String texturePath = readNullableString(dis);
            float r = dis.readFloat();
            float g = dis.readFloat();
            float b = dis.readFloat();
            float a = dis.readFloat();
            byte[] textureData = readNullableByteArray(dis);
            int width = dis.readInt();
            int height = dis.readInt();
            materials.add(new MaterialInfo(name, texturePath, r, g, b, a, textureData, width, height));
        }
        return materials;
    }

    private static Map<Integer, MeshData> readMeshParts(DataInputStream dis) throws IOException {
        int count = dis.readInt();
        Map<Integer, MeshData> parts = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            int materialIndex = dis.readInt();
            int vertexStride = dis.readInt();
            float[] vertices = readFloatArray(dis);
            int[] indices = readIntArray(dis);
            float[] weights = readNullableFloatArray(dis);
            int[] jointIndices = readNullableIntArray(dis);

            MeshData mesh = new MeshData(vertices, indices, vertexStride);
            mesh.setWeights(weights);
            mesh.setJointIndices(jointIndices);
            parts.put(materialIndex, mesh);
        }
        return parts;
    }

    private static Skeleton readSkeleton(DataInputStream dis) throws IOException {
        int boneCount = dis.readInt();
        int rootIndex = -1;

        if (boneCount == 0) {
            dis.readInt(); // consume root index marker (-1)
            return null;
        }

        // First pass: create all bones
        Bone[] bones = new Bone[boneCount];
        int[][] childIndices = new int[boneCount][];
        int[] parentIndices = new int[boneCount];

        for (int i = 0; i < boneCount; i++) {
            int index = dis.readInt();
            String name = dis.readUTF();
            Matrix4f offset = readMatrix4f(dis);
            Matrix4f localRest = readMatrix4f(dis);
            parentIndices[i] = dis.readInt();
            int childCount = dis.readInt();
            childIndices[i] = new int[childCount];
            for (int c = 0; c < childCount; c++) {
                childIndices[i][c] = dis.readInt();
            }
            bones[index] = new Bone(index, name, offset, localRest);
        }

        rootIndex = dis.readInt();

        // Second pass: wire parent-child relationships
        for (int i = 0; i < boneCount; i++) {
            for (int childIdx : childIndices[i]) {
                bones[i].addChild(bones[childIdx]);
            }
        }

        List<Bone> boneList = new ArrayList<>(boneCount);
        for (Bone bone : bones) {
            boneList.add(bone);
        }

        return new Skeleton(boneList, bones[rootIndex]);
    }

    private static List<Animation> readAnimations(DataInputStream dis) throws IOException {
        int count = dis.readInt();
        List<Animation> animations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = dis.readUTF();
            double duration = dis.readDouble();
            double ticksPerSecond = dis.readDouble();

            int nodeAnimCount = dis.readInt();
            List<Animation.NodeAnimation> nodeAnims = new ArrayList<>(nodeAnimCount);
            for (int j = 0; j < nodeAnimCount; j++) {
                String nodeName = dis.readUTF();

                // Position keys
                int posCount = dis.readInt();
                List<Animation.KeyFrame<Vector3f>> posKeys = new ArrayList<>(posCount);
                for (int k = 0; k < posCount; k++) {
                    double time = dis.readDouble();
                    posKeys.add(new Animation.KeyFrame<>(time, new Vector3f(dis.readFloat(), dis.readFloat(), dis.readFloat())));
                }

                // Rotation keys
                int rotCount = dis.readInt();
                List<Animation.KeyFrame<Quaternionf>> rotKeys = new ArrayList<>(rotCount);
                for (int k = 0; k < rotCount; k++) {
                    double time = dis.readDouble();
                    rotKeys.add(new Animation.KeyFrame<>(time, new Quaternionf(dis.readFloat(), dis.readFloat(), dis.readFloat(), dis.readFloat())));
                }

                // Scaling keys
                int scaleCount = dis.readInt();
                List<Animation.KeyFrame<Vector3f>> scaleKeys = new ArrayList<>(scaleCount);
                for (int k = 0; k < scaleCount; k++) {
                    double time = dis.readDouble();
                    scaleKeys.add(new Animation.KeyFrame<>(time, new Vector3f(dis.readFloat(), dis.readFloat(), dis.readFloat())));
                }

                nodeAnims.add(new Animation.NodeAnimation(nodeName, posKeys, rotKeys, scaleKeys));
            }
            animations.add(new Animation(name, duration, ticksPerSecond, nodeAnims));
        }
        return animations;
    }

    // ==================== Primitive I/O helpers ====================

    private static void writeNullableString(DataOutputStream dos, String s) throws IOException {
        dos.writeBoolean(s != null);
        if (s != null) dos.writeUTF(s);
    }

    private static String readNullableString(DataInputStream dis) throws IOException {
        return dis.readBoolean() ? dis.readUTF() : null;
    }

    private static void writeNullableByteArray(DataOutputStream dos, byte[] arr) throws IOException {
        dos.writeBoolean(arr != null);
        if (arr != null) {
            dos.writeInt(arr.length);
            dos.write(arr);
        }
    }

    private static byte[] readNullableByteArray(DataInputStream dis) throws IOException {
        if (!dis.readBoolean()) return null;
        int len = dis.readInt();
        byte[] arr = new byte[len];
        dis.readFully(arr);
        return arr;
    }

    private static void writeFloatArray(DataOutputStream dos, float[] arr) throws IOException {
        dos.writeInt(arr.length);
        for (float f : arr) dos.writeFloat(f);
    }

    private static float[] readFloatArray(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        float[] arr = new float[len];
        for (int i = 0; i < len; i++) arr[i] = dis.readFloat();
        return arr;
    }

    private static void writeIntArray(DataOutputStream dos, int[] arr) throws IOException {
        dos.writeInt(arr.length);
        for (int v : arr) dos.writeInt(v);
    }

    private static int[] readIntArray(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) arr[i] = dis.readInt();
        return arr;
    }

    private static void writeNullableFloatArray(DataOutputStream dos, float[] arr) throws IOException {
        dos.writeBoolean(arr != null);
        if (arr != null) writeFloatArray(dos, arr);
    }

    private static float[] readNullableFloatArray(DataInputStream dis) throws IOException {
        return dis.readBoolean() ? readFloatArray(dis) : null;
    }

    private static void writeNullableIntArray(DataOutputStream dos, int[] arr) throws IOException {
        dos.writeBoolean(arr != null);
        if (arr != null) writeIntArray(dos, arr);
    }

    private static int[] readNullableIntArray(DataInputStream dis) throws IOException {
        return dis.readBoolean() ? readIntArray(dis) : null;
    }

    private static void writeMatrix4f(DataOutputStream dos, Matrix4f m) throws IOException {
        float[] buf = new float[16];
        m.get(buf); // column-major
        for (float f : buf) dos.writeFloat(f);
    }

    private static Matrix4f readMatrix4f(DataInputStream dis) throws IOException {
        float[] buf = new float[16];
        for (int i = 0; i < 16; i++) buf[i] = dis.readFloat();
        return new Matrix4f().set(buf);
    }
}
