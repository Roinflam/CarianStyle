package pers.roinflam.carianstyle.fabric.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.fabric.RockBlasterExperience;
import pers.roinflam.carianstyle.platform.CustomEnchantCategory;
import pers.roinflam.carianstyle.platform.Platform;

import java.nio.file.Path;

/**
 * Fabric 上的 {@link Platform}。
 * <p>
 * 和 Forge 的差别都来自 Fabric 没有对应的扩展点：多部位生物只认原版的末影龙部位；
 * 附魔适用的查询按 Forge 补丁方法的默认实现写（别的模组物品在 Forge 上覆写这些方法改变的结果，
 * 在 Fabric 上没有途径体现）；自定义附魔类型挂不进原版枚举，一律挂 {@code BREAKABLE}，
 * 真正的判定由 {@code mixin/enchant} 在附魔台和 {@code canEnchant} 上改问本模组附魔；
 * 实体持久数据由 {@code mixin/entity} 加在 {@link Entity} 上。
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
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakePlayer;
    }

    /** Fabric 没有通用的多部位实体抽象，只认原版末影龙的部位。 */
    @Override
    public Entity partParent(Entity entity) {
        return entity instanceof EnderDragonPart part ? part.parentMob : null;
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return ((CarianEntityData) entity).carianstyle$getPersistentData();
    }

    /**
     * 问 {@link PlayerBlockBreakEvents#BEFORE}：领地、保护类模组在这里拒绝。
     * 本模组自己的破坏方块监听也挂在这个回调上（排在默认阶段之后），和 Forge 上探测事件同样会经过本模组的监听。
     */
    @Override
    public boolean probeBlockBreak(Level level, BlockPos pos, BlockState state,
                                   Player player) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
    }

    /**
     * 客户端照原版 {@code MultiPlayerGameMode} 换成流体方块，服务端照原版 {@code ServerPlayerGameMode} 移除。
     * <p>
     * 服务端移除成功就给这一格打上碎岩标记：调用方接着调原版 {@code playerDestroy}，那里放的经验由
     * {@code popExperience} 的 mixin 按标记减半。Forge 上减半由 {@link #popChainBreakExperience} 做。
     * </p>
     */
    @Override
    public boolean destroyByPlayer(BlockState state, Level level, BlockPos pos,
                                   Player player, boolean willHarvest, FluidState fluid) {
        if (level.isClientSide()) {
            return level.setBlock(pos, fluid.createLegacyBlock(), Block.UPDATE_ALL_IMMEDIATE);
        }
        boolean removed = level.removeBlock(pos, false);
        if (removed && level instanceof ServerLevel serverLevel) {
            RockBlasterExperience.mark(serverLevel, pos);
        }
        return removed;
    }

    /** 原版 {@code playerDestroy} 已经放过经验，减半见 {@link #destroyByPlayer}。 */
    @Override
    public void popChainBreakExperience(BlockState state, ServerLevel level, BlockPos pos,
                                        int fortuneLevel) {
    }

    /** 原版没有「物品拒收附魔书」这一条，恒为真（同 Forge 默认实现）。 */
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return true;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return stack.getItem().getEnchantmentValue();
    }

    /** 三种自定义类型都是可损耗物品的子集，挂 {@code BREAKABLE}；创造栏等直接读 category 的地方会按它判。 */
    @Override
    public EnchantmentCategory hostCategory(CustomEnchantCategory type) {
        return EnchantmentCategory.BREAKABLE;
    }

    /**
     * 不能调 {@code Enchantment#canEnchant}：Fabric 上它对本模组附魔已被 mixin 转回
     * {@code canApplyAtEnchantingTable}，再调回来就是死循环。
     */
    @Override
    public boolean defaultTableCheck(Enchantment enchantment, ItemStack stack,
                                     CustomEnchantCategory custom) {
        if (custom != null) {
            return custom.canEnchant(stack.getItem());
        }
        return enchantment.category.canEnchant(stack.getItem());
    }

    @Override
    public boolean canApplyAtEnchantingTable(Enchantment enchantment, ItemStack stack) {
        if (enchantment instanceof EnchantmentBase base) {
            return base.canApplyAtEnchantingTable(stack);
        }
        return enchantment.category.canEnchant(stack.getItem());
    }

    @Override
    public boolean isAllowedOnBooks(Enchantment enchantment) {
        if (enchantment instanceof EnchantmentBase base) {
            return base.isAllowedOnBooks();
        }
        return true;
    }
}
