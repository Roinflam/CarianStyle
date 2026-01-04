package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 刺客赌局附魔
 *
 * 受击时获得隐身效果（等级×20tick）
 * 隐身状态下攻击：移除隐身，增伤+25%×等级
 * 隐身状态下暴击：移除隐身，暴击倍率×2
 */
@AutoRegisterEnchantment(
        id = "assassin_gambit",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentAssassinGambit extends EnchantmentBase {

    public EnchantmentAssassinGambit() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击者视角：隐身状态下增伤
     * 受击者视角：获得隐身
     * 由于需要同时处理攻击者和受击者双方，保留静态监听器
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        // 攻击者视角：隐身状态下增伤
        if (attacker.getActivePotionEffect(CarianStylePotion.STEALTH) != null) {
            if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(
                        assassinGambit,
                        attacker.getHeldItem(attacker.getActiveHand()));

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    attacker.removePotionEffect(CarianStylePotion.STEALTH);
                    evt.setAmount(evt.getAmount() + evt.getAmount() * level * 0.25f);
                }
            }
        }

        // 受击者视角：获得隐身
        if (!victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(
                    assassinGambit,
                    victim.getHeldItem(victim.getActiveHand()));

            if (level > 0) {
                victim.addPotionEffect(new PotionEffect(CarianStylePotion.STEALTH, level * 20));
            }
        }
    }

    /**
     * 暴击时：隐身状态下暴击倍率×2
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!evt.isVanillaCritical()) {
            return;
        }

        if (!(evt.getTarget() instanceof EntityLivingBase)) {
            return;
        }

        EntityPlayer attacker = evt.getEntityPlayer();

        if (attacker.getActivePotionEffect(CarianStylePotion.STEALTH) == null) {
            return;
        }

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                assassinGambit,
                attacker.getHeldItem(attacker.getActiveHand()));

        if (level > 0) {
            attacker.removePotionEffect(CarianStylePotion.STEALTH);
            evt.setDamageModifier(evt.getDamageModifier() * 2);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}