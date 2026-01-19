// 文件：EnchantmentParry.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentParry.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import java.util.UUID;

/**
 * 招架附魔
 * <p>
 * 用盾牌完全格挡攻击后，10tick内攻击可触发增伤（+25% × 等级）
 * 触发后进入40tick冷却
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "parry",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.BREAKABLE,
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentParry extends EnchantmentBase {

    private static final String PARRY_LEVEL_KEY = "parry_level";
    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";

    public EnchantmentParry() {
        super(EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    /**
     * 检测盾牌格挡（LOWEST优先级）
     * 完全格挡后进入招架状态
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt_Shield(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 只有完全格挡（伤害<=0）才触发
        if (evt.getAmount() > 0) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        // 检查是否正在举盾
        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getItemInHand(holder.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment parry = EnchantmentRegistry.getEnchantmentByClass(EnchantmentParry.class);
        if (parry == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(parry, activeItem);
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
    public static void onLivingHurt_Attack(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
        UUID uuid = attacker.getUUID();

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
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}