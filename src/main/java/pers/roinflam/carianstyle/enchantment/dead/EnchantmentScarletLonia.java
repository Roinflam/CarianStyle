package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.event.LivingTickEvent;

/**
 * PORT-STUB(E2)
 *
 * 猩红罗妮亚附魔
 * <p>
 * 死亡时触发：
 * - 取消死亡，保留1点生命
 * - 击退周围敌人
 * - 1.5秒后对范围内敌人造成猩红腐败伤害并击退
 * - 最终自身死亡
 * </p>
 * <p>
 * <h3>击退方向的坑</h3>
 * <p>
 * {@code knockback(strength, x, z)} 内部会对传入向量<b>取反</b>再施加。
 * 因此想把敌人推开，必须传 {@code hurter - entity}（从 entity 指向 hurter）；
 * 早期版本传成了 {@code entity - hurter}，实际效果是把敌人<b>拉向</b>自己。
 * 本类两处击退现已统一为正确方向。
 * </p>
 *
 * <h3>性能安全上限</h3>
 * <ul>
 *   <li>{@link #MAX_KNOCKBACK_RADIUS}：第一次击退搜索半径硬上限。
 *       原式 {@code level*4} 在 100 级时会达到 400 格。</li>
 *   <li>{@link #MAX_DAMAGE_RADIUS}：第二次伤害搜索半径硬上限。
 *       原式 {@code finalLevel*2} 在 100 级时会达到 200 格。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数。</li>
 * </ul>
 * <p>
 * 本附魔虽有 1800tick 冷却，但一次触发就是双阶段 AOE，
 * 第二阶段还对每个目标调用 damageHealthDirectly + addEffect + knockback 三重事件链，
 * 高等级下单次触发就足以让 tick 耗时突破看门狗阈值。<b>请勿放宽这三个上限。</b>
 * </p>
 *
 * <h3>特效的时序必须对齐机制</h3>
 * <p>
 * {@link CarianStyleEffects#scarletBloom} 是一段 5400ms 的两段式演出：
 * 前 1500ms 花苞缓慢绽放（蓄能），1500ms 处盛放爆发，随后凋谢余波。
 * 而本附魔恰好是「拉取无敌 1.5 秒 → 第二阶段 AOE 伤害」。
 * </p>
 * <p>
 * 因此特效<b>必须在 {@link #onDeath} 触发的第一时间调用</b>，
 * 而不是塞进下方延迟 30tick 的 {@code SynchronizationTask} 里——
 * 否则蓄能段会与拉取无敌前摇错位，玩家看到的是「先无敌一秒半，花才开始长」。
 * </p>
 * <p>
 * 特效绑定持有者<b>跟随实时位置</b>：受致命伤后被击退、视角移动时花贴身不脱离；
 * 实体死亡 / 卸载后客户端自动回退到最后已知坐标继续播完凋谢。
 * 中心 Y 取脚底（实体重载已自动处理），花从地面长起。
 * </p>
 *
 * <h3>v3.1 修复：演出期间登出可以完全逃掉这次死亡</h3>
 * <p>
 * <b>问题：</b>本附魔的机制是「取消死亡 → 保留 1 点血 → 1.5 秒无敌演出 → 由延迟任务补上死亡」。
 * 若玩家在这 30tick 内退出游戏，延迟任务照常执行，
 * 但 {@link EntityLivingUtil#kill} 内部有 {@code isAlive()} 判断，
 * 而离线玩家的 {@code ServerPlayer} 已被标记移除、{@code isAlive()} 恒为 false，
 * 于是<b>补刀直接空转</b>。玩家重新登录时读档回来是「1 点血、活着、冷却照常走」——
 * 等于白嫖了一次免死。癫火蔓延（{@code EnchantmentEpilepsySpread}）同理。
 * </p>
 * <p>
 * <b>修复：</b>补刀失败时登记 {@link #PENDING_DEATH_KEY} 标记，
 * 由 {@link ServerEventHandler#onPlayerLoggedIn} 在玩家重新登录后延迟
 * {@link #PENDING_DEATH_DELAY} tick 补上死亡。
 * 延迟是为了等客户端完成进入世界的初始化，避免死亡画面在加载阶段弹出。
 * </p>
 * <p>
 * <b>已知局限：</b>{@link EnchantmentDataManager} 是纯内存存储，
 * 若服务器在玩家登出后、重登前<b>重启</b>，标记随之丢失，这次死亡仍会被逃掉。
 * 做成真正持久化需要走 NBT / Capability，改动面远超收益，故不处理——
 * 该场景要求玩家在 1.5 秒窗口内精准登出并恰好赶上一次重启，实战中可忽略。
 * </p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@Deprecated
public class EnchantmentScarletLonia extends EnchantmentBase {
    private static final String VALUE_ID = "scarlet_lonia";
    private static final EnchantmentValues.Handle MAX_KNOCKBACK_RADIUS = EnchantmentValues.define(VALUE_ID, "max_knockback_radius",
                    16, 1, 64);
    private static final EnchantmentValues.Handle MAX_DAMAGE_RADIUS = EnchantmentValues.define(VALUE_ID, "max_damage_radius",
                    12, 1, 64);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    24, 1, 200);
    private static final EnchantmentValues.Handle PENDING_DEATH_EXPIRY = EnchantmentValues.define(VALUE_ID, "pending_death_expiry",
                    72000, 20, 432000);
    private static final EnchantmentValues.Handle PENDING_DEATH_DELAY = EnchantmentValues.define(VALUE_ID, "pending_death_delay",
                    20, 1, 1200);
    private static final EnchantmentValues.Handle COOLDOWN = EnchantmentValues.define(VALUE_ID, "cooldown",
                    1800, 20, 144000);
    private static final EnchantmentValues.Handle KNOCKBACK_RADIUS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "knockback_radius_per_level",
                    4, 1, 32);
    private static final EnchantmentValues.Handle DAMAGE_RADIUS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_radius_per_level",
                    2, 1, 32);
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "knockback_strength_per_level",
                    0.7D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.05D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle ROT_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "rot_seconds_per_level",
                    10, 1, 120);
    private static final EnchantmentValues.Handle FINAL_KNOCKBACK_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "final_knockback_per_level",
                    0.75D, 0.0D, 5.0D);
    private static final String PENDING_DEATH_KEY = "scarlet_lonia_pending_death";
    protected void onDeath(@NotNull EnchantmentContext ctx, int pieceLevel) {
    }
    protected void onDefendHighest(@NotNull EnchantmentContext ctx, int level) {
    }

    public static class ClientEventHandler {
    }

    public static class ServerEventHandler {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

