package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

import javax.annotation.Nonnull;

/**
 * 沙布里里嚎叫附魔
 *
 * 武器附魔，叠层诅咒系统
 * 攻击时：
 * - 满蓄力命中才触发
 * - 给目标叠加沙布里里嚎叫效果（持续 = 等级 × 3秒，第一次命中 1 层，每次 +1，至多 6 层）
 * - 目标满层时，伤害增加 15% × 等级
 * - 攻击者自身受到癫火伤害（延迟 5 tick 后 3 秒内共 5% 最大生命值，创造模式免疫）
 * <p>
 * 以前没有效果时按 0 层再 +1，第一次命中就是 2 层，比说明早一级、满层少打一次；
 * 自损改走持续伤害管理器，时间回溯期间暂停扣血，耗死时也走正常的死亡流程。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "howl_shabriri",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentHowlShabriri extends EnchantmentBase {

    /** 效果等级上限（amplifier 5 即 6 层） */
    private static final int MAX_AMPLIFIER = 5;
    /** 满层后每级增加的伤害比例 */
    private static final float BONUS_DAMAGE_PER_LEVEL = 0.15F;
    /** 每级效果持续秒数 */
    private static final int STACK_SECONDS_PER_LEVEL = 3;
    /** 自损总量占最大生命值的比例 */
    private static final float SELF_DAMAGE_RATIO = 0.05F;
    /** 自损持续 tick 数 */
    private static final int SELF_DOT_DURATION = 60;
    /** 自损起始延迟（tick） */
    private static final int SELF_DOT_DELAY = 5;

    public EnchantmentHowlShabriri() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时叠加诅咒并自伤
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (!isFullyCharged(attacker)) {
            return;
        }

        // 当前层数，没有效果时为 -1
        int currentAmplifier = -1;
        PotionEffect current = victim.getActivePotionEffect(CarianStylePotion.HOWL_SHABRIRI);
        if (current != null) {
            currentAmplifier = current.getAmplifier();
        }

        // 满层时增伤
        if (currentAmplifier >= MAX_AMPLIFIER) {
            float bonusDamage = ctx.getDamage() * level * BONUS_DAMAGE_PER_LEVEL;
            ctx.addDamage(bonusDamage);
        }

        // 叠加沙布里里嚎叫效果：第一次 1 层，之后每次 +1
        int newAmplifier = currentAmplifier < 0 ? 0 : Math.min(currentAmplifier + 1, MAX_AMPLIFIER);
        victim.addPotionEffect(new PotionEffect(
                CarianStylePotion.HOWL_SHABRIRI,
                level * STACK_SECONDS_PER_LEVEL * 20,
                newAmplifier
        ));

        // 攻击者受到癫火伤害（创造模式免疫）
        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.EPILEPSY_FIRE_BURNING,
                    3 * 20 + 5,
                    0
            ));

            // 每 tick 扣 5% 最大生命 / 60，以命中时的最大生命为准
            float selfDamagePerTick = attacker.getMaxHealth() * SELF_DAMAGE_RATIO / SELF_DOT_DURATION;
            DamageOverTimeManager.applySelfLinear(attacker, selfDamagePerTick, SELF_DOT_DURATION, SELF_DOT_DELAY,
                    NewDamageSource.EPILEPSY_FIRE);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}