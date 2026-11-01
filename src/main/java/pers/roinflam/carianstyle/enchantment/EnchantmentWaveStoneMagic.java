package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentDoomedDeath;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * 波石魔法附魔
 *
 * 武器附魔，魔法伤害增强
 * 造成魔法伤害时：
 * - 伤害 ×1.5
 * - 这一下不再算魔法伤害（转为物理伤害）
 * <p>
 * 改成在原伤害上就地处理：以前取消原伤害、再用一个没有攻击者的新伤害源重打，
 * 击杀归属（经验、玩家专属掉落、击杀类附魔计数）、击退和原伤害源上的无视护甲等属性都会丢。
 * 挂在 LOW：亚杜拉的月光剑、黑暗弃子在更早的优先级把伤害转成魔法，要排在它们之后才看得到。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "wave_stone_magic",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
public class EnchantmentWaveStoneMagic extends EnchantmentBase {

    // 魔法伤害的倍率
    private static final float DAMAGE_MULTIPLIER = 1.5F;

    /**
     * DamageSource 的魔法标记（1.12.2 只有置位的 setMagicDamage，没有清除的方法）；
     * 开发环境下按 SRG 名会自动换成 MCP 名
     */
    private static Field magicDamageField;
    private static boolean magicDamageFieldResolved;

    public EnchantmentWaveStoneMagic() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 魔法伤害 ×1.5 并去掉魔法标记
     */
    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource source = ctx.getDamageSource();
        if (source == null || !source.isMagicDamage() || ctx.getVictim() == null) {
            return;
        }

        ctx.multiplyDamage(DAMAGE_MULTIPLIER);

        // 只改每次攻击新建的实体伤害源；DamageSource.MAGIC 这类全局共用的常量改了会影响所有地方
        if (source instanceof EntityDamageSource) {
            clearMagicDamage(source);
        }
    }

    private static synchronized void clearMagicDamage(@Nonnull DamageSource source) {
        if (!magicDamageFieldResolved) {
            magicDamageFieldResolved = true;
            try {
                magicDamageField = ObfuscationReflectionHelper.findField(DamageSource.class, "field_82730_x");
            } catch (Exception e) {
                LogUtil.debug("无法获取 DamageSource 的魔法标记字段");
            }
        }
        if (magicDamageField != null) {
            try {
                magicDamageField.setBoolean(source, false);
            } catch (Exception e) {
                // 忽略：只少了「转为物理」这一半，倍率照常生效
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDoomedDeath.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentDeathBlade.class));
    }
}