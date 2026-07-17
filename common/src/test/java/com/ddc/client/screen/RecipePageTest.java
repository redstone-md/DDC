package com.ddc.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guide's crafting page, checked against the recipes that actually ship.
 *
 * <p>The page names its ingredients rather than reading the recipe files, which buys a page that
 * needs no recipe manager and costs the risk of drifting out of date. This is the thing that stops
 * the drift: if a recipe changes and the page does not, a test fails rather than a player following
 * instructions that do not work.
 */
class RecipePageTest {

    private static final Path RECIPES = Path.of("src", "main", "resources", "data", "ddc", "recipe");

    @Test
    @DisplayName("every craft the page draws is a recipe that exists")
    void everyCraftIsReal() throws IOException {
        for (RecipePage.Craft craft : RecipePage.CRAFTS) {
            assertTrue(recipeFor(craft.result()).isPresent(),
                    craft.result() + " is drawn in the guide but nothing crafts it");
        }
    }

    @Test
    @DisplayName("the page asks for exactly what the recipe asks for")
    void theIngredientsMatch() throws IOException {
        for (RecipePage.Craft craft : RecipePage.CRAFTS) {
            List<String> real = ingredientsOf(recipeFor(craft.result()).orElseThrow());

            assertEquals(real.stream().sorted().toList(), craft.ingredients().stream().sorted().toList(),
                    craft.result() + ": the guide and the recipe disagree about what it costs");
        }
    }

    @Test
    @DisplayName("every craft has a line saying what the thing is for")
    void everyCraftIsExplained() {
        for (RecipePage.Craft craft : RecipePage.CRAFTS) {
            assertTrue(craft.note().startsWith("ddc.recipe."),
                    craft.result() + " has no note, so the page would show an item and no reason for it");
        }
    }

    /** The recipe that makes this item, whatever shape it is written in. */
    private static java.util.Optional<JsonObject> recipeFor(String item) throws IOException {
        try (Stream<Path> files = Files.list(RECIPES)) {
            return files.map(RecipePageTest::read)
                    .filter(recipe -> item.equals(recipe.getAsJsonObject("result").get("id").getAsString()))
                    .findFirst();
        }
    }

    /**
     * What a recipe takes, flattened.
     *
     * <p>Shaped and shapeless recipes say it differently -- a pattern and a key, or a list -- and the
     * page draws a row of items either way, so this reads both into the same thing.
     */
    private static List<String> ingredientsOf(JsonObject recipe) {
        List<String> ingredients = new ArrayList<>();
        if (recipe.has("ingredients")) {
            recipe.getAsJsonArray("ingredients").forEach(item -> ingredients.add(item.getAsString()));
            return ingredients;
        }
        JsonObject key = recipe.getAsJsonObject("key");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        for (JsonElement row : pattern) {
            for (char slot : row.getAsString().toCharArray()) {
                if (slot != ' ') {
                    ingredients.add(key.get(String.valueOf(slot)).getAsString());
                }
            }
        }
        return ingredients;
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
