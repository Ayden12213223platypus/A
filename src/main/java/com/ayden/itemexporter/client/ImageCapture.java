package com.ayden.itemexporter.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;

public final class ImageCapture {
    private static final int BACKGROUND_CHANNEL_TOLERANCE = 24;

    private ImageCapture() {
    }

    public static BufferedImage cropKeyedCanvas(NativeImage source, CaptureScreen screen) {
        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double scaleX = source.getWidth() / (double) guiWidth;
        double scaleY = source.getHeight() / (double) guiHeight;

        int left = (int) Math.floor(screen.canvasX() * scaleX);
        int top = (int) Math.floor(screen.canvasY() * scaleY);
        int right = (int) Math.ceil((screen.canvasX() + screen.canvasWidth()) * scaleX);
        int bottom = (int) Math.ceil((screen.canvasY() + screen.canvasHeight()) * scaleY);
        left = Math.max(0, Math.min(left, source.getWidth() - 1));
        top = Math.max(0, Math.min(top, source.getHeight() - 1));
        right = Math.max(left + 1, Math.min(right, source.getWidth()));
        bottom = Math.max(top + 1, Math.min(bottom, source.getHeight()));

        int cropWidth = right - left;
        int cropHeight = bottom - top;
        BufferedImage cropped = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cropHeight; y++) {
            for (int x = 0; x < cropWidth; x++) {
                // NativeImage.getPixel() already converts its internal ABGR data to ARGB.
                cropped.setRGB(x, y, source.getPixel(left + x, top + y));
            }
        }
        removeBackground(cropped, screen.keyColor());
        return cropped;
    }

    /**
     * Removes the explicit key and any smooth background connected to the crop border.
     * The second pass is a fallback for menu/world pixels that slip past the key fill;
     * the item itself is separated from the crop border by the capture canvas padding.
     */
    private static void removeBackground(BufferedImage image, int key) {
        int width = image.getWidth();
        int height = image.getHeight();
        int count = width * height;
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        boolean[] background = new boolean[count];
        int[] queue = new int[count];
        int head = 0;
        int tail = 0;

        for (int x = 0; x < width; x++) {
            tail = enqueue(x, background, queue, tail);
            tail = enqueue((height - 1) * width + x, background, queue, tail);
        }
        for (int y = 0; y < height; y++) {
            tail = enqueue(y * width, background, queue, tail);
            tail = enqueue(y * width + width - 1, background, queue, tail);
        }

        while (head < tail) {
            int index = queue[head++];
            int x = index % width;
            int y = index / width;
            if (x > 0) tail = enqueueIfBackground(index - 1, pixels[index], pixels,
                    background, queue, tail);
            if (x + 1 < width) tail = enqueueIfBackground(index + 1, pixels[index], pixels,
                    background, queue, tail);
            if (y > 0) tail = enqueueIfBackground(index - width, pixels[index], pixels,
                    background, queue, tail);
            if (y + 1 < height) tail = enqueueIfBackground(index + width, pixels[index], pixels,
                    background, queue, tail);
        }

        // Key-colored pixels are background even if a renderer leaves a tiny isolated patch.
        int keyRed = (key >>> 16) & 0xFF;
        int keyGreen = (key >>> 8) & 0xFF;
        int keyBlue = key & 0xFF;
        for (int i = 0; i < count; i++) {
            int pixel = pixels[i];
            int alpha = (pixel >>> 24) & 0xFF;
            int red = (pixel >>> 16) & 0xFF;
            int green = (pixel >>> 8) & 0xFF;
            int blue = pixel & 0xFF;
            boolean keyMatch = alpha > 240
                    && Math.abs(red - keyRed) <= 4
                    && Math.abs(green - keyGreen) <= 4
                    && Math.abs(blue - keyBlue) <= 4;
            if (background[i] || keyMatch) {
                image.setRGB(i % width, i / width, 0x00000000);
            }
        }
    }

    private static int enqueue(int index, boolean[] background, int[] queue, int tail) {
        if (!background[index]) {
            background[index] = true;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int enqueueIfBackground(int neighbor, int currentPixel, int[] pixels,
                                            boolean[] background, int[] queue, int tail) {
        if (background[neighbor]) return tail;
        int nextPixel = pixels[neighbor];
        if (maxChannelDifference(currentPixel, nextPixel) <= BACKGROUND_CHANNEL_TOLERANCE) {
            background[neighbor] = true;
            queue[tail++] = neighbor;
        }
        return tail;
    }

    private static int maxChannelDifference(int first, int second) {
        int redDifference = Math.abs(((first >>> 16) & 0xFF) - ((second >>> 16) & 0xFF));
        int greenDifference = Math.abs(((first >>> 8) & 0xFF) - ((second >>> 8) & 0xFF));
        int blueDifference = Math.abs((first & 0xFF) - (second & 0xFF));
        return Math.max(redDifference, Math.max(greenDifference, blueDifference));
    }
}
