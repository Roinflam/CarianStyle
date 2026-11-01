package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 招架附魔
 *
 * 举盾时正面受到近战攻击后，10tick内攻击可触发增伤（+25% × 等级）
 * 触发后进入40tick冷却
 * <p>
 * 进入招架改在攻击事件里判断：举着本附魔的盾、攻击者在正前方就进入，不要求这一下真的被格挡掉
 * （举盾前几 tick、盾被斧头打禁用时也算）。以前要等受伤事件里伤害被盾牌减到 0 才进入。
 * 招架状态 10 tick 后自己过期；以前窗口过了没反击，状态要等下一次攻击才清，期间拦住新的招架。
 * 只能附在盾牌上（以前所有可损坏物品都能附）。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "parry",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentParry extends EnchantmentBase {

    private static final String PARRY_LEVEL_KEY = "parry_level";
    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";
    // 招架窗口（tick）
    private static final int WINDOW_TICKS = 10;
    // 每级反击增伤比例
    private static final float BONUS_DAMAGE_PER_LEVEL = 0.25F;
    // 反击命中后的冷却（tick）
    private static final int COOLDOWN_TICKS = 40;

    public EnchantmentParry() {
        super(CarianStyleEnchantments.SHIELD, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    /**
     * 举盾时正面受到近战攻击，进入招架状态
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();
        if (shouldBlockMobTrigger(holder, false)) {
            return;
        }
        UUID uuid = holder.getUniqueID();

        // 检查是否正在举盾
        if (!holder.isHandActive()) {
            return;
        }

        ItemStack activeItem = holder.getActiveItemStack();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        Enchantment parry = EnchantmentRegistry.getEnchantmentByClass(EnchantmentParry.class);
        if (parry == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(parry, activeItem);
        if (level <= 0) {
            return;
        }

        // 检查是否在冷却中或已有招架状态
        if (EnchantmentDataManager.isOnCooldown(PARRY_COOLDOWN_KEY, uuid)
                || EnchantmentDataManager.getData(PARRY_LEVEL_KEY, uuid) != null) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        if (!isAttackFromFront(holder, attacker)) {
            return;
        }

        // 进入招架状态，窗口一过自己过期
        EnchantmentDataManager.setData(PARRY_LEVEL_KEY, uuid, level, WINDOW_TICKS);
    }

    /**
     * 招架窗口内攻击时增伤（LOW优先级）
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }
        UUID uuid = attacker.getUniqueID();

        Integer parryLevel = EnchantmentDataManager.getData(PARRY_LEVEL_KEY, uuid);
        if (parryLevel == null || parryLevel <= 0) {
            return;
        }

        // 增伤：+25% × 等级
        evt.setAmount(evt.getAmount() + evt.getAmount() * parryLevel * BONUS_DAMAGE_PER_LEVEL);

        // 清除招架状态，进入冷却
        EnchantmentDataManager.removeData(PARRY_LEVEL_KEY, uuid);
        EnchantmentDataManager.setCooldown(PARRY_COOLDOWN_KEY, uuid, COOLDOWN_TICKS);
    }

    /**
     * 攻击者是不是在持有者正前方（水平方向上与持有者朝向的点积为正）
     */
    private static boolean isAttackFromFront(@Nonnull EntityLivingBase defender, @Nonnull EntityLivingBase attacker) {
        double dx = attacker.posX - defender.posX;
        double dz = attacker.posZ - defender.posZ;

        double yaw = Math.toRadians(defender.rotationYaw);
        double defenderDirX = -Math.sin(yaw);
        double defenderDirZ = Math.cos(yaw);

        return dx * defenderDirX + dz * defenderDirZ > 0;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}