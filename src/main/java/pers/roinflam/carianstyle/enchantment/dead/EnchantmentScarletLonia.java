package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 猩红艾奥尼亚附魔
 * <p>
 * 死亡时（90 秒冷却）抵消死亡、生命设为 1，推开周围 等级×4 格（至多 16 格）内的生物，
 * 自身进入 1.5 秒无敌（无法移动）；之后对 等级×2 格（至多 12 格）内的生物直接扣除当前生命的 等级×5%，
 * 施加 等级 级猩红腐败（持续 等级×10 秒）并再推开一次，最后自身死亡。两段各至多 24 个目标。
 * 多件盔甲取最高等级。
 * </p>
 * <p>
 * 以前第一段传的方向是「目标 - 自己」，原版 knockBack 会把向量取反，实际是把敌人拉向自己；
 * 现在传「自己 - 目标」，是推开。以前两段半径、目标数都不封顶，等级取扫到的第一件，
 * 演出期间下线能逃过最后的死亡；现在封顶、取全身最高等级，下线的玩家记一个待补刀标记，
 * 重新登录 1 秒后补上死亡。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "scarlet_lonia",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentScarletLonia extends EnchantmentBase {

    /** 冷却（tick） */
    private static final int COOLDOWN = 1800;
    /** 第一段击退每级半径（格） */
    private static final int KNOCKBACK_RADIUS_PER_LEVEL = 4;
    /** 第一段击退半径上限（格） */
    private static final int MAX_KNOCKBACK_RADIUS = 16;
    /** 第二段伤害每级半径（格） */
    private static final int DAMAGE_RADIUS_PER_LEVEL = 2;
    /** 第二段伤害半径上限（格） */
    private static final int MAX_DAMAGE_RADIUS = 12;
    /** 每段至多处理的目标数 */
    private static final int MAX_TARGETS = 24;
    /** 第一段每级击退强度系数 */
    private static final double KNOCKBACK_STRENGTH_PER_LEVEL = 0.7D;
    /** 第二段每级扣除的当前生命比例 */
    private static final float DAMAGE_PER_LEVEL = 0.05F;
    /** 猩红腐败每级持续秒数 */
    private static final int ROT_SECONDS_PER_LEVEL = 10;
    /** 第二段每级击退强度 */
    private static final float FINAL_KNOCKBACK_PER_LEVEL = 0.75F;
    /** 两段之间的间隔（tick） */
    private static final int SECOND_STAGE_DELAY = 30;
    /** 待补刀标记的保留时长（tick，1 小时） */
    private static final int PENDING_DEATH_EXPIRY = 72000;
    /** 重新登录后补刀的延迟（tick） */
    private static final int PENDING_DEATH_DELAY = 20;

    /** 待补刀标记：演出结束时持有者已经下线 */
    private static final String PENDING_DEATH_KEY = "scarlet_lonia_pending_death";

    public EnchantmentScarletLonia() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@Nonnull EnchantmentContext ctx, int pieceLevel) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("scarlet_lonia_cooldown", hurter.getUniqueID())) {
            Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUniqueID());
            if (isActive != null && isActive) {
                ctx.cancelEvent();
                hurter.setHealth(1);
            }
            return;
        }

        // 分发器按件回调，第一件触发后就进冷却，等级要取全身最高
        int level = highestLevel(hurter);

        EnchantmentDataManager.setData("scarlet_lonia_active", hurter.getUniqueID(), true);
        EnchantmentDataManager.setCooldown("scarlet_lonia_cooldown", hurter.getUniqueID(), COOLDOWN);

        ctx.cancelEvent();
        hurter.setHealth(1);
        hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 6));

        int knockbackRadius = Math.min(level * KNOCKBACK_RADIUS_PER_LEVEL, MAX_KNOCKBACK_RADIUS);
        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                hurter,
                knockbackRadius,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        int knockbackHitCount = 0;
        for (EntityLivingBase entityLivingBase : entities) {
            if (knockbackHitCount >= MAX_TARGETS) {
                break;
            }
            // knockBack 会把传入的向量取反，传「自己 - 目标」才是把目标推开
            double x = hurter.posX - entityLivingBase.posX;
            double z = hurter.posZ - entityLivingBase.posZ;
            float stronge = (float) (level * KNOCKBACK_STRENGTH_PER_LEVEL * Math.max(Math.abs(x), Math.abs(z)) / 14);
            entityLivingBase.knockBack(hurter, stronge, x, z);
            knockbackHitCount++;
        }

        int finalLevel = level;
        new SynchronizationTask(SECOND_STAGE_DELAY) {
            @Override
            public void run() {
                int damageRadius = Math.min(finalLevel * DAMAGE_RADIUS_PER_LEVEL, MAX_DAMAGE_RADIUS);
                List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        hurter,
                        damageRadius,
                        entityLivingBase -> !entityLivingBase.equals(hurter)
                );

                int damageHitCount = 0;
                for (EntityLivingBase entityLivingBase : nearbyEntities) {
                    if (damageHitCount >= MAX_TARGETS) {
                        break;
                    }
                    float damage = entityLivingBase.getHealth() * finalLevel * DAMAGE_PER_LEVEL;
                    EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);
                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT,
                            finalLevel * ROT_SECONDS_PER_LEVEL * 20, finalLevel - 1));

                    double x = hurter.posX - entityLivingBase.posX;
                    double z = hurter.posZ - entityLivingBase.posZ;
                    entityLivingBase.knockBack(hurter, finalLevel * FINAL_KNOCKBACK_PER_LEVEL, x, z);
                    damageHitCount++;
                }

                EnchantmentDataManager.removeData("scarlet_lonia_active", hurter.getUniqueID());

                // 下线后实体已移除，kill 会空转，登记待补刀，重新登录时补上
                if (hurter.isEntityAlive()) {
                    EntityLivingUtil.kill(hurter, NewDamageSource.SCARLET_ROT);
                } else if (hurter instanceof EntityPlayer && hurter.getHealth() > 0.0F) {
                    EnchantmentDataManager.setData(PENDING_DEATH_KEY, hurter.getUniqueID(), true, PENDING_DEATH_EXPIRY);
                }
            }
        }.start();
    }

    @Override
    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUniqueID());

        if (isActive != null && isActive) {
            ctx.cancelEvent();
        }
    }

    @Mod.EventBusSubscriber
    public static class ClientEventHandler {

        @SubscribeEvent
        public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
            if (evt.getEntity().world.isRemote) {
                EntityLivingBase entityLiving = evt.getEntityLiving();
                Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", entityLiving.getUniqueID());

                if (isActive != null && isActive) {
                    EntityLivingUtil.setJumped(entityLiving);
                }
            }
        }
    }

    /**
     * 演出期间下线逃掉死亡的补刀
     */
    @Mod.EventBusSubscriber
    public static class ServerEventHandler {

        @SubscribeEvent
        public static void onPlayerLoggedIn(@Nonnull PlayerEvent.PlayerLoggedInEvent evt) {
            EntityPlayer player = evt.player;
            if (player.world.isRemote) {
                return;
            }

            Boolean pending = EnchantmentDataManager.getData(PENDING_DEATH_KEY, player.getUniqueID());
            if (pending == null || !pending) {
                return;
            }

            // 先清标记再补刀，补刀失败也不会每次登录都被杀
            EnchantmentDataManager.removeData(PENDING_DEATH_KEY, player.getUniqueID());

            new SynchronizationTask(PENDING_DEATH_DELAY) {
                @Override
                public void run() {
                    if (player.isEntityAlive()) {
                        EntityLivingUtil.kill(player, NewDamageSource.SCARLET_ROT);
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