package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.Reference;
import javax.annotation.Nonnull;

/**
 * 黄金粪金龟附魔 - 修复: getUsedItemHand -> EnumHand.MAIN_HAND @version 2.1
 * <p>
 * 用主手武器打死的生物多掉 30% × 等级的经验。
 * </p>
 */
@AutoRegisterEnchantment(id = "golden_dung_turtle", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentGoldenDungTurtle extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.golden_dung_turtle.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "golden_dung_turtle";

    /**
     * 每级额外掉落的经验比例
     * <p>默认 0.3，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle EXP_BONUS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "exp_bonus_per_level",
                    0.3D, 0.0D, 10.0D);

    public EnchantmentGoldenDungTurtle() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @SubscribeEvent
    public static void onLivingExperienceDrop(@Nonnull LivingExperienceDropEvent evt) {
        if (evt.getEntityLiving().world.isRemote) return;
        EntityPlayer player = evt.getAttackingPlayer();
        if (player == null) return;
        // 修复：使用主手
        ItemStack heldItem = player.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment goldenDungTurtle = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenDungTurtle.class);
        if (goldenDungTurtle == null) return;
        int level = EnchantmentHelper.getEnchantmentLevel(goldenDungTurtle, heldItem);
        level = applyLevelLimit(goldenDungTurtle, level, 10);
        if (level <= 0) return;
        evt.setDroppedExperience(evt.getDroppedExperience() + (int) (evt.getDroppedExperience() * level * EXP_BONUS_PER_LEVEL.get()));
    }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
