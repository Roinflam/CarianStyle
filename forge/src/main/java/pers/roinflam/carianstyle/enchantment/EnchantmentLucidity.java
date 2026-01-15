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
 * 清醒附魔
 *
 * 护甲附魔，缩短负面效果持续时间但增强效果
 * 获得负面效果时：
 * - 持续时间减少 15% × 等级
 * - 效果等级+1（效果更强但更短）
 */
@AutoRegisterEnchantment(
        id = "lucidity",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentLucidity extends EnchantmentBase {

    public EnchantmentLucidity() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 获得负面效果时修改效果属性
     * 由于 PotionEvent.PotionAddedEvent 没有模板方法，且需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionAdded(@Nonnull PotionEvent.PotionAddedEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();

        Enchantment lucidity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLucidity.class);
        if (lucidity == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(lucidity, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        PotionEffect potionEffect = evt.getPotionEffect();
        Potion potion = potionEffect.getPotion();

        // 只对非瞬时、可渲染的负面效果生效
        if (potion.isInstant() || !potion.shouldRender(potionEffect) || !potion.isBadEffect()) {
            return;
        }

        // 修改效果：持续时间减少，等级+1
        int newDuration = (int) (potionEffect.getDuration() - potionEffect.getDuration() * totalLevel * 0.15);
        evt.getPotionEffect().combine(new PotionEffect(
                potion,
                newDuration,
                potionEffect.getAmplifier() + 1
        ));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}