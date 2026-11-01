package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 因果律附魔
 *
 * 护甲附魔，受到5次伤害后触发范围反击
 * 对周围所有敌人造成等同于最后一次伤害×等级×75%的伤害
 * 反击范围 = 等级×3格
 */
@AutoRegisterEnchantment(
        id = "causality_principle",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentCausalityPrinciple extends EnchantmentBase {

    private static final String COUNTER_KEY = "causality_principle";
    /** 反击冷却键 */
    private static final String COOLDOWN_KEY = "causality_principle_cooldown";
    /** 受击几次触发一次反击 */
    private static final int TRIGGER_COUNT = 5;
    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 每级反击伤害倍率（乘最后那一下的伤害） */
    private static final float DAMAGE_PER_LEVEL = 0.75F;
    /** 反击半径上限（格），半径 = 等级 × 3 */
    private static final int MAX_SEARCH_RADIUS = 10;
    /** 一次反击至多命中的目标数 */
    private static final int MAX_TARGETS = 20;
    /** 反击冷却（tick） */
    private static final int TRIGGER_COOLDOWN = 20;

    /**
     * 正在结算反击
     * <p>
     * 反击造成的伤害会再进这个监听；两个都穿着因果性原理的人互相反击时会无限连锁，
     * 和别的模组的反伤叠在一起能把服务端打崩。结算期间的伤害事件一律不计数、不触发。
     * 伤害事件只在服务端主线程上发，一个静态标记就够。
     * </p>
     */
    private static boolean inRetaliation = false;

    public EnchantmentCausalityPrinciple() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 受到生物伤害累计 5 次后，对周围造成最后一下伤害 × [等级] × 75% 的伤害
     * <p>
     * 半径 = 等级 × 3，至多 10 格；一次至多 20 个；触发后 1 秒冷却，
     * 冷却中计数不清零，冷却一结束下一次受击立刻触发。
     * 以前半径能到 30 格、没有数量上限和冷却，反击还会连锁触发。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (inRetaliation) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        Enchantment causalityPrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCausalityPrinciple.class);
        if (causalityPrinciple == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(causalityPrinciple, armor);
            }
        }

        totalLevel = capLevel(totalLevel, LEVEL_CAP);

        if (totalLevel <= 0) {
            return;
        }

        final int effectiveLevel = totalLevel;

        int currentCount = EnchantmentDataManager.incrementCounter(COUNTER_KEY, victim.getUniqueID());

        if (currentCount < TRIGGER_COUNT) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(COOLDOWN_KEY, victim.getUniqueID())) {
            return;
        }

        EnchantmentDataManager.resetCounter(COUNTER_KEY, victim.getUniqueID());
        EnchantmentDataManager.setCooldown(COOLDOWN_KEY, victim.getUniqueID(), TRIGGER_COOLDOWN);

        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                Math.min(effectiveLevel * 3, MAX_SEARCH_RADIUS),
                entity -> entity != null && !entity.equals(victim)
        );

        float damage = evt.getAmount() * effectiveLevel * DAMAGE_PER_LEVEL;

        inRetaliation = true;
        try {
            int hitCount = 0;
            for (EntityLivingBase target : targets) {
                if (hitCount >= MAX_TARGETS) {
                    break;
                }
                target.attackEntityFrom(DamageSource.causeMobDamage(victim), damage);
                hitCount++;
            }
        } finally {
            inRetaliation = false;
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}