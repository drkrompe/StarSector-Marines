package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.FurnishableRoom;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFloor;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.ShipGymFitting;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

/** Real-driver acceptance: the gym pool moves, dry deck and the hull void do not. */
@Tag("shader-evidence")
class ShipGymWaterGlEvidence {

    private static final int CELLS_W = 18;
    private static final int CELLS_H = 14;
    private static final int CELL_PX = 48;
    private static final int SURFACE_W = CELLS_W * CELL_PX;
    private static final int SURFACE_H = CELLS_H * CELL_PX;

    private static final EnumSet<RenderLayer> GROUND_ONLY = EnumSet.of(RenderLayer.GROUND);

    @Test
    void poolAnimatesThroughTheDeckHostWithoutMovingDryFloorOrFillingVoid() throws Exception {
        try(HeadlessGl gl=HeadlessGl.createOrNull(SURFACE_W,SURFACE_H)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(gl!=null,HeadlessGl.unavailableReason());
            System.out.println("[gym-water] context: "+gl.rendererDescription());
            Global.setSettings(screenSettings());
            int[] target=null; BattleRenderer renderer=null;
            try(GlSpriteTokens tokens=new GlSpriteTokens()) {
                DiskRegistries.installMapGeneration(Path.of(".").toAbsolutePath());
                target=target();
                renderer=new BattleRenderer(new HeadlessBattleSprites(Path.of("mod").toAbsolutePath(),tokens));
                renderer.onAttach();renderer.buildTileBatches();
                try(ShipDeckBattleScene scene=new ShipDeckBattleScene(terrain(),7)) {
                    var pass=scene.pass(new ShipDeckBattleScene.DeckView(CELLS_W*.5f,CELLS_H*.5f,CELL_PX),GROUND_ONLY);
                    var viewport=new CanvasHostViewport(0,0,SURFACE_W,SURFACE_H,SURFACE_W,SURFACE_H);
                    scene.advanceTo(1f);
                    RenderContext firstContext=pass.prepare(viewport,1f).context();
                    assertTrue(GroundParallaxPipeline.deckWaterVisible(firstContext));
                    draw(target[0],renderer,firstContext);
                    byte[] first=draw(target[0],renderer,firstContext);
                    assertArrayEquals(first,draw(target[0],renderer,pass.prepare(viewport,1f).context()),
                            "repeated passes at one deck time must agree");
                    scene.advanceTo(1.5f);
                    byte[] next=draw(target[0],renderer,pass.prepare(viewport,1f).context());
                    int changedWater=0,changedDry=0;
                    for(int y=0;y<SURFACE_H;y++) for(int x=0;x<SURFACE_W;x++) {
                        int i=(y*SURFACE_W+x)*4;
                        boolean changed=false;for(int c=0;c<3;c++) changed|=first[i+c]!=next[i+c];
                        boolean water=x>=10*CELL_PX && x<14*CELL_PX && y>=5*CELL_PX && y<9*CELL_PX;
                        if(changed) {if(water) changedWater++;else changedDry++;}
                    }
                    assertTrue(changedWater>100,"water must visibly move, including through the deck host");
                    assertEquals(0,changedDry,"water must not animate the coping, dry floor or hull void");
                    assertEquals(255,first[0]&255);assertEquals(0,first[1]&255);assertEquals(255,first[2]&255);
                    save(first,"gym-water-0.png");save(next,"gym-water-1.png");
                    System.out.println("[gym-water] changed water pixels="+changedWater+", dry="+changedDry);
                }
            } finally {
                if(renderer!=null) {renderer.getGroundParallax().dispose();renderer.getGroundAtlas().dispose();renderer.getGroundMesh().dispose();}
                if(target!=null) {glDeleteFramebuffers(target[0]);glDeleteTextures(target[1]);}
                Global.setSettings(null);
            }
        }
    }
    private static void save(byte[] pixels,String name) throws Exception {
        var image=new java.awt.image.BufferedImage(SURFACE_W,SURFACE_H,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<SURFACE_H;y++) for(int x=0;x<SURFACE_W;x++) {
            int i=(y*SURFACE_W+x)*4;
            image.setRGB(x,SURFACE_H-1-y,0xff000000|(pixels[i]&255)<<16|(pixels[i+1]&255)<<8|(pixels[i+2]&255));
        }
        var dir=Path.of("build/surface-relief");java.nio.file.Files.createDirectories(dir);
        javax.imageio.ImageIO.write(image,"png",dir.resolve(name).toFile());
    }

    /** Draws the GROUND layer over a known clear and reads the pixels back. */
    private static byte[] draw(int fbo, BattleRenderer renderer, RenderContext context) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, SURFACE_W, SURFACE_H);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, SURFACE_W, 0, SURFACE_H, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glClearColor(1f, 0f, 1f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        renderer.renderWorld(context, GROUND_ONLY, null);

        ByteBuffer pixels = BufferUtils.createByteBuffer(SURFACE_W * SURFACE_H * 4);
        glReadPixels(0, 0, SURFACE_W, SURFACE_H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        byte[] read = new byte[SURFACE_W * SURFACE_H * 4];
        pixels.get(read);
        return read;
    }

    // ---- the world -----------------------------------------------------------

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors) implements FurnishableRoom {}

    private static MapResult terrain() {
        NavigationGrid grid=new NavigationGrid(CELLS_W,CELLS_H);
        CellTopology topology=new CellTopology(CELLS_W,CELLS_H);
        for(int y=0;y<CELLS_H;y++) for(int x=0;x<CELLS_W;x++)
            topology.setGroundKind(x,y,CellTopology.GroundKind.VOID);
        for(int y=2;y<12;y++) for(int x=2;x<16;x++) {
            grid.setWalkableFloor(x,y);topology.setGroundKind(x,y,CellTopology.GroundKind.INDOOR);
            topology.setSurface(x,y,topology.surfaceIndex("ship.crew-deck"));
        }
        var ctx=new GenContext(grid,topology,new Random(7),CELLS_W,CELLS_H,7);
        var shape=RoomShape.rectangle(14,10);
        var floor=new RoomFloor(ctx,new Room(shape,2,2,RoomPose.CANONICAL,
                RoomPurpose.GYMNASIUM,List.of()),RoomFit.STANDARD);
        new ShipGymFitting().fit(floor);floor.seal();
        return new MapResult(grid,topology,2,2,15,11,List.of(),ctx.doodads);
    }

    // ---- GL scaffolding ------------------------------------------------------

    /** {@code {fbo, texture}} at the surface size. */
    private static int[] target() {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SURFACE_W, SURFACE_H, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);

        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return new int[]{fbo, texture};
    }

    /** Just enough {@link SettingsAPI} that renderer construction has a game to ask. */
    private static SettingsAPI screenSettings() {
        return (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getScreenWidth" -> (float) SURFACE_W;
                    case "getScreenHeight" -> (float) SURFACE_H;
                    case "getScreenScaleMult" -> 1f;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "HeadlessSettings";
                    default -> HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                });
    }
}
