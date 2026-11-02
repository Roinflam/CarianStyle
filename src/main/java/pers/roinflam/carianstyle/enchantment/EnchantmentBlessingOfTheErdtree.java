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
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 黄金树祝福附魔
 * <p>
 * 受击或攻击时给自己挂上黄金树祝福（加最大生命、定时回血的状态效果，逻辑在 {@code MobEffectBlessingOfTheErdtree}）：
 * 持续 = 每级 2.5 秒 × 全身等级，效果等级 = 全身等级 - 1；攻击方的效果等级最高 5 级。
 * </p>
 * <p>v2.1：LivingAttack双向监听器入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "blessing_of_the_erdtree",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        forceTreasure = true
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentBlessingOfTheErdtree extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.blessing_of_the_erdtree.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blessing_of_the_erdtree";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 攻击方效果等级的上限
     * <p>默认 5，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle ATTACKER_AMPLIFIER_CAP =
            EnchantmentValues.define(VALUE_ID, "attacker_amplifier_cap",
                    5, 1, 100);

    /**
     * 每级的效果持续秒数
     * <p>默认 2.5，允许范围 0.1 ~ 120.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    2.5D, 0.1D, 120.0D);

    public EnchantmentBlessingOfTheErdtree() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment blessingOfTheErdtree = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlessingOfTheErdtree.class);
        if (blessingOfTheErdtree == null) {
            return 0;
        }
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blessingOfTheErdtree, armor);
            }
        }
        totalLevel = applyLevelLimit(blessingOfTheErdtree, totalLevel, LEVEL_CAP.getInt());
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        // 受击者视角
        // v2.1：怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            int victimLevel = getTotalLevel(victim);
            if (victimLevel > 0) {
                int duration = (int) (DURATION_SECONDS_PER_LEVEL.get() * victimLevel * 20);
                int amplifier = victimLevel - 1;
                victim.addPotionEffect(new PotionEffect(CarianStylePotion.BLESSING_OF_THE_ERDTREE.get(), duration, amplifier));
            }
        }

        // 攻击者视角
        // v2.1：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;
        int attackerLevel = getTotalLevel(attacker);
        if (attackerLevel > 0) {
            attackerLevel = Math.min(attackerLevel, ATTACKER_AMPLIFIER_CAP.getInt());
            int duration = (int) (DURATION_SECONDS_PER_LEVEL.get() * attackerLevel * 20);
            int amplifier = attackerLevel - 1;
            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.BLESSING_OF_THE_ERDTREE.get(), duration, amplifier));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
