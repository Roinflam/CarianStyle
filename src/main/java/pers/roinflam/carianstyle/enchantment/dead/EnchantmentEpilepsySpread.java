package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.event.LivingTickEvent;

/**
 * PORT-STUB(E2)
 *
 * 癫火蔓延附魔
 * <p>
 * 受到致命伤害时触发：
 * - 保留30%最大生命值
 * - 击退周围敌人
 * - 1.5秒后对范围内所有实体施加癫火灼烧效果
 * - 持续3秒的伤害，最终自身死亡
 * </p>
 * <p>
 * <h3>击退方向的坑</h3>
 * <p>
 * {@code knockback(strength, x, z)} 内部会对传入向量<b>取反</b>再施加。
 * 想把敌人推开必须传 {@code hurter - entity}；早期版本传反了，实际是把敌人拉向自己。
 * </p>
 *
 * <h3>性能安全上限（本附魔尤其危险）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限。
 *       原式 {@code level*4} 在 100 级时会达到 400 格。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数。</li>
 * </ul>
 * <p>
 * <b>本附魔为每个命中目标创建一个独立的 {@code SynchronizationTask}（60tick 循环任务）。</b>
 * 100 级 + 400 格 + 200 个实体 = 200 个并发 tick 任务，
 * 即使有 1800tick 冷却，单次触发就是灾难级性能打击。<b>请勿放宽这两个上限。</b>
 * </p>
 *
 * <h3>特效的时序必须对齐机制</h3>
 * <p>
 * {@link CarianStyleEffects#frenziedFlame} 是一段 5400ms 的两段式演出：
 * 狂乱蓄能（颤动放射焰舌 + 多重星形法阵）→ 1500ms 处白热爆发冲击环 → 焦黑余烬长尾。
 * 而本附魔恰好是「拉取无敌 1.5 秒 → 群体灼烧」。
 * </p>
 * <p>
 * 因此特效<b>必须在触发入口的第一时间调用</b>，
 * 不能塞进下方延迟 30tick 的 {@code SynchronizationTask} 里，否则蓄能段与前摇错位。
 * </p>
 * <p>
 * 特效绑定持有者<b>跟随实时位置</b>：触发后被击退 / 视角移动时狂乱裂纹贴身不脱离；
 * 实体死亡 / 移除后客户端回退到最后已知坐标继续播放余烬。
 * 中心 Y 取脚底（实体重载已自动处理），裂纹贴地铺开。
 * </p>
 *
 * <h3>v3.1 修复：演出期间登出可以完全逃掉这次死亡</h3>
 * <p>
 * <b>问题：</b>本附魔的机制是「拦下致命伤 → 保留 30% 血 → 1.5 秒无敌演出 → 3 秒灼烧 → 自身死亡」。
 * 若玩家在这段时间内退出游戏，两条补刀路径（每目标的循环任务、末尾的 66tick 任务）
 * 都会因 {@link EntityLivingUtil#kill} 内部的 {@code isAlive()} 判断而空转——
 * 离线玩家的 {@code ServerPlayer} 已被标记移除。
 * 玩家重新登录时读档回来是「30% 血、活着、冷却照常走」，等于白嫖了一次免死。
 * 猩红罗妮亚（{@code EnchantmentScarletLonia}）同理。
 * </p>
 * <p>
 * <b>修复：</b>末尾补刀失败时登记 {@link #PENDING_DEATH_KEY} 标记，
 * 由 {@link ServerEventHandler#onPlayerLoggedIn} 在玩家重新登录后延迟
 * {@link #PENDING_DEATH_DELAY} tick 补上死亡。
 * </p>
 * <p>
 * <b>已知局限：</b>{@link EnchantmentDataManager} 是纯内存存储，
 * 若服务器在玩家登出后、重登前<b>重启</b>，标记随之丢失，这次死亡仍会被逃掉。
 * 做成真正持久化需要走 NBT / Capability，改动面远超收益，故不处理。
 * </p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@Deprecated
public class EnchantmentEpilepsySpread extends EnchantmentBase {
    private static final String VALUE_ID = "epilepsy_spread";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    16, 1, 64);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    24, 1, 200);
    private static final EnchantmentValues.Handle PENDING_DEATH_EXPIRY = EnchantmentValues.define(VALUE_ID, "pending_death_expiry",
                    72000, 20, 432000);
    private static final EnchantmentValues.Handle PENDING_DEATH_DELAY = EnchantmentValues.define(VALUE_ID, "pending_death_delay",
                    20, 1, 1200);
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.3D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle REVIVE_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "revive_health_ratio",
                    0.3D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle COOLDOWN = EnchantmentValues.define(VALUE_ID, "cooldown",
                    1800, 20, 144000);
    private static final EnchantmentValues.Handle RADIUS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "radius_per_level",
                    4, 1, 32);
    private static final EnchantmentValues.Handle KNOCKBACK_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "knockback_per_level",
                    0.7D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle SELF_DOT_RATIO = EnchantmentValues.define(VALUE_ID, "self_dot_ratio",
                    0.3D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle TARGET_DOT_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "target_dot_per_level",
                    0.3D, 0.0D, 2.0D);
    private static final String PENDING_DEATH_KEY = "epilepsy_spread_pending_death";
    protected void onDamageAsVictimLowest(@NotNull EnchantmentContext ctx, int pieceLevel) {
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

