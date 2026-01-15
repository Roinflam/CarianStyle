package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 暗弃子附魔
 *
 * 攻击时：伤害变为魔法且无视护甲，偷取敌人一个正面效果
 * 受击时：夜晚减伤10%
 * 被动：夜晚持续回血
 */
@AutoRegisterEnchantment(
        id = "dark_abandoned_child",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentDarkAbandonedChild extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentDarkAbandonedChild() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                darkAbandonedChild,
                attacker.getHeldItem(attacker.getActiveHand()));

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        evt.getSource().setMagicDamage().setDamageBypassesArmor();

        if (!victim.getActivePotionEffects().isEmpty()) {
            List<PotionEffect> positiveEffects = new ArrayList<>(victim.getActivePotionEffects());
            positiveEffects.removeIf(effect ->
                    effect.getPotion().isBadEffect() ||
                            effect.getPotion().isInstant() ||
                            !effect.getPotion().shouldRender(effect)
            );

            if (!positiveEffects.isEmpty()) {
                PotionEffect stolen = positiveEffects.get(RandomUtil.getInt(0, positiveEffects.size() - 1));
                attacker.addPotionEffect(stolen);
                victim.removePotionEffect(stolen.getPotion());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                darkAbandonedChild,
                victim.getHeldItem(victim.getActiveHand()));

        if (level > 0 && !victim.world.isDaytime()) {
            evt.setAmount(evt.getAmount() * 0.9f);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.player.world.isDaytime()) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        if (player.getHeldItem(player.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                darkAbandonedChild,
                player.getHeldItem(player.getActiveHand()));

        if (level > 0) {
            player.heal(player.getMaxHealth() * 0.015f / 20);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}