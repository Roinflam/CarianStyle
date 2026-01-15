package pers.roinflam.carianstyle.enchantment;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 岩石爆破附魔
 *
 * 镐子附魔，范围挖掘
 * 挖掘方块时：
 * - 范围内同类型方块一起挖掘
 * - 范围 = 1 + 等级/2（受配置上限限制）
 */
@AutoRegisterEnchantment(
        id = "rock_blaster",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentRockBlaster extends EnchantmentBase {

    public EnchantmentRockBlaster() {
        super(EnumEnchantmentType.DIGGER, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 挖掘方块时范围挖掘同类型方块
     * 由于 BlockEvent.BreakEvent 没有模板方法，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(@Nonnull BlockEvent.BreakEvent evt) {
        if (evt.getPlayer().world.isRemote) {
            return;
        }

        EntityPlayer player = evt.getPlayer();
        if (player.swingingHand == null) {
            return;
        }

        ItemStack tool = player.getHeldItem(player.swingingHand);
        if (tool.isEmpty() || !(tool.getItem() instanceof ItemPickaxe)) {
            return;
        }

        Enchantment rockBlaster = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRockBlaster.class);
        if (rockBlaster == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(rockBlaster, tool);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 计算范围
        int radius = 1 + level / 2;
        radius = Math.min(radius, ConfigLoader.rockBlasterMaxRange);

        // 收集范围内的方块位置
        List<BlockPos> blockPosList = new ArrayList<>();
        BlockPos center = evt.getPos();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    blockPosList.add(new BlockPos(center.getX() + x, center.getY() + y, center.getZ() + z));
                }
            }
        }

        World world = player.world;
        Block targetBlock = evt.getState().getBlock();

        for (BlockPos pos : blockPosList) {
            // 检查工具耐久
            if (tool.isItemStackDamageable()) {
                if (tool.getItemDamage() >= tool.getMaxDamage() - 1) {
                    return;
                }
            }

            IBlockState blockState = world.getBlockState(pos);
            Block block = blockState.getBlock();

            // 只挖掘同类型方块
            if (!block.equals(targetBlock)) {
                continue;
            }

            if (!player.capabilities.isCreativeMode) {
                block.onBlockHarvested(world, pos, blockState, player);
                if (block.removedByPlayer(blockState, world, pos, player, true)) {
                    world.playEvent(2001, pos, Block.getStateId(blockState));
                    block.harvestBlock(world, player, pos, blockState, world.getTileEntity(pos), tool);
                    block.onBlockDestroyedByPlayer(world, pos, blockState);
                    block.dropXpOnBlockBreak(world, pos,
                            block.getExpDrop(blockState, world, pos, EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool)) / 2);
                    if (tool.isItemStackDamageable()) {
                        tool.attemptDamageItem(1, world.rand, (EntityPlayerMP) player);
                    }
                }
            } else {
                world.destroyBlock(pos, false);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 35) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.UNBREAKING);
    }
}