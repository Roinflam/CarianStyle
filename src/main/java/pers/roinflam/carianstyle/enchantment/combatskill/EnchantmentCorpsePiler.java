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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * 尸山血海附魔
 * <p>
 * 击杀时 50% 概率攒一层击杀数（上限 50 层，6000 tick 不再击杀则清零）；
 * 攻击时按层数增伤 1% × 层数 × 等级，并回复最大生命 0.05% × 层数 × 等级。持有者死亡时层数减半。
 * </p>
 * <p>v2.2：击杀计数器（killer视角）入口接入怪物附魔触发开关。
 * 死亡者计数衰减为清理逻辑，无需开关。
 * onDamageAsAttackerHighest 走中央分发器，已被 scanEntity 拦截。</p>
 * <p>v2.3：增伤与回血要求玩家满蓄力；击杀计数不受影响。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "corpse_piler",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentCorpsePiler extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "corpse_piler";

    /**
     * 每层击杀数每级提供的额外伤害倍率
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_KILL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_kill_per_level",
                    0.01D, 0.0D, 1.0D);

    /**
     * 每层击杀数每级提供的最大生命回复占比
     * <p>默认 0.0005，允许范围 0.0 ~ 0.1。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_KILL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_kill_per_level",
                    0.0005D, 0.0D, 0.1D);

    /**
     * 击杀层数上限
     * <p>默认 50，允许范围 1 ~ 999。</p>
     */
    private static final EnchantmentValues.Handle MAX_KILL_COUNT =
            EnchantmentValues.define(VALUE_ID, "max_kill_count",
                    50, 1, 999);

    /**
     * 击杀层数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNT_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "count_duration_ticks",
                    6000, 20, 72000);

    private static final String KILL_COUNT_KEY = "corpse_piler_kills";

    public EnchantmentCorpsePiler() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        // v2.3：回血按最大生命算、与这一下打多少无关，连点每下都回，要求满蓄力（增伤一并跳过）
        if (!isFullyCharged(attacker)) {
            return;
        }

        UUID uuid = attacker.getUniqueID();

        int killCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, uuid);
        if (killCount <= 0) {
            return;
        }

        ctx.addDamage(ctx.getDamage() * killCount * level * (float) DAMAGE_PER_KILL_PER_LEVEL.get());
        attacker.heal(attacker.getMaxHealth() * killCount * level * (float) HEAL_PER_KILL_PER_LEVEL.get());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase dead = evt.getEntityLiving();

        Enchantment corpsePiler = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCorpsePiler.class);
        if (corpsePiler == null) {
            return;
        }

        // 击杀者增加计数
        if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();

            // v2.2：怪物附魔触发开关（击杀者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) {
                ItemStack heldItem = killer.getHeldItem(EnumHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getEnchantmentLevel(corpsePiler, heldItem);

                    if (level > 0) {
                        if (killer.world.rand.nextBoolean()) {
                            int current = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, killer.getUniqueID());
                            int newCount = Math.min(current + 1, MAX_KILL_COUNT.getInt());
                            EnchantmentDataManager.setCounter(KILL_COUNT_KEY, killer.getUniqueID(), newCount, COUNT_DURATION_TICKS.getInt());
                        }
                    }
                }
            }
        }

        // 死亡者计数衰减（清理逻辑，无需开关）
        int deadCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, dead.getUniqueID());
        if (deadCount > 0) {
            EnchantmentDataManager.setCounter(KILL_COUNT_KEY, dead.getUniqueID(), deadCount / 2, COUNT_DURATION_TICKS.getInt());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
