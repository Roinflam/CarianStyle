package pers.roinflam.carianstyle.enchantment.registry;

import com.google.common.reflect.ClassPath;
import net.minecraft.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * 附魔注册管理器
 * 自动扫描指定包下所有带有AutoRegisterEnchantment注解的类并完成注册
 */
public class EnchantmentRegistry {

    private static final Map<String, Enchantment> REGISTERED_ENCHANTMENTS = new HashMap<>();
    private static final Map<Class<?>, Enchantment> CLASS_TO_INSTANCE = new HashMap<>();
    private static final List<EnchantmentRegistration> ALL_REGISTRATIONS = new ArrayList<>();

    /**
     * 自动扫描并注册指定包下的所有附魔
     */
    public static void scanAndRegister(@Nonnull String packageName) {
        LogUtil.info("卡利亚式附魔 - 开始扫描包：%s", packageName);
        long startTime = System.currentTimeMillis();

        try {
            ClassPath classPath = ClassPath.from(EnchantmentRegistry.class.getClassLoader());
            Set<ClassPath.ClassInfo> classInfos = classPath.getTopLevelClassesRecursive(packageName);

            int scannedCount = 0;
            int registeredCount = 0;

            for (ClassPath.ClassInfo classInfo : classInfos) {
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    scannedCount++;

                    if (!EnchantmentBase.class.isAssignableFrom(clazz)) {
                        continue;
                    }

                    if (clazz.isInterface() || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                        continue;
                    }

                    AutoRegisterEnchantment annotation = clazz.getAnnotation(AutoRegisterEnchantment.class);
                    if (annotation == null) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Class<? extends EnchantmentBase> enchantmentClass = (Class<? extends EnchantmentBase>) clazz;

                    if (registerSingle(enchantmentClass)) {
                        registeredCount++;
                    }

                } catch (ClassNotFoundException e) {
                    LogUtil.debug("卡利亚式附魔 - 跳过类：%s", classInfo.getName());
                } catch (Exception e) {
                    LogUtil.error("卡利亚式附魔 - 处理类时出错：%s", e, classInfo.getName());
                }
            }

            long endTime = System.currentTimeMillis();
            LogUtil.info("卡利亚式附魔 - 扫描完成，扫描了 %d 个类，注册了 %d 个附魔，耗时 %d 毫秒",
                    scannedCount, registeredCount, endTime - startTime);

        } catch (IOException e) {
            LogUtil.error("卡利亚式附魔 - 扫描类路径时发生错误", e);
            throw new RuntimeException("附魔自动注册失败", e);
        }
    }

    /**
     * 注册单个附魔类
     */
    private static boolean registerSingle(@Nonnull Class<? extends EnchantmentBase> enchantmentClass) {
        try {
            AutoRegisterEnchantment annotation = enchantmentClass.getAnnotation(AutoRegisterEnchantment.class);
            if (annotation == null) {
                return false;
            }

            LogUtil.debug("卡利亚式附魔 - 正在注册：%s", annotation.id());

            Enchantment enchantment = enchantmentClass.newInstance();

            REGISTERED_ENCHANTMENTS.put(annotation.id(), enchantment);
            CLASS_TO_INSTANCE.put(enchantmentClass, enchantment);

            EnchantmentRegistration registration = new EnchantmentRegistration(enchantmentClass, enchantment, annotation);
            ALL_REGISTRATIONS.add(registration);

            addToCategory(registration);

            LogUtil.debug("卡利亚式附魔 - %s 注册成功", annotation.id());

            return true;

        } catch (Exception e) {
            LogUtil.error("卡利亚式附魔 - 注册失败：%s", e, enchantmentClass.getSimpleName());
            return false;
        }
    }

    private static void addToCategory(@Nonnull EnchantmentRegistration registration) {
        EnchantmentCategory category = registration.annotation.category();
        Enchantment enchantment = registration.enchantment;

        switch (category) {
            case COMBAT_SKILL:
                CarianStyleEnchantments.COMBAT_SKILL.add(enchantment);
                break;
            case RECOLLECT:
                CarianStyleEnchantments.RECOLLECT.add(enchantment);
                break;
            case LAW:
                CarianStyleEnchantments.LAW.add(enchantment);
                break;
            case DEAD:
                CarianStyleEnchantments.DEAD.add(enchantment);
                break;
            case GENERAL:
                break;
        }
    }

    /**
     * 通过附魔ID获取附魔实例
     */
    @Nullable
    public static Enchantment getEnchantment(@Nonnull String id) {
        return REGISTERED_ENCHANTMENTS.get(id);
    }

    /**
     * 通过附魔类获取附魔实例
     */
    @Nullable
    public static Enchantment getEnchantmentByClass(@Nonnull Class<? extends EnchantmentBase> clazz) {
        return CLASS_TO_INSTANCE.get(clazz);
    }

    /**
     * 获取所有已注册的附魔ID
     */
    @Nonnull
    public static Set<String> getAllEnchantmentIds() {
        return Collections.unmodifiableSet(REGISTERED_ENCHANTMENTS.keySet());
    }

    private static class EnchantmentRegistration {
        final Class<?> enchantmentClass;
        final Enchantment enchantment;
        final AutoRegisterEnchantment annotation;

        EnchantmentRegistration(Class<?> enchantmentClass, Enchantment enchantment, AutoRegisterEnchantment annotation) {
            this.enchantmentClass = enchantmentClass;
            this.enchantment = enchantment;
            this.annotation = annotation;
        }
    }
}