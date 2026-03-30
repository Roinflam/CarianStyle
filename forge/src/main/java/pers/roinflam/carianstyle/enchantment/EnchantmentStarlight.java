package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;

/** 星光附魔 - 优化: LivingTickEvent -> PlayerTickEvent @version 2.1 */
@AutoRegisterEnchantment(id = "starlight", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
@Mod.EventBusSubscriber
public class EnchantmentStarlight extends EnchantmentBase {
    public EnchantmentStarlight() { super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}); }

    /** 优化：从LivingTickEvent改为PlayerTickEvent，怪物不触发 */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;
        Player entity = evt.player;
        Enchantment starlight = EnchantmentRegistry.getEnchantmentByClass(EnchantmentStarlight.class);
        if (starlight == null) return;
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(starlight, armor);
        }
        if (totalLevel <= 0) return;
        Level world = entity.level();
        int blockX = Mth.floor(entity.getX());
        int blockY = Mth.floor(entity.getY() - 0.2D);
        int blockZ = Mth.floor(entity.getZ());
        BlockPos blockPos = new BlockPos(blockX, blockY + 1, blockZ);
        if (!world.isEmptyBlock(blockPos)) return;
        if (world.getBlockEntity(blockPos) instanceof MoveLight moveLight) { moveLight.retime(); return; }
        else if (world.getBlockState(blockPos).getBlock() instanceof HideLight) { world.removeBlock(blockPos, false); }
        world.setBlock(blockPos, CarianStyleBlocks.HIDE_LIGHT.get().defaultBlockState(), 3);
    }

    @Override public int getMinCost(int l) { return (int)((23 + (l - 1) * 9) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
