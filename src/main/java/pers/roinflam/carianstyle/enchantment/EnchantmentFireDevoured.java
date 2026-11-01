package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 火焰吞噬附魔
 *
 * 攻击者着火时，满蓄力命中后对目标周围的生物造成火焰伤害并点燃
 * <p>
 * 伤害 = 这一下护甲后的最终伤害 × 等级 × 15%，半径 = 等级（至多 8 格），一次至多 16 个；
 * 不波及持有者自己、同类、队友和自己的宠物。
 * 以前挂在攻击事件上，那时还取不到伤害值，火焰伤害恒为 0，只剩点燃；
 * 过滤条件写成了「除目标外全部」，连持有者自己和别的玩家都会被点着；也不看蓄力。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "fire_devoured",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentFireDevoured extends EnchantmentBase {

    /** 搜索半径上限（格） */
    private static final int MAX_SEARCH_RADIUS = 8;
    /** 单次触发至多波及的目标数 */
    private static final int MAX_TARGETS = 16;
    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;
    /** 每级火焰伤害占这一下伤害的比例 */
    private static final float DAMAGE_PER_LEVEL = 0.15F;
    /** 点燃秒数 */
    private static final int IGNITE_SECONDS = 10;

    public EnchantmentFireDevoured() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 每次命中至多 16 次火焰伤害加点燃，连点会成倍放大，要求满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        int effectiveLevel = capLevel(level, LEVEL_CAP);

        // 攻击者必须着火
        if (EntityUtil.getFire(attacker) <= 0) {
            return;
        }

        int searchRadius = Math.min(effectiveLevel, MAX_SEARCH_RADIUS);

        // 除了被打的目标，只波及别人：持有者自己、同类、队友和自己的宠物都不烧
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                searchRadius,
                entity -> entity != null && !entity.equals(victim) && !isSameKindOrAlly(attacker, entity)
        );

        float damage = ctx.getDamage() * effectiveLevel * DAMAGE_PER_LEVEL;

        int hitCount = 0;
        for (EntityLivingBase entity : nearbyEntities) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            entity.attackEntityFrom(DamageSource.IN_FIRE, damage);

            // 燃烧时间不足 200 tick 时点燃 10 秒
            if (EntityUtil.getFire(entity) < 200) {
                entity.setFire(IGNITE_SECONDS);
            }
            hitCount++;
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}