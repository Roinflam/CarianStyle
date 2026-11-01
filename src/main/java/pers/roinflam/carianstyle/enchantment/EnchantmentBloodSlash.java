package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 血斩附魔
 *
 * 攻击：增伤（目标血量×等级×5%，上限目标最大血量），代价扣除自身10%血量
 * 击杀：治疗（有BloodCollection：5%，无：2.5%）
 */
@AutoRegisterEnchantment(
        id = "blood_slash",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentBloodSlash extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 每级额外伤害（目标当前生命的比例） */
    private static final float BONUS_DAMAGE_PER_LEVEL = 0.05F;
    /** 每下满蓄力攻击的代价（最大生命的比例） */
    private static final float SELF_DAMAGE_RATIO = 0.1F;
    /** 击杀回血（每级，最大生命的比例） */
    private static final float HEAL_PER_LEVEL = 0.025F;
    /** 同时有鲜血征收时的击杀回血（每级，最大生命的比例） */
    private static final float HEAL_PER_LEVEL_WITH_COLLECTION = 0.05F;

    public EnchantmentBloodSlash() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 满蓄力攻击额外造成目标当前生命 [等级]×5% 的伤害，自己付 10% 最大生命
     * <p>
     * 代价直接扣血；生命值不高于 10% 时直接死亡。普拉顿桑克斯回溯期间不付代价，
     * 否则这一下会被算进储存伤害。创造模式不付。
     * </p>
     */
    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = capLevel(level, LEVEL_CAP);

        if (!isFullyCharged(attacker)) {
            return;
        }

        float bonusDamage = Math.min(victim.getHealth() * effectiveLevel * BONUS_DAMAGE_PER_LEVEL, victim.getMaxHealth());
        ctx.addDamage(bonusDamage);

        if ((!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative())
                && !EnchantmentTimeReversal.isReversing(attacker)) {
            float cost = attacker.getMaxHealth() * SELF_DAMAGE_RATIO;
            if (attacker.getHealth() > cost) {
                EntityLivingUtil.damageHealthDirectly(attacker, cost);
            } else {
                EntityLivingUtil.kill(attacker, NewDamageSource.HEMORRHAGE);
            }
        }
    }

    /**
     * 用主手的这把武器击杀生物时回血
     * <p>以前看的是「正在用的那只手」；怪物击杀受「允许怪物触发附魔」配置控制。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (!killer.isEntityAlive() || evt.getEntityLiving().equals(killer)) {
            return;
        }

        if (shouldBlockMobTrigger(killer, false)) {
            return;
        }

        ItemStack heldItem = killer.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
        if (bloodSlash == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(bloodSlash, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP);
        }

        if (level <= 0) {
            return;
        }

        Enchantment bloodCollection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodCollection.class);
        if (bloodCollection != null && EnchantmentHelper.getEnchantmentLevel(bloodCollection, heldItem) > 0) {
            killer.heal(killer.getMaxHealth() * level * HEAL_PER_LEVEL_WITH_COLLECTION);
        } else {
            killer.heal(killer.getMaxHealth() * level * HEAL_PER_LEVEL);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}