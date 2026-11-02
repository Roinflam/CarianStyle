package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 龙徽大盾附魔
 * <p>
 * 每次受到物理伤害叠一层护盾（隐藏的动态属性 {@code DRAGONCREST_GREATSHIELD}，30 秒内不受击就清空），
 * 叠满 20 层后，之后的物理伤害只受 75%。魔法伤害和无视护甲的伤害不叠层、不减伤。
 * </p>
 * <p>
 * 1.12.2 版：「无视护甲」按 {@code DamageTags.BYPASSES_ARMOR} 判断（1.20.1 是伤害类型标签），
 * 原版的 {@code isUnblockable()} 也算在里面。
 * </p>
 * <p>v2.1：LivingDamage受击者叠盾入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "dragoncrest_greatshield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentDragoncrestGreatshield extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.dragoncrest_greatshield.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dragoncrest_greatshield";

    /**
     * 护盾叠加的最高层数
     * <p>默认 19，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_SHIELD_LEVEL =
            EnchantmentValues.define(VALUE_ID, "max_shield_level",
                    19, 0, 127);

    /**
     * 护盾持续时间（tick）
     * <p>默认 600，允许范围 20 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle SHIELD_DURATION =
            EnchantmentValues.define(VALUE_ID, "shield_duration",
                    600, 20, 12000);

    /**
     * 护盾生效时受到伤害的倍率（越小减伤越多）
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.75D, 0.0D, 1.0D);

    public EnchantmentDragoncrestGreatshield() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (DamageSourceUtil.isMagicDamage(damageSource) ||
                DamageTags.is(damageSource, DamageTags.BYPASSES_ARMOR)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角，物理叠层护盾）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment dragoncrest = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragoncrestGreatshield.class);
        if (dragoncrest == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(dragoncrest, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        int currentAmplifier = DynamicAttributeManager.getAmplifier(victim, DynamicAttributes.DRAGONCREST_GREATSHIELD);
        if (currentAmplifier < 0) {
            DynamicAttributeManager.apply(victim,
                    DynamicAttributes.DRAGONCREST_GREATSHIELD.createInstance(SHIELD_DURATION.getInt(), 0));
        } else if (currentAmplifier < MAX_SHIELD_LEVEL.getInt()) {
            DynamicAttributeManager.apply(victim,
                    DynamicAttributes.DRAGONCREST_GREATSHIELD.createInstance(SHIELD_DURATION.getInt(), currentAmplifier + 1));
        } else {
            DynamicAttributeManager.apply(victim,
                    DynamicAttributes.DRAGONCREST_GREATSHIELD.createInstance(SHIELD_DURATION.getInt(), MAX_SHIELD_LEVEL.getInt()));
            evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}
