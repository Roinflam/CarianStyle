package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 清醒附魔
 * <p>
 * 护甲附魔，缩短负面效果持续时间但增强效果
 * 获得负面效果时：
 * - 持续时间减少 15% × 等级
 * - 效果等级+1（效果更强但更短）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "lucidity",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentLucidity extends EnchantmentBase {

    public EnchantmentLucidity() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionAdded(@NotNull MobEffectEvent.Added evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity entity = evt.getEntity();

        Enchantment lucidity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLucidity.class);
        if (lucidity == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(lucidity, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        MobEffectInstance potionEffect = evt.getEffectInstance();
        MobEffect potion = potionEffect.getEffect();

        // 只对非瞬时、可渲染的负面效果生效
        if (potion.isInstantenous() || !potionEffect.isVisible() || !potion.getCategory().equals(net.minecraft.world.effect.MobEffectCategory.HARMFUL)) {
            return;
        }

        // 修改效果：持续时间减少，等级+1
        int newDuration = (int) (potionEffect.getDuration() - potionEffect.getDuration() * totalLevel * 0.15);

        // 创建新的效果实例（1.20.1不能直接combine）
        MobEffectInstance newEffect = new MobEffectInstance(
                potion,
                newDuration,
                potionEffect.getAmplifier() + 1,
                potionEffect.isAmbient(),
                potionEffect.isVisible(),
                potionEffect.showIcon()
        );

        // 移除旧效果并添加新效果
        entity.removeEffect(potion);
        entity.addEffect(newEffect);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}