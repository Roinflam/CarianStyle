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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import java.util.UUID;

/**
 * 连击附魔
 * <p>v2.2：onDamageAsAttackerHighest 走中央事件分发器，已被 scanEntity 拦截。
 * onLivingDeath 是清理逻辑（清除目标死亡时的连击叠层），不影响触发行为，无需开关。</p>
 * <p>v2.3：加层与增伤要求玩家满蓄力；换目标清层、击杀清理的逻辑不变。</p>
 *
 * @author RoinFlam
 * @version 2.3
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

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.repeating_thrust.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "repeating_thrust";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每层每级的额外伤害倍率
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_STACK_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_stack_per_level",
                    0.05D, 0.0D, 2.0D);


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
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();

        String storedTargetUUID = EnchantmentDataManager.getData(CURRENT_TARGET_KEY, attackerUUID);
        int currentStacks = EnchantmentDataManager.getCounter(STACK_COUNT_KEY, attackerUUID);

        boolean isSameTarget = victimUUID.toString().equals(storedTargetUUID);

        // ⭐ v2.3：叠层与这一下打多少无关，连点能先攒层再一刀兑现，加层和增伤要求满蓄力。
        // 换目标清层不受蓄力限制：否则满蓄力打首领、轻点打小怪，首领身上的层数一直保得住
        if (!isFullyCharged(attacker)) {
            if (!isSameTarget) {
                EnchantmentDataManager.setData(CURRENT_TARGET_KEY, attackerUUID, victimUUID.toString(), STACK_DURATION);
                EnchantmentDataManager.setCounter(STACK_COUNT_KEY, attackerUUID, 0, STACK_DURATION);
            }
            return;
        }

        if (isSameTarget) {
            currentStacks++;
        } else {
            currentStacks = 1;
        }

        EnchantmentDataManager.setData(CURRENT_TARGET_KEY, attackerUUID, victimUUID.toString(), STACK_DURATION);
        EnchantmentDataManager.setCounter(STACK_COUNT_KEY, attackerUUID, currentStacks, STACK_DURATION);

        float damageMultiplier = 1 + (currentStacks * effectiveLevel * (float) DAMAGE_PER_STACK_PER_LEVEL.get());
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
