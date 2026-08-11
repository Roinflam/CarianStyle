package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.List;

/**
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
@AutoRegisterEnchantment(
        id = "scarlet_lonia",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentScarletLonia extends EnchantmentBase {

    /** 第一次击退阶段 AOE 搜索半径硬上限（方块） */
    private static final int MAX_KNOCKBACK_RADIUS = 16;

    /** 第二次伤害阶段 AOE 搜索半径硬上限（方块） */
    private static final int MAX_DAMAGE_RADIUS = 12;

    /** 单次触发最大命中目标数：防止密集怪物场景下事件风暴 */
    private static final int MAX_TARGETS = 24;

    /**
     * 「待补刀」标记键。
     * <p>演出结束时若持有者已离线（补刀空转），登记此标记；
     * 玩家重新登录后由 {@link ServerEventHandler} 补上死亡，详见类注释「v3.1 修复」小节。</p>
     */
    private static final String PENDING_DEATH_KEY = "scarlet_lonia_pending_death";

    /**
     * 「待补刀」标记的存活时长（tick）。
     * <p>72000 tick = 1 小时。取得足够长以覆盖「打完就下线、隔天再上」之外的绝大多数情况；
     * 由于是纯内存存储，服务器重启即失效（见类注释「已知局限」）。</p>
     */
    private static final int PENDING_DEATH_EXPIRY = 72000;

    /**
     * 重登补刀的延迟（tick）。
     * <p>1 秒。等客户端完成进入世界的初始化再执行死亡，
     * 避免死亡画面在加载过程中弹出导致 UI 异常。</p>
     */
    private static final int PENDING_DEATH_DELAY = 20;

    public EnchantmentScarletLonia() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 死亡触发：取消死亡 → 拉取无敌 1.5 秒 → 第二阶段 AOE 伤害 → 自身死亡。
     *
     * @param ctx   附魔上下文
     * @param level 附魔等级
     */
    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("scarlet_lonia_cooldown", hurter.getUUID())) {
            Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUUID());
            if (isActive != null && isActive) {
                ctx.cancelEvent();
                hurter.setHealth(1);
            }
            return;
        }

        EnchantmentDataManager.setData("scarlet_lonia_active", hurter.getUUID(), true);
        EnchantmentDataManager.setCooldown("scarlet_lonia_cooldown", hurter.getUUID(), 1800);

        ctx.cancelEvent();
        hurter.setHealth(1);
        hurter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6));

        // ⭐ 触发瞬间在脚底地面生成猩红立体花，并绑定持有者跟随。
        // 时序关键：客户端演出的盛放主点（p≈0.42）恰好对齐下方延迟 30tick 的第二阶段爆发，
        // 因此必须在这里调用，不能挪进延迟任务里（详见类注释）。
        // 纯服务端广播，不触发任何事件，不影响下方双阶段 AOE / 上限 / 击退 / 清理逻辑。
        if (hurter.level() instanceof ServerLevel serverLevel) {
            CarianStyleEffects.scarletBloom(serverLevel, hurter);
        }

        // 第一次击退搜索半径硬上限（原式 level * 4，100 级 = 400 格）
        int knockbackRadius = Math.min(level * 4, MAX_KNOCKBACK_RADIUS);

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                hurter,
                knockbackRadius,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        // 第一次击退命中数量硬上限
        int knockbackHitCount = 0;
        for (LivingEntity entityLivingBase : entities) {
            if (knockbackHitCount >= MAX_TARGETS) {
                break;
            }
            // 方向须为 hurter - entity（从 entity 指向 hurter），
            // knockback 内部取反后才能把 entity 推离 hurter
            double x = hurter.getX() - entityLivingBase.getX();
            double z = hurter.getZ() - entityLivingBase.getZ();
            float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
            entityLivingBase.knockback(stronge, x, z);
            knockbackHitCount++;
        }

        int finalLevel = level;
        new SynchronizationTask(30) {
            @Override
            public void run() {
                // 注意：第二阶段的特效调用在 onDeath 触发入口（见上方），不在这里。
                // 此处只有 AOE 伤害 / 击退 / 自身死亡逻辑。

                // 第二次伤害搜索半径硬上限（原式 finalLevel * 2，100 级 = 200 格）
                int damageRadius = Math.min(finalLevel * 2, MAX_DAMAGE_RADIUS);

                List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        hurter,
                        damageRadius,
                        entityLivingBase -> !entityLivingBase.equals(hurter)
                );

                if (!nearbyEntities.isEmpty()) {
                    // 第二次伤害命中数量硬上限
                    int damageHitCount = 0;
                    for (Entity entity : nearbyEntities) {
                        if (damageHitCount >= MAX_TARGETS) {
                            break;
                        }
                        LivingEntity entityLivingBase = (LivingEntity) entity;
                        // 使用真伤系统
                        float damage = entityLivingBase.getHealth() * finalLevel * 0.05f;
                        EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);

                        entityLivingBase.addEffect(new MobEffectInstance(
                                CarianStylePotion.SCARLET_ROT.get(), finalLevel * 10 * 20, finalLevel - 1));

                        // 第二次击退方向原本就正确（hurter - entity）
                        double x = hurter.getX() - entityLivingBase.getX();
                        double z = hurter.getZ() - entityLivingBase.getZ();
                        entityLivingBase.knockback(finalLevel * 0.75f, x, z);
                        damageHitCount++;
                    }
                }

                EnchantmentDataManager.removeData("scarlet_lonia_active", hurter.getUUID());

                // ⭐ v3.1：持有者若已离线，kill 会因 isAlive() 为 false 空转，
                // 这次死亡就被白嫖掉了。此处登记待补刀标记，重登时补上（详见类注释）。
                if (hurter.isAlive()) {
                    EntityLivingUtil.kill(hurter, NewDamageSource.scarletRot(hurter.level()));
                } else if (hurter instanceof Player) {
                    EnchantmentDataManager.setData(
                            PENDING_DEATH_KEY, hurter.getUUID(), true, PENDING_DEATH_EXPIRY);
                }
            }
        }.start();
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
        Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUUID());

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
                Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", entityLiving.getUUID());

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
                        EntityLivingUtil.kill(player, NewDamageSource.scarletRot(player.level()));
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
