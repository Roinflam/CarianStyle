package pers.roinflam.carianstyle.forge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.carianstyle.enchantment.RockBlasterProbeEvent;
import pers.roinflam.carianstyle.platform.CustomEnchantCategory;
import pers.roinflam.carianstyle.platform.Platform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class ForgePlatform implements Platform {

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

    @Override
    public boolean probeBlockBreak(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                                   @Nonnull Player player) {
        return !MinecraftForge.EVENT_BUS.post(new RockBlasterProbeEvent(level, pos, state, player));
    }

    @Override
    public boolean destroyByPlayer(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                   @Nonnull Player player, boolean willHarvest, @Nonnull FluidState fluid) {
        return state.getBlock().onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void popChainBreakExperience(@Nonnull BlockState state, @Nonnull ServerLevel level, @Nonnull BlockPos pos,
                                        int fortuneLevel) {
        // 精准采集参数写死 0：以前就是这样算的，Forge 版行为不变
        state.getBlock().popExperience(level, pos,
                state.getBlock().getExpDrop(state, level, level.random, pos, fortuneLevel, 0) / 2);
    }

    @Override
    public boolean isBookEnchantable(@Nonnull ItemStack stack, @Nonnull ItemStack book) {
        return stack.isBookEnchantable(book);
    }

    @Override
    public int getEnchantmentValue(@Nonnull ItemStack stack) {
        return stack.getEnchantmentValue();
    }

    @Override
    @Nonnull
    public EnchantmentCategory hostCategory(@Nonnull CustomEnchantCategory type) {
        return Hosts.BY_TYPE.get(type);
    }

    @Override
    public boolean defaultTableCheck(@Nonnull Enchantment enchantment, @Nonnull ItemStack stack,
                                     @Nullable CustomEnchantCategory custom) {
        // 就是 Forge 补丁里 Enchantment#canApplyAtEnchantingTable 的默认实现
        return stack.canApplyAtEnchantingTable(enchantment);
    }

    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull Enchantment enchantment, @Nonnull ItemStack stack) {
        return enchantment.canApplyAtEnchantingTable(stack);
    }

    @Override
    public boolean isAllowedOnBooks(@Nonnull Enchantment enchantment) {
        return enchantment.isAllowedOnBooks();
    }

    // 扩展枚举同一个名字只能 create 一次，放在静态内部类里由类初始化保证只建一次、各线程看到的是同一份
    private static final class Hosts {
        static final Map<CustomEnchantCategory, EnchantmentCategory> BY_TYPE;

        static {
            EnumMap<CustomEnchantCategory, EnchantmentCategory> map = new EnumMap<>(CustomEnchantCategory.class);
            for (CustomEnchantCategory type : CustomEnchantCategory.values()) {
                map.put(type, EnchantmentCategory.create(type.hostName(), type::canEnchant));
            }
            BY_TYPE = map;
        }
    }
}
