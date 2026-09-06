package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * 巨剑方阵附魔
 * <p>
 * 死亡时触发：
 * - 在空中生成3把巨大的辉石剑
 * - 延迟后向攻击者发射
 * - 伤害基于攻击者损失的生命值
 * </p>
 *
 * <h3>v3.0：把生成距离从 ±10 格收到贴身</h3>
 * <p>
 * <b>原实现的问题：</b>三把剑的偏移分别是 {@code (-10,+10)}、{@code (-10,-10)}、{@code (+10,0)}，
 * 也就是散布在死者周围<b>十格开外</b>、跨度达 20 格。加上 {@code size=7.5} 的体积，
 * 玩家死的那一刻只会看到三把巨剑从视野边缘外冒出来，完全读不出「这是我的护甲在反击」。
 * 原作巨剑方阵是在施法者<b>身后上方</b>浮起数把巨剑再压下去。
 * </p>
 * <p>
 * 现改为半径 {@link #RING_RADIUS} 的环形布置、高度 {@link #RING_HEIGHT}——
 * 巨剑本身就有 7.5 的体积，4 格半径已足够让三把剑彼此不穿模，同时全部落在死者视野内。
 * </p>
 * <p>
 * <b>刻意不挂悬浮锚点：</b>本附魔在持有者<b>死亡瞬间</b>触发，尸体随即移除，
 * 没有可跟随的对象。{@code EntityGlintblades} 在释放者失效时会自动停止跟随、原地悬停，
 * 因此这里直接不设锚点即可，行为一致且少一次同步写入。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "greatblade_phalanx",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentGreatbladePhalanx extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.greatblade_phalanx.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "greatblade_phalanx";

    /**
     * 巨剑数量（发射方位自动均分整圆）
     * <p>默认 3，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle BLADE_COUNT =
            EnchantmentValues.define(VALUE_ID, "blade_count",
                    3, 1, 32);

    /**
     * 环形展开半径（格）
     * <p>默认 4.0，允许范围 0.5 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle RING_RADIUS =
            EnchantmentValues.define(VALUE_ID, "ring_radius",
                    4.0D, 0.5D, 32.0D);

    /**
     * 环形离地高度（格）
     * <p>默认 4.5，允许范围 0.0 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle RING_HEIGHT =
            EnchantmentValues.define(VALUE_ID, "ring_height",
                    4.5D, 0.0D, 32.0D);

    /**
     * 触发冷却时间（tick）
     * <p>默认 6000，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "cooldown",
                    6000, 20, 144000);

    /**
     * 第一把巨剑的发射延迟（tick）
     * <p>默认 75，允许范围 0 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle BASE_DELAY =
            EnchantmentValues.define(VALUE_ID, "base_delay",
                    75, 0, 600);

    /**
     * 相邻两把巨剑的发射间隔（tick）
     * <p>默认 25，允许范围 0 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle DELAY_STEP =
            EnchantmentValues.define(VALUE_ID, "delay_step",
                    25, 0, 300);

    /**
     * 每级按已损失生命计算的巨剑伤害比例
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);

    /**
     * 巨剑的追踪强度
     * <p>默认 0.08，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRACKING_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "tracking_strength",
                    0.08D, 0.0D, 1.0D);

    /**
     * 巨剑的最大存活时间（tick）
     * <p>默认 120，允许范围 20 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle MAX_LIFETIME =
            EnchantmentValues.define(VALUE_ID, "max_lifetime",
                    120, 20, 1200);


    /**
     * 巨剑环半径（格）。
     * <p>原实现是 ±10（跨度 20 格），巨剑会飞出视野。巨剑 {@code size=7.5}，
     * 4 格半径下三把剑呈 120° 分布、彼此不穿模，且全部在死者视野内。</p>
     */

    public EnchantmentGreatbladePhalanx() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        if (!(ctx.getDamageSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity hurter = ctx.getHolder();
        LivingEntity attacker = (LivingEntity) ctx.getDamageSource().getEntity();

        // 检查冷却
        if (EnchantmentDataManager.isOnCooldown("greatblade_phalanx", hurter.getUUID())) {
            return;
        }

        // 设置冷却（6000tick = 5分钟）
        EnchantmentDataManager.setCooldown("greatblade_phalanx", hurter.getUUID(), COOLDOWN.getInt());

        // 生成巨剑：以死者为中心的水平环，均布
        for (int i = 0; i < BLADE_COUNT.getInt(); i++) {
            double angle = Math.PI * 2.0 * i / BLADE_COUNT.getInt();
            double posX = hurter.getX() + Math.cos(angle) * RING_RADIUS.get();
            double posY = hurter.getY() + RING_HEIGHT.get();
            double posZ = hurter.getZ() + Math.sin(angle) * RING_RADIUS.get();

            // 延迟时间递增（形成连击效果）
            int delayTicks = BASE_DELAY.getInt() + i * DELAY_STEP.getInt();

            // 显示用的剑（悬浮效果）。不挂锚点：持有者已死，无跟随对象
            EntityGlintblades showBlade = new EntityGlintblades(hurter, attacker)
                    .setDeadTick(delayTicks)
                    .setSize(7.5f);
            showBlade.setPos(posX, posY, posZ);
            hurter.level().addFreshEntity(showBlade);

            // 保存位置到final变量供延迟任务使用
            int finalLevel = level;
            double finalPosX = posX;
            double finalPosY = posY;
            double finalPosZ = posZ;

            // 延迟发射攻击剑
            new SynchronizationTask(delayTicks) {
                @Override
                public void run() {
                    // v4.1：攻击者死了也照样发射，朝其死亡地点砸下去。
                    // 巨剑阵是死亡反击，最该出现的场景恰恰是「同归于尽」——
                    // 原实现在这里 return，等于对方补刀后反击直接作废。
                    net.minecraft.world.phys.Vec3 aimPoint = new net.minecraft.world.phys.Vec3(
                            attacker.getX(),
                            attacker.getY() + attacker.getEyeHeight() * 0.8,
                            attacker.getZ());

                    // 创建攻击剑
                    EntityGlintblades attackBlade = new EntityGlintblades(hurter, attacker)
                            .setSize(7.5f)
                            .setAimPoint(aimPoint)
                            .setDamage((attacker.getMaxHealth() - attacker.getHealth()) * finalLevel * (float) DAMAGE_PER_LEVEL.get())
                            .setDamageSource(hurter.damageSources().indirectMagic(null, hurter))
                            .setTrackingStrength((float) TRACKING_STRENGTH.get())  // 巨剑追踪较慢（更有重量感）
                            .setMaxLifetime(MAX_LIFETIME.getInt());         // 6秒存活时间

                    attackBlade.setPos(finalPosX, finalPosY, finalPosZ);
                    attackBlade.shoot(1.0f);  // 降低初始速度，依靠追踪
                    hurter.level().addFreshEntity(attackBlade);
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
