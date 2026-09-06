package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 黑焰仪式附魔
 * <p>v2.2：LivingHurt攻击者视角入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，无需检查。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "black_flame_ritual", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentShelterOfFire.class, EnchantmentHealingByFire.class})
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameRitual extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.black_flame_ritual.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "black_flame_ritual";

    /**
     * 每个负面效果提供的伤害加成
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle HARMFUL_BONUS =
            EnchantmentValues.define(VALUE_ID, "harmful_bonus",
                    0.2D, 0.0D, 5.0D);

    /**
     * 每个正面效果提供的伤害加成
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BENEFICIAL_BONUS =
            EnchantmentValues.define(VALUE_ID, "beneficial_bonus",
                    0.1D, 0.0D, 5.0D);

    /**
     * 自损结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次自损后保留的生命比例（越小掉血越快）
     * <p>默认 0.95，允许范围 0.5 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEALTH_RETAIN_RATIO =
            EnchantmentValues.define(VALUE_ID, "health_retain_ratio",
                    0.95D, 0.5D, 1.0D);

    public EnchantmentBlackFlameRitual() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角，根据自身效果数增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) return;

        // v-cache：走中央装备缓存（主手 + 四个护甲槽）
        int totalLevel = EnchantmentEventHandler.armorAndMainHand(attacker, blackFlameRitual);
        if (totalLevel <= 0) return;

        float damageMultiplier = 1;
        for (MobEffectInstance effect : attacker.getActiveEffects()) {
            MobEffect potion = effect.getEffect();
            if (!potion.isInstantenous() && effect.isVisible()) {
                damageMultiplier += (float) ((!potion.isBeneficial()) ? HARMFUL_BONUS.get() : BENEFICIAL_BONUS.get());
            }
        }
        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    /**
     * 持续燃烧自损（PlayerTickEvent 玩家专属，无需开关检查）
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;
        if (evt.player.tickCount % TICK_INTERVAL.getInt() != 0) return;
        Player holder = evt.player;
        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) return;
        // v-cache：走中央装备缓存
        int totalLevel = EnchantmentEventHandler.armorTotal(holder, blackFlameRitual);
        if (totalLevel <= 0) return;
        boolean hasPotion = false;
        for (MobEffectInstance effect : holder.getActiveEffects()) {
            MobEffect potion = effect.getEffect();
            if (!potion.isInstantenous() && effect.isVisible()) {
                hasPotion = true;
                break;
            }
        }
        if (hasPotion) {
            DynamicAttributeManager.apply(holder, DynamicAttributes.DESTRUCTION_FIRE_BURNING.createInstance(21, 0));
            holder.setHealth(holder.getHealth() * (float) HEALTH_RETAIN_RATIO.get());
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }

    @Override
    public int getMinCost(int l) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
