package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 艾奥尼亚附魔 - 优化: LivingTickEvent -> PlayerTickEvent @version 2.1
 * <p>
 * 被动：主手拿着时每秒给自己续上 1 级猩红腐败；攻击：目标身上有猩红腐败、且满蓄力时，回复自身最大生命 × 10%。
 * 只对玩家、只认主手（怪物拿着不会自己腐败）。
 * </p>
 */
@AutoRegisterEnchantment(id = "aeonia", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class})
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentAeonia extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.aeonia.desc，
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

    public EnchantmentAeonia() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();
        if (victim == null || victim.getActivePotionEffect(CarianStylePotion.SCARLET_ROT.get()) == null) return;
        if (!isFullyCharged(ctx.getHolder())) return;
        attacker.heal(attacker.getMaxHealth() * (float) HEAL_RATIO.get());
    }

    /** 优化：从LivingTickEvent改为PlayerTickEvent */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) return;
        if (evt.player.ticksExisted % TICK_INTERVAL.getInt() != 0) return;
        EntityPlayer holder = evt.player;
        ItemStack heldItem = holder.getHeldItemMainhand();
        if (heldItem.isEmpty()) return;
        Enchantment aeonia = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
        if (aeonia == null) return;
        // v-cache：走中央装备缓存，同一 tick 内与其它监听器共用一次扫描
        int level = EnchantmentEventHandler.mainHand(holder, aeonia);
        if (level > 0) holder.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT.get(), SELF_ROT_DURATION.getInt(), 0));
    }

    @Override
    public int getMinCost(int l) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
