package edu.uob;

import java.util.HashMap;
import java.util.ArrayList;

public class Location extends GameEntity {
    private HashMap<String, Location> paths;
    private ArrayList<GameEntity> entities;

    public Location(String name, String description) {
        super(name, description);
        this.paths = new HashMap<>();
        this.entities = new ArrayList<>();
    }

    // About paths
    public void addPath(String destinationName, Location destination) {
        paths.put(destinationName, destination);
    }

    public HashMap<String, Location> getPaths() {
        return paths;
    }

    // About entities
    public void addEntity(GameEntity entity) {
        entities.add(entity);
    }

    public void removeEntity(GameEntity entity) {
        entities.remove(entity);
    }

    public ArrayList<GameEntity> getEntities() {
        return entities;
    }
}
