package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.GlErrors;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/** Screen-resolution artwork alpha gates light, without repainting any scene content. */
final class NightLightingShader {
    private static final Logger LOG = Global.getLogger(NightLightingShader.class);
    static final String VERTEX_SRC = "#version 120\nvarying vec2 uv; void main(){uv=gl_MultiTexCoord0.xy;gl_Position=ftransform();}\n";
    static final String COVERAGE_FRAGMENT_SRC = "#version 120\nuniform sampler2D artwork; varying vec2 uv; void main(){gl_FragColor=vec4(1.0,1.0,1.0,texture2D(artwork,uv).a);}\n";
    static final String FRAGMENT_SRC = "#version 120\nuniform sampler2D fogShade; uniform sampler2D litShade; uniform sampler2D coverage; varying vec2 uv; void main(){gl_FragColor=mix(texture2D(fogShade,uv),texture2D(litShade,uv),texture2D(coverage,uv).a);}\n";
    private final ShaderProgram program = new ShaderProgram("NightLighting", VERTEX_SRC, FRAGMENT_SRC);
    private final ShaderProgram coverageProgram = new ShaderProgram("NightCoverage", VERTEX_SRC, COVERAGE_FRAGMENT_SRC);
    private final SolidQuadBatch batch = new SolidQuadBatch(4096);
    private final int[] fbos = new int[3], textures = new int[3];
    private int width, height;
    private boolean broken;

    ShaderProgram program() { return program; }
    ShaderProgram coverageProgram() { return coverageProgram; }

    void draw(NightLightingPass pass) {
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int oldMatrix = glGetInteger(GL_MATRIX_MODE);
        int oldFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int oldArray = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int oldElements = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        // Each used texture unit has independent binding and texture matrix state.
        int[] bindings = new int[3];
        for (int i = 0; i < 3; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            bindings[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            glMatrixMode(GL_TEXTURE); glPushMatrix(); glLoadIdentity();
        }
        glActiveTexture(GL_TEXTURE0);
        boolean composite = false;
        GlErrors.clear();
        try {
            glDisable(GL_CULL_FACE); glDisable(GL_LIGHTING); glDisable(GL_FOG);
            glDisable(GL_STENCIL_TEST); glDisable(GL_COLOR_LOGIC_OP);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            if (!broken && program.ensure() && coverageProgram.ensure()) {
                float scaleX = Display.isCreated() ? (float) Display.getWidth() / Global.getSettings().getScreenWidth() : 1f;
                float scaleY = Display.isCreated() ? (float) Display.getHeight() / Global.getSettings().getScreenHeight() : 1f;
                int w = Math.max(1, (int)Math.ceil(pass.viewportWidth() * scaleX));
                int h = Math.max(1, (int)Math.ceil(pass.viewportHeight() * scaleY));
                ensureTargets(w, h);
                renderTarget(pass, 0, () -> drawMesh(pass.fogShade(), true));
                renderTarget(pass, 1, () -> drawMesh(pass.litShade(), true));
                renderTarget(pass, 2, () -> drawCoverage(pass));
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldFbo);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldReadFbo);
                // Restore the incoming viewport and scissor before the final screen draw.
                glPopAttrib(); glPushAttrib(GL_ALL_ATTRIB_BITS);
                glColorMask(true, true, true, true); glDisable(GL_DEPTH_TEST);
                glDisable(GL_CULL_FACE); glDisable(GL_LIGHTING); glDisable(GL_FOG);
                glDisable(GL_STENCIL_TEST); glDisable(GL_COLOR_LOGIC_OP);
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                glDisable(GL_ALPHA_TEST); glEnable(GL_BLEND); glBlendEquation(GL_FUNC_ADD); glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                program.use();
                program.set1i("fogShade", 0); program.set1i("litShade", 1); program.set1i("coverage", 2);
                for (int i = 0; i < 3; i++) { glActiveTexture(GL_TEXTURE0 + i); glBindTexture(GL_TEXTURE_2D, textures[i]); }
                glActiveTexture(GL_TEXTURE0);
                float x = pass.viewportX(), y = pass.viewportY();
                glBegin(GL_QUADS);
                glTexCoord2f(0,0); glVertex2f(x,y);
                glTexCoord2f(1,0); glVertex2f(x+pass.viewportWidth(),y);
                glTexCoord2f(1,1); glVertex2f(x+pass.viewportWidth(),y+pass.viewportHeight());
                glTexCoord2f(0,1); glVertex2f(x,y+pass.viewportHeight());
                glEnd();
                composite = true;
            }
        } catch (RuntimeException failure) {
            broken = true;
            LOG.error("Night lighting coverage failed; using fog-clipped darkness", failure);
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldFbo);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldReadFbo);
            glBindBuffer(GL_ARRAY_BUFFER, oldArray); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, oldElements);
            glUseProgram(oldProgram);
            for (int i = 2; i >= 0; i--) {
                glActiveTexture(GL_TEXTURE0+i); glBindTexture(GL_TEXTURE_2D,bindings[i]);
                glMatrixMode(GL_TEXTURE); glPopMatrix();
            }
            glActiveTexture(oldActive); glMatrixMode(oldMatrix);
            glPopAttrib();
        }
        if (!composite) {
            glPushAttrib(GL_ALL_ATTRIB_BITS);
            try {
                glUseProgram(0); glColorMask(true,true,true,true); glDisable(GL_DEPTH_TEST);
                glDisable(GL_CULL_FACE); glDisable(GL_LIGHTING); glDisable(GL_FOG);
                glDisable(GL_STENCIL_TEST); glDisable(GL_COLOR_LOGIC_OP);
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                glDisable(GL_ALPHA_TEST); glEnable(GL_BLEND); glBlendEquation(GL_FUNC_ADD); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                drawMesh(pass.fogShade(), false);
            } finally { glBindBuffer(GL_ARRAY_BUFFER,oldArray); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,oldElements); glUseProgram(oldProgram); glPopAttrib(); }
        }
    }

    private void renderTarget(NightLightingPass pass, int target, Runnable draw) {
        int matrix = glGetInteger(GL_MATRIX_MODE);
        glMatrixMode(GL_PROJECTION); glPushMatrix();
        glMatrixMode(GL_MODELVIEW); glPushMatrix();
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbos[target]); glViewport(0,0,width,height);
            glDisable(GL_SCISSOR_TEST); glDisable(GL_DEPTH_TEST); glDisable(GL_ALPHA_TEST);
            glColorMask(true,true,true,true); glClearColor(0,0,0,0); glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(0);
            glMatrixMode(GL_PROJECTION); glLoadIdentity();
            glOrtho(pass.viewportX(),pass.viewportX()+pass.viewportWidth(),pass.viewportY(),pass.viewportY()+pass.viewportHeight(),-1,1);
            glMatrixMode(GL_MODELVIEW); glLoadIdentity();
            draw.run();
            if (GlErrors.check("night lighting target")) throw new IllegalStateException("Night target draw failed");
        } finally {
            glMatrixMode(GL_MODELVIEW); glPopMatrix(); glMatrixMode(GL_PROJECTION); glPopMatrix(); glMatrixMode(matrix);
        }
    }

    private void drawMesh(PolyMesh mesh, boolean premultiplied) {
        if (premultiplied) glDisable(GL_BLEND);
        glUseProgram(0);
        int units = glGetInteger(GL_MAX_TEXTURE_UNITS);
        for (int unit = 0; unit < units; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit); glDisable(GL_TEXTURE_2D);
            glDisable(GL_TEXTURE_1D); glDisable(GL_TEXTURE_3D); glDisable(GL_TEXTURE_CUBE_MAP);
        }
        glActiveTexture(GL_TEXTURE0);
        for (int i=0;i<mesh.quadCount();i++) {
            float a=mesh.alpha(i), factor=premultiplied ? a : 1f;
            batch.appendQuad(mesh.vertexX(i,0),mesh.vertexY(i,0),mesh.vertexX(i,1),mesh.vertexY(i,1),
                    mesh.vertexX(i,2),mesh.vertexY(i,2),mesh.vertexX(i,3),mesh.vertexY(i,3),
                    mesh.red(i)*factor,mesh.green(i)*factor,mesh.blue(i)*factor,a);
        }
        batch.flush();
    }

    private void drawCoverage(NightLightingPass pass) {
        glEnable(GL_TEXTURE_2D); glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
        coverageProgram.use(); coverageProgram.set1i("artwork",0);
        DrawList list=pass.surfaces();
        for (int i=0;i<list.count(RenderLayer.FOG);i++) {
            DrawCommand c=list.buffer(RenderLayer.FOG)[i];
            c.sprite().bindTexture();
            // Sprite content size and normalized POT extent follow QuadBatch's addressing.
            float u0=c.sourceX()/c.sprite().getWidth()*c.sprite().getTextureWidth();
            float u1=(c.sourceX()+c.sourceWidth())/c.sprite().getWidth()*c.sprite().getTextureWidth();
            float v0=c.sprite().getTextureHeight()-c.sourceY()/c.sprite().getHeight()*c.sprite().getTextureHeight();
            float v1=c.sprite().getTextureHeight()-(c.sourceY()+c.sourceHeight())/c.sprite().getHeight()*c.sprite().getTextureHeight();
            if(c.flippedVertically()){float swap=v0;v0=v1;v1=swap;}
            double angle=Math.toRadians(c.angleDegrees()); float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
            glBegin(GL_QUADS);
            for(int corner=0;corner<4;corner++) {
                glTexCoord2f(corner==0||corner==3 ? u0:u1,corner<2?v1:v0);
                float localX=(corner==0||corner==3 ? -.5f : .5f)*c.width();
                float localY=(corner<2 ? -.5f : .5f)*c.height();
                glVertex2f(c.centerX()+localX*cos-localY*sin,c.centerY()+localX*sin+localY*cos);
            }
            glEnd();
        }
    }

    private void ensureTargets(int w,int h) {
        if(w>8192||h>8192)throw new IllegalArgumentException("Night viewport exceeds target limit");
        if(width==w&&height==h&&fbos[0]!=0)return;
        releaseTargets(); width=w;height=h;
        for(int i=0;i<3;i++) {
            textures[i]=glGenTextures(); glBindTexture(GL_TEXTURE_2D,textures[i]);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            fbos[i]=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbos[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[i],0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete night target");
            if (GlErrors.check("night target allocation")) throw new IllegalStateException("Night target allocation failed");
        }
    }
    private void releaseTargets(){for(int i=0;i<3;i++){if(fbos[i]!=0)glDeleteFramebuffers(fbos[i]);if(textures[i]!=0)glDeleteTextures(textures[i]);fbos[i]=textures[i]=0;}}
    void dispose(){releaseTargets();program.dispose();coverageProgram.dispose();broken=false;width=height=0;}
}
