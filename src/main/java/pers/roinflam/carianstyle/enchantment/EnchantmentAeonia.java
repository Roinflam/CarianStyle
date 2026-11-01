package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 艾奥尼亚附魔
 *
 * 被动：每秒给自己施加猩红腐烂
 * 攻击：目标有猩红腐烂时，治疗自身最大血量×10%
 */
@AutoRegisterEnchantment(
        id = "aeonia",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentAeonia extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;
    /** 回血比例（最大生命） */
    private static final float HEAL_RATIO = 0.1F;
    /** 自身腐败的刷新间隔（tick） */
    private static final int TICK_INTERVAL = 20;
    /** 每次给自己挂的腐败时长（tick），比间隔多 1 tick 保证不断档 */
    private static final int SELF_ROT_DURATION = 21;

    public EnchantmentAeonia() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (victim.getActivePotionEffect(CarianStylePotion.SCARLET_ROT) == null) {
            return;
        }

        if (!isFullyCharged(attacker)) {
            return;
        }

        attacker.heal(attacker.getMaxHealth() * HEAL_RATIO);
    }

    /**
     * 主手拿着时自己身上保持 1 级猩红腐败
     * <p>
     * 只对玩家、只认主手。以前挂在所有生物的 LivingUpdate 上、按「正在用的那只手」判断，
     * 每个生物每 tick 都要进一次；怪物拿着也会自己腐败。
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (evt.player.ticksExisted % TICK_INTERVAL != 0) {
            return;
        }

        EntityPlayer holder = evt.player;
        ItemStack heldItem = holder.getHeldItemMainhand();

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment aeonia = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
        if (aeonia == null) {
            return;
        }

        if (EnchantmentHelper.getEnchantmentLevel(aeonia, heldItem) > 0) {
            holder.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT, SELF_ROT_DURATION, 0));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}