package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 龙息腐败附魔
 *
 * 弓箭附魔，箭矢落地时对范围内敌人施加猩红腐败
 * 范围 = 等级 × 2格
 * 猩红腐败：持续时间 = 等级 × 5秒，等级 = 附魔等级 - 1
 */
@AutoRegisterEnchantment(
        id = "dragon_breath_corruption",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentDragonBreathCorruption extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 范围半径上限（格），半径 = 等级 × 2 */
    private static final int MAX_SEARCH_RADIUS = 10;
    /** 一次至多影响的目标数 */
    private static final int MAX_TARGETS = 20;
    /** 每级腐败持续秒数 */
    private static final int EFFECT_SECONDS_PER_LEVEL = 5;

    public EnchantmentDragonBreathCorruption() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢落地时对周围所有生物（含自己）施加猩红腐败
     * <p>
     * 半径 = 等级 × 2，至多 10 格；一次至多 20 个。以前半径能到 20 格、没有数量上限。
     * 只认射手主手的弓；射手不是生物时不触发（以前直接强转会崩）。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有射击者且未击中实体（落地）
        if (evt.getArrow().shootingEntity == null || evt.getRayTraceResult().entityHit != null) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment dragonBreath = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragonBreathCorruption.class);
        if (dragonBreath == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(dragonBreath, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP);
        }

        if (level <= 0) {
            return;
        }

        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                arrow,
                Math.min(level * 2, MAX_SEARCH_RADIUS)
        );

        int hitCount = 0;
        for (EntityLivingBase target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            target.addPotionEffect(new PotionEffect(
                    CarianStylePotion.SCARLET_ROT,
                    level * EFFECT_SECONDS_PER_LEVEL * 20,
                    level - 1
            ));
            hitCount++;
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}