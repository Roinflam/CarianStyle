package pers.roinflam.carianstyle.init;

import com.google.common.collect.ImmutableList;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemSword;
import net.minecraftforge.common.util.EnumHelper;
import pers.roinflam.carianstyle.enchantment.*;
import pers.roinflam.carianstyle.enchantment.combatskill.*;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentEpilepsySpread;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentGreatbladePhalanx;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentScarletLonia;
import pers.roinflam.carianstyle.enchantment.law.EnchantmentStarsLaw;
import pers.roinflam.carianstyle.enchantment.recollect.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CarianStyleEnchantments {
    @Nullable
    public static final EnumEnchantmentType SHIELD = EnumHelper.addEnchantmentType("cs_shield", item -> item instanceof ItemShield);
    @Nullable
    public static final EnumEnchantmentType ARMS = EnumHelper.addEnchantmentType("cs_arms", item -> item instanceof ItemSword || item instanceof ItemBow);
    @Nullable
    public static final EnumEnchantmentType PICKAEX = EnumHelper.addEnchantmentType("cs_pickaxe", item -> item instanceof ItemPickaxe);
    public static final int RECOLLECT_ENCHANTABILITY = 38;
    public static final List<Enchantment> ENCHANTMENTS = new ArrayList<Enchantment>();

    public static final Set<Enchantment> RECOLLECT = new HashSet<>();

    public static final Set<Enchantment> COMBAT_SKILL = new HashSet<>();
    public static final Set<Enchantment> LAW = new HashSet<>();
    public static final Set<Enchantment> DEAD = new HashSet<>();
}