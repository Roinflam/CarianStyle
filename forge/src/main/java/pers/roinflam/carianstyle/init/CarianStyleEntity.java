package pers.roinflam.carianstyle.init;

import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import java.util.ArrayList;
import java.util.List;

public class CarianStyleEntity {
    public static final List<EntityEntry> ENTITY_ENTRIES = new ArrayList<EntityEntry>();

    public static final EntityEntry GLINTBLADES = EntityEntryBuilder
            .create()
            .entity(EntityGlintblades.class)
            .id(EntityGlintblades.ID, 0)
            .name(EntityGlintblades.NAME)
            .tracker(64, 10, true)
            .build();

    static {
        ENTITY_ENTRIES.add(GLINTBLADES);
    }

}