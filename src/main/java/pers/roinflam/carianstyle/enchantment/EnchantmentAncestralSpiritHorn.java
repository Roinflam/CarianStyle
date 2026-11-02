package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.util.DamageSource;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 先祖之角附魔
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "ancestral_spirit_horn",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class,
                EnchantmentBlackFlameShelter.class
        }
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentAncestralSpiritHorn extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.ancestral_spirit_horn.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "ancestral_spirit_horn";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 受到伤害的倍率（越小减伤越多）
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.75D, 0.0D, 1.0D);

    /**
     * 每级把受到伤害转为持续治疗的比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.05D, 0.0D, 2.0D);

    public EnchantmentAncestralSpiritHorn() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment ancestralSpiritHorn = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncestralSpiritHorn.class);
        if (ancestralSpiritHorn == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(ancestralSpiritHorn, armor);
            }
        }
        totalLevel = applyLevelLimit(ancestralSpiritHorn, totalLevel, LEVEL_CAP.getInt());
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (DamageTags.is(damageSource, DamageTags.BYPASSES_INVULNERABILITY) ||
                !DamageSourceUtil.isMagicDamage(damageSource)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角，魔法减伤+持续回血）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());

        float healPerTick = evt.getAmount() * totalLevel * (float) HEAL_PER_LEVEL.get() / 20;

        new SynchronizationTask(10, 10) {
            private int tick = 0;

            @Override
            public void run() {
                tick += 10;
                if (tick > 200 || !holder.isEntityAlive()) {
                    this.cancel();
                    return;
                }
                holder.heal(healPerTick);
            }
        }.start();
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
