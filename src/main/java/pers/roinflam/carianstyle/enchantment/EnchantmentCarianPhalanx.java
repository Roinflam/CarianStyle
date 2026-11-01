package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 卡利亚圆阵附魔
 * <p>
 * 弓箭伤害时按 [等级]×2% 概率在身后召出 9 把辉剑，排成头顶后方的扇形，跟着射手移动和转身，
 * 45~85 tick 后依次追踪射向目标；每把伤害 = 触发那一箭的伤害 × [等级] × 5%。
 * </p>
 * <p>
 * 以前剑阵是目标身上的 3×3 方阵、不追踪，箭直接把目标射死时整套剑阵一把都飞不出去；
 * 现在目标死了剑照样飞向它倒下的地方。射手在发射前死了就不发。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "carian_phalanx",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentPyroxeneIce.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 辉剑数量 */
    private static final int BLADE_COUNT = 9;
    /** 扇形半径（格）：剑离射手的水平距离 */
    private static final double FAN_RADIUS = 2.1D;
    /** 第一把剑的发射延迟（tick） */
    private static final int BASE_DELAY = 45;
    /** 相邻两把剑的发射间隔（tick） */
    private static final int DELAY_STEP = 5;
    /** 每级触发概率（百分比） */
    private static final double TRIGGER_CHANCE_PER_LEVEL = 2.0D;
    /** 每级每把剑的伤害倍率 */
    private static final float DAMAGE_PER_LEVEL = 0.05F;
    /** 扇形张角的一半（度），以正后方为中心左右展开；第一人称只有最外侧一对落在余光边缘 */
    private static final double FAN_HALF_SPREAD = 125.0D;
    /** 扇形中心（正后方那把）离射手脚底的高度（格） */
    private static final double FAN_HEIGHT_CENTER = 2.0D;
    /** 扇形两端相对中心的下沉量（格），中间高两边低 */
    private static final double FAN_HEIGHT_DROP = 0.75D;
    /** 辉剑追踪强度 */
    private static final float TRACKING_STRENGTH = 0.2F;
    /** 辉剑寿命（tick） */
    private static final int BLADE_LIFETIME = 80;
    /** 辉剑速度 */
    private static final float BLADE_SPEED = 1.5F;

    public EnchantmentCarianPhalanx() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (!(damageSource.getImmediateSource() instanceof EntityArrow)) {
            return;
        }
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment carianPhalanx = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianPhalanx.class);
        if (carianPhalanx == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(carianPhalanx, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP);
        }

        if (level <= 0) {
            return;
        }

        if (!RandomUtil.percentageChance(level * TRIGGER_CHANCE_PER_LEVEL)) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int i = 0; i < BLADE_COUNT; i++) {
            // t: -1（最左）→ 0（正后方）→ +1（最右）
            double t = BLADE_COUNT == 1 ? 0.0D : (i * 2.0D / (BLADE_COUNT - 1) - 1.0D);
            double spreadRad = Math.toRadians(FAN_HALF_SPREAD * t);

            // 局部轴：x=右，y=上，z=前。剑在身后，前向分量恒为负
            final double offsetX = Math.sin(spreadRad) * FAN_RADIUS;
            final double offsetY = FAN_HEIGHT_CENTER - FAN_HEIGHT_DROP * Math.abs(t);
            final double offsetZ = -Math.cos(spreadRad) * FAN_RADIUS;
            final int delay = BASE_DELAY + i * DELAY_STEP;

            // 悬浮展示用的剑：挂锚点跟随射手，延迟到期后自行消失
            Vec3d spawn = localToWorld(attacker, offsetX, offsetY, offsetZ);
            EntityGlintblades showBlade = new EntityGlintblades(attacker, victim)
                    .setDeadTick(delay)
                    .setSize(1.0f)
                    .setHoverAnchor(new Vec3d(offsetX, offsetY, offsetZ));
            showBlade.setPosition(spawn.x, spawn.y, spawn.z);
            attacker.world.spawnEntity(showBlade);

            new SynchronizationTask(delay) {
                @Override
                public void run() {
                    // 射手没了就不发：发射点是相对他算的
                    if (!attacker.isEntityAlive()) {
                        return;
                    }

                    // 目标死了也照样发，朝它倒下的地方飞
                    Vec3d aimPoint = new Vec3d(victim.posX, victim.posY + victim.getEyeHeight() * 0.8D, victim.posZ);

                    // 从射手当前的位置和朝向推算发射点：悬浮剑这段时间一直跟着他
                    Vec3d launch = localToWorld(attacker, offsetX, offsetY, offsetZ);

                    EntityGlintblades attackBlade = new EntityGlintblades(attacker, victim)
                            .setSize(1.0f)
                            .setAimPoint(aimPoint)
                            .setDamage(baseDamage * effectiveLevel * DAMAGE_PER_LEVEL)
                            .setTrackingStrength(TRACKING_STRENGTH)
                            .setMaxLifetime(BLADE_LIFETIME);
                    attackBlade.setDamageSource(DamageSource.causeThrownDamage(attackBlade, attacker).setMagicDamage());
                    attackBlade.setPosition(launch.x, launch.y, launch.z);
                    attackBlade.shoot(BLADE_SPEED);
                    attacker.world.spawnEntity(attackBlade);
                }
            }.start();
        }
    }

    /**
     * 射手局部偏移（右 / 上 / 前）换算成世界坐标，和辉剑悬浮锚点同一个公式
     */
    @Nonnull
    private static Vec3d localToWorld(@Nonnull EntityLivingBase owner, double right, double up, double forward) {
        double yawRad = Math.toRadians(owner.rotationYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        return new Vec3d(
                owner.posX + (-cos) * right + (-sin) * forward,
                owner.posY + up,
                owner.posZ + (-sin) * right + cos * forward
        );
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}
