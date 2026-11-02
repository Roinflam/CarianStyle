package pers.roinflam.carianstyle.enchantment;

import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.world.WorldServer;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.RandomUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

/**
 * 精准落雷附魔
 * <p>v2.2：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "precise_lightning", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnumEnchantmentType.ARMOR, slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}, conflictsWith = {EnchantmentCausalityPrinciple.class})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentPreciseLightning extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.precise_lightning.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "precise_lightning";

    /**
     * 反击雷电伤害占原伤害的比例
     * <p>默认 0.3，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.3D, 0.0D, 5.0D);

    /**
     * 反击的击退强度
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.2D, 0.0D, 5.0D);

    /**
     * 高倍率档的伤害放大倍数
     * <p>默认 4，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAGNIFICATION_HIGH =
            EnchantmentValues.define(VALUE_ID, "magnification_high",
                    4, 1, 100);

    /**
     * 中倍率档的伤害放大倍数
     * <p>默认 2，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAGNIFICATION_MID =
            EnchantmentValues.define(VALUE_ID, "magnification_mid",
                    2, 1, 100);

    public EnchantmentPreciseLightning() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntityLiving().world.isRemote) return;

        DamageSource damageSource = evt.getSource();
        if (!isProjectile(damageSource.getImmediateSource())) return;
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) return;

        EntityLivingBase victim = evt.getEntityLiving();

        // v2.2：怪物附魔触发开关（受击者视角，远程反击落雷）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        Enchantment preciseLightning = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPreciseLightning.class);
        if (preciseLightning == null) return;

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getEnchantmentLevel(preciseLightning, armor);
        }
        totalLevel = applyLevelLimit(preciseLightning, totalLevel, 10);
        if (totalLevel <= 0) return;

        final int effectiveLevel = totalLevel;
        final float originalDamage = evt.getAmount();

        new SynchronizationTask(5, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > effectiveLevel) {
                    this.cancel();
                    return;
                }

                // 只有画面和声音的闪电（effectOnly），伤害在下面单独算
                World world = attacker.world;
                world.addWeatherEffect(new EntityLightningBolt(world, attacker.posX, attacker.posY, attacker.posZ, true));

                attacker.hurtResistantTime = attacker.maxHurtResistantTime / 2;

                int magnification = 1;
                if (attacker.world.isThundering()) {
                    magnification = MAGNIFICATION_HIGH.getInt();
                } else if (attacker.world.isRaining()) {
                    magnification = MAGNIFICATION_MID.getInt();
                }

                attacker.attackEntityFrom(DamageSource.LIGHTNING_BOLT, originalDamage * (float) DAMAGE_RATIO.get() * magnification);

                if (attacker.onGround) {
                    double x = RandomUtils.nextBoolean() ? victim.posX - attacker.posX : attacker.posX - victim.posX;
                    double z = RandomUtils.nextBoolean() ? victim.posZ - attacker.posZ : attacker.posZ - victim.posZ;
                    EntityLivingUtil.knockBack(attacker, victim, (float) KNOCKBACK_STRENGTH.get(), x, z);
                }
            }
        }.start();
    }

    /**
     * 是不是投射物。
     * <p>1.12.2 版：没有 1.20.1 那样统一的投射物基类，箭、雪球这类实现 {@link IProjectile}，
     * 火球和潜影贝的子弹不实现，这里一并算上，范围和 1.20.1 的 {@code Projectile} 对齐。</p>
     * <p>辉剑（{@code EntityGlintblades}）不算：1.20.1 里辉剑的伤害没有直接实体，不会触发；
     * 1.12.2 为了死亡信息和兼容把直接实体填成了剑，剑又是 {@code EntityThrowable}，要单独排除。</p>
     */
    private static boolean isProjectile(@Nullable Entity entity) {
        if (entity instanceof EntityGlintblades) {
            return false;
        }
        return entity instanceof IProjectile
                || entity instanceof EntityFireball
                || entity instanceof EntityShulkerBullet;
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((30 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
