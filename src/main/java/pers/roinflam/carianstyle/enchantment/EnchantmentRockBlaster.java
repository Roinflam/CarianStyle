package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.util.EnumHand;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import java.util.*;

/**
 * 岩石爆破附魔
 * <p>
 * 镐子附魔，BFS 连通范围挖掘
 * 挖掘方块时：
 * - 从破坏点向外 BFS 扩散，仅破坏与之六面相邻连通的同类型方块
 * - 范围半径 = 等级（受配置上限限制）
 * - 耐久只剩 1 点时拒绝挖掘，连带挖给中心那一格留够耐久，镐不会被挖坏
 * </p>
 *
 * <h3>v3.2 修复：附魔类型由 DIGGER 收窄为 PICKAXE</h3>
 * <p>
 * <b>问题：</b>原来注解与构造函数都用的是原版 {@code EnumEnchantmentType.DIGGER}，
 * 而 DIGGER 的判定是「挖掘工具」（1.20.1 是 {@code DiggerItem}，1.12.2 是 {@code ItemTool}）——
 * 它同时包含<b>镐、斧、锹、锄</b>四类工具。
 * </p>
 * <p>
 * 但本附魔的实际逻辑在 {@link #onBreak} 里明确要求
 * {@code tool.getItem() instanceof ItemPickaxe}，也就是<b>只有镐子才会生效</b>。
 * 于是玩家用附魔台附斧头 / 锹 / 锄时，岩石爆破照样会出现在候选池里并被刷出来，
 * 附上去之后却毫无效果，在玩家看来就是「工具附魔串了、刷出来一堆没用的」。
 * </p>
 * <p>
 * <b>修复：</b>改用 {@code CarianStyleEnchantments} 中定义的自定义类型
 * {@code "PICKAXE"}，让候选池与实际生效条件一致。
 * 注解的 {@code type} 参数随之替换为 {@code customType}，两边保持同步。
 * </p>
 * <p>
 * <b>兼容性：</b>已经附在斧 / 锹 / 锄上的旧存档物品不会被自动清除，
 * 但它们本来就没有任何效果；铁砧与附魔台之后不会再把本附魔加到这些工具上。
 * </p>
 *
 * @author RoinFlam
 * @version 3.2
 */
@AutoRegisterEnchantment(
        id = "rock_blaster",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        // v3.2：DIGGER（镐斧锹锄）→ PICKAXE（仅镐），与下方 onBreak 的 ItemPickaxe 判定一致
        customType = "PICKAXE",
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentRockBlaster extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.rock_blaster.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "rock_blaster";

    /**
     * 参与计算的等级上限（连锁范围随等级增长）
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);


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

    /**
     * 常见废石方块注册名集合
     * 开启 rockBlasterSuppressCommonDrops 配置后，碎岩者破坏这些方块时不产生掉落物，
     * 以减少大范围挖掘时掉落物实体数量导致的卡顿
     */
    private static final Set<String> COMMON_STONE_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "minecraft:stone",
            "minecraft:cobblestone",
            "minecraft:deepslate",
            "minecraft:cobbled_deepslate",
            "minecraft:granite",
            "minecraft:diorite",
            "minecraft:andesite",
            "minecraft:tuff",
            "minecraft:calcite",
            "minecraft:smooth_basalt",
            "minecraft:gravel",
            "minecraft:netherrack",
            "minecraft:basalt",
            "minecraft:blackstone",
            "minecraft:end_stone"
    )));

    public EnchantmentRockBlaster() {
        // v3.2：DIGGER → PICKAXE 自定义类型（详见类注释）
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("PICKAXE"),
                new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
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
        // 1.12.2 版：BlockPos 没有静态的 asLong，照 BlockPos.toLong 的位布局（X 26 位、Y 12 位、Z 26 位）自己拼
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
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
     * @param targetState 起点方块的状态（按 {@link #sameKind} 比较）
     * @param maxRadius   最大扩散半径（切比雪夫距离）
     * @return 需要破坏的连通同类方块坐标列表
     */
    private static List<BlockPos> findConnectedBlocks(
            World world, BlockPos center, IBlockState targetState, int maxRadius) {

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
                mutablePos.setPos(nx, ny, nz);
                if (sameKind(world.getBlockState(mutablePos), targetState)) {
                    // 确认需要破坏时才创建不可变 BlockPos 加入结果
                    result.add(new BlockPos(nx, ny, nz));
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }

        return result;
    }

    /**
     * 亮的红石矿归到普通红石矿
     * <p>1.12.2 开始挖红石矿时 {@code onBlockClicked} 就把它换成了另一个方块 {@code LIT_REDSTONE_ORE}，
     * 1.20.1 亮不亮只是同一个方块的 {@code LIT} 属性，不归一的话整条矿脉连不起来。</p>
     */
    private static Block normalize(Block block) {
        return block == Blocks.LIT_REDSTONE_ORE ? Blocks.REDSTONE_ORE : block;
    }

    /**
     * 两个方块状态是不是「同一种方块」（对应 1.20.1 的同一个 {@code Block}）
     * <p>
     * 1.12.2 还没扁平化：花岗岩 / 闪长岩 / 安山岩和它们的磨制版、16 色混凝土和陶瓦、石砖的苔石 / 裂纹 / 錾制、
     * 砂岩和石英的变种都是同一个 {@code Block} 的不同元数据，只比 {@code Block} 会顺着连到别的颜色、别的变种。
     * 再比一次 {@code damageDropped}：它只看变种，不看原木轴向、石英柱方向、台阶上下半这些摆放属性，
     * 正好对应 1.20.1 的方块 id。泥土和灰化土的 {@code damageDropped} 都是 0，会被当成一种，影响很小，不单独处理。
     * </p>
     */
    private static boolean sameKind(IBlockState state, IBlockState target) {
        Block a = normalize(state.getBlock());
        Block b = normalize(target.getBlock());
        return a == b && a.damageDropped(state) == b.damageDropped(target);
    }

    /**
     * 判断指定方块是否属于常见废石，需要在配置开启时抑制掉落
     * <p>1.12.2 的 {@code minecraft:stone} 还包着花岗岩、闪长岩、安山岩和三种磨制版；磨制版在 1.20.1 是
     * {@code polished_*}，不在废石列表里，这里只认石头本身和三种原石。</p>
     *
     * @param state 要检查的方块状态
     * @return 如果配置开启且方块在废石列表中则返回 true
     */
    private static boolean shouldSuppressDrop(IBlockState state) {
        if (!ConfigLoader.rockBlasterSuppressCommonDrops) {
            return false;
        }
        Block block = state.getBlock();
        if (block == Blocks.STONE) {
            BlockStone.EnumType variant = state.getValue(BlockStone.VARIANT);
            return variant == BlockStone.EnumType.STONE || variant == BlockStone.EnumType.GRANITE
                    || variant == BlockStone.EnumType.DIORITE || variant == BlockStone.EnumType.ANDESITE;
        }
        // 从注册表获取方块的注册名进行匹配
        net.minecraft.util.ResourceLocation key = block.getRegistryName();
        return key != null && COMMON_STONE_IDS.contains(key.toString());
    }

    /**
     * 方块破坏事件处理
     *
     * @param evt 方块破坏事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(@Nonnull BlockEvent.BreakEvent evt) {
        // 仅服务端处理
        if (evt.getPlayer().world.isRemote) {
            return;
        }

        // 重入保护：若当前方块正在被范围挖掘处理，直接跳过，避免递归
        Set<Long> processing = processingBlocks.get();
        BlockPos centerPos = evt.getPos();
        long centerEncoded = encodeXYZ(centerPos.getX(), centerPos.getY(), centerPos.getZ());
        if (processing.contains(centerEncoded)) {
            return;
        }

        EntityPlayer player = evt.getPlayer();
        // 1.12.2 版：原版挖方块固定用主手的工具（PlayerInteractionManager.tryHarvestBlock 读的就是主手），
        // 1.20.1 的 getUsedItemHand() 在不使用物品时同样返回主手
        EnumHand hand = EnumHand.MAIN_HAND;

        ItemStack tool = player.getHeldItem(hand);
        if (tool.isEmpty() || !(tool.getItem() instanceof ItemPickaxe)) {
            return;
        }

        // 懒加载缓存附魔实例
        if (cachedEnchantment == null) {
            cachedEnchantment = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRockBlaster.class);
        }
        if (cachedEnchantment == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(cachedEnchantment, tool);

        level = applyLevelLimit(cachedEnchantment, level, LEVEL_CAP.getInt());

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

        // 提前提取 WorldServer，非创造模式下必须是 WorldServer 才能处理掉落
        WorldServer serverLevel = null;
        if (world instanceof WorldServer) {
            WorldServer sl = (WorldServer) world;
            serverLevel = sl;
        } else if (!player.capabilities.isCreativeMode) {
            return;
        }

        IBlockState targetState = evt.getState();

        // BFS 查找连通同类方块
        List<BlockPos> targets = findConnectedBlocks(world, centerPos, targetState, radius);

        if (targets.isEmpty()) {
            return;
        }

        // 提前获取时运等级，避免循环内重复查找附魔
        int fortuneLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool);

        // 提前判断目标方块是否需要抑制掉落物（同一批 BFS 结果全是同类方块，只需判断一次）
        boolean suppressDrop = shouldSuppressDrop(targetState);

        for (BlockPos pos : targets) {

            // 耐久不足时立即终止整个范围挖掘，留 2 点：1 点给中心那一格，1 点保住工具
            if (isDamageable && tool.getItemDamage() >= tool.getMaxDamage() - 2) {
                return;
            }

            long encoded = encodeXYZ(pos.getX(), pos.getY(), pos.getZ());

            // 将该坐标加入重入保护集合
            processing.add(encoded);
            try {
                IBlockState blockState = world.getBlockState(pos);
                Block block = blockState.getBlock();

                // 二次校验，防止 BFS 之后方块已被其他逻辑移除
                if (!sameKind(blockState, targetState)) {
                    continue;
                }

                if (serverLevel != null && !mayBreak(serverLevel, pos, blockState, player, hand)) {
                    continue;
                }

                if (!player.capabilities.isCreativeMode) {
                    // 生存模式破坏方块（1.12.2 版：顺序照 PlayerInteractionManager.tryHarvestBlock）
                    block.onBlockHarvested(world, pos, blockState, player);

                    // willHarvest 参数：抑制掉落时传 false，阻止方块保留状态给后续掉落逻辑
                    if (block.removedByPlayer(blockState, world, pos, player, !suppressDrop)) {
                        // 1.20.1 的 playerWillDestroy 自带碎块粒子和声音，1.12.2 要自己发
                        world.playEvent(2001, pos, Block.getStateId(blockState));
                        block.onBlockDestroyedByPlayer(world, pos, blockState);

                        if (!suppressDrop) {
                            // 非废石方块：正常产生掉落物，经验减半以避免范围挖掘获得过多经验
                            block.harvestBlock(world, player, pos, blockState, world.getTileEntity(pos), tool);
                            block.dropXpOnBlockBreak(world, pos,
                                    block.getExpDrop(blockState, world, pos, fortuneLevel) / 2);
                        }
                        // 废石方块：跳过 harvestBlock 和 dropXpOnBlockBreak，方块直接消失，不产生掉落物和经验

                        // 无论是否抑制掉落物，耐久消耗照常
                        if (isDamageable) {
                            tool.damageItem(1, player);
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

    /**
     * 连带挖的这一格先以玩家身份问一遍领地 / 保护（{@link RockBlasterProbeEvent}），被取消就不挖。
     * 只有中心那一格走过了原版破坏流程的权限检查，相连的方块可能越过领地边界（在自己地皮边上挖到路或隔壁地皮）。
     * 发事件那一下把拿着镐的那只手临时清空：按手上工具连带挖的监听器（本附魔、别的范围挖掘附魔）看到空手就不会再挖一片，
     * 领地模组只看玩家和位置，照样拦得住。直接改背包格、不走 setHeldItem，不触发换装备的事件和音效。
     */
    private static boolean mayBreak(WorldServer level, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand) {
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
            return !MinecraftForge.EVENT_BUS.post(new RockBlasterProbeEvent(level, pos, state, player));
        } finally {
            if (mainHand) {
                inventory.mainInventory.set(slot, held);
            } else {
                inventory.offHandInventory.set(0, held);
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
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.UNBREAKING);
    }
}
