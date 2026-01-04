// 文件：EnchantmentCragblade.java
// 路径：src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentCragblade.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 岩石剑附魔
 *
 * 效果：
 * - 攻击时给自己施加持续10秒的增益效果
 * - 无法跳跃
 * - 获得击退抗性 10% × 等级
 * - 攻击力提高 10% × 等级
 * - 护甲提高 10% × 等级
 * - 韧性提高 10% × 等级
 */
@AutoRegisterEnchantment(
        id = "cragblade",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentCragblade extends EnchantmentBase {

    /**
     * 构造函数
     */
    public EnchantmentCragblade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时给攻击者自己施加岩石剑增益效果
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment cragblade = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCragblade.class);
        if (cragblade == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                cragblade,
                attacker.getHeldItem(attacker.getActiveHand()));

        if (level <= 0) {
            return;
        }

        // 给攻击者自己施加岩石剑增益效果
        int potionDuration = 200; // 10秒（200 tick）
        int potionLevel = level - 1;
        attacker.addPotionEffect(new PotionEffect(CarianStylePotion.CRAGBLADE, potionDuration, potionLevel));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}