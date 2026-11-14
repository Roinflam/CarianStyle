/**
 * 本模组总线上的平台事件类：每种事件一个抽象类，只声明现有监听实际用到的成员；类名、方法名照 NeoForge 1.21.1 的叫法，
 * 监听迁过来只换 import。
 * <p>
 * 三种实现：NeoForge / Forge 的直通包装（在平台模块里，读写、取消都落在平台事件上）；
 * {@code event.impl} 下的字段型实现（Fabric 侧的 mixin / 回调和测试台直接 new）；客户端事件在 {@code event.client}。
 * </p>
 * <p>
 * 本模组只对死亡、治疗、换目标、破坏方块这几种逻辑事件调 {@code setCanceled}，所以只有它们声明
 * {@link pers.roinflam.carianstyle.bus.ICancellableEvent}。平台事件本身可取消、这里没声明的（如实体加入世界），
 * 直通包装仍然实现取消接口，别的模组取消之后本模组后面的监听照样被跳过，和直接挂在平台总线上一样。
 * </p>
 */
package pers.roinflam.carianstyle.event;
