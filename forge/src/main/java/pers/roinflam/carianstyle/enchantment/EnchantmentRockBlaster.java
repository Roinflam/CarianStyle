package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import java.util.*;

/**
 * 岩石爆破附魔
 * <p>
 * 镐子附魔，BFS 连通范围挖掘
 * 挖掘方块时：
 * - 从破坏点向外 BFS 扩散，仅破坏与之六面相邻连通的同类型方块
 * - 范围半径 = 1 + 等级/2（受配置上限限制）
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
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

    /**
     * 缓存的附魔实例，首次使用时懒加载，避免每次事件触发时重复查找注册表
     */
    private static Enchantment cachedEnchantment = null;

    /**
     * 重入保护：记录当前线程正在被范围挖掘处理的方块坐标（编码为 long）
     * 防止范围破坏触发的子 BreakEvent 再次进入本逻辑，导致指数级性能爆炸
     */
    private static final ThreadLocal<Set<Long>> processingBlocks =
            ThreadLocal.withInitial(HashSet::new);

    /**
     * BFS 六方向偏移量（X/Y/Z 分量）
     * 仅扩散六面相邻，不包括对角线，保证连通性语义正确
     */
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    public EnchantmentRockBlaster() {
        super(EnchantmentCategory.DIGGER, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 将坐标分量编码为 long，用于高效的 Set 去重，避免创建 BlockPos 包装对象
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     * @return 编码后的 long 值
     */
    private static long encodeXYZ(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    /**
     * BFS 查找以 center 为起点，六面相邻连通的同类型方块坐标列表
     * <p>
     * 使用坐标分量直接操作，避免在 BFS 过程中频繁创建临时 BlockPos 对象
     * 只有真正需要破坏的方块才会创建不可变 BlockPos 加入结果列表
     * </p>
     *
     * @param world       世界
     * @param center      起点坐标（不含于结果列表中）
     * @param targetBlock 目标方块类型
     * @param maxRadius   最大扩散半径（切比雪夫距离）
     * @return 需要破坏的连通同类方块坐标列表
     */
    private static List<BlockPos> findConnectedBlocks(
            Level world, BlockPos center, Block targetBlock, int maxRadius) {

        List<BlockPos> result = new ArrayList<>();

        // visited 集合用 long 编码去重，避免创建大量 BlockPos 对象
        Set<Long> visited = new HashSet<>();

        // BFS 队列存储坐标分量的 int[]，避免队列内部持有大量 BlockPos 对象
        Queue<int[]> queue = new ArrayDeque<>();

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        visited.add(encodeXYZ(cx, cy, cz));
        queue.add(new int[]{cx, cy, cz});

        // 复用 MutableBlockPos 仅用于 getBlockState 查询，减少对象分配
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int curX = current[0];
            int curY = current[1];
            int curZ = current[2];

            for (int i = 0; i < 6; i++) {
                int nx = curX + DX[i];
                int ny = curY + DY[i];
                int nz = curZ + DZ[i];

                // 超出切比雪夫半径，剪枝
                if (Math.abs(nx - cx) > maxRadius ||
                        Math.abs(ny - cy) > maxRadius ||
                        Math.abs(nz - cz) > maxRadius) {
                    continue;
                }

                long encoded = encodeXYZ(nx, ny, nz);
                if (visited.contains(encoded)) {
                    continue;
                }
                visited.add(encoded);

                // 使用 MutableBlockPos 查询方块状态，避免为每个邻居创建新 BlockPos
                mutablePos.set(nx, ny, nz);
                if (world.getBlockState(mutablePos).getBlock().equals(targetBlock)) {
                    // 确认需要破坏时才创建不可变 BlockPos 加入结果
                    result.add(new BlockPos(nx, ny, nz));
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        return result;
    }

    /**
     * 方块破坏事件处理
     *
     * @param evt 方块破坏事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(@NotNull BlockEvent.BreakEvent evt) {
        // 仅服务端处理
        if (evt.getPlayer().level().isClientSide) {
            return;
        }

        // 重入保护：若当前方块正在被范围挖掘处理，直接跳过，避免递归
        Set<Long> processing = processingBlocks.get();
        BlockPos centerPos = evt.getPos();
        long centerEncoded = encodeXYZ(centerPos.getX(), centerPos.getY(), centerPos.getZ());
        if (processing.contains(centerEncoded)) {
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

        // 懒加载缓存附魔实例
        if (cachedEnchantment == null) {
            cachedEnchantment = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRockBlaster.class);
        }
        if (cachedEnchantment == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(cachedEnchantment, tool);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        int radius = Math.min(1 + level / 2, ConfigLoader.rockBlasterMaxRange);

        Level world = player.level();

        // 提前提取 ServerLevel，非创造模式下必须是 ServerLevel 才能处理掉落
        ServerLevel serverLevel = null;
        if (world instanceof ServerLevel sl) {
            serverLevel = sl;
        } else if (!player.getAbilities().instabuild) {
            return;
        }

        Block targetBlock = evt.getState().getBlock();

        // BFS 查找连通同类方块
        List<BlockPos> targets = findConnectedBlocks(world, centerPos, targetBlock, radius);

        if (targets.isEmpty()) {
            return;
        }

        // 提前判断工具是否可损耗，避免循环内重复调用
        boolean isDamageable = tool.isDamageableItem();

        // 提前获取时运等级，避免循环内重复查找附魔
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);

        for (BlockPos pos : targets) {

            // 工具耐久不足时立即终止整个范围挖掘
            if (isDamageable && tool.getDamageValue() >= tool.getMaxDamage() - 1) {
                return;
            }

            long encoded = encodeXYZ(pos.getX(), pos.getY(), pos.getZ());

            // 将该坐标加入重入保护集合
            processing.add(encoded);
            try {
                BlockState blockState = world.getBlockState(pos);
                Block block = blockState.getBlock();

                // 二次校验，防止 BFS 之后方块已被其他逻辑移除
                if (!block.equals(targetBlock)) {
                    continue;
                }

                if (!player.getAbilities().instabuild) {
                    // 生存模式：触发破坏粒子、掉落物、耐久消耗、经验
                    block.playerWillDestroy(world, pos, blockState, player);

                    if (block.onDestroyedByPlayer(blockState, world, pos, player, true, world.getFluidState(pos))) {
                        block.destroy(world, pos, blockState);
                        block.playerDestroy(world, player, pos, blockState, world.getBlockEntity(pos), tool);

                        // 经验减半，避免范围挖掘获得过多经验
                        block.popExperience(serverLevel, pos,
                                block.getExpDrop(blockState, serverLevel, serverLevel.random, pos, fortuneLevel, 0) / 2);

                        if (isDamageable) {
                            tool.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
                        }
                    }
                } else {
                    // 创造模式：直接移除方块，不产生掉落物
                    world.destroyBlock(pos, false);
                }
            } finally {
                // 无论成功与否，必须移除重入标记
                processing.remove(encoded);
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