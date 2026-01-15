package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 切割附魔
 *
 * 激活：血量>=75%时攻击，消耗50%血量获得INCISION状态（200tick）
 * 激活后：攻击治疗自身（min(伤害×0.25, 最大血量×0.25)），给敌人施加出血
 * 击杀：治疗（损失血量×0.1），延长INCISION时间
 */
@AutoRegisterEnchantment(
        id = "incision",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentIncision extends EnchantmentBase {

    public EnchantmentIncision() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 玩家需要刚挥剑
        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        if (attacker.getActivePotionEffect(CarianStylePotion.INCISION) == null) {
            // 激活阶段：血量>=75%时消耗50%血量获得INCISION
            if (attacker.getHealth() >= attacker.getMaxHealth() * 0.75f) {
                attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * 0.5f);
                attacker.addPotionEffect(new PotionEffect(CarianStylePotion.INCISION, 200, 0));
            }
        } else {
            // 激活后：治疗自身，给敌人施加出血
            float healAmount = Math.min(ctx.getDamage() * 0.25f, attacker.getMaxHealth() * 0.25f);
            attacker.heal(healAmount);
            victim.addPotionEffect(new PotionEffect(CarianStylePotion.HEMORRHAGE, 30, 0));
        }
    }

    /**
     * 击杀敌人时：治疗并延长INCISION时间
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

        // 击杀者必须存活且不是自杀
        if (!killer.isEntityAlive() || killer.equals(evt.getEntityLiving())) {
            return;
        }

        if (killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment incision = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIncision.class);
        if (incision == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                incision,
                killer.getHeldItem(killer.getActiveHand()));

        if (level <= 0) {
            return;
        }

        PotionEffect incisionEffect = killer.getActivePotionEffect(CarianStylePotion.INCISION);
        if (incisionEffect == null) {
            return;
        }

        // 治疗损失血量的10%
        killer.heal((killer.getMaxHealth() - killer.getHealth()) * 0.1f);

        // 延长INCISION时间（+100tick，上限200tick）
        int newDuration = Math.min(incisionEffect.getDuration() + 100, 200);
        killer.addPotionEffect(new PotionEffect(CarianStylePotion.INCISION, newDuration, 0));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}