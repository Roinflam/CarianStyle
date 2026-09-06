package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import java.util.UUID;

/**
 * 忍耐附魔
 * <p>v2.2：受击累积监听器接入怪物附魔触发开关。
 * onHurtAsAttackerLowest 走中央事件分发器，已被 scanEntity 入口拦截。
 * onLivingDeath 是清理类，不影响行为，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "patience",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentPatience extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.patience.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "patience";

    /**
     * 每级可累积伤害的最大生命占比上限
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MAX_ACCUMULATED_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_accumulated_per_level",
                    0.4D, 0.0D, 5.0D);

    /**
     * 每级把受到伤害转为累积值的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ACCUMULATE_RATE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "accumulate_rate_per_level",
                    0.1D, 0.0D, 2.0D);


    private static final String PATIENCE_DATA_KEY = "patience_accumulated";

    public EnchantmentPatience() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        UUID uuid = attacker.getUUID();

        Float accumulated = EnchantmentDataManager.getData(PATIENCE_DATA_KEY, uuid);
        if (accumulated != null && accumulated > 0) {
            ctx.addDamage(accumulated);
            EnchantmentDataManager.removeData(PATIENCE_DATA_KEY, uuid);
        }
    }

    /**
     * 受击时累积能量
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamageStatic(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment patience = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPatience.class);
        if (patience == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(patience, heldItem);

        if (level <= 0) {
            return;
        }

        UUID uuid = victim.getUUID();
        Float current = EnchantmentDataManager.getData(PATIENCE_DATA_KEY, uuid);
        float accumulated = current != null ? current : 0f;

        float maxAccumulated = victim.getMaxHealth() * level * (float) MAX_ACCUMULATED_PER_LEVEL.get();
        accumulated = Math.min(accumulated + evt.getAmount() * level * (float) ACCUMULATE_RATE_PER_LEVEL.get(), maxAccumulated);

        EnchantmentDataManager.setData(PATIENCE_DATA_KEY, uuid, accumulated);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        // 清理类，无需开关检查
        EnchantmentDataManager.removeData(PATIENCE_DATA_KEY, evt.getEntity().getUUID());
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
