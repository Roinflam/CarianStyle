package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.enchantment.EnchantmentAeonia;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 猩红腐烂药水效果
 *
 * 效果：
 * - 护甲和韧性减少50%
 * - 治疗量减少25%
 * - 每秒造成持续伤害
 * - 与艾奥尼亚附魔联动：伤害增强、传播效果
 *
 * <h3>v2.0 性能优化：艾奥尼亚邻近检测加缓存（行为基本不变）</h3>
 * <p>
 * <b>问题：</b>本类有三处需要判断「附近 32 格内是否有人主手带艾奥尼亚附魔」，
 * 优化前每次都现场执行一遍完整扫描：
 * <ol>
 *     <li>{@link #applyEffectTick}——<b>每个中毒实体每秒一次</b>（这是绝对大头）；</li>
 *     <li>{@link #onLivingDamage}——中毒者每次攻击有 25% 概率触发一次；</li>
 *     <li>{@link #onLivingDeath}——中毒者死亡时一次。</li>
 * </ol>
 * 单次扫描的成本是：{@code EntityUtil.getNearbyEntities(LivingEntity.class, entity, 32)}
 * 要遍历 64×64×64 范围覆盖到的全部实体分区并做包围盒测试，随后<b>对每个结果实体</b>调用
 * {@code EnchantmentHelper.getItemEnchantmentLevel}——而该方法内部会逐条遍历主手物品的
 * 附魔 NBT 并为每条 {@code ResourceLocation.tryParse} 一次。
 * </p>
 * <p>
 * 于是在「刷怪塔 / 群体中毒」这类场景下开销是相乘的：N 个中毒实体 × 每秒 1 次 ×
 * （范围查询 + M 个邻近实体的 NBT 解析）。20 个中毒怪、周围 30 个实体时，
 * 每秒就是 20 次范围查询 + 600 次附魔 NBT 解析。
 * </p>
 * <p>
 * <b>本次优化的两点：</b>
 * <ol>
 *     <li><b>结果缓存</b>（{@link #hasAeoniaHolderNearby}）——按实体 UUID 缓存判定结果，
 *         有效期 {@value #AEONIA_CACHE_TTL_TICKS} tick。由于 {@link #applyEffectTick}
 *         每 20 tick 才跑一次，缓存把扫描频率降为原来的 <b>1/3</b>；
 *         三处调用点共用同一份缓存，实战中命中率更高。</li>
 *     <li><b>廉价前置过滤</b>（{@link #scanForAeoniaHolder}）——用
 *         {@link ItemStack#isEnchanted()} 先筛掉裸装。该方法只检查 NBT 标签是否存在、
 *         不做任何反序列化，开销极低；而绝大多数怪物主手是空手或未附魔武器，
 *         因此这一行就能砍掉扫描内绝大部分的 NBT 解析。</li>
 * </ol>
 * </p>
 * <p>
 * <b>行为差异（可接受的取舍）：</b>缓存意味着最多 {@value #AEONIA_CACHE_TTL_TICKS} tick
 * （3 秒）的滞后——艾奥尼亚持有者刚走进 / 走出 32 格范围时，伤害加成与传播判定会晚至多 3 秒生效。
 * 考虑到判定半径是 32 格、而玩家疾跑 3 秒约移动 13 格，这个滞后在实战中难以察觉；
 * 若你希望更灵敏，调小 {@link #AEONIA_CACHE_TTL_TICKS} 即可（代价是扫描更频繁）。
 * </p>
 * <p>
 * <b>缓存的清理：</b>不额外注册 tick 事件，而是在写入时若条目数超过
 * {@value #AEONIA_CACHE_SWEEP_THRESHOLD} 就顺手清一遍过期项（{@link #sweepExpired}）。
 * 这样清理成本摊薄在写入路径上、且自我限制，不会无限增长，也不需要额外的生命周期钩子。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@Mod.EventBusSubscriber
public class MobEffectScarletRot extends IconBase {

    /**
     * 艾奥尼亚邻近检测的缓存有效期（tick）。
     * <p>60 tick = 3 秒。{@link #applyEffectTick} 每 20 tick 触发一次，
     * 故该值使扫描频率降为原来的 1/3。调小则更灵敏、扫描更频繁；调大则更省、滞后更明显。</p>
     */
    private static final long AEONIA_CACHE_TTL_TICKS = 60L;

    /**
     * 缓存条目数超过此值时，在下次写入时顺手清理过期项。
     * <p>取 128 是因为同时处于猩红腐败状态的实体极少超过这个量级；
     * 真超过了也只是多做一次遍历清理，成本可忽略。</p>
     */
    private static final int AEONIA_CACHE_SWEEP_THRESHOLD = 128;

    /**
     * 艾奥尼亚邻近检测结果缓存：实体 UUID -> 缓存条目。
     * <p>仅服务端主线程访问（效果 tick 与事件监听都在主线程），
     * 此处仍用 {@link ConcurrentHashMap} 是出于对 Mohist 等混合端可能的跨线程调用的保守考虑。</p>
     */
    private static final Map<UUID, AeoniaCacheEntry> AEONIA_CACHE = new ConcurrentHashMap<>();

    /**
     * 一条缓存记录。
     *
     * @param result     该实体附近是否存在艾奥尼亚持有者
     * @param expiryTick 过期的游戏刻（超过此值即视为失效，需要重新扫描）
     */
    private record AeoniaCacheEntry(boolean result, long expiryTick) {
    }

    public MobEffectScarletRot(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ARMOR,
                "4ffde3df-c955-f645-d34b-814fda024326",
                -0.5,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                "0b4792a8-c918-bf55-5c7a-62a83b54e569",
                -0.5,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 获取艾奥尼亚附魔实例
     */
    private static Enchantment getAeoniaEnchantment() {
        return EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
    }

    // ==================== 艾奥尼亚邻近检测（带缓存） ====================

    /**
     * 判断指定实体附近 32 格内是否存在主手带艾奥尼亚附魔的实体（带缓存）。
     * <p>
     * 缓存命中则直接返回上次结果；未命中才执行完整扫描
     * （{@link #scanForAeoniaHolder}）并写入缓存。详见类注释的性能优化小节。
     * </p>
     *
     * @param center 检测中心实体
     * @return 附近存在艾奥尼亚持有者返回 true
     */
    private static boolean hasAeoniaHolderNearby(@Nonnull LivingEntity center) {
        long now = center.level().getGameTime();
        UUID id = center.getUUID();

        AeoniaCacheEntry cached = AEONIA_CACHE.get(id);
        if (cached != null && now < cached.expiryTick()) {
            return cached.result();
        }

        boolean result = scanForAeoniaHolder(center);

        // 写入前顺手清理过期项，避免长期运行后条目无限累积
        if (AEONIA_CACHE.size() > AEONIA_CACHE_SWEEP_THRESHOLD) {
            sweepExpired(now);
        }
        AEONIA_CACHE.put(id, new AeoniaCacheEntry(result, now + AEONIA_CACHE_TTL_TICKS));
        return result;
    }

    /**
     * 执行一次完整的艾奥尼亚持有者扫描（无缓存，供 {@link #hasAeoniaHolderNearby} 内部调用）。
     * <p>
     * <b>性能要点：</b>用 {@link ItemStack#isEnchanted()} 做廉价前置过滤——该方法只检查
     * {@code Enchantments} 标签是否存在，不做任何 NBT 反序列化。绝大多数怪物空手或持未附魔武器，
     * 因此这一行能砍掉循环内绝大部分的 {@code getItemEnchantmentLevel} 调用
     * （后者会逐条遍历附魔 NBT 并做 {@code ResourceLocation} 解析）。
     * </p>
     * <p>命中即刻返回，不遍历完整列表。</p>
     *
     * @param center 检测中心实体
     * @return 附近存在艾奥尼亚持有者返回 true
     */
    private static boolean scanForAeoniaHolder(@Nonnull LivingEntity center) {
        Enchantment aeonia = getAeoniaEnchantment();
        if (aeonia == null) {
            return false;
        }

        @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                center,
                32
        );
        for (LivingEntity entityLivingBase : entities) {
            ItemStack mainHand = entityLivingBase.getMainHandItem();
            // 廉价前置过滤：空手 / 未附魔直接跳过，不做 NBT 解析
            if (mainHand.isEmpty() || !mainHand.isEnchanted()) {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(aeonia, mainHand) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理已过期的缓存条目。
     *
     * @param now 当前游戏刻
     */
    private static void sweepExpired(long now) {
        AEONIA_CACHE.entrySet().removeIf(e -> now >= e.getValue().expiryTick());
    }

    // ==================== 事件监听 ====================

    /**
     * 治疗量减少25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().level().isClientSide) {
            LivingEntity healer = evt.getEntity();
            if (healer.hasEffect(CarianStylePotion.SCARLET_ROT.get())) {
                evt.setAmount(evt.getAmount() * 0.75f);
            }
        }
    }

    /**
     * 攻击时25%概率传播猩红腐烂（需要周围有艾奥尼亚附魔持有者）
     * <p>v2.0：邻近检测改走带缓存的 {@link #hasAeoniaHolderNearby}。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide) {
            if (evt.getSource().getDirectEntity() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
                MobEffectInstance potionEffect = attacker.getEffect(CarianStylePotion.SCARLET_ROT.get());
                if (potionEffect != null) {
                    if (RandomUtil.percentageChance(25)) {
                        if (hasAeoniaHolderNearby(attacker)) {
                            LivingEntity hurter = evt.getEntity();
                            hurter.addEffect(new MobEffectInstance(
                                    CarianStylePotion.SCARLET_ROT.get(),
                                    potionEffect.getDuration(),
                                    potionEffect.getAmplifier()
                            ));
                        }
                    }
                }
            }
        }
    }

    /**
     * 死亡时传播猩红腐烂（需要周围有艾奥尼亚附魔持有者）
     * <p>
     * v2.0：前置的「附近是否有艾奥尼亚持有者」判定改走带缓存的
     * {@link #hasAeoniaHolderNearby}；后续 16 格范围的传播目标列表仍需实时查询
     * （它要的是实体本身而非布尔结果，且死亡事件频率远低于每秒 tick，不是热点）。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().level().isClientSide) {
            LivingEntity dead = evt.getEntity();
            MobEffectInstance potionEffect = dead.getEffect(CarianStylePotion.SCARLET_ROT.get());
            if (potionEffect != null) {
                if (hasAeoniaHolderNearby(dead)) {
                    @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                            LivingEntity.class,
                            dead,
                            16
                    );
                    for (LivingEntity target : new ArrayList<>(entities)) {
                        if (RandomUtil.percentageChance(50)) {
                            target.addEffect(new MobEffectInstance(
                                    CarianStylePotion.SCARLET_ROT.get(),
                                    200,
                                    potionEffect.getAmplifier()
                            ));
                        }
                    }
                }
                // 实体已死亡，其缓存条目不再有用，立刻移除（避免等到下次 sweep）
                AEONIA_CACHE.remove(dead.getUUID());
            }
        }
    }

    /**
     * 每秒造成持续伤害
     * 有艾奥尼亚附魔持有者在附近时伤害×2.5
     * <p>
     * v2.0：邻近检测改走带缓存的 {@link #hasAeoniaHolderNearby}。
     * 这是本类最热的调用点——每个中毒实体每秒一次，缓存收益也最大。
     * </p>
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        if (!entityLivingBaseIn.level().isClientSide) {
            if (entityLivingBaseIn.getRemainingFireTicks() <= 0 || RandomUtil.percentageChance(25)) {
                float damage = entityLivingBaseIn.getHealth() * 0.03f + entityLivingBaseIn.getMaxHealth() * 0.00075f;
                damage += damage * amplifier * 0.33;

                if (hasAeoniaHolderNearby(entityLivingBaseIn)) {
                    entityLivingBaseIn.hurt(NewDamageSource.scarletRot(entityLivingBaseIn.level()), damage * 2.5f);
                    return;
                }
                entityLivingBaseIn.hurt(NewDamageSource.scarletRot(entityLivingBaseIn.level()), damage);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/scarlet_rot.png");
    }
}
