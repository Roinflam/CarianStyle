package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/** 艾奥尼亚附魔 - 优化: LivingTickEvent -> PlayerTickEvent @version 2.1 */
@AutoRegisterEnchantment(id = "aeonia", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class})
@Mod.EventBusSubscriber
public class EnchantmentAeonia extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.aeonia.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "aeonia";

    /**
     * 命中时回复的最大生命占比
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 自身腐败续期的结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次续期施加的自身猩红腐败时长（tick，需略大于结算间隔）
     * <p>默认 21，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle SELF_ROT_DURATION =
            EnchantmentValues.define(VALUE_ID, "self_rot_duration",
                    21, 1, 600);


    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentAeonia() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();
        if (victim == null || victim.getEffect(CarianStylePotion.SCARLET_ROT.get()) == null) return;
        if (!isFullyCharged(ctx.getHolder())) return;
        attacker.heal(attacker.getMaxHealth() * (float) HEAL_RATIO.get());
    }

    /** 优化：从LivingTickEvent改为PlayerTickEvent */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;
        if (evt.player.tickCount % TICK_INTERVAL.getInt() != 0) return;
        Player holder = evt.player;
        ItemStack heldItem = holder.getMainHandItem();
        if (heldItem.isEmpty()) return;
        Enchantment aeonia = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
        if (aeonia == null) return;
        // v-cache：走中央装备缓存，同一 tick 内与其它监听器共用一次扫描
        int level = EnchantmentEventHandler.mainHand(holder, aeonia);
        if (level > 0) holder.addEffect(new MobEffectInstance(CarianStylePotion.SCARLET_ROT.get(), SELF_ROT_DURATION.getInt(), 0));
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
