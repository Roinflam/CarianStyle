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

    /** 巨剑数量 */
    private static final int BLADE_COUNT = 3;

    /**
     * 巨剑环半径（格）。
     * <p>原实现是 ±10（跨度 20 格），巨剑会飞出视野。巨剑 {@code size=7.5}，
     * 4 格半径下三把剑呈 120° 分布、彼此不穿模，且全部在死者视野内。</p>
     */
    private static final double RING_RADIUS = 4.0;

    /** 巨剑离死者脚底的悬浮高度（格）：压在头顶上方，落下时才有压迫感 */
    private static final double RING_HEIGHT = 4.5;

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
        EnchantmentDataManager.setCooldown("greatblade_phalanx", hurter.getUUID(), 6000);

        // 生成巨剑：以死者为中心的水平环，均布
        for (int i = 0; i < BLADE_COUNT; i++) {
            double angle = Math.PI * 2.0 * i / BLADE_COUNT;
            double posX = hurter.getX() + Math.cos(angle) * RING_RADIUS;
            double posY = hurter.getY() + RING_HEIGHT;
            double posZ = hurter.getZ() + Math.sin(angle) * RING_RADIUS;

            // 延迟时间递增（形成连击效果）
            int delayTicks = 75 + i * 25;

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
                            .setDamage((attacker.getMaxHealth() - attacker.getHealth()) * finalLevel * 0.1f)
                            .setDamageSource(hurter.damageSources().indirectMagic(null, hurter))
                            .setTrackingStrength(0.08f)  // 巨剑追踪较慢（更有重量感）
                            .setMaxLifetime(120);         // 6秒存活时间

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
