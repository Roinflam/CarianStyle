package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/** 血的收藏附魔 - 修复: BloodSlash联动检查getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "blood_collection", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class, EnchantmentDarkMoon.class})
public class EnchantmentBloodCollection extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.blood_collection.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood_collection";

    /**
     * 每级按已损失生命比例提供的额外伤害倍率
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);

    /**
     * 基础治疗系数
     * <p>默认 0.02，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    0.02D, 0.0D, 1.0D);

    /**
     * 同时装备「鲜血斩击」时的治疗系数
     * <p>默认 0.04，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER_WITH_SLASH =
            EnchantmentValues.define(VALUE_ID, "heal_multiplier_with_slash",
                    0.04D, 0.0D, 1.0D);

    public EnchantmentBloodCollection() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) effectiveLevel = Math.min(effectiveLevel, 10);
        if (!isFullyCharged(ctx.getHolder())) return;
        float lostHealthRatio = 1 - attacker.getHealth() / attacker.getMaxHealth();
        float bonusDamage = ctx.getDamage() * lostHealthRatio * effectiveLevel * (float) BONUS_DAMAGE_PER_LEVEL.get();
        ctx.addDamage(bonusDamage);
        // 修复：使用主手检查BloodSlash联动
        float healMultiplier = (float) HEAL_MULTIPLIER.get();
        Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
        if (bloodSlash != null) {
            ItemStack mainHand = attacker.getItemInHand(InteractionHand.MAIN_HAND);
            if (!mainHand.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(bloodSlash, mainHand) > 0) {
                healMultiplier = (float) HEAL_MULTIPLIER_WITH_SLASH.get();
            }
        }
        attacker.heal(attacker.getMaxHealth() * lostHealthRatio * effectiveLevel * healMultiplier);
    }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 15) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
