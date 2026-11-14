package pers.roinflam.carianstyle.fabric.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import pers.roinflam.carianstyle.platform.Platform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Fabric 上的 {@link Platform}。
 * <p>
 * 和 NeoForge 的差别都来自 Fabric 没有对应的扩展点：多部位生物只认原版的末影龙部位；
 * 附魔适用的四个查询按 NeoForge 扩展方法的默认实现写（别的模组物品在 NeoForge 上覆写这些方法改变的结果，
 * 在 Fabric 上没有途径体现）；实体持久数据由 {@code mixin/entity} 加在 {@link Entity} 上。
 * </p>
 */
public final class FabricPlatform implements Platform {

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    @Nonnull
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isFakePlayer(@Nonnull ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    /** Fabric 没有通用的多部位实体抽象，只认原版末影龙的部位。 */
    @Override
    @Nullable
    public Entity partParent(@Nonnull Entity entity) {
        return entity instanceof EnderDragonPart part ? part.parentMob : null;
    }

    @Override
    @Nonnull
    public CompoundTag persistentData(@Nonnull Entity entity) {
        return ((CarianEntityData) entity).carianstyle$getPersistentData();
    }

    /**
     * 问 {@link PlayerBlockBreakEvents#BEFORE}：领地、保护类模组在这里拒绝。
     * 本模组自己的破坏方块监听也挂在这个回调上（排在默认阶段之后），和 NeoForge 上探测事件同样会经过本模组的监听。
     */
    @Override
    public boolean probeBlockBreak(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                                   @Nonnull Player player) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
    }

    /**
     * 同 NeoForge {@code IBlockExtension#onDestroyedByPlayer} 的默认实现：客户端照原版 {@code MultiPlayerGameMode}
     * 换成流体方块，服务端照原版 {@code ServerPlayerGameMode} 移除。
     */
    @Override
    public boolean destroyByPlayer(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                   @Nonnull Player player, boolean willHarvest, @Nonnull FluidState fluid) {
        if (level.isClientSide()) {
            return level.setBlock(pos, fluid.createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE);
        }
        return level.removeBlock(pos, false);
    }

    @Override
    public boolean supportsEnchantment(@Nonnull ItemStack stack, @Nonnull Holder<Enchantment> enchantment) {
        return stack.is(Items.ENCHANTED_BOOK) || enchantment.value().isSupportedItem(stack);
    }

    /**
     * 逐字照 NeoForge 默认实现：先问 {@link #supportsEnchantment}（附魔书恒真），再看主要物品。
     * 不用原版 {@code Enchantment#isPrimaryItem}：它只认适用物品标签，传进来的是附魔书时结论和 NeoForge 不同。
     */
    @Override
    public boolean isPrimaryItemFor(@Nonnull ItemStack stack, @Nonnull Holder<Enchantment> enchantment) {
        if (stack.is(Items.BOOK)) {
            return true;
        }
        Optional<HolderSet<Item>> primaryItems = enchantment.value().definition().primaryItems();
        return supportsEnchantment(stack, enchantment) && (primaryItems.isEmpty() || stack.is(primaryItems.get()));
    }

    /** 原版没有「物品拒收附魔书」这一条，恒为真（同 NeoForge 默认实现）。 */
    @Override
    public boolean isBookEnchantable(@Nonnull ItemStack stack, @Nonnull ItemStack book) {
        return true;
    }

    @Override
    public int getEnchantmentValue(@Nonnull ItemStack stack) {
        return stack.getItem().getEnchantmentValue();
    }
}
