package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 附魔基类
 * 提供完整的事件优先级控制和攻击者受击者双视角支持
 */
public abstract class EnchantmentBase extends Enchantment {

    protected final EnchantmentRarity enchantmentRarity;

    @Nullable
    protected final AutoRegisterEnchantment annotation;

    protected EnchantmentBase(@Nonnull EnumEnchantmentType typeIn, @Nonnull EntityEquipmentSlot[] slots) {
        super(Rarity.COMMON, typeIn, slots);

        this.annotation = this.getClass().getAnnotation(AutoRegisterEnchantment.class);

        if (annotation != null) {
            this.enchantmentRarity = annotation.rarity();
            EnchantmentUtil.registerEnchantment(this, annotation.id());
            CarianStyleEnchantments.ENCHANTMENTS.add(this);
            LogUtil.debug("卡利亚式附魔 - 通过注解注册附魔: %s", annotation.id());
        } else {
            this.enchantmentRarity = EnchantmentRarity.UNCOMMON;
            LogUtil.warn("卡利亚式附魔 - 附魔类 %s 未使用AutoRegisterEnchantment注解",
                    this.getClass().getSimpleName());
        }
    }

    protected EnchantmentBase(@Nonnull Rarity rarityIn, @Nonnull EnumEnchantmentType typeIn,
                              @Nonnull EntityEquipmentSlot[] slots, String name) {
        super(rarityIn, typeIn, slots);
        this.annotation = null;

        switch (rarityIn) {
            case UNCOMMON:
                this.enchantmentRarity = EnchantmentRarity.UNCOMMON;
                break;
            case RARE:
                this.enchantmentRarity = EnchantmentRarity.RARE;
                break;
            case VERY_RARE:
                this.enchantmentRarity = EnchantmentRarity.VERY_RARE;
                break;
            default:
                this.enchantmentRarity = EnchantmentRarity.UNCOMMON;
        }

        EnchantmentUtil.registerEnchantment(this, name);
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
        LogUtil.debug("卡利亚式附魔 - 通过传统方式注册附魔: %s", name);
    }

    @Override
    public int getMaxLevel() {
        return enchantmentRarity.getMaxLevel();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        if (annotation != null && annotation.baseEnchantability() != -1) {
            return (int) ((annotation.baseEnchantability() +
                    (enchantmentLevel - 1) * annotation.levelMultiplier()) *
                    ConfigLoader.enchantingDifficulty);
        }
        return enchantmentRarity.calculateEnchantability(enchantmentLevel, ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean isTreasureEnchantment() {
        if (annotation != null && annotation.forceTreasure()) {
            return true;
        }

        switch (enchantmentRarity) {
            case VERY_RARE:
                return ConfigLoader.isTreasureVeryRaryEnchantment || super.isTreasureEnchantment();
            case RARE:
                return ConfigLoader.isTreasureRaryEnchantment || super.isTreasureEnchantment();
            case UNCOMMON:
                return ConfigLoader.isTreasureUncommonEnchantment || super.isTreasureEnchantment();
            default:
                return super.isTreasureEnchantment();
        }
    }

    @Override
    public boolean isCurse() {
        if (annotation != null && annotation.isCurse()) {
            return true;
        }
        return super.isCurse();
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment other) {
        if (!super.canApplyTogether(other)) {
            return false;
        }

        if (annotation == null) {
            return true;
        }

        for (Class<?> allowedClass : annotation.allowWith()) {
            if (allowedClass.isInstance(other)) {
                return true;
            }
        }

        AutoRegisterEnchantment otherAnnotation = other.getClass().getAnnotation(AutoRegisterEnchantment.class);
        if (otherAnnotation != null) {
            if (annotation.category() == otherAnnotation.category() &&
                    annotation.category() != pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL) {
                return false;
            }
        }

        for (Class<?> conflictClass : annotation.conflictsWith()) {
            if (conflictClass.isInstance(other)) {
                return false;
            }
        }

        return true;
    }

    protected int getEnchantmentLevelFromWeapon(@Nonnull EntityLivingBase entity) {
        ItemStack mainHand = entity.getHeldItemMainhand();
        ItemStack offHand = entity.getHeldItemOffhand();

        int level = 0;

        if (!mainHand.isEmpty()) {
            level = Math.max(level, EnchantmentHelper.getEnchantmentLevel(this, mainHand));
        }

        if (!offHand.isEmpty()) {
            level = Math.max(level, EnchantmentHelper.getEnchantmentLevel(this, offHand));
        }

        return applyLevelLimit(level);
    }

    protected int getEnchantmentLevelFromArmor(@Nonnull EntityLivingBase entity) {
        int totalLevel = 0;

        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(this, armor);
            }
        }

        return applyLevelLimit(totalLevel);
    }

    protected int applyLevelLimit(int level) {
        if (ConfigLoader.levelLimit) {
            return Math.min(level, 10);
        }
        return level;
    }

    protected boolean isJustSwung(@Nonnull EntityPlayer player) {
        return EntityLivingUtil.getTicksSinceLastSwing(player) == 1;
    }

    // ==================== LivingAttackEvent 模板方法 (10个) ====================

    protected void onAttackHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttackHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttack(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefend(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttackLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttackLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== LivingHurtEvent 模板方法 (10个) ====================

    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttackerHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== LivingDamageEvent 模板方法 (10个) ====================

    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttackerHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictim(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== 其他事件模板方法 ====================

    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHeal(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onCriticalHit(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== LivingAttackEvent 实际监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingAttackHighest(@Nonnull LivingAttackEvent event) {
        handleLivingAttack(event, EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingAttackHigh(@Nonnull LivingAttackEvent event) {
        handleLivingAttack(event, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingAttackNormal(@Nonnull LivingAttackEvent event) {
        handleLivingAttack(event, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingAttackLow(@Nonnull LivingAttackEvent event) {
        handleLivingAttack(event, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handleLivingAttackLowest(@Nonnull LivingAttackEvent event) {
        handleLivingAttack(event, EventPriority.LOWEST);
    }

    private static void handleLivingAttack(@Nonnull LivingAttackEvent event, @Nonnull EventPriority priority) {
        if (event.getEntity().world.isRemote) {
            return;
        }

        DamageSource source = event.getSource();
        EntityLivingBase victim = event.getEntityLiving();

        if (source.getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getImmediateSource();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getTrueSource();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    // ==================== LivingHurtEvent 实际监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingHurtHighest(@Nonnull LivingHurtEvent event) {
        handleLivingHurt(event, EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingHurtHigh(@Nonnull LivingHurtEvent event) {
        handleLivingHurt(event, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHurtNormal(@Nonnull LivingHurtEvent event) {
        handleLivingHurt(event, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingHurtLow(@Nonnull LivingHurtEvent event) {
        handleLivingHurt(event, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handleLivingHurtLowest(@Nonnull LivingHurtEvent event) {
        handleLivingHurt(event, EventPriority.LOWEST);
    }

    private static void handleLivingHurt(@Nonnull LivingHurtEvent event, @Nonnull EventPriority priority) {
        if (event.getEntity().world.isRemote) {
            return;
        }

        DamageSource source = event.getSource();
        EntityLivingBase victim = event.getEntityLiving();

        if (source.getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getImmediateSource();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getTrueSource();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    // ==================== LivingDamageEvent 实际监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDamageHighest(@Nonnull LivingDamageEvent event) {
        handleLivingDamage(event, EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingDamageHigh(@Nonnull LivingDamageEvent event) {
        handleLivingDamage(event, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingDamageNormal(@Nonnull LivingDamageEvent event) {
        handleLivingDamage(event, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingDamageLow(@Nonnull LivingDamageEvent event) {
        handleLivingDamage(event, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handleLivingDamageLowest(@Nonnull LivingDamageEvent event) {
        handleLivingDamage(event, EventPriority.LOWEST);
    }

    private static void handleLivingDamage(@Nonnull LivingDamageEvent event, @Nonnull EventPriority priority) {
        if (event.getEntity().world.isRemote) {
            return;
        }

        DamageSource source = event.getSource();
        EntityLivingBase victim = event.getEntityLiving();

        if (source.getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getImmediateSource();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) source.getTrueSource();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    // ==================== 其他事件监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDeath(@Nonnull LivingDeathEvent event) {
        if (event.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = event.getEntityLiving();

        for (ItemStack stack : victim.getEquipmentAndArmor()) {
            if (stack.isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (!(enchantment instanceof EnchantmentBase)) {
                    continue;
                }

                EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
                int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
                level = baseEnchantment.applyLevelLimit(level);

                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(
                            event, victim, stack, level,
                            null, victim, event.getSource()
                    );
                    baseEnchantment.onDeath(ctx, level);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHeal(@Nonnull LivingHealEvent event) {
        if (event.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase healer = event.getEntityLiving();

        for (ItemStack stack : healer.getEquipmentAndArmor()) {
            if (stack.isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (!(enchantment instanceof EnchantmentBase)) {
                    continue;
                }

                EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
                int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
                level = baseEnchantment.applyLevelLimit(level);

                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(
                            event, healer, stack, level
                    );
                    baseEnchantment.onHeal(ctx, level);
                }
            }
        }
    }

    @SubscribeEvent
    public static void handlePlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.player.world.isRemote) {
            return;
        }

        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = event.player;

        for (ItemStack stack : player.getEquipmentAndArmor()) {
            if (stack.isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (!(enchantment instanceof EnchantmentBase)) {
                    continue;
                }

                EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
                int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
                level = baseEnchantment.applyLevelLimit(level);

                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(
                            event, player, stack, level
                    );
                    baseEnchantment.onPlayerTick(ctx, level);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleCriticalHit(@Nonnull CriticalHitEvent event) {
        if (event.getEntity().world.isRemote) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        ItemStack weapon = player.getHeldItemMainhand();

        if (weapon.isEmpty()) {
            return;
        }

        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(weapon).keySet()) {
            if (!(enchantment instanceof EnchantmentBase)) {
                continue;
            }

            EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
            int level = EnchantmentHelper.getEnchantmentLevel(enchantment, weapon);
            level = baseEnchantment.applyLevelLimit(level);

            if (level > 0) {
                EnchantmentContext ctx = new EnchantmentContext(
                        event, player, weapon, level
                );
                baseEnchantment.onCriticalHit(ctx, level);
            }
        }
    }

    // ==================== 核心处理方法 ====================

    private static void processEntityEnchantments(
            @Nonnull EntityLivingBase holder,
            @Nullable EntityLivingBase victim,
            @Nullable DamageSource source,
            @Nonnull Object event,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        if (isAttacker) {
            ItemStack mainHand = holder.getHeldItemMainhand();
            ItemStack offHand = holder.getHeldItemOffhand();

            if (!mainHand.isEmpty()) {
                processItemEnchantments(mainHand, holder, victim, source, event, priority, true);
            }
            if (!offHand.isEmpty()) {
                processItemEnchantments(offHand, holder, victim, source, event, priority, true);
            }
        } else {
            for (ItemStack stack : holder.getEquipmentAndArmor()) {
                if (!stack.isEmpty()) {
                    processItemEnchantments(stack, holder, victim, source, event, priority, false);
                }
            }
        }
    }

    private static void processItemEnchantments(
            @Nonnull ItemStack stack,
            @Nonnull EntityLivingBase holder,
            @Nullable EntityLivingBase victim,
            @Nullable DamageSource source,
            @Nonnull Object event,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
            if (!(enchantment instanceof EnchantmentBase)) {
                continue;
            }

            EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
            int level = EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
            level = baseEnchantment.applyLevelLimit(level);

            if (level <= 0) {
                continue;
            }

            EntityLivingBase attacker = isAttacker ? holder : (source != null && source.getTrueSource() instanceof EntityLivingBase ? (EntityLivingBase) source.getTrueSource() : null);
            EnchantmentContext ctx = new EnchantmentContext(
                    event, holder, stack, level,
                    attacker, victim, source
            );

            if (event instanceof LivingAttackEvent) {
                dispatchLivingAttackEvent(baseEnchantment, ctx, level, priority, isAttacker);
            } else if (event instanceof LivingHurtEvent) {
                dispatchLivingHurtEvent(baseEnchantment, ctx, level, priority, isAttacker);
            } else if (event instanceof LivingDamageEvent) {
                dispatchLivingDamageEvent(baseEnchantment, ctx, level, priority, isAttacker);
            }
        }
    }

    private static void dispatchLivingAttackEvent(
            @Nonnull EnchantmentBase enchantment,
            @Nonnull EnchantmentContext ctx,
            int level,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        switch (priority) {
            case HIGHEST:
                if (isAttacker) {
                    enchantment.onAttackHighest(ctx, level);
                } else {
                    enchantment.onDefendHighest(ctx, level);
                }
                break;
            case HIGH:
                if (isAttacker) {
                    enchantment.onAttackHigh(ctx, level);
                } else {
                    enchantment.onDefendHigh(ctx, level);
                }
                break;
            case NORMAL:
                if (isAttacker) {
                    enchantment.onAttack(ctx, level);
                } else {
                    enchantment.onDefend(ctx, level);
                }
                break;
            case LOW:
                if (isAttacker) {
                    enchantment.onAttackLow(ctx, level);
                } else {
                    enchantment.onDefendLow(ctx, level);
                }
                break;
            case LOWEST:
                if (isAttacker) {
                    enchantment.onAttackLowest(ctx, level);
                } else {
                    enchantment.onDefendLowest(ctx, level);
                }
                break;
        }
    }

    private static void dispatchLivingHurtEvent(
            @Nonnull EnchantmentBase enchantment,
            @Nonnull EnchantmentContext ctx,
            int level,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        switch (priority) {
            case HIGHEST:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerHighest(ctx, level);
                } else {
                    enchantment.onHurtAsVictimHighest(ctx, level);
                }
                break;
            case HIGH:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerHigh(ctx, level);
                } else {
                    enchantment.onHurtAsVictimHigh(ctx, level);
                }
                break;
            case NORMAL:
                if (isAttacker) {
                    enchantment.onHurtAsAttacker(ctx, level);
                } else {
                    enchantment.onHurtAsVictim(ctx, level);
                }
                break;
            case LOW:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerLow(ctx, level);
                } else {
                    enchantment.onHurtAsVictimLow(ctx, level);
                }
                break;
            case LOWEST:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerLowest(ctx, level);
                } else {
                    enchantment.onHurtAsVictimLowest(ctx, level);
                }
                break;
        }
    }

    private static void dispatchLivingDamageEvent(
            @Nonnull EnchantmentBase enchantment,
            @Nonnull EnchantmentContext ctx,
            int level,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        switch (priority) {
            case HIGHEST:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerHighest(ctx, level);
                } else {
                    enchantment.onDamageAsVictimHighest(ctx, level);
                }
                break;
            case HIGH:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerHigh(ctx, level);
                } else {
                    enchantment.onDamageAsVictimHigh(ctx, level);
                }
                break;
            case NORMAL:
                if (isAttacker) {
                    enchantment.onDamageAsAttacker(ctx, level);
                } else {
                    enchantment.onDamageAsVictim(ctx, level);
                }
                break;
            case LOW:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerLow(ctx, level);
                } else {
                    enchantment.onDamageAsVictimLow(ctx, level);
                }
                break;
            case LOWEST:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerLowest(ctx, level);
                } else {
                    enchantment.onDamageAsVictimLowest(ctx, level);
                }
                break;
        }
    }
}