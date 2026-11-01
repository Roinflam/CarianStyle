package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.init.MobEffects;
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

import javax.annotation.Nonnull;

/**
 * 熔炉之羽附魔
 *
 * 护甲附魔，以受到更多伤害换取机动性增益
 * 受击时（护甲、抗性、吸收之后）：
 * - 受到的伤害 ×(全身等级之和 × 50% + 100%)，四件 Ⅲ 为 ×7
 * - 延长无敌时间（max + max/2 × 最高一件等级 × 1.5）
 * - 获得速度、跳跃提升（持续 = 最高一件等级 × 40 tick，效果等级 = 最高一件等级 - 1）
 * <p>
 * 只有伤害倍率按全身等级相加，无敌时间和两个效果按最高一件算、不叠加。
 * 以前是在护甲之前固定 +25%、和等级无关，无敌时间和效果却按全身等级之和叠加。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "furnace_feather",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentFurnaceFeather extends EnchantmentBase {

    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;
    /** 每级延长无敌时间的系数 */
    private static final double INVULNERABLE_FACTOR = 1.5D;
    /** 每级速度 / 跳跃提升的持续时间（tick） */
    private static final int EFFECT_TICKS_PER_LEVEL = 40;
    /** 全身每级的受伤倍率加成 */
    private static final float DAMAGE_PER_LEVEL = 0.5F;

    public EnchantmentFurnaceFeather() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 受击时结算一次：放大最终伤害，再给无敌时间和机动性增益
     * <p>
     * 全身一起结算，所以挂静态监听器、每次伤害只走一遍。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        Enchantment furnaceFeather = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFurnaceFeather.class);
        if (furnaceFeather == null) {
            return;
        }

        // 全身等级之和（定伤害倍率）与最高一件（定无敌时间和效果）
        int totalLevel = 0;
        int highestLevel = 0;
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack stack = victim.getItemStackFromSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int pieceLevel = capLevel(EnchantmentHelper.getEnchantmentLevel(furnaceFeather, stack), LEVEL_CAP);
            if (pieceLevel > 0) {
                totalLevel += pieceLevel;
                highestLevel = Math.max(highestLevel, pieceLevel);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        int effectiveLevel = capLevel(highestLevel, LEVEL_CAP);

        // 延长无敌时间（原版在发伤害事件之前就把 hurtResistantTime 设好了，这里覆盖掉）
        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime
                + victim.maxHurtResistantTime / 2.0 * effectiveLevel * INVULNERABLE_FACTOR);

        // 速度
        victim.addPotionEffect(new PotionEffect(
                MobEffects.SPEED,
                effectiveLevel * EFFECT_TICKS_PER_LEVEL,
                effectiveLevel - 1
        ));

        // 跳跃提升
        victim.addPotionEffect(new PotionEffect(
                MobEffects.JUMP_BOOST,
                effectiveLevel * EFFECT_TICKS_PER_LEVEL,
                effectiveLevel - 1
        ));

        // 受到的最终伤害 ×(全身等级之和 × 50% + 100%)
        evt.setAmount(evt.getAmount() * (1 + DAMAGE_PER_LEVEL * totalLevel));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}