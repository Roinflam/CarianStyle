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
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
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
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(B2a)
 *
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
 * <b>优化有两点：</b>
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
@Deprecated
public class MobEffectScarletRot extends IconBase {
    private static final long AEONIA_CACHE_TTL_TICKS = 60L;
    private static final int AEONIA_CACHE_SWEEP_THRESHOLD = 128;
    private static final Map<UUID, AeoniaCacheEntry> AEONIA_CACHE = new ConcurrentHashMap<>();

    private record AeoniaCacheEntry(boolean result, long expiryTick) {
    }
    public MobEffectScarletRot(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
    @Nonnull @OnlyIn(Dist.CLIENT) protected ResourceLocation getIconTexture() {
        return null;
    }
}

