package com.ayden.itemexporter.client;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ImageExporter {
    private ImageExporter() {}

    public static void write(List<GifFrame> frames, Path output, boolean gif) throws IOException {
        if (frames.isEmpty()) throw new IOException("No frames were captured.");
        Path parent = output.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (gif) writeGif(frames, output, 0, 0);
        else if (!ImageIO.write(frames.getFirst().image(), "png", output.toFile()))
            throw new IOException("No PNG writer is available.");
    }

    public static void writeItemIcon(List<GifFrame> frames, Path output, boolean gif) throws IOException {
        if (frames.isEmpty()) throw new IOException("No frames were captured.");
        Path parent = output.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (gif) writeGif(frames, output, 200, 200);
        else if (!ImageIO.write(scaleNearest(frames.getFirst().image(), 200, 200), "png", output.toFile()))
            throw new IOException("No PNG writer is available.");
    }

    private static BufferedImage scaleNearest(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int sy = Math.min(source.getHeight() - 1, (int) ((long) y * source.getHeight() / height));
            for (int x = 0; x < width; x++) {
                int sx = Math.min(source.getWidth() - 1, (int) ((long) x * source.getWidth() / width));
                target.setRGB(x, y, source.getRGB(sx, sy));
            }
        }
        return target;
    }

    private static void writeGif(List<GifFrame> frames, Path output, int targetWidth, int targetHeight) throws IOException {
        Palette palette = createPalette(frames);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) throw new IOException("No GIF writer is available in this Java runtime.");
        ImageWriter writer = writers.next();
        try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(outputStream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(parameters);
            configureGifStreamMetadata(streamMetadata, palette.colorModel());
            writer.prepareWriteSequence(streamMetadata);
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = targetWidth > 0 && targetHeight > 0
                        ? scaleNearest(frames.get(i).image(), targetWidth, targetHeight) : frames.get(i).image();
                BufferedImage indexed = toIndexed(frame, palette);
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(indexed);
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, parameters);
                configureGifMetadata(metadata, i == 0, frames.get(i).delayCentiseconds());
                writer.writeToSequence(new IIOImage(indexed, null, metadata), parameters);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static Palette createPalette(List<GifFrame> frames) {
        Map<Integer, Integer> histogram = new HashMap<>();
        for (GifFrame captured : frames) {
            BufferedImage frame = captured.image();
            for (int y = 0; y < frame.getHeight(); y++) for (int x = 0; x < frame.getWidth(); x++) {
                int argb = frame.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) > 16) histogram.merge(argb & 0x00FFFFFF, 1, Integer::sum);
            }
        }
        List<Map.Entry<Integer, Integer>> colors = histogram.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(255).toList();
        byte[] red = new byte[256], green = new byte[256], blue = new byte[256];
        int[] paletteRgb = new int[Math.max(colors.size(), 1)];
        for (int i = 0; i < colors.size(); i++) {
            int rgb = colors.get(i).getKey(), index = i + 1;
            red[index] = (byte) ((rgb >>> 16) & 255);
            green[index] = (byte) ((rgb >>> 8) & 255);
            blue[index] = (byte) (rgb & 255);
            paletteRgb[i] = rgb;
        }
        if (colors.isEmpty()) {
            red[1] = (byte) 255; green[1] = (byte) 255; blue[1] = (byte) 255; paletteRgb[0] = 0xFFFFFF;
        }
        byte[] nearest = new byte[32 * 32 * 32];
        for (int r = 0; r < 32; r++) for (int g = 0; g < 32; g++) for (int b = 0; b < 32; b++) {
            int rr = (r << 3) | 4, gg = (g << 3) | 4, bb = (b << 3) | 4;
            long bestDistance = Long.MAX_VALUE;
            int bestIndex = 1;
            for (int p = 0; p < paletteRgb.length; p++) {
                int rgb = paletteRgb[p];
                int dr = rr - ((rgb >>> 16) & 255), dg = gg - ((rgb >>> 8) & 255), db = bb - (rgb & 255);
                long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
                if (distance < bestDistance) { bestDistance = distance; bestIndex = p + 1; }
            }
            nearest[(r << 10) | (g << 5) | b] = (byte) bestIndex;
        }
        return new Palette(new IndexColorModel(8, 256, red, green, blue, 0), nearest);
    }

    private static BufferedImage toIndexed(BufferedImage source, Palette palette) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_BYTE_INDEXED, palette.colorModel());
        byte[] pixels = ((DataBufferByte) target.getRaster().getDataBuffer()).getData();
        int offset = 0;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int argb = source.getRGB(x, y);
            if (((argb >>> 24) & 255) <= 16) pixels[offset++] = 0;
            else {
                int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
                pixels[offset++] = palette.nearest()[((r >>> 3) << 10) | ((g >>> 3) << 5) | (b >>> 3)];
            }
        }
        return target;
    }

    private static void configureGifStreamMetadata(IIOMetadata metadata, IndexColorModel colorModel) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode table = getOrCreate(root, "GlobalColorTable");
        table.setAttribute("sizeOfGlobalColorTable", "256");
        table.setAttribute("backgroundColorIndex", "0");
        table.setAttribute("sortFlag", "FALSE");
        while (table.hasChildNodes()) table.removeChild(table.getFirstChild());
        for (int i = 0; i < 256; i++) {
            IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
            entry.setAttribute("index", Integer.toString(i));
            entry.setAttribute("red", Integer.toString(colorModel.getRed(i)));
            entry.setAttribute("green", Integer.toString(colorModel.getGreen(i)));
            entry.setAttribute("blue", Integer.toString(colorModel.getBlue(i)));
            table.appendChild(entry);
        }
        metadata.setFromTree(format, root);
    }

    private static void configureGifMetadata(IIOMetadata metadata, boolean firstFrame, int delayCentiseconds) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        for (int i = root.getLength() - 1; i >= 0; i--)
            if ("LocalColorTable".equals(root.item(i).getNodeName())) root.removeChild(root.item(i));
        IIOMetadataNode control = getOrCreate(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "restoreToBackgroundColor");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "TRUE");
        control.setAttribute("delayTime", Integer.toString(Math.max(1, Math.min(65535, delayCentiseconds))));
        control.setAttribute("transparentColorIndex", "0");
        if (firstFrame) {
            IIOMetadataNode extensions = getOrCreate(root, "ApplicationExtensions");
            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            loop.setUserObject(new byte[]{0x1, 0x0, 0x0});
            extensions.appendChild(loop);
        }
        metadata.setFromTree(format, root);
    }

    private static IIOMetadataNode getOrCreate(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++)
            if (name.equals(root.item(i).getNodeName())) return (IIOMetadataNode) root.item(i);
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    public record GifFrame(BufferedImage image, int delayCentiseconds) {
        public GifFrame {
            if (image == null) throw new IllegalArgumentException("Frame image cannot be null.");
            if (delayCentiseconds < 1) delayCentiseconds = 1;
        }
    }
    private record Palette(IndexColorModel colorModel, byte[] nearest) {}
}
