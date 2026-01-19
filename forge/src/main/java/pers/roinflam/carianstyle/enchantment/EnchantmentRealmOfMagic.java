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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 魔法领域附魔
 * <p>
 * 护甲附魔，团队魔法增伤
 * 当队友（同类实体）造成魔法伤害时：
 * - 如果周围6格内有穿戴此附魔的同类实体，伤害增加50%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 必须有攻击者且是魔法伤害
        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }
        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        Enchantment realmOfMagic = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRealmOfMagic.class);
        if (realmOfMagic == null) {
            return;
        }

        // 获取攻击者周围6格内的同类实体
        List<LivingEntity> allies = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                attacker,
                6,
                entity -> entity.getClass() == attacker.getClass()
        );

        // 检查同类是否有穿戴此附魔
        for (LivingEntity ally : allies) {
            for (ItemStack armor : ally.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getItemEnchantmentLevel(realmOfMagic, armor) > 0) {
                        // 找到一个即增伤50%
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