package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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
 * 黄金树祝福附魔
 *
 * 受击时和攻击时都获得黄金树祝福效果
 * 持续时间 = 2.5 × 等级 × 20 tick
 * 攻击者等级上限为5
 */
@AutoRegisterEnchantment(
        id = "blessing_of_the_erdtree",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentBlessingOfTheErdtree extends EnchantmentBase {

    public EnchantmentBlessingOfTheErdtree() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment blessingOfTheErdtree = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlessingOfTheErdtree.class);
        if (blessingOfTheErdtree == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blessingOfTheErdtree, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        int victimLevel = getTotalLevel(victim);
        if (victimLevel > 0) {
            int duration = (int) (2.5 * victimLevel * 20);
            int amplifier = victimLevel - 1;
            victim.addPotionEffect(new PotionEffect(CarianStylePotion.BLESSING_OF_THE_ERDTREE, duration, amplifier));
        }

        int attackerLevel = getTotalLevel(attacker);
        if (attackerLevel > 0) {
            attackerLevel = Math.min(attackerLevel, 5);
            int duration = (int) (2.5 * attackerLevel * 20);
            int amplifier = attackerLevel - 1;
            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.BLESSING_OF_THE_ERDTREE, duration, amplifier));
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }
}