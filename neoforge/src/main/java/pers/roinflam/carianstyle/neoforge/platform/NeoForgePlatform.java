package pers.roinflam.carianstyle.neoforge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.entity.PartEntity;
import pers.roinflam.carianstyle.enchantment.RockBlasterProbeEvent;
import pers.roinflam.carianstyle.platform.Platform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * NeoForge 上的 {@link Platform}。每个方法都是现有代码在调用处的原样写法搬过来，行为不变。
 */
public final class NeoForgePlatform implements Platform {

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    @Override
    @Nonnull
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isFakePlayer(@Nonnull ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    @Override
    @Nullable
    public Entity partParent(@Nonnull Entity entity) {
        return entity instanceof PartEntity<?> part ? part.getParent() : null;
    }

    @Override
    @Nonnull
    public CompoundTag persistentData(@Nonnull Entity entity) {
        return entity.getPersistentData();
    }

    /**
     * 照旧在 NeoForge 总线上发 {@link RockBlasterProbeEvent}（{@code BreakEvent} 的子类）：
     * 只认 {@code BreakEvent} 的领地 / 保护模组照样收得到，混合端照旧按类名认出这是权限探测。
     */
    @Override
    public boolean probeBlockBreak(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                                   @Nonnull Player player) {
        return !NeoForge.EVENT_BUS.post(new RockBlasterProbeEvent(level, pos, state, player)).isCanceled();
    }

    @Override
    public boolean destroyByPlayer(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                   @Nonnull Player player, boolean willHarvest, @Nonnull FluidState fluid) {
        return state.getBlock().onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public boolean supportsEnchantment(@Nonnull ItemStack stack, @Nonnull Holder<Enchantment> enchantment) {
        return stack.supportsEnchantment(enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(@Nonnull ItemStack stack, @Nonnull Holder<Enchantment> enchantment) {
        return stack.isPrimaryItemFor(enchantment);
    }

    @Override
    public boolean isBookEnchantable(@Nonnull ItemStack stack, @Nonnull ItemStack book) {
        return stack.isBookEnchantable(book);
    }

    @Override
    public int getEnchantmentValue(@Nonnull ItemStack stack) {
        return stack.getEnchantmentValue();
    }
}
