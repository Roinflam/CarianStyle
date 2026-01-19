package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 龙徽大盾附魔
 * <p>
 * 护甲附魔，物理伤害护盾叠层系统
 * 受到物理伤害时叠加护盾层数（最多20层）
 * 每层持续30秒，满20层时减伤25%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
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

        MobEffectInstance currentShield = victim.getEffect(CarianStylePotion.DRAGONCREST_GREATSHIELD.get());

        if (currentShield == null) {
            victim.addEffect(new MobEffectInstance(
                    CarianStylePotion.DRAGONCREST_GREATSHIELD.get(),
                    SHIELD_DURATION,
                    0
            ));
        } else if (currentShield.getAmplifier() < MAX_SHIELD_LEVEL) {
            victim.addEffect(new MobEffectInstance(
                    CarianStylePotion.DRAGONCREST_GREATSHIELD.get(),
                    SHIELD_DURATION,
                    currentShield.getAmplifier() + 1
            ));
        } else {
            victim.addEffect(new MobEffectInstance(
                    CarianStylePotion.DRAGONCREST_GREATSHIELD.get(),
                    SHIELD_DURATION,
                    MAX_SHIELD_LEVEL
            ));
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