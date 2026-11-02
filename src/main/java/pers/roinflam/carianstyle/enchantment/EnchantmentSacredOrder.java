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
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 神圣秩序附魔
 * <p>
 * 穿着时无法被治疗，改靠伤害吸收（黄心）续命：
 * - 每条命第一次进入世界时获得等于最大生命的吸收；
 * - 击杀生物时吸收增加最大生命的 10%，最多叠到最大生命的 3 倍；
 * - 身上有吸收时受到的伤害降低 25%，并把剩余吸收量的 5% 反弹给攻击者；
 * - 身上有吸收时造成的伤害提高 50%。
 * </p>
 * <p>v2.1：onLivingDeath（击杀者）+ onLivingDamage（双向）+ onLivingHeal（受治疗者）入口
 * 接入怪物附魔触发开关。EntityJoinWorld 是恢复初始吸收盾，属于状态恢复而非"触发"，无需检查。</p>
 * <p>v2.2：持盾减伤挪到 LivingHurtEvent。LivingDamageEvent 发出时原版已经扣完吸收盾了，
 * 盾够厚时事件里的伤害是 0，盾被打穿时盾量又是 0，这个减伤基本不生效。
 * 初始吸收盾改成每条命只给一次，以前没盾时切维度、重新登录就能反复刷满。</p>
 * <p>
 * 1.12.2 版：实体持久数据是 {@code getEntityData()}（1.20.1 是 {@code getPersistentData()}）；
 * {@code EntityJoinWorldEvent} 在区块重新加载时也会发，领盾标记照样挡得住；
 * 「无视无敌」按 {@code DamageTags.BYPASSES_INVULNERABILITY} 判断（即 {@code canHarmInCreative()}）。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentSacredOrder extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.sacred_order.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sacred_order";

    /**
     * 伤害吸收上限相对最大生命的倍数
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "absorption_cap_ratio",
                    3.0D, 0.0D, 20.0D);

    /**
     * 每次击杀获得的伤害吸收占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_PER_KILL =
            EnchantmentValues.define(VALUE_ID, "absorption_per_kill",
                    0.1D, 0.0D, 2.0D);

    /**
     * 自身有吸收护盾时受到伤害的倍率
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SHIELDED_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "shielded_damage_multiplier",
                    0.75D, 0.0D, 1.0D);

    /**
     * 按目标剩余吸收量反弹给攻击者的伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle REFLECT_RATIO =
            EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.05D, 0.0D, 2.0D);

    /**
     * 攻击带吸收护盾的目标时的伤害倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle VS_SHIELDED_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "vs_shielded_multiplier",
                    1.5D, 0.0D, 10.0D);

    /**
     * 这条命已经领过初始吸收盾的标记，存在实体持久数据里。
     * <p>玩家死亡重生是新实体、标记不带过去（不放进 PERSISTED_NBT_TAG），重生后能再领一次；
     * 切维度、重新登录时标记还在。</p>
     */
    private static final String INITIAL_SHIELD_GIVEN_KEY = "carianstyle_sacred_order_shield_given";

    public EnchantmentSacredOrder() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getTrueSource();

        // v2.1：怪物附魔触发开关（击杀者视角，击杀加吸收盾非濒死）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : killer.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        if (killer.getAbsorptionAmount() < killer.getMaxHealth() * (float) ABSORPTION_CAP_RATIO.get()) {
            float newAbsorption = Math.min(killer.getMaxHealth() * (float) ABSORPTION_CAP_RATIO.get(),
                    killer.getAbsorptionAmount() + killer.getMaxHealth() * (float) ABSORPTION_PER_KILL.get());
            killer.setAbsorptionAmount(newAbsorption);
        }
    }

    /**
     * 持盾时受到的伤害降低。要在护甲、吸收盾结算之前改，所以挂在 LivingHurtEvent。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (victim.getAbsorptionAmount() <= 0
                || DamageTags.is(evt.getSource(), DamageTags.BYPASSES_INVULNERABILITY)
                || EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            return;
        }

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int victimLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                victimLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }
        if (victimLevel > 0) {
            evt.setAmount(evt.getAmount() * (float) SHIELDED_DAMAGE_MULTIPLIER.get());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        // 受击者视角（吸收盾反弹；减伤在 onLivingHurt）
        // v2.1：怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            if (victim.getAbsorptionAmount() > 0) {
                int victimLevel = 0;
                for (ItemStack armor : victim.getArmorInventoryList()) {
                    if (!armor.isEmpty()) {
                        victimLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (victimLevel > 0 && damageSource.getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                    // 原版共享的魔法伤害源，只用不改
                    attacker.attackEntityFrom(DamageSource.MAGIC, victim.getAbsorptionAmount() * (float) REFLECT_RATIO.get());
                }
            }
        }

        // 攻击者视角（持有吸收盾时增伤）
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

            // v2.1：怪物附魔触发开关（攻击者视角）
            if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

            if (attacker.getAbsorptionAmount() > 0) {
                int attackerLevel = 0;
                for (ItemStack armor : attacker.getArmorInventoryList()) {
                    if (!armor.isEmpty()) {
                        attackerLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (attackerLevel > 0) {
                    evt.setAmount(evt.getAmount() * (float) VS_SHIELDED_MULTIPLIER.get());
                }
            }
        }
    }

    /**
     * 每条命第一次进入世界时获得初始吸收盾。
     * 此事件属于状态恢复（玩家重连等），未接入怪物附魔开关。
     * 但若有"启用开关时怪物已经获得过盾"的情况，无法回收，这是设计权衡。
     * <p>1.12.2 版：区块重新加载时也会发这个事件，靠持久数据里的领盾标记只给一次。</p>
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote) {
            return;
        }

        if (!(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase entity = (EntityLivingBase) evt.getEntity();

        if (entity.getAbsorptionAmount() > 0) {
            return;
        }

        // v2.1：状态恢复也加开关检查，避免新生怪物获得吸收盾
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0 && !entity.getEntityData().getBoolean(INITIAL_SHIELD_GIVEN_KEY)) {
            entity.getEntityData().setBoolean(INITIAL_SHIELD_GIVEN_KEY, true);
            entity.setAbsorptionAmount(entity.getMaxHealth());
        }
    }

    /**
     * 从末地回主世界也会换一个玩家实体，这条命没结束，领盾标记要带过去。
     */
    @SubscribeEvent
    public static void onPlayerClone(@Nonnull PlayerEvent.Clone evt) {
        if (!evt.isWasDeath() && evt.getOriginal().getEntityData().getBoolean(INITIAL_SHIELD_GIVEN_KEY)) {
            evt.getEntityPlayer().getEntityData().setBoolean(INITIAL_SHIELD_GIVEN_KEY, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntityLiving().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受治疗者视角，"无法被治疗"也属于附魔触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            evt.setCanceled(true);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
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
