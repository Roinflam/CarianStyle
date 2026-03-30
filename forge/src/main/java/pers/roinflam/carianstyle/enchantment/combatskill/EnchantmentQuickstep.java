package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;

/**
 * 快步附魔
 * <p>
 * 血量越低速度越快
 * 速度等级 = (损失血量百分比) / 5 × 附魔总等级
 * </p>
 * <p>
 * 性能优化记录：
 * - 每4tick检测一次（而非每tick），给予6tick的duration确保无缝衔接
 * - 提前检查附魔等级，避免无附魔玩家执行后续计算
 * - 速度等级为0时跳过apply调用
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "quickstep",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_FEET,
        slots = {EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentQuickstep extends EnchantmentBase {

    public EnchantmentQuickstep() {
        super(EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    /**
     * 每4tick检测一次血量比例并调整速度
     * <p>
     * 优化：从每tick检测改为每4tick检测，duration给6tick确保覆盖间隔
     * 血量变化不需要逐tick精确跟踪，4tick的延迟对玩家体验几乎无感知
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) {
            return;
        }

        // 优化：每4tick检测一次
        if (evt.player.tickCount % 4 != 0) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        Enchantment quickstep = EnchantmentRegistry.getEnchantmentByClass(EnchantmentQuickstep.class);
        if (quickstep == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(quickstep, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - player.getHealth() / player.getMaxHealth();
        int speedLevel = (int) (missingHealthPercent * 100 / 5 * totalLevel);

        // 优化：速度等级为0时不创建实例
        if (speedLevel > 0) {
            // 优化：duration给6tick覆盖4tick间隔，确保效果无缝
            DynamicAttributeManager.apply(player,
                    DynamicAttributes.SPEED_BOOST.createInstance(6, speedLevel - 1));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
