package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.mc112.util.EntityFireAccess;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 巨人火焰附魔
 * <p>
 * 着火时受击反弹50%伤害（火焰）
 * 减伤：伤害 × 血量比例 × 0.25
 * 免疫火焰伤害并转化为治疗（10tick冷却）
 * </p>
 * <p>v2.1：三个监听器入口接入怪物附魔触发开关</p>
 * <p>
 * 1.12.2 版保留：三处「创造模式判定」看伤害源能不能伤到创造模式（{@code canHarmInCreative()}，虚空、/kill 这类），
 * 1.20.1 看的是伤害是不是来自创造模式玩家。剩余燃烧时间读私有字段 {@code Entity.fire}（1.12.2 没有 getter）。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "giant_flame",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentGiantFlame extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.giant_flame.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "giant_flame";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 燃烧时反弹给攻击者的伤害比例
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle REFLECT_RATIO =
            EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.5D, 0.0D, 5.0D);

    /**
     * 按已损失生命比例提供的减伤系数
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_RATIO =
            EnchantmentValues.define(VALUE_ID, "reduction_ratio",
                    0.25D, 0.0D, 1.0D);

    /**
     * 燃烧治疗的结算冷却（tick）
     * <p>默认 10，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle HEAL_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "heal_cooldown",
                    10, 1, 600);

    private static final String FLAME_HEAL_COOLDOWN_KEY = "giant_flame_heal_cooldown";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGiantFlame() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment giantFlame = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGiantFlame.class);
        if (giantFlame == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(giantFlame, armor);
            }
        }
        totalLevel = applyLevelLimit(giantFlame, totalLevel, LEVEL_CAP.getInt());
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 1.12.2 版保留：按 canHarmInCreative 判断（见类注释）
        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (EntityFireAccess.getRemainingFireTicks(holder) <= 0) {
            return;
        }

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        // 1.20.1 的 isInvulnerableTo 让免火生物对 is_fire 伤害无敌；1.12.2 的 attackEntityFrom 不看 isImmuneToFire，
        // 烈焰人、岩浆怪、僵尸猪人、恶魂、凋灵骷髅这些会照吃反伤，这里先判掉
        if (attacker.isImmuneToFire()) {
            return;
        }

        attacker.attackEntityFrom(DamageSource.IN_FIRE,
                    evt.getAmount() * (float) REFLECT_RATIO.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 1.12.2 版保留：按 canHarmInCreative 判断（见类注释）
        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        float healthRatio = holder.getHealth() / holder.getMaxHealth();
        evt.setAmount(evt.getAmount() - evt.getAmount() * healthRatio * (float) REDUCTION_RATIO.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!evt.getEntity().isEntityAlive()) {
            return;
        }

        // 1.12.2 版保留：按 canHarmInCreative 判断（见类注释）
        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角，火免疫属于自保不属于濒死）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        UUID uuid = holder.getUniqueID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (!DamageSourceUtil.isFireDamage(evt.getSource())) {
            return;
        }

        evt.setCanceled(true);

        if (!EnchantmentDataManager.isOnCooldown(FLAME_HEAL_COOLDOWN_KEY, uuid)) {
            holder.heal(evt.getAmount());
            EnchantmentDataManager.setCooldown(FLAME_HEAL_COOLDOWN_KEY, uuid, HEAL_COOLDOWN.getInt());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
