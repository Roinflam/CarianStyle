package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.ArrayList;
import java.util.List;

/**
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
@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块）：不管等级多高，最多搜索半径 16 方块 */
    private static final int MAX_SEARCH_RADIUS = 16;

    /** 单次触发最大命中目标数：防止并发 SynchronizationTask 过载 */
    private static final int MAX_TARGETS = 24;

    /**
     * 「待补刀」标记键。
     * <p>演出结束时若持有者已离线（补刀空转），登记此标记；
     * 玩家重新登录后由 {@link ServerEventHandler} 补上死亡，详见类注释「v3.1 修复」小节。</p>
     */
    private static final String PENDING_DEATH_KEY = "epilepsy_spread_pending_death";

    /**
     * 「待补刀」标记的存活时长（tick）。
     * <p>72000 tick = 1 小时。纯内存存储，服务器重启即失效（见类注释「已知局限」）。</p>
     */
    private static final int PENDING_DEATH_EXPIRY = 72000;

    /**
     * 重登补刀的延迟（tick）。
     * <p>1 秒。等客户端完成进入世界的初始化再执行死亡，避免死亡画面在加载过程中弹出。</p>
     */
    private static final int PENDING_DEATH_DELAY = 20;

    public EnchantmentEpilepsySpread() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 濒死触发：保留 30% 生命 → 拉取无敌 1.5 秒 → 群体灼烧 → 自身死亡。
     *
     * @param ctx   附魔上下文
     * @param level 附魔等级
     */
    @Override
    protected void onDamageAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("epilepsy_spread_cooldown", hurter.getUUID())) {
            return;
        }

        if (hurter.getHealth() - ctx.getDamage() <= hurter.getMaxHealth() * 0.3) {
            EnchantmentDataManager.setData("epilepsy_spread_active", hurter.getUUID(), true);
            EnchantmentDataManager.setCooldown("epilepsy_spread_cooldown", hurter.getUUID(), 1800);

            ctx.cancelEvent();
            hurter.setHealth(hurter.getMaxHealth() * 0.3f);
            hurter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));

            // ⭐ 触发瞬间在脚底地面生成癫火狂乱演出，并绑定持有者跟随。
            // 时序关键：客户端演出的爆发主点（p≈0.42）恰好对齐下方延迟 30tick 的灼烧阶段，
            // 因此必须在这里调用，不能挪进延迟任务里（详见类注释）。
            // 纯服务端广播，不触发任何事件，不影响下方并发灼烧任务 / 上限 / 清理逻辑。
            if (hurter.level() instanceof ServerLevel serverLevel) {
                CarianStyleEffects.frenziedFlame(serverLevel, hurter);
            }

            // 搜索半径硬上限（原式 level * 4，100 级 = 400 格）
            int searchRadius = Math.min(level * 4, MAX_SEARCH_RADIUS);

            List<LivingEntity> rawEntities = EntityUtil.getNearbyEntities(
                    LivingEntity.class,
                    hurter,
                    searchRadius
            );

            // 命中数量硬上限，防止并发 SynchronizationTask 过载
            // 使用单独的列表存储被处理的实体，供延迟任务复用
            List<LivingEntity> entities = new ArrayList<>();
            int hitCount = 0;
            for (LivingEntity entityLivingBase : rawEntities) {
                if (hitCount >= MAX_TARGETS) {
                    break;
                }
                entities.add(entityLivingBase);

                entityLivingBase.playSound(SoundEvents.GHAST_HURT, 1, 1);
                if (!entityLivingBase.equals(hurter)) {
                    // 方向须为 hurter - entity（从 entity 指向 hurter），
                    // knockback 内部取反后才能把 entity 推离 hurter
                    double x = hurter.getX() - entityLivingBase.getX();
                    double z = hurter.getZ() - entityLivingBase.getZ();
                    float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                    entityLivingBase.knockback(stronge, x, z);
                }
                hitCount++;
            }

            int finalLevel = level;
            new SynchronizationTask(30) {
                @Override
                public void run() {
                    // 注意：爆发阶段的特效调用在触发入口（见上方），不在这里。
                    // 此处只有并发灼烧伤害 / 自身死亡逻辑。

                    if (!entities.isEmpty()) {
                        for (Entity entity : entities) {
                            LivingEntity entityLivingBase = (LivingEntity) entity;
                            entityLivingBase.playSound(SoundEvents.GHAST_HURT, 1, 1);

                            // 应用火焰燃烧效果（需要同步网络）
                            DynamicAttributeManager.apply(entityLivingBase,
                                    DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(3 * 20 + 5, 0));
                            ClientSyncEffectHelper.onAttributeApplied(
                                    entityLivingBase, DynamicAttributes.EPILEPSY_FIRE_BURNING);

                            new SynchronizationTask(5, 1) {
                                private int tick = 0;

                                @Override
                                public void run() {
                                    if (++tick > 60 || !entityLivingBase.isAlive()) {
                                        this.cancel();
                                        return;
                                    }

                                    if (entityLivingBase.equals(hurter)) {
                                        float damage = hurter.getMaxHealth() * 0.3f / 60;
                                        if (hurter.getHealth() - damage * 2 > 0) {
                                            EntityLivingUtil.damageHealthDirectly(hurter, damage);
                                        } else {
                                            EnchantmentDataManager.removeData(
                                                    "epilepsy_spread_active", hurter.getUUID());
                                            EntityLivingUtil.kill(hurter,
                                                    NewDamageSource.epilepsyFire(hurter.level()));
                                            this.cancel();
                                        }
                                    } else {
                                        float damage = hurter.getMaxHealth() * finalLevel * 0.3f * 2 / 60;
                                        if (entityLivingBase.getHealth() - damage * 2 > 0) {
                                            EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);
                                        } else {
                                            EntityLivingUtil.kill(entityLivingBase,
                                                    NewDamageSource.epilepsyFire(entityLivingBase.level()));
                                            this.cancel();
                                        }
                                    }
                                }
                            }.start();
                        }
                    }

                    new SynchronizationTask(66) {
                        @Override
                        public void run() {
                            // ⭐ v3.1：持有者若已离线，kill 会因 isAlive() 为 false 空转，
                            // 这次死亡就被白嫖掉了。此处登记待补刀标记，重登时补上（详见类注释）。
                            if (hurter.isAlive()) {
                                EntityLivingUtil.kill(hurter, NewDamageSource.epilepsyFire(hurter.level()));
                            } else if (hurter instanceof Player) {
                                EnchantmentDataManager.setData(
                                        PENDING_DEATH_KEY, hurter.getUUID(), true, PENDING_DEATH_EXPIRY);
                            }
                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUUID());
                        }
                    }.start();
                }
            }.start();
        }
    }

    /**
     * 拉取无敌期间免疫一切伤害。
     *
     * @param ctx   附魔上下文
     * @param level 附魔等级
     */
    @Override
    protected void onDefendHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", hurter.getUUID());

        if (isActive != null && isActive) {
            ctx.cancelEvent();
        }
    }

    /**
     * 客户端：拉取无敌期间压制跳跃，避免本地预测导致的抖动。
     */
    @Mod.EventBusSubscriber
    public static class ClientEventHandler {

        /**
         * 每 tick 检查是否处于拉取无敌状态，是则压制跳跃。
         *
         * @param evt 生物 tick 事件
         */
        @SubscribeEvent
        public static void onLivingUpdate(@NotNull LivingEvent.LivingTickEvent evt) {
            if (evt.getEntity().level().isClientSide) {
                LivingEntity entityLiving = evt.getEntity();
                Boolean isActive = EnchantmentDataManager.getData(
                        "epilepsy_spread_active", entityLiving.getUUID());

                if (isActive != null && isActive) {
                    EntityLivingUtil.setJumped(entityLiving);
                }
            }
        }
    }

    /**
     * 服务端：处理「演出期间登出逃掉死亡」的补刀（v3.1 新增）。
     * <p>详见类注释「v3.1 修复」小节。</p>
     */
    @Mod.EventBusSubscriber
    public static class ServerEventHandler {

        /**
         * 玩家重新登录时，若存在待补刀标记则延迟补上死亡。
         *
         * @param evt 玩家登录事件
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(@NotNull PlayerEvent.PlayerLoggedInEvent evt) {
            Player player = evt.getEntity();
            if (player.level().isClientSide) {
                return;
            }

            Boolean pending = EnchantmentDataManager.getData(PENDING_DEATH_KEY, player.getUUID());
            if (pending == null || !pending) {
                return;
            }

            // 先清标记再补刀：即使补刀因任何原因失败也不会陷入「每次登录都被杀」的死循环
            EnchantmentDataManager.removeData(PENDING_DEATH_KEY, player.getUUID());

            new SynchronizationTask(PENDING_DEATH_DELAY) {
                @Override
                public void run() {
                    if (player.isAlive()) {
                        EntityLivingUtil.kill(player, NewDamageSource.epilepsyFire(player.level()));
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
