package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentFullMoon;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 暗月附魔
 *
 * 武器附魔，夜晚魔法伤害强化
 * 功能：
 * 1. 受到魔法伤害时减伤25%（有满月时37.5%）
 * 2. 造成魔法伤害时增伤25%（有满月时37.5%）
 * 3. 对锁定自己为目标的敌人额外增伤并吸血
 * 4. 治疗增强25%（有满月时37.5%）
 * 5. 持续夜视效果
 */
@AutoRegisterEnchantment(
        id = "dark_moon",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentDarkMoon extends EnchantmentBase {

    /** 参与计算的等级上限（只用来判断有没有） */
    private static final int LEVEL_CAP = 10;
    /** 魔法减伤 / 增伤 / 恢复加成比例 */
    private static final float RATIO = 0.25F;
    /** 同时装备满月时的比例（+50%） */
    private static final float RATIO_WITH_FULL_MOON = 0.375F;
    /** 对正在以你为目标的生物的额外伤害（当前生命比例） */
    private static final float EXTRA_DAMAGE = 0.05F;
    /** 同时装备满月时的额外伤害 */
    private static final float EXTRA_DAMAGE_WITH_FULL_MOON = 0.075F;
    /** 夜视每次给的时长（tick） */
    private static final int NIGHT_VISION_TICKS = 210;

    public EnchantmentDarkMoon() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    private static float ratio(boolean hasFullMoon) {
        return hasFullMoon ? RATIO_WITH_FULL_MOON : RATIO;
    }

    /**
     * 主手这把武器上的暗月等级
     */
    private static int mainHandLevel(@Nonnull EntityLivingBase entity, @Nonnull Enchantment darkMoon) {
        ItemStack heldItem = entity.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return 0;
        }
        return capLevel(EnchantmentHelper.getEnchantmentLevel(darkMoon, heldItem), LEVEL_CAP);
    }

    /**
     * 检查是否装备了满月附魔
     */
    private static boolean hasFullMoonEnchantment(@Nonnull EntityLivingBase entity) {
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return false;
        }

        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                if (EnchantmentHelper.getEnchantmentLevel(fullMoon, armor) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 夜晚：受到的魔法伤害 -25%；满蓄力近战魔法伤害 +25%，对正以你为目标的生物额外造成其当前生命 5% 并吸血
     * <p>
     * 受击减伤对玩家也生效，以前只对怪物生效（玩家拿着没有减伤）。
     * 只认主手，以前看的是「正在用的那只手」。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getEntity().world.isDaytime()) {
            return;
        }

        if (!evt.getSource().isMagicDamage()) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        // 受击者视角（减伤），玩家和怪物都生效
        if (!shouldBlockMobTrigger(victim, false) && mainHandLevel(victim, darkMoon) > 0) {
            evt.setAmount(evt.getAmount() * (1 - ratio(hasFullMoonEnchantment(victim))));
        }

        // 攻击者视角（增伤+吸血）
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        if (mainHandLevel(attacker, darkMoon) <= 0) {
            return;
        }

        if (!isFullyCharged(attacker)) {
            return;
        }

        boolean hasFullMoon = hasFullMoonEnchantment(attacker);
        evt.setAmount(evt.getAmount() * (1 + ratio(hasFullMoon)));

        if (victim instanceof EntityLiving) {
            EntityLiving livingVictim = (EntityLiving) victim;
            if (livingVictim.getAttackTarget() != null && livingVictim.getAttackTarget().equals(attacker)) {
                float extraDamage = hasFullMoon ? EXTRA_DAMAGE_WITH_FULL_MOON : EXTRA_DAMAGE;

                evt.setAmount(evt.getAmount() + livingVictim.getHealth() * extraDamage);

                float healAmount = Math.min(evt.getAmount() * extraDamage, attacker.getMaxHealth() * extraDamage);
                attacker.heal(healAmount);
            }
        }
    }

    /**
     * 夜晚恢复效果 +25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getEntity().world.isDaytime()) {
            return;
        }

        EntityLivingBase healer = evt.getEntityLiving();

        if (shouldBlockMobTrigger(healer, false)) {
            return;
        }

        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        if (mainHandLevel(healer, darkMoon) <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * (1 + ratio(hasFullMoonEnchantment(healer))));
    }

    /**
     * 夜晚主手拿着时持续夜视
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        if (evt.player.world.isDaytime()) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        if (!player.isEntityAlive()) {
            return;
        }

        if (mainHandLevel(player, darkMoon) > 0) {
            player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench);
    }
}