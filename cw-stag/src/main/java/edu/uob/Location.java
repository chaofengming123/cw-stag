package edu.uob;

import java.util.HashMap;
import java.util.ArrayList;

public class Location extends GameEntity {
    // 存储从这个地点可以到达的其他地点 (目的地名字 -> 目的地对象)
    private HashMap<String, Location> paths;
    // 存储当前地点里的所有实体 (包含 Artefact, Furniture, Character 等)
    private ArrayList<GameEntity> entities;

    public Location(String name, String description) {
        super(name, description);
        this.paths = new HashMap<>();
        this.entities = new ArrayList<>();
    }

    // --- 路径相关的方法 ---
    public void addPath(String destinationName, Location destination) {
        paths.put(destinationName, destination);
    }

    public HashMap<String, Location> getPaths() {
        return paths;
    }

    // --- 实体相关的方法 ---
    public void addEntity(GameEntity entity) {
        entities.add(entity);
    }

    public ArrayList<GameEntity> getEntities() {
        return entities;
    }
}
