package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.mc112.util.Reflect;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 火焰疗愈附魔
 * <p>
 * 着火时被攻击，有 2.5% × 全身等级的几率随机清掉身上一个负面效果，并获得最大生命 10% 的伤害吸收。
 * </p>
 * <p>v2.2：LivingAttack受击者视角入口接入怪物附魔触发开关</p>
 * <p>
 * 1.12.2 版：效果好坏按 {@code Potion.isBadEffect()} 分（1.12.2 的 {@code isBeneficial()} 只在客户端存在，
 * 是信标用的标记）；发光这类中性效果 {@code isBadEffect()} 为 false，不算负面，不会被清掉（1.20.1 只排除增益，中性效果也可能被清，差别只在中性效果上）。
 * 剩余燃烧时间读 {@code Entity} 的私有字段 {@code fire}（1.20.1 是 {@code getRemainingFireTicks()}）。
 * </p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "healing_by_fire", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnumEnchantmentType.ARMOR, slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentHealingByFire extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.healing_by_fire.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "healing_by_fire";

    /**
     * 每级的触发概率（百分比）
     * <p>默认 2.5，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    2.5D, 0.0D, 100.0D);

    /**
     * 触发时获得的伤害吸收占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_RATIO =
            EnchantmentValues.define(VALUE_ID, "absorption_ratio",
                    0.1D, 0.0D, 2.0D);

    /** 1.12.2 版：{@code Entity.fire} 是私有字段，反射读剩余燃烧 tick */
    private static final Field FIRE_FIELD = Reflect.fieldOrNull(Entity.class, "fire", "field_190534_ay");

    public EnchantmentHealingByFire() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntityLiving().world.isRemote) return;
        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) return;
        EntityLivingBase victim = evt.getEntityLiving();
        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;
        if (remainingFireTicks(victim) <= 0) return;
        if (victim.getActivePotionEffects().isEmpty()) return;
        Enchantment healingByFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHealingByFire.class);
        if (healingByFire == null) return;
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getEnchantmentLevel(healingByFire, armor);
        }
        totalLevel = applyLevelLimit(healingByFire, totalLevel, 10);
        if (totalLevel <= 0) return;
        if (!RandomUtil.percentageChance(totalLevel * CHANCE_PER_LEVEL.get())) return;
        List<PotionEffect> badEffects = new ArrayList<>(victim.getActivePotionEffects());
        // 1.12.2 版：服务端不能调 isBeneficial()，「不是负面」一律写成 !isBadEffect()
        badEffects.removeIf(effect ->
                !effect.getPotion().isBadEffect() ||
                        effect.getPotion().isInstant() ||
                        !effect.doesShowParticles()
        );
        if (badEffects.isEmpty()) return;
        PotionEffect toRemove = badEffects.get(RandomUtil.getInt(0, badEffects.size() - 1));
        victim.removePotionEffect(toRemove.getPotion());
        victim.setAbsorptionAmount(victim.getAbsorptionAmount()
                + victim.getMaxHealth() * (float) ABSORPTION_RATIO.get());
    }

    /**
     * 剩余燃烧 tick（1.20.1 的 {@code getRemainingFireTicks()}）
     *
     * @param entity 实体
     * @return 剩余燃烧 tick；字段读不到时退回 {@code isBurning()}
     */
    private static int remainingFireTicks(@Nonnull Entity entity) {
        if (FIRE_FIELD == null) {
            return entity.isBurning() ? 1 : 0;
        }
        try {
            return FIRE_FIELD.getInt(entity);
        } catch (IllegalAccessException e) {
            return entity.isBurning() ? 1 : 0;
        }
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((20 + (l - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}
