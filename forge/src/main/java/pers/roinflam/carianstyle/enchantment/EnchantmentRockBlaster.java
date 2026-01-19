package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 岩石爆破附魔
 * <p>
 * 镐子附魔，范围挖掘
 * 挖掘方块时：
 * - 范围内同类型方块一起挖掘
 * - 范围 = 1 + 等级/2（受配置上限限制）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "rock_blaster",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.DIGGER,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {}
)
@Mod.EventBusSubscriber
public class EnchantmentRockBlaster extends EnchantmentBase {

    public EnchantmentRockBlaster() {
        super(EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(@NotNull BlockEvent.BreakEvent evt) {
        if (evt.getPlayer().level().isClientSide) {
            return;
        }

        Player player = evt.getPlayer();
        if (player.getUsedItemHand() == null) {
            return;
        }

        ItemStack tool = player.getItemInHand(player.getUsedItemHand());
        if (tool.isEmpty() || !(tool.getItem() instanceof PickaxeItem)) {
            return;
        }

        Enchantment rockBlaster = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRockBlaster.class);
        if (rockBlaster == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(rockBlaster, tool);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        int radius = 1 + level / 2;
        radius = Math.min(radius, ConfigLoader.rockBlasterMaxRange);

        List<BlockPos> blockPosList = new ArrayList<>();
        BlockPos center = evt.getPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    blockPosList.add(new BlockPos(center.getX() + x, center.getY() + y, center.getZ() + z));
                }
            }
        }

        Level world = player.level();
        Block targetBlock = evt.getState().getBlock();

        for (BlockPos pos : blockPosList) {
            if (tool.isDamageableItem()) {
                if (tool.getDamageValue() >= tool.getMaxDamage() - 1) {
                    return;
                }
            }

            BlockState blockState = world.getBlockState(pos);
            Block block = blockState.getBlock();

            if (!block.equals(targetBlock)) {
                continue;
            }

            if (!player.getAbilities().instabuild) {
                if (world instanceof ServerLevel serverLevel) {
                    block.playerWillDestroy(world, pos, blockState, player);

                    if (block.onDestroyedByPlayer(blockState, world, pos, player, true, world.getFluidState(pos))) {
                        block.destroy(world, pos, blockState);
                        block.playerDestroy(world, player, pos, blockState, world.getBlockEntity(pos), tool);

                        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
                        block.popExperience(serverLevel, pos, block.getExpDrop(blockState, serverLevel, serverLevel.random, pos, fortuneLevel, 0) / 2);

                        if (tool.isDamageableItem()) {
                            tool.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
                        }
                    }
                }
            } else {
                world.destroyBlock(pos, false);
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 35) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.UNBREAKING);
    }
}