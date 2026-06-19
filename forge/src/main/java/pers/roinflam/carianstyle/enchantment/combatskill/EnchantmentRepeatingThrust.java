package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
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
 * <p>v2.2：onDamageAsAttackerHighest 走中央事件分发器，已被 scanEntity 拦截。
 * onLivingDeath 是清理逻辑（清除目标死亡时的连击叠层），不影响触发行为，无需开关。</p>
 *
 * @author RoinFlam
 * @version 2.2
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

    private static final String CURRENT_TARGET_KEY = "repeating_thrust_target";
    private static final String STACK_COUNT_KEY = "repeating_thrust_stacks";
    private static final int STACK_DURATION = 200;

    public EnchantmentRepeatingThrust() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();

        String storedTargetUUID = EnchantmentDataManager.getData(CURRENT_TARGET_KEY, attackerUUID);
        int currentStacks = EnchantmentDataManager.getCounter(STACK_COUNT_KEY, attackerUUID);

        boolean isSameTarget = victimUUID.toString().equals(storedTargetUUID);

        if (isSameTarget) {
            currentStacks++;
        } else {
            currentStacks = 1;
        }

        EnchantmentDataManager.setData(CURRENT_TARGET_KEY, attackerUUID, victimUUID.toString(), STACK_DURATION);
        EnchantmentDataManager.setCounter(STACK_COUNT_KEY, attackerUUID, currentStacks, STACK_DURATION);

        float damageMultiplier = 1 + (currentStacks * effectiveLevel * 0.05f);
        ctx.multiplyDamage(damageMultiplier);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 清理类逻辑：目标死亡时清除其攻击者的连击叠层。
        // 此处不影响附魔触发行为，无需接入怪物附魔开关。

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

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(repeatingThrust, heldItem);
        if (level <= 0) {
            return;
        }

        UUID attackerUUID = attacker.getUUID();

        String storedTargetUUID = EnchantmentDataManager.getData(CURRENT_TARGET_KEY, attackerUUID);

        if (deadUUID.toString().equals(storedTargetUUID)) {
            EnchantmentDataManager.removeData(CURRENT_TARGET_KEY, attackerUUID);
            EnchantmentDataManager.resetCounter(STACK_COUNT_KEY, attackerUUID);
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
