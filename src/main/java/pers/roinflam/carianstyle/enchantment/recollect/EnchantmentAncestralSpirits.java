package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

import javax.annotation.Nonnull;

/**
 * 先祖之魂附魔
 * <p>受到魔法伤害时减免，每次受伤后 10 秒内按已损失生命持续回复。</p>
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "ancestral_spirits",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentAncestralSpirits extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.ancestral_spirits.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "ancestral_spirits";

    /**
     * 触发时受到伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.5D, 0.0D, 1.0D);

    /**
     * 持续治疗的总时长（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle HEAL_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "heal_duration_ticks",
                    200, 20, 6000);

    /**
     * 每秒回复已损失生命的比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.05D, 0.0D, 2.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAncestralSpirits() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 1.12.2 的「无视无敌」就是 canHarmInCreative（虚空、/kill 这类），DamageTags 按开关推出这个标签
        if (DamageTags.is(evt.getSource(), DamageTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        Enchantment ancestralSpirits = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncestralSpirits.class);
        if (ancestralSpirits == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(ancestralSpirits, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        if (DamageSourceUtil.isMagicDamage(evt.getSource())) {
            evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());
        }

        if (holder.isEntityAlive()) {
            new SynchronizationTask(10, 10) {
                private int tick = 0;

                @Override
                public void run() {
                    tick += 10;
                    if (tick > HEAL_DURATION_TICKS.getInt() || !holder.isEntityAlive()) {
                        this.cancel();
                        return;
                    }
                    // 每次按当时已损失的生命重新算
                    float healPerTick = (holder.getMaxHealth() - holder.getHealth()) * (float) HEAL_RATIO_PER_SECOND.get() / 20;
                    holder.heal(healPerTick);
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
