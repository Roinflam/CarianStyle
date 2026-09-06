package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 血斩附魔
 * <p>v2.2：onLivingDeath 击杀者视角入口接入怪物附魔触发开关。
 * onHurtAsAttackerLow 走中央事件分发器，已被 scanEntity 拦截。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "blood_slash", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class, EnchantmentDarkMoon.class})
@Mod.EventBusSubscriber
public class EnchantmentBloodSlash extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.blood_slash.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood_slash";

    /**
     * 每级按目标当前生命计算的额外伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.05D, 0.0D, 2.0D);

    /**
     * 每次触发自伤的最大生命占比
     * <p>默认 0.1，允许范围 0.0 ~ 0.9。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.1D, 0.0D, 0.9D);

    /**
     * 同时装备「血的收藏」时击杀每级回复的最大生命占比
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL_WITH_COLLECTION =
            EnchantmentValues.define(VALUE_ID, "heal_per_level_with_collection",
                    0.05D, 0.0D, 2.0D);

    /**
     * 击杀每级回复的最大生命占比
     * <p>默认 0.025，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.025D, 0.0D, 2.0D);

    public EnchantmentBloodSlash() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @Override
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();
        if (victim == null) return;
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) effectiveLevel = Math.min(effectiveLevel, 10);
        if (ctx.isHolderPlayer() && ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) return;
        float bonusDamage = Math.min(
                victim.getHealth() * effectiveLevel * (float) BONUS_DAMAGE_PER_LEVEL.get(),
                victim.getMaxHealth());
        ctx.addDamage(bonusDamage);
        if (!(attacker instanceof Player) || !((Player) attacker).isCreative()) {
            if (attacker.getHealth() > attacker.getMaxHealth() * SELF_DAMAGE_RATIO.get()) {
                EntityLivingUtil.damageHealthDirectly(attacker,
                    attacker.getMaxHealth() * (float) SELF_DAMAGE_RATIO.get());
            } else {
                EntityLivingUtil.kill(attacker, NewDamageSource.hemorrhage(attacker.level()));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity killer)) return;
        if (!killer.isAlive() || evt.getEntity().equals(killer)) return;

        // ⭐ v2.2：怪物附魔触发开关（击杀者视角，击杀回血非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
        if (bloodSlash == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(bloodSlash, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        Enchantment bloodCollection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodCollection.class);
        if (bloodCollection != null && EnchantmentHelper.getItemEnchantmentLevel(bloodCollection, heldItem) > 0) {
            killer.heal(killer.getMaxHealth() * level * (float) HEAL_PER_LEVEL_WITH_COLLECTION.get());
        } else {
            killer.heal(killer.getMaxHealth() * level * (float) HEAL_PER_LEVEL.get());
        }
    }

    @Override public int getMinCost(int l) { return (int)((20 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
