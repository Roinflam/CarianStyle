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
 * <p>v2.2新增: onLivingDeath 入口接入怪物附魔触发开关，
 * 怪物身上的"濒死复活"效果可由配置 allowMobTriggerDeathEnchantments 控制</p>
 * <p>v2.3新增: onLivingHeal 入口补齐怪物附魔触发开关，
 * 怪物在夜晚的治疗加成由 allowMobTriggerEnchantments 控制</p>
 *
 * <h3>v2.4新增：月华复活特效</h3>
 * <p>
 * 濒死复活触发的瞬间，调用
 * {@link CarianStyleEffects#moonBlessing(ServerLevel, LivingEntity)} 广播一个
 * <b>跟随持有者</b>的自绘演出：头顶浮现月轮 → 一道月华柱自上而下笼罩全身 →
 * 脚下每秒一圈<b>向内收拢</b>的回春环（对应每秒回 0.5% 最大生命的节奏）→ 月尘上升。
 * </p>
 * <p>
 * <b>为什么用跟随而非定点：</b>复活后玩家往往立刻被继续攻击、被击退或主动跑位，
 * 定点特效会导致「人跑出了光柱」。跟随特效由客户端每帧取实体插值位置作为中心，
 * 实体若中途死亡 / 卸载则回退到最后已知坐标播完剩余演出。
 * </p>
 * <p>
 * <b>放置位置：</b>紧跟在 {@code evt.setCanceled(true)} 与设置残血之后、
 * 启动回血任务之前。此时已确认复活确实触发（冷却检查已通过），
 * 不会出现「特效放了但没复活」的情况。
 * </p>
 * <p>
 * <b>特效不产生任何机制影响</b>：不生成实体、不触发事件、不造成伤害，
 * 只向 64 格内的客户端广播一个约 30 字节的轻量包，对服务端 tick 的开销可视为零。
 * 特效时长（5 秒）与机制回血时长（10 秒 / 有暗月时 20 秒）刻意<b>不强行对齐</b>——
 * 演出覆盖最戏剧化的前半段即可，全程铺满反而拖沓。
 * </p>
 *
 * @version 2.4
 */
@AutoRegisterEnchantment(id = "full_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class})
@Mod.EventBusSubscriber
public class EnchantmentFullMoon extends EnchantmentBase {
    private static final String FULL_MOON_STATE_KEY = "full_moon_state";
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentFullMoon() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 监听生物死亡事件 - 触发濒死复活机制
     * <p>v2.2新增：怪物附魔触发开关（濒死类）拦截</p>
     * <p>v2.4新增：复活瞬间广播月华特效（跟随持有者）</p>
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getSource().isCreativePlayer()) return;
        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关 —— 满月属于濒死复活类，怪物身上不触发
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

            // ⭐ v2.4：月华复活特效（跟随持有者）。
            // 放在此处是因为复活已确认触发（冷却检查已通过），不会出现「放了特效却没复活」。
            // 纯视觉，不产生任何机制影响，详见类注释。
            if (holder.level() instanceof ServerLevel serverLevel) {
                CarianStyleEffects.moonBlessing(serverLevel, holder);
            }

            // 修复：使用主手检查DarkMoon
            boolean hasDarkMoon = false;
            Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
            ItemStack heldItem = holder.getItemInHand(InteractionHand.MAIN_HAND);
            if (darkMoon != null && !heldItem.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem) > 0) {
                hasDarkMoon = true;
            }
            int duration = hasDarkMoon ? 400 : 200;
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
     * <p>v2.3：补齐怪物附魔触发开关（受治疗者视角，非濒死触发）。
     * 此前缺失开关检查，导致怪物在夜晚被治疗时仍获得 25% 加成。</p>
     *
     * @param evt 治疗事件
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        LivingEntity holder = evt.getEntity();

        // ⭐ v2.3：怪物附魔触发开关（受治疗者视角，治疗加成非濒死触发）
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
