package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 米凯拉的锋刃附魔
 * <p>
 * 主手持有时伤害 ×0.4；每次满蓄力攻击获得一层增幅，每层 +20% 伤害，2 秒内没有再次满蓄力攻击则层数全部清空。
 * 有层数时自己受到的伤害每层 +10%。
 * </p>
 * <p>
 * 以前每一下攻击都叠层、都续 2 秒，连点就能叠满。现在只有满蓄力才叠层并刷新计时；
 * 未满蓄力的一下只吃基础 ×0.4，不叠层也拿不到层数加成（不能整段跳过，否则低层数时连点反而更痛）。
 * 武器以前按「最近一次使用物品的那只手」取，现在一律看主手；怪物持有时按怪物触发附魔的开关处理。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "mikaela_blade",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentMikaelaBlade extends EnchantmentBase {

    private static final String COMBO_COUNT_KEY = "mikaela_blade_combo";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 基础伤害倍率 */
    private static final float BASE_MULTIPLIER = 0.4F;
    /** 每层增幅 */
    private static final float MULTIPLIER_PER_COMBO = 0.2F;
    /** 层数保留时间（tick），期间没有再次满蓄力攻击就清空 */
    private static final int COMBO_DURATION = 40;
    /** 有层数时每层受到的额外伤害 */
    private static final float VICTIM_COMBO_BONUS = 0.1F;
    /** 等级上限 */
    private static final int LEVEL_CAP = 10;

    public EnchantmentMikaelaBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击增伤（连击机制）+ 受击增伤（连击惩罚）
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        Enchantment mikaelaBlade = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMikaelaBlade.class);
        if (mikaelaBlade == null) {
            return;
        }

        // 攻击者视角：连击增伤
        if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

            if (!shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getHeldItemMainhand();
                if (!heldItem.isEmpty()) {
                    int level = capLevel(EnchantmentHelper.getEnchantmentLevel(mikaelaBlade, heldItem), LEVEL_CAP);
                    if (level > 0) {
                        if (isFullyCharged(attacker)) {
                            UUID uuid = attacker.getUniqueID();
                            int combo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, uuid);
                            evt.setAmount(evt.getAmount() * BASE_MULTIPLIER + evt.getAmount() * combo * MULTIPLIER_PER_COMBO);
                            EnchantmentDataManager.setCounter(COMBO_COUNT_KEY, uuid, combo + 1, COMBO_DURATION);
                        } else {
                            evt.setAmount(evt.getAmount() * BASE_MULTIPLIER);
                        }
                    }
                }
            }
        }

        // 受击者视角：连击惩罚
        EntityLivingBase victim = evt.getEntityLiving();
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        int victimCombo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, victim.getUniqueID());
        if (victimCombo > 0) {
            evt.setAmount(evt.getAmount() + evt.getAmount() * victimCombo * VICTIM_COMBO_BONUS);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}