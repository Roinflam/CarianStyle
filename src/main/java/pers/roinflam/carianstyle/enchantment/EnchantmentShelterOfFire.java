package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

/**
 * 火焰庇护附魔
 *
 * 护甲附魔，着火时减伤并回血
 * 着火时：
 * - 受到伤害减少 2% × 等级（50级时完全免疫）
 * - 每秒恢复 0.1% × 等级 最大生命值
 */
@AutoRegisterEnchantment(
        id = "shelter_of_fire",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentShelterOfFire extends EnchantmentBase {

    public EnchantmentShelterOfFire() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 着火时减伤
     * 由于需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // 必须着火
        if (EntityUtil.getFire(victim) <= 0) {
            return;
        }

        Enchantment shelterOfFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentShelterOfFire.class);
        if (shelterOfFire == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(shelterOfFire, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 减伤 2% × 等级（50级时完全免疫）
        if (totalLevel * 0.02 >= 1) {
            evt.setCanceled(true);
        } else {
            evt.setAmount(evt.getAmount() - evt.getAmount() * totalLevel * 0.02f);
        }
    }

    /**
     * 着火时回血
     * 由于需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;

        // 必须着火
        if (EntityUtil.getFire(player) <= 0) {
            return;
        }

        if (!player.isEntityAlive()) {
            return;
        }

        Enchantment shelterOfFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentShelterOfFire.class);
        if (shelterOfFire == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(shelterOfFire, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 每tick恢复 0.1% × 等级 / 20 的最大生命值
        player.heal(player.getMaxHealth() * totalLevel * 0.001f / 20);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}