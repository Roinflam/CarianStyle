package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
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

    public EnchantmentBloodSlash() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        float bonusDamage = Math.min(victim.getHealth() * effectiveLevel * 0.05f, victim.getMaxHealth());
        ctx.addDamage(bonusDamage);

        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
            if (attacker.getHealth() > attacker.getMaxHealth() * 0.1) {
                attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * 0.1f);
            } else {
                EntityLivingUtil.kill(attacker, NewDamageSource.HEMORRHAGE);
            }
        }
    }

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

        if (killer.getHeldItem(killer.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
        if (bloodSlash == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                bloodSlash,
                killer.getHeldItem(killer.getActiveHand()));

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        Enchantment bloodCollection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodCollection.class);
        if (bloodCollection != null &&
                EnchantmentHelper.getEnchantmentLevel(
                        bloodCollection,
                        killer.getHeldItem(killer.getActiveHand())) > 0) {
            killer.heal(killer.getMaxHealth() * level * 0.05f);
        } else {
            killer.heal(killer.getMaxHealth() * level * 0.025f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}