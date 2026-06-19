package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 癫火附魔
 * <p>v3.1：LivingHurt攻击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentEpilepsyFire extends EnchantmentBase {

    private static final int BURN_VISUAL_DURATION = 65;
    private static final int DAMAGE_TICKS = 60;
    private static final int DOT_DELAY = 5;

    public EnchantmentEpilepsyFire() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        // ⭐ v3.1：怪物附魔触发开关（攻击者视角，自损+对敌DoT）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        Enchantment epilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class);
        if (epilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(epilepsyFire, heldItem);
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }
        if (level <= 0) {
            return;
        }

        if (attacker instanceof Player) {
            if (((Player) attacker).getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        final int effectiveLevel = level;

        DynamicAttributeManager.apply(attacker,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION, 0));
        ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        float attackerDmgPerTick = attacker.getMaxHealth() * 0.2f / DAMAGE_TICKS;
        DamageOverTimeManager.applyLinear(
                attacker, attackerDmgPerTick, DAMAGE_TICKS, DOT_DELAY,
                NewDamageSource.epilepsyFire(attacker.level()), true
        );

        DynamicAttributeManager.apply(victim,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION, 0));
        ClientSyncEffectHelper.onAttributeApplied(victim, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        float victimDmgPerTick = attacker.getMaxHealth() * 0.2f * effectiveLevel * 0.1f / DAMAGE_TICKS;
        DamageOverTimeManager.applyLinear(
                victim, victimDmgPerTick, DAMAGE_TICKS, DOT_DELAY,
                NewDamageSource.epilepsyFire(victim.level()), true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
