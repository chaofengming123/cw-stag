package edu.uob;

import java.util.ArrayList;

public class Player extends Character {
    // Inventory
    private ArrayList<Artefact> inventory;
    // Current Location
    private Location currentLocation;
    // Health
    private int health = 3;

    public Player(String name, String description) {
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

    public int getHealth() {
        return health;
    }

    public void decreaseHealth() {
        if (health > 0) {
            health--;
        }
    }

    public void increaseHealth() {
        if (health < 3) {
            health++;
        }
    }

    public void resetHealth() {
        health = 3;
    }
}