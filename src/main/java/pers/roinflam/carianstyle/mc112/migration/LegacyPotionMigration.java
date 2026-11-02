package pers.roinflam.carianstyle.mc112.migration;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 旧存档迁移：以前的隐藏药水和冻伤的旧移速修饰符
 *
 * <h3>为什么要迁移</h3>
 * <ul>
 *     <li>已发布的 2.0.0 和之前的 1.12.2 版本把十个隐藏效果（命定之死、隐身、加速、攻击提升、夏玻利利的嘶吼、
 *         龙徽大盾、三种灼烧、岩石剑）注册成了真药水。现在它们改成了内存里的动态属性，不再注册，
 *         旧存档的玩家和生物身上可能还带着这些药水，以及它们加上的属性修饰符（这些修饰符是存档的）。</li>
 *     <li>读档时原版先读属性修饰符、再读药水，查不到的药水直接跳过——药水没了，<b>修饰符却作为孤儿留下来</b>，
 *         比如命定之死的最大生命降低永远不会消失。</li>
 *     <li>冻伤还是真药水，但 2.0.0 的移速修饰符 UUID 和睡眠共用，现在换了新 UUID。旧档里冻伤生效中的实体
 *         带着旧修饰符进来，冻伤到期时按新 UUID 删不到，减速会永久留下。</li>
 * </ul>
 *
 * <h3>做了什么</h3>
 * <ol>
 *     <li><b>缺失注册项：</b>存档的注册表快照里有这十个药水、现在没注册，Forge 会发 {@code MissingMappings}。
 *         不处理的话单人会弹「检测到缺少注册项」，专用服要在控制台 {@code /fml confirm}。这里把这十个标成忽略
 *         （{@code ignore()}：不提示，编号也不会被别的药水复用）。别的缺失项不管，交给 Forge 默认处理。</li>
 *     <li><b>孤儿修饰符：</b>服务端每个生物进世界（{@code EntityJoinWorldEvent}，区块加载出来的生物也会触发）时，
 *         按 {@link #LEGACY_MODIFIERS} 在对应属性上删掉旧 UUID 的修饰符。冻伤的旧 UUID 和睡眠一样，
 *         身上<b>没有</b>睡眠时才删；有睡眠就是睡眠自己的修饰符，到期会被正常删掉（旧冻伤剩下那几秒没有减速，无所谓）。</li>
 * </ol>
 * <p>
 * 旧药水剩下的时长不换算成动态属性：这些效果都只有几秒到几十秒，升级时丢掉没有感觉。
 * 每次进世界多查 15 个 UUID，开销可以忽略；这段长期保留，多久以前的存档拿来都能清干净。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class LegacyPotionMigration {

    /** 不再注册的十个隐藏药水的注册名（命名空间 carianstyle） */
    private static final Set<String> LEGACY_POTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "doomed_death",
            "stealth",
            "speed_boost",
            "attack_boost",
            "howl_shabriri",
            "dragoncrest_greatshield",
            "doomed_death_burning",
            "destruction_fire_burning",
            "epilepsy_fire_burning",
            "cragblade"
    )));

    /**
     * 旧隐藏药水加在属性上的修饰符：属性 + UUID
     */
    private static final LegacyModifier[] LEGACY_MODIFIERS = {
            // 攻击提升
            new LegacyModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "74817132-1c8f-0594-1350-1a7734e34205"),
            new LegacyModifier(SharedMonsterAttributes.ATTACK_SPEED, "bb938acd-fd3f-a0e5-d625-0352b8f23fd9"),
            // 岩石剑
            new LegacyModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "1288f431-abde-d1dc-0daa-6f3e8a2b1c0d"),
            new LegacyModifier(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0d"),
            new LegacyModifier(SharedMonsterAttributes.ARMOR, "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0a"),
            new LegacyModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "8c4a9e3d-7b2f-4e1a-9d5c-6f3e8a2b1c0c"),
            // 命定之死
            new LegacyModifier(SharedMonsterAttributes.MAX_HEALTH, "58993fe2-d11c-2b97-4958-6a8304ff8ad8"),
            // 龙徽大盾
            new LegacyModifier(SharedMonsterAttributes.ARMOR, "024ae079-00fa-5f4c-88ca-c88f217a719e"),
            new LegacyModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "39a196e1-530e-c8ab-93fd-5f3f67d25f85"),
            new LegacyModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "e864bfe0-57bd-da1c-1c85-881295377cf6"),
            new LegacyModifier(SharedMonsterAttributes.FLYING_SPEED, "9bed7f27-3367-08be-fe90-5b8605b2df0a"),
            // 夏玻利利的嘶吼
            new LegacyModifier(SharedMonsterAttributes.ARMOR, "55fb160e-958d-4962-9dcf-086634ca0699"),
            new LegacyModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "915e48f6-3049-a15e-e892-035e2d5a7ca1"),
            // 加速
            new LegacyModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "541f4474-0598-ad68-f26c-66ac0152f427"),
    };

    /**
     * 2.0.0 冻伤的移速修饰符 UUID（和睡眠共用）。只在身上没有睡眠时删
     */
    private static final UUID LEGACY_FROSTBITE_SPEED = UUID.fromString("5d59080b-eda9-f5b7-1b3c-51568e5b6682");

    private LegacyPotionMigration() {
    }

    /**
     * 十个旧隐藏药水的缺失注册项标成忽略
     * <p>
     * 用原始类型订阅，所有注册表的缺失事件都会进来，再按 {@code getGenericType()} 只处理药水表。
     * 匹配一律用 {@code equals} 比较命名空间和路径（这个映射版本的 {@code ResourceLocation} 只有
     * {@code getResourceDomain()} / {@code getResourcePath()}）。
     * </p>
     *
     * @param event 缺失注册项事件
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @SubscribeEvent
    public static void onMissingMappings(@Nonnull RegistryEvent.MissingMappings event) {
        if (event.getGenericType() != Potion.class) {
            return;
        }
        int ignored = 0;
        for (Object o : event.getAllMappings()) {
            RegistryEvent.MissingMappings.Mapping<Potion> mapping = (RegistryEvent.MissingMappings.Mapping<Potion>) o;
            ResourceLocation key = mapping.key;
            if (Reference.MOD_ID.equals(key.getResourceDomain()) && LEGACY_POTIONS.contains(key.getResourcePath())) {
                mapping.ignore();
                ignored++;
            }
        }
        if (ignored > 0) {
            LogUtil.info("旧存档里的 " + ignored + " 个隐藏药水已不再注册，忽略缺失项（属性修饰符在实体进世界时清掉）");
        }
    }

    /**
     * 实体进世界时清掉旧修饰符
     * <p>放在最高优先级：别的模组在进世界事件里读属性时已经是干净的。</p>
     *
     * @param event 进世界事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityLivingBase)) {
            return;
        }
        cleanLegacyModifiers((EntityLivingBase) event.getEntity());
    }

    /**
     * 删掉一个生物身上的旧修饰符
     *
     * @param entity 生物
     */
    public static void cleanLegacyModifiers(@Nonnull EntityLivingBase entity) {
        for (LegacyModifier legacy : LEGACY_MODIFIERS) {
            IAttributeInstance instance = entity.getEntityAttribute(legacy.attribute);
            if (instance != null && instance.getModifier(legacy.uuid) != null) {
                instance.removeModifier(legacy.uuid);
            }
        }

        IAttributeInstance speed = entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(LEGACY_FROSTBITE_SPEED) != null
                && !entity.isPotionActive(CarianStylePotion.SLEEP.get())) {
            speed.removeModifier(LEGACY_FROSTBITE_SPEED);
        }
    }

    /**
     * 一条旧修饰符：属性 + UUID
     */
    private static final class LegacyModifier {
        private final IAttribute attribute;
        private final UUID uuid;

        private LegacyModifier(@Nonnull IAttribute attribute, @Nonnull String uuid) {
            this.attribute = attribute;
            this.uuid = UUID.fromString(uuid);
        }
    }
}
