package com.ayden.itemexporter.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CaptureManager {
    private static final long STATIC_SETTLE_NANOS = 3_000_000_000L;
    private static final long MAX_AUTO_CAPTURE_NANOS = 15_000_000_000L;

    private static CaptureScreen activeScreen;
    private static final List<ImageExporter.GifFrame> frames = new ArrayList<>();
    private static String outputBaseName;
    private static boolean recipeCapture;
    private static boolean active;
    private static boolean screenshotPending;
    private static long captureSession;
    private static long captureStartedAtNanos;
    private static BufferedImage firstItemFrame;
    private static BufferedImage lastItemFrame;
    private static long lastItemFrameStartedAtNanos;
    private static boolean itemAnimationChanged;

    private CaptureManager() {}

    public static void beginItem(net.minecraft.world.item.ItemStack stack, String registryId) {
        begin(CaptureScreen.forItem(stack), registryId, false);
    }

    public static void beginRecipe(mezz.jei.api.gui.IRecipeLayoutDrawable<Object> layout, int scale, String baseName) {
        begin(CaptureScreen.forRecipe(layout, scale), baseName, true);
    }

    private static void begin(CaptureScreen screen, String baseName, boolean isRecipe) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active) throw new IllegalStateException("An export is already running.");
        if (screen.canvasWidth() + 4 > minecraft.getWindow().getGuiScaledWidth()
                || screen.canvasHeight() + 4 > minecraft.getWindow().getGuiScaledHeight()) {
            throw new IllegalArgumentException("The capture area is larger than the current game window. Increase GUI resolution or window size.");
        }
        activeScreen = screen;
        captureSession++;
        frames.clear();
        outputBaseName = sanitize(baseName);
        recipeCapture = isRecipe;
        screenshotPending = false;
        firstItemFrame = null;
        lastItemFrame = null;
        lastItemFrameStartedAtNanos = 0L;
        captureStartedAtNanos = System.nanoTime();
        itemAnimationChanged = false;
        active = true;
        minecraft.gui.pushScreenLayer(screen);
    }

    public static void cancel() {
        if (!active) throw new IllegalStateException("No export is currently running.");
        abort(Minecraft.getInstance(), "Item Exporter capture cancelled.");
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) return;
        if (activeScreen != null && activeScreen.recipeLayout() != null) activeScreen.recipeLayout().tick();
    }

    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        // Capture every rendered frame, not just one frame per game tick. This is
        // important for animated item models that interpolate their scale each frame.
        if (!active || screenshotPending) return;
        screenshotPending = true;
        Minecraft minecraft = Minecraft.getInstance();
        long session = captureSession;
        try {
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(),
                    image -> minecraft.execute(() -> acceptScreenshot(minecraft, image, session)));
        } catch (RuntimeException exception) {
            screenshotPending = false;
            abort(minecraft, "Could not start the Minecraft framebuffer capture: " + exception.getMessage());
        }
    }

    private static void acceptScreenshot(Minecraft minecraft, NativeImage nativeImage, long session) {
        if (session != captureSession) {
            if (nativeImage != null) nativeImage.close();
            return;
        }
        if (nativeImage == null) {
            screenshotPending = false;
            abort(minecraft, "Minecraft did not return an image for the capture.");
            return;
        }
        try {
            if (!active || activeScreen == null) return;
            BufferedImage frame = ImageCapture.cropKeyedCanvas(nativeImage, activeScreen);
            if (recipeCapture) {
                frames.add(new ImageExporter.GifFrame(frame, 5));
                finish(minecraft, false, false);
            } else {
                acceptItemFrame(minecraft, frame, System.nanoTime());
            }
        } catch (Exception exception) {
            abort(minecraft, "Image capture failed: " + exception.getMessage());
        } finally {
            nativeImage.close();
            screenshotPending = false;
        }
    }

    private static void acceptItemFrame(Minecraft minecraft, BufferedImage frame, long capturedAtNanos) {
        if (firstItemFrame == null) {
            firstItemFrame = frame;
            lastItemFrame = frame;
            lastItemFrameStartedAtNanos = capturedAtNanos;
            return;
        }

        long elapsedNanos = Math.max(0L, capturedAtNanos - captureStartedAtNanos);
        if (samePixels(frame, lastItemFrame)) {
            if (!itemAnimationChanged && elapsedNanos >= STATIC_SETTLE_NANOS) {
                finishStaticItem(minecraft);
            } else if (itemAnimationChanged && elapsedNanos >= MAX_AUTO_CAPTURE_NANOS) {
                frames.add(new ImageExporter.GifFrame(lastItemFrame,
                        delayCentiseconds(lastItemFrameStartedAtNanos, capturedAtNanos)));
                finish(minecraft, true, true);
            }
            return;
        }

        itemAnimationChanged = true;
        // Animated sprites can start on an arbitrary frame. Detect a loop when any
        // previously captured visual frame reappears, not only when the first frame does.
        if (hasSeenFrame(frame)) {
            frames.add(new ImageExporter.GifFrame(lastItemFrame,
                    delayCentiseconds(lastItemFrameStartedAtNanos, capturedAtNanos)));
            finish(minecraft, true, false);
            return;
        }

        frames.add(new ImageExporter.GifFrame(lastItemFrame,
                delayCentiseconds(lastItemFrameStartedAtNanos, capturedAtNanos)));
        lastItemFrame = frame;
        lastItemFrameStartedAtNanos = capturedAtNanos;

        if (elapsedNanos >= MAX_AUTO_CAPTURE_NANOS) {
            frames.add(new ImageExporter.GifFrame(lastItemFrame,
                    delayCentiseconds(lastItemFrameStartedAtNanos, capturedAtNanos)));
            finish(minecraft, true, true);
        }
    }

    private static int delayCentiseconds(long frameStartedAtNanos, long frameEndedAtNanos) {
        long elapsed = Math.max(1L, frameEndedAtNanos - frameStartedAtNanos);
        long centiseconds = Math.max(1L, Math.round(elapsed / 10_000_000.0));
        return (int) Math.min(65_535L, centiseconds);
    }

    private static boolean hasSeenFrame(BufferedImage frame) {
        for (ImageExporter.GifFrame captured : frames) {
            if (samePixels(frame, captured.image())) return true;
        }
        return firstItemFrame != null && samePixels(frame, firstItemFrame);
    }

    private static boolean samePixels(BufferedImage first, BufferedImage second) {
        if (first == null || second == null || first.getWidth() != second.getWidth()
                || first.getHeight() != second.getHeight()) return false;
        int width = first.getWidth(), height = first.getHeight();
        int[] a = first.getRGB(0, 0, width, height, null, 0, width);
        int[] b = second.getRGB(0, 0, width, height, null, 0, width);
        return java.util.Arrays.equals(a, b);
    }

    private static void finishStaticItem(Minecraft minecraft) {
        frames.clear();
        frames.add(new ImageExporter.GifFrame(firstItemFrame, 5));
        finish(minecraft, false, false);
    }

    private static void finish(Minecraft minecraft, boolean writeGif, boolean incompleteLoop) {
        active = false;
        screenshotPending = false;
        if (minecraft.gui.screen() == activeScreen) minecraft.gui.popScreenLayer();
        List<ImageExporter.GifFrame> completedFrames = List.copyOf(frames);
        boolean scaleItemIcon = !recipeCapture;
        String baseName = outputBaseName;
        Path outputDirectory = minecraft.gameDirectory.toPath().resolve("item_exports");
        Path output = outputDirectory.resolve(baseName + (writeGif ? ".gif" : ".png"));
        frames.clear();
        activeScreen = null;
        firstItemFrame = null;
        lastItemFrame = null;
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(
                "Capture complete. Writing " + output.getFileName() + " to " + outputDirectory + "..."));
        CompletableFuture.runAsync(() -> {
            try {
                if (scaleItemIcon) ImageExporter.writeItemIcon(completedFrames, output, writeGif);
                else ImageExporter.write(completedFrames, output, writeGif);
                minecraft.execute(() -> {
                    if (minecraft.player != null) {
                        minecraft.player.sendSystemMessage(Component.literal("Export saved: " + output.toAbsolutePath()));
                        if (incompleteLoop) minecraft.player.sendSystemMessage(Component.literal(
                                "The animation did not visibly repeat a captured frame before the 15-second safety limit; this GIF may not be a seamless loop."));
                    }
                });
            } catch (Exception exception) {
                minecraft.execute(() -> {
                    if (minecraft.player != null) minecraft.player.sendSystemMessage(
                            Component.literal("Could not write export: " + exception.getMessage()));
                });
            }
        });
    }

    private static void abort(Minecraft minecraft, String message) {
        active = false;
        screenshotPending = false;
        frames.clear();
        if (minecraft.gui.screen() == activeScreen) minecraft.gui.popScreenLayer();
        activeScreen = null;
        firstItemFrame = null;
        lastItemFrame = null;
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(message));
    }

    private static String sanitize(String name) {
        return name.replace(':', '_').replace('/', '_').replace('\\', '_')
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
