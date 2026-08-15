package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 卡利亚方阵附魔
 * <p>v2.2：LivingDamage攻击者视角入口接入怪物附魔触发开关</p>
 *
 * <h3>v3.0：剑阵改为围绕<b>施法者</b>成环并跟随（修正生成位置）</h3>
 * <p>
 * <b>原实现的问题：</b>辉剑是围着 {@code victim}（被打的人）以 3×3 网格生成的：
 * </p>
 * <pre>
 * showBlade.setPos(victim.getX() + x * 1.5, victim.getY() + 2, victim.getZ() + z * 1.5);
 * </pre>
 * <p>
 * 效果上等于「敌人头顶凭空冒出一圈剑再往下扎」——这既不是原作的表现，
 * 也让玩家完全感受不到「这阵是我召出来的」。原作卡利亚圆阵是
 * <b>辉剑在施法者身周成环 → 齐射向目标</b>。
 * </p>
 * <p>
 * <b>现改为：</b>以 {@code attacker}（射箭的人，即施法者）为中心、
 *  {@link #BLADE_COUNT} 把剑，
 * 并通过 {@link EntityGlintblades#setHoverAnchor(Vec3)} 挂上悬浮锚点——
 * 施法者走位时整个剑阵跟着平移，直到各自的延迟结束才依次射出。
 * </p>
 * <p>
 * <b>数量由 8 改为 9：</b>原来的 3×3 去掉中心恰好是 8 把，但语言文件里写的是
 * 「生成9道魔法辉剑」，两边对不上。改成环形之后数量不再受网格约束，
 * 索性取 9 把与文案一致，省掉一次文案改动。
 * </p>
 * <p>
 * <b>依次发射而非齐射：</b>延迟按下标递增（{@link #BASE_DELAY} + i × {@link #DELAY_STEP}），
 * 剑会绕着环一把接一把地飞出去。齐射在同一帧生成 9 个投射物，
 * 既看不清也容易撞上同一 tick 的多次 {@code hurt} 判定。
 * </p>
 *
 * @version 3.0
 */
@AutoRegisterEnchantment(id = "carian_phalanx", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.BOW, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentPyroxeneIce.class})
@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends EnchantmentBase {

    /** 环上的辉剑数量（与语言文件的「9道」一致） */
    private static final int BLADE_COUNT = 9;

    /**
     * 扇形半径（格）：剑距施法者的水平距离。
     * <p>贴身但不穿模；第三人称视角下整扇都在身后可见，第一人称则完全不挡视线。</p>
     */
    private static final double FAN_RADIUS = 2.1;

    /**
     * 扇形张角的一半（度）：整扇覆盖 2×该值，以正后方为中心左右展开。
     *
     * <h4>这个值直接决定「第一人称能不能瞥见剑」</h4>
     * <p>
     * 张角 θ 的剑，其方位偏离<b>视线方向</b>的角度是 {@code 180° - θ}。而 Minecraft 默认
     * FOV 70 指的是<b>垂直</b>视角，16:9 下换算出的水平半视角约 51°：
     * </p>
     * <pre>
     * θ = 58°   → 偏离视线 122°   完全在背后，第一人称一点都看不到
     * θ = 90°   → 偏离视线  90°   正侧方，仍在视野外
     * θ = 125°  → 偏离视线  55°   ★ 恰好压在余光边缘
     * θ = 135°  → 偏离视线  45°   明确可见，开始占视野
     * </pre>
     * <p>
     * 取 {@value} 后，9 把剑里<b>只有最外侧那一对</b>落在视野边缘、能瞥见一点冷蓝的闪，
     * 其余七把仍在身后与两侧。FOV 开得越大看到的越多（FOV 90 时水平半视角约 61°，
     * 外侧两对都会进来）。
     * </p>
     * <p>
     * <b>再往上调要谨慎：</b>超过 140° 中间那几把就会绕到身前，
     * 挡视线的观感会立刻盖过「余光有东西」的惊艳感。
     * </p>
     */
    private static final double FAN_HALF_SPREAD = 125.0;

    /** 扇形中心（正后方那把）离施法者脚底的高度（格） */
    private static final double FAN_HEIGHT_CENTER = 2.0;

    /**
     * 扇形两端相对中心的下沉量（格）：中间高两边低，形成向上拱起的弧。
     * <p>玩家眼高约 1.62 格，外侧剑落在 {@code 2.0 - 0.75 = 1.25} 格——略低于视线，
     * 正好是余光最自然的位置；抬到眼高反而会变成「有东西挡在脸边上」。</p>
     */
    private static final double FAN_HEIGHT_DROP = 0.75;

    /** 第一把剑的发射延迟（tick） */
    private static final int BASE_DELAY = 45;

    /** 相邻两把剑的发射间隔（tick）：整环射完约 (BLADE_COUNT-1) × 该值 */
    private static final int DELAY_STEP = 5;

    public EnchantmentCarianPhalanx() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getDirectEntity() instanceof AbstractArrow)) return;
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角，箭矢命中追加魔法剑阵）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        LivingEntity victim = evt.getEntity();
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment carianPhalanx = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianPhalanx.class);
        if (carianPhalanx == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(carianPhalanx, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0 || !RandomUtil.percentageChance(level * 2)) return;

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int i = 0; i < BLADE_COUNT; i++) {
            // t: -1（最左）→ 0（正后方）→ +1（最右）
            double t = (BLADE_COUNT == 1) ? 0.0 : (i * 2.0 / (BLADE_COUNT - 1) - 1.0);
            double spreadRad = Math.toRadians(FAN_HALF_SPREAD * t);

            // 局部轴：x=右，y=上，z=前。剑在身后，故前向分量恒为负。
            final double offsetX = Math.sin(spreadRad) * FAN_RADIUS;
            final double offsetY = FAN_HEIGHT_CENTER - FAN_HEIGHT_DROP * Math.abs(t);
            final double offsetZ = -Math.cos(spreadRad) * FAN_RADIUS;
            final int delay = BASE_DELAY + i * DELAY_STEP;

            // 悬浮展示用的剑：挂锚点跟随施法者，延迟到期后自行消失
            EntityGlintblades showBlade = new EntityGlintblades(attacker, victim)
                    .setDeadTick(delay)
                    .setSize(1.0f)
                    .setHoverAnchor(new Vec3(offsetX, offsetY, offsetZ));
            showBlade.setPos(attacker.getX() + offsetX, attacker.getY() + offsetY, attacker.getZ() + offsetZ);
            attacker.level().addFreshEntity(showBlade);

            new SynchronizationTask(delay) {
                @Override
                public void run() {
                    // 施法者没了就取消：发射点是相对他算出来的，人不在就无处可发
                    if (!attacker.isAlive() || attacker.isRemoved()) return;

                    // v4.1：目标死了【也照样发射】。原实现在这里直接 return，
                    // 于是补刀瞬间整排还没射出去的剑会凭空消失。现在改为朝其死亡地点飞完，
                    // 沿途撞到任何生物都正常造成伤害。
                    Vec3 aimPoint = new Vec3(
                            victim.getX(),
                            victim.getY() + victim.getEyeHeight() * 0.8,
                            victim.getZ());

                    // 从施法者的【当前】位置与朝向推算发射点。
                    // 悬浮剑这 45~85 tick 一直跟着施法者走，沿用生成时的坐标会让剑从空地飞出。
                    float yawRad = (float) Math.toRadians(attacker.getYRot());
                    double sin = Math.sin(yawRad);
                    double cos = Math.cos(yawRad);
                    double launchX = attacker.getX() + (-cos) * offsetX + (-sin) * offsetZ;
                    double launchY = attacker.getY() + offsetY;
                    double launchZ = attacker.getZ() + (-sin) * offsetX + cos * offsetZ;

                    EntityGlintblades attackBlade = new EntityGlintblades(attacker, victim)
                            .setSize(1.0f)
                            .setAimPoint(aimPoint)
                            .setDamage(baseDamage * effectiveLevel * 0.05f)
                            .setDamageSource(attacker.damageSources().thrown(null, attacker))
                            .setTrackingStrength(0.2f)
                            .setMaxLifetime(80);
                    DamageSourceUtil.setMagicDamage(attackBlade.getDamageSource());
                    attackBlade.setPos(launchX, launchY, launchZ);
                    attackBlade.shoot(1.5f);
                    attacker.level().addFreshEntity(attackBlade);
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((20 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
