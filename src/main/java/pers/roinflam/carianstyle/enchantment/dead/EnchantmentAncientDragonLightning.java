package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.commons.lang3.RandomUtils;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 古龙雷击附魔
 *
 * 死亡时（90 秒冷却）对周围 60×15 格内至多 100 个生物降下落雷：总量 等级×100 道随机分给各个目标，
 * 每个目标至多 等级×15 道，每 5 tick 一道；每道伤害 = 当前生命 5% + 最大生命 0.5%×倍率（雨天 ×2，雷暴 ×4）。
 * <p>
 * 以前先判下雨再判雷暴，雷暴时下雨也成立，雷暴分支永远走不到，只有 ×2；目标数不封顶，
 * 怪多时每个目标各开一个任务。目标已死时也会先劈一道视觉雷再停，现在先判存活。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "ancient_dragon_lightning",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentAncientDragonLightning extends EnchantmentBase {

    /** 冷却（tick） */
    private static final int COOLDOWN = 1800;
    /** 单次触发最多命中的目标数 */
    private static final int MAX_TARGETS = 100;
    /** 水平搜索范围（格） */
    private static final int SEARCH_RADIUS = 60;
    /** 垂直搜索范围（格） */
    private static final int SEARCH_HEIGHT = 15;
    /** 每级的落雷总量 */
    private static final int BOLTS_PER_LEVEL = 100;
    /** 每个目标每级至多分到的落雷数 */
    private static final int BOLTS_PER_TARGET_PER_LEVEL = 15;
    /** 按当前生命计的伤害比例 */
    private static final float DAMAGE_HEALTH_RATIO = 0.05F;
    /** 按最大生命计的伤害比例（吃天气倍率） */
    private static final float DAMAGE_MAX_HEALTH_RATIO = 0.005F;
    /** 雷暴倍率 */
    private static final int MAGNIFICATION_THUNDER = 4;
    /** 下雨倍率 */
    private static final int MAGNIFICATION_RAIN = 2;
    /** 击退强度 */
    private static final float KNOCKBACK_STRENGTH = 0.2F;

    public EnchantmentAncientDragonLightning() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("ancient_dragon_lightning", hurter.getUniqueID())) {
            return;
        }

        if (hurter.isDead) {
            return;
        }

        EnchantmentDataManager.setCooldown("ancient_dragon_lightning", hurter.getUniqueID(), COOLDOWN);

        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                hurter,
                SEARCH_RADIUS,
                SEARCH_HEIGHT,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        if (nearbyEntities.isEmpty()) {
            return;
        }

        // 先截断再分配，落雷总量按实际命中的目标数分
        final List<EntityLivingBase> entities = nearbyEntities.size() > MAX_TARGETS
                ? new ArrayList<>(nearbyEntities.subList(0, MAX_TARGETS))
                : nearbyEntities;

        List<Integer> list = RandomUtil.randomList(level * BOLTS_PER_LEVEL, entities.size());

        for (int i = 0; i < entities.size(); i++) {
            EntityLivingBase entityLivingBase = entities.get(i);
            int timeLightning = Math.min(list.get(i), level * BOLTS_PER_TARGET_PER_LEVEL);

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

                    if (!entityLivingBase.isEntityAlive()) {
                        this.cancel();
                        return;
                    }

                    World world = entityLivingBase.world;
                    world.addWeatherEffect(
                            new EntityLightningBolt(
                                    world,
                                    entityLivingBase.posX,
                                    entityLivingBase.posY,
                                    entityLivingBase.posZ,
                                    true
                            )
                    );

                    entityLivingBase.hurtResistantTime = entityLivingBase.maxHurtResistantTime / 2;

                    // 先判雷暴：雷暴时 isRaining 也为真
                    int magnification = 1;
                    if (world.isThundering()) {
                        magnification = MAGNIFICATION_THUNDER;
                    } else if (world.isRaining()) {
                        magnification = MAGNIFICATION_RAIN;
                    }

                    entityLivingBase.attackEntityFrom(
                            DamageSource.LIGHTNING_BOLT,
                            entityLivingBase.getHealth() * DAMAGE_HEALTH_RATIO
                                    + entityLivingBase.getMaxHealth() * DAMAGE_MAX_HEALTH_RATIO * magnification
                    );

                    if (entityLivingBase.onGround) {
                        double x = RandomUtils.nextBoolean() ?
                                hurter.posX - entityLivingBase.posX :
                                entityLivingBase.posX - hurter.posX;
                        double z = RandomUtils.nextBoolean() ?
                                hurter.posZ - entityLivingBase.posZ :
                                entityLivingBase.posZ - hurter.posZ;
                        entityLivingBase.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) entityLivingBase.rotationYaw);
                        entityLivingBase.knockBack(hurter, KNOCKBACK_STRENGTH, x, z);
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

}