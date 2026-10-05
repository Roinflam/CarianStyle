package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 水鸟乱舞附魔
 * <p>
 * 武器附魔，连击系统
 * 攻击时：
 * - 将伤害分成(等级+1)段
 * - 重置玩家攻击冷却
 * - 每2tick造成一段伤害
 * </p>
 *
 * <h3>v2.1新增：连斩刀光（纯视觉，机制零影响）</h3>
 * <p>
 * 水鸟乱舞是玛莲妮亚最标志性的招式，但在此之前<b>完全没有任何视觉</b>——
 * 玩家只看到伤害数字连着跳几下，感受不到「乱舞」，
 * 甚至分不清是这个附魔生效了还是纯粹攻速快。
 * </p>
 * <p>
 * 本次在<b>每一段</b>攻击各触发一道刀光：首段在
 * {@link #onHurtAsAttackerHighest} 里、后续 {@code level} 段在延迟任务里，
 * 与实际伤害<b>严格同帧</b>。因此玩家看到几道刀光，就是真的挨了几下——
 * 视觉与机制不会脱节。
 * </p>
 * <p>
 * <b>为什么一段一个特效包，而不是发一个包让客户端播 N 道：</b>
 * 后者需要在包里加「段数」字段，而包格式一改就要同时改编解码与全部既有类型的构造点；
 * 而多个短命特效各自独立的诞生时刻，本身就天然产生了错相叠加的连斩节奏，
 * 客户端一行状态都不用维护。段序号只用来派生交叉方向，随 yaw 一起编码，无需新字段。
 * </p>
 * <p>
 * <b>开销：</b>每段一个约 25 字节的包，且经 {@code PacketDistributor.NEAR}
 * 只广播给 48 格内的玩家。附魔为 RARE、上限 3 级，即单次挥砍至多 4 个包，
 * 相比它本身触发的 4 次完整伤害管线可以忽略。
 * </p>
 *
 * <h3>v2.2：攻击冷却改为延后清零</h3>
 * <p>
 * <b>问题：</b>本附魔在 LivingHurtEvent HIGHEST 里过了蓄力判断就立刻清零攻击冷却，
 * 同一下挥击里排在后面的蓄力判断（命定之死在 LOWEST）读到的都是清零后的值，全部失效——
 * 带水鸟乱舞的武器打不出命定之死，普通武器和拔刀剑都一样。
 * </p>
 * <p>
 * <b>为什么这么改：</b>清零是为了让补刀段读到低蓄力、不把被蓄力拦着的附魔每段都再触发一遍，这个作用要保留。
 * 改用 {@link AttackCooldownHelper#resetNextTick}，在下一个服务端 tick 开头（HIGHEST，早于驱动补刀段的
 * {@link SynchronizationTask}）清零：首段的事件链读到出手前的蓄力，补刀段读到清零后的蓄力。
 * </p>
 * <p>
 * <b>行为影响：</b>首段上的命定之死等满蓄力附魔恢复生效；补刀段与之前一样不会重复触发它们；
 * 拆段方式、段数、间隔都没有变化。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "waterfowl_flurry",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentWaterfowlFlurry extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.waterfowl_flurry.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "waterfowl_flurry";

    /**
     * 「水鸟乱舞伤害」标记的保持时长（tick，需覆盖全部段数）
     * <p>默认 100，允许范围 20 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle MARKER_DURATION =
            EnchantmentValues.define(VALUE_ID, "marker_duration",
                    100, 20, 1200);

    /**
     * 两段攻击之间的间隔（tick）
     * <p>默认 2，允许范围 1 ~ 40。</p>
     */
    private static final EnchantmentValues.Handle HIT_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "hit_interval",
                    2, 1, 40);

    /**
     * 每段命中后目标的无敌帧（tick）
     * <p>默认 10，允许范围 0 ~ 40。</p>
     */
    private static final EnchantmentValues.Handle HIT_INVULNERABLE_TIME =
            EnchantmentValues.define(VALUE_ID, "hit_invulnerable_time",
                    10, 0, 40);


    private static final String DAMAGE_TYPE_MARKER = "waterfowl_dance_marker";

    public EnchantmentWaterfowlFlurry() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();
        DamageSource damageSource = ctx.getDamageSource();

        if (victim == null || damageSource == null) {
            return;
        }

        if (attacker instanceof Player) {
            if (!isJustSwung((Player) attacker)) {
                return;
            }
            // v2.2：延后到下一 tick 开头清零（见类注释），不在本次事件链中途清零
            AttackCooldownHelper.resetNextTick((Player) attacker);
        }

        // 防止递归：使用DataManager检查
        if (EnchantmentDataManager.getData(DAMAGE_TYPE_MARKER, attacker.getUUID()) != null) {
            return;
        }

        // 防止与死亡之刃冲突
        if ("deathBlade".equals(damageSource.getMsgId()) || "noDeathBlade".equals(damageSource.getMsgId())) {
            return;
        }

        float damagePerHit = ctx.getDamage() / (level + 1);
        ctx.setDamage(damagePerHit);

        // 标记为水鸟乱舞伤害
        EnchantmentDataManager.setData(DAMAGE_TYPE_MARKER, attacker.getUUID(), true, MARKER_DURATION.getInt());

        // ⭐ v2.1：首段刀光。放在这里而非任务里，是为了与本段伤害同帧——
        // 本方法运行时这一下伤害已经在结算中，刀光晚一帧出现就会与打击感脱节
        spawnSlash(attacker, 0);

        new SynchronizationTask(1, HIT_INTERVAL.getInt()) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > level || !victim.isAlive()) {
                    EnchantmentDataManager.removeData(DAMAGE_TYPE_MARKER, attacker.getUUID());
                    this.cancel();
                    return;
                }

                // ⭐ v2.1：后续各段的刀光。刻意放在上面的存活判断之后、hurt 之前——
                // 目标已死时不再补刀光，避免「对着空气继续劈」
                spawnSlash(attacker, time);

                victim.invulnerableTime = HIT_INVULNERABLE_TIME.getInt();
                victim.hurt(damageSource, damagePerHit);
            }
        }.start();
    }

    /**
     * 为某一段攻击广播一道刀光（v2.1 新增，纯视觉）。
     * <p>
     * 取<b>攻击者当前</b>的位置与朝向，而不是首段那一刻的快照——
     * 乱舞持续 {@code 2 × level} tick，期间玩家可能在移动 / 转身，
     * 用实时姿态才能让刀光跟着人走，读起来像「他在连续挥砍」而不是「原地放了个特效」。
     * </p>
     * <p>
     * {@code segmentIndex} 交给
     * {@link CarianStyleCombatArtEffects#waterfowlFlurry(ServerLevel, LivingEntity, int)}
     * 派生交叉方向，使相邻两段一左一右。
     * </p>
     *
     * @param attacker     攻击者
     * @param segmentIndex 段序号（首段 0，此后递增）
     */
    private static void spawnSlash(@NotNull LivingEntity attacker, int segmentIndex) {
        if (attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.waterfowlFlurry(serverLevel, attacker, segmentIndex);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
