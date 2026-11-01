package pers.roinflam.carianstyle.mc112.registry;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 延迟注册器，对应 1.20.1 的 {@code net.minecraftforge.registries.DeferredRegister}
 * <p>
 * 让 {@code init/*} 的写法和 1.20.1 一样：先 {@code register("名字", 工厂)} 拿到 {@link RegistryObject}，
 * 主类在 preInit 里把注册器挂到 Forge 总线上，等 {@code RegistryEvent.Register} 到了再实例化、起名、注册。
 * </p>
 * <h3>起名</h3>
 * <p>
 * 注册名统一 {@code carianstyle:<名字>}。翻译键照 1.20.1 的 lang 对齐：
 * 附魔 {@code setName("carianstyle.<名字>")}（翻译键成为 {@code enchantment.carianstyle.<名字>}）；
 * 药水 {@code setPotionName("effect.carianstyle.<名字>")}；
 * 方块、物品 {@code setUnlocalizedName("carianstyle.<名字>")}（1.12.2 会自己加 {@code tile.} / {@code item.} 前缀和 {@code .name} 后缀）。
 * 工厂返回的实例已经有注册名时（例如 {@code EntityEntryBuilder} 建出来的实体条目）不再重设。
 * </p>
 * <h3>订阅方法用原始类型</h3>
 * <p>
 * {@link #onRegister} 的参数必须写原始类型 {@code RegistryEvent.Register}。写成 {@code Register<?>} 的话，
 * 1.12.2 的事件总线会把通配符当成泛型过滤类型，永远匹配不上；所以收所有注册事件，在方法里比 {@code getGenericType()}。
 * </p>
 *
 * @param <T> 注册表类型
 */
public final class DeferredRegister<T extends IForgeRegistryEntry<T>> {

    private final Class<T> type;
    private final String modid;
    private final List<Entry<T, ? extends T>> entries = new ArrayList<>();
    private final List<RegistryObject<T>> view = new ArrayList<>();
    private boolean registered;

    private DeferredRegister(@Nonnull Class<T> type, @Nonnull String modid) {
        this.type = type;
        this.modid = modid;
    }

    /**
     * 新建一个注册器
     *
     * @param type  注册表的类型（{@code Block.class}、{@code Enchantment.class}……）
     * @param modid 模组 id
     * @param <T>   注册表类型
     * @return 注册器
     */
    @Nonnull
    public static <T extends IForgeRegistryEntry<T>> DeferredRegister<T> create(@Nonnull Class<T> type, @Nonnull String modid) {
        return new DeferredRegister<>(type, modid);
    }

    /**
     * 登记一个注册项
     *
     * @param name    注册名（不带模组前缀）
     * @param factory 注册事件到了才调用的工厂
     * @param <I>     实际类型
     * @return 注册后才有值的引用
     * @throws IllegalStateException 注册事件已经过去了
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public <I extends T> RegistryObject<I> register(@Nonnull String name, @Nonnull Supplier<? extends I> factory) {
        if (registered) {
            throw new IllegalStateException("注册事件已经过去，不能再登记：" + modid + ":" + name);
        }
        RegistryObject<I> obj = new RegistryObject<>(new ResourceLocation(modid, name));
        entries.add(new Entry<>(name, factory, obj));
        view.add((RegistryObject<T>) (RegistryObject<?>) obj);
        return obj;
    }

    /**
     * 挂到事件总线上（{@code bus.register(this)}）
     *
     * @param bus Forge 总线
     */
    public void register(@Nonnull EventBus bus) {
        bus.register(this);
    }

    /**
     * 全部登记过的注册项（只读）
     */
    @Nonnull
    public Collection<RegistryObject<T>> getEntries() {
        return Collections.unmodifiableList(view);
    }

    /**
     * 注册事件：只处理自己这种类型的注册表
     *
     * @param event 注册事件（原始类型，原因见类注释）
     */
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onRegister(RegistryEvent.Register event) {
        if (event.getGenericType() != type) {
            return;
        }
        IForgeRegistry<T> registry = event.getRegistry();
        for (Entry<T, ? extends T> entry : entries) {
            registerOne(registry, entry);
        }
        registered = true;
    }

    private <I extends T> void registerOne(@Nonnull IForgeRegistry<T> registry, @Nonnull Entry<T, I> entry) {
        I value = entry.factory.get();
        if (value == null) {
            throw new IllegalStateException("注册项工厂返回了 null：" + entry.object.getId());
        }
        if (value.getRegistryName() == null) {
            value.setRegistryName(entry.object.getId());
        }
        String key = modid + "." + entry.name;
        if (value instanceof Enchantment) {
            ((Enchantment) value).setName(key);
        } else if (value instanceof Potion) {
            ((Potion) value).setPotionName("effect." + key);
        } else if (value instanceof Block) {
            ((Block) value).setUnlocalizedName(key);
        } else if (value instanceof Item) {
            ((Item) value).setUnlocalizedName(key);
        }
        registry.register(value);
        entry.object.set(value);
    }

    private static final class Entry<T, I extends T> {
        final String name;
        final Supplier<? extends I> factory;
        final RegistryObject<I> object;

        Entry(String name, Supplier<? extends I> factory, RegistryObject<I> object) {
            this.name = name;
            this.factory = factory;
            this.object = object;
        }
    }
}
