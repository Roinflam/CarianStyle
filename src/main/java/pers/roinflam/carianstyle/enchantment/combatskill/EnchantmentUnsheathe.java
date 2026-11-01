package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 居合附魔
 *
 * 满蓄力攻击时有概率触发居合斩（概率 = 1% + 攻击次数 × 0.5%）
 * 触发时：伤害 × 等级 × 3.3，但攻速降低75%持续等级×66tick
 * 未触发时：攻击计数+1，继续蓄力
 * <p>
 * 一下挥击只结算一次：横扫打到的副目标、范围伤害的其它目标、主副手都带本附魔时，
 * 以前每进来一次都要掷一次概率、累计一次计数，现在同一 tick 只算第一次。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "unsheathe",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentUnsheathe extends EnchantmentBase {

    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("a9f7b1c6-4c2d-4f0e-9f2c-3a8b3f7d0a5b");
    private static final String ATTACK_SPEED_MODIFIER_NAME = "enchantment.unsheathe";
    private static final String ATTACK_COUNT_KEY = "unsheathe_attack_count";

    /** 基础触发概率（%） */
    private static final double BASE_TRIGGER_CHANCE = 1.0D;
    /** 每次未触发累加的概率（%） */
    private static final double CHANCE_PER_ATTACK = 0.5D;
    /** 触发时每级的伤害倍率 */
    private static final float DAMAGE_PER_LEVEL = 3.3F;
    /** 攻速惩罚（总乘算） */
    private static final double ATTACK_SPEED_PENALTY = -0.75D;
    /** 攻速惩罚每级持续 tick */
    private static final int PENALTY_TICKS_PER_LEVEL = 66;
    /** 攻击计数过期 tick */
    private static final int COUNTER_EXPIRE_TICKS = 12000;

    public EnchantmentUnsheathe() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 排除水鸟乱舞伤害
        if (ctx.getDamageSource() != null && "waterfowlDance".equals(ctx.getDamageSource().damageType)) {
            return;
        }

        // 只有玩家能触发
        if (!ctx.isHolderPlayer()) {
            return;
        }

        EntityPlayer player = ctx.getHolderAsPlayer();

        // 满蓄力才算
        if (!isFullyCharged(player)) {
            return;
        }

        // 同一下挥击只结算一次；冷却在下一 tick 开头清零，不影响同一下里后面的蓄力判断
        if (!AttackCooldownHelper.spendSwing(player)) {
            return;
        }

        // 获取攻击计数
        int attackCount = EnchantmentDataManager.getCounter(ATTACK_COUNT_KEY, player.getUniqueID());

        // 触发概率：1% + 攻击次数 × 0.5%
        double triggerChance = BASE_TRIGGER_CHANCE + attackCount * CHANCE_PER_ATTACK;

        if (RandomUtil.percentageChance(triggerChance)) {
            // 触发居合斩
            EnchantmentDataManager.resetCounter(ATTACK_COUNT_KEY, player.getUniqueID());
            ctx.multiplyDamage(level * DAMAGE_PER_LEVEL);
            applyAttackSpeedPenalty(player, level);
        } else {
            // 未触发，累积计数（12000tick过期）
            EnchantmentDataManager.incrementCounter(ATTACK_COUNT_KEY, player.getUniqueID(), COUNTER_EXPIRE_TICKS);
        }
    }

    /**
     * 施加攻速惩罚：降低75%，持续等级×66tick
     */
    private void applyAttackSpeedPenalty(@Nonnull EntityPlayer player, int level) {
        IAttributeInstance attributeInstance = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (attributeInstance == null || attributeInstance.getModifier(ATTACK_SPEED_MODIFIER_ID) != null) {
            return;
        }

        attributeInstance.applyModifier(new AttributeModifier(
                ATTACK_SPEED_MODIFIER_ID,
                ATTACK_SPEED_MODIFIER_NAME,
                ATTACK_SPEED_PENALTY,
                2
        ));

        new SynchronizationTask(level * PENALTY_TICKS_PER_LEVEL) {
            @Override
            public void run() {
                attributeInstance.removeModifier(ATTACK_SPEED_MODIFIER_ID);
            }
        }.start();
    }

    /**
     * 实体加入世界时清理残留的攻速修正
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote || !(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase entity = (EntityLivingBase) evt.getEntity();
        IAttributeInstance attributeInstance = entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        if (attributeInstance != null) {
            attributeInstance.removeModifier(ATTACK_SPEED_MODIFIER_ID);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((28 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }
}