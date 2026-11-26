package com.ayden.itemexporter.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;

public final class ImageCapture {
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
        removeKeyColor(cropped, screen.keyColor());
        return cropped;
    }

    private static void removeKeyColor(BufferedImage image, int key) {
        int keyRed = (key >>> 16) & 0xFF;
        int keyGreen = (key >>> 8) & 0xFF;
        int keyBlue = key & 0xFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int red = (pixel >>> 16) & 0xFF;
                int green = (pixel >>> 8) & 0xFF;
                int blue = pixel & 0xFF;
                boolean keyMatch = ((pixel >>> 24) & 0xFF) > 240
                        && Math.abs(red - keyRed) <= 4
                        && Math.abs(green - keyGreen) <= 4
                        && Math.abs(blue - keyBlue) <= 4;
                if (keyMatch) {
                    image.setRGB(x, y, 0x00000000);
                }
            }
        }
    }
}
