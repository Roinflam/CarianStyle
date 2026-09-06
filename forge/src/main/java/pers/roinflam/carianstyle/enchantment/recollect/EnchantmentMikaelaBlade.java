package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
import java.util.UUID;

/**
 * 米凯拉之刃附魔
 * <p>v2.2：攻击者+受击者计数器累积均接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "mikaela_blade", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentMikaelaBlade extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.mikaela_blade.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "mikaela_blade";

    /**
     * 连击起始时的伤害倍率
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "base_multiplier",
                    0.4D, 0.0D, 5.0D);

    /**
     * 每层连击增加的伤害倍率
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MULTIPLIER_PER_COMBO =
            EnchantmentValues.define(VALUE_ID, "multiplier_per_combo",
                    0.2D, 0.0D, 5.0D);

    /**
     * 连击层数的保持时长（tick）
     * <p>默认 40，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle COMBO_DURATION =
            EnchantmentValues.define(VALUE_ID, "combo_duration",
                    40, 1, 1200);

    /**
     * 目标每层连击时自身受到的额外伤害比例
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_COMBO_BONUS =
            EnchantmentValues.define(VALUE_ID, "victim_combo_bonus",
                    0.1D, 0.0D, 5.0D);


    private static final String COMBO_COUNT_KEY = "mikaela_blade_combo";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentMikaelaBlade() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        Enchantment mikaelaBlade = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMikaelaBlade.class);
        if (mikaelaBlade == null) return;

        // 攻击者视角
        if (evt.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(mikaelaBlade, heldItem);
                    if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                    if (level > 0) {
                        UUID uuid = attacker.getUUID();
                        int combo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, uuid);
                        evt.setAmount(evt.getAmount() * (float) BASE_MULTIPLIER.get()
                    + evt.getAmount() * combo * (float) MULTIPLIER_PER_COMBO.get());
                        EnchantmentDataManager.setCounter(COMBO_COUNT_KEY, uuid, combo + 1, COMBO_DURATION.getInt());
                    }
                }
            }
        }

        // 受击者视角（被打断连击的额外伤害）
        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        int victimCombo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, victim.getUUID());
        if (victimCombo > 0) evt.setAmount(evt.getAmount() + evt.getAmount() * victimCombo * (float) VICTIM_COMBO_BONUS.get());
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
