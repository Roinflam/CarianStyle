package pers.roinflam.carianstyle.annotation.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 运行期生成的内置数据包 {@code carianstyle:generated}：113 个附魔的 JSON、获取途径标签、互斥标签、适用物品标签
 *
 * <h3>问题</h3>
 * <p>
 * 1.21.1 的附魔是数据包注册表里的条目，花费、权重、铁砧花费、宝藏、交易、禁用、互斥、能上哪些物品全写在 JSON 和标签里。
 * 这些在 1.20.1 都由 {@code carianstyle-common.toml} 决定（附魔难度、稀有度权重、按稀有度设宝藏、村民交易、禁用列表），
 * 放进 jar 的静态 JSON 只能写默认值，服主改了配置等于没改。
 * </p>
 *
 * <h3>做法</h3>
 * <ul>
 *   <li>MOD 总线 {@link AddPackFindersEvent} 里给服务端数据包仓库塞一个自己的 {@link Pack}：必选（始终启用，老世界也会被补进已选列表）、
 *       放在最底下（世界数据包、别的模组能用同名文件覆盖）。</li>
 *   <li>{@code knownPackInfo} 留空。注册表同步时，来自「客户端也认识的已知包」的条目只发引用、让客户端用本地那份；
 *       模组 jar 的数据包和 {@code addPackFinders} 这个帮手都会带上已知包信息，所以不用帮手。
 *       客户端的配置可能和服务器不同，附魔定义必须整条从服务器下发。</li>
 *   <li><b>每个包实例一份快照</b>：每次打开（进世界、{@code /reload}、读包元数据）都新建一个实例，构造时按当时的配置把全部文件生成到内存里，
 *       这个实例的读取都只读这份快照。一次重载里注册表加载器和标签加载器在后台线程并行读包，
 *       快照保证同一次重载里附魔 JSON 和标签出自同一份配置；配置文件在别的线程被改，也不会读到一半新一半旧。</li>
 *   <li><b>不许出错</b>：按当前配置生成时出了任何异常，打一条 ERROR，退回默认配置再生成一遍（这一遍逐条兜底）。
 *       113 个附魔一个都不能少：注册表里少一条，存档里带它的物品读出来就丢附魔。禁用的附魔照样生成，只是不进获取途径标签。</li>
 * </ul>
 *
 * <h3>行为影响</h3>
 * <ul>
 *   <li>改了花费、权重类配置（{@code enchantingDifficulty}、{@code useVanillaRarityWeight}）要重进世界 / 重启服务器才生效：
 *       附魔注册表只在加载世界时读一次。获取途径类（宝藏、交易、禁用）{@code /reload} 就生效。1.20.1 是改完立即生效。</li>
 *   <li>这个包关不掉：{@code /datapack disable "carianstyle:generated"} 会提示成功，但下次重载必选包又被补回来。
 *       要调整某个附魔请改配置，或者用普通数据包放同名 JSON 覆盖。</li>
 * </ul>
 *
 * @author RoinFlam
 */
@EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GeneratedDataPack implements PackResources {

    public static final String PACK_ID = Reference.MOD_ID + ":generated";

    /** 1.21.1 数据包格式（{@code SharedConstants.DATA_PACK_FORMAT}） */
    static final int PACK_FORMAT = 48;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID,
            Component.literal("CarianStyle (generated)"),
            PackSource.BUILT_IN,
            Optional.empty());

    private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);

    private static final byte[] META_BYTES = metadataBytes();

    private static final Pack.ResourcesSupplier SUPPLIER = new Pack.ResourcesSupplier() {
        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return new GeneratedDataPack(location);
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return new GeneratedDataPack(location);
        }
    };

    private final PackLocationInfo location;
    /** 这个实例的快照：数据包内路径（{@code 命名空间:enchantment/x.json} 之类）→ 文件内容 */
    private final Map<ResourceLocation, byte[]> files;

    private GeneratedDataPack(@Nonnull PackLocationInfo location) {
        this.location = location;
        this.files = snapshot();
    }

    // ==================== 注册 ====================

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(LOCATION, SUPPLIER, PackType.SERVER_DATA, SELECTION);
            if (pack != null) {
                consumer.accept(pack);
            } else {
                LogUtil.error("卡利亚式附魔 - 内置数据包 %s 创建失败，本模组的附魔不会出现在注册表里", PACK_ID);
            }
        });
    }

    // ==================== 生成 ====================

    /**
     * 生成时用到的全部配置，开始生成前一次读齐，整份快照都用这一份。
     * <p>
     * 权重、铁砧、宝藏、交易的换算和 {@code EnchantmentBase} 的 {@code getWeight}、{@code getAnvilCost}、
     * {@code isTreasureOnly}、{@code isTradeable} 是同一套规则；这里不直接调那几个方法，是因为它们每次都现读配置：
     * 生成到一半配置被别的线程重载，就会出现前一半旧、后一半新的包；另外出错时要能换成默认配置再生成一遍。
     * </p>
     *
     * @param difficulty       附魔难度 {@code enchantingDifficulty}
     * @param vanillaWeight    {@code useVanillaRarityWeight}
     * @param treasureUncommon {@code isTreasureUncommonEnchantment}
     * @param treasureRare     {@code isTreasureRareEnchantment}
     * @param treasureVeryRare {@code isTreasureVeryRareEnchantment}
     * @param villagerTrade    {@code allowVillagerBookTrade}
     * @param disabled         被 {@code uninstallEnchantment} 禁用的附魔 id（不带命名空间）
     */
    record Settings(double difficulty, boolean vanillaWeight, boolean treasureUncommon, boolean treasureRare,
                    boolean treasureVeryRare, boolean villagerTrade, Set<String> disabled) {

        static Settings current() {
            Set<String> disabled = new HashSet<>();
            for (EnchantmentBase base : EnchantmentRegistry.all()) {
                if (base.isDisabled()) {
                    disabled.add(base.getId());
                }
            }
            return new Settings(ConfigLoader.enchantingDifficulty, ConfigLoader.useVanillaRarityWeight,
                    ConfigLoader.isTreasureUncommonEnchantment, ConfigLoader.isTreasureRaryEnchantment,
                    ConfigLoader.isTreasureVeryRaryEnchantment, ConfigLoader.allowVillagerBookTrade,
                    Collections.unmodifiableSet(disabled));
        }

        /**
         * 配置文件的默认值（读 {@code ModConfigSpec} 的默认值，不读文件），什么都不禁用。
         */
        static Settings defaults() {
            ConfigLoader.Common c = ConfigLoader.COMMON;
            return new Settings(c.enchantingDifficulty.getDefault(), c.useVanillaRarityWeight.getDefault(),
                    c.isTreasureUncommonEnchantment.getDefault(), c.isTreasureRareEnchantment.getDefault(),
                    c.isTreasureVeryRareEnchantment.getDefault(), c.allowVillagerBookTrade.getDefault(),
                    Set.of());
        }

        boolean isDisabled(@Nonnull EnchantmentBase base) {
            return disabled.contains(base.getId());
        }

        /** 同 {@code EnchantmentBase#isTreasureOnly}：注解强制宝藏，或按稀有度的开关 */
        boolean isTreasure(@Nonnull EnchantmentBase base) {
            if (base.getAnnotation() != null && base.getAnnotation().forceTreasure()) {
                return true;
            }
            EnchantmentRarity rarity = base.getEnchantmentRarity();
            return switch (rarity) {
                case VERY_RARE -> treasureVeryRare;
                case RARE -> treasureRare;
                case UNCOMMON -> treasureUncommon;
            };
        }

        /** 同 {@code EnchantmentBase#isTradeable}：被禁用的一律不能交易 */
        boolean isTradeable(@Nonnull EnchantmentBase base) {
            return villagerTrade && !isDisabled(base);
        }
    }

    /**
     * 按当前配置生成全部文件；出错退回默认配置。
     */
    private static Map<ResourceLocation, byte[]> snapshot() {
        try {
            return generate(Settings.current(), true);
        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 按当前配置生成内置数据包失败，这一次改用默认配置生成（附魔照样全部注册，配置里改过的花费、获取途径暂不生效）", e);
            return generate(Settings.defaults(), false);
        }
    }

    /**
     * 生成全部文件。
     *
     * @param settings 配置
     * @param strict   true：任何异常直接抛出（由调用方退回默认配置）；false：逐条兜底，保证 113 个附魔都有 JSON
     */
    static Map<ResourceLocation, byte[]> generate(@Nonnull Settings settings, boolean strict) {
        List<EnchantmentBase> enchantments = EnchantmentRegistry.all().stream()
                .filter(base -> base.getKey() != null)
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
        if (enchantments.isEmpty()) {
            LogUtil.error("卡利亚式附魔 - 生成内置数据包时没有任何附魔行为对象，注解扫描可能失败了");
        }

        Map<ResourceLocation, byte[]> out = new TreeMap<>();

        Map<EnchantmentBase, List<JsonElement>> exclusive = EnchantmentTagWriter.exclusiveSets(enchantments, strict);
        for (Map.Entry<EnchantmentBase, List<JsonElement>> en : exclusive.entrySet()) {
            put(out, EnchantmentTagWriter.exclusiveSetFile(en.getKey()), EnchantmentTagWriter.tag(en.getValue()));
        }

        for (EnchantmentBase base : enchantments) {
            boolean hasExclusive = exclusive.containsKey(base);
            JsonObject json;
            if (strict) {
                json = EnchantmentJsonWriter.write(base, settings, hasExclusive);
            } else {
                try {
                    json = EnchantmentJsonWriter.write(base, settings, hasExclusive);
                } catch (Exception e) {
                    LogUtil.error("卡利亚式附魔 - 附魔 %s 的 JSON 生成失败，改用最简定义（只保证注册表里有这一条）：%s", base.getId(), e);
                    json = EnchantmentJsonWriter.minimal(base);
                }
            }
            put(out, EnchantmentJsonWriter.file(base), json);
        }

        EnchantmentTagWriter.acquisitionTags(enchantments, settings).forEach((path, json) -> put(out, path, json));
        EnchantmentTagWriter.itemTags(strict).forEach((path, json) -> put(out, path, json));

        LogUtil.debug("卡利亚式附魔 - 内置数据包已生成：%d 个附魔，%d 个文件（难度 %s，禁用 %d 个）",
                enchantments.size(), out.size(), settings.difficulty(), settings.disabled().size());
        return out;
    }

    private static void put(Map<ResourceLocation, byte[]> out, ResourceLocation path, JsonElement json) {
        out.put(path, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] metadataBytes() {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "CarianStyle enchantments and tags, generated from carianstyle-common.toml");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    // ==================== PackResources ====================

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && PackResources.PACK_META.equals(elements[0])) {
            return supplier(META_BYTES);
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        byte[] bytes = files.get(location);
        return bytes != null ? supplier(bytes) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> en : files.entrySet()) {
            ResourceLocation rl = en.getKey();
            if (rl.getNamespace().equals(namespace) && rl.getPath().startsWith(prefix)) {
                output.accept(rl, supplier(en.getValue()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.SERVER_DATA) {
            return Set.of();
        }
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation rl : files.keySet()) {
            namespaces.add(rl.getNamespace());
        }
        return namespaces;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        return AbstractPackResources.getMetadataFromStream(serializer, new ByteArrayInputStream(META_BYTES));
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }

    private static IoSupplier<InputStream> supplier(byte[] bytes) {
        return () -> new ByteArrayInputStream(bytes);
    }
}
