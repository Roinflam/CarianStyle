package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
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
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 神圣秩序附魔
 *
 * 护甲附魔，吸收盾系统
 * 效果：
 * - 进入世界时获得100%最大生命值的吸收盾
 * - 击杀敌人时获得10%最大生命值的吸收盾（最多叠加到300%）
 * - 有吸收盾时受到伤害减少25%，并反弹5%吸收盾值的魔法伤害
 * - 有吸收盾时造成伤害增加50%
 * - 无法被治疗
 * <p>
 * 持盾减伤改到 LivingHurtEvent：LivingDamageEvent 发出时吸收盾已经扣完了，盾够厚时伤害是 0，
 * 盾被打穿时盾量又是 0，以前这个减伤基本不生效。初始吸收盾改成每条命只给一次，
 * 以前没盾时切维度、重新登录就能反复刷满。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentSacredOrder extends EnchantmentBase {

    // 吸收盾上限：最大生命值的倍数
    private static final float ABSORPTION_CAP_RATIO = 3.0F;
    // 每次击杀获得的吸收盾：最大生命值的比例
    private static final float ABSORPTION_PER_KILL = 0.1F;
    // 有吸收盾时受到伤害的倍率
    private static final float SHIELDED_DAMAGE_MULTIPLIER = 0.75F;
    // 受击后按剩余吸收盾反弹给攻击者的魔法伤害比例
    private static final float REFLECT_RATIO = 0.05F;
    // 自身有吸收盾时造成伤害的倍率
    private static final float VS_SHIELDED_MULTIPLIER = 1.5F;
    // 这条命已经领过初始吸收盾的标记（存实体数据；死亡重生不带过去，切维度由 Clone 带过去）
    private static final String INITIAL_SHIELD_GIVEN_KEY = "carianstyle_sacred_order_shield_given";

    public EnchantmentSacredOrder() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 击杀敌人时获得吸收盾
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getTrueSource();
        if (shouldBlockMobTrigger(killer, false)) {
            return;
        }

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

        if (killer.getAbsorptionAmount() < killer.getMaxHealth() * ABSORPTION_CAP_RATIO) {
            float newAbsorption = Math.min(killer.getMaxHealth() * ABSORPTION_CAP_RATIO,
                    killer.getAbsorptionAmount() + killer.getMaxHealth() * ABSORPTION_PER_KILL);
            killer.setAbsorptionAmount(newAbsorption);
        }
    }

    /**
     * 有吸收盾时受到的伤害降低
     * <p>
     * 要在护甲、吸收盾结算之前改，所以挂在 LivingHurtEvent；虚空、/kill 这类无视创造模式的伤害不减。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (victim.getAbsorptionAmount() <= 0 || evt.getSource().canHarmInCreative()
                || shouldBlockMobTrigger(victim, false)) {
            return;
        }

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        if (armorLevel(victim, sacredOrder) > 0) {
            evt.setAmount(evt.getAmount() * SHIELDED_DAMAGE_MULTIPLIER);
        }
    }

    /**
     * 受击后盾还有剩余时反弹，自身有盾时增伤
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        // 受击者：吸收盾结算后还有剩余，按剩余量反弹魔法伤害（减伤在 onLivingHurt）
        if (!shouldBlockMobTrigger(victim, false) && victim.getAbsorptionAmount() > 0
                && armorLevel(victim, sacredOrder) > 0
                && damageSource.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
            attacker.attackEntityFrom(DamageSource.MAGIC, victim.getAbsorptionAmount() * REFLECT_RATIO);
        }

        // 攻击者：自身有吸收盾时增伤
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
            if (shouldBlockMobTrigger(attacker, false)) {
                return;
            }

            if (attacker.getAbsorptionAmount() > 0 && armorLevel(attacker, sacredOrder) > 0) {
                evt.setAmount(evt.getAmount() * VS_SHIELDED_MULTIPLIER);
            }
        }
    }

    private static int armorLevel(@Nonnull EntityLivingBase entity, @Nonnull Enchantment sacredOrder) {
        int total = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                total += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }
        return total;
    }

    /**
     * 每条命第一次进入世界时获得吸收盾
     * <p>
     * 标记存在实体数据里，切维度、重新登录时还在；死亡重生是新实体、标记不带过去，重生后能再领一次。
     * </p>
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote) {
            return;
        }

        if (!(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase entity = (EntityLivingBase) evt.getEntity();

        // 已有吸收盾则跳过
        if (entity.getAbsorptionAmount() > 0 || shouldBlockMobTrigger(entity, false)) {
            return;
        }

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
     * 从末地回主世界也会换一个玩家实体，这条命没结束，领盾标记要带过去
     */
    @SubscribeEvent
    public static void onPlayerClone(@Nonnull PlayerEvent.Clone evt) {
        if (!evt.isWasDeath() && evt.getOriginal().getEntityData().getBoolean(INITIAL_SHIELD_GIVEN_KEY)) {
            evt.getEntityPlayer().getEntityData().setBoolean(INITIAL_SHIELD_GIVEN_KEY, true);
        }
    }

    /**
     * 禁止治疗
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();
        if (shouldBlockMobTrigger(entity, false)) {
            return;
        }

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        if (armorLevel(entity, sacredOrder) > 0) {
            evt.setCanceled(true);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}