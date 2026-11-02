package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死诞者附魔
 * <p>
 * 死亡时满血复活，但开始持续失血直到再次死亡
 * 冷却4800tick
 * </p>
 * <p>
 * v2.1新增: onLivingDeath 入口接入怪物附魔触发开关，
 * 怪物身上的"濒死复活+流血"效果可由配置 allowMobTriggerDeathEnchantments 控制
 * </p>
 * <p>
 * v2.2 修复：流血期间不管被什么打死，都算这条命的终点——清掉流血标记、不再复活。
 * 以前只有流血自己耗死才清标记：被别的伤害打死时标记留着，重生后又开始流血；
 * 在死亡界面停超过冷却时间，流血致死还会再触发一次复活，一直循环。
 * 流血任务同一实体只留一份（{@link #RUNNING_BLEEDS}）：切维度也会发 EntityJoinLevelEvent，
 * 以前会再开一份，两份一起扣血。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "living_corpse",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentLivingCorpse extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.living_corpse.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "living_corpse";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 复活的冷却时间（tick）
     * <p>默认 4800，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle REVIVE_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "revive_cooldown",
                    4800, 20, 144000);

    /**
     * 复活后每秒流失的最大生命占比基数
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DRAIN_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "drain_ratio_per_second",
                    0.01D, 0.0D, 1.0D);


    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /**
     * 正在跑的流血任务，实体 UUID → 任务。
     * <p>玩家切维度实体对象不变，留着原任务；怪物切维度、玩家重新登录是新对象，原任务作废，换新的接上。</p>
     */
    private static final Map<UUID, BleedTask> RUNNING_BLEEDS = new ConcurrentHashMap<>();

    public EnchantmentLivingCorpse() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 获取实体装备的死诞者附魔总等级
     *
     * @param entity 实体
     * @return 附魔总等级
     */
    private static int getTotalLevel(LivingEntity entity) {
        Enchantment livingCorpse = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLivingCorpse.class);
        if (livingCorpse == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(livingCorpse, armor);
            }
        }
        totalLevel = applyLevelLimit(livingCorpse, totalLevel, LEVEL_CAP.getInt());
        return totalLevel;
    }

    /**
     * 监听生物死亡事件 - 触发复活机制
     * <p>v2.1新增：怪物附魔触发开关（濒死类）拦截</p>
     *
     * @param evt 死亡事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // v2.1：怪物附魔触发开关 —— 死诞者属于濒死复活类，怪物身上不触发
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, true)) {
            return;
        }

        UUID uuid = holder.getUUID();

        // 流血期间死亡（耗死或被打死）就是真的死了：清标记，不再复活
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

        // 取消死亡事件
        evt.setCanceled(true);

        // 满血复活
        holder.setHealth(holder.getMaxHealth());
        holder.invulnerableTime = 20;

        // 设置冷却和流血状态
        EnchantmentDataManager.setCooldown(REVIVE_COOLDOWN_KEY, uuid, REVIVE_COOLDOWN.getInt());
        EnchantmentDataManager.setData(BLEEDING_STATE_KEY, uuid, true);

        // 启动持续失血任务
        startBleeding(holder, new BleedTask(holder, evt.getSource(), 5, 75));
    }

    /**
     * 监听实体加入世界事件 - 恢复失血状态
     * <p>注意：此事件用于玩家重新登录时恢复未完结的流血状态，
     * 不属于"触发亡语"，未接入怪物附魔开关。
     * 若 onLivingDeath 被拦截，BLEEDING_STATE_KEY 本来就不会被设置，
     * 本方法不会对怪物产生影响。</p>
     *
     * @param evt 实体加入事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = (LivingEntity) evt.getEntity();
        UUID uuid = holder.getUUID();

        Boolean bleeding = EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid);
        if (bleeding == null || !bleeding) {
            return;
        }

        // 切维度时实体对象没变，原任务接着跑就行，再开一份会双倍扣血
        BleedTask running = RUNNING_BLEEDS.get(uuid);
        if (running != null && running.holder == holder) {
            return;
        }

        // 恢复失血任务
        startBleeding(holder, new BleedTask(holder, holder.damageSources().fellOutOfWorld(), 6, 30));
    }

    /**
     * 启动流血任务，顶掉这个实体之前的那一份
     *
     * @param holder 流血的实体
     * @param task   新任务
     */
    private static void startBleeding(@NotNull LivingEntity holder, @NotNull BleedTask task) {
        BleedTask previous = RUNNING_BLEEDS.put(holder.getUUID(), task);
        if (previous != null) {
            previous.cancel();
        }
        task.start();
    }

    /**
     * 持续失血任务：每 tick 扣血，扣到快没了就处决。标记没了（已经死过一次）就结束。
     */
    private static final class BleedTask extends SynchronizationTask {
        private final LivingEntity holder;
        private final DamageSource lethalSource;
        private final int baseMultiplier;
        private final int rampDivisor;
        private int tick = 0;

        BleedTask(LivingEntity holder, DamageSource lethalSource, int baseMultiplier, int rampDivisor) {
            super(1, 1);
            this.holder = holder;
            this.lethalSource = lethalSource;
            this.baseMultiplier = baseMultiplier;
            this.rampDivisor = rampDivisor;
        }

        @Override
        public void run() {
            UUID uuid = holder.getUUID();
            if (!holder.isAlive() || !Boolean.TRUE.equals(EnchantmentDataManager.getData(BLEEDING_STATE_KEY, uuid))) {
                finish(uuid);
                return;
            }

            float baseDamage = holder.getMaxHealth() * (float) DRAIN_RATIO_PER_SECOND.get() / 20;
            float damage = baseDamage * baseMultiplier + baseDamage * ++tick / rampDivisor;

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
    protected boolean checkCompatibility(Enchantment ench) {
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    /**
     * 判断是否为死亡类附魔（互斥）
     *
     * @param ench 待检查的附魔
     * @return 是否为死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench instanceof EnchantmentFullMoon || ench instanceof EnchantmentTimeReversal;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
