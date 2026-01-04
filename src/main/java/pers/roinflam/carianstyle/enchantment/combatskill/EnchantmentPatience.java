package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 忍耐附魔
 *
 * 受击时累积能量（累积值 = 伤害 × 等级 × 0.1，上限 = 最大血量 × 等级 × 0.4）
 * 攻击时释放累积的能量作为额外伤害
 */
@AutoRegisterEnchantment(
        id = "patience",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentPatience extends EnchantmentBase {

    private static final String PATIENCE_DATA_KEY = "patience_accumulated";

    public EnchantmentPatience() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        UUID uuid = attacker.getUniqueID();

        Float accumulated = EnchantmentDataManager.getData(PATIENCE_DATA_KEY, uuid);
        if (accumulated != null && accumulated > 0) {
            ctx.addDamage(accumulated);
            EnchantmentDataManager.removeData(PATIENCE_DATA_KEY, uuid);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamageStatic(@Nonnull net.minecraftforge.event.entity.living.LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
            return;
        }

        Enchantment patience = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPatience.class);
        if (patience == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                patience,
                victim.getHeldItem(victim.getActiveHand()));

        if (level <= 0) {
            return;
        }

        UUID uuid = victim.getUniqueID();
        Float current = EnchantmentDataManager.getData(PATIENCE_DATA_KEY, uuid);
        float accumulated = current != null ? current : 0f;

        float maxAccumulated = victim.getMaxHealth() * level * 0.4f;
        accumulated = Math.min(accumulated + evt.getAmount() * level * 0.1f, maxAccumulated);

        EnchantmentDataManager.setData(PATIENCE_DATA_KEY, uuid, accumulated);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        EnchantmentDataManager.removeData(PATIENCE_DATA_KEY, evt.getEntityLiving().getUniqueID());
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }
}