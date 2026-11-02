package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodCollection;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodSlash;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 血附魔
 * <p>满蓄力连打两下后，第三下按目标当前生命追加直接伤害并回复自身，同时施加出血；
 * 武器同时带血斩和集血时出血等级提高。</p>
 * <p>修复: 联动检查固定看主手</p>
 *
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "blood", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentBlood extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.blood.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood";

    /**
     * 触发所需的连续攻击次数
     * <p>默认 2，允许范围 1 ~ 50。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_ATTACK_COUNT =
            EnchantmentValues.define(VALUE_ID, "trigger_attack_count",
                    2, 1, 50);

    /**
     * 按目标当前生命计算的额外伤害比例
     * <p>默认 0.12，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.12D, 0.0D, 2.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.18，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.18D, 0.0D, 2.0D);

    /**
     * 三件套联动时施加的出血等级
     * <p>默认 7，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle HEMORRHAGE_LEVEL_COMBO =
            EnchantmentValues.define(VALUE_ID, "hemorrhage_level_combo",
                    7, 0, 127);

    /**
     * 施加出血的持续时间（tick）
     * <p>默认 30，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle HEMORRHAGE_DURATION =
            EnchantmentValues.define(VALUE_ID, "hemorrhage_duration",
                    30, 1, 1200);

    /**
     * 攻击计数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNTER_DURATION =
            EnchantmentValues.define(VALUE_ID, "counter_duration",
                    6000, 20, 72000);

    private static final String ATTACK_COUNT_KEY = "blood_attack_count";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentBlood() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override
    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) return;
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) return;
        UUID uuid = attacker.getUniqueID();
        int attackCount = EnchantmentDataManager.getCounter(ATTACK_COUNT_KEY, uuid);
        if (attackCount >= TRIGGER_ATTACK_COUNT.getInt()) {
            EnchantmentDataManager.resetCounter(ATTACK_COUNT_KEY, uuid);
            float damage = victim.getHealth() * (float) DAMAGE_RATIO.get();
            attacker.heal(Math.min(damage, attacker.getMaxHealth() * (float) HEAL_CAP_RATIO.get()));
            // damage_ratio 允许配到 1 以上，归零时要走死亡，不能停在 0 血
            EntityLivingUtil.damageHealthDirectly(victim, damage, ctx.getDamageSource());
            // 联动只看主手
            int hemorrhageLevel = 0;
            ItemStack heldItem = attacker.getHeldItemMainhand();
            Enchantment bloodSlash = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodSlash.class);
            Enchantment bloodCollection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBloodCollection.class);
            if (bloodSlash != null && bloodCollection != null && !heldItem.isEmpty()) {
                if (EnchantmentHelper.getEnchantmentLevel(bloodSlash, heldItem) > 0 &&
                    EnchantmentHelper.getEnchantmentLevel(bloodCollection, heldItem) > 0) {
                    hemorrhageLevel = HEMORRHAGE_LEVEL_COMBO.getInt();
                }
            }
            victim.addPotionEffect(new PotionEffect(CarianStylePotion.HEMORRHAGE.get(), HEMORRHAGE_DURATION.getInt(), hemorrhageLevel));
        } else {
            EnchantmentDataManager.incrementCounter(ATTACK_COUNT_KEY, uuid, COUNTER_DURATION.getInt());
        }
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
