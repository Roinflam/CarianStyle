package pers.roinflam.carianstyle.annotation.registry;

import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * PORT-STUB(B1a)
 *
 * 附魔注册管理器
 * 使用Forge的ModFileScanData进行注解扫描
 *
 * @author RoinFlam
 * @version 2.0
 */
@Deprecated
public class EnchantmentRegistry {
    private static final Map<String, Enchantment> REGISTERED_ENCHANTMENTS = new HashMap<>();
    private static final Map<Class<?>, Enchantment> CLASS_TO_INSTANCE = new HashMap<>();
    private static final List<EnchantmentRegistration> ALL_REGISTRATIONS = new ArrayList<>();
    public static void scanAndRegister(@NotNull String packageName) {
    }
    @Nullable public static Enchantment getEnchantment(@NotNull String id) {
        return null;
    }
    @Nullable public static Enchantment getEnchantmentByClass(@NotNull Class<? extends EnchantmentBase> clazz) {
        return null;
    }
    @NotNull public static Set<String> getAllEnchantmentIds() {
        return java.util.Set.of();
    }

    private static class EnchantmentRegistration {
        Class<?> enchantmentClass;
        Enchantment enchantment;
        AutoRegisterEnchantment annotation;
        EnchantmentRegistration(Class<?> enchantmentClass, Enchantment enchantment, AutoRegisterEnchantment annotation) {
        }
    }
}

