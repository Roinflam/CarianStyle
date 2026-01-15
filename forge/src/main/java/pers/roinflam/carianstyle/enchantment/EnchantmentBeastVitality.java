package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 野兽活力附魔
 *
 * 获得药水效果时延长持续时间：原时间 + 原时间×等级×0.3
 */
@AutoRegisterEnchantment(
        id = "beast_vitality",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentBeastVitality extends EnchantmentBase {

    public EnchantmentBeastVitality() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment beastVitality = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBeastVitality.class);
        if (beastVitality == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(beastVitality, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionAdded(@Nonnull PotionEvent.PotionAddedEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        PotionEffect potionEffect = evt.getPotionEffect();
        Potion potion = potionEffect.getPotion();

        if (potion.isInstant() || !potion.shouldRender(potionEffect)) {
            return;
        }

        int originalDuration = potionEffect.getDuration();
        int newDuration = (int) (originalDuration + originalDuration * totalLevel * 0.3);

        evt.getPotionEffect().combine(new PotionEffect(potion, newDuration, potionEffect.getAmplifier()));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }
}