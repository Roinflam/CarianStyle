package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>死诞者附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "living_corpse",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
public class EnchantmentLivingCorpse extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "living_corpse";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 复活的冷却时间（tick）
     * <p>默认 4800，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle REVIVE_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "revive_cooldown",
                    4800, 20, 144000);

    /**
     * 复活后每秒流失的最大生命占比基数
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DRAIN_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "drain_ratio_per_second",
                    0.01D, 0.0D, 1.0D);

    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";

    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /**
     * 正在跑的流血任务，实体 UUID → 任务。
     * <p>玩家切维度实体对象不变，留着原任务；怪物切维度、玩家重新登录是新对象，原任务作废，换新的接上。</p>
     */
    private static final Map<UUID, Object> RUNNING_BLEEDS = new ConcurrentHashMap<>();

    public EnchantmentLivingCorpse() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    /**
     * 判断是否为死亡类附魔（互斥）
     *
     * @param ench 待检查的附魔
     * @return 是否为死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench instanceof EnchantmentFullMoon || ench instanceof EnchantmentTimeReversal;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
