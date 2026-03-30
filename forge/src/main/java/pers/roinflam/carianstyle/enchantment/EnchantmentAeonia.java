package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/** 艾奥尼亚附魔 - 优化: LivingTickEvent -> PlayerTickEvent @version 2.1 */
@AutoRegisterEnchantment(id = "aeonia", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class})
@Mod.EventBusSubscriber
public class EnchantmentAeonia extends EnchantmentBase {
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentAeonia() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();
        if (victim == null || victim.getEffect(CarianStylePotion.SCARLET_ROT.get()) == null) return;
        if (ctx.isHolderPlayer() && ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) return;
        attacker.heal(attacker.getMaxHealth() * 0.1f);
    }

    /** 优化：从LivingTickEvent改为PlayerTickEvent */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;
        if (evt.player.tickCount % 20 != 0) return;
        Player holder = evt.player;
        ItemStack heldItem = holder.getMainHandItem();
        if (heldItem.isEmpty()) return;
        Enchantment aeonia = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
        if (aeonia == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(aeonia, heldItem);
        if (level > 0) holder.addEffect(new MobEffectInstance(CarianStylePotion.SCARLET_ROT.get(), 21, 0));
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
