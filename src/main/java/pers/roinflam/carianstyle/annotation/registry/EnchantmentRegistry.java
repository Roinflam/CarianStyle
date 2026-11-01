package pers.roinflam.carianstyle.annotation.registry;

import net.minecraftforge.fml.common.discovery.ASMDataTable;

import javax.annotation.Nonnull;

/**
 * PORT-STUB(B1)
 * <p>附魔扫描注册：桩里什么都不做，附魔类由附魔框架批次生成后再接上。</p>
 */
@Deprecated
public final class EnchantmentRegistry {

    private EnchantmentRegistry() {
    }

    /**
     * 扫描带 {@code @AutoRegisterEnchantment} 的附魔类并登记到注册器（桩：什么都不做）
     *
     * @param asm         FML 的注解扫描结果
     * @param packageName 只扫这个包下的类
     */
    public static void scanAndRegister(@Nonnull ASMDataTable asm, @Nonnull String packageName) {
        // 桩
    }
}
