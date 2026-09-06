package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 卡利亚报复附魔
 * <p>v2.2：LivingAttack格挡反击入口接入怪物附魔触发开关</p>
 *
 * <h3>v3.0：剑阵跟随持盾者</h3>
 * <p>
 * <b>生成位置本来就是对的</b>——三把剑以 {@code holder} 为中心呈 120° 三角布置，
 * 符合原作「格挡瞬间在身前弹出辉剑」的表现，本次没有改动几何。
 * </p>
 * <p>
 * 改的是两点：
 * </p>
 * <ol>
 *     <li>挂上 {@link EntityGlintblades#setHoverAnchor(Vec3)}，悬浮的 40~50 tick 里
 *         剑跟着持盾者走。举盾格挡本就是个会边挡边挪的动作，剑钉在原地会立刻穿帮；</li>
 *     <li>攻击剑的发射点改用持盾者的<b>实时</b>位置推算。原实现捕获的是生成时的
 *         {@code posX/posY/posZ}，持盾者移动后剑会从身后凭空飞出。</li>
 * </ol>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "carian_retaliation",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND},
        conflictsWith = {
                EnchantmentScholarShield.class,
                EnchantmentImmutableShield.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianRetaliation extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.carian_retaliation.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "carian_retaliation";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 反击辉剑的数量（发射方位自动均分整圆）
     * <p>默认 3，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle BLADE_COUNT =
            EnchantmentValues.define(VALUE_ID, "blade_count",
                    3, 1, 32);

    /**
     * 第一把剑的发射延迟（tick）
     * <p>默认 40，允许范围 0 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle BASE_DELAY =
            EnchantmentValues.define(VALUE_ID, "base_delay",
                    40, 0, 600);

    /**
     * 相邻两把剑的发射间隔（tick）
     * <p>默认 5，允许范围 0 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DELAY_STEP =
            EnchantmentValues.define(VALUE_ID, "delay_step",
                    5, 0, 120);

    /**
     * 每级的辉剑伤害倍率
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.2D, 0.0D, 5.0D);

    /**
     * 辉剑的追踪强度
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRACKING_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "tracking_strength",
                    0.15D, 0.0D, 1.0D);


    /** 剑环半径（格），与原实现一致 */
    private static final double RING_RADIUS = 1.5;

    /** 剑环离持盾者脚底的高度（格），与原实现一致 */
    private static final double RING_HEIGHT = 0.5;

    public EnchantmentCarianRetaliation() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"),
                new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.getEntity() == null) {
            return;
        }

        boolean isRanged = !damageSource.getEntity().equals(damageSource.getDirectEntity());
        boolean isMagic = DamageSourceUtil.isMagicDamage(damageSource);
        if (!isRanged && !isMagic) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，格挡反击）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        Entity attacker = damageSource.getEntity();

        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack heldItem = holder.getItemInHand(holder.getUsedItemHand());

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment carianRetaliation = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianRetaliation.class);
        if (carianRetaliation == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(carianRetaliation, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP.getInt());
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int i = 0; i < BLADE_COUNT.getInt(); i++) {
            final int delay = BASE_DELAY.getInt() + i * DELAY_STEP.getInt();

            // 方位按剑数均分整圆，改 blade_count 时不必再手动改这里的角度
                double angle = (i * 360.0 / BLADE_COUNT.getInt()) * Math.PI / 180.0;
            // v3.0：相对持盾者的局部偏移（原实现在这里直接算成了绝对坐标）
            final double offsetX = Math.cos(angle) * RING_RADIUS;
            final double offsetY = RING_HEIGHT;
            final double offsetZ = Math.sin(angle) * RING_RADIUS;

            EntityGlintblades showBlade = new EntityGlintblades(holder, attacker)
                    .setDeadTick(delay)
                    .setSize(1.0f)
                    .setHoverAnchor(new Vec3(offsetX, offsetY, offsetZ));
            // 生成位置由 followAnchor 在首个 tick 立刻校正，这里给个初值即可
            showBlade.setPos(holder.getX() + offsetX, holder.getY() + offsetY, holder.getZ() + offsetZ);
            holder.level().addFreshEntity(showBlade);

            new SynchronizationTask(delay) {
                @Override
                public void run() {
                    // 持盾者没了就取消：发射点相对他算出，人不在无处可发
                    if (!holder.isAlive() || holder.isRemoved()) {
                        return;
                    }

                    // v4.1：攻击者死了也照样发射，朝其死亡地点飞完全程
                    Vec3 aimPoint = new Vec3(
                            attacker.getX(),
                            attacker.getY() + attacker.getEyeHeight() * 0.8,
                            attacker.getZ());

                    // v4.1：按持盾者的实时位置与朝向把局部偏移旋转到世界坐标
                    float yawRad = (float) Math.toRadians(holder.getYRot());
                    double sin = Math.sin(yawRad);
                    double cos = Math.cos(yawRad);
                    double launchX = holder.getX() + (-cos) * offsetX + (-sin) * offsetZ;
                    double launchY = holder.getY() + offsetY;
                    double launchZ = holder.getZ() + (-sin) * offsetX + cos * offsetZ;

                    EntityGlintblades attackBlade = new EntityGlintblades(holder, attacker)
                            .setSize(1.0f)
                            .setAimPoint(aimPoint)
                            .setDamage(baseDamage * effectiveLevel * (float) DAMAGE_PER_LEVEL.get())
                            .setDamageSource(holder.damageSources().thrown(null, holder))
                            .setTrackingStrength((float) TRACKING_STRENGTH.get())
                            .setMaxLifetime(100);

                    DamageSourceUtil.setMagicDamage(attackBlade.getDamageSource());

                    attackBlade.setPos(launchX, launchY, launchZ);
                    attackBlade.shoot(1.2f);
                    holder.level().addFreshEntity(attackBlade);
                }
            }.start();
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class)) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentImmutableShield.class));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
