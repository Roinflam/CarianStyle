/**
 * 平台事件类的字段型实现：成员都是普通字段，取消状态用总线基类的默认实现。
 * Fabric 侧的 mixin / 回调发事件、测试台造事件都直接 new 这里的类，再交给 {@code CarianBus.post}。
 */
package pers.roinflam.carianstyle.event.impl;
