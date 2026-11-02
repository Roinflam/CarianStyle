package pers.roinflam.carianstyle.enchantment;

import net.minecraft.util.DamageSource;
import net.minecraft.potion.PotionEffect;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 辉石冰附魔
 * <p>
 * 弓箭附魔，箭矢伤害转为魔法并施加冻伤
 * 箭矢命中时：
 * - 伤害转为魔法伤害
 * - 施加冻伤效果（持续10秒，效果等级 = 附魔等级 - 1）
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - 移除无效的 conflictsWith = {Enchantments.class}
 *   Enchantments 是原版附魔的持有者类（包含静态字段如 FLAMING_ARROWS），
 *   不是 Enchantment 的子类，checkCompatibility 中的 isInstance 检查永远不会匹配。
 *   与火矢的互斥已在 checkCompatibility 方法中正确实现。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "pyroxene_ice",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
        // v2.1修复：移除 conflictsWith = {Enchantments.class}，该配置无效
        // 与火矢(FLAMING_ARROWS)的互斥通过下方 checkCompatibility 方法实现
)
public class EnchantmentPyroxeneIce extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.pyroxene_ice.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "pyroxene_ice";

    /**
     * 施加效果的持续秒数
     * <p>默认 10，允许范围 1 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_SECONDS =
            EnchantmentValues.define(VALUE_ID, "effect_seconds",
                    10, 1, 300);

    public EnchantmentPyroxeneIce() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        // 必须是箭矢伤害
        if (damageSource == null || !(damageSource.getImmediateSource() instanceof EntityArrow)) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 转为魔法伤害
        DamageSourceUtil.setMagicDamage(damageSource);

        // 施加冻伤效果
        victim.addPotionEffect(new PotionEffect(
                CarianStylePotion.FROSTBITE.get(),
                EFFECT_SECONDS.getInt() * 20,
                level - 1
        ));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        // 与火矢互斥
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.FLAME);
    }
}
