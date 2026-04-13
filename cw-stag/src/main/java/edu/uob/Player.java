package edu.uob;

import java.util.ArrayList;

public class Player extends Character {
    // 玩家的背包，只存放可收集的物品
    private ArrayList<Artefact> inventory;
    // 玩家当前所在的地点
    private Location currentLocation;

    public Player(String name, String description) {
        // 玩家的 description 可以留空或者设为默认值
        super(name, description);
        this.inventory = new ArrayList<>();
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public void addArtefactToInventory(Artefact artefact) {
        inventory.add(artefact);
    }

    public ArrayList<Artefact> getInventory() {
        return inventory;
    }
}