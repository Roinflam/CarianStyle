package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import java.util.UUID;

/**
 * 居合附魔
 * <p>
 * 每次攻击有概率触发居合斩（概率 = 1% + 攻击次数 × 0.5%）
 * 触发时：伤害 × 等级 × 3.3，但攻速降低75%持续等级×66tick
 * 未触发时：攻击计数+1，继续蓄力
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "unsheathe",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentUnsheathe extends EnchantmentBase {

    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("a9f7b1c6-4c2d-4f0e-9f2c-3a8b3f7d0a5b");
    private static final String ATTACK_SPEED_MODIFIER_NAME = "enchantment.unsheathe";
    private static final String ATTACK_COUNT_KEY = "unsheathe_attack_count";

    public EnchantmentUnsheathe() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        // 排除水鸟乱舞伤害
        if (ctx.getDamageSource() != null && "waterfowlDance".equals(ctx.getDamageSource().getMsgId())) {
            return;
        }

        // 只有玩家能触发
        if (!ctx.isHolderPlayer()) {
            return;
        }

        Player player = ctx.getHolderAsPlayer();

        // 检查刚挥剑
        if (!isJustSwung(player)) {
            return;
        }

        // 重置攻击冷却
        player.resetAttackStrengthTicker();

        // 获取攻击计数
        int attackCount = EnchantmentDataManager.getCounter(ATTACK_COUNT_KEY, player.getUUID());

        // 触发概率：1% + 攻击次数 × 0.5%
        double triggerChance = 1.0 + attackCount * 0.5;

        if (RandomUtil.percentageChance(triggerChance)) {
            // 触发居合斩
            EnchantmentDataManager.resetCounter(ATTACK_COUNT_KEY, player.getUUID());
            ctx.multiplyDamage(level * 3.3f);
            applyAttackSpeedPenalty(player, level);
        } else {
            // 未触发，累积计数（12000tick过期）
            EnchantmentDataManager.incrementCounter(ATTACK_COUNT_KEY, player.getUUID(), 12000);
        }
    }

    /**
     * 施加攻速惩罚：降低75%，持续等级×66tick
     */
    private void applyAttackSpeedPenalty(@NotNull Player player, int level) {
        AttributeInstance attributeInstance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attributeInstance == null || attributeInstance.getModifier(ATTACK_SPEED_MODIFIER_ID) != null) {
            return;
        }

        attributeInstance.addPermanentModifier(new AttributeModifier(
                ATTACK_SPEED_MODIFIER_ID,
                ATTACK_SPEED_MODIFIER_NAME,
                -0.75,
                AttributeModifier.Operation.MULTIPLY_TOTAL
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
    public static void onEntityJoinLevel(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide || !(evt.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) evt.getEntity();
        AttributeInstance attributeInstance = entity.getAttribute(Attributes.ATTACK_SPEED);
        if (attributeInstance != null) {
            attributeInstance.removeModifier(ATTACK_SPEED_MODIFIER_ID);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((28 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}