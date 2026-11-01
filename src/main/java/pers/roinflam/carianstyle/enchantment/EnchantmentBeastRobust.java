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
 * 野兽强健附魔
 *
 * 获得药水效果时增强：持续时间×0.4，等级×2+1
 */
@AutoRegisterEnchantment(
        id = "beast_robust",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentBeastRobust extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 时长倍率 */
    private static final float DURATION_RATIO = 0.4F;
    /** 等级倍率（新等级 = 原等级 × 它 + 1） */
    private static final int AMPLIFIER_MULTIPLIER = 2;
    /** 改写后的等级上限（amplifier），存档里是 byte，叠太高会溢出成负数 */
    private static final int MAX_AMPLIFIER = 100;

    public EnchantmentBeastRobust() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 只读胸甲一件的等级
     * <p>只能附在胸甲上；以前任意护甲件都能附，全身每件的等级相加。</p>
     */
    private static int getChestLevel(EntityLivingBase entity) {
        Enchantment beastRobust = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBeastRobust.class);
        if (beastRobust == null) {
            return 0;
        }

        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0;
        }
        return capLevel(EnchantmentHelper.getEnchantmentLevel(beastRobust, chest), LEVEL_CAP);
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

        int newDuration = (int) (potionEffect.getDuration() * DURATION_RATIO);
        int newAmplifier = Math.min(potionEffect.getAmplifier() * AMPLIFIER_MULTIPLIER + 1, MAX_AMPLIFIER);

        evt.getPotionEffect().combine(new PotionEffect(potion, newDuration, newAmplifier));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}