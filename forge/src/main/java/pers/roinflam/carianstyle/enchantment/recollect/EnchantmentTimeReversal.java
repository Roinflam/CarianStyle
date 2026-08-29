package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import java.util.UUID;

/**
 * 时间逆转附魔
 * <p>
 * 死亡时触发：取消死亡，进入逆转状态100tick
 * 逆转状态：免疫伤害并反弹，累积伤害值
 * 逆转结束：治疗累积伤害×25%
 * 冷却6000tick
 * </p>
 * <p>
 * v2.1新增: onLivingDeath 入口接入怪物附魔触发开关，
 * 怪物身上的"濒死逆转+反弹"效果可由配置 allowMobTriggerDeathEnchantments 控制
 * </p>
 * <p>
 * v2.2新增: 反弹步骤新增 ThreadLocal 重入保护，阻断两个同时处于逆转状态的实体
 * 互相反弹形成的事件级联。逆转免疫（取消伤害）、伤害累积、单次反弹逻辑完全保留，
 * 仅阻断"反弹伤害再次触发反弹"的连锁。
 * </p>
 *
 * <h3>v2.3新增：逆转状态的客户端可见性同步（纯视觉，机制零影响）</h3>
 * <p>
 * <b>问题：</b>逆转是本模组戏剧性最强、却最没有反馈的机制之一——
 * 5 秒完全无敌、期间受到的全部伤害被储存并反弹、结束时回复其 25%。
 * 但在此之前，这 5 秒里<b>屏幕上什么都不会发生</b>：
 * 玩家只看到自己残血站着挨打却不掉血，既不知道自己正处在无敌里、
 * 也不知道已经攒了多少伤害、更不知道还剩几秒。
 * </p>
 * <p>
 * 本次接入两条<b>纯读取</b>的反馈链路，均不改动任何机制逻辑：
 * </p>
 * <ol>
 *     <li><b>世界视觉</b>——{@link #TIME_REVERSAL_SERIAL} 通过
 *         {@link ClientSyncEffectManager} 同步「该实体正处于逆转状态」，
 *         由客户端 {@code TimeReversalRenderer} 绘制逆行时环 / 凝固碎片 / 钟面。
 *         这条链路与 {@code EnchantmentGravitas.GRAVITY_FIELD_SERIAL} 完全同款，
 *         沿用现成的增量广播 + 定期重同步机制，<b>不新增任何网络包</b>；</li>
 *     <li><b>HUD 储存值</b>——{@code CarianStyleTimeReversalDisplay} 直接读
 *         {@link #REVERSAL_DAMAGE_KEY} 这份既有数据，本类无需为它做任何事。</li>
 * </ol>
 * <p>
 * <b>为什么用「持续状态同步」而不是发一个定点特效包：</b>逆转是一段
 * 100 tick 的<b>状态</b>而非瞬时演出，且期间玩家会被击退、会自己跑位——
 * 定点特效会让人跑出自己的光圈。用状态同步则视觉天然跟随实体，
 * 且状态结束时移除即可，不需要客户端猜时长。
 * </p>
 * <p>
 * <b>移除时机：</b>与治疗结算放在同一个 {@link SynchronizationTask} 里，
 * 因此视觉的消失与「无敌结束」严格同帧，不会出现「光还亮着但已经能被打死」的误导。
 * 若实体在此期间被移除 / 卸载，{@code ClientSyncEffectManager} 每 5 秒一次的
 * 全量重同步会把它剪掉；实体死亡则由 {@code ClientSyncEffectEventHandler} 主动清理。
 * </p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "time_reversal",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentTimeReversal extends EnchantmentBase {

    /**
     * 逆转状态的客户端同步序列号（v2.3 新增）。
     * <p>
     * 已占用：1~3 自定义火焰、4 隐身、5 猩红腐败、6 重力力场、7 冻伤、
     * 8 出血、9 切腹、10 睡眠、11 噩兆。故逆转取 12。
     * </p>
     * <p>
     * <b>定义在附魔类而非渲染器里</b>，与
     * {@code EnchantmentGravitas.GRAVITY_FIELD_SERIAL} 的做法一致：
     * 渲染器是 {@code @OnlyIn(Dist.CLIENT)} 的，服务端引用它会在
     * Mohist 等混合端引发类加载问题；反过来渲染器引用本类则完全安全。
     * </p>
     */
    public static final int TIME_REVERSAL_SERIAL = 12;

    private static final String REVERSAL_COOLDOWN_KEY = "time_reversal_cooldown";
    private static final String REVERSAL_STATE_KEY = "time_reversal_state";

    /**
     * 逆转期间累积伤害的数据键（值类型 {@code Float}）。
     * <p>
     * v2.3：{@code CarianStyleTimeReversalDisplay} 会用同一个字符串读取它来驱动 HUD。
     * 那边按本模组既有惯例<b>复制了一份字符串常量并加注释标注来源</b>
     * （{@code CarianStyleStackDisplays} 里全部十余个 key 都是这么做的），
     * <b>改动此处务必同步改那边</b>，否则 HUD 会静默地永远读不到值。
     * </p>
     */
    private static final String REVERSAL_DAMAGE_KEY = "time_reversal_damage";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /**
     * 线程级重入保护标记。
     * <p>
     * 当本线程正在执行逆转反弹时置为 {@code true}。
     * 反弹使用原始伤害源（带攻击者实体），会再次进入 {@link #onLivingAttack}，
     * 若反弹目标同样处于逆转状态则会再次反弹，形成来回反弹。
     * 此标记在「反弹」步骤前拦截，确保反弹只发生一次；
     * 而逆转免疫（{@code setCanceled}）与伤害累积在标记拦截前已执行，正常效果完整保留。
     * </p>
     */
    private static final ThreadLocal<Boolean> PROCESSING_REFLECT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentTimeReversal() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 获取实体装备的时间逆转附魔总等级
     *
     * @param entity 实体
     * @return 附魔总等级
     */
    private static int getTotalLevel(LivingEntity entity) {
        Enchantment timeReversal = EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class);
        if (timeReversal == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(timeReversal, armor);
            }
        }
        return totalLevel;
    }

    /**
     * 监听生物死亡事件 - 触发时间逆转
     * <p>v2.1新增：怪物附魔触发开关（濒死类）拦截</p>
     * <p>v2.3新增：登记 / 注销逆转状态的客户端同步序列号（纯视觉，不影响任何判定）</p>
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关 —— 时间逆转属于濒死无敌类，怪物身上不触发
        // 若此处被拦截，REVERSAL_STATE_KEY 不会被设置，
        // onLivingAttack 中的反弹判断也自然失效，无需在 onLivingAttack 中重复拦截
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, true)) {
            return;
        }

        UUID uuid = holder.getUUID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(REVERSAL_COOLDOWN_KEY, uuid)) {
            return;
        }

        // 取消死亡事件
        evt.setCanceled(true);

        // 保留1点生命值
        holder.setHealth(1);
        holder.invulnerableTime = 20;

        // 设置冷却和逆转状态
        EnchantmentDataManager.setCooldown(REVERSAL_COOLDOWN_KEY, uuid, 6000);
        EnchantmentDataManager.setData(REVERSAL_STATE_KEY, uuid, true);
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, 0f);

        // ⭐ v2.3：登记逆转状态，供客户端绘制世界视觉。
        // 放在这里而非任务里，是为了让视觉与无敌<b>同帧开始</b>——
        // 玩家看到光圈亮起的那一刻就是他真正免疫的那一刻，不存在误导窗口
        ClientSyncEffectManager.addEntity(holder, TIME_REVERSAL_SERIAL);

        // 100tick后结束逆转状态
        new SynchronizationTask(100) {
            @Override
            public void run() {
                if (holder.isAlive()) {
                    Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
                    if (accumulated != null) {
                        // 治疗累积伤害的25%
                        holder.heal(accumulated * 0.25f);
                    }
                }
                EnchantmentDataManager.removeData(REVERSAL_STATE_KEY, uuid);
                EnchantmentDataManager.removeData(REVERSAL_DAMAGE_KEY, uuid);

                // ⭐ v2.3：与治疗结算同帧注销视觉。
                // 不加 isAlive 守卫：实体若已被移除，removeEntity 内部按维度查表，
                // 对不在集合中的条目是零开销空操作；而漏掉这次注销会让
                // 残留条目一直等到 5 秒后的定期重同步才被剪掉
                ClientSyncEffectManager.removeEntity(holder, TIME_REVERSAL_SERIAL);
            }
        }.start();
    }

    /**
     * 监听生物受击事件 - 逆转状态下反弹伤害
     * <p>注意：本事件不属于"死亡触发"，未接入怪物附魔开关。
     * 但由于 onLivingDeath 已拦截，REVERSAL_STATE_KEY 不会被设置，
     * 本方法对怪物自然不会触发反弹效果。</p>
     * <p>v2.2：反弹步骤前增加 {@link #PROCESSING_REFLECT} 重入保护，
     * 阻断双方逆转状态下的连锁反弹；免疫与累积在拦截前已执行，效果不变。</p>
     *
     * @param evt 受击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        Boolean inReversal = EnchantmentDataManager.getData(REVERSAL_STATE_KEY, uuid);
        if (inReversal == null || !inReversal) {
            return;
        }

        // 防止自伤循环
        if (evt.getEntity().equals(evt.getSource().getEntity())) {
            return;
        }

        // 取消伤害
        evt.setCanceled(true);

        // 累积伤害值
        Float accumulated = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, uuid);
        if (accumulated == null) {
            accumulated = 0f;
        }
        EnchantmentDataManager.setData(REVERSAL_DAMAGE_KEY, uuid, accumulated + evt.getAmount());

        // 反弹伤害
        if (evt.getSource().getEntity() instanceof LivingEntity) {
            // ⭐ v2.2：重入保护 —— 若本次受击由另一个逆转实体的反弹造成，跳过本次反弹，
            // 阻断双方逆转状态下的连锁反弹。免疫（取消伤害）与累积已在上方执行，正常效果保留
            if (PROCESSING_REFLECT.get()) {
                return;
            }

            LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

            // 反弹期间置位重入标记，确保反弹伤害不会再次触发反弹；
            // try-finally 保证标记可靠复位，避免标记滞留导致逆转反弹永久失效
            PROCESSING_REFLECT.set(Boolean.TRUE);
            try {
                attacker.hurt(evt.getSource(), evt.getAmount());
            } finally {
                PROCESSING_REFLECT.set(Boolean.FALSE);
            }
        }
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    /**
     * 判断是否为死亡类附魔（互斥）
     *
     * @param ench 待检查的附魔
     * @return 是否为死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench instanceof EnchantmentFullMoon || ench instanceof EnchantmentLivingCorpse;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
