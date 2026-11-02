package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 黄金律法附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.3：接入律法碑特效</h3>
 * <p>
 * 胸前立起一面矩形黄金律法碑 + 碑面刻纹 + 脚下外扩金环。
 * <b>只挂在 {@link #onLivingAttack} 的两个 {@code setCanceled(true)} 分支上</b>，
 * 即「免疫真正生效」的那一刻。
 * </p>
 * <p>
 * <b>刻意不给 {@link #onLivingHurt} 里的增伤 / 减伤加视觉</b>——那两个是常驻数值、
 * 每次交火都在生效，加了会变成一个持续闪烁的噪音源。而免疫是离散事件，
 * 且此前完全不可见（伤害被取消，看起来和「没打中」一模一样），
 * 这正是最需要反馈的地方。
 * </p>
 * <p>
 * 「矩形碑面」是全模组唯一的矩形图形，与同为金色的祈祷一击（竖直光柱 + 十字）、
 * 神圣净化（三维十字）、黄金树祝福（根须 + 落叶）在形状上彻底分开。
 * </p>
 * <p>
 * 1.12.2 版保留：「创造模式判定」看伤害源能不能伤到创造模式（{@code canHarmInCreative()}，虚空、/kill 这类），
 * 这类伤害既不增减也不免疫；1.20.1 看的是伤害是不是来自创造模式玩家。
 * </p>
 *
 * @version 2.3
 */
@AutoRegisterEnchantment(id = "golden_law", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentGoldenLaw extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.golden_law.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "golden_law";

    /**
     * 固定的额外伤害比例
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_BONUS =
            EnchantmentValues.define(VALUE_ID, "base_bonus",
                    0.15D, 0.0D, 5.0D);

    /**
     * 按已损失生命比例提供的额外伤害系数
     * <p>默认 0.45，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MISSING_HEALTH_BONUS =
            EnchantmentValues.define(VALUE_ID, "missing_health_bonus",
                    0.45D, 0.0D, 5.0D);

    /**
     * 受到伤害的倍率
     * <p>默认 0.85，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.85D, 0.0D, 2.0D);

    /**
     * 触发免疫所需的伤害占当前生命比例上限
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle IMMUNITY_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "immunity_threshold",
                    0.15D, 0.0D, 1.0D);

    /**
     * 免疫的冷却时间（tick）
     * <p>默认 100，允许范围 1 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle IMMUNITY_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "immunity_cooldown",
                    100, 1, 12000);

    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGoldenLaw() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override protected boolean checkCompatibility(Enchantment ench) {
        if (packageNameOf(ench).contains("law") && !ench.equals(this)) return false;
        return super.checkCompatibility(ench);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        // 1.12.2 版保留：按 canHarmInCreative 判断（见类注释）
        if (evt.getEntity().world.isRemote || evt.getSource().canHarmInCreative()) return;
        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) return;

        // 攻击者视角
        if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
            // v2.2：怪物附魔触发开关（攻击者视角）
            if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getHeldItemMainhand();
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getEnchantmentLevel(goldenLaw, heldItem);
                    if (level > 0) {
                        float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
                        evt.setAmount(evt.getAmount()
                                + evt.getAmount() * (float) BASE_BONUS.get()
                                + evt.getAmount() * (float) MISSING_HEALTH_BONUS.get() * (1 - healthRatio));
                    }
                }
            }
        }

        // 受击者视角
        EntityLivingBase victim = evt.getEntityLiving();

        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getHeldItemMainhand();
        if (!heldItem.isEmpty()) {
            int level = EnchantmentHelper.getEnchantmentLevel(goldenLaw, heldItem);
            if (level > 0) evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        // 1.12.2 版保留：按 canHarmInCreative 判断（见类注释）
        if (evt.getEntity().world.isRemote || evt.getSource().canHarmInCreative()) return;
        EntityLivingBase holder = evt.getEntityLiving();

        // v2.2：怪物附魔触发开关（受击者视角，"小伤免疫"非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        UUID uuid = holder.getUniqueID();
        ItemStack mainHand = holder.getHeldItemMainhand();
        if (mainHand.isEmpty()) return;
        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) return;
        int level = EnchantmentHelper.getEnchantmentLevel(goldenLaw, mainHand);
        if (level <= 0) return;
        if (evt.getAmount() <= holder.getHealth() * IMMUNITY_THRESHOLD.get()) {
            evt.setCanceled(true);
            // v2.3：小伤免疫生效
            emitLawTablet(holder, evt.getSource().getImmediateSource());
            return;
        }
        if (!EnchantmentDataManager.isOnCooldown(IMMUNITY_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            EnchantmentDataManager.setCooldown(IMMUNITY_COOLDOWN_KEY, uuid, IMMUNITY_COOLDOWN.getInt());
            // v2.3：每 5 秒一次的完全免疫生效
            emitLawTablet(holder, evt.getSource().getImmediateSource());
        }
    }

    /**
     * 播放黄金律法碑特效。
     * <p>
     * 抽成一个方法是因为免疫有两条互斥的触发路径（小伤免疫 / 冷却免疫），
     * 两处的表现应当完全一致——玩家不需要区分是哪一种免疫救了自己，
     * 只需要知道「这一下被律法挡住了」。
     * </p>
     * <p>
     * 有伤害来源时碑面正对来源方向，读作「挡住了这一下」；
     * 来源为空时（如摔落、窒息这类无实体伤害）退回持有者自身朝向。
     * </p>
     *
     * @param holder 免疫了这次伤害的持有者
     * @param source 伤害的直接来源实体，可为 null
     */
    private static void emitLawTablet(@Nonnull EntityLivingBase holder, @Nullable Entity source) {
        if (!(holder.world instanceof WorldServer)) {
            return;
        }
        WorldServer serverLevel = (WorldServer) holder.world;
        if (source != null) {
            CarianStyleCombatArtEffects.goldenLaw(serverLevel, holder, source);
        } else {
            CarianStyleCombatArtEffects.goldenLaw(serverLevel, holder);
        }
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
