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

    public EnchantmentDarkMoon() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
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
     * 处理伤害事件
     * 由于涉及复杂的双向逻辑和特殊检查，保留静态监听器
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

        // 受击者视角（减伤）
        if (victim instanceof EntityLiving) {
            EntityLiving livingVictim = (EntityLiving) victim;

            if (!livingVictim.getHeldItem(livingVictim.getActiveHand()).isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(
                        darkMoon,
                        livingVictim.getHeldItem(livingVictim.getActiveHand())
                );

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    boolean hasFullMoon = hasFullMoonEnchantment(livingVictim);
                    float reduction = hasFullMoon ? 0.375f : 0.25f;
                    evt.setAmount(evt.getAmount() * (1 - reduction));
                }
            }
        }

        // 攻击者视角（增伤+吸血）
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                darkMoon,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        if (attacker instanceof EntityPlayer) {
            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                return;
            }
        }

        boolean hasFullMoon = hasFullMoonEnchantment(attacker);
        float damageBonus = hasFullMoon ? 0.375f : 0.25f;

        evt.setAmount(evt.getAmount() * (1 + damageBonus));

        if (victim instanceof EntityLiving) {
            EntityLiving livingVictim = (EntityLiving) victim;
            if (livingVictim.getAttackTarget() != null && livingVictim.getAttackTarget().equals(attacker)) {
                float extraDamage = hasFullMoon ? 0.075f : 0.05f;

                evt.setAmount(evt.getAmount() + livingVictim.getHealth() * extraDamage);

                float healAmount = Math.min(evt.getAmount() * extraDamage, attacker.getMaxHealth() * extraDamage);
                attacker.heal(healAmount);
            }
        }
    }

    /**
     * 治疗增强
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
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        if (healer.getHeldItem(healer.getActiveHand()).isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                darkMoon,
                healer.getHeldItem(healer.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        boolean hasFullMoon = hasFullMoonEnchantment(healer);
        float healBonus = hasFullMoon ? 0.375f : 0.25f;
        evt.setAmount(evt.getAmount() * (1 + healBonus));
    }

    /**
     * 持续夜视效果
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

        if (player.getHeldItem(player.getActiveHand()).isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                darkMoon,
                player.getHeldItem(player.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level > 0) {
            player.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 210));
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