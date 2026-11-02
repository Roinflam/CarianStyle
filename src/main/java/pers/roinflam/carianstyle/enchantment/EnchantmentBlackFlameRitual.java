package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黑焰仪式附魔
 * <p>身上每有一个可见的持续效果，造成的伤害提高（负面效果加得更多）；身上有效果时每秒燃烧自损。</p>
 * <p>v2.2：LivingHurt攻击者视角入口接入怪物附魔触发开关。
 * onPlayerTick 玩家专属，无需检查。</p>
 * <p>1.12.2 版：效果好坏按 {@code isBadEffect()} 判断。{@code isBeneficial()} 在 1.12.2 是客户端专用的信标标记，
 * 专用服上调用就会崩，而且只有原版信标效果才是 true；发光这类中性效果按增益算，和 1.20.1 写
 * {@code !isBeneficial()} 时一致。「可见」对应 {@code doesShowParticles()}。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "black_flame_ritual", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.ARMOR_CHEST, slots = {EntityEquipmentSlot.CHEST}, conflictsWith = {EnchantmentShelterOfFire.class, EnchantmentHealingByFire.class})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentBlackFlameRitual extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.black_flame_ritual.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "black_flame_ritual";

    /**
     * 每个负面效果提供的伤害加成
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle HARMFUL_BONUS =
            EnchantmentValues.define(VALUE_ID, "harmful_bonus",
                    0.2D, 0.0D, 5.0D);

    /**
     * 每个正面效果提供的伤害加成
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BENEFICIAL_BONUS =
            EnchantmentValues.define(VALUE_ID, "beneficial_bonus",
                    0.1D, 0.0D, 5.0D);

    /**
     * 自损结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次自损后保留的生命比例（越小掉血越快）
     * <p>默认 0.95，允许范围 0.5 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEALTH_RETAIN_RATIO =
            EnchantmentValues.define(VALUE_ID, "health_retain_ratio",
                    0.95D, 0.5D, 1.0D);

    public EnchantmentBlackFlameRitual() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) return;
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        // v2.2：怪物附魔触发开关（攻击者视角，根据自身效果数增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) return;

        // v-cache：走中央装备缓存（主手 + 四个护甲槽）
        int totalLevel = EnchantmentEventHandler.armorAndMainHand(attacker, blackFlameRitual);
        if (totalLevel <= 0) return;

        float damageMultiplier = 1;
        for (PotionEffect effect : attacker.getActivePotionEffects()) {
            Potion potion = effect.getPotion();
            if (!potion.isInstant() && effect.doesShowParticles()) {
                // 1.12.2 版：服务端只能用 isBadEffect（见类注释）
                damageMultiplier += (float) (potion.isBadEffect() ? HARMFUL_BONUS.get() : BENEFICIAL_BONUS.get());
            }
        }
        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    /**
     * 持续燃烧自损（PlayerTickEvent 玩家专属，无需开关检查）
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) return;
        if (evt.player.ticksExisted % TICK_INTERVAL.getInt() != 0) return;
        EntityPlayer holder = evt.player;
        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) return;
        // v-cache：走中央装备缓存
        int totalLevel = EnchantmentEventHandler.armorTotal(holder, blackFlameRitual);
        if (totalLevel <= 0) return;
        boolean hasPotion = false;
        for (PotionEffect effect : holder.getActivePotionEffects()) {
            Potion potion = effect.getPotion();
            if (!potion.isInstant() && effect.doesShowParticles()) {
                hasPotion = true;
                break;
            }
        }
        if (hasPotion) {
            DynamicAttributeManager.apply(holder, DynamicAttributes.DESTRUCTION_FIRE_BURNING.createInstance(21, 0));
            holder.setHealth(holder.getHealth() * (float) HEALTH_RETAIN_RATIO.get());
        }
    }

    @Override
    public int getMinCost(int l) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
