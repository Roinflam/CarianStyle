package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

/**
 * 不动之盾附魔
 * <p>
 * 举盾时：完全挡住一下攻击，清掉攻击者身上所有药水效果，并回复自身最大生命 1% × 等级；
 * 没完全挡住（伤害还剩一些）时减伤 10% × 等级。
 * </p>
 * <p>
 * 修复记录：构造函数 BREAKABLE → CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD")
 * 原bug：所有可损坏物品都能附上此盾牌专属附魔
 * </p>
 * <p>
 * 1.12.2 版：盾牌挡住时原版把伤害改成 0 后照样发 {@code LivingHurtEvent}，和 1.20.1 一样能在这里判「完全格挡」；
 * 清效果用 {@code clearActivePotions()}（1.20.1 是 {@code removeAllEffects()}，同样逐个发移除事件）。
 * </p>
 *
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "immutable_shield", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, customType = "SHIELD", slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND})
public class EnchantmentImmutableShield extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.immutable_shield.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "immutable_shield";

    /**
     * 格挡成功时每级回复的最大生命占比
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.01D, 0.0D, 1.0D);

    /**
     * 每级的减伤比例
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.1D, 0.0D, 1.0D);

    public EnchantmentImmutableShield() {
        // 修复：BREAKABLE → SHIELD自定义类型
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND
        });
    }

    @Override
    protected void onHurtAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();
        if (!victim.isHandActive()) return;
        ItemStack activeItem = victim.getHeldItem(victim.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) return;
        if (!ctx.getEnchantedItem().equals(activeItem)) return;
        if (ctx.getDamage() <= 0 && ctx.getAttacker() != null) {
            // 完全格挡：清除攻击者药水效果，治疗自己
            ctx.getAttacker().clearActivePotions();
            victim.heal(victim.getMaxHealth() * level * (float) HEAL_PER_LEVEL.get());
        } else {
            // 未完全格挡：减伤 10% × 等级
            ctx.reduceDamage(ctx.getDamage() * level * (float) REDUCTION_PER_LEVEL.get());
        }
    }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class));
    }
}
