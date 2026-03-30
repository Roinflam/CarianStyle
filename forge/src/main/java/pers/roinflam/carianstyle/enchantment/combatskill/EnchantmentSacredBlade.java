package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

import java.util.UUID;

/**
 * 神圣之刃附魔
 * <p>
 * 对亡灵生物：额外伤害 = 伤害 × 等级 × 0.25 × 目标血量比例，并治疗自身
 * 但会累积降低自身攻击力（等级 × -5%，最多-99%）
 * 对非亡灵生物：伤害降为20%
 * </p>
 * <p>
 * 修复记录：
 * - 添加攻击力惩罚清除机制：玩家重生时清除、实体加入世界时清除
 * - 修复累积逻辑：每次攻击亡灵时惩罚递增，而非固定值
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentDeathBlade.class,
                EnchantmentBlackFlameBlade.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentSacredBlade extends EnchantmentBase {

    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "enchantment.sacred_blade";

    public EnchantmentSacredBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 判断是否为亡灵生物
        if (victim.getMobType() == MobType.UNDEAD) {
            // 对亡灵：额外伤害 = 当前伤害 × 等级 × 0.25 × 目标血量比例
            float healthRatio = victim.getHealth() / victim.getMaxHealth();
            float bonusDamage = ctx.getDamage() * level * 0.25f * healthRatio;
            ctx.addDamage(bonusDamage);

            // 治疗攻击者（上限为最大血量的10%）
            float healAmount = Math.min(bonusDamage * 0.2f, attacker.getMaxHealth() * 0.1f);
            attacker.heal(healAmount);

            // 累积攻击力惩罚
            applyAttackPenalty(attacker, level);
        } else {
            // 对非亡灵：伤害降为20%
            ctx.multiplyDamage(0.2f);
        }
    }

    /**
     * 应用攻击力惩罚（累积降低，最多-99%）
     * <p>
     * 修复：每次攻击在现有基础上累积惩罚，而非覆盖
     * </p>
     *
     * @param attacker 攻击者
     * @param level    附魔等级
     */
    private void applyAttackPenalty(@NotNull LivingEntity attacker, int level) {
        AttributeInstance attributeInstance = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attributeInstance == null) {
            return;
        }

        double penaltyStep = level * -0.05;
        AttributeModifier existing = attributeInstance.getModifier(ATTACK_DAMAGE_MODIFIER_ID);

        if (existing == null) {
            // 首次：添加惩罚
            attributeInstance.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    penaltyStep,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        } else {
            // 修复：累积惩罚，每次增加一个step，最多-99%
            double newReduction = Math.max(existing.getAmount() + penaltyStep, -0.99);
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
            attributeInstance.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    newReduction,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    /**
     * 玩家重生时清除攻击力惩罚
     *
     * @param evt 重生事件
     */
    @SubscribeEvent
    public static void onPlayerRespawn(@NotNull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.isEndConquered()) {
            return;
        }
        removeAttackPenalty(evt.getEntity());
    }

    /**
     * 实体加入世界时清除残留的攻击力惩罚
     *
     * @param evt 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide || !(evt.getEntity() instanceof LivingEntity)) {
            return;
        }
        removeAttackPenalty((LivingEntity) evt.getEntity());
    }

    /**
     * 移除攻击力惩罚修正器
     *
     * @param entity 实体
     */
    private static void removeAttackPenalty(@NotNull LivingEntity entity) {
        AttributeInstance attributeInstance = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attributeInstance != null) {
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.SHARPNESS) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
