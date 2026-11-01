package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 死诞者附魔
 * <p>
 * 死亡时（240 秒冷却）满血复活并获得 1 秒无敌，但之后持续失血、越来越快，直到再次死亡；
 * 流血期间死亡（流干或被打死）就是真的死了，不会再次复活。
 * </p>
 * <p>
 * 以前流血期间被别的原因打死时不清流血标记，重生后进世界又开一份流血，新身体开局就在流血；
 * 每次进世界（换维度也算）都再开一份流血，会叠成多份同时扣血；复活后也没有无敌时间。
 * 现在死亡事件开头看到流血标记就清掉并放行；同一个实体只跑一份流血，换维度时原任务接着跑；
 * 复活后 20 tick 无敌。怪物持有时按死亡类附魔的开关处理。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "living_corpse",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentLivingCorpse extends EnchantmentBase {

    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 复活冷却（tick） */
    private static final int REVIVE_COOLDOWN = 4800;
    /** 复活后的无敌时间（tick） */
    private static final int REVIVE_INVULNERABLE_TICKS = 20;
    /** 每秒失血的基础量（占最大生命） */
    private static final float DRAIN_RATIO_PER_SECOND = 0.01F;

    /** 正在跑的流血任务，同一个实体只留一份 */
    private static final Map<UUID, BleedTask> RUNNING_BLEEDS = new HashMap<>();

    public EnchantmentLivingCorpse() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment livingCorpse = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLivingCorpse.class);
        if (livingCorpse == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(livingCorpse, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        if (shouldBlockMobTrigger(holder, true)) {
            return;
        }

        UUID uuid = holder.getUniqueID();

        // 流血期间死亡（流干或被打死）就是真的死了：清标记，不再复活
        if (Boolean.TRUE.equals(EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid))) {
            EnchantmentDataManager.removeData(BLEEDING_STATE_KEY, uuid);
            return;
        }

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(REVIVE_COOLDOWN_KEY, uuid)) {
            return;
        }

        if (holder.isDead) {
            return;
        }

        evt.setCanceled(true);
        holder.setHealth(holder.getMaxHealth());
        holder.hurtResistantTime = REVIVE_INVULNERABLE_TICKS;

        EnchantmentDataManager.setCooldown(REVIVE_COOLDOWN_KEY, uuid, REVIVE_COOLDOWN);
        EnchantmentDataManager.setData(BLEEDING_STATE_KEY, uuid, true);

        startBleeding(holder, new BleedTask(holder, evt.getSource(), 5, 75));
    }

    /**
     * 进世界时接上没流完的血（重新登录）；换维度时实体对象没变，原任务接着跑，不再开第二份
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = (EntityLivingBase) evt.getEntity();
        UUID uuid = holder.getUniqueID();

        Boolean bleeding = EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid);
        if (bleeding == null || !bleeding) {
            return;
        }

        BleedTask running = RUNNING_BLEEDS.get(uuid);
        if (running != null && running.holder == holder) {
            return;
        }

        startBleeding(holder, new BleedTask(holder, DamageSource.OUT_OF_WORLD, 6, 30));
    }

    /**
     * 启动流血任务，顶掉这个实体之前的那一份
     */
    private static void startBleeding(@Nonnull EntityLivingBase holder, @Nonnull BleedTask task) {
        BleedTask previous = RUNNING_BLEEDS.put(holder.getUniqueID(), task);
        if (previous != null) {
            previous.cancel();
        }
        task.start();
    }

    /**
     * 持续失血：每 tick 扣血，扣到快没了就处决；标记没了（已经死过一次）就结束
     */
    private static final class BleedTask extends SynchronizationTask {
        private final EntityLivingBase holder;
        private final DamageSource lethalSource;
        private final int baseMultiplier;
        private final int rampDivisor;
        private int elapsed = 0;

        BleedTask(EntityLivingBase holder, DamageSource lethalSource, int baseMultiplier, int rampDivisor) {
            super(1, 1);
            this.holder = holder;
            this.lethalSource = lethalSource;
            this.baseMultiplier = baseMultiplier;
            this.rampDivisor = rampDivisor;
        }

        @Override
        public void run() {
            UUID uuid = holder.getUniqueID();
            if (!holder.isEntityAlive() || !Boolean.TRUE.equals(EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid))) {
                finish(uuid);
                return;
            }

            float baseDamage = holder.getMaxHealth() * DRAIN_RATIO_PER_SECOND / 20;
            float damage = baseDamage * baseMultiplier + baseDamage * ++elapsed / rampDivisor;

            if (holder.getHealth() - damage * 2 > 0) {
                EntityLivingUtil.damageHealthDirectly(holder, damage);
            } else {
                EntityLivingUtil.kill(holder, lethalSource);
                EnchantmentDataManager.removeData(BLEEDING_STATE_KEY, uuid);
                finish(uuid);
            }
        }

        private void finish(UUID uuid) {
            RUNNING_BLEEDS.remove(uuid, this);
            this.cancel();
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与死亡类附魔冲突
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    /**
     * 判断是否是死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class))
                || ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}