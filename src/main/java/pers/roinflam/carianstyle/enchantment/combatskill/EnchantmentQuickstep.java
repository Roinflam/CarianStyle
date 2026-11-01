package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 快步附魔
 *
 * 血量越低速度越快
 * 速度等级 = (损失血量百分比) / 5 × 附魔总等级，每级 +1% 移动速度（乘总值），随生命值实时升降
 * <p>
 * 改成直接给玩家挂一个固定 UUID 的移速修饰符，不再借用群星律法也在用的速度药水：
 * 同一种药水只留等级高的那个，以前两者同时生效时是取高、不叠加，现在相乘叠加。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "quickstep",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentQuickstep extends EnchantmentBase {

    // 碎步移速修饰符的固定 UUID
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("4c137fcb-9a4a-49ee-9ffa-affa0313d268");
    private static final String SPEED_MODIFIER_NAME = "enchantment.quickstep";
    // 每多少点已损失生命值百分比提升 1 级
    private static final float SPEED_DIVISOR = 5.0F;
    // 每级移速加成（乘总值）
    private static final double SPEED_PER_LEVEL = 0.01D;
    // 重新结算的间隔（tick）
    private static final int CHECK_INTERVAL_TICKS = 4;
    // 乘总值
    private static final int OPERATION_MULTIPLY_TOTAL = 2;

    public EnchantmentQuickstep() {
        super(EnumEnchantmentType.ARMOR_FEET, new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET});
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        if (player.ticksExisted % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        int speedLevel = 0;
        Enchantment quickstep = EnchantmentRegistry.getEnchantmentByClass(EnchantmentQuickstep.class);
        if (quickstep != null && player.isEntityAlive()) {
            int totalLevel = 0;
            for (ItemStack armor : player.getArmorInventoryList()) {
                if (!armor.isEmpty()) {
                    totalLevel += EnchantmentHelper.getEnchantmentLevel(quickstep, armor);
                }
            }
            if (totalLevel > 0) {
                float missingHealthPercent = 1 - player.getHealth() / player.getMaxHealth();
                speedLevel = (int) (missingHealthPercent * 100 / SPEED_DIVISOR * totalLevel);
            }
        }

        applySpeed(player, speedLevel);
    }

    /**
     * 把移速修饰符改成对应等级；等级为 0 时摘掉。数值没变就不动，免得每次都触发属性同步
     */
    private static void applySpeed(@Nonnull EntityPlayer player, int speedLevel) {
        IAttributeInstance attribute = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(SPEED_MODIFIER_ID);
        double amount = Math.max(speedLevel, 0) * SPEED_PER_LEVEL;
        if (existing != null) {
            if (existing.getAmount() == amount) {
                return;
            }
            attribute.removeModifier(SPEED_MODIFIER_ID);
        }
        if (amount > 0) {
            attribute.applyModifier(new AttributeModifier(SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, amount, OPERATION_MULTIPLY_TOTAL)
                    .setSaved(false));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }
}