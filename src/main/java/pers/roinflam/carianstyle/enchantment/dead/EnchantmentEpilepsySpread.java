package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
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
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 发狂扩散附魔
 * <p>
 * 受到伤害后生命值将不高于 30% 时触发（90 秒冷却）：抵消该伤害、生命设为 30%，击退周围
 * 等级×4 格（至多 16 格）内的生物（含自己至多 24 个），自身无敌且无法移动；1.5 秒后范围内所有生物
 * 陷入癫火，3 秒内持续掉血，最后自身死亡。多件盔甲取最高等级。
 * </p>
 * <p>
 * 以前对周围生物传的方向是「目标 - 自己」，原版 knockBack 会把向量取反，实际是把敌人拉向自己；
 * 现在传「自己 - 目标」，是推开。以前半径和目标数不封顶、等级取扫到的第一件，
 * 处于普拉顿桑克斯的回溯中的目标照样掉血，演出期间下线能逃过最后的死亡。
 * 现在半径至多 16 格、至多 24 个，取全身最高等级，回溯中的目标不掉血，
 * 下线的玩家记一个待补刀标记，重新登录 1 秒后补上死亡。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {

    /** 搜索半径上限（格） */
    private static final int MAX_SEARCH_RADIUS = 16;
    /** 单次触发至多处理的生物数（含自己） */
    private static final int MAX_TARGETS = 24;
    /** 每级搜索半径（格） */
    private static final int RADIUS_PER_LEVEL = 4;
    /** 触发线：受击后生命不高于最大生命的这个比例 */
    private static final float TRIGGER_HEALTH_RATIO = 0.3F;
    /** 触发后生命设为最大生命的这个比例 */
    private static final float REVIVE_HEALTH_RATIO = 0.3F;
    /** 冷却（tick） */
    private static final int COOLDOWN = 1800;
    /** 每级击退强度系数 */
    private static final double KNOCKBACK_PER_LEVEL = 0.7D;
    /** 自身 3 秒内掉的血（占最大生命） */
    private static final float SELF_DOT_RATIO = 0.3F;
    /** 目标 3 秒内掉的血（占持有者最大生命，× 等级 × 2） */
    private static final float TARGET_DOT_PER_LEVEL = 0.3F;
    /** 待补刀标记的保留时长（tick，1 小时） */
    private static final int PENDING_DEATH_EXPIRY = 72000;
    /** 重新登录后补刀的延迟（tick） */
    private static final int PENDING_DEATH_DELAY = 20;

    /** 待补刀标记：演出结束时持有者已经下线 */
    private static final String PENDING_DEATH_KEY = "epilepsy_spread_pending_death";

    public EnchantmentEpilepsySpread() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    protected void onDamageAsVictimLowest(@Nonnull EnchantmentContext ctx, int pieceLevel) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        EntityLivingBase hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("epilepsy_spread_cooldown", hurter.getUniqueID())) {
            return;
        }

        // 分发器按件回调，第一件触发后就进冷却，等级要取全身最高
        int level = highestLevel(hurter);

        if (hurter.getHealth() - ctx.getDamage() <= hurter.getMaxHealth() * TRIGGER_HEALTH_RATIO) {
            EnchantmentDataManager.setData("epilepsy_spread_active", hurter.getUniqueID(), true);
            EnchantmentDataManager.setCooldown("epilepsy_spread_cooldown", hurter.getUniqueID(), COOLDOWN);

            ctx.cancelEvent();
            hurter.setHealth(hurter.getMaxHealth() * REVIVE_HEALTH_RATIO);
            hurter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 6));

            int searchRadius = Math.min(level * RADIUS_PER_LEVEL, MAX_SEARCH_RADIUS);

            List<EntityLivingBase> rawEntities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    hurter,
                    searchRadius
            );

            List<EntityLivingBase> entities = new ArrayList<>();
            for (EntityLivingBase entityLivingBase : rawEntities) {
                if (entities.size() >= MAX_TARGETS) {
                    break;
                }
                entities.add(entityLivingBase);

                entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);
                if (!entityLivingBase.equals(hurter)) {
                    // knockBack 会把传入的向量取反，传「自己 - 目标」才是把目标推开
                    double x = hurter.posX - entityLivingBase.posX;
                    double z = hurter.posZ - entityLivingBase.posZ;
                    float stronge = (float) (level * KNOCKBACK_PER_LEVEL * Math.max(Math.abs(x), Math.abs(z)) / 14);
                    entityLivingBase.knockBack(hurter, stronge, x, z);
                }
            }

            int finalLevel = level;
            new SynchronizationTask(30) {
                @Override
                public void run() {
                    for (EntityLivingBase entityLivingBase : entities) {
                        entityLivingBase.playSound(SoundEvents.ENTITY_GHAST_HURT, 1, 1);
                        entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.EPILEPSY_FIRE_BURNING, 3 * 20 + 5, 0));

                        new SynchronizationTask(5, 1) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 60 || !entityLivingBase.isEntityAlive()) {
                                    this.cancel();
                                    return;
                                }

                                if (entityLivingBase.equals(hurter)) {
                                    float damage = hurter.getMaxHealth() * SELF_DOT_RATIO / 60;
                                    if (hurter.getHealth() - damage * 2 > 0) {
                                        EntityLivingUtil.damageHealthDirectly(hurter, damage);
                                    } else {
                                        EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUniqueID());
                                        EntityLivingUtil.kill(hurter, NewDamageSource.EPILEPSY_FIRE);
                                        this.cancel();
                                    }
                                } else if (!EnchantmentTimeReversal.isReversing(entityLivingBase)) {
                                    float damage = hurter.getMaxHealth() * finalLevel * TARGET_DOT_PER_LEVEL * 2 / 60;
                                    if (entityLivingBase.getHealth() - damage * 2 > 0) {
                                        EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);
                                    } else {
                                        EntityLivingUtil.kill(entityLivingBase, NewDamageSource.EPILEPSY_FIRE);
                                        this.cancel();
                                    }
                                }
                            }
                        }.start();
                    }

                    new SynchronizationTask(66) {
                        @Override
                        public void run() {
                            // 下线后实体已移除，kill 会空转，登记待补刀，重新登录时补上
                            if (hurter.isEntityAlive()) {
                                EntityLivingUtil.kill(hurter, NewDamageSource.EPILEPSY_FIRE);
                            } else if (hurter instanceof EntityPlayer && hurter.getHealth() > 0.0F) {
                                EnchantmentDataManager.setData(PENDING_DEATH_KEY, hurter.getUniqueID(), true, PENDING_DEATH_EXPIRY);
                            }
                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUniqueID());
                        }
                    }.start();
                }
            }.start();
        }
    }

    @Override
    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", hurter.getUniqueID());

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
                Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", entityLiving.getUniqueID());

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
                        EntityLivingUtil.kill(player, NewDamageSource.EPILEPSY_FIRE);
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