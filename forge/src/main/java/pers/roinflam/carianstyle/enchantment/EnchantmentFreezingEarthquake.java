package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
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
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 冻结地震附魔
 *
 * 护甲附魔，受到重击时触发范围冻结
 * 当受到伤害 >= 最大生命值25%时：
 * - 将周围地面上的敌人弹起（高度 = 等级 × 0.35）
 * - 施加冻伤效果（持续 = 等级 × 5秒，效果等级 = 附魔等级 - 1）
 * - 范围 = 3 + (等级 - 1) × 2 格
 */
@AutoRegisterEnchantment(
        id = "freezing_earthquake",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentFreezingEarthquake extends EnchantmentBase {

    public EnchantmentFreezingEarthquake() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 受到重击时触发范围冻结效果
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有攻击来源
        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        // 伤害必须 >= 最大生命值的25%
        EntityLivingBase victim = evt.getEntityLiving();
        if (evt.getAmount() < victim.getMaxHealth() * 0.25f) {
            return;
        }

        Enchantment freezingEarthquake = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFreezingEarthquake.class);
        if (freezingEarthquake == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(freezingEarthquake, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 计算范围：3 + (等级-1) × 2
        int range = 3 + (totalLevel - 1) * 2;

        // 获取范围内地面上的实体（排除自己）
        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                range,
                entity -> entity.onGround && !entity.equals(victim)
        );

        final int effectiveLevel = totalLevel;

        for (EntityLivingBase target : targets) {
            // 再次检查是否在地面（虽然过滤器已检查，保持原逻辑）
            if (target.onGround) {
                // 触发击退事件
                LivingKnockBackEvent knockBackEvent = ForgeHooks.onLivingKnockBack(
                        target,
                        victim,
                        effectiveLevel * 0.35f,
                        0,
                        0
                );

                if (!knockBackEvent.isCanceled()) {
                    // 弹起目标
                    target.motionY = knockBackEvent.getStrength();

                    // 施加冻伤效果
                    target.addPotionEffect(new PotionEffect(
                            CarianStylePotion.FROSTBITE,
                            effectiveLevel * 5 * 20,  // 持续时间：等级 × 5秒
                            effectiveLevel - 1         // 效果等级：附魔等级 - 1
                    ));
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}