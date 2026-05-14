package com.dillon.starsectormarines.render;

/**
 * Column-major 4x4 matrix helpers stored as {@code float[16]}.
 * Layout: index = col*4 + row, so {@code m[12..14]} is the translation column.
 * Allocates fresh arrays per call — fine at PoC scale; revisit if profiling shows pressure.
 */
public final class Mat4 {

    private Mat4() {}

    public static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    public static float[] perspective(float fovYDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) * 0.5));
        float[] m = new float[16];
        m[0]  = f / aspect;
        m[5]  = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
        return m;
    }

    public static float[] translation(float x, float y, float z) {
        float[] m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    public static float[] scaling(float x, float y, float z) {
        float[] m = new float[16];
        m[0]  = x;
        m[5]  = y;
        m[10] = z;
        m[15] = 1f;
        return m;
    }

    public static float[] rotationX(float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float[] m = identity();
        m[5] =  c;  m[6]  = s;
        m[9] = -s;  m[10] = c;
        return m;
    }

    public static float[] rotationY(float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float[] m = identity();
        m[0] = c;  m[2]  = -s;
        m[8] = s;  m[10] =  c;
        return m;
    }

    public static float[] rotationZ(float rad) {
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float[] m = identity();
        m[0] =  c;  m[1] = s;
        m[4] = -s;  m[5] = c;
        return m;
    }

    public static float[] mul(float[] a, float[] b) {
        float[] c = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                c[col * 4 + row] = sum;
            }
        }
        return c;
    }

    /** Right-handed lookAt matching gluLookAt / GL convention (looks down -Z). */
    public static float[] lookAt(float ex, float ey, float ez,
                                 float tx, float ty, float tz,
                                 float ux, float uy, float uz) {
        float fx = tx - ex, fy = ty - ey, fz = tz - ez;
        float flen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= flen; fy /= flen; fz /= flen;

        // right = forward × up
        float rx = fy * uz - fz * uy;
        float ry = fz * ux - fx * uz;
        float rz = fx * uy - fy * ux;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rlen; ry /= rlen; rz /= rlen;

        // up' = right × forward (re-orthogonalize)
        float upx = ry * fz - rz * fy;
        float upy = rz * fx - rx * fz;
        float upz = rx * fy - ry * fx;

        float[] m = new float[16];
        m[0]  = rx;   m[4] = ry;   m[8]  = rz;   m[12] = -(rx * ex + ry * ey + rz * ez);
        m[1]  = upx;  m[5] = upy;  m[9]  = upz;  m[13] = -(upx * ex + upy * ey + upz * ez);
        m[2]  = -fx;  m[6] = -fy;  m[10] = -fz;  m[14] = fx * ex + fy * ey + fz * ez;
        m[15] = 1f;
        return m;
    }
}
