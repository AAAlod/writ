package ke.writ;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-side single settlement; no boss loot-table modifier or drops event. */
final class BossDrops {
    private static final String SETTLED = "writ:boss_drop_settled";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.isCanceled() || !entity.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;
        var snapshot = Presets.INSTANCE.currentSnapshot();
        var id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        var boss = id == null ? null : snapshot.bosses().get(id.toString());
        if (boss == null || entity.getPersistentData().getBoolean(SETTLED)) return;
        entity.getPersistentData().putBoolean(SETTLED, true);
        var random = entity.getRandom();
        if (random.nextDouble() >= boss.chance()) return;
        var credit = entity.getKillCredit();
        if (!(credit instanceof ServerPlayer player) || player instanceof FakePlayer || player.getServer() == null
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
        ItemStack stack = Presets.INSTANCE.samplePreset(snapshot, boss, random)
                .map(preset -> Presets.INSTANCE.contractFor(snapshot, preset)).orElse(ItemStack.EMPTY);
        if (!stack.isEmpty() && !player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }
}
