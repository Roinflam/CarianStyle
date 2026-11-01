package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 巨剑阵附魔
 * <p>
 * 被生物杀死时（300 秒冷却）在自己周围半径 4、高 4.5 格的环上召出 3 把巨剑，分别在
 * 75 / 100 / 125 tick 后射向攻击者并持续追踪，每把造成 (攻击者最大生命 - 当前生命) × 等级 × 10% 的
 * 魔法伤害（无视护甲），命中时爆炸。巨剑最长飞 6 秒。多件盔甲取最高等级。
 * </p>
 * <p>
 * 以前巨剑固定出现在 (-10, +5, +10) / (-10, +5, -10) / (+10, +5, 0) 三处，射出时按一次预判直线飞，
 * 不追踪、没有寿命上限；攻击者在发射前死了整把剑直接消失；伤害只带魔法标志，护甲照样减免；
 * 等级取扫到的第一件。现在剑均布在环上、出手后每 tick 向攻击者偏转 8%（攻击者死了就飞向死亡地点），
 * 120 tick 后消失；伤害改成间接魔法伤害（无视护甲），等级取全身最高。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "greatblade_phalanx",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentGreatbladePhalanx extends EnchantmentBase {

    /** 巨剑数量 */
    private static final int BLADE_COUNT = 3;
    /** 环半径（格） */
    private static final double RING_RADIUS = 4.0D;
    /** 环高度（格） */
    private static final double RING_HEIGHT = 4.5D;
    /** 冷却（tick） */
    private static final int COOLDOWN = 6000;
    /** 第一把剑的蓄力时间（tick） */
    private static final int BASE_DELAY = 75;
    /** 每把剑多等的时间（tick） */
    private static final int DELAY_STEP = 25;
    /** 每级伤害（占攻击者已损失生命） */
    private static final float DAMAGE_PER_LEVEL = 0.1F;
    /** 每 tick 向目标偏转的比例 */
    private static final float TRACKING_STRENGTH = 0.08F;
    /** 射出后的最长存活时间（tick） */
    private static final int MAX_LIFETIME = 120;
    /** 射出速度（格/tick） */
    private static final float SHOOT_VELOCITY = 1.0F;
    /** 巨剑大小（同时是爆炸半径） */
    private static final float BLADE_SIZE = 7.5F;

    public EnchantmentGreatbladePhalanx() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@Nonnull EnchantmentContext ctx, int pieceLevel) {
        if (!(ctx.getDamageSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();
        EntityLivingBase attacker = (EntityLivingBase) ctx.getDamageSource().getTrueSource();

        if (EnchantmentDataManager.isOnCooldown("greatblade_phalanx", hurter.getUniqueID())) {
            return;
        }

        EnchantmentDataManager.setCooldown("greatblade_phalanx", hurter.getUniqueID(), COOLDOWN);

        // 分发器按件回调，第一件触发后就进冷却，等级要取全身最高
        int level = highestLevel(hurter);

        for (int i = 0; i < BLADE_COUNT; i++) {
            double angle = Math.PI * 2.0D * i / BLADE_COUNT;
            double posX = hurter.posX + Math.cos(angle) * RING_RADIUS;
            double posY = hurter.posY + RING_HEIGHT;
            double posZ = hurter.posZ + Math.sin(angle) * RING_RADIUS;
            int delayTicks = BASE_DELAY + i * DELAY_STEP;

            // 悬浮展示用的剑
            EntityGlintblades showBlade = new EntityGlintblades(hurter, attacker)
                    .setDeadTick(delayTicks)
                    .setSize(BLADE_SIZE);
            showBlade.setPosition(posX, posY, posZ);
            hurter.world.spawnEntity(showBlade);

            new SynchronizationTask(delayTicks) {
                @Override
                public void run() {
                    // 攻击者死了也照样发射，飞向它的死亡地点
                    EntityGlintblades attackBlade = new EntityGlintblades(hurter, attacker);
                    attackBlade.setSize(BLADE_SIZE);
                    attackBlade.setPosition(posX, posY, posZ);
                    attackBlade.setDamageSource(DamageSource.causeIndirectMagicDamage(attackBlade, hurter));
                    attackBlade.setDamage((attacker.getMaxHealth() - attacker.getHealth()) * level * DAMAGE_PER_LEVEL);
                    // 给一个瞄准点兜底：攻击者在发射前死了，剑照样飞向它倒下的地方，不会被直接移除
                    attackBlade.setAimPoint(new Vec3d(attacker.posX, attacker.posY + attacker.getEyeHeight() * 0.8D, attacker.posZ))
                            .setTrackingStrength(TRACKING_STRENGTH)
                            .setMaxLifetime(MAX_LIFETIME);
                    hurter.world.spawnEntity(attackBlade);
                    attackBlade.shoot(SHOOT_VELOCITY);
                }
            }.start();
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

}