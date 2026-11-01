package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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

import javax.annotation.Nonnull;

/**
 * 卡利亚式奉还附魔
 * <p>
 * 举盾挡下远程或魔法攻击时，在持盾者身周召出 3 把辉剑，环绕一圈、跟着持盾者移动和转身，
 * 40 / 45 / 50 tick 后依次追踪射向攻击者；每把伤害 = 被打那一下的伤害 × [等级] × 20%。
 * </p>
 * <p>
 * 以前剑钉在固定的世界坐标、不追踪，攻击者在发射前死了剑就消失；现在照样飞向它倒下的地方。
 * 持盾者在发射前死了就不发。只能附在盾牌上，以前任何有耐久的物品都能附（但只有举盾才触发）。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "carian_retaliation",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BREAKABLE,
        conflictsWith = {
                EnchantmentScholarShield.class,
                EnchantmentImmutableShield.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianRetaliation extends EnchantmentBase {

    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 反击辉剑数量（方位均分整圆） */
    private static final int BLADE_COUNT = 3;
    /** 第一把剑的发射延迟（tick） */
    private static final int BASE_DELAY = 40;
    /** 相邻两把剑的发射间隔（tick） */
    private static final int DELAY_STEP = 5;
    /** 每级每把剑的伤害倍率 */
    private static final float DAMAGE_PER_LEVEL = 0.2F;
    /** 辉剑追踪强度 */
    private static final float TRACKING_STRENGTH = 0.15F;
    /** 辉剑寿命（tick） */
    private static final int BLADE_LIFETIME = 100;
    /** 辉剑速度 */
    private static final float BLADE_SPEED = 1.2F;
    /** 剑环半径（格） */
    private static final double RING_RADIUS = 1.5D;
    /** 剑环离持盾者脚底的高度（格） */
    private static final double RING_HEIGHT = 0.5D;

    public EnchantmentCarianRetaliation() {
        super(EnumEnchantmentType.BREAKABLE, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND});
    }

    /**
     * 只能附在盾牌上
     */
    @Override
    public boolean canApply(@Nonnull ItemStack stack) {
        return isShield(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack) {
        return isShield(stack);
    }

    private static boolean isShield(@Nonnull ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemShield || stack.getItem().isShield(stack, null));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.getTrueSource() == null) {
            return;
        }

        boolean isRanged = !damageSource.getTrueSource().equals(damageSource.getImmediateSource());
        boolean isMagic = damageSource.isMagicDamage();
        if (!isRanged && !isMagic) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        if (shouldBlockMobTrigger(holder, false)) {
            return;
        }

        Entity attacker = damageSource.getTrueSource();

        if (!holder.isHandActive()) {
            return;
        }

        ItemStack heldItem = holder.getHeldItem(holder.getActiveHand());

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment carianRetaliation = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianRetaliation.class);
        if (carianRetaliation == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(carianRetaliation, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int i = 0; i < BLADE_COUNT; i++) {
            final int delay = BASE_DELAY + i * DELAY_STEP;

            // 方位按剑数均分整圆；偏移相对持盾者（右 / 上 / 前），跟着他转身
            double angle = Math.toRadians(i * 360.0D / BLADE_COUNT);
            final double offsetX = Math.cos(angle) * RING_RADIUS;
            final double offsetY = RING_HEIGHT;
            final double offsetZ = Math.sin(angle) * RING_RADIUS;

            Vec3d spawn = localToWorld(holder, offsetX, offsetY, offsetZ);
            EntityGlintblades showBlade = new EntityGlintblades(holder, attacker)
                    .setDeadTick(delay)
                    .setSize(1.0f)
                    .setHoverAnchor(new Vec3d(offsetX, offsetY, offsetZ));
            showBlade.setPosition(spawn.x, spawn.y, spawn.z);
            holder.world.spawnEntity(showBlade);

            new SynchronizationTask(delay) {
                @Override
                public void run() {
                    // 持盾者没了就不发：发射点是相对他算的
                    if (!holder.isEntityAlive()) {
                        return;
                    }

                    // 攻击者死了也照样发，朝它倒下的地方飞
                    Vec3d aimPoint = new Vec3d(attacker.posX, attacker.posY + attacker.getEyeHeight() * 0.8D, attacker.posZ);

                    Vec3d launch = localToWorld(holder, offsetX, offsetY, offsetZ);

                    EntityGlintblades attackBlade = new EntityGlintblades(holder, attacker)
                            .setSize(1.0f)
                            .setAimPoint(aimPoint)
                            .setDamage(baseDamage * effectiveLevel * DAMAGE_PER_LEVEL)
                            .setTrackingStrength(TRACKING_STRENGTH)
                            .setMaxLifetime(BLADE_LIFETIME);
                    attackBlade.setDamageSource(DamageSource.causeThrownDamage(attackBlade, holder).setMagicDamage());
                    attackBlade.setPosition(launch.x, launch.y, launch.z);
                    attackBlade.shoot(BLADE_SPEED);
                    holder.world.spawnEntity(attackBlade);
                }
            }.start();
        }
    }

    /**
     * 持盾者局部偏移（右 / 上 / 前）换算成世界坐标，和辉剑悬浮锚点同一个公式
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
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class)) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentImmutableShield.class));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}
