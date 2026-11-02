package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
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
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
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
@Deprecated
public class EnchantmentLivingCorpse extends EnchantmentBase {
    private static final String VALUE_ID = "living_corpse";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle REVIVE_COOLDOWN = EnchantmentValues.define(VALUE_ID, "revive_cooldown",
                    4800, 20, 144000);
    private static final EnchantmentValues.Handle DRAIN_RATIO_PER_SECOND = EnchantmentValues.define(VALUE_ID, "drain_ratio_per_second",
                    0.01D, 0.0D, 1.0D);
    private static final String REVIVE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String BLEEDING_STATE_KEY = "living_corpse_bleeding";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    private static final Map<UUID, BleedTask> RUNNING_BLEEDS = new ConcurrentHashMap<>();

    private static final class BleedTask extends SynchronizationTask {
        BleedTask(LivingEntity holder, DamageSource lethalSource, int baseMultiplier, int rampDivisor) {
            super(1, 1);
        }
        public void run() {
        }
    }
    protected boolean checkCompatibility(Enchantment ench) {
        return false;
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

