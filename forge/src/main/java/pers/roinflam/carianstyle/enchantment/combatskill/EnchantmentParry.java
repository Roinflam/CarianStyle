package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.UUID;

/**
 * 招架附魔
 * <p>v2.2：双向监听器入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.3 修复：注解声明的附魔类型与构造函数不一致</h3>
 * <p>
 * <b>问题：</b>注解里写的是 {@code type = EnchantmentCategory.BREAKABLE}
 * （原版判定为「任何有耐久的物品」，镐、斧、锹全都算），
 * 而构造函数传的是自定义类型 {@code "SHIELD"}。
 * </p>
 * <p>
 * 目前 {@code EnchantmentRegistry.createEnchantmentInstance} 走的是无参构造函数分支，
 * 会把 {@code resolveEnchantmentCategory} 解析出来的注解类型<b>直接丢弃</b>，
 * 一切以构造函数为准，因此本附魔<b>当前运行时行为是正确的</b>（只能附在盾牌上）。
 * </p>
 * <p>
 * <b>但这是一颗雷。</b>一旦以后有人把注册器改成「以注解为准」——
 * 那其实才是注解本来的设计意图——本附魔会瞬间变成 BREAKABLE，
 * 也就是能被附到镐 / 斧 / 锹上，正好复现「盾牌附魔串到工具上」的故障现象。
 * </p>
 * <p>
 * <b>修复：</b>把注解改为 {@code customType = "SHIELD"}，与构造函数对齐。
 * 本次改动<b>不改变任何当前运行时行为</b>，只是消除两边不一致的隐患。
 * </p>
 *
 * <h3>v2.4：成功架住时播放「格挡窗口」自绘特效</h3>
 * <p>
 * <b>为什么播在 {@link #onLivingAttack} 而不是 {@link #onLivingHurt}：</b>
 * 玩家需要的是「现在可以反击了」这个<b>预告</b>，而不是「刚才那一下有加成」这个事后通报。
 * 加成真正兑现是在 {@code onLivingHurt}，但那时玩家已经打出去了，提示没有意义。
 * 因此特效跟着 {@code setData(PARRY_LEVEL_KEY, ...)} 这一行走——
 * 数据写进去的那一刻，就是窗口开始的那一刻。
 * </p>
 * <p>
 * <b>⚠ 时长与 10 tick 严格绑定。</b>客户端那边
 * {@code CombatArtEffectManager.PARRY_WINDOW_DURATION_MS} 取 500ms，
 * 正好等于这里 {@code setData(..., 10)} 的 10 tick。
 * 准星收缩到零的瞬间就是加成失效的瞬间——<b>改了这里的 10，必须同步改那个常量</b>，
 * 否则视觉会比机制早结束（玩家白白放过还能用的加成）或晚结束（玩家以为还有加成而挨打）。
 * </p>
 * <p>
 * 特效为定点，不跟随实体。举盾时原版会大幅削减移速，500ms 内位移不足半格，
 * 定点完全够用；为此改包格式、动到全部既有构造点，收益不成比例。
 * </p>
 *
 * @author RoinFlam
 * @version 2.4
 */
@AutoRegisterEnchantment(
        id = "parry",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        // v2.3：type = BREAKABLE → customType = "SHIELD"，与下方构造函数保持一致
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentParry extends EnchantmentBase {

    private static final String PARRY_LEVEL_KEY = "parry_level";
    private static final String PARRY_COOLDOWN_KEY = "parry_cooldown";

    public EnchantmentParry() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，进入招架状态）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        UUID uuid = holder.getUUID();

        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment parry = EnchantmentRegistry.getEnchantmentByClass(EnchantmentParry.class);
        if (parry == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(parry, activeItem);
        if (level <= 0) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(PARRY_COOLDOWN_KEY, uuid)) {
            return;
        }

        if (EnchantmentDataManager.hasData(PARRY_LEVEL_KEY, uuid)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
        if (!isAttackFromFront(holder, attacker)) {
            return;
        }

        EnchantmentDataManager.setData(PARRY_LEVEL_KEY, uuid, level, 10);

        // ⭐ v2.4：架住成功，播放「格挡窗口」自绘特效。
        // 时长与上面那个 10 tick 严格对齐，准星收缩到零即窗口关闭（详见类注释）
        if (holder.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.parryWindow(serverLevel, holder);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角，招架增伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        UUID uuid = attacker.getUUID();

        Integer parryLevel = EnchantmentDataManager.getData(PARRY_LEVEL_KEY, uuid);
        if (parryLevel == null || parryLevel <= 0) {
            return;
        }

        float bonusDamage = evt.getAmount() * parryLevel * 0.25f;
        evt.setAmount(evt.getAmount() + bonusDamage);

        EnchantmentDataManager.removeData(PARRY_LEVEL_KEY, uuid);
        EnchantmentDataManager.setCooldown(PARRY_COOLDOWN_KEY, uuid, 40);
    }

    private static boolean isAttackFromFront(LivingEntity defender, LivingEntity attacker) {
        double dx = attacker.getX() - defender.getX();
        double dz = attacker.getZ() - defender.getZ();

        float yaw = defender.getYRot();
        double defenderDirX = -Math.sin(Math.toRadians(yaw));
        double defenderDirZ = Math.cos(Math.toRadians(yaw));

        double dot = dx * defenderDirX + dz * defenderDirZ;

        return dot > 0;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
