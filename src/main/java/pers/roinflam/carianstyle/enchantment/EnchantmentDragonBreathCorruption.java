package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.potion.PotionEffect;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 龙息腐败附魔
 * <p>v2.3：ProjectileImpact射手视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "dragon_breath_corruption",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentDragonBreathCorruption extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.dragon_breath_corruption.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dragon_breath_corruption";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 10，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级施加效果的持续秒数
     * <p>默认 5，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "effect_seconds_per_level",
                    5, 1, 120);

    public EnchantmentDragonBreathCorruption() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        EntityArrow arrow = evt.getArrow();
        if (arrow.world.isRemote) {
            return;
        }

        // 1.12.2 版：箭的主人是 shootingEntity 字段；射中实体时 typeOfHit 为 ENTITY
        if (arrow.shootingEntity == null || evt.getRayTraceResult().typeOfHit == RayTraceResult.Type.ENTITY) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        // v2.3：怪物附魔触发开关（射手视角，落地AOE腐败）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getHeldItem(EnumHand.MAIN_HAND);

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment dragonBreath = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragonBreathCorruption.class);
        if (dragonBreath == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(dragonBreath, heldItem);

        level = applyLevelLimit(dragonBreath, level, LEVEL_CAP.getInt());

        if (level <= 0) {
            return;
        }

        int searchRadius = Math.min(level * 2, MAX_SEARCH_RADIUS.getInt());

        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                arrow,
                searchRadius
        );

        int hitCount = 0;
        for (EntityLivingBase target : targets) {
            if (hitCount >= MAX_TARGETS.getInt()) {
                break;
            }
            target.addPotionEffect(new PotionEffect(
                    CarianStylePotion.SCARLET_ROT.get(),
                    level * EFFECT_SECONDS_PER_LEVEL.getInt() * 20,
                    level - 1
            ));
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
