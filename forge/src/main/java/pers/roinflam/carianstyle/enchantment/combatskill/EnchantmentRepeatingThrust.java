package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import java.util.UUID;

/**
 * 连击附魔
 * <p>
 * 对同一目标在10秒内重复攻击时，每次攻击伤害叠加 5% × 等级
 * 攻击后刷新10秒持续时间
 * 切换目标时清除之前的叠加
 * 同时只能对一个目标保持叠加
 * 目标死亡时清除叠加
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "repeating_thrust",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentRepeatingThrust extends EnchantmentBase {

    /**
     * 存储当前目标UUID的键
     */
    private static final String CURRENT_TARGET_KEY = "repeating_thrust_target";

    /**
     * 存储叠加层数的键
     */
    private static final String STACK_COUNT_KEY = "repeating_thrust_stacks";

    /**
     * 叠加持续时间（10秒 = 200 tick）
     */
    private static final int STACK_DURATION = 200;

    public EnchantmentRepeatingThrust() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发：叠加伤害并刷新计时
     */
    @Override
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();

        // 获取当前记录的目标UUID（如果存在）
        String currentTargetKey = CURRENT_TARGET_KEY + "_" + attackerUUID;
        String storedTargetUUID = EnchantmentDataManager.getData(currentTargetKey, attackerUUID);

        // 获取当前叠加层数
        String stackCountKey = STACK_COUNT_KEY + "_" + attackerUUID;
        int currentStacks = EnchantmentDataManager.getCounter(stackCountKey, attackerUUID);

        // 判断是否是同一个目标
        boolean isSameTarget = victimUUID.toString().equals(storedTargetUUID);

        if (isSameTarget) {
            // 同一目标：增加叠加层数
            currentStacks++;
        } else {
            // 新目标：重置叠加层数
            currentStacks = 1;
            // 更新目标UUID
            EnchantmentDataManager.setData(currentTargetKey, attackerUUID, victimUUID.toString(), STACK_DURATION);
        }

        // 更新叠加层数并刷新持续时间
        EnchantmentDataManager.setCounter(stackCountKey, attackerUUID, currentStacks, STACK_DURATION);

        // 计算伤害加成：每层 5% × 等级
        float damageMultiplier = 1 + (currentStacks * effectiveLevel * 0.05f);
        ctx.multiplyDamage(damageMultiplier);
    }

    /**
     * 目标死亡时清除叠加
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity dead = evt.getEntity();
        UUID deadUUID = dead.getUUID();

        Enchantment repeatingThrust = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRepeatingThrust.class);
        if (repeatingThrust == null) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(repeatingThrust, heldItem);
        if (level <= 0) {
            return;
        }

        UUID attackerUUID = attacker.getUUID();

        // 获取当前记录的目标UUID
        String currentTargetKey = CURRENT_TARGET_KEY + "_" + attackerUUID;
        String storedTargetUUID = EnchantmentDataManager.getData(currentTargetKey, attackerUUID);

        // 如果死亡的是当前目标，清除叠加
        if (deadUUID.toString().equals(storedTargetUUID)) {
            EnchantmentDataManager.removeData(currentTargetKey, attackerUUID);
            String stackCountKey = STACK_COUNT_KEY + "_" + attackerUUID;
            EnchantmentDataManager.setCounter(stackCountKey, attackerUUID, 0, 0);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}