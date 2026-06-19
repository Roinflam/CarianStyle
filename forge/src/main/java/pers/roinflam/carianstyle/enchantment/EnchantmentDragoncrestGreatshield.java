package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 龙徽大盾附魔
 * <p>v2.1：LivingDamage受击者叠盾入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "dragoncrest_greatshield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentDragoncrestGreatshield extends EnchantmentBase {

    private static final int MAX_SHIELD_LEVEL = 19;
    private static final int SHIELD_DURATION = 600;

    public EnchantmentDragoncrestGreatshield() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (DamageSourceUtil.isMagicDamage(damageSource) ||
                damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受击者视角，物理叠层护盾）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment dragoncrest = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragoncrestGreatshield.class);

        if (dragoncrest == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(dragoncrest, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        int currentAmplifier = DynamicAttributeManager.getAmplifier(victim, DynamicAttributes.DRAGONCREST_GREATSHIELD);

        if (currentAmplifier < 0) {
            DynamicAttributeManager.apply(victim,
                    DynamicAttributes.DRAGONCREST_GREATSHIELD.createInstance(SHIELD_DURATION, 0));
        } else if (currentAmplifier < MAX_SHIELD_LEVEL) {
            DynamicAttributeManager.apply(victim,
                    DynamicAttributes.DRAGONCREST_GREATSHIELD.createInstance(SHIELD_DURATION, currentAmplifier + 1));
        } else {
            DynamicAttributeManager.apply(victim,
                    DynamicAttributes.DRAGONCREST_GREATSHIELD.createInstance(SHIELD_DURATION, MAX_SHIELD_LEVEL));
            evt.setAmount(evt.getAmount() * 0.75f);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}
