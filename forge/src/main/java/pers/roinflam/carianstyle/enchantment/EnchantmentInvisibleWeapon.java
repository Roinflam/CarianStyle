package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

/**
 * 隐形武器附魔
 * <p>
 * 武器/弓箭附魔，攻击后获得隐身
 * 攻击时：
 * - 获得隐身效果（持续 = 等级 × 10 tick）
 * - 箭矢攻击时持续时间 × 3
 * </p>
 *
 * <h3>v2.1 修复：构造函数用的类型与注解声明不一致</h3>
 * <p>
 * <b>问题：</b>注解声明的是 {@code customType = "ARMS"}（剑 + 弓），
 * 而构造函数传的是原版 {@code EnchantmentCategory.WEAPON}（只有剑）。
 * </p>
 * <p>
 * 由于 {@code EnchantmentRegistry.createEnchantmentInstance} 走无参构造函数分支时
 * 会丢弃注解解析出来的类型，实际生效的一直是构造函数里的 WEAPON——
 * 也就是说本附魔<b>从来没能附到弓上</b>，
 * 但它的核心逻辑 {@link #onDamageAsAttackerHighest} 里专门写了
 * 「箭矢攻击时隐身时长 × 3」的分支，这段代码在弓上永远跑不到。
 * </p>
 * <p>
 * <b>修复：</b>构造函数改用 {@code "ARMS"} 自定义类型，与注解和实际逻辑对齐。
 * </p>
 * <p>
 * <b>注意：</b>这是一次<b>扩大</b>适用范围的改动——修复后弓也会进入本附魔的
 * 附魔台候选池。若你的服务器不希望弓能附隐形武器，
 * 应当反向修改（把注解的 customType 改成 {@code type = EnchantmentCategory.WEAPON}）
 * 并同步删掉上面那段箭矢倍率逻辑。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "invisible_weapon",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        customType = "ARMS",
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentInvisibleWeapon extends EnchantmentBase {

    public EnchantmentInvisibleWeapon() {
        // v2.1：EnchantmentCategory.WEAPON → ARMS 自定义类型（剑+弓），与注解一致
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("ARMS"),
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        DamageSource damageSource = ctx.getDamageSource();

        // 计算持续时间倍率
        int magnification = 1;
        if (damageSource != null && damageSource.getDirectEntity() instanceof AbstractArrow) {
            magnification += 2;  // 箭矢攻击倍率 = 3
        }

        // 施加隐身效果
        DynamicAttributeManager.apply(attacker,
                DynamicAttributes.STEALTH.createInstance(level * 20 * magnification));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentHypnoticArrow.class));
    }
}
