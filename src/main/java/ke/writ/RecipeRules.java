package ke.writ;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Pure selection: retain every unrelated recipe. */
final class RecipeRules {
    static final String CHAOS_RECIPE = "writ:chaos_writ";
    static <T> List<T> filter(Collection<T> recipes, ObtainMode mode, Function<T, String> id) {
        return recipes.stream().filter(recipe -> mode == ObtainMode.CRAFT_9 || !CHAOS_RECIPE.equals(id.apply(recipe))).toList();
    }
}
