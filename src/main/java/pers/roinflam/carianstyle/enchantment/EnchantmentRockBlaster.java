package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>岩石爆破附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "rock_blaster",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        // v3.2：DIGGER（镐斧锹锄）→ PICKAXE（仅镐），与下方 onBreak 的 PickaxeItem 判定一致
        customType = "PICKAXE",
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {}
)
public class EnchantmentRockBlaster extends EnchantmentBase {

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
