package pers.roinflam.carianstyle.dynamicattr;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 1.20.1 存档里本模组属性修饰符的迁移
 *
 * <h3>问题</h3>
 * <p>
 * 1.20.1 的属性修饰符按 UUID 区分，1.21.1 改成按 ResourceLocation。升级存档时原版修复器只认得原版那张
 * UUID 表，认不出的一律改成 {@code minecraft:<小写 uuid>}、仍是永久修饰符。本模组的 UUID 全不在表里，
 * 于是升级后身上挂着一堆 {@code minecraft:<uuid>}，而 1.21.1 的代码只认新 id，<b>再也没人删它们</b>：
 * </p>
 * <ul>
 *     <li>带着噩兆 / 切腹 / 重力下线的玩家，药水到期后攻击力、移速永久停在减益后的数值；</li>
 *     <li>1.20.1 动态属性的永久残留（碎步、群星律法的加速等，见 {@code DynamicAttributeManager.applyModifiers}）
 *         永久留着；</li>
 *     <li>祈祷一击的最大生命加成本该保留到重生，变成了一个重生也清不掉的永久加成。</li>
 * </ul>
 *
 * <h3>做法</h3>
 * <p>
 * 实体进世界时（服务端 {@link EntityJoinLevelEvent}，只处理 {@link LivingEntity}）按下表逐个查 {@code minecraft:<uuid>}：
 * 玩家登录、读区块、换维度、重生都会进世界，盔甲架这类不 tick 的实体也会进世界，全都覆盖得到。
 * </p>
 * <ul>
 *     <li><b>药水的</b>：删掉旧的；若这个药水还在身上，按 1.21.1 的新 id、同等级补上（只补药水自带的永久修饰符，
 *         切腹的瞬时加速和 1.20.1 一样不进存档、读档后也没有，不补）；</li>
 *     <li><b>动态属性的</b>：按 1.20.1 的公式重算 UUID、删掉。动态属性 1.21.1 改成不进存档的临时修饰符，状态读档后本来就没有；</li>
 *     <li><b>祈祷一击</b>：读出旧数值，删掉后用新 id、同数值加回（它本来就该跨下线保留到重生）；</li>
 *     <li><b>神圣之刃、拔刀</b>：删掉（1.20.1 里进世界时本来就会清掉）。</li>
 * </ul>
 * <p>
 * 旧 UUID 全部抄在本类自己的表里，不引用各附魔类：那些常量在 1.21.1 已经换成新 id，表要按 1.20.1 2.3.1 定死。
 * 每个实体每次进世界查三十来个 id，先用 {@link AttributeMap#hasModifier} 查（哈希表、不新建属性实例），
 * 开销可以忽略；不打「已迁移」标记，省得多一份存档数据。
 * </p>
 *
 * <h3>行为影响</h3>
 * <p>
 * 只对 1.20.1 升级上来的存档有作用；新存档里查不到这些 id，什么都不做。
 * 升级后药水修饰符的数值和 1.20.1 一样（按当前等级重算），祈祷一击的加成原样保留，其余残留清掉。
 * </p>
 */
@EventBusSubscriber(modid = Reference.MOD_ID)
public final class LegacyModifierMigration {

    /** 祈祷一击最大生命：1.20.1 的 UUID */
    private static final ResourceLocation PRAYERFUL_STRIKE_LEGACY = legacy("b55a7c8a-df03-bca7-b5ea-ec703b261525");

    /** 祈祷一击最大生命：1.21.1 的 id（和附魔类里的常量一致） */
    private static final ResourceLocation PRAYERFUL_STRIKE_ID =
            ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "prayerful_strike/max_health");

    /**
     * 1.20.1 的动态属性及其修饰的属性（2.3.1 的 {@code DynamicAttributes} 里全部带修饰符的配置，共 15 组）
     * <p>
     * 第二列是 1.20.1 {@code Attribute.getDescriptionId()} 去掉 {@code attribute.name.} 前缀后的部分。
     * 隐身和三种燃烧不带修饰符，不在表里。
     * </p>
     */
    private static final String[][] LEGACY_DYNAMIC = {
            {"carianstyle_attack_boost", "generic.attack_damage"},
            {"carianstyle_attack_boost", "generic.attack_speed"},
            {"carianstyle_speed_boost", "generic.movement_speed"},
            {"carianstyle_quickstep_speed", "generic.movement_speed"},
            {"carianstyle_dragoncrest_greatshield", "generic.armor"},
            {"carianstyle_dragoncrest_greatshield", "generic.armor_toughness"},
            {"carianstyle_dragoncrest_greatshield", "generic.movement_speed"},
            {"carianstyle_dragoncrest_greatshield", "generic.flying_speed"},
            {"carianstyle_cragblade", "generic.attack_damage"},
            {"carianstyle_cragblade", "generic.knockback_resistance"},
            {"carianstyle_cragblade", "generic.armor"},
            {"carianstyle_cragblade", "generic.armor_toughness"},
            {"carianstyle_doomed_death", "generic.max_health"},
            {"carianstyle_howl_shabriri", "generic.armor"},
            {"carianstyle_howl_shabriri", "generic.armor_toughness"},
    };

    /** 要删的旧修饰符（不含祈祷一击，它单独处理） */
    private static final List<Legacy> TABLE;

    static {
        List<Legacy> t = new ArrayList<>();

        // ---- 药水（1.20.1 各药水构造器里 addAttributeModifier 的 UUID）----
        effect(t, CarianStylePotion.BAD_OMEN, Attributes.ATTACK_DAMAGE, "5154bf3b-743a-cee6-cf4f-2a62cf832d25");
        effect(t, CarianStylePotion.BAD_OMEN, Attributes.ATTACK_SPEED, "d545b2e6-98a0-c8c7-a7f3-345df7ec14dc");
        effect(t, CarianStylePotion.BLESSING_OF_THE_ERDTREE, Attributes.MAX_HEALTH, "c407bffa-97df-adf8-51db-5681fdef4b8c");
        effect(t, CarianStylePotion.FROSTBITE, Attributes.MOVEMENT_SPEED, "a3e7f012-8b9c-4d6e-b1f0-3c7829d5a4e1");
        effect(t, CarianStylePotion.GRAVITAS, Attributes.ATTACK_SPEED, "64dd94d7-8d83-122b-82be-0c52223463ca");
        effect(t, CarianStylePotion.GRAVITAS, Attributes.MOVEMENT_SPEED, "53878b9c-1134-c379-59c4-391599537f5e");
        effect(t, CarianStylePotion.GRAVITAS, Attributes.FLYING_SPEED, "710bd865-953f-ed1a-facf-eba7de0ce330");
        effect(t, CarianStylePotion.INCISION, Attributes.ATTACK_DAMAGE, "0788fd21-aade-d9dc-0daa-faee0f26e5ee");
        effect(t, CarianStylePotion.INCISION, Attributes.ATTACK_SPEED, "07a1b38c-e245-d4c0-1e0e-6529582fbb6d");
        effect(t, CarianStylePotion.INCISION, Attributes.ARMOR, "53c9ebac-b292-2d82-993a-cb183e208411");
        effect(t, CarianStylePotion.INCISION, Attributes.ARMOR_TOUGHNESS, "68d0f463-1a46-6e25-2ed1-c0aec31b641e");
        effect(t, CarianStylePotion.SCARLET_ROT, Attributes.ARMOR, "4ffde3df-c955-f645-d34b-814fda024326");
        effect(t, CarianStylePotion.SCARLET_ROT, Attributes.ARMOR_TOUGHNESS, "0b4792a8-c918-bf55-5c7a-62a83b54e569");
        effect(t, CarianStylePotion.SLEEP, Attributes.MOVEMENT_SPEED, "5d59080b-eda9-f5b7-1b3c-51568e5b6682");
        // 切腹的瞬时加速：1.20.1 本来就不进存档，这里只是兜底
        t.add(new Legacy(Attributes.MOVEMENT_SPEED, legacy("0a6b62ca-ead9-3641-c4dd-a4d33daf5cc1"), null));

        // ---- 附魔 ----
        // 神圣之刃（加在亡灵受害者身上的攻击力削弱），1.20.1 进世界时本来就清
        t.add(new Legacy(Attributes.ATTACK_DAMAGE, legacy("0dada439-4e61-fd5e-44d7-c620fd5a11fb"), null));
        // 拔刀（攻速），1.20.1 进世界时本来就清
        t.add(new Legacy(Attributes.ATTACK_SPEED, legacy("a9f7b1c6-4c2d-4f0e-9f2c-3a8b3f7d0a5b"), null));

        // ---- 动态属性：nameUUIDFromBytes("carianstyle:dynamic_attribute:" + 名 + ":" + descriptionId) ----
        for (String[] row : LEGACY_DYNAMIC) {
            Holder<Attribute> attribute = vanillaAttribute(row[1]);
            String fullName = "carianstyle:dynamic_attribute:" + row[0] + ":attribute.name." + row[1];
            UUID uuid = UUID.nameUUIDFromBytes(fullName.getBytes(StandardCharsets.UTF_8));
            t.add(new Legacy(attribute, legacy(uuid.toString()), null));
        }

        TABLE = Collections.unmodifiableList(t);
    }

    private LegacyModifierMigration() {
    }

    /**
     * 实体进世界时迁移
     * <p>
     * 最高优先级：别的进世界监听（包括本模组附魔里清修饰符的那几个）看到的已经是迁移后的属性。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            migrate(living);
        }
    }

    /**
     * 把实体身上 1.20.1 留下的修饰符清掉 / 换成新 id
     *
     * @param entity 实体
     * @return 处理掉的旧修饰符个数
     */
    public static int migrate(@Nonnull LivingEntity entity) {
        AttributeMap map = entity.getAttributes();
        int migrated = 0;
        Set<Holder<MobEffect>> effectsToRestore = null;

        for (Legacy legacy : TABLE) {
            if (!map.hasModifier(legacy.attribute(), legacy.id())) {
                continue;
            }
            AttributeInstance instance = map.getInstance(legacy.attribute());
            if (instance == null || !instance.removeModifier(legacy.id())) {
                continue;
            }
            migrated++;
            if (legacy.effect() != null) {
                if (effectsToRestore == null) {
                    effectsToRestore = new LinkedHashSet<>();
                }
                effectsToRestore.add(legacy.effect());
            }
        }

        migrated += migratePrayerfulStrike(map);

        if (effectsToRestore != null) {
            for (Holder<MobEffect> effect : effectsToRestore) {
                restoreEffectModifiers(entity, map, effect);
            }
        }
        return migrated;
    }

    /**
     * 祈祷一击：同数值换成新 id
     */
    private static int migratePrayerfulStrike(@Nonnull AttributeMap map) {
        if (!map.hasModifier(Attributes.MAX_HEALTH, PRAYERFUL_STRIKE_LEGACY)) {
            return 0;
        }
        AttributeInstance instance = map.getInstance(Attributes.MAX_HEALTH);
        if (instance == null) {
            return 0;
        }
        AttributeModifier old = instance.getModifier(PRAYERFUL_STRIKE_LEGACY);
        if (old == null) {
            return 0;
        }
        instance.removeModifier(PRAYERFUL_STRIKE_LEGACY);
        // 新 id 已经有了（不该发生）就以新的为准，不叠加
        if (!instance.hasModifier(PRAYERFUL_STRIKE_ID)) {
            instance.addPermanentModifier(new AttributeModifier(
                    PRAYERFUL_STRIKE_ID, old.amount(), AttributeModifier.Operation.ADD_VALUE));
        }
        return 1;
    }

    /**
     * 药水还在身上时，按新 id 补上它自带的永久修饰符
     * <p>
     * 用 {@code createModifiers} 而不是 {@code addAttributeModifiers}：后者会走切腹覆写的那一版，
     * 把初始 +120% 的瞬时加速也加上，读档后的切腹加速就和 1.20.1（读档后没有）不一样了。
     * </p>
     */
    private static void restoreEffectModifiers(@Nonnull LivingEntity entity, @Nonnull AttributeMap map,
                                               @Nonnull Holder<MobEffect> effect) {
        MobEffectInstance active = entity.getEffect(effect);
        if (active == null) {
            return;
        }
        effect.value().createModifiers(active.getAmplifier(), (attribute, modifier) -> {
            AttributeInstance instance = map.getInstance(attribute);
            if (instance != null) {
                instance.addOrReplacePermanentModifier(modifier);
            }
        });
    }

    private static void effect(@Nonnull List<Legacy> table, @Nonnull Holder<MobEffect> effect,
                               @Nonnull Holder<Attribute> attribute, @Nonnull String uuid) {
        table.add(new Legacy(attribute, legacy(uuid), effect));
    }

    /** 原版修复器给认不出的 UUID 起的 id：{@code minecraft:<小写 uuid>} */
    @Nonnull
    private static ResourceLocation legacy(@Nonnull String uuid) {
        return ResourceLocation.withDefaultNamespace(uuid.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    private static Holder<Attribute> vanillaAttribute(@Nonnull String path) {
        return switch (path) {
            case "generic.attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "generic.attack_speed" -> Attributes.ATTACK_SPEED;
            case "generic.movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "generic.flying_speed" -> Attributes.FLYING_SPEED;
            case "generic.armor" -> Attributes.ARMOR;
            case "generic.armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "generic.knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "generic.max_health" -> Attributes.MAX_HEALTH;
            default -> throw new IllegalArgumentException("未知属性：" + path);
        };
    }

    /**
     * 一条旧修饰符
     *
     * @param attribute 挂在哪个属性上
     * @param id        升级后的 id（{@code minecraft:<uuid>}）
     * @param effect    来自哪个药水；不是药水的为 null
     */
    private record Legacy(Holder<Attribute> attribute, ResourceLocation id, @Nullable Holder<MobEffect> effect) {
    }
}
