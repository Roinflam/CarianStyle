// 文件：EnchantmentRepeatingThrust.java
// 路径：src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentRepeatingThrust.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 连击附魔
 *
 * 效果：
 * - 满蓄力攻击同一目标时每击叠加1层，每层伤害 +5% × 等级（不封顶）
 * - 每次满蓄力命中都把目标和层数的10秒计时一起刷新
 * - 切换目标时清除之前的叠加（不满蓄力打到别的目标也会清）
 * - 同时只能对一个目标保持叠加
 * - 目标死亡时清除叠加
 * - 最大等级：5
 * <p>
 * 加层和增伤要求满蓄力：叠层与这一下打多少无关，以前连点能先攒层再一刀兑现。
 * 以前同目标连续命中只刷新层数的计时、不刷新目标记录，目标记录第一次命中 10 秒后到期，
 * 持续打同一个目标每 10 秒层数就被重置成 1。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "repeating_thrust",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
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

    // 每层每级增伤比例
    private static final float DAMAGE_PER_STACK_PER_LEVEL = 0.05F;
    // 等级上限（开了等级限制时）
    private static final int LEVEL_CAP = 10;

    /**
     * 构造函数
     */
    public EnchantmentRepeatingThrust() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发：叠加伤害并刷新计时
     */
    @Override
    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        // 被攻击者不能为空
        if (victim == null) {
            return;
        }

        int effectiveLevel = capLevel(level, LEVEL_CAP);

        UUID attackerUUID = attacker.getUniqueID();
        UUID victimUUID = victim.getUniqueID();

        // 获取当前记录的目标UUID（如果存在）
        String currentTargetKey = CURRENT_TARGET_KEY + "_" + attackerUUID;
        String storedTargetUUID = EnchantmentDataManager.getData(currentTargetKey, attackerUUID);

        // 获取当前叠加层数
        String stackCountKey = STACK_COUNT_KEY + "_" + attackerUUID;
        int currentStacks = EnchantmentDataManager.getCounter(stackCountKey, attackerUUID);

        // 判断是否是同一个目标
        boolean isSameTarget = victimUUID.toString().equals(storedTargetUUID);

        // 不满蓄力：不加层、不增伤；换了目标照样清层（否则满蓄力打首领、轻点打小怪，首领身上的层数一直保得住）
        if (!isFullyCharged(attacker)) {
            if (!isSameTarget) {
                EnchantmentDataManager.setData(currentTargetKey, attackerUUID, victimUUID.toString(), STACK_DURATION);
                EnchantmentDataManager.setCounter(stackCountKey, attackerUUID, 0, STACK_DURATION);
            }
            return;
        }

        // 同一目标加一层，新目标从 1 层开始
        currentStacks = isSameTarget ? currentStacks + 1 : 1;

        // 目标和层数的计时一起刷新
        EnchantmentDataManager.setData(currentTargetKey, attackerUUID, victimUUID.toString(), STACK_DURATION);
        EnchantmentDataManager.setCounter(stackCountKey, attackerUUID, currentStacks, STACK_DURATION);

        // 计算伤害加成：每层 5% × 等级
        float damageMultiplier = 1 + (currentStacks * effectiveLevel * DAMAGE_PER_STACK_PER_LEVEL);
        ctx.multiplyDamage(damageMultiplier);
    }

    /**
     * 目标死亡时清除叠加
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase dead = evt.getEntityLiving();
        UUID deadUUID = dead.getUniqueID();

        // 获取附魔实例
        Enchantment repeatingThrust = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRepeatingThrust.class);
        if (repeatingThrust == null) {
            return;
        }

        // 检查是否有攻击者
        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();

        // 检查攻击者主手是否持有此附魔
        if (attacker.getHeldItemMainhand().isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(repeatingThrust, attacker.getHeldItemMainhand());

        if (level <= 0) {
            return;
        }

        UUID attackerUUID = attacker.getUniqueID();

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
    public int getMinEnchantability(int enchantmentLevel) {
        // UNCOMMON 的默认公式：5 + (level - 1) * 10
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}