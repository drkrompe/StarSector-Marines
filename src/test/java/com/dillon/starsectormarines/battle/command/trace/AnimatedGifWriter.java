package com.dillon.starsectormarines.battle.command.trace;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/** Streaming, dependency-free animated GIF encoder for visual evidence. */
final class AnimatedGifWriter implements AutoCloseable {
    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final ImageOutputStream output;
    private final int delayCentiseconds;
    private boolean firstFrame = true;
    private boolean closed;

    AnimatedGifWriter(Path path, int frameDelayMillis) throws IOException {
        if (frameDelayMillis < 10) {
            throw new IllegalArgumentException(
                    "GIF frame delay must be at least 10 milliseconds");
        }
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No GIF ImageIO writer is installed");
        }
        writer = writers.next();
        params = writer.getDefaultWriteParam();
        output = ImageIO.createImageOutputStream(path.toFile());
        if (output == null) {
            writer.dispose();
            throw new IOException("Could not open GIF output " + path);
        }
        delayCentiseconds = Math.max(1,
                Math.round(frameDelayMillis / 10f));
        writer.setOutput(output);
        writer.prepareWriteSequence(null);
    }

    void append(BufferedImage image) throws IOException {
        if (closed) throw new IllegalStateException("GIF writer is closed");
        ImageTypeSpecifier type = ImageTypeSpecifier
                .createFromRenderedImage(image);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, params);
        configure(metadata, firstFrame);
        writer.writeToSequence(new IIOImage(image, null, metadata), params);
        firstFrame = false;
    }

    private void configure(IIOMetadata metadata, boolean loop) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(delayCentiseconds));
        control.setAttribute("transparentColorIndex", "0");
        if (loop) {
            IIOMetadataNode extensions = child(root, "ApplicationExtensions");
            IIOMetadataNode extension = new IIOMetadataNode("ApplicationExtension");
            extension.setAttribute("applicationID", "NETSCAPE");
            extension.setAttribute("authenticationCode", "2.0");
            extension.setUserObject(new byte[]{1, 0, 0});
            extensions.appendChild(extension);
        }
        metadata.setFromTree(format, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int index = 0; index < root.getLength(); index++) {
            Node node = root.item(index);
            if (name.equals(node.getNodeName())) {
                return (IIOMetadataNode) node;
            }
        }
        IIOMetadataNode result = new IIOMetadataNode(name);
        root.appendChild(result);
        return result;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try {
            writer.endWriteSequence();
        } catch (IOException ex) {
            failure = ex;
        }
        try {
            output.close();
        } catch (IOException ex) {
            if (failure == null) failure = ex;
            else failure.addSuppressed(ex);
        } finally {
            writer.dispose();
        }
        if (failure != null) throw failure;
    }
}
