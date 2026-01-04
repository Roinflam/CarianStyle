package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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

/**
 * 洛蕾塔大弓附魔
 *
 * 弓箭附魔，箭矢命中时爆炸
 * 效果：
 * - 箭矢伤害增加50%
 * - 在箭矢位置创建爆炸（着火箭威力3，普通箭威力2）
 */
@AutoRegisterEnchantment(
        id = "loretta_big_bow",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentLorettaBigBow extends EnchantmentBase {

    public EnchantmentLorettaBigBow() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时增伤并爆炸
     * 由于 ProjectileImpactEvent.Arrow 没有模板方法，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getArrow().shootingEntity == null) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                lorettaBigBow,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 箭矢伤害增加50%
        arrow.setDamage(arrow.getDamage() + arrow.getDamage() * 0.5);

        // 创建爆炸（着火箭威力3，普通箭威力2）
        float explosionStrength = EntityUtil.getFire(arrow) > 0 ? 3 : 2;
        attacker.world.createExplosion(attacker, arrow.posX, arrow.posY, arrow.posZ, explosionStrength, false);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (25 * ConfigLoader.enchantingDifficulty);
    }
}