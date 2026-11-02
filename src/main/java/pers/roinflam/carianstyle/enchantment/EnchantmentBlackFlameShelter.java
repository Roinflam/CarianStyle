package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

import javax.annotation.Nonnull;

/**
 * 黑焰庇护附魔
 * <p>v2.1：LivingDamage受击+LivingHeal受治疗入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.2：加 {@value #MAX_EFFECTIVE_LEVEL} 级硬上限，堵住「挨打变回血」</h3>
 *
 * <h4>原来会发生什么</h4>
 * <p>
 * 减伤是 {@code amount * (1 - totalLevel * 0.125f)}，也就是每级 12.5%。
 * 累加等级到 <b>8 级系数正好归零</b>（完全免疫），<b>9 级及以上系数为负</b>。
 * </p>
 * <p>
 * 负系数不是「减伤超过 100% 所以按 0 算」——原版扣血那一步（1.12.2 的
 * {@code EntityLivingBase.damageEntity}）判断的是 {@code damageAmount != 0.0F}，
 * <b>不是 {@code > 0}</b>，所以负值会一路走到 {@code setHealth(health - 负数)}，
 * 结果是<b>挨打反而加血</b>。这不是数值失衡，是符号翻转。
 * </p>
 *
 * <h4>正常途径够不着，但这个模组明确允许超上限</h4>
 * <p>
 * 本附魔是 {@code RARE}（{@link EnchantmentRarity} 表里 maxLevel = 3）且注册为
 * <b>胸甲专属</b>，附魔台与铁砧的上限就是 3 级，减伤 37.5%。
 * </p>
 * <p>
 * 但 {@code ConfigLoader.levelLimit} 这个配置项的存在本身就说明等级会超上限——
 * 它的说明写的是「为某些附魔在超过等级上限后设置限制」，而且<b>默认是关的</b>。
 * 指令发放、NBT 改级、服务器自己的附魔发放系统，任何一条路给出 9 级以上，
 * 这个符号翻转就是实打实会跑到的，而且默认没有任何东西拦着。
 * </p>
 *
 * <h4>为什么是 7 而不是 8</h4>
 * <p>
 * 8 级是系数归零、完全免疫。<b>「完全免疫物理伤害」本身就已经是个坏状态</b>——
 * 它不会导致符号翻转，但会让这件胸甲变成无敌装。卡在 7 级（减伤 87.5%）
 * 是「离出问题最近但仍然正常」的那一级：伤害恒为正数，效果依然极强，
 * 但打得死。
 * </p>
 *
 * <h4>为什么硬上限与 levelLimit 并存</h4>
 * <ul>
 *     <li>{@code ConfigLoader.levelLimit} 是<b>平衡</b>开关，服主可以关掉，
 *         全模组统一口径钳到 10 级；</li>
 *     <li>{@link #MAX_EFFECTIVE_LEVEL} 是<b>正确性</b>下限，
 *         防的是符号翻转这种「怎么配都不该发生」的事，因此<b>无条件生效</b>、
 *         不看任何配置。</li>
 * </ul>
 * <p>
 * 现在 7 &lt; 10，levelLimit 那一段在本附魔上确实不会再改变结果。
 * <b>刻意保留</b>：一是与模组里其它附魔的写法保持一致，二是万一将来调了
 * 每级 12.5% 这个系数、硬上限跟着上移，那段就又有意义了。
 * </p>
 *
 * <h4>本次<b>没有</b>动治疗削减那一侧</h4>
 * <p>
 * 治疗削减是 {@code 1 - totalLevel * 0.25f}，每级 25%——<b>4 级就归零</b>。
 * 但它不会符号翻转：{@code heal} 里是「小于等于 0 就不回血」，
 * 负值只是不回血，不会掉血。这是个平衡问题而非正确性问题，故保持原样。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "black_flame_shelter",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentBlackFlameShelter extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.black_flame_shelter.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "black_flame_shelter";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级对物理伤害的减免比例（魔法伤害和无视护甲的伤害不减；键名沿用旧的 melee，改了老配置就对不上）
     * <p>默认 0.125，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle MELEE_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "melee_reduction_per_level",
                    0.125D, 0.0D, 0.5D);

    /**
     * 每级的治疗削减比例（代价；键名沿用旧的 projectile，改了老配置就对不上）
     * <p>默认 0.25，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle PROJECTILE_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "projectile_reduction_per_level",
                    0.25D, 0.0D, 0.5D);

    /**
     * 参与计算的累加等级硬上限。
     * <p>
     * 减伤为每级 12.5%，8 级系数归零、9 级起为负并导致<b>伤害反向变成治疗</b>
     * （详见类注释）。卡在 {@value} 级 = 减伤 87.5%，是符号翻转前的最后一级。
     * </p>
     * <p>
     * <b>无条件生效</b>，不受 {@code ConfigLoader.levelLimit} 影响——
     * 这是正确性约束，不是平衡开关。
     * </p>
     * <p>
     * <b>public 的原因：</b>{@code CarianStyleConditionDisplay} 的 HUD 要显示当前减伤百分比，
     * 必须用与这里完全相同的上限，否则会出现「HUD 显示 125%、实际只有 87.5%」。
     * 在那边复制一份 {@code 7} 的字面量是不安全的——改了这里而那边没跟上，
     * HUD 会安静地开始说谎。
     * </p>
     */
    public static final int MAX_EFFECTIVE_LEVEL = 7;

    public EnchantmentBlackFlameShelter() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment blackFlameShelter = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameShelter.class);
        if (blackFlameShelter == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameShelter, armor);
            }
        }
        totalLevel = applyLevelLimit(blackFlameShelter, totalLevel, LEVEL_CAP.getInt());
        // v2.2：无条件硬上限，防止减伤系数归零（8级）乃至变负（9级+）。
        // 与上面的 levelLimit 是两回事：那个是可关闭的平衡开关，这个是正确性约束。
        // 详见 MAX_EFFECTIVE_LEVEL 与类注释。
        totalLevel = Math.min(totalLevel, MAX_EFFECTIVE_LEVEL);
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (DamageSourceUtil.isMagicDamage(damageSource) ||
                DamageTags.is(damageSource, DamageTags.BYPASSES_ARMOR)) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角，物理减伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * (1 - totalLevel * (float) MELEE_REDUCTION_PER_LEVEL.get()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受治疗者视角，治疗削减）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * (1 - totalLevel * (float) PROJECTILE_REDUCTION_PER_LEVEL.get()));
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
