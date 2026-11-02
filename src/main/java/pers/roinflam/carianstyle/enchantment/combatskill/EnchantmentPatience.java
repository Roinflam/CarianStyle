package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * 忍耐附魔
 * <p>
 * 主手拿着时，被生物打中会把这一下伤害的 10% × 等级攒起来（至多最大生命 40% × 等级），
 * 下一次攻击把攒下的全部作为额外伤害打出去。
 * </p>
 * <p>v2.2：受击累积监听器接入怪物附魔触发开关。
 * onHurtAsAttackerLowest 走中央事件分发器，已被 scanEntity 入口拦截。
 * onLivingDeath 是清理类，不影响行为，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "patience",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentPatience extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "patience";

    /**
     * 每级可累积伤害的最大生命占比上限
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MAX_ACCUMULATED_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_accumulated_per_level",
                    0.4D, 0.0D, 5.0D);

    /**
     * 每级把受到伤害转为累积值的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ACCUMULATE_RATE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "accumulate_rate_per_level",
                    0.1D, 0.0D, 2.0D);

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

    /**
     * 受击时累积能量
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamageStatic(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment patience = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPatience.class);
        if (patience == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(patience, heldItem);
        level = decayLevel(patience, level);

        if (level <= 0) {
            return;
        }

        UUID uuid = victim.getUniqueID();
        Float current = EnchantmentDataManager.getData(PATIENCE_DATA_KEY, uuid);
        float accumulated = current != null ? current : 0f;

        float maxAccumulated = victim.getMaxHealth() * level * (float) MAX_ACCUMULATED_PER_LEVEL.get();
        accumulated = Math.min(accumulated + evt.getAmount() * level * (float) ACCUMULATE_RATE_PER_LEVEL.get(), maxAccumulated);

        EnchantmentDataManager.setData(PATIENCE_DATA_KEY, uuid, accumulated);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        // 清理类，无需开关检查
        EnchantmentDataManager.removeData(PATIENCE_DATA_KEY, evt.getEntityLiving().getUniqueID());
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
