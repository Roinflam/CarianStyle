package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 火焰疗愈附魔
 * <p>v2.2：LivingAttack受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "healing_by_fire", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
@Mod.EventBusSubscriber
public class EnchantmentHealingByFire extends EnchantmentBase {

    public EnchantmentHealingByFire() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        if (victim.getRemainingFireTicks() <= 0) return;
        if (victim.getActiveEffects().isEmpty()) return;

        Enchantment healingByFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHealingByFire.class);
        if (healingByFire == null) return;

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(healingByFire, armor);
        }
        if (ConfigLoader.levelLimit) totalLevel = Math.min(totalLevel, 10);
        if (totalLevel <= 0) return;
        if (!RandomUtil.percentageChance(totalLevel * 2.5)) return;

        List<MobEffectInstance> badEffects = new ArrayList<>(victim.getActiveEffects());
        badEffects.removeIf(effect ->
                effect.getEffect().isBeneficial() ||
                        effect.getEffect().isInstantenous() ||
                        !effect.isVisible()
        );

        if (badEffects.isEmpty()) return;

        MobEffectInstance toRemove = badEffects.get(RandomUtil.getInt(0, badEffects.size() - 1));
        victim.removeEffect(toRemove.getEffect());
        victim.setAbsorptionAmount(victim.getAbsorptionAmount() + victim.getMaxHealth() * 0.1f);
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((20 + (l - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}
