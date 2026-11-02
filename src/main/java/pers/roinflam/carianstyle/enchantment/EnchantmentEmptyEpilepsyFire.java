package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

import javax.annotation.Nonnull;

/**
 * 空癫火附魔
 * <p>弓箭命中生物时自己和目标一起烧起来：自己按最大生命持续掉血，目标按自己的掉血量乘等级倍率持续掉血。</p>
 * <p>v3.1：ProjectileImpact射手视角入口接入怪物附魔触发开关</p>
 * <p>
 * 1.12.2 版：订阅 {@code ProjectileImpactEvent.Arrow}（1.20.1 订阅总的投射物事件再筛箭），射手取 {@code shootingEntity}。
 * 保留：创造模式玩家不烧自己（自损和自己身上的火焰都跳过），目标照烧；1.20.1 创造模式也会烧自己。
 * </p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "empty_epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentEmptyEpilepsyFire extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.empty_epilepsy_fire.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "empty_epilepsy_fire";

    /**
     * 燃烧视觉效果的持续时间（tick）
     * <p>默认 65，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle BURN_VISUAL_DURATION =
            EnchantmentValues.define(VALUE_ID, "burn_visual_duration",
                    65, 1, 1200);

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 60，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_TICKS =
            EnchantmentValues.define(VALUE_ID, "damage_ticks",
                    60, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 自身承受的总伤害占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.1D, 0.0D, 1.0D);

    /**
     * 每级施加给目标的伤害倍率（以自身承受量为基数）
     * <p>默认 0.2，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "victim_damage_per_level",
                    0.2D, 0.0D, 2.0D);

    public EnchantmentEmptyEpilepsyFire() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityArrow arrow = evt.getArrow();

        if (arrow.shootingEntity == null) {
            return;
        }

        RayTraceResult hit = evt.getRayTraceResult();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.ENTITY) {
            return;
        }

        if (!(hit.entityHit instanceof EntityLivingBase)) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        // v3.1：怪物附魔触发开关（射手视角，自损+对敌DoT）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        EntityLivingBase victim = (EntityLivingBase) hit.entityHit;

        Enchantment emptyEpilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class);
        if (emptyEpilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(emptyEpilepsyFire, heldItem);
        level = applyLevelLimit(emptyEpilepsyFire, level, LEVEL_CAP.getInt());
        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        // 1.12.2 版保留：创造模式玩家不烧自己（见类注释）
        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
            DynamicAttributeManager.apply(attacker,
                    DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION.getInt(), 0));
            ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

            // 自损：被反噬耗死时致死一击走原版伤害管线（见 DamageOverTimeManager）
            float attackerDmgPerTick = attacker.getMaxHealth() * (float) SELF_DAMAGE_RATIO.get() / DAMAGE_TICKS.getInt();
            DamageOverTimeManager.applySelfLinear(
                    attacker, attackerDmgPerTick, DAMAGE_TICKS.getInt(), DOT_DELAY.getInt(),
                    NewDamageSource.epilepsyFire(attacker.world)
            );
        }

        DynamicAttributeManager.apply(victim,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION.getInt(), 0));
        ClientSyncEffectHelper.onAttributeApplied(victim, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        float victimDmgPerTick = attacker.getMaxHealth() * (float) SELF_DAMAGE_RATIO.get()
                * effectiveLevel * (float) VICTIM_DAMAGE_PER_LEVEL.get() / DAMAGE_TICKS.getInt();
        DamageOverTimeManager.applyLinear(
                victim, victimDmgPerTick, DAMAGE_TICKS.getInt(), DOT_DELAY.getInt(),
                NewDamageSource.epilepsyFire(victim.world), true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
