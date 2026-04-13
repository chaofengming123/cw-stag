package edu.uob;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Node;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;

    // 保存游戏中所有的地点 (名称 -> 地点对象)
    private HashMap<String, Location> gameMap;
    // 记录玩家第一次进入游戏的初始地点
    private Location startLocation;

    public static void main(String[] args) throws IOException {
        File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
        GameServer server = new GameServer(entitiesFile, actionsFile);
        server.blockingListenOn(8888);
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Instanciates a new server instance, specifying a game with some configuration files
    *
    * @param entitiesFile The game configuration file containing all game entities to use in your game
    * @param actionsFile The game configuration file containing all game actions to use in your game
    */
    public GameServer(File entitiesFile, File actionsFile) {
        // 1. 初始化地图容器
        gameMap = new HashMap<>();

        // 2. 解析实体文件
        parseEntitiesFile(entitiesFile);
        // TODO implement your server logic here
    }

    // 为了方便我们后续做单元测试，临时添加一个 getter 方法
    public HashMap<String, Location> getGameMap() {
        return gameMap;
    }

    // --- 核心解析逻辑：读取 DOT 文件 ---
    private void parseEntitiesFile(File entitiesFile) {
        try {
            Parser parser = new Parser();
            FileReader reader = new FileReader(entitiesFile);
            parser.parse(reader);
            Graph wholeDocument = parser.getGraphs().get(0);
            ArrayList<Graph> sections = wholeDocument.getSubgraphs();

            // DOT 文件包含两个主要子图：locations(0) 和 paths(1)
            Graph locations = sections.get(0);
            Graph paths = sections.get(1);

            // 1. 解析所有的 Locations 及其内部的实体
            boolean isFirstLocation = true;
            for (Graph locationGraph : locations.getSubgraphs()) {
                // 获取地点节点 (比如 cabin, forest)
                Node locationDetails = locationGraph.getNodes(false).get(0);
                String locName = locationDetails.getId().getId();
                String locDesc = locationDetails.getAttribute("description");

                Location newLocation = new Location(locName, locDesc);
                gameMap.put(locName, newLocation);

                // 设置初始出生点（文件中的第一个地点）
                if (isFirstLocation) {
                    startLocation = newLocation;
                    isFirstLocation = false;
                }

                // 获取地点内部的分类（artefacts, furniture, characters）
                for (Graph entityCategory : locationGraph.getSubgraphs()) {
                    String categoryName = entityCategory.getId().getId();

                    // 遍历该分类下的所有具体实体
                    for (Node entityNode : entityCategory.getNodes(false)) {
                        String entityName = entityNode.getId().getId();
                        String entityDesc = entityNode.getAttribute("description");

                        // 根据分类实例化不同类型的对象，并放入当前地点
                        if (categoryName.equals("artefacts")) {
                            newLocation.addEntity(new Artefact(entityName, entityDesc));
                        } else if (categoryName.equals("furniture")) {
                            newLocation.addEntity(new Furniture(entityName, entityDesc));
                        } else if (categoryName.equals("characters")) {
                            newLocation.addEntity(new Character(entityName, entityDesc));
                        }
                    }
                }
            }

            // 2. 检查并确保 "storeroom" (储藏室) 存在
            if (!gameMap.containsKey("storeroom")) {
                gameMap.put("storeroom", new Location("storeroom", "Storage for any entities not placed in the game"));
            }

            // 3. 解析 Paths (连通路径)
            for (Edge pathEdge : paths.getEdges()) {
                String fromName = pathEdge.getSource().getNode().getId().getId();
                String toName = pathEdge.getTarget().getNode().getId().getId();

                Location fromLoc = gameMap.get(fromName);
                Location toLoc = gameMap.get(toName);

                // 将目的地添加到起始地点的路径中
                if (fromLoc != null && toLoc != null) {
                    fromLoc.addPath(toName, toLoc);
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to parse entities file: " + e.getMessage());
        }
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * This method handles all incoming game commands and carries out the corresponding actions.</p>
    *
    * @param command The incoming command to be processed
    */
    public String handleCommand(String command) {
        // TODO implement your server logic here
        return "";
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Starts a *blocking* socket server listening for new connections.
    *
    * @param portNumber The port to listen on.
    * @throws IOException If any IO related operation fails.
    */
    public void blockingListenOn(int portNumber) throws IOException {
        try (ServerSocket s = new ServerSocket(portNumber)) {
            System.out.println("Server listening on port " + portNumber);
            while (!Thread.interrupted()) {
                try {
                    blockingHandleConnection(s);
                } catch (IOException e) {
                    System.out.println("Connection closed");
                }
            }
        }
    }

    /**
    * Do not change the following method signature or we won't be able to mark your submission
    * Handles an incoming connection from the socket server.
    *
    * @param serverSocket The client socket to read/write from.
    * @throws IOException If any IO related operation fails.
    */
    private void blockingHandleConnection(ServerSocket serverSocket) throws IOException {
        try (Socket s = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
            System.out.println("Connection established");
            String incomingCommand = reader.readLine();
            if(incomingCommand != null) {
                System.out.println("Received message from " + incomingCommand);
                String result = handleCommand(incomingCommand);
                writer.write(result);
                writer.write("\n" + END_OF_TRANSMISSION + "\n");
                writer.flush();
            }
        }
    }
}
