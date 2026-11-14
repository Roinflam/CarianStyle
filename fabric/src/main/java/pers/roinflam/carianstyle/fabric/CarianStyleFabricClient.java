package pers.roinflam.carianstyle.fabric;

import net.fabricmc.api.ClientModInitializer;

public final class CarianStyleFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientEvents.init();
    }
}
