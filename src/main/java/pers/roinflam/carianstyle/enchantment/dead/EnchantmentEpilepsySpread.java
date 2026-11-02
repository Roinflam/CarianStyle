package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import javax.annotation.Nonnull;
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
 * {@code knockBack(attacker, strength, x, z)} 内部会对传入向量<b>取反</b>再施加。
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
 * 都会因 {@link EntityLivingUtil#kill} 内部的存活判断而空转——
 * 离线玩家的 {@code EntityPlayerMP} 已被标记移除。
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
 * <h3>1.12.2 版</h3>
 * <ul>
 *     <li>只有「已经不在世界里、但生命还大于 0」（登出）才登记待补刀；演出期间被别的东西打死的
 *         （生命已经归零）不登记，免得重生后下次登录又被杀一次。</li>
 *     <li>两个内部事件类在 1.12.2 的事件总线上不会和猩红罗妮亚的同名内部类撞：
 *         1.12.2 给每个监听器生成的类名带自增序号。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.epilepsy_spread.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "epilepsy_spread";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 16，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    16, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 24，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    24, 1, 200);

    /**
     * 濒死标记的过期时长（tick）
     * <p>默认 72000，允许范围 20 ~ 432000。</p>
     */
    private static final EnchantmentValues.Handle PENDING_DEATH_EXPIRY =
            EnchantmentValues.define(VALUE_ID, "pending_death_expiry",
                    72000, 20, 432000);

    /**
     * 濒死判定的延迟（tick）
     * <p>默认 20，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle PENDING_DEATH_DELAY =
            EnchantmentValues.define(VALUE_ID, "pending_death_delay",
                    20, 1, 1200);

    /**
     * 触发所需的剩余生命比例阈值
     * <p>默认 0.3，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.3D, 0.0D, 1.0D);

    /**
     * 触发后自身保留的生命比例
     * <p>默认 0.3，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REVIVE_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "revive_health_ratio",
                    0.3D, 0.0D, 1.0D);

    /**
     * 触发冷却时间（tick）
     * <p>默认 1800，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "cooldown",
                    1800, 20, 144000);

    /**
     * 每级增加的搜索半径（格，受上限截断）
     * <p>默认 4，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle RADIUS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "radius_per_level",
                    4, 1, 32);

    /**
     * 每级的击退强度系数
     * <p>默认 0.7，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "knockback_per_level",
                    0.7D, 0.0D, 5.0D);

    /**
     * 自身持续伤害的最大生命占比总量
     * <p>默认 0.3，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DOT_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_dot_ratio",
                    0.3D, 0.0D, 1.0D);

    /**
     * 每级施加给目标的持续伤害占比总量
     * <p>默认 0.3，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle TARGET_DOT_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "target_dot_per_level",
                    0.3D, 0.0D, 2.0D);

    /**
     * 「待补刀」标记键。
     * <p>演出结束时若持有者已离线（补刀空转），登记此标记；
     * 玩家重新登录后由 {@link ServerEventHandler} 补上死亡，详见类注释「v3.1 修复」小节。</p>
     */
    private static final String PENDING_DEATH_KEY = "epilepsy_spread_pending_death";

    /**
     * 重登补刀的延迟（tick）。
     * <p>1 秒。等客户端完成进入世界的初始化再执行死亡，避免死亡画面在加载过程中弹出。</p>
     */
    public EnchantmentEpilepsySpread() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 濒死触发：保留 30% 生命 → 拉取无敌 1.5 秒 → 群体灼烧 → 自身死亡。
     *
     * @param ctx        附魔上下文
     * @param pieceLevel 这一件的等级（实际按全身最高等级算）
     */
    @Override
    protected void onDamageAsVictimLowest(@Nonnull EnchantmentContext ctx, int pieceLevel) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();
        int level = highestLevel(hurter);

        if (EnchantmentDataManager.isOnCooldown("epilepsy_spread_cooldown", hurter.getUniqueID())) {
            return;
        }

        if (hurter.getHealth() - ctx.getDamage() <= hurter.getMaxHealth() * TRIGGER_HEALTH_RATIO.get()) {
            EnchantmentDataManager.setData("epilepsy_spread_active", hurter.getUniqueID(), true);
            EnchantmentDataManager.setCooldown("epilepsy_spread_cooldown", hurter.getUniqueID(), COOLDOWN.getInt());

            ctx.cancelEvent();
            hurter.setHealth(hurter.getMaxHealth() * (float) REVIVE_HEALTH_RATIO.get());
            hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 6));

            // 触发瞬间在脚底地面生成癫火狂乱演出，并绑定持有者跟随。
            // 时序关键：客户端演出的爆发主点（p≈0.42）恰好对齐下方延迟 30tick 的灼烧阶段，
            // 因此必须在这里调用，不能挪进延迟任务里（详见类注释）。
            // 纯服务端广播，不触发任何事件，不影响下方并发灼烧任务 / 上限 / 清理逻辑。
            if (hurter.world instanceof WorldServer) {
                CarianStyleEffects.frenziedFlame((WorldServer) hurter.world, hurter);
            }

            // 搜索半径硬上限（原式 level * 4，100 级 = 400 格）
            int searchRadius = Math.min(level * RADIUS_PER_LEVEL.getInt(), MAX_SEARCH_RADIUS.getInt());

            List<EntityLivingBase> rawEntities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    hurter,
                    searchRadius
            );

            // 命中数量硬上限，防止并发 SynchronizationTask 过载
            // 使用单独的列表存储被处理的实体，供延迟任务复用
            List<EntityLivingBase> entities = new ArrayList<>();
            int hitCount = 0;
            for (EntityLivingBase entityLivingBase : rawEntities) {
                if (hitCount >= MAX_TARGETS.getInt()) {
                    break;
                }
                entities.add(entityLivingBase);

                entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);
                if (!entityLivingBase.equals(hurter)) {
                    // 方向须为 hurter - entity（从 entity 指向 hurter），
                    // knockBack 内部取反后才能把 entity 推离 hurter
                    double x = hurter.posX - entityLivingBase.posX;
                    double z = hurter.posZ - entityLivingBase.posZ;
                    float stronge = (float) (level * KNOCKBACK_PER_LEVEL.get()
                            * Math.max(Math.abs(x), Math.abs(z)) / 14);
                    entityLivingBase.knockBack(hurter, stronge, x, z);
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
                        for (EntityLivingBase entityLivingBase : entities) {
                            entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);

                            // 应用火焰燃烧效果（需要同步网络）
                            DynamicAttributeManager.apply(entityLivingBase,
                                    DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(3 * 20 + 5, 0));
                            ClientSyncEffectHelper.onAttributeApplied(
                                    entityLivingBase, DynamicAttributes.EPILEPSY_FIRE_BURNING);

                            new SynchronizationTask(5, 1) {
                                private int elapsed = 0;

                                @Override
                                public void run() {
                                    if (++elapsed > 60 || !entityLivingBase.isEntityAlive()) {
                                        this.cancel();
                                        return;
                                    }

                                    if (entityLivingBase.equals(hurter)) {
                                        float damage = hurter.getMaxHealth() * (float) SELF_DOT_RATIO.get() / 60;
                                        if (hurter.getHealth() - damage * 2 > 0) {
                                            EntityLivingUtil.damageHealthDirectly(hurter, damage);
                                        } else {
                                            EnchantmentDataManager.removeData(
                                                    "epilepsy_spread_active", hurter.getUniqueID());
                                            EntityLivingUtil.kill(hurter,
                                                    NewDamageSource.epilepsyFire(hurter.world));
                                            this.cancel();
                                        }
                                    } else if (!EnchantmentTimeReversal.isReversing(entityLivingBase)) {
                                        float damage = hurter.getMaxHealth() * finalLevel * (float) TARGET_DOT_PER_LEVEL.get() * 2 / 60;
                                        if (entityLivingBase.getHealth() - damage * 2 > 0) {
                                            EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);
                                        } else {
                                            EntityLivingUtil.kill(entityLivingBase,
                                                    NewDamageSource.epilepsyFire(entityLivingBase.world));
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
                            // v3.1：持有者若已离线，kill 会因存活判断空转，
                            // 这次死亡就被白嫖掉了。此处登记待补刀标记，重登时补上（详见类注释）。
                            // 1.12.2 版：生命已经归零（演出期间被别的打死）的不登记（见类注释）
                            if (hurter.isEntityAlive()) {
                                EntityLivingUtil.kill(hurter, NewDamageSource.epilepsyFire(hurter.world));
                            } else if (hurter instanceof EntityPlayer && hurter.getHealth() > 0.0F) {
                                EnchantmentDataManager.setData(
                                        PENDING_DEATH_KEY, hurter.getUniqueID(), true, PENDING_DEATH_EXPIRY.getInt());
                            }
                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUniqueID());
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
    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", hurter.getUniqueID());

        if (isActive != null && isActive) {
            ctx.cancelEvent();
        }
    }

    /**
     * 客户端：拉取无敌期间压制跳跃，避免本地预测导致的抖动。
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID)
    public static class ClientEventHandler {

        /**
         * 每 tick 检查是否处于拉取无敌状态，是则压制跳跃。
         *
         * @param evt 生物 tick 事件
         */
        @SubscribeEvent
        public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
            if (evt.getEntity().world.isRemote) {
                EntityLivingBase entityLiving = evt.getEntityLiving();
                Boolean isActive = EnchantmentDataManager.getData(
                        "epilepsy_spread_active", entityLiving.getUniqueID());

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
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID)
    public static class ServerEventHandler {

        /**
         * 玩家重新登录时，若存在待补刀标记则延迟补上死亡。
         *
         * @param evt 玩家登录事件
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent evt) {
            EntityPlayer player = evt.player;
            if (player.world.isRemote) {
                return;
            }

            Boolean pending = EnchantmentDataManager.getData(PENDING_DEATH_KEY, player.getUniqueID());
            if (pending == null || !pending) {
                return;
            }

            // 先清标记再补刀：即使补刀因任何原因失败也不会陷入「每次登录都被杀」的死循环
            EnchantmentDataManager.removeData(PENDING_DEATH_KEY, player.getUniqueID());

            new SynchronizationTask(PENDING_DEATH_DELAY.getInt()) {
                @Override
                public void run() {
                    if (player.isEntityAlive()) {
                        EntityLivingUtil.kill(player, NewDamageSource.epilepsyFire(player.world));
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
