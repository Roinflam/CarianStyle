package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * 古龙雷电附魔
 * <p>
 * 死亡时触发：
 * - 对 60 格范围内的敌人降下红色龙雷
 * - 根据天气加成伤害倍率
 * - 持续攻击直到敌人死亡
 * </p>
 * <p>
 * <h3>天气倍率必须互斥判断</h3>
 * <p>
 * MC 中雷暴时 {@code isRaining()} <b>也返回 true</b>。
 * 早期版本先乘 isRaining 再乘 isThundering，倍率变成 {@code 1*2*4=8} 而非预期的 4。
 * 现改为互斥：雷暴 4x、下雨 2x、晴天 1x（与 PreciseLightning 一致）。
 * </p>
 *
 * <h3>红色龙雷：去掉原版闪电后必须自己补雷声</h3>
 * <p>
 * 为还原艾尔登法环「龙雷」的红色雷击意象，原版蓝白 {@code LightningBolt}
 * 已替换为 {@link CarianStyleEffects#redLightning}（自绘红色之字电柱 + 分叉 + 落地红色冲击）。
 * </p>
 * <p>
 * 原版闪电用的是 {@code setVisualOnly(true)}，本就只有「视觉 + 音效」无副作用，
 * 但<b>音效随它一起没了</b>。因此下方两条 {@code playSound}
 * （{@code LIGHTNING_BOLT_THUNDER} 雷鸣 + {@code LIGHTNING_BOLT_IMPACT} 落地）
 * <b>必须保留</b>，否则只有画面没有声音。
 * </p>
 *
 * <h3>为什么高频落雷不会「鬼畜」</h3>
 * <p>
 * 本附魔对同一目标每 5 tick 重复降雷。若每次都新建特效实例，
 * 同一处会同时叠着多道形态各异的闪电并不断新生，视觉上呈高频跳变。
 * </p>
 * <p>
 * 客户端 {@code AoeEffectManager} 对 {@code TYPE_RED_LIGHTNING} 做了 2.5 格内的
 * <b>同位置合并</b>——重复落雷只续命已存在那道、不新建，且其外形种子保持不变，
 * 因此表现为一道<b>持续劈着、缓慢明灭</b>的雷。这是红闪独有的处理，
 * 其余演出类型不参与合并（详见 {@code refreshNearbyRedLightning}）。
 * </p>
 *
 * <h3>v3.1 新增：命中目标数硬上限（{@link #MAX_TARGETS}）</h3>
 * <p>
 * <b>这是全模组最后一个没有目标数上限的 AOE 附魔。</b>其余附魔（猩红罗妮亚、癫火蔓延、
 * 因果律、火焰吞噬等）早已各自补上 {@code MAX_TARGETS}，唯独本附魔一直是
 * 「60 格范围内有多少打多少」，在实体密集场景（刷怪塔、村民聚集、大型基地）下会出问题：
 * </p>
 * <ol>
 *   <li><b>并发任务无上限</b>——每个命中目标创建一个 {@code SynchronizationTask(40, 5)}
 *       周期任务，持续最多 {@code 40 + 5 × level × 15} tick。300 个目标即 300 个并发任务，
 *       全部进入 {@code SynchronizationTask} 的长期任务表（{@code ConcurrentHashMap}），
 *       每 tick 全表遍历；</li>
 *   <li><b>网络包爆炸</b>——每次落雷广播 1 个红闪特效包 + 2 个音效包。
 *       落雷总次数由 {@code RandomUtil.randomList(level*100, 目标数)} 分配、
 *       单目标封顶 {@code level*15}，目标越多总次数越接近 {@code level*100}；
 *       但每次落雷的<b>三个包都要发给附近每个玩家</b>，目标数越多、落雷点越分散，
 *       同一时刻的包量峰值越高；</li>
 *   <li><b>客户端特效被挤掉</b>——红闪的同位置合并只覆盖 2.5 格，不同目标各自成一道。
 *       客户端 {@code AoeEffectManager.MAX_ACTIVE} 为 64，超出后
 *       {@code ACTIVE.remove(0)} 会挤掉最早的实例，表现为<b>闪电随机消失、闪烁</b>——
 *       目标越多这个现象越严重，反而不如封顶后好看。</li>
 * </ol>
 * <p>
 * 现封顶 {@value #MAX_TARGETS}：既远高于常规战斗场景（正常不会有 100 个敌人同时在 60 格内），
 * 保留「大范围降雷」的设计意图，又能在密集场景下守住并发任务数、包量峰值与客户端特效名额。
 * </p>
 * <p>
 * <b>注意：</b>{@code randomList} 的分配基数必须用<b>截断后的目标数</b>，否则
 * {@code level*100} 的总量会被分配给不存在的目标，造成实际落雷次数远低于预期
 * （封顶前 300 个目标每个平均 1 次，封顶后 100 个目标每个平均 3 次，总量不变、分布更集中）。
 * </p>
 * <p>
 * <b>搜索半径 60 格保持不变</b>：本附魔仅在死亡时触发一次、且有 1800 tick 冷却，
 * 单次范围查询的开销可接受；真正的风险在于「每个目标一个长期任务」，封目标数即可解决。
 * </p>
 *
 * <h3>v3.1 新增：存活检查提前到发包之前</h3>
 * <p>
 * 原顺序是「发红闪 + 发两个音效 → 检查 {@code isAlive()} → 不存活则 cancel」，
 * 意味着目标死亡 / 卸载后<b>仍会多发一轮三个包</b>（画面上是往一具尸体或空气里劈一道雷）。
 * 现将存活检查提到发包之前，死亡目标立即 cancel、不再产生任何网络流量。
 * </p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "ancient_dragon_lightning",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
public class EnchantmentAncientDragonLightning extends EnchantmentBase {

    /**
     * 单次触发最大命中目标数。
     * <p>
     * 每个目标会创建一个独立的周期任务并持续广播特效 / 音效包，
     * 故必须封顶。取 {@value} 的理由见类注释「v3.1 新增：命中目标数硬上限」小节：
     * 常规战斗远达不到该值（不影响正常体验），密集场景下则守住并发任务数、
     * 网络包峰值与客户端 {@code AoeEffectManager.MAX_ACTIVE}(64) 的特效名额。
     * </p>
     * <p>
     * <b>请勿放宽。</b>若确需更大范围的压制感，应调整 {@code level * 15} 的
     * 单目标落雷次数上限，而不是放开目标数——前者只增加时长，后者会线性放大
     * 并发任务与瞬时包量。
     * </p>
     */
    private static final int MAX_TARGETS = 100;

    /** AOE 搜索水平半径（格）。死亡一次性触发 + 1800 tick 冷却，该开销可接受 */
    private static final int SEARCH_RADIUS = 60;

    /** AOE 搜索垂直半径（格） */
    private static final int SEARCH_HEIGHT = 15;

    public EnchantmentAncientDragonLightning() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 死亡触发：对范围内敌人持续降下红色龙雷。
     *
     * @param ctx   附魔上下文
     * @param level 附魔等级
     */
    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("ancient_dragon_lightning", hurter.getUUID())) {
            return;
        }

        EnchantmentDataManager.setCooldown("ancient_dragon_lightning", hurter.getUUID(), 1800);

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                hurter,
                SEARCH_RADIUS,
                SEARCH_HEIGHT,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        if (nearbyEntities.isEmpty()) {
            return;
        }

        // ⭐ v3.1：命中目标数硬上限。
        // 必须在 randomList 之前截断——分配基数要用截断后的数量，
        // 否则 level*100 的落雷总量会被摊给不存在的目标（详见类注释）。
        // 复制成新列表而非用 subList 视图，避免持有原大列表的引用。
        final List<LivingEntity> entities;
        if (nearbyEntities.size() > MAX_TARGETS) {
            entities = new ArrayList<>(nearbyEntities.subList(0, MAX_TARGETS));
        } else {
            entities = nearbyEntities;
        }

        List<Integer> list = RandomUtil.randomList(level * 100, entities.size());

        for (int i = 0; i < entities.size(); i++) {
            LivingEntity entityLivingBase = entities.get(i);
            int timeLightning = Math.min(list.get(i), level * 15);

            // 分配到 0 次落雷的目标直接跳过，不创建空转任务
            if (timeLightning <= 0) {
                continue;
            }

            new SynchronizationTask(40, 5) {
                private int time = 0;

                @Override
                public void run() {
                    if (++time > timeLightning) {
                        this.cancel();
                        return;
                    }

                    // ⭐ v3.1：存活检查提前到发包之前。
                    // 原实现先发一轮红闪 + 两个音效再检查，导致目标死亡 / 卸载后
                    // 仍会往尸体或空气里多劈一道雷、多发三个包（详见类注释）。
                    if (!entityLivingBase.isAlive()) {
                        this.cancel();
                        return;
                    }

                    Level world = entityLivingBase.level();
                    if (world instanceof ServerLevel serverLevel) {
                        double lx = entityLivingBase.getX();
                        double ly = entityLivingBase.getY();
                        double lz = entityLivingBase.getZ();

                        // ⭐ 原版蓝白闪电替换为红色自绘闪电（古龙龙雷意象）。
                        // 客户端会对 2.5 格内的重复落雷做同位置合并，
                        // 因此高频重复调用不会叠成一团「鬼畜」，而是一道持续劈着的雷。
                        CarianStyleEffects.redLightning(serverLevel, lx, ly, lz);

                        // 原版闪电去掉后需手动补雷声：雷鸣 + 落地，
                        // 音高带轻微随机，避免高频重复时听感机械
                        serverLevel.playSound(null, lx, ly, lz,
                                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                                0.8f, 0.9f + serverLevel.random.nextFloat() * 0.2f);
                        serverLevel.playSound(null, lx, ly, lz,
                                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                                0.5f, 1.0f + serverLevel.random.nextFloat() * 0.2f);
                    }

                    entityLivingBase.invulnerableTime = 10;

                    // 互斥判断，先判雷暴再判下雨。
                    // MC 中 isThundering() 为 true 时 isRaining() 也为 true，
                    // 累乘会导致雷暴变成 8x；互斥后雷暴 4x、下雨 2x、晴天 1x
                    int magnification = 1;
                    if (entityLivingBase.level().isThundering()) {
                        magnification = 4;
                    } else if (entityLivingBase.level().isRaining()) {
                        magnification = 2;
                    }

                    entityLivingBase.hurt(
                            entityLivingBase.damageSources().lightningBolt(),
                            entityLivingBase.getHealth() * 0.05f
                                    + entityLivingBase.getMaxHealth() * 0.005f * magnification
                    );

                    if (entityLivingBase.onGround()) {
                        double x = RandomUtils.nextBoolean() ?
                                hurter.getX() - entityLivingBase.getX() :
                                entityLivingBase.getX() - hurter.getX();
                        double z = RandomUtils.nextBoolean() ?
                                hurter.getZ() - entityLivingBase.getZ() :
                                entityLivingBase.getZ() - hurter.getZ();
                        entityLivingBase.knockback(0.2f, x, z);
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
