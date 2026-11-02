package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 刺客赌局附魔
 * <p>
 * 受击时获得隐身（等级 × 20 tick）；隐身状态下攻击：移除隐身，增伤 +25% × 等级；
 * 隐身状态下暴击：移除隐身，暴击倍率 ×2。
 * </p>
 * <p>v2.2：双向监听器+暴击事件入口接入怪物附魔触发开关。
 * CriticalHitEvent 仅玩家触发，开关检查作为安全网保留。</p>
 * <p>
 * 1.12.2 版：暴击事件里攻击者是 {@code getEntityPlayer()}；隐身是 dynamicattr 的内存属性
 * （{@link DynamicAttributes#STEALTH}），不再是隐藏药水。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "assassin_gambit",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentAssassinGambit extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "assassin_gambit";

    /**
     * 参与计算的等级上限，防止超高等级导致伤害失控
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级对潜行目标的额外伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.25D, 0.0D, 5.0D);

    /**
     * 每级提供的潜行持续时间（tick）
     * <p>默认 20，允许范围 1 ~ 400。</p>
     */
    private static final EnchantmentValues.Handle STEALTH_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "stealth_ticks_per_level",
                    20, 1, 400);

    /**
     * 潜行状态下的暴击倍率
     * <p>默认 2.0，允许范围 1.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle CRIT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "crit_multiplier",
                    2.0D, 1.0D, 20.0D);

    public EnchantmentAssassinGambit() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        // 攻击者视角：隐身状态下增伤
        // v2.2：怪物附魔触发开关（攻击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
            if (DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
                ItemStack heldItem = attacker.getHeldItem(EnumHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getEnchantmentLevel(assassinGambit, heldItem);
                    level = applyLevelLimit(assassinGambit, level, LEVEL_CAP.getInt());
                    if (level > 0) {
                        DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
                        evt.setAmount(evt.getAmount() + evt.getAmount() * level * (float) DAMAGE_PER_LEVEL.get());
                    }
                }
            }
        }

        // 受击者视角：获得隐身
        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack victimHeldItem = victim.getHeldItem(EnumHand.MAIN_HAND);
        if (!victimHeldItem.isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(assassinGambit, victimHeldItem);
            level = decayLevel(assassinGambit, level);
            if (level > 0) {
                DynamicAttributeManager.apply(victim,
                        DynamicAttributes.STEALTH.createInstance(level * STEALTH_TICKS_PER_LEVEL.getInt()));
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        if (!evt.isVanillaCritical()) {
            return;
        }
        if (!(evt.getTarget() instanceof EntityLivingBase)) {
            return;
        }

        EntityPlayer attacker = evt.getEntityPlayer();

        // v2.2：CriticalHitEvent 仅玩家触发，开关检查作为安全网（玩家始终放行）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        if (!DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(assassinGambit, heldItem);
        if (level > 0) {
            DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
            evt.setDamageModifier(evt.getDamageModifier() * (float) CRIT_MULTIPLIER.get());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
