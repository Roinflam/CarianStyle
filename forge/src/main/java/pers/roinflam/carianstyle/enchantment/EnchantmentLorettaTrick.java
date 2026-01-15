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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

/**
 * 洛蕾塔戏法附魔
 *
 * 弓箭附魔，箭矢命中时连续爆炸
 * 效果：
 * - 箭矢伤害减少25%
 * - 延迟创建4次随机位置爆炸（着火箭威力4，普通箭威力3）
 */
@AutoRegisterEnchantment(
        id = "loretta_trick",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentLorettaTrick extends EnchantmentBase {

    public EnchantmentLorettaTrick() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时减伤并连续爆炸
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

        Enchantment lorettaTrick = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaTrick.class);
        if (lorettaTrick == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                lorettaTrick,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 箭矢伤害减少25%
        arrow.setDamage(arrow.getDamage() - arrow.getDamage() * 0.25);

        // 爆炸威力（着火箭威力4，普通箭威力3）
        float explosionStrength = EntityUtil.getFire(arrow) > 0 ? 4 : 3;

        // 延迟创建4次随机位置爆炸
        new SynchronizationTask(1, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > 4) {
                    this.cancel();
                    return;
                }

                // 在箭矢位置附近随机偏移
                double offsetX = -2.5 + RandomUtil.getInt(0, 5);
                double offsetZ = -2.5 + RandomUtil.getInt(0, 5);

                attacker.world.createExplosion(
                        attacker,
                        arrow.posX + offsetX,
                        arrow.posY,
                        arrow.posZ + offsetZ,
                        explosionStrength,
                        false
                );
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class));
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}