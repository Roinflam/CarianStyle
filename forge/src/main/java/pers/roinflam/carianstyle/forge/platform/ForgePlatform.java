package pers.roinflam.carianstyle.forge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.carianstyle.enchantment.RockBlasterProbeEvent;
import pers.roinflam.carianstyle.platform.Platform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

// 附魔适用的几个查询（GE 加）还没进接口
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
}
