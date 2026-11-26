package com.ayden.itemexporter.client;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A tiny keyed canvas used only while a client-side export is being captured. */
public final class CaptureScreen extends Screen {
    private final int keyColor;
    private final ItemStack itemStack;
    private final IRecipeLayoutDrawable<Object> recipeLayout;
    private final int canvasWidth;
    private final int canvasHeight;
    private final int innerPadding;
    private final int recipeRenderScale;
    private final int logicalCanvasWidth;
    private final int logicalCanvasHeight;
    private int canvasX;
    private int canvasY;

    private CaptureScreen(ItemStack itemStack,
                          IRecipeLayoutDrawable<Object> recipeLayout,
                          int canvasWidth,
                          int canvasHeight,
                          int innerPadding,
                          int recipeRenderScale,
                          int logicalCanvasWidth,
                          int logicalCanvasHeight) {
        super(Component.literal("Item Exporter capture"));
        this.keyColor = 0xFF000000 | java.util.concurrent.ThreadLocalRandom.current().nextInt(0x1000000);
        this.itemStack = itemStack;
        this.recipeLayout = recipeLayout;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.innerPadding = innerPadding;
        this.recipeRenderScale = recipeRenderScale;
        this.logicalCanvasWidth = logicalCanvasWidth;
        this.logicalCanvasHeight = logicalCanvasHeight;
    }

    public static CaptureScreen forItem(ItemStack stack) {
        return new CaptureScreen(stack.copy(), null, 20, 20, 2, 1, 20, 20);
    }

    public static CaptureScreen forRecipe(IRecipeLayoutDrawable<Object> layout, int requestedScale) {
        int recipeWidth = layout.getRect().getWidth();
        int recipeHeight = layout.getRect().getHeight();
        if (recipeWidth <= 0 || recipeHeight <= 0) {
            throw new IllegalArgumentException("JEI returned a recipe layout with invalid dimensions.");
        }
        int padding = 6;
        int logicalWidth = recipeWidth + padding * 2;
        int logicalHeight = recipeHeight + padding * 2;
        Minecraft minecraft = Minecraft.getInstance();
        int chosenScale = requestedScale;
        if (chosenScale == 0) {
            int fitX = Math.max(1, (minecraft.getWindow().getGuiScaledWidth() - 8) / logicalWidth);
            int fitY = Math.max(1, (minecraft.getWindow().getGuiScaledHeight() - 8) / logicalHeight);
            chosenScale = Math.min(10, Math.min(fitX, fitY));
        }
        return new CaptureScreen(null, layout, logicalWidth * chosenScale, logicalHeight * chosenScale,
                padding, chosenScale, logicalWidth, logicalHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        canvasX = (guiWidth - canvasWidth) / 2;
        canvasY = (guiHeight - canvasHeight) / 2;

        if (itemStack != null) {
            graphics.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, keyColor);
            graphics.item(itemStack, canvasX + innerPadding, canvasY + innerPadding, 0);
        } else if (recipeLayout != null) {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(canvasX, canvasY);
                graphics.pose().scale(recipeRenderScale, recipeRenderScale);
                graphics.fill(0, 0, logicalCanvasWidth, logicalCanvasHeight, keyColor);
                recipeLayout.setPosition(innerPadding, innerPadding);
                recipeLayout.drawRecipe(graphics, -1000, -1000);
            } finally {
                graphics.pose().popMatrix();
            }
        }
    }

    public int keyColor() { return keyColor; }
    public int canvasX() { return canvasX; }
    public int canvasY() { return canvasY; }
    public int canvasWidth() { return canvasWidth; }
    public int canvasHeight() { return canvasHeight; }
    public int recipeRenderScale() { return recipeRenderScale; }
    public IRecipeLayoutDrawable<Object> recipeLayout() { return recipeLayout; }
}
