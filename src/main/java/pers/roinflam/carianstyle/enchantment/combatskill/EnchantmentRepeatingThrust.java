package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * 连击附魔
 * <p>
 * 连续攻击同一个目标时每下叠一层，伤害 × (1 + 层数 × 等级 × 5%)；换目标从 1 层重新叠，
 * 10 秒没再打或者目标死了清零。
 * </p>
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
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentRepeatingThrust extends EnchantmentBase {

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
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        UUID attackerUUID = attacker.getUniqueID();
        UUID victimUUID = victim.getUniqueID();

        String storedTargetUUID = EnchantmentDataManager.getData(CURRENT_TARGET_KEY, attackerUUID);
        int currentStacks = EnchantmentDataManager.getCounter(STACK_COUNT_KEY, attackerUUID);

        boolean isSameTarget = victimUUID.toString().equals(storedTargetUUID);

        // v2.3：叠层与这一下打多少无关，连点能先攒层再一刀兑现，加层和增伤要求满蓄力。
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
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 清理类逻辑：目标死亡时清除其攻击者的连击叠层。
        // 此处不影响附魔触发行为，无需接入怪物附魔开关。

        EntityLivingBase dead = evt.getEntityLiving();
        UUID deadUUID = dead.getUniqueID();

        Enchantment repeatingThrust = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRepeatingThrust.class);
        if (repeatingThrust == null) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        ItemStack heldItem = attacker.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(repeatingThrust, heldItem);
        if (level <= 0) {
            return;
        }

        UUID attackerUUID = attacker.getUniqueID();

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
