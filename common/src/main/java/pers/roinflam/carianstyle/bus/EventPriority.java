package pers.roinflam.carianstyle.bus;

/**
 * 监听优先级，五档，和 NeoForge / Forge 的同名枚举一一对应（按序号对应，转发层靠 {@code ordinal()} 换算）。
 * 先 HIGHEST 后 LOWEST；同一档内按 {@link SubscribeEvent#order()} 从小到大，再按登记先后。
 */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}
