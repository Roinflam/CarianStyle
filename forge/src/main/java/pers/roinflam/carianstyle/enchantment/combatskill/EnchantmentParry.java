package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import java.util.UUID;

/**
 * 招架附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
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
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，进入招架状态）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        UUID uuid = holder.getUUID();

        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getUseItem();
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

        if (EnchantmentDataManager.isOnCooldown(PARRY_COOLDOWN_KEY, uuid)) {
            return;
        }

        if (EnchantmentDataManager.hasData(PARRY_LEVEL_KEY, uuid)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
        if (!isAttackFromFront(holder, attacker)) {
            return;
        }

        EnchantmentDataManager.setData(PARRY_LEVEL_KEY, uuid, level, 10);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角，招架增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        UUID uuid = attacker.getUUID();

        Integer parryLevel = EnchantmentDataManager.getData(PARRY_LEVEL_KEY, uuid);
        if (parryLevel == null || parryLevel <= 0) {
            return;
        }

        float bonusDamage = evt.getAmount() * parryLevel * 0.25f;
        evt.setAmount(evt.getAmount() + bonusDamage);

        EnchantmentDataManager.removeData(PARRY_LEVEL_KEY, uuid);
        EnchantmentDataManager.setCooldown(PARRY_COOLDOWN_KEY, uuid, 40);
    }

    private static boolean isAttackFromFront(LivingEntity defender, LivingEntity attacker) {
        double dx = attacker.getX() - defender.getX();
        double dz = attacker.getZ() - defender.getZ();

        float yaw = defender.getYRot();
        double defenderDirX = -Math.sin(Math.toRadians(yaw));
        double defenderDirZ = Math.cos(Math.toRadians(yaw));

        double dot = dx * defenderDirX + dz * defenderDirZ;

        return dot > 0;
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
