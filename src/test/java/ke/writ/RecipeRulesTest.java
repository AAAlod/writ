package ke.writ;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeRulesTest {
    @Test void tradeRemovesOnlyChaosAndCraftKeepsAll() {
        var recipes = List.of("minecraft:bread", RecipeRules.CHAOS_RECIPE, "example:other");
        assertEquals(List.of("minecraft:bread", "example:other"), RecipeRules.filter(recipes, ObtainMode.TRADE, id -> id));
        assertEquals(recipes, RecipeRules.filter(recipes, ObtainMode.CRAFT_9, id -> id));
    }
}
