package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * 精准落雷附魔
 * <p>v2.2：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "precise_lightning", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantmentCategory.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}, conflictsWith = {EnchantmentCausalityPrinciple.class})
@Mod.EventBusSubscriber
public class EnchantmentPreciseLightning extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.precise_lightning.desc，
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
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getDirectEntity() instanceof Projectile)) return;
        if (!(damageSource.getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，远程反击落雷）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        LivingEntity attacker = (LivingEntity) damageSource.getEntity();

        Enchantment preciseLightning = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPreciseLightning.class);
        if (preciseLightning == null) return;

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(preciseLightning, armor);
        }
        if (ConfigLoader.levelLimit) totalLevel = Math.min(totalLevel, 10);
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

                Level world = attacker.level();
                if (world instanceof ServerLevel serverLevel) {
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (lightning != null) {
                        lightning.moveTo(attacker.getX(), attacker.getY(), attacker.getZ());
                        lightning.setVisualOnly(true);
                        serverLevel.addFreshEntity(lightning);
                    }
                }

                attacker.invulnerableTime = attacker.invulnerableDuration / 2;

                int magnification = 1;
                if (attacker.level().isThundering()) {
                    magnification = MAGNIFICATION_HIGH.getInt();
                } else if (attacker.level().isRaining()) {
                    magnification = MAGNIFICATION_MID.getInt();
                }

                attacker.hurt(attacker.damageSources().lightningBolt(), originalDamage * (float) DAMAGE_RATIO.get() * magnification);

                if (attacker.onGround()) {
                    double x = RandomUtils.nextBoolean() ? victim.getX() - attacker.getX() : attacker.getX() - victim.getX();
                    double z = RandomUtils.nextBoolean() ? victim.getZ() - attacker.getZ() : attacker.getZ() - victim.getZ();
                    attacker.knockback((float) KNOCKBACK_STRENGTH.get(), x, z);
                }
            }
        }.start();
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
