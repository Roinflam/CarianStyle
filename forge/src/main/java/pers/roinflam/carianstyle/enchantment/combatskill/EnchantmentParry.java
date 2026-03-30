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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import java.util.UUID;

/**
 * 招架附魔
 * <p>
 * 用盾牌完全格挡攻击后，10tick内攻击可触发增伤（+25% × 等级）
 * 触发后进入40tick冷却
 * </p>
 * <p>
 * 修复记录：
 * - 修复招架状态检测bug：原代码用isOnCooldown检查通过setData存入的数据，
 *   两个是不同的存储系统，导致重复触发检查永远失败。
 *   现改为统一使用hasData/getData系统。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
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

    /** 招架状态数据键（存储等级值，通过setData/getData管理） */
    private static final String PARRY_LEVEL_KEY = "parry_level";
    /** 招架冷却键（通过setCooldown/isOnCooldown管理） */
    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";

    public EnchantmentParry() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    /**
     * 检测盾牌格挡（使用LivingAttackEvent更可靠）
     * 当玩家举盾受击时进入招架状态
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 必须是近战攻击
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        // 检查是否正在举盾
        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        // 检查盾牌是否有招架附魔
        Enchantment parry = EnchantmentRegistry.getEnchantmentByClass(EnchantmentParry.class);
        if (parry == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(parry, activeItem);
        if (level <= 0) {
            return;
        }

        // 检查是否在冷却中
        if (EnchantmentDataManager.isOnCooldown(PARRY_COOLDOWN_KEY, uuid)) {
            return;
        }

        // 修复：使用hasData检查招架状态是否已存在（原代码误用isOnCooldown检查setData的数据）
        if (EnchantmentDataManager.hasData(PARRY_LEVEL_KEY, uuid)) {
            return;
        }

        // 检查伤害是否来自正前方（盾牌只能格挡前方攻击）
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
        if (!isAttackFromFront(holder, attacker)) {
            return;
        }

        // 进入招架状态（10tick窗口 = 0.5秒）
        EnchantmentDataManager.setData(PARRY_LEVEL_KEY, uuid, level, 10);
    }

    /**
     * 攻击时检查招架状态并增伤
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
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

        // 增伤：+25% × 等级
        float bonusDamage = evt.getAmount() * parryLevel * 0.25f;
        evt.setAmount(evt.getAmount() + bonusDamage);

        // 清除招架状态，进入冷却（40tick = 2秒）
        EnchantmentDataManager.removeData(PARRY_LEVEL_KEY, uuid);
        EnchantmentDataManager.setCooldown(PARRY_COOLDOWN_KEY, uuid, 40);
    }

    /**
     * 判断攻击是否来自正前方
     * （盾牌只能格挡前方的攻击）
     *
     * @param defender 防御者
     * @param attacker 攻击者
     * @return 是否来自前方
     */
    private static boolean isAttackFromFront(LivingEntity defender, LivingEntity attacker) {
        // 计算攻击者相对于防御者的方向
        double dx = attacker.getX() - defender.getX();
        double dz = attacker.getZ() - defender.getZ();

        // 计算防御者的朝向
        float yaw = defender.getYRot();
        double defenderDirX = -Math.sin(Math.toRadians(yaw));
        double defenderDirZ = Math.cos(Math.toRadians(yaw));

        // 计算点积（判断是否在前方120度范围内）
        double dot = dx * defenderDirX + dz * defenderDirZ;

        // dot > 0 表示在前方
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
