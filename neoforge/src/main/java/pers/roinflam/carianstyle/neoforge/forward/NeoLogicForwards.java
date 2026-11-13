package pers.roinflam.carianstyle.neoforge.forward;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.bus.WeakEventRef;
import pers.roinflam.carianstyle.event.BlockDropsEvent;
import pers.roinflam.carianstyle.event.BlockEvent;
import pers.roinflam.carianstyle.event.CriticalHitEvent;
import pers.roinflam.carianstyle.event.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.EntityLeaveLevelEvent;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;
import pers.roinflam.carianstyle.event.MobEffectEvent;
import pers.roinflam.carianstyle.event.PlayerEvent;
import pers.roinflam.carianstyle.event.PlayerTickEvent;
import pers.roinflam.carianstyle.event.ProjectileImpactEvent;
import pers.roinflam.carianstyle.event.ServerStartedEvent;
import pers.roinflam.carianstyle.event.ServerStoppedEvent;
import pers.roinflam.carianstyle.event.ServerTickEvent;

import javax.annotation.Nullable;

import static pers.roinflam.carianstyle.neoforge.forward.NeoForwarding.forward;

/**
 * 逻辑类（双端都有）平台事件的直通包装和转发登记。
 * <p>
 * 包装类只弱引用平台事件（{@link WeakEventRef}），所有成员当场读写平台事件；平台事件可取消的，包装类都实现取消接口，
 * 取消状态读写平台事件本身。分发结束之后不能再读包装对象。
 * </p>
 */
final class NeoLogicForwards {

    private NeoLogicForwards() {
    }

    static void install() {
        forward(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, LivingDeathEvent.class, Death.class, Death::new);
        forward(net.neoforged.neoforge.event.entity.living.LivingHealEvent.class, LivingHealEvent.class, Heal.class, Heal::new);
        forward(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, EntityJoinLevelEvent.class, JoinLevel.class, JoinLevel::new);
        forward(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent.class, EntityLeaveLevelEvent.class, LeaveLevel.class, LeaveLevel::new);
        forward(net.neoforged.neoforge.event.entity.ProjectileImpactEvent.class, ProjectileImpactEvent.class, ProjectileImpact.class, ProjectileImpact::new);
        forward(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                PlayerEvent.PlayerLoggedInEvent.class, LoggedIn.class, LoggedIn::new);
        forward(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                PlayerEvent.PlayerLoggedOutEvent.class, LoggedOut.class, LoggedOut::new);
        forward(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class,
                PlayerEvent.PlayerRespawnEvent.class, Respawn.class, Respawn::new);
        forward(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class,
                PlayerEvent.PlayerChangedDimensionEvent.class, ChangedDimension.class, ChangedDimension::new);
        forward(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone.class, PlayerEvent.Clone.class, Clone.class, Clone::new);
        forward(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre.class, PlayerTickEvent.Pre.class, PlayerTickPre.class, PlayerTickPre::new);
        forward(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre.class, ServerTickEvent.Pre.class, ServerTickPre.class, ServerTickPre::new);
        forward(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, ServerTickEvent.Post.class, ServerTickPost.class, ServerTickPost::new);
        forward(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added.class, MobEffectEvent.Added.class, EffectAdded.class, EffectAdded::new);
        forward(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove.class, MobEffectEvent.Remove.class, EffectRemove.class, EffectRemove::new);
        forward(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired.class, MobEffectEvent.Expired.class, EffectExpired.class, EffectExpired::new);
        forward(net.neoforged.neoforge.event.entity.player.CriticalHitEvent.class, CriticalHitEvent.class, CriticalHit.class, CriticalHit::new);
        forward(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.class, LivingChangeTargetEvent.class, ChangeTarget.class, ChangeTarget::new);
        forward(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent.class, LivingKnockBackEvent.class, KnockBack.class, KnockBack::new);
        forward(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent.class, LivingExperienceDropEvent.class, ExperienceDrop.class, ExperienceDrop::new);
        // 碎岩连挖的权限探测事件是 BreakEvent 的子类，NeoForge 总线照样分发到这里，本模组的破坏方块监听照旧收到
        forward(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, BlockEvent.BreakEvent.class, Break.class, Break::new);
        forward(net.neoforged.neoforge.event.level.BlockDropsEvent.class, BlockDropsEvent.class, BlockDrops.class, BlockDrops::new);
        forward(net.neoforged.neoforge.event.server.ServerStartedEvent.class, ServerStartedEvent.class, ServerStarted.class, ServerStarted::new);
        forward(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, ServerStoppedEvent.class, ServerStopped.class, ServerStopped::new);
    }

    // ==================== 包装类 ====================

    static final class Death extends LivingDeathEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> e;

        Death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public DamageSource getSource() {
            return e.get().getSource();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Heal extends LivingHealEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.LivingHealEvent> e;

        Heal(net.neoforged.neoforge.event.entity.living.LivingHealEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public float getAmount() {
            return e.get().getAmount();
        }

        @Override
        public void setAmount(float amount) {
            e.get().setAmount(amount);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class JoinLevel extends EntityJoinLevelEvent implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> e;

        JoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Entity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Level getLevel() {
            return e.get().getLevel();
        }

        @Override
        public boolean loadedFromDisk() {
            return e.get().loadedFromDisk();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class LeaveLevel extends EntityLeaveLevelEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent> e;

        LeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Entity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Level getLevel() {
            return e.get().getLevel();
        }
    }

    static final class ProjectileImpact extends ProjectileImpactEvent implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.ProjectileImpactEvent> e;

        ProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Entity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Projectile getProjectile() {
            return e.get().getProjectile();
        }

        @Override
        public HitResult getRayTraceResult() {
            return e.get().getRayTraceResult();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class LoggedIn extends PlayerEvent.PlayerLoggedInEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent> e;

        LoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class LoggedOut extends PlayerEvent.PlayerLoggedOutEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent> e;

        LoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class Respawn extends PlayerEvent.PlayerRespawnEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent> e;

        Respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public boolean isEndConquered() {
            return e.get().isEndConquered();
        }
    }

    static final class ChangedDimension extends PlayerEvent.PlayerChangedDimensionEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent> e;

        ChangedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class Clone extends PlayerEvent.Clone {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone> e;

        Clone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Player getOriginal() {
            return e.get().getOriginal();
        }

        @Override
        public boolean isWasDeath() {
            return e.get().isWasDeath();
        }
    }

    static final class PlayerTickPre extends PlayerTickEvent.Pre {
        private final WeakEventRef<net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre> e;

        PlayerTickPre(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }
    }

    static final class ServerTickPre extends ServerTickEvent.Pre {
        private final WeakEventRef<net.neoforged.neoforge.event.tick.ServerTickEvent.Pre> e;

        ServerTickPre(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }

    static final class ServerTickPost extends ServerTickEvent.Post {
        private final WeakEventRef<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> e;

        ServerTickPost(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }

    static final class EffectAdded extends MobEffectEvent.Added {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added> e;

        EffectAdded(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public MobEffectInstance getEffectInstance() {
            return e.get().getEffectInstance();
        }
    }

    static final class EffectRemove extends MobEffectEvent.Remove implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove> e;

        EffectRemove(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Holder<MobEffect> getEffect() {
            return e.get().getEffect();
        }

        @Nullable
        @Override
        public MobEffectInstance getEffectInstance() {
            return e.get().getEffectInstance();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class EffectExpired extends MobEffectEvent.Expired implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired> e;

        EffectExpired(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public MobEffectInstance getEffectInstance() {
            return e.get().getEffectInstance();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class CriticalHit extends CriticalHitEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.player.CriticalHitEvent> e;

        CriticalHit(net.neoforged.neoforge.event.entity.player.CriticalHitEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public Entity getTarget() {
            return e.get().getTarget();
        }

        @Override
        public boolean isVanillaCritical() {
            return e.get().isVanillaCritical();
        }

        @Override
        public float getDamageMultiplier() {
            return e.get().getDamageMultiplier();
        }

        @Override
        public void setDamageMultiplier(float multiplier) {
            e.get().setDamageMultiplier(multiplier);
        }
    }

    static final class ChangeTarget extends LivingChangeTargetEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent> e;

        ChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Nullable
        @Override
        public LivingEntity getNewAboutToBeSetTarget() {
            return e.get().getNewAboutToBeSetTarget();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class KnockBack extends LivingKnockBackEvent implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent> e;

        KnockBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public float getStrength() {
            return e.get().getStrength();
        }

        @Override
        public void setStrength(float strength) {
            e.get().setStrength(strength);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class ExperienceDrop extends LivingExperienceDropEvent implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent> e;

        ExperienceDrop(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Nullable
        @Override
        public Player getAttackingPlayer() {
            return e.get().getAttackingPlayer();
        }

        @Override
        public int getDroppedExperience() {
            return e.get().getDroppedExperience();
        }

        @Override
        public void setDroppedExperience(int experience) {
            e.get().setDroppedExperience(experience);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class Break extends BlockEvent.BreakEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> e;

        Break(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LevelAccessor getLevel() {
            return e.get().getLevel();
        }

        @Override
        public BlockPos getPos() {
            return e.get().getPos();
        }

        @Override
        public BlockState getState() {
            return e.get().getState();
        }

        @Override
        public Player getPlayer() {
            return e.get().getPlayer();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class BlockDrops extends BlockDropsEvent implements ICancellableEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.level.BlockDropsEvent> e;

        BlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public ServerLevel getLevel() {
            return e.get().getLevel();
        }

        @Override
        public BlockPos getPos() {
            return e.get().getPos();
        }

        @Override
        public BlockState getState() {
            return e.get().getState();
        }

        @Override
        public int getDroppedExperience() {
            return e.get().getDroppedExperience();
        }

        @Override
        public void setDroppedExperience(int experience) {
            e.get().setDroppedExperience(experience);
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class ServerStarted extends ServerStartedEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.server.ServerStartedEvent> e;

        ServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }

    static final class ServerStopped extends ServerStoppedEvent {
        private final WeakEventRef<net.neoforged.neoforge.event.server.ServerStoppedEvent> e;

        ServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public MinecraftServer getServer() {
            return e.get().getServer();
        }
    }
}
