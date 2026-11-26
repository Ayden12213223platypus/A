package com.ayden.itemexporter.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class CommandRegistration {
    private CommandRegistration() {}

    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("itemexport")
                .then(argument("item", IdentifierArgument.id())
                        .executes(context -> exportItem(context.getSource(), IdentifierArgument.getId(context, "item")))));

        var scaleNode = argument("scale", IntegerArgumentType.integer(1, 10))
                .executes(context -> exportRecipe(context.getSource(),
                        IdentifierArgument.getId(context, "item"), methodArgument(context, "method"),
                        IntegerArgumentType.getInteger(context, "index"),
                        IntegerArgumentType.getInteger(context, "scale")));

        var indexNode = argument("index", IntegerArgumentType.integer(1))
                .executes(context -> exportRecipe(context.getSource(),
                        IdentifierArgument.getId(context, "item"), methodArgument(context, "method"),
                        IntegerArgumentType.getInteger(context, "index"), 0))
                .then(scaleNode);

        var methodNode = argument("method", IdentifierArgument.id())
                .suggests((context, builder) -> suggestMethods(builder))
                .executes(context -> exportRecipe(context.getSource(),
                        IdentifierArgument.getId(context, "item"), methodArgument(context, "method"), 1, 0))
                .then(indexNode);

        event.getDispatcher().register(literal("recipeexport")
                .then(argument("item", IdentifierArgument.id()).then(methodNode)));

        event.getDispatcher().register(literal("itemexportcancel").executes(context -> {
            try {
                CaptureManager.cancel();
                return 1;
            } catch (IllegalStateException exception) {
                context.getSource().sendFailure(Component.literal(exception.getMessage()));
                return 0;
            }
        }));
    }

    private static String methodArgument(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                         String argumentName) {
        Identifier methodId = IdentifierArgument.getId(context, argumentName);
        return methodId.getNamespace().equals("minecraft") ? methodId.getPath() : methodId.toString();
    }

    private static int exportItem(CommandSourceStack source, Identifier itemId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            source.sendFailure(Component.literal("You must be in a world to export an item."));
            return 0;
        }
        ItemStack stack;
        if (itemId.getNamespace().equals("minecraft") && itemId.getPath().equalsIgnoreCase("hand")) {
            stack = minecraft.player.getMainHandItem().copy();
        } else {
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) {
                source.sendFailure(Component.literal("Unknown item: " + itemId));
                return 0;
            }
            stack = item.getDefaultInstance();
        }
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Your main hand is empty."));
            return 0;
        }
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try {
            CaptureManager.beginItem(stack, registryId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Capturing " + stack.getHoverName().getString()
                + " at 10x scale. Animation is detected automatically; use /itemexportcancel to stop."), false);
        return 1;
    }

    private static int exportRecipe(CommandSourceStack source, Identifier id, String method, int index, int scale) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            source.sendFailure(Component.literal("Unknown item: " + id));
            return 0;
        }
        try {
            var recipeType = RecipeBridge.findType(method).orElseThrow(
                    () -> new IllegalArgumentException("Unknown JEI recipe method: " + method));
            var selection = RecipeBridge.findRecipe(item.getDefaultInstance(), recipeType, index);
            CaptureManager.beginRecipe(selection.layout(), scale,
                    id + "_" + selection.methodId() + "_" + selection.selectedIndex());
            source.sendSuccess(() -> Component.literal("Rendering JEI recipe " + selection.selectedIndex()
                    + "/" + selection.matchCount() + " for " + id + " using " + selection.methodId() + " at "
                    + (scale == 0 ? "automatic max-fit scale" : scale + "x") + "."), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Could not prepare that JEI recipe: " + exception.getMessage()));
            return 0;
        }
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMethods(SuggestionsBuilder builder) {
        List<String> suggestions = new ArrayList<>(List.of("crafting", "smelting", "blasting", "smoking",
                "campfire", "stonecutting", "smithing", "brewing", "anvil", "grindstone"));
        suggestions.addAll(RecipeBridge.methodSuggestions());
        return SharedSuggestionProvider.suggest(suggestions.stream().distinct().toList(), builder);
    }
}
