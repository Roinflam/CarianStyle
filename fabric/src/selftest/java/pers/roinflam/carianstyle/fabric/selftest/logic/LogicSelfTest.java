package pers.roinflam.carianstyle.fabric.selftest.logic;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.bus.Event;
import pers.roinflam.carianstyle.bus.EventPriority;
import pers.roinflam.carianstyle.event.BlockEvent;
import pers.roinflam.carianstyle.event.CriticalHitEvent;
import pers.roinflam.carianstyle.event.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.EntityLeaveLevelEvent;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.event.MobEffectEvent;
import pers.roinflam.carianstyle.event.PlayerTickEvent;
import pers.roinflam.carianstyle.event.ProjectileImpactEvent;
import pers.roinflam.carianstyle.event.ServerStartedEvent;
import pers.roinflam.carianstyle.event.ServerStoppedEvent;
import pers.roinflam.carianstyle.event.ServerTickEvent;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.fabric.RockBlasterExperience;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 逻辑事件自测（专用服）：{@code -Dcarianstyle.selftest=logic} 时由 {@code FabricLogicEvents} 反射挂上。
 * 服务端起好之后按 tick 分几步跑，每条断言打一行 {@code [SELFTEST] 名字 PASS|FAIL 实际值}，跑完 halt。
 * 只经 {@link CarianBus#on} 监听，不碰 Fabric API 的回调（服务端起好、每 tick 推进本身也走自带总线，顺带验那几个回调）。
 */
public final class LogicSelfTest {

    private static final Logger OUT = LoggerFactory.getLogger("carianstyle-selftest");

    // ==================== 伤害三段 / 死亡的记录器 ====================

    /** 一次发出的上下文；用例的处理器改 amount、置 canceled，记录器写回真正的事件。 */
    static final class Ev {
        final String stage;
        final LivingEntity entity;
        final DamageSource source;
        float amount;
        boolean canceled;
        final float absorptionNow;
        final int depth;

        Ev(String stage, LivingEntity entity, DamageSource source, float amount, int depth) {
            this.stage = stage;
            this.entity = entity;
            this.source = source;
            this.amount = amount;
            this.absorptionNow = entity.getAbsorptionAmount();
            this.depth = depth;
        }
    }

    static Consumer<Ev> onAttack;
    static Consumer<Ev> onHurt;
    static Consumer<Ev> onDamage;
    static Consumer<Ev> onDeath;
    /** 暴击倍率：小于 0 = 不改 */
    static float critMultiplier = -1F;
    static Boolean lastVanillaCrit;
    static float lastInitialMultiplier = Float.NaN;
    static int critEvents;

    static final List<String> LOG = new ArrayList<>();
    static final Map<Object, String> LABELS = new IdentityHashMap<>();
    private static int depth;

    /** 其余事件：按类记下收到的事件，用例可以临时挂处理器 */
    private static final Map<Class<?>, List<Event>> SEEN = new LinkedHashMap<>();
    private static final Map<Class<?>, Consumer<Event>> HANDLERS = new HashMap<>();
    private static final BlockPos STALE_MARK = new BlockPos(0, -60, 0);
    private static LivingEntity tickTarget;
    private static int tickSeen;
    private static boolean tickCancel;

    // ==================== 跑的状态 ====================

    private static MinecraftServer server;
    private static ServerLevel level;
    private static int tick = -1;
    private static int pass;
    private static int fail;
    private static String suffix = "";
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private static Zombie armored;
    private static Villager dieCancelled;
    private static Villager expiring;
    private static Arrow arrow;
    private static Villager arrowTarget;
    private static Zoglin zoglin;
    private static int zoglinTargetEvents;
    private static EnderDragon dragon;
    private static final List<Integer> DRAGON_XP = new ArrayList<>();
    private static final StringBuilder TICK_SEQ = new StringBuilder();
    private static boolean recordTicks;

    private LogicSelfTest() {
    }

    // ==================== 挂上 ====================

    public static void install() {
        OUT.info("[SELFTEST] logic group installed");
        CarianBus.on(LivingTickEvent.class, EventPriority.NORMAL, false, e -> {
            if (tickTarget != null && e.getEntity() == tickTarget) {
                tickSeen++;
                if (tickCancel) {
                    e.setCanceled(true);
                }
            }
        });
        CarianBus.on(ServerStartedEvent.class, EventPriority.NORMAL, false, e -> onStarted(e.getServer()));
        CarianBus.on(ServerTickEvent.Pre.class, EventPriority.NORMAL, false, e -> {
            if (recordTicks) {
                TICK_SEQ.append('<');
            }
        });
        CarianBus.on(ServerTickEvent.Post.class, EventPriority.NORMAL, false, e -> {
            if (recordTicks) {
                TICK_SEQ.append('>');
            }
            onTick(e.getServer());
        });
        CarianBus.on(ServerStoppedEvent.class, EventPriority.NORMAL, false, e ->
                OUT.info("[SELFTEST] server_stopped_event PASS server={}", e.getServer() != null));

        CarianBus.on(LivingAttackEvent.class, EventPriority.NORMAL, false, e -> {
            Ev ev = fire("attack", onAttack, e.getEntity(), e.getSource(), e.getAmount());
            if (ev.canceled) {
                e.setCanceled(true);
            }
        });
        CarianBus.on(LivingHurtEvent.class, EventPriority.NORMAL, false, e -> {
            Ev ev = fire("hurt", onHurt, e.getEntity(), e.getSource(), e.getAmount());
            e.setAmount(ev.amount);
            if (ev.canceled) {
                e.setCanceled(true);
            }
        });
        CarianBus.on(LivingDamageEvent.class, EventPriority.NORMAL, false, e -> {
            Ev ev = fire("damage", onDamage, e.getEntity(), e.getSource(), e.getAmount());
            e.setAmount(ev.amount);
            if (ev.canceled) {
                e.setCanceled(true);
            }
            LOG.add(label(e.getEntity()) + ":damage-out:" + (ev.canceled ? 0F : ev.amount));
        });
        CarianBus.on(LivingDeathEvent.class, EventPriority.NORMAL, false, e -> {
            Ev ev = fire("death", onDeath, e.getEntity(), e.getSource(), e.getEntity().getHealth());
            if (ev.canceled) {
                e.setCanceled(true);
            }
        });
        CarianBus.on(CriticalHitEvent.class, EventPriority.NORMAL, false, e -> {
            critEvents++;
            lastVanillaCrit = e.isVanillaCritical();
            lastInitialMultiplier = e.getDamageMultiplier();
            if (critMultiplier >= 0F) {
                e.setDamageMultiplier(critMultiplier);
            }
        });

        hook(LivingHealEvent.class);
        hook(LivingKnockBackEvent.class);
        hook(MobEffectEvent.Added.class);
        hook(MobEffectEvent.Remove.class);
        hook(MobEffectEvent.Expired.class);
        hook(LivingExperienceDropEvent.class);
        hook(LivingChangeTargetEvent.class);
        hook(EntityJoinLevelEvent.class);
        hook(EntityLeaveLevelEvent.class);
        hook(ProjectileImpactEvent.class);
        hook(PlayerTickEvent.Pre.class);
        hook(BlockEvent.BreakEvent.class);

        // 常驻：僵尸疣猪兽经大脑选目标一律取消（验 StartAttacking 那一处）
        CarianBus.on(LivingChangeTargetEvent.class, EventPriority.HIGHEST, false, e -> {
            if (zoglin != null && e.getEntity() == zoglin) {
                zoglinTargetEvents++;
                e.setCanceled(true);
            }
        });
        // 常驻：末影龙死亡动画里每份经验都记下、改成 0
        CarianBus.on(LivingExperienceDropEvent.class, EventPriority.HIGHEST, false, e -> {
            if (dragon != null && e.getEntity() == dragon) {
                DRAGON_XP.add(e.getDroppedExperience());
                e.setDroppedExperience(0);
            }
        });
    }

    private static <T extends Event> void hook(Class<T> type) {
        SEEN.put(type, new ArrayList<>());
        CarianBus.on(type, EventPriority.NORMAL, false, e -> {
            SEEN.get(type).add(e);
            Consumer<Event> h = HANDLERS.get(type);
            if (h != null) {
                h.accept(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event> List<T> seen(Class<T> type) {
        return (List<T>) (List<?>) SEEN.get(type);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event> void handle(Class<T> type, Consumer<T> handler) {
        HANDLERS.put(type, e -> handler.accept((T) e));
    }

    private static <T extends Event> int count(Class<T> type, Predicate<T> filter) {
        int n = 0;
        for (T e : seen(type)) {
            if (filter.test(e)) {
                n++;
            }
        }
        return n;
    }

    private static Ev fire(String stage, Consumer<Ev> listener, LivingEntity e, DamageSource src, float amount) {
        Ev ev = new Ev(stage, e, src, amount, depth + 1);
        LOG.add(label(e) + ":" + stage + ":" + amount);
        if (listener != null) {
            depth++;
            try {
                listener.accept(ev);
            } finally {
                depth--;
            }
        }
        return ev;
    }

    static String label(Object e) {
        String s = LABELS.get(e);
        if (s != null) {
            return s;
        }
        return e instanceof Entity en ? en.getType().toShortString() : String.valueOf(e);
    }

    private static void reset() {
        onAttack = null;
        onHurt = null;
        onDamage = null;
        onDeath = null;
        critMultiplier = -1F;
        lastVanillaCrit = null;
        lastInitialMultiplier = Float.NaN;
        critEvents = 0;
        LOG.clear();
        HANDLERS.clear();
        for (List<Event> l : SEEN.values()) {
            l.clear();
        }
    }

    // ==================== 推进 ====================

    private static void onStarted(MinecraftServer s) {
        server = s;
        level = s.overworld();
        tick = 0;
        OUT.info("[SELFTEST] server_started_event PASS overworld={}", level != null);
        pass++;
    }

    private static void onTick(MinecraftServer s) {
        if (tick < 0) {
            return;
        }
        tick++;
        try {
            if (tick == 20) {
                injectionChecks();
                enchantCategoryChecks();
                setup();
            } else if (tick == 30) {
                damageTests();
                absorptionSkipTests();
                dieTests();
                critTests();
                chargeTests();
                enchantLevelTests();
                eventTests();
                startAsyncTests();
                reset();
                recordTicks = true;
            } else if (tick == 40) {
                recordTicks = false;
                tickOrderCheck();
                expiredCheck();
                arrowCheck();
                ok("rock_blaster_mark_cleared_at_tick_end", !RockBlasterExperience.consume(level, STALE_MARK), "still marked");
            } else if (tick == 90) {
                dieFollowUp();
                zoglinCheck();
            } else if (tick == 250) {
                dragonCheck();
                OUT.info("[SELFTEST] summary logic pass={} fail={}", pass, fail);
                for (Entity e : SPAWNED) {
                    if (!e.isRemoved()) {
                        e.discard();
                    }
                }
                tick = -1;
                s.halt(false);
            }
        } catch (Throwable t) {
            OUT.error("[SELFTEST] crashed FAIL at tick {}", tick, t);
            tick = -1;
            s.halt(false);
        }
    }

    // ==================== 工具 ====================

    private static void ok(String name, boolean cond, Object actual) {
        if (cond) {
            pass++;
        } else {
            fail++;
        }
        OUT.info("[SELFTEST] {}{} {} {}", name, suffix, cond ? "PASS" : "FAIL", actual);
    }

    private static void info(String name, Object actual) {
        OUT.info("[SELFTEST] {} INFO {}", name, actual);
    }

    private static boolean eq(float a, float b) {
        return Math.abs(a - b) < 1e-4F;
    }

    private static <T extends Mob> T spawn(EntityType<T> type, String label, double dx) {
        return spawnAt(type, label, dx, 1, 0);
    }

    private static <T extends Mob> T spawnAt(EntityType<T> type, String label, double dx, double dy, double dz) {
        T e = type.create(level);
        BlockPos sp = level.getSharedSpawnPos();
        e.moveTo(sp.getX() + 0.5 + dx, sp.getY() + dy, sp.getZ() + 0.5 + dz, 0F, 0F);
        e.setNoAi(true);
        e.setPersistenceRequired();
        level.addFreshEntity(e);
        SPAWNED.add(e);
        LABELS.put(e, label);
        return e;
    }

    private static Villager villager(String label) {
        return spawn(EntityType.VILLAGER, label, 0);
    }

    private static TestPlayer player(String name) {
        TestPlayer p = new TestPlayer(level, name);
        BlockPos sp = level.getSharedSpawnPos();
        p.moveTo(sp.getX() + 0.5, sp.getY() + 5, sp.getZ() + 2.5, 0F, 0F);
        LABELS.put(p, name);
        return p;
    }

    private static void giveAbsorption(LivingEntity e, float value) {
        e.setAbsorptionAmount(value);
    }

    private static DamageSource generic() {
        return level.damageSources().generic();
    }

    private static boolean logHas(String prefix) {
        for (String s : LOG) {
            if (s.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static int logCount(String part) {
        int n = 0;
        for (String s : LOG) {
            if (s.contains(part)) {
                n++;
            }
        }
        return n;
    }

    private static String log() {
        return String.join(" ", LOG);
    }

    private static int orbValue(AABB box) {
        int sum = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            sum += orb.getValue();
        }
        return sum;
    }

    // ==================== 第 1 步：注入点全部应用、准备实体 ====================

    /**
     * 每个目标类先加载一遍（mixin 在类加载时应用，配置 defaultRequire = 1，有一处找不到注入点就会在这里抛错），
     * 再数合进去的处理器方法个数。
     */
    private static void injectionChecks() {
        Map<String, Integer> expect = new LinkedHashMap<>();
        // 12 个事件 / 伤害注入点，另有 common 的 addEffect 参数改写 1 个、访问器 5 个（静态访问器不合进目标类）
        expect.put("net.minecraft.world.entity.LivingEntity", 18);
        expect.put("net.minecraft.world.entity.player.Player", 10);
        expect.put("net.minecraft.server.level.ServerPlayer", 2);
        expect.put("net.minecraft.server.players.PlayerList", 2);
        expect.put("net.minecraft.world.entity.boss.enderdragon.EnderDragon", 2);
        expect.put("net.minecraft.world.entity.Mob", 1);
        expect.put("net.minecraft.world.entity.ai.behavior.StartAttacking", 1);
        expect.put("net.minecraft.world.entity.projectile.AbstractArrow", 1);
        expect.put("net.minecraft.server.level.ServerLevel", 1);
        expect.put("net.minecraft.world.level.entity.PersistentEntitySectionManager", 1);
        expect.put("net.minecraft.server.level.ServerLevel$EntityCallbacks", 1);
        expect.put("net.minecraft.world.level.block.Block", 1);
        // 主 mixin 配置：持久数据（取、存、读）、附魔台和 canEnchant 两处改问本模组附魔、登录握手的访问器
        expect.put("net.minecraft.world.entity.Entity", 3);
        // @WrapOperation 合进去两个方法：处理器和包住原调用的那个
        expect.put("net.minecraft.world.item.enchantment.EnchantmentHelper", 2);
        expect.put("net.minecraft.world.item.enchantment.Enchantment", 1);
        expect.put("net.minecraft.server.network.ServerLoginPacketListenerImpl", 1);
        int total = 0;
        StringBuilder detail = new StringBuilder();
        boolean all = true;
        for (Map.Entry<String, Integer> en : expect.entrySet()) {
            int n;
            try {
                Class<?> c = Class.forName(en.getKey(), true, LogicSelfTest.class.getClassLoader());
                n = 0;
                List<String> names = new ArrayList<>();
                for (Method m : c.getDeclaredMethods()) {
                    // 处理器合进目标类后名字里保留原方法名；末影龙那份另有一个私有辅助方法
                    if (m.getName().contains("carianstyle$") && !m.getName().endsWith("carianstyle$post")) {
                        n++;
                        names.add(m.getName());
                    }
                }
                detail.append(names).append(' ');
            } catch (Throwable t) {
                n = -1;
                detail.append(en.getKey()).append(" load failed: ").append(t).append("; ");
            }
            total += Math.max(n, 0);
            boolean good = n == en.getValue();
            all &= good;
            detail.append(en.getKey().substring(en.getKey().lastIndexOf('.') + 1)).append('=').append(n)
                    .append('/').append(en.getValue()).append(' ');
        }
        // 逻辑类 12 个类 26 个事件注入点（含生物 tick）+ 伤害三段和蓄力 9 个（含蓄力欠账兜底），另加上面 common 6 个、主配置 7 个
        ok("injections_all_applied", all && total == 48, "total=" + total + " " + detail);
    }

    /**
     * Fabric 上本模组附魔的 category 都是 BREAKABLE，真正的判定靠附魔台和 canEnchant 两个 mixin 改问附魔自己。
     * 没生效的话，挂 BREAKABLE 的附魔会按「可损耗物品」刷到镐、盾上，抽到的数比各附魔自己认的多。
     */
    private static void enchantCategoryChecks() {
        ItemStack[] items = {new ItemStack(Items.SHIELD), new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.BOW)};
        int mods = 0;
        int breakableHosted = 0;
        int canEnchantMismatch = 0;
        int tableMismatch = 0;
        int tableMod = 0;
        int breakableLoose = 0;
        for (Enchantment e : BuiltInRegistries.ENCHANTMENT) {
            if (!(e instanceof EnchantmentBase base)) {
                continue;
            }
            mods++;
            if (e.category == EnchantmentCategory.BREAKABLE) {
                breakableHosted++;
            }
            for (ItemStack stack : items) {
                if (e.canEnchant(stack) != base.canApplyAtEnchantingTable(stack)) {
                    canEnchantMismatch++;
                }
                if (e.category == EnchantmentCategory.BREAKABLE && EnchantmentCategory.BREAKABLE.canEnchant(stack.getItem())
                        && !base.canApplyAtEnchantingTable(stack)) {
                    breakableLoose++;
                }
            }
        }
        for (ItemStack stack : items) {
            for (EnchantmentInstance inst : EnchantmentHelper.getAvailableEnchantmentResults(30, stack, true)) {
                if (inst.enchantment instanceof EnchantmentBase base) {
                    tableMod++;
                    if (!base.canApplyAtEnchantingTable(stack)) {
                        tableMismatch++;
                    }
                }
            }
        }
        // breakableLoose > 0 说明这组物品里确实有「只看 BREAKABLE 会放行、附魔自己不认」的组合，下面两项才有区分力
        ok("enchant_can_enchant_asks_mod", mods > 100 && breakableLoose > 0 && canEnchantMismatch == 0,
                "mods=" + mods + " breakableHosted=" + breakableHosted + " breakableLoose=" + breakableLoose
                        + " mismatch=" + canEnchantMismatch);
        ok("enchant_table_asks_mod", tableMod > 0 && tableMismatch == 0,
                "tableMod=" + tableMod + " mismatch=" + tableMismatch);
    }

    private static void setup() {
        BlockPos sp = level.getSharedSpawnPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setChunkForced((sp.getX() >> 4) + dx, (sp.getZ() >> 4) + dz, true);
            }
        }
        // 几十个测试村民叠在同一格，超过 24 个会被挤死，关掉
        level.getGameRules().getRule(GameRules.RULE_MAX_ENTITY_CRAMMING).set(0, server);
        armored = spawn(EntityType.ZOMBIE, "armored", 3);
        armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        // 原版装备属性要等实体 tick 一次才生效
    }

    // ==================== 伤害三段 ====================

    private static void damageTests() {
        basicSuite();
        absorptionSuite();
        statSuite();
        nestedSuite();
        playerSuite();
        // 原版 FakePlayer：ServerPlayer.hurt 第一行 isInvulnerableTo 就返回，三段都不发
        {
            reset();
            net.fabricmc.fabric.api.entity.FakePlayer fp = net.fabricmc.fabric.api.entity.FakePlayer.get(level);
            LABELS.put(fp, "fakeplayer");
            boolean r = fp.hurt(generic(), 3F);
            ok("fakeplayer_no_stages", !r && LOG.isEmpty(), "ret=" + r + " log=[" + log() + "]");
        }
    }

    private static void basicSuite() {
        {
            reset();
            Villager v = villager("v_atk");
            onAttack = ev -> ev.canceled = true;
            boolean r = v.hurt(generic(), 3F);
            ok("attack_cancel_returns_false", !r, r);
            ok("attack_cancel_no_invuln", v.invulnerableTime == 0, v.invulnerableTime);
            ok("attack_cancel_no_later_stages", !logHas("v_atk:hurt") && !logHas("v_atk:damage") && eq(v.getHealth(), 20F), log() + " hp=" + v.getHealth());
        }
        {
            reset();
            Villager v = villager("v_hurtc");
            onHurt = ev -> ev.canceled = true;
            boolean r = v.hurt(generic(), 3F);
            ok("hurt_cancel_returns_true", r, r);
            ok("hurt_cancel_invuln_20", v.invulnerableTime == 20, v.invulnerableTime + " hurtTime=" + v.hurtTime);
            ok("hurt_cancel_no_health_loss_no_damage_stage", eq(v.getHealth(), 20F) && !logHas("v_hurtc:damage"), log() + " hp=" + v.getHealth());
        }
        {
            reset();
            Pig p = spawn(EntityType.PIG, "pig36", 1);
            onHurt = ev -> ev.amount = 6F;
            p.hurt(generic(), 3F);
            ok("hurt_set_3_to_6", eq(p.getHealth(), 4F) && logHas("pig36:damage:6.0"), log() + " hp=" + p.getHealth());
        }
        {
            reset();
            Villager v = villager("v_hurt0");
            onHurt = ev -> ev.amount = 0F;
            boolean r = v.hurt(generic(), 3F);
            ok("hurt_set_0_skips_rest", r && eq(v.getHealth(), 20F) && !logHas("v_hurt0:damage"), log() + " hp=" + v.getHealth());
        }
        {
            reset();
            Zombie attacker = EntityType.ZOMBIE.create(level);
            armored.setHealth(armored.getMaxHealth());
            armored.invulnerableTime = 0;
            float hp0 = armored.getHealth();
            armored.hurt(level.damageSources().mobAttack(attacker), 10F);
            ok("armor_hurt_stage_pre_armor", logHas("armored:hurt:10.0"), log() + " armor=" + armored.getArmorValue());
            float dmg = Float.NaN;
            for (String s : LOG) {
                if (s.startsWith("armored:damage:")) {
                    dmg = Float.parseFloat(s.substring(s.lastIndexOf(':') + 1));
                }
            }
            ok("armor_damage_stage_post_armor", dmg > 0F && dmg < 10F && eq(armored.getHealth(), hp0 - dmg),
                    log() + " hp0=" + hp0 + " hp=" + armored.getHealth());
        }
        {
            reset();
            Villager v = villager("v_iframe");
            v.hurt(generic(), 3F);
            boolean r2 = v.hurt(generic(), 9F);
            ok("iframe_second_hit_hurt_stage_6", r2 && logHas("v_iframe:hurt:6.0") && eq(v.getHealth(), 11F), log() + " hp=" + v.getHealth());
        }
        {
            reset();
            TestPlayer p = player("pl36");
            onHurt = ev -> ev.amount = 6F;
            boolean r = p.hurt(generic(), 3F);
            ok("player_hurt_set_3_to_6", r && eq(p.getHealth(), 14F) && logHas("pl36:damage:6.0"), log() + " hp=" + p.getHealth());
        }
    }

    private static void absorptionSuite() {
        {
            reset();
            Villager v = villager("abs1");
            giveAbsorption(v, 4F);
            float[] seenAbs = {-1F};
            onDamage = ev -> seenAbs[0] = ev.absorptionNow;
            v.hurt(generic(), 6F);
            ok("absorb_damage_stage_gets_2", logHas("abs1:damage:2.0") && eq(seenAbs[0], 0F) && eq(v.getHealth(), 18F),
                    log() + " absInStage=" + seenAbs[0] + " hp=" + v.getHealth());
        }
        {
            reset();
            Villager v = villager("abs2");
            giveAbsorption(v, 4F);
            onDamage = ev -> ev.amount = 5F;
            v.hurt(generic(), 6F);
            ok("absorb_set_5", eq(v.getHealth(), 15F) && eq(v.getAbsorptionAmount(), 0F), log() + " hp=" + v.getHealth() + " abs=" + v.getAbsorptionAmount());
        }
        {
            reset();
            Villager v = villager("abs3");
            giveAbsorption(v, 4F);
            onDamage = ev -> ev.canceled = true;
            v.hurt(generic(), 6F);
            ok("absorb_cancel", eq(v.getHealth(), 20F) && eq(v.getAbsorptionAmount(), 0F), log() + " hp=" + v.getHealth() + " abs=" + v.getAbsorptionAmount());
        }
        {
            reset();
            Villager v = villager("abs4");
            giveAbsorption(v, 4F);
            v.hurt(generic(), 3F);
            ok("absorb_zero_still_fires", logHas("abs4:damage:0.0") && eq(v.getHealth(), 20F) && eq(v.getAbsorptionAmount(), 1F),
                    log() + " hp=" + v.getHealth() + " abs=" + v.getAbsorptionAmount());
        }
    }

    /** 统计值 DAMAGE_DEALT_ABSORBED（攻击者是玩家时记）：吸收 4、打 6、伤害阶段改成 5，应该仍记 40（4 × 10） */
    private static void statSuite() {
        reset();
        TestPlayer atk = player("statatk");
        Villager v = villager("stat");
        giveAbsorption(v, 4F);
        onDamage = ev -> ev.amount = 5F;
        v.hurt(level.damageSources().playerAttack(atk), 6F);
        Integer st = atk.customStats.get(Stats.DAMAGE_DEALT_ABSORBED);
        ok("stat_dealt_absorbed_unchanged", st != null && st == 40 && eq(v.getHealth(), 15F),
                "stat=" + st + " hp=" + v.getHealth() + " log=" + log());
    }

    /** 伤害阶段里对另一个实体、对同一个实体再 hurt */
    private static void nestedSuite() {
        reset();
        Villager a = villager("nA");
        Pig b = spawn(EntityType.PIG, "nB", 1);
        boolean[] done = {false};
        boolean[] rets = new boolean[2];
        float[] outerAfter = {-1F};
        onDamage = ev -> {
            if (ev.entity == a && ev.depth == 1 && !done[0]) {
                done[0] = true;
                rets[0] = b.hurt(generic(), 2F);
                rets[1] = a.hurt(generic(), 6F);
                outerAfter[0] = ev.amount;
            }
        };
        a.hurt(generic(), 4F);
        String act = log() + " A.hp=" + a.getHealth() + " B.hp=" + b.getHealth() + " rets=" + rets[0] + "," + rets[1];
        ok("nested_other_entity_all_stages", rets[0] && logHas("nB:attack:2.0") && logHas("nB:hurt:2.0") && logHas("nB:damage:2.0") && eq(b.getHealth(), 8F), act);
        ok("nested_same_entity_gets_iframe_delta", rets[1] && logHas("nA:hurt:2.0") && logHas("nA:damage:2.0"), act);
        ok("nested_outer_keeps_own_value", eq(outerAfter[0], 4F) && eq(a.getHealth(), 14F)
                && LOG.get(LOG.size() - 1).equals("nA:damage-out:4.0"), act);
    }

    private static void playerSuite() {
        {
            reset();
            TestPlayer p = player("pl_atk");
            onAttack = ev -> ev.canceled = true;
            boolean r = p.hurt(generic(), 3F);
            ok("player_attack_cancel", !r && p.invulnerableTime == 0 && eq(p.getHealth(), 20F) && logCount(":attack:") == 1,
                    "ret=" + r + " inv=" + p.invulnerableTime + " " + log());
        }
        {
            reset();
            TestPlayer p = player("pl_hurtc");
            onHurt = ev -> ev.canceled = true;
            boolean r = p.hurt(generic(), 3F);
            ok("player_hurt_cancel", r && p.invulnerableTime == 20 && eq(p.getHealth(), 20F) && !logHas("pl_hurtc:damage"), "ret=" + r + " inv=" + p.invulnerableTime + " " + log());
        }
        {
            reset();
            TestPlayer p = player("pl_abs");
            giveAbsorption(p, 4F);
            float[] seenAbs = {-1F};
            onDamage = ev -> {
                seenAbs[0] = ev.absorptionNow;
                ev.amount = 5F;
            };
            p.hurt(generic(), 6F);
            Integer st = p.customStats.get(Stats.DAMAGE_ABSORBED);
            ok("player_absorb_stage_and_stat", logHas("pl_abs:damage:2.0") && eq(seenAbs[0], 0F) && eq(p.getHealth(), 15F) && st != null && st == 40,
                    log() + " absInStage=" + seenAbs[0] + " hp=" + p.getHealth() + " DAMAGE_ABSORBED=" + st);
        }
    }

    /**
     * 跳过第二次吸收扣减：吸收 4、打 3（全被吸收，伤害段拿到 0）、伤害段改成 5 → 扣 5 血、吸收剩 1。
     * 原版不跳过时第二次 setAbsorptionAmount(1 − 5) 会把剩下的 1 也扣掉。玩家只有一次扣减，原版就是剩 1。
     */
    private static void absorptionSkipTests() {
        {
            reset();
            Villager v = villager("abs_skip");
            giveAbsorption(v, 4F);
            onDamage = ev -> ev.amount = 5F;
            v.hurt(generic(), 3F);
            // Forge 1.20.1 留着扣血后的第二次扣吸收：1 - 5 夹到 0（1.21.1 对齐 NeoForge 跳过，剩 1）
            ok("absorb_zero_raised_to_5_second_deduction", logHas("abs_skip:damage:0.0") && eq(v.getHealth(), 15F) && eq(v.getAbsorptionAmount(), 0F),
                    log() + " hp=" + v.getHealth() + " abs=" + v.getAbsorptionAmount());
        }
        {
            reset();
            TestPlayer p = player("pl_abs_skip");
            giveAbsorption(p, 4F);
            onDamage = ev -> ev.amount = 5F;
            p.hurt(generic(), 3F);
            ok("player_absorb_zero_raised_to_5_vanilla", logHas("pl_abs_skip:damage:0.0") && eq(p.getHealth(), 15F) && eq(p.getAbsorptionAmount(), 1F),
                    log() + " hp=" + p.getHealth() + " abs=" + p.getAbsorptionAmount());
        }
    }

    // ==================== 死亡 ====================

    private static void dieTests() {
        {
            reset();
            Villager v = villager("die_c");
            v.setHealth(2F);
            onDeath = ev -> {
                ev.canceled = true;
                ev.entity.setHealth(10F);
            };
            boolean r = v.hurt(generic(), 5F);
            ok("die_cancel_mob", r && v.isAlive() && !v.isDeadOrDying() && eq(v.getHealth(), 10F) && logCount(":death:") == 1,
                    "ret=" + r + " alive=" + v.isAlive() + " hp=" + v.getHealth() + " " + log());
            dieCancelled = v;
        }
        {
            reset();
            Villager v = villager("die_n");
            v.setHealth(2F);
            v.hurt(generic(), 5F);
            ok("die_normal_mob", v.isDeadOrDying() && logCount(":death:") == 1, "hp=" + v.getHealth() + " " + log());
        }
        {
            reset();
            TestPlayer p = player("die_p");
            p.setHealth(2F);
            onDeath = ev -> {
                ev.canceled = true;
                ev.entity.setHealth(10F);
            };
            boolean r = p.hurt(generic(), 5F);
            ok("die_cancel_player", r && !p.isDeadOrDying() && eq(p.getHealth(), 10F) && logCount(":death:") == 1,
                    "ret=" + r + " hp=" + p.getHealth() + " " + log());
        }
        // ServerPlayer.die 直接调：死亡事件在 gameEvent(ENTITY_DIE) 之后、可取消；取消后不记死亡统计
        {
            reset();
            TestPlayer p = player("die_sp_c");
            onDeath = ev -> ev.canceled = true;
            p.die(generic());
            int ge = LOG.indexOf("die_sp_c:gameEvent:entity_die");
            int de = -1;
            for (int i = 0; i < LOG.size(); i++) {
                if (LOG.get(i).startsWith("die_sp_c:death:")) {
                    de = i;
                }
            }
            ok("server_player_die_after_game_event_cancelable", ge >= 0 && de > ge && logCount(":death:") == 1
                            && !p.customStats.containsKey(Stats.DEATHS),
                    "gameEventIdx=" + ge + " deathIdx=" + de + " deaths=" + p.customStats.get(Stats.DEATHS) + " " + log());
        }
        {
            reset();
            TestPlayer p = player("die_sp_n");
            p.die(generic());
            ok("server_player_die_once_not_canceled", logCount(":death:") == 1 && Integer.valueOf(1).equals(p.customStats.get(Stats.DEATHS)),
                    "deaths=" + p.customStats.get(Stats.DEATHS) + " " + log());
        }
        // 非服务端玩家：Player.die 一次 + super 进 LivingEntity.die 一次，同 Forge
        {
            reset();
            Player p = plainPlayer("die_plain");
            p.die(generic());
            int n = logCount("die_plain:death:");
            reset();
            Player q = plainPlayer("die_plain_c");
            onDeath = ev -> ev.canceled = true;
            q.die(generic());
            int m = logCount("die_plain_c:death:");
            ok("plain_player_die_twice_like_neoforge", n == 2 && m == 1, "notCanceled=" + n + " canceledAtFirst=" + m);
        }
    }

    private static Player plainPlayer(String name) {
        Player p = new Player(level, level.getSharedSpawnPos().above(5), 0F,
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name)) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        LABELS.put(p, name);
        return p;
    }

    private static void dieFollowUp() {
        Villager v = dieCancelled;
        ok("die_cancel_mob_still_alive_60_ticks_later", v != null && v.isAlive() && !v.isRemoved() && v.deathTime == 0,
                v == null ? "null" : "alive=" + v.isAlive() + " removed=" + v.isRemoved() + " deathTime=" + v.deathTime + " hp=" + v.getHealth());
    }

    // ==================== 暴击 ====================

    private static float attackOnce(String label, float mul, boolean airborne) {
        return attackOnce(label, mul, airborne, false);
    }

    private static float attackOnce(String label, float mul, boolean airborne, boolean sprinting) {
        reset();
        critMultiplier = mul;
        TestPlayer p = player("crit_" + label);
        Villager v = villager("cv_" + label);
        if (airborne) {
            p.fallDistance = 1F;
            p.setOnGround(false);
        } else {
            p.setOnGround(true);
        }
        p.setSprinting(sprinting);
        p.fullCharge();
        p.attack(v);
        for (String s : LOG) {
            if (s.startsWith("cv_" + label + ":hurt:")) {
                return Float.parseFloat(s.substring(s.lastIndexOf(':') + 1));
            }
        }
        return Float.NaN;
    }

    /**
     * 事件类没有改「是否暴击」的接口（本模组的监听只改倍率），所以「强制暴击 / 压掉暴击」不验，
     * 改验倍率初值和「倍率只在暴击时生效」（同 Forge）。
     */
    private static void critTests() {
        float a = attackOnce("default", -1F, false);
        ok("crit_default_no_crit", eq(a, 1F) && Boolean.FALSE.equals(lastVanillaCrit) && critEvents == 1 && eq(lastInitialMultiplier, 1.0F),
                a + " vanilla=" + lastVanillaCrit + " events=" + critEvents + " initMul=" + lastInitialMultiplier);
        float d = attackOnce("vanilla_air", -1F, true);
        ok("crit_vanilla_airborne_detected", eq(d, 1.5F) && Boolean.TRUE.equals(lastVanillaCrit) && critEvents == 1 && eq(lastInitialMultiplier, 1.5F),
                d + " vanilla=" + lastVanillaCrit + " events=" + critEvents + " initMul=" + lastInitialMultiplier);
        float c = attackOnce("air_mul3", 3F, true);
        ok("crit_multiplier_3", eq(c, 3F), c);
        float e = attackOnce("ground_mul3", 3F, false);
        ok("crit_multiplier_ignored_when_not_crit", eq(e, 1F) && Boolean.FALSE.equals(lastVanillaCrit), e + " vanilla=" + lastVanillaCrit);
        // 1.20.1 的暴击布尔存两次，第二次才 && !isSprinting()：挂错成第一次的话这里会读到 true
        float f = attackOnce("air_sprint", -1F, true, true);
        ok("crit_airborne_sprinting_not_crit", eq(f, 1F) && Boolean.FALSE.equals(lastVanillaCrit) && critEvents == 1,
                f + " vanilla=" + lastVanillaCrit + " events=" + critEvents);
        reset();
    }

    // ==================== 攻击蓄力重置时机 ====================

    private static void chargeTests() {
        {
            reset();
            TestPlayer p = player("chg");
            Villager a = spawn(EntityType.VILLAGER, "chgA", 16);
            Villager b = spawn(EntityType.VILLAGER, "chgB", 17);
            p.moveTo(a.getX(), a.getY(), a.getZ() + 1.5, 180F, 0F);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            p.setOnGround(true);
            p.setSprinting(false);
            p.fallDistance = 0F;
            p.setSpeed(0.1F);
            p.fullCharge();
            float[] minMax = {Float.MAX_VALUE, -1F};
            int[] count = {0};
            List<String> seenScale = new ArrayList<>();
            Consumer<Ev> rec = ev -> {
                if (ev.source.getEntity() == p) {
                    float sc = p.getAttackStrengthScale(0.5F);
                    minMax[0] = Math.min(minMax[0], sc);
                    minMax[1] = Math.max(minMax[1], sc);
                    count[0]++;
                    seenScale.add(label(ev.entity) + ":" + ev.stage + "=" + sc);
                }
            };
            onAttack = rec;
            onHurt = rec;
            onDamage = rec;
            int before = p.strengthTicker();
            p.attack(a);
            int after = p.strengthTicker();
            boolean swept = logHas("chgB:attack");
            String act = "events=" + count[0] + " min=" + minMax[0] + " max=" + minMax[1] + " swept=" + swept
                    + " ticker " + before + "->" + after + " scaleAfter=" + p.getAttackStrengthScale(0.5F) + " " + seenScale;
            ok("charge_in_events_full_deferred", count[0] == 6 && swept && minMax[0] > 0.9F, act);
            ok("charge_reset_after_attack_deferred", after == 0 && p.getAttackStrengthScale(0.5F) < 0.9F, act);
        }
        // 伤害 ≤ 0 那条：原版提前返回时重置已经做过，挪到末尾后照样重置
        {
            reset();
            TestPlayer p = player("chg_zero");
            Villager v = spawn(EntityType.VILLAGER, "chg_zero_v", 24);
            p.moveTo(v.getX(), v.getY(), v.getZ() + 1.5, 180F, 0F);
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
            p.fullCharge();
            p.attack(v);
            ok("charge_zero_damage_path_still_resets", p.strengthTicker() == 0 && !logHas("chg_zero_v:"),
                    "ticker=" + p.strengthTicker() + " log=" + log());
        }
        // 目标不可攻击（掉落物）：原版直接返回、从来不重置，挪了以后也不重置
        {
            reset();
            TestPlayer p = player("chg_item");
            ItemEntity item = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.STICK));
            p.fullCharge();
            p.attack(item);
            ok("charge_not_attackable_no_reset", p.strengthTicker() == 1000, "ticker=" + p.strengthTicker());
        }
        // 嵌套：攻击阶段里让同一个玩家再打另一个目标，外层伤害段读到的蓄力
        {
            reset();
            TestPlayer p = player("chg_nest");
            Villager a = spawn(EntityType.VILLAGER, "chg_nestA", 28);
            Villager b = spawn(EntityType.VILLAGER, "chg_nestB", 32);
            p.moveTo(a.getX(), a.getY(), a.getZ() + 1.5, 180F, 0F);
            p.fullCharge();
            boolean[] once = {false};
            float[] outerDamageScale = {-1F};
            onAttack = ev -> {
                if (ev.entity == a && !once[0]) {
                    once[0] = true;
                    p.attack(b);
                }
            };
            onDamage = ev -> {
                if (ev.entity == a) {
                    outerDamageScale[0] = p.getAttackStrengthScale(0.5F);
                }
            };
            p.attack(a);
            info("charge_nested_same_player", "outerDamageStageScale=" + outerDamageScale[0] + " tickerAfter=" + p.strengthTicker() + " log=" + log());
        }
        // 攻击在 hurt 处被打断（目标 hurt 抛异常，等同别的模组在那里中止 attack）：RETURN 补调走不到，
        // 下一次玩家 tick 开头补上，tick 末尾照常 +1
        {
            reset();
            Player p = plainPlayer("chg_abort");
            Villager v = new Villager(EntityType.VILLAGER, level) {
                @Override
                public boolean hurt(DamageSource source, float amount) {
                    throw new IllegalStateException("abort");
                }
            };
            v.moveTo(p.getX(), p.getY(), p.getZ() + 1.5, 0F, 0F);
            setStrengthTicker(p, 1000);
            boolean threw = false;
            try {
                p.attack(v);
            } catch (IllegalStateException e) {
                threw = true;
            }
            int mid = strengthTicker(p);
            String tickErr = "";
            try {
                p.tick();
            } catch (Throwable t) {
                tickErr = " (tick threw " + t + ")";
            }
            int after = strengthTicker(p);
            ok("charge_reset_owed_after_abort", threw && mid == 1000 && after == 1,
                    "threw=" + threw + " ticker 1000->" + mid + "->" + after + tickErr);
        }
        reset();
    }

    private static java.lang.reflect.Field strengthTickerField() throws NoSuchFieldException {
        for (Class<?> c = Player.class; c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("attackStrengthTicker");
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // 往父类找
            }
        }
        throw new NoSuchFieldException("attackStrengthTicker");
    }

    private static void setStrengthTicker(Player p, int value) {
        try {
            strengthTickerField().setInt(p, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int strengthTicker(Player p) {
        try {
            return strengthTickerField().getInt(p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== 附魔等级 ====================

    private static void enchantLevelTests() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 2);
        int byItem = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, sword);
        ok("ench_level_item", byItem == 2, byItem);

        ItemStack back = ItemStack.of(sword.save(new CompoundTag()));
        int backLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, back);
        ok("ench_level_roundtrip", backLevel == 2, backLevel);

        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 3));
        int bookEnch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, book);
        int bookStored = EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(book))
                .getOrDefault(Enchantments.SHARPNESS, 0);
        ok("ench_level_book_stored", bookEnch == 0 && bookStored == 3, "Enchantments=" + bookEnch + " Stored=" + bookStored);

        TestPlayer p = player("ench_holder");
        p.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
        int byEntity = EnchantmentHelper.getEnchantmentLevel(Enchantments.SHARPNESS, p);
        ok("ench_level_by_entity_slot", byEntity == 2, byEntity);
    }

    // ==================== 其余逻辑事件逐个触发 ====================

    private static void eventTests() {
        healTests();
        knockBackTests();
        effectTests();
        joinLeaveTests();
        changeTargetTests();
        experienceTests();
        blockTests();
        playerTickTests();
        livingTickTests();
    }

    private static void healTests() {
        reset();
        Villager v = villager("heal");
        v.setHealth(10F);
        handle(LivingHealEvent.class, e -> e.setAmount(5F));
        v.heal(2F);
        float h1 = v.getHealth();
        handle(LivingHealEvent.class, e -> e.setCanceled(true));
        v.heal(2F);
        float h2 = v.getHealth();
        handle(LivingHealEvent.class, e -> e.setAmount(-1F));
        v.heal(2F);
        float h3 = v.getHealth();
        int n = count(LivingHealEvent.class, e -> e.getEntity() == v);
        ok("heal_set_cancel_nonpositive", eq(h1, 15F) && eq(h2, 15F) && eq(h3, 15F) && n == 3,
                "h=" + h1 + "," + h2 + "," + h3 + " events=" + n);
    }

    private static void knockBackTests() {
        reset();
        Villager v = villager("kb");
        float[] got = {-1F};
        handle(LivingKnockBackEvent.class, e -> {
            got[0] = e.getStrength();
            e.setStrength(0F);
        });
        v.setDeltaMovement(0, 0, 0);
        v.knockback(0.4, 1.0, 0.0);
        double vx0 = v.getDeltaMovement().x;
        reset();
        Villager w = villager("kb2");
        handle(LivingKnockBackEvent.class, e -> e.setStrength(0.8F));
        w.setDeltaMovement(0, 0, 0);
        w.knockback(0.4, 1.0, 0.0);
        double vx1 = w.getDeltaMovement().x;
        ok("knockback_strength_read_and_set", eq(got[0], 0.4F) && vx0 == 0.0 && vx1 < -0.79 && vx1 > -0.81,
                "seen=" + got[0] + " vxZeroed=" + vx0 + " vxDoubled=" + vx1);
    }

    private static void effectTests() {
        reset();
        Villager v = villager("eff");
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        int added = count(MobEffectEvent.Added.class, e -> e.getEntity() == v && e.getEffectInstance().getEffect() == MobEffects.MOVEMENT_SPEED);
        v.forceAddEffect(new MobEffectInstance(MobEffects.JUMP, 200), null);
        int addedAfterForce = count(MobEffectEvent.Added.class, e -> e.getEntity() == v);
        ok("effect_added_not_on_force", added == 1 && addedAfterForce == 1, "added=" + added + " afterForce=" + addedAfterForce);

        v.removeEffect(MobEffects.MOVEMENT_SPEED);
        int rmPresent = count(MobEffectEvent.Remove.class, e -> e.getEntity() == v && e.getEffect() == MobEffects.MOVEMENT_SPEED && e.getEffectInstance() != null);
        v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        int rmAbsent = count(MobEffectEvent.Remove.class, e -> e.getEntity() == v && e.getEffect() == MobEffects.MOVEMENT_SLOWDOWN && e.getEffectInstance() == null);
        ok("effect_remove_single_even_if_absent", rmPresent == 1 && rmAbsent == 1, "present=" + rmPresent + " absent=" + rmAbsent);

        reset();
        v.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200));
        v.removeAllEffects();
        int rmAll = count(MobEffectEvent.Remove.class, e -> e.getEntity() == v && e.getEffectInstance() != null);
        ok("effect_remove_all_each", rmAll == 2 && v.getActiveEffects().isEmpty(), "events=" + rmAll + " left=" + v.getActiveEffects());

        expiring = villager("eff_exp");
        expiring.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3));
    }

    private static void expiredCheck() {
        int n = count(MobEffectEvent.Expired.class, e -> e.getEntity() == expiring && e.getEffectInstance().getEffect() == MobEffects.MOVEMENT_SPEED);
        ok("effect_expired_once", n == 1 && !expiring.hasEffect(MobEffects.MOVEMENT_SPEED), "events=" + n);
    }

    private static void joinLeaveTests() {
        reset();
        Villager v = villager("join");
        int join = count(EntityJoinLevelEvent.class, e -> e.getEntity() == v && e.getLevel() == level && !e.loadedFromDisk());
        v.discard();
        int leave = count(EntityLeaveLevelEvent.class, e -> e.getEntity() == v && e.getLevel() == level);
        ok("join_leave_mob", join == 1 && leave == 1, "join=" + join + " leave=" + leave);

        // 玩家加入世界：ServerLevel.addPlayer 开头发一次，后面经过实体管理器那一处跳过，不重复
        reset();
        TestPlayer p = player("join_p");
        String act;
        boolean good;
        try {
            level.addNewPlayer(p);
            int pj = count(EntityJoinLevelEvent.class, e -> e.getEntity() == p);
            boolean pjLevel = count(EntityJoinLevelEvent.class, e -> e.getEntity() == p && e.getLevel() == level && !e.loadedFromDisk()) == 1;
            level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            int pl = count(EntityLeaveLevelEvent.class, e -> e.getEntity() == p && e.getLevel() == level);
            good = pj == 1 && pjLevel && pl == 1;
            act = "join=" + pj + " leave=" + pl;
        } catch (Throwable t) {
            good = false;
            act = "threw " + t;
        }
        ok("join_leave_player_once", good, act);
    }

    private static void changeTargetTests() {
        reset();
        Zombie z = spawn(EntityType.ZOMBIE, "tgt_z", 5);
        Villager v = villager("tgt_v");
        handle(LivingChangeTargetEvent.class, e -> e.setCanceled(true));
        z.setTarget(v);
        LivingEntity t1 = z.getTarget();
        HANDLERS.clear();
        z.setTarget(v);
        LivingEntity t2 = z.getTarget();
        int n = count(LivingChangeTargetEvent.class, e -> e.getEntity() == z && e.getNewAboutToBeSetTarget() == v);
        ok("change_target_mob_cancel_keeps_old", t1 == null && t2 == v && n == 2, "t1=" + t1 + " t2=" + label(t2) + " events=" + n);
    }

    private static void experienceTests() {
        reset();
        TestPlayer atk = player("xp_atk");
        Zombie z = spawnAt(EntityType.ZOMBIE, "xp_z", 25, 1, 25);
        int[] orig = {-1};
        Player[] who = {null};
        handle(LivingExperienceDropEvent.class, e -> {
            if (e.getEntity() == z) {
                orig[0] = e.getDroppedExperience();
                who[0] = e.getAttackingPlayer();
                e.setDroppedExperience(7);
            }
        });
        z.hurt(level.damageSources().playerAttack(atk), 1000F);
        int orbs = orbValue(new AABB(z.blockPosition()).inflate(2));
        ok("experience_drop_mob_set_7", orig[0] > 0 && who[0] == atk && orbs == 7, "orig=" + orig[0] + " attacker=" + label(who[0]) + " orbs=" + orbs);
    }

    private static void blockTests() {
        reset();
        BlockPos sp = level.getSharedSpawnPos();
        BlockPos spawnerPos = sp.offset(-25, 3, 25);
        // 幽匿催发体固定掉 5 点经验：标过的那一下减半成 2，标记用掉后下一下照原样 5
        BlockState catalyst = Blocks.SCULK_CATALYST.defaultBlockState();
        level.setBlock(spawnerPos, catalyst, 3);
        RockBlasterExperience.mark(level, spawnerPos);
        catalyst.spawnAfterBreak(level, spawnerPos, ItemStack.EMPTY, true);
        int halved = orbValue(new AABB(spawnerPos).inflate(2));
        catalyst.spawnAfterBreak(level, spawnerPos, ItemStack.EMPTY, true);
        int both = orbValue(new AABB(spawnerPos).inflate(2));
        ok("rock_blaster_experience_halved_once", halved == 2 && both - halved == 5, "halved=" + halved + " next=" + (both - halved));
        level.setBlock(spawnerPos, Blocks.AIR.defaultBlockState(), 3);
        // 用不掉的标记（精准采集不放经验）tick 末尾清空，tick 40 再看
        RockBlasterExperience.mark(level, STALE_MARK);

        reset();
        TestPlayer p = player("breaker");
        BlockPos stonePos = sp.offset(-28, 3, 25);
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);
        handle(BlockEvent.BreakEvent.class, e -> e.setCanceled(true));
        boolean r1 = p.gameMode.destroyBlock(stonePos);
        boolean still = level.getBlockState(stonePos).is(Blocks.STONE);
        HANDLERS.clear();
        boolean r2 = p.gameMode.destroyBlock(stonePos);
        boolean gone = level.getBlockState(stonePos).isAir();
        int n = count(BlockEvent.BreakEvent.class, e -> e.getPos().equals(stonePos) && e.getPlayer() == p && e.getState().is(Blocks.STONE));
        ok("block_break_cancel_then_allow", !r1 && still && r2 && gone && n == 2, "r1=" + r1 + " still=" + still + " r2=" + r2 + " gone=" + gone + " events=" + n);
    }

    private static void livingTickTests() {
        reset();
        Villager v = villager("ltick");
        tickTarget = v;
        tickSeen = 0;
        v.hurtTime = 5;
        tickCancel = true;
        v.tick();
        int seenCanceled = tickSeen;
        int hurtCanceled = v.hurtTime;
        tickCancel = false;
        v.tick();
        tickTarget = null;
        ok("living_tick_event_cancel_skips_tick", seenCanceled == 1 && hurtCanceled == 5 && tickSeen == 2 && v.hurtTime == 4,
                "seen=" + seenCanceled + "/" + tickSeen + " hurtTime=" + hurtCanceled + "/" + v.hurtTime);
    }

    private static void playerTickTests() {
        reset();
        Player p = plainPlayer("tick_plain");
        String act;
        int n;
        try {
            p.tick();
            n = count(PlayerTickEvent.Pre.class, e -> e.getEntity() == p);
            act = "events=" + n;
        } catch (Throwable t) {
            n = count(PlayerTickEvent.Pre.class, e -> e.getEntity() == p);
            act = "events=" + n + " (tick threw " + t + ")";
        }
        ok("player_tick_pre", n == 1, act);
    }

    // ==================== 要过几 tick 的 ====================

    private static void startAsyncTests() {
        // 箭射中村民：命中事件（不发 MISS）+ 三段
        // 放在出生点上方 60 格的空中，路径上不会有方块
        BlockPos sp = level.getSharedSpawnPos();
        arrowTarget = spawnAt(EntityType.VILLAGER, "arrow_v", 0.0, 60, -14);
        arrow = new Arrow(level, sp.getX() + 0.5, sp.getY() + 61.0, sp.getZ() + 0.5 - 18);
        arrow.shoot(0, 0, 1, 3.0F, 0F);
        LABELS.put(arrow, "arrow");
        level.addFreshEntity(arrow);
        SPAWNED.add(arrow);

        // 僵尸疣猪兽：大脑记忆选目标那条路（StartAttacking），常驻监听一律取消
        zoglin = EntityType.ZOGLIN.create(level);
        zoglin.moveTo(sp.getX() + 0.5 + 2, sp.getY() + 1, sp.getZ() + 0.5 + 2, 0F, 0F);
        zoglin.setPersistenceRequired();
        level.addFreshEntity(zoglin);
        SPAWNED.add(zoglin);
        LABELS.put(zoglin, "zoglin");

        // 末影龙死亡动画：血量置 0，200 tick 里分批给经验（两处 award）
        dragon = EntityType.ENDER_DRAGON.create(level);
        dragon.moveTo(sp.getX() + 0.5 + 20, sp.getY() + 30, sp.getZ() + 0.5 - 25, 0F, 0F);
        dragon.setNoAi(true);
        level.addFreshEntity(dragon);
        SPAWNED.add(dragon);
        dragon.setHealth(0F);
    }

    private static void tickOrderCheck() {
        String seq = TICK_SEQ.toString();
        boolean alternating = seq.equals("<>".repeat(10));
        ok("server_tick_pre_post_alternate", alternating, seq);
    }

    private static void arrowCheck() {
        List<ProjectileImpactEvent> hits = new ArrayList<>();
        for (ProjectileImpactEvent e : seen(ProjectileImpactEvent.class)) {
            if (e.getProjectile() == arrow) {
                hits.add(e);
            }
        }
        boolean noMiss = true;
        boolean entityHit = false;
        for (ProjectileImpactEvent e : hits) {
            noMiss &= e.getRayTraceResult().getType() != HitResult.Type.MISS;
            entityHit |= e.getRayTraceResult().getType() == HitResult.Type.ENTITY && e.getEntity() == arrow;
        }
        // 三段只看这只村民、伤害直接来源是这支箭的
        boolean atk = false;
        boolean hurt = false;
        boolean dmg = false;
        for (String s : LOG) {
            atk |= s.startsWith("arrow_v:attack:");
            hurt |= s.startsWith("arrow_v:hurt:");
            dmg |= s.startsWith("arrow_v:damage:");
        }
        ok("arrow_hits_villager_impact_and_three_stages", !hits.isEmpty() && noMiss && entityHit && atk && hurt && dmg && arrowTarget.getHealth() < 20F,
                "impacts=" + hits.size() + " noMiss=" + noMiss + " entityHit=" + entityHit + " stages=" + atk + hurt + dmg
                        + " hp=" + arrowTarget.getHealth()
                        + " arrowPos=" + arrow.position() + " arrowRemoved=" + arrow.isRemoved()
                        + " arrowTicking=" + level.isPositionEntityTicking(arrow.blockPosition())
                        + " targetPos=" + arrowTarget.position() + " log=" + log());
    }

    private static void zoglinCheck() {
        boolean noTarget = zoglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty();
        ok("change_target_start_attacking_cancel", zoglinTargetEvents >= 1 && noTarget,
                "events=" + zoglinTargetEvents + " attackTargetMemoryEmpty=" + noTarget);
    }

    private static void dragonCheck() {
        int forty = 0;
        int hundred = 0;
        for (int x : DRAGON_XP) {
            if (x == 40) {
                forty++;
            } else if (x == 100) {
                hundred++;
            }
        }
        int orbs = orbValue(dragon.getBoundingBox().inflate(16));
        ok("experience_drop_dragon_both_awards", forty == 10 && hundred == 1 && DRAGON_XP.size() == 11 && dragon.isRemoved() && orbs == 0,
                "xp=" + DRAGON_XP + " removed=" + dragon.isRemoved() + " orbs=" + orbs);
    }
}
