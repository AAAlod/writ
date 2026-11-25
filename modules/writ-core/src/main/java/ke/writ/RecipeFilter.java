package ke.writ;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

/** Applies only to the server's freshly reloaded recipe manager. */
final class RecipeFilter extends SimplePreparableReloadListener<Void> {
    private static RecipeFilter current;
    private final RecipeManager manager;
    private List<Recipe<?>> original;

    RecipeFilter(RecipeManager manager) { this.manager = manager; }
    @Override protected Void prepare(ResourceManager resources, ProfilerFiller profiler) { return null; }
    @Override protected void apply(Void unused, ResourceManager resources, ProfilerFiller profiler) {
        synchronized (RecipeFilter.class) {
            original = List.copyOf(manager.getRecipes());
            current = this;
            applyMode(Writ.recipeMode());
        }
    }
    static synchronized void clear() { current = null; }
    private void applyMode(ObtainMode mode) {
        manager.replaceRecipes(RecipeRules.filter(original, mode, recipe -> recipe.getId().toString()));
        boolean enabled = manager.byKey(new ResourceLocation(RecipeRules.CHAOS_RECIPE)).isPresent();
        if (Presets.INSTANCE.modeReady()) Writ.LOG.info("Chaos contract recipe: mode={}, enabled={}", mode, enabled);
    }
}
