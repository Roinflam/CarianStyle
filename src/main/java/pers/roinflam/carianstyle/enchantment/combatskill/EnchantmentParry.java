package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
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
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 招架附魔
 *
 * 用盾牌完全格挡攻击后，10tick内攻击可触发增伤（+25% × 等级）
 * 触发后进入40tick冷却
 */
@AutoRegisterEnchantment(
        id = "parry",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentParry extends EnchantmentBase {

    private static final String PARRY_LEVEL_KEY = "parry_level";
    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";

    public EnchantmentParry() {
        super(EnumEnchantmentType.BREAKABLE, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    /**
     * 检测盾牌格挡（LOWEST优先级）
     * 完全格挡后进入招架状态
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt_Shield(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 只有完全格挡（伤害<=0）才触发
        if (evt.getAmount() > 0) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        UUID uuid = holder.getUniqueID();

        // 检查是否正在举盾
        if (!holder.isHandActive()) {
            return;
        }

        ItemStack activeItem = holder.getHeldItem(holder.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment parry = EnchantmentRegistry.getEnchantmentByClass(EnchantmentParry.class);
        if (parry == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(parry, activeItem);
        if (level <= 0) {
            return;
        }

        // 检查是否在冷却中或已有招架状态
        if (EnchantmentDataManager.isOnCooldown(PARRY_COOLDOWN_KEY, uuid)) {
            return;
        }

        Integer existingLevel = EnchantmentDataManager.getData(PARRY_LEVEL_KEY, uuid);
        if (existingLevel != null) {
            return;
        }

        // 进入招架状态（10tick窗口）
        EnchantmentDataManager.setData(PARRY_LEVEL_KEY, uuid, level);
        EnchantmentDataManager.setCooldown(PARRY_LEVEL_KEY, uuid, 10);
    }

    /**
     * 攻击时检查招架状态并增伤（LOW优先级）
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt_Attack(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        UUID uuid = attacker.getUniqueID();

        // 检查招架状态
        Integer parryLevel = EnchantmentDataManager.getData(PARRY_LEVEL_KEY, uuid);
        if (parryLevel == null || parryLevel <= 0) {
            return;
        }

        // 检查是否还在招架窗口内
        if (!EnchantmentDataManager.isOnCooldown(PARRY_LEVEL_KEY, uuid)) {
            EnchantmentDataManager.removeData(PARRY_LEVEL_KEY, uuid);
            return;
        }

        // 增伤：+25% × 等级
        evt.setAmount(evt.getAmount() + evt.getAmount() * parryLevel * 0.25f);

        // 清除招架状态，进入冷却
        EnchantmentDataManager.removeData(PARRY_LEVEL_KEY, uuid);
        EnchantmentDataManager.clearCooldown(PARRY_LEVEL_KEY, uuid);
        EnchantmentDataManager.setCooldown(PARRY_COOLDOWN_KEY, uuid, 40);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}