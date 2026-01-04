package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 黄金粪金龟附魔
 *
 * 武器附魔，增加经验掉落
 * 击杀生物时：
 * - 经验掉落增加 30% × 等级
 */
@AutoRegisterEnchantment(
        id = "golden_dung_turtle",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentGoldenDungTurtle extends EnchantmentBase {

    public EnchantmentGoldenDungTurtle() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 击杀生物时增加经验掉落
     * 由于 LivingExperienceDropEvent 没有模板方法，保留静态监听器
     */
    @SubscribeEvent
    public static void onLivingExperienceDrop(@Nonnull LivingExperienceDropEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityPlayer player = evt.getAttackingPlayer();
        if (player == null) {
            return;
        }

        if (player.getHeldItem(player.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment goldenDungTurtle = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenDungTurtle.class);
        if (goldenDungTurtle == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                goldenDungTurtle,
                player.getHeldItem(player.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 经验增加 30% × 等级
        int bonusExp = (int) (evt.getDroppedExperience() * level * 0.3);
        evt.setDroppedExperience(evt.getDroppedExperience() + bonusExp);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}