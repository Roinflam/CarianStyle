package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 戴狄卡之祸附魔（诅咒）
 *
 * 护甲诅咒附魔
 * 受到伤害时：
 * - 伤害 ×5（严重增伤）
 * - 无敌时间减少到75%（更容易连续受击）
 */
@AutoRegisterEnchantment(
        id = "daedicar_woe",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentDaedicarWoe extends EnchantmentBase {

    /** 参与计算的等级上限（只用来判断有没有） */
    private static final int LEVEL_CAP = 10;
    /** 受到的伤害倍率 */
    private static final float DAMAGE_MULTIPLIER = 3.0F;
    /** 受伤后无敌时间占最大值的比例（有效保护窗口减半） */
    private static final float INVULNERABLE_RATIO = 0.75F;

    public EnchantmentDaedicarWoe() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        Enchantment daedicarWoe = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDaedicarWoe.class);
        if (daedicarWoe == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(daedicarWoe, armor);
            }
        }

        totalLevel = capLevel(totalLevel, LEVEL_CAP);

        if (totalLevel <= 0) {
            return;
        }

        // 以前是 ×5，和说明写的 300% 不符
        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime * INVULNERABLE_RATIO);
        evt.setAmount(evt.getAmount() * DAMAGE_MULTIPLIER);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isCurse() {
        return true;
    }
}