package com.ayden.itemexporter.client;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@JeiPlugin
public final class RecipeBridge implements IModPlugin {
    private static volatile IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("itemexporter", "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) { runtime = jeiRuntime; }

    @Override
    public void onRuntimeUnavailable() { runtime = null; }

    public static Optional<IJeiRuntime> runtime() { return Optional.ofNullable(runtime); }

    public static List<String> methodSuggestions() {
        IJeiRuntime current = runtime;
        if (current == null) return List.of();
        try (var stream = current.getRecipeManager().createRecipeCategoryLookup().get()) {
            return stream.map(category -> category.getRecipeType().getUid().toString())
                    .distinct().sorted().toList();
        }
    }

    public static Optional<IRecipeType<?>> findType(String method) {
        IJeiRuntime current = runtime;
        if (current == null) return Optional.empty();
        String normalized = method.toLowerCase(Locale.ROOT);
        IRecipeType<?> vanilla = switch (normalized) {
            case "crafting" -> RecipeTypes.CRAFTING;
            case "smelting", "furnace" -> RecipeTypes.SMELTING;
            case "blasting" -> RecipeTypes.BLASTING;
            case "smoking" -> RecipeTypes.SMOKING;
            case "campfire", "campfire_cooking" -> RecipeTypes.CAMPFIRE_COOKING;
            case "stonecutting", "stonecutter" -> RecipeTypes.STONECUTTING;
            case "smithing" -> RecipeTypes.SMITHING;
            case "brewing" -> RecipeTypes.BREWING;
            case "anvil" -> RecipeTypes.ANVIL;
            case "grindstone" -> RecipeTypes.GRINDSTONE;
            default -> null;
        };
        if (vanilla != null) return Optional.of(vanilla);
        Identifier id = Identifier.tryParse(method);
        if (id == null) return Optional.empty();
        return current.getRecipeManager().getRecipeType(id);
    }

    public static RecipeSelection findRecipe(ItemStack output, IRecipeType<?> type, int oneBasedIndex) {
        IJeiRuntime current = runtime;
        if (current == null) throw new IllegalStateException("JEI is not ready. Make sure JEI is installed and loaded.");
        var focusFactory = current.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> outputFocus = focusFactory.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output.copy());
        IFocusGroup focuses = focusFactory.createFocusGroup(List.of(outputFocus));
        IRecipeManager manager = current.getRecipeManager();
        List<?> matches = lookupUnchecked(manager, type, outputFocus);
        if (matches.isEmpty()) throw new IllegalArgumentException("JEI has no " + type.getUid() + " recipe for that item.");
        if (oneBasedIndex > matches.size()) {
            throw new IllegalArgumentException("Only " + matches.size() + " matching recipe(s) exist for this method.");
        }
        IRecipeCategory<Object> category = categoryUnchecked(manager, type);
        Object recipe = matches.get(oneBasedIndex - 1);
        var layout = manager.createRecipeLayoutDrawableOrShowError(category, recipe, focuses);
        return new RecipeSelection(layout, matches.size(), oneBasedIndex, type.getUid().toString());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<?> lookupUnchecked(IRecipeManager manager, IRecipeType type, IFocus<?> focus) {
        return manager.createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IRecipeCategory<Object> categoryUnchecked(IRecipeManager manager, IRecipeType type) {
        return (IRecipeCategory<Object>) manager.getRecipeCategory(type);
    }

    public record RecipeSelection(
            mezz.jei.api.gui.IRecipeLayoutDrawable<Object> layout,
            int matchCount, int selectedIndex, String methodId
    ) {}
}
