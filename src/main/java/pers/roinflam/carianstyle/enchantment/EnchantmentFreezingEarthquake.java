package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
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
 * - 范围 = 3 + (等级 - 1) × 2 格，至多 10 格，一次至多 20 个
 * <p>
 * 直接改竖直速度弹起，不再走击退事件：以前被别的模组取消击退时既不弹起也不上冻伤。
 * 以前半径和目标数都没有上限。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "freezing_earthquake",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentFreezingEarthquake extends EnchantmentBase {

    /** 作用半径上限（格） */
    private static final int MAX_SEARCH_RADIUS = 10;
    /** 单次触发至多影响的目标数 */
    private static final int MAX_TARGETS = 20;
    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;
    /** 触发所需的单次伤害占最大生命值的比例 */
    private static final float TRIGGER_HEALTH_RATIO = 0.25F;
    /** 基础半径（格） */
    private static final int BASE_RANGE = 3;
    /** 每超过一级增加的半径（格） */
    private static final int RANGE_PER_LEVEL = 2;
    /** 每级弹起速度 */
    private static final double LAUNCH_PER_LEVEL = 0.35D;
    /** 每级冻伤秒数 */
    private static final int SLOW_SECONDS_PER_LEVEL = 5;

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

        EntityLivingBase victim = evt.getEntityLiving();
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        // 伤害必须 >= 最大生命值的 25%
        if (evt.getAmount() < victim.getMaxHealth() * TRIGGER_HEALTH_RATIO) {
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

        totalLevel = capLevel(totalLevel, LEVEL_CAP);

        if (totalLevel <= 0) {
            return;
        }

        // 范围：3 + (等级-1) × 2，至多 10 格
        int range = Math.min(BASE_RANGE + (totalLevel - 1) * RANGE_PER_LEVEL, MAX_SEARCH_RADIUS);

        // 获取范围内地面上的生物（排除自己）
        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                range,
                entity -> entity != null && entity.onGround && !entity.equals(victim)
        );

        final int effectiveLevel = totalLevel;

        int hitCount = 0;
        for (EntityLivingBase target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            if (target.onGround) {
                // 弹起目标；velocityChanged 让玩家客户端也收到新速度
                target.motionY = effectiveLevel * LAUNCH_PER_LEVEL;
                target.velocityChanged = true;

                // 冻伤：持续 等级 × 5 秒，效果等级 = 附魔等级 - 1
                target.addPotionEffect(new PotionEffect(
                        CarianStylePotion.FROSTBITE,
                        effectiveLevel * SLOW_SECONDS_PER_LEVEL * 20,
                        effectiveLevel - 1
                ));
                hitCount++;
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}