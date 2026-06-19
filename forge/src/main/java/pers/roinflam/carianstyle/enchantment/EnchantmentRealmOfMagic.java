package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 魔法领域附魔
 * <p>v2.1：LivingHurt攻击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "realm_of_magic",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        conflictsWith = {EnchantmentToppsStand.class},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentRealmOfMagic extends EnchantmentBase {

    public EnchantmentRealmOfMagic() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }
        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        // ⭐ v2.1：怪物附魔触发开关（攻击者视角，团队魔法增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        Enchantment realmOfMagic = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRealmOfMagic.class);
        if (realmOfMagic == null) {
            return;
        }

        List<LivingEntity> allies = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                attacker,
                6,
                entity -> entity.getClass() == attacker.getClass()
        );

        for (LivingEntity ally : allies) {
            for (ItemStack armor : ally.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getItemEnchantmentLevel(realmOfMagic, armor) > 0) {
                        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.5f);
                        return;
                    }
                }
            }
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
}
