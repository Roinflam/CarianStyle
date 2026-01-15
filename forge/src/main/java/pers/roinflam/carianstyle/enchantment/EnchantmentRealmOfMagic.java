package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * 魔法领域附魔
 *
 * 护甲附魔，团队魔法增伤
 * 当队友（同类实体）造成魔法伤害时：
 * - 如果周围6格内有穿戴此附魔的同类实体，伤害增加50%
 */
@AutoRegisterEnchantment(
        id = "realm_of_magic",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentRealmOfMagic extends EnchantmentBase {

    public EnchantmentRealmOfMagic() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 魔法伤害时检查周围是否有穿戴此附魔的同类
     * 由于检查的是攻击者周围的其他实体，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有攻击者且是魔法伤害
        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }
        if (!evt.getSource().isMagicDamage()) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        Enchantment realmOfMagic = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRealmOfMagic.class);
        if (realmOfMagic == null) {
            return;
        }

        // 获取攻击者周围6格内的同类实体
        List<EntityLivingBase> allies = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                attacker,
                6,
                entity -> entity.getClass() == attacker.getClass()
        );

        // 检查同类是否有穿戴此附魔
        for (EntityLivingBase ally : allies) {
            for (ItemStack armor : ally.getArmorInventoryList()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getEnchantmentLevel(realmOfMagic, armor) > 0) {
                        // 找到一个即增伤50%
                        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.5f);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentToppsStand.class));
    }
}