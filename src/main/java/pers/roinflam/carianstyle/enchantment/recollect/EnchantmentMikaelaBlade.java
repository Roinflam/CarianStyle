package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
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
 * 米凯拉之刃附魔
 *
 * 攻击：伤害 = 原伤害×0.4 + 原伤害×连击数×0.2，然后连击+1
 * 受击：额外受到伤害 = 伤害×自己连击数×0.1（连击越多越脆弱）
 * 连击数每40tick衰减1
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

            if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(
                        mikaelaBlade,
                        attacker.getHeldItem(attacker.getActiveHand()));

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    UUID uuid = attacker.getUniqueID();
                    int combo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, uuid);

                    evt.setAmount(evt.getAmount() * 0.4f + evt.getAmount() * combo * 0.2f);
                    EnchantmentDataManager.setCounter(COMBO_COUNT_KEY, uuid, combo + 1, 40);
                }
            }
        }

        // 受击者视角：连击惩罚
        EntityLivingBase victim = evt.getEntityLiving();
        UUID victimUuid = victim.getUniqueID();
        int victimCombo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, victimUuid);

        if (victimCombo > 0) {
            evt.setAmount(evt.getAmount() + evt.getAmount() * victimCombo * 0.1f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}