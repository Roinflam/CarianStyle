package pers.roinflam.carianstyle.fabric;

import net.fabricmc.api.ModInitializer;

public final class CarianStyleFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricLogicEvents.init();
    }
}
