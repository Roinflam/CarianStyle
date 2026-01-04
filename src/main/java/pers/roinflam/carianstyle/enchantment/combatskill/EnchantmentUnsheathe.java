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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 居合附魔
 *
 * 每次攻击有概率触发居合斩（概率 = 1% + 攻击次数 × 0.5%）
 * 触发时：伤害 × 等级 × 3.3，但攻速降低75%持续等级×66tick
 * 未触发时：攻击计数+1，继续蓄力
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

        // 检查刚挥剑
        if (!isJustSwung(player)) {
            return;
        }

        // 重置攻击冷却
        player.resetCooldown();

        // 获取攻击计数
        int attackCount = EnchantmentDataManager.getCounter(ATTACK_COUNT_KEY, player.getUniqueID());

        // 触发概率：1% + 攻击次数 × 0.5%
        double triggerChance = 1.0 + attackCount * 0.5;

        if (RandomUtil.percentageChance(triggerChance)) {
            // 触发居合斩
            EnchantmentDataManager.resetCounter(ATTACK_COUNT_KEY, player.getUniqueID());
            ctx.multiplyDamage(level * 3.3f);
            applyAttackSpeedPenalty(player, level);
        } else {
            // 未触发，累积计数（12000tick过期）
            EnchantmentDataManager.incrementCounter(ATTACK_COUNT_KEY, player.getUniqueID(), 12000);
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
                -0.75,
                2
        ));

        new SynchronizationTask(level * 66) {
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