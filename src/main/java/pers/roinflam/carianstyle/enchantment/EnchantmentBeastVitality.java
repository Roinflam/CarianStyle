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

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 每级延长的时长比例 */
    private static final double DURATION_PER_LEVEL = 0.3D;

    public EnchantmentBeastVitality() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 只读胸甲一件的等级
     * <p>只能附在胸甲上；以前任意护甲件都能附，全身每件的等级相加。</p>
     */
    private static int getChestLevel(EntityLivingBase entity) {
        Enchantment beastVitality = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBeastVitality.class);
        if (beastVitality == null) {
            return 0;
        }

        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0;
        }
        return capLevel(EnchantmentHelper.getEnchantmentLevel(beastVitality, chest), LEVEL_CAP);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionAdded(@Nonnull PotionEvent.PotionAddedEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        int totalLevel = getChestLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        PotionEffect potionEffect = evt.getPotionEffect();
        Potion potion = potionEffect.getPotion();

        if (potion.isInstant() || !potion.shouldRender(potionEffect)) {
            return;
        }

        int originalDuration = potionEffect.getDuration();
        int newDuration = (int) (originalDuration + originalDuration * totalLevel * DURATION_PER_LEVEL);

        evt.getPotionEffect().combine(new PotionEffect(potion, newDuration, potionEffect.getAmplifier()));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }
}