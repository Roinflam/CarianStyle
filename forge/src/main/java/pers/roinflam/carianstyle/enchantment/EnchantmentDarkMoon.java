package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentFullMoon;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 暗月附魔
 * <p>v2.2：LivingHurt双向+LivingHeal入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，无需检查。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "dark_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class})
@Mod.EventBusSubscriber
public class EnchantmentDarkMoon extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.dark_moon.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dark_moon";

    /**
     * 未同时装备满月时的减伤/增伤比例
     * <p>默认 0.25，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION =
            EnchantmentValues.define(VALUE_ID, "reduction",
                    0.25D, 0.0D, 1.0D);

    /**
     * 同时装备满月时的减伤/增伤比例
     * <p>默认 0.375，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_WITH_FULL_MOON =
            EnchantmentValues.define(VALUE_ID, "reduction_with_full_moon",
                    0.375D, 0.0D, 1.0D);

    /**
     * 未同时装备满月时的追加伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_DAMAGE =
            EnchantmentValues.define(VALUE_ID, "extra_damage",
                    0.05D, 0.0D, 1.0D);

    /**
     * 同时装备满月时的追加伤害比例
     * <p>默认 0.075，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_DAMAGE_WITH_FULL_MOON =
            EnchantmentValues.define(VALUE_ID, "extra_damage_with_full_moon",
                    0.075D, 0.0D, 1.0D);

    /**
     * 夜视效果的持续时间（tick）
     * <p>默认 210，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_VISION_TICKS =
            EnchantmentValues.define(VALUE_ID, "night_vision_ticks",
                    210, 20, 6000);

    public EnchantmentDarkMoon() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    /**
     * 取当前生效的减伤 / 增伤比例。
     * <p>
     * 原本三处各写了一遍 {@code hasFullMoon ? 0.375f : 0.25f}，
     * 改成可配置之后如果照搬会变成三处各写一遍三元表达式 —— 抽出来一处，
     * 服主改配置时也只有一组数值需要理解。
     * </p>
     *
     * @param hasFullMoon 是否同时装备了满月
     * @return 对应的比例
     */
    private static float ratio(boolean hasFullMoon) {
        return (float) (hasFullMoon ? REDUCTION_WITH_FULL_MOON.get() : REDUCTION.get());
    }

    private static boolean hasFullMoonEnchantment(@NotNull LivingEntity entity) {
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return false;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor) > 0) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) return;
        LivingEntity victim = evt.getEntity();
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) return;

        // 受击者视角（减伤）
        if (victim instanceof Mob livingVictim) {
            // ⭐ v2.2：怪物附魔触发开关（受击者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(livingVictim, false)) {
                ItemStack heldItem = livingVictim.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
                    if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                    if (level > 0) {
                        boolean hasFullMoon = hasFullMoonEnchantment(livingVictim);
                        evt.setAmount(evt.getAmount() * (1 - ratio(hasFullMoon)));
                    }
                }
            }
        }

        // 攻击者视角（增伤+吸血）
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        if (!isFullyCharged(attacker)) return;
        boolean hasFullMoon = hasFullMoonEnchantment(attacker);
        float damageBonus = ratio(hasFullMoon);
        evt.setAmount(evt.getAmount() * (1 + damageBonus));
        if (victim instanceof Mob livingVictim && livingVictim.getTarget() != null && livingVictim.getTarget().equals(attacker)) {
            float extraDamage = (float) (hasFullMoon ? EXTRA_DAMAGE_WITH_FULL_MOON.get() : EXTRA_DAMAGE.get());
            evt.setAmount(evt.getAmount() + livingVictim.getHealth() * extraDamage);
            attacker.heal(Math.min(evt.getAmount() * extraDamage, attacker.getMaxHealth() * extraDamage));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        LivingEntity healer = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受治疗者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(healer, false)) return;

        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) return;
        ItemStack heldItem = healer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        boolean hasFullMoon = hasFullMoonEnchantment(healer);
        evt.setAmount(evt.getAmount() * (1 + ratio(hasFullMoon)));
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.player.level().isDay() || evt.phase != TickEvent.Phase.START) return;
        Player player = evt.player;
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null || !player.isAlive()) return;
        // v-cache：走中央装备缓存。本方法每玩家每 tick 都会跑（夜间），
        // 直查会每次反序列化一遍主手的附魔 ListTag。
        int level = EnchantmentEventHandler.mainHand(player, darkMoon);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level > 0) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS.getInt()));
    }

    @Override public int getMinCost(int l) { return (int)(35 * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
