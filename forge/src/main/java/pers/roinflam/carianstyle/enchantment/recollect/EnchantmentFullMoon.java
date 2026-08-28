// 文件：EnchantmentFullMoon.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/recollect/EnchantmentFullMoon.java
package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.UUID;

/**
 * 满月附魔
 * <p>修复: DarkMoon检查getUsedItemHand -> InteractionHand.MAIN_HAND</p>
 * <p>v2.2新增: onLivingDeath 入口接入怪物附魔触发开关</p>
 * <p>v2.3新增: onLivingHeal 入口补齐怪物附魔触发开关</p>
 * <p>v2.4新增: 月华复活特效</p>
 *
 * <h3>v2.5：特效时长与实际回血时长严格对齐</h3>
 * <p>
 * <b>问题：</b>本附魔的回血持续时间<b>取决于持有者有没有装备暗月</b>——
 * 不带是 200 tick(10 秒)，带是 400 tick(20 秒)。而客户端无从得知这一点，
 * 早期版本只能让渲染器取最长的 20 秒写死，结果不带暗月时
 * <b>特效比实际回血多播 10 秒</b>——玩家早就满血了，月光还挂在头顶。
 * </p>
 * <p>
 * <b>修复：</b>把 {@code duration} 的计算<b>提前到特效调用之前</b>，
 * 再通过 {@link CarianStyleEffects#moonBlessing(ServerLevel, LivingEntity, int)}
 * 把实际 tick 数发给客户端。渲染器会用它把归一化进度换算回绝对秒数，
 * 因此 10 秒版与 20 秒版的<b>动画速度完全一致</b>，只是持续段长短不同。
 * </p>
 * <p>
 * <b>⚠ 顺序要求：</b>{@code hasDarkMoon} 与 {@code duration} 的计算<b>必须</b>
 * 排在 {@code moonBlessing(...)} 调用之前——这是本次改动唯一调整的代码顺序。
 * 若将来有人把特效调用挪到前面，特效时长会退回默认值，
 * 又会出现「特效比回血长」的老毛病。
 * </p>
 * <p>
 * <b>特效不产生任何机制影响</b>：不生成实体、不触发事件、不造成伤害，
 * 只向 64 格内的客户端广播一个约 34 字节的轻量包。
 * </p>
 *
 * @version 2.5
 */
@AutoRegisterEnchantment(id = "full_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class})
@Mod.EventBusSubscriber
public class EnchantmentFullMoon extends EnchantmentBase {
    private static final String FULL_MOON_STATE_KEY = "full_moon_state";
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 不带暗月时的回血持续时间（游戏刻）= 10 秒 */
    private static final int HEAL_DURATION_TICKS = 200;
    /** 装备暗月时的回血持续时间（游戏刻）= 20 秒 */
    private static final int HEAL_DURATION_TICKS_DARK_MOON = 400;

    public EnchantmentFullMoon() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 监听生物死亡事件 - 触发濒死复活机制
     * <p>v2.2：怪物附魔触发开关（濒死类）拦截</p>
     * <p>v2.4：复活瞬间广播月华特效（跟随持有者）</p>
     * <p>v2.5：特效时长改为传入实际回血 tick 数，与机制严格对齐</p>
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();

        // v2.2：怪物附魔触发开关 —— 满月属于濒死复活类，怪物身上不触发
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, true)) return;

        UUID uuid = holder.getUUID();
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
        }
        if (ConfigLoader.levelLimit) totalLevel = Math.min(totalLevel, 10);
        if (totalLevel <= 0) return;
        if (!EnchantmentDataManager.isOnCooldown(FULL_MOON_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            holder.setHealth(holder.getMaxHealth() * 0.0075f);
            EnchantmentDataManager.setCooldown(FULL_MOON_STATE_KEY, uuid, 400);

            // ⭐ v2.5：先算出实际回血时长，才能把它随特效包发给客户端。
            // 这两行原本在特效调用之后，现已提前——顺序不可再颠倒（详见类注释）。
            // 修复：使用主手检查DarkMoon
            boolean hasDarkMoon = false;
            Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
            ItemStack heldItem = holder.getItemInHand(InteractionHand.MAIN_HAND);
            if (darkMoon != null && !heldItem.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem) > 0) {
                hasDarkMoon = true;
            }
            int duration = hasDarkMoon ? HEAL_DURATION_TICKS_DARK_MOON : HEAL_DURATION_TICKS;

            // ⭐ v2.4/2.5：月华复活特效（跟随持有者，时长 = 实际回血时长）。
            // 放在此处是因为复活已确认触发（冷却检查已通过），不会出现「放了特效却没复活」。
            // 纯视觉，不产生任何机制影响。
            if (holder.level() instanceof ServerLevel serverLevel) {
                CarianStyleEffects.moonBlessing(serverLevel, holder, duration);
            }

            new SynchronizationTask(1, 1) {
                private int tick = 1;

                @Override
                public void run() {
                    if (++tick > duration || !holder.isAlive()) {
                        this.cancel();
                        EnchantmentDataManager.clearCooldown(FULL_MOON_STATE_KEY, uuid);
                        return;
                    }
                    holder.heal(holder.getMaxHealth() / 200);
                }
            }.start();
        }
        EnchantmentDataManager.setCooldown(FULL_MOON_COOLDOWN_KEY, uuid, holder.level().isDay() ? 3600 : 1800);
    }

    /**
     * 监听生物受伤事件 - 复活期间50%伤害减免
     * <p>注意：本事件不属于"死亡触发"，未接入怪物附魔开关；
     * 若 onLivingDeath 已被拦截，复活状态本来就不会被设置，本方法的伤害减免不会触发</p>
     *
     * @param evt 受伤事件
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
        }
        if (totalLevel > 0 && EnchantmentDataManager.isOnCooldown(FULL_MOON_STATE_KEY, holder.getUUID())) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    /**
     * 监听生物治疗事件 - 夜晚恢复效果+25%
     * <p>v2.3：补齐怪物附魔触发开关（受治疗者视角，非濒死触发）。</p>
     *
     * @param evt 治疗事件
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        LivingEntity holder = evt.getEntity();

        // v2.3：怪物附魔触发开关（受治疗者视角，治疗加成非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor);
        }
        if (totalLevel > 0) evt.setAmount(evt.getAmount() * 1.25f);
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (ench instanceof EnchantmentTimeReversal) return false;
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int l) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
