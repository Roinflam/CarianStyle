package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 托普斯之架附魔
 *
 * 护甲附魔，魔法屏障
 * 当受击者或攻击者周围6格内有穿戴此附魔的实体时：
 * - 完全取消魔法伤害
 */
@AutoRegisterEnchantment(
        id = "topps_stand",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentToppsStand extends EnchantmentBase {

    public EnchantmentToppsStand() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 周围有穿戴此附魔的实体时取消魔法伤害
     * 由于检查范围内多个实体，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须是魔法伤害
        if (!evt.getSource().isMagicDamage()) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment toppsStand = EnchantmentRegistry.getEnchantmentByClass(EnchantmentToppsStand.class);
        if (toppsStand == null) {
            return;
        }

        // 收集受击者周围6格的实体
        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                6
        );

        // 如果有攻击者，也收集攻击者周围的实体
        if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
            entities.addAll(EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    attacker,
                    6
            ));
        }

        // 检查是否有实体穿戴此附魔
        for (EntityLivingBase entity : entities) {
            for (ItemStack armor : entity.getArmorInventoryList()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getEnchantmentLevel(toppsStand, armor) > 0) {
                        evt.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}