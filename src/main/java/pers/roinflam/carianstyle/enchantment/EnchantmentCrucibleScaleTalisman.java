package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.utils.Reference;
import javax.annotation.Nonnull;

/**
 * 坩埚鳞片护符附魔
 * <p>
 * 受到的伤害不低于自身最大生命时（会被一击打死的伤害），按全身等级减伤：每级 15%，最多算 6 级。
 * </p>
 * <p>v2.1：LivingDamage受击者致命减伤入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "crucible_scale_talisman",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentCrucibleScaleTalisman extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.crucible_scale_talisman.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "crucible_scale_talisman";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 参与减伤计算的等级上限（低于总等级上限）
     * <p>默认 6，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle EFFECTIVE_LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "effective_level_cap",
                    6, 1, 100);

    /**
     * 每级的减伤比例
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.15D, 0.0D, 1.0D);

    public EnchantmentCrucibleScaleTalisman() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (evt.getAmount() < victim.getMaxHealth()) {
            return;
        }

        // v2.1：怪物附魔触发开关（受击者视角，致命伤害减伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment crucibleScaleTalisman = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCrucibleScaleTalisman.class);
        if (crucibleScaleTalisman == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(crucibleScaleTalisman, armor);
            }
        }

        totalLevel = applyLevelLimit(crucibleScaleTalisman, totalLevel, LEVEL_CAP.getInt());

        if (totalLevel <= 0) {
            return;
        }

        float reduction = evt.getAmount() * Math.min(totalLevel, EFFECTIVE_LEVEL_CAP.getInt())
                * (float) REDUCTION_PER_LEVEL.get();
        evt.setAmount(evt.getAmount() - reduction);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
