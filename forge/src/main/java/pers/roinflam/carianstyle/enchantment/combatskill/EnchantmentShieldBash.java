package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 盾击附魔
 * <p>
 * 举盾格挡时被攻击，将攻击者击退（击退强度 = 等级 × 0.25）
 * </p>
 * <p>
 * 修复记录：构造函数 EnchantmentCategory.BREAKABLE → CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD")
 * </p>
 * <p>v2.2：击退后播放「前向扇形冲击波」自绘特效</p>
 *
 * <h3>v2.2 为什么朝向取持有者而不是攻击者</h3>
 * <p>
 * 冲击波是从<b>盾面</b>推出去的，所以扇形的中轴必须是持有者的朝向。
 * 用攻击者的朝向会让扇形反过来指向自己身后，读作「我被推了」而不是「我把他推开了」——
 * 语义正好相反。
 * </p>
 * <p>
 * 这也是它与狮子斩 / 二连斩 / 箭步上砍取法不同的地方：那三个是「打在别人身上」，
 * 这个是「从自己身上推出去」。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "shield_bash",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
public class EnchantmentShieldBash extends EnchantmentBase {

    public EnchantmentShieldBash() {
        // 修复：BREAKABLE → SHIELD自定义类型
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    @Override
    protected void onHurtAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        // 攻击者必须是生物实体（排除箭矢等投射物）
        if (ctx.getDamageSource() == null ||
                !(ctx.getDamageSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = ctx.getHolder();
        LivingEntity attacker = (LivingEntity) ctx.getDamageSource().getDirectEntity();

        // 检查是否正在举盾
        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getItemInHand(holder.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        // 确保当前附魔物品是正在使用的盾牌
        if (!ctx.getEnchantedItem().equals(activeItem)) {
            return;
        }

        // 击退攻击者
        double x = holder.getX() - attacker.getX();
        double z = holder.getZ() - attacker.getZ();
        attacker.knockback(level * 0.25f, x, z);

        // ⭐ v2.2：播放「前向扇形冲击波」自绘特效。
        // 位置与朝向都取持有者——冲击波是从盾面推出去的（详见类注释）
        if (holder.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.shieldBash(serverLevel, holder);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
