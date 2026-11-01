package pers.roinflam.carianstyle.enchantment;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 岩石爆破附魔
 *
 * 镐子附魔，连锁挖掘
 * 挖掘方块时：
 * - 连锁破坏与它六面相连的同种方块，限制在半径 = 等级 的立方体内（Ⅰ 3×3×3、Ⅱ 5×5×5、Ⅲ 7×7×7，受配置上限限制）
 * - 每多挖一格消耗 1 点耐久、给一半经验；镐只剩 1 点耐久时无法挖掘，不会把镐挖坏
 * - 每一格先以玩家身份发一次破坏事件问领地 / 保护类模组，被取消的格子不挖
 * <p>
 * 以前半径是 1 + 等级/2（Ⅲ 和 Ⅱ 一样是 5×5×5），立方体里所有同种方块都挖、不要求相连，
 * 也不经过任何破坏事件，能越过领地边界；只剩 1 点耐久时中心那一格照挖，会把镐挖坏。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "rock_blaster",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentRockBlaster extends EnchantmentBase {

    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;

    /** 六面相邻的偏移量，不含对角线 */
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    /**
     * 正在被连锁处理的坐标：问权限时发出的破坏事件会再回到这里，碰到这些坐标直接放过，避免连锁再触发连锁
     */
    private static final ThreadLocal<Set<Long>> PROCESSING_BLOCKS = ThreadLocal.withInitial(HashSet::new);

    public EnchantmentRockBlaster() {
        super(EnumEnchantmentType.DIGGER, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 挖掘方块时连锁挖掘相连的同种方块
     * 由于 BlockEvent.BreakEvent 没有模板方法，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(@Nonnull BlockEvent.BreakEvent evt) {
        if (evt.getPlayer().world.isRemote) {
            return;
        }

        // 问权限发出的事件，或正在连锁的格子，不再展开
        Set<Long> processing = PROCESSING_BLOCKS.get();
        BlockPos center = evt.getPos();
        if (processing.contains(center.toLong())) {
            return;
        }

        EntityPlayer player = evt.getPlayer();
        EnumHand hand = player.swingingHand == null ? EnumHand.MAIN_HAND : player.swingingHand;

        ItemStack tool = player.getHeldItem(hand);
        if (tool.isEmpty() || !(tool.getItem() instanceof ItemPickaxe)) {
            return;
        }

        Enchantment rockBlaster = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRockBlaster.class);
        if (rockBlaster == null) {
            return;
        }

        int level = capLevel(EnchantmentHelper.getEnchantmentLevel(rockBlaster, tool), LEVEL_CAP);
        if (level <= 0) {
            return;
        }

        // 中心那一格的耐久由原版在事件之后扣，只剩 1 点时放行就会把镐挖坏
        boolean isDamageable = tool.isItemStackDamageable() && !player.capabilities.isCreativeMode;
        if (isDamageable && tool.getItemDamage() >= tool.getMaxDamage() - 1) {
            evt.setCanceled(true);
            return;
        }

        int radius = Math.min(level, ConfigLoader.rockBlasterMaxRange);

        World world = player.world;
        Block targetBlock = evt.getState().getBlock();

        List<BlockPos> targets = findConnectedBlocks(world, center, targetBlock, radius);
        if (targets.isEmpty()) {
            return;
        }

        int fortuneLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool);

        for (BlockPos pos : targets) {
            // 耐久不足时停下，留 2 点：1 点给中心那一格，1 点保住工具
            if (isDamageable && tool.getItemDamage() >= tool.getMaxDamage() - 2) {
                return;
            }

            long encoded = pos.toLong();
            processing.add(encoded);
            try {
                IBlockState blockState = world.getBlockState(pos);
                Block block = blockState.getBlock();

                // 二次校验，防止找完之后方块已被别的逻辑换掉
                if (!block.equals(targetBlock)) {
                    continue;
                }

                if (!mayBreak(world, pos, blockState, player, hand)) {
                    continue;
                }

                if (!player.capabilities.isCreativeMode) {
                    int exp = block.getExpDrop(blockState, world, pos, fortuneLevel) / 2;
                    block.onBlockHarvested(world, pos, blockState, player);
                    if (block.removedByPlayer(blockState, world, pos, player, true)) {
                        world.playEvent(2001, pos, Block.getStateId(blockState));
                        block.onBlockDestroyedByPlayer(world, pos, blockState);
                        block.harvestBlock(world, player, pos, blockState, world.getTileEntity(pos), tool);
                        // 经验减半，免得范围挖掘拿太多经验
                        block.dropXpOnBlockBreak(world, pos, exp);
                        if (isDamageable) {
                            tool.damageItem(1, player);
                        }
                    }
                } else {
                    // 创造模式：直接移除，不掉落
                    world.destroyBlock(pos, false);
                }
            } finally {
                processing.remove(encoded);
            }
        }
    }

    /**
     * 从中心出发沿六个方向广度优先，找出与它相连、且在半径立方体内的同种方块（不含中心）
     *
     * @param world       世界
     * @param center      起点
     * @param targetBlock 目标方块
     * @param maxRadius   切比雪夫半径
     * @return 要连带挖掉的坐标
     */
    @Nonnull
    private static List<BlockPos> findConnectedBlocks(@Nonnull World world, @Nonnull BlockPos center,
                                                      @Nonnull Block targetBlock, int maxRadius) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        visited.add(center.toLong());
        queue.add(center);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int i = 0; i < 6; i++) {
                int nx = current.getX() + DX[i];
                int ny = current.getY() + DY[i];
                int nz = current.getZ() + DZ[i];

                if (Math.abs(nx - cx) > maxRadius || Math.abs(ny - cy) > maxRadius || Math.abs(nz - cz) > maxRadius) {
                    continue;
                }

                mutablePos.setPos(nx, ny, nz);
                if (!visited.add(mutablePos.toLong())) {
                    continue;
                }

                if (world.getBlockState(mutablePos).getBlock().equals(targetBlock)) {
                    BlockPos found = new BlockPos(nx, ny, nz);
                    result.add(found);
                    queue.add(found);
                }
            }
        }

        return result;
    }

    /**
     * 连带挖的这一格先以玩家身份发一次破坏事件问领地 / 保护，被取消就不挖
     * <p>
     * 只有中心那一格走过了原版破坏流程的权限检查，相连的方块可能越过领地边界。
     * 发事件那一下把拿着镐的那只手临时清空：按手上工具连带挖的监听器（本附魔、别的范围挖掘附魔）
     * 看到空手就不会再挖一片，领地模组只看玩家和位置，照样拦得住。
     * 直接改背包格，不走 setHeldItem，不触发换装备的同步和音效。
     * </p>
     */
    private static boolean mayBreak(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state,
                                    @Nonnull EntityPlayer player, @Nonnull EnumHand hand) {
        InventoryPlayer inventory = player.inventory;
        boolean mainHand = hand == EnumHand.MAIN_HAND;
        int slot = inventory.currentItem;
        ItemStack held = mainHand ? inventory.mainInventory.get(slot) : inventory.offHandInventory.get(0);
        if (mainHand) {
            inventory.mainInventory.set(slot, ItemStack.EMPTY);
        } else {
            inventory.offHandInventory.set(0, ItemStack.EMPTY);
        }
        try {
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, state, player));
        } finally {
            if (mainHand) {
                inventory.mainInventory.set(slot, held);
            } else {
                inventory.offHandInventory.set(0, held);
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