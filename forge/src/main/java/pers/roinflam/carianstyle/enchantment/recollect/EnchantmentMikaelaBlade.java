package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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

/** 米凯拉之刃附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "mikaela_blade", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentMikaelaBlade extends EnchantmentBase {
    private static final String COMBO_COUNT_KEY = "mikaela_blade_combo";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentMikaelaBlade() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        Enchantment mikaelaBlade = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMikaelaBlade.class);
        if (mikaelaBlade == null) return;
        if (evt.getSource().getDirectEntity() instanceof LivingEntity attacker) {
            ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(mikaelaBlade, heldItem);
                if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                if (level > 0) {
                    UUID uuid = attacker.getUUID();
                    int combo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, uuid);
                    evt.setAmount(evt.getAmount() * 0.4f + evt.getAmount() * combo * 0.2f);
                    EnchantmentDataManager.setCounter(COMBO_COUNT_KEY, uuid, combo + 1, 40);
                }
            }
        }
        LivingEntity victim = evt.getEntity();
        int victimCombo = EnchantmentDataManager.getCounter(COMBO_COUNT_KEY, victim.getUUID());
        if (victimCombo > 0) evt.setAmount(evt.getAmount() + evt.getAmount() * victimCombo * 0.1f);
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
