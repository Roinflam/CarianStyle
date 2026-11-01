package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 神圣之刃附魔
 *
 * 对亡灵生物：额外伤害 = 伤害 × 等级 × 0.25 × 目标血量比例；
 * 满蓄力时再按额外伤害回血，并永久削弱被砍的亡灵的攻击力（每下再叠等级 × 5%，最多 -99%）。
 * 对非亡灵生物：伤害降为20%。
 * <p>
 * 以前削弱施加在持有者自己身上，越砍自己越弱，而且只取 max(等级×-5%, -99%) 不累积；
 * 回血和削弱也不要求满蓄力，连点几下就能把亡灵削到底。现在改成削弱被砍的亡灵、每下累积一档，
 * 回血和削弱都要满蓄力。实体进世界、玩家重生时清掉这个修正器（顺带清掉旧版本留在玩家身上的那份）。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentDeathBlade.class,
                EnchantmentBlackFlameBlade.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentSacredBlade extends EnchantmentBase {

    /** 攻击力削弱修正器的 UUID，沿用旧值，清理时能顺带清掉旧版本留在玩家身上的那份 */
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "enchantment.sacred_blade";

    /** 对亡灵每级的额外伤害倍率 */
    private static final float BONUS_DAMAGE_PER_LEVEL = 0.25F;
    /** 额外伤害转化为治疗的比例 */
    private static final float LIFESTEAL_RATIO = 0.2F;
    /** 单次治疗量上限（占最大生命） */
    private static final float LIFESTEAL_CAP_RATIO = 0.1F;
    /** 对非亡灵的伤害倍率 */
    private static final float NON_UNDEAD_MULTIPLIER = 0.2F;
    /** 每命中一次的削弱步长（× 等级） */
    private static final double PENALTY_STEP_PER_LEVEL = -0.05D;
    /** 削弱下限：最多削到只剩 1% 攻击力 */
    private static final double PENALTY_FLOOR = -0.99D;

    public EnchantmentSacredBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (victim.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD) {
            // 对亡灵：额外伤害 = 当前伤害 × 等级 × 0.25 × 目标血量比例，不受蓄力限制
            float healthRatio = victim.getHealth() / victim.getMaxHealth();
            float bonusDamage = ctx.getDamage() * level * BONUS_DAMAGE_PER_LEVEL * healthRatio;
            ctx.addDamage(bonusDamage);

            // 削弱每下固定叠一档、和这一下打多少无关，连点就能削到底，所以回血和削弱一起要求满蓄力
            if (isFullyCharged(attacker)) {
                float healAmount = Math.min(bonusDamage * LIFESTEAL_RATIO, attacker.getMaxHealth() * LIFESTEAL_CAP_RATIO);
                attacker.heal(healAmount);
                applyAttackPenalty(victim, level);
            }
        } else {
            ctx.multiplyDamage(NON_UNDEAD_MULTIPLIER);
        }
    }

    /**
     * 对被砍的亡灵叠一档攻击力削弱：在已有修正器的基础上再减一个步长，下限 -99%
     *
     * @param target 被削弱的目标
     * @param level  附魔等级
     */
    private void applyAttackPenalty(@Nonnull EntityLivingBase target, int level) {
        IAttributeInstance attributeInstance = target.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (attributeInstance == null) {
            return;
        }

        double penaltyStep = level * PENALTY_STEP_PER_LEVEL;
        AttributeModifier existing = attributeInstance.getModifier(ATTACK_DAMAGE_MODIFIER_ID);
        double newReduction = existing == null
                ? Math.max(penaltyStep, PENALTY_FLOOR)
                : Math.max(existing.getAmount() + penaltyStep, PENALTY_FLOOR);

        if (existing != null) {
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        }
        attributeInstance.applyModifier(new AttributeModifier(
                ATTACK_DAMAGE_MODIFIER_ID,
                ATTACK_DAMAGE_MODIFIER_NAME,
                newReduction,
                2
        ));
    }

    /**
     * 玩家重生时清掉削弱（正常情况下玩家身上不会有，留着清旧版本的残留）
     *
     * @param evt 重生事件
     */
    @SubscribeEvent
    public static void onPlayerRespawn(@Nonnull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.player.world.isRemote || evt.isEndConquered()) {
            return;
        }
        removeAttackPenalty(evt.player);
    }

    /**
     * 实体进世界时清掉削弱：亡灵的削弱只管一场战斗，区块重载后失效；
     * 旧版本留在玩家身上的削弱，重登一次就清掉
     *
     * @param evt 进世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote || !(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }
        removeAttackPenalty((EntityLivingBase) evt.getEntity());
    }

    private static void removeAttackPenalty(@Nonnull EntityLivingBase entity) {
        IAttributeInstance attributeInstance = entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (attributeInstance != null && attributeInstance.getModifier(ATTACK_DAMAGE_MODIFIER_ID) != null) {
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与原版锋利冲突
        if (ench == Enchantments.SHARPNESS) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}