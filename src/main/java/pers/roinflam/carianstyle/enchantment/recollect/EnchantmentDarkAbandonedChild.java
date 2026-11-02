package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.DayNightUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 暗弃子附魔
 * <p>满蓄力的攻击变为无视护甲的魔法伤害，并偷走目标一个可见的增益效果；夜晚受到的伤害降低、持续回复生命。</p>
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关。
 * onHurtAsAttacker 走中央事件分发器，已被 scanEntity 拦截。
 * onPlayerTick 玩家专属，无需检查。</p>
 * <p>1.12.2 版：增益按 {@code !isBadEffect()} 判断（{@code isBeneficial()} 在 1.12.2 是客户端专用的信标标记，
 * 专用服上不存在），所以发光这类中性效果也会被偷；「可见」对应 {@code doesShowParticles()}。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "dark_abandoned_child",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentDarkAbandonedChild extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.dark_abandoned_child.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dark_abandoned_child";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 夜间受到伤害的倍率
     * <p>默认 0.9，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "night_damage_multiplier",
                    0.9D, 0.0D, 2.0D);

    /**
     * 夜间每秒回复的最大生命占比
     * <p>默认 0.015，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.015D, 0.0D, 1.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentDarkAbandonedChild() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        if (!isFullyCharged(ctx.getHolder())) {
            return;
        }

        // 受伤阶段才设无视护甲：盾牌检查已经过了，盾牌照样能挡，和 1.20.1 一致
        if (ctx.getDamageSource() != null) {
            DamageSourceUtil.setBypassesArmor(ctx.getDamageSource());
            DamageSourceUtil.setMagicDamage(ctx.getDamageSource());
        }

        Collection<PotionEffect> activeEffects = victim.getActivePotionEffects();
        if (!activeEffects.isEmpty()) {
            List<PotionEffect> positiveEffects = new ArrayList<>(activeEffects);
            positiveEffects.removeIf(effect -> {
                Potion potion = effect.getPotion();
                // 1.12.2 版：服务端只能用 isBadEffect（见类注释）
                return potion.isBadEffect() ||
                        potion.isInstant() ||
                        !effect.doesShowParticles();
            });

            if (!positiveEffects.isEmpty()) {
                PotionEffect stolen = positiveEffects.get(RandomUtil.getInt(0, positiveEffects.size() - 1));
                attacker.addPotionEffect(new PotionEffect(stolen));
                victim.removePotionEffect(stolen.getPotion());
            }
        }
    }

    /**
     * 受击减伤（夜晚10%）
     * <p>v2.1：受击者视角接入怪物附魔触发开关</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (DamageTags.is(evt.getSource(), DamageTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        // v-cache：走中央装备缓存
        int level = EnchantmentEventHandler.mainHand(victim, darkAbandonedChild);

        if (level > 0 && !DayNightUtil.isDay(victim.world)) {
            evt.setAmount(evt.getAmount() * (float) NIGHT_DAMAGE_MULTIPLIER.get());
        }
    }

    /**
     * 夜晚持续回血（PlayerTickEvent玩家专属，无需开关检查）
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || DayNightUtil.isDay(evt.player.world)) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        // v-cache：走中央装备缓存
        int level = EnchantmentEventHandler.mainHand(player, darkAbandonedChild);

        if (level > 0) {
            player.heal(player.getMaxHealth() * (float) HEAL_RATIO_PER_SECOND.get() / 20);
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
