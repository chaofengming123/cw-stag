package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.Duration;

class ExampleSTAGTests {

  private GameServer server;

  // Create a new server _before_ every @Test
  @BeforeEach
  void setup() {
      File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
      File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
      server = new GameServer(entitiesFile, actionsFile);
  }

  String sendCommandToServer(String command) {
      // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
      return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
      "Server took too long to respond (probably stuck in an infinite loop)");
  }

  // A lot of tests will probably check the game state using 'look' - so we better make sure 'look' works well !
  @Test
  void testLook() {
    String response = sendCommandToServer("simon: look");
    response = response.toLowerCase();
    assertTrue(response.contains("cabin"), "Did not see the name of the current room in response to look");
    assertTrue(response.contains("log cabin"), "Did not see a description of the room in response to look");
    assertTrue(response.contains("magic potion"), "Did not see a description of artifacts in response to look");
    assertTrue(response.contains("wooden trapdoor"), "Did not see description of furniture in response to look");
    assertTrue(response.contains("forest"), "Did not see available paths in response to look");
  }

  // Test that we can pick something up and that it appears in our inventory
  @Test
  void testGet()
  {
      String response;
      sendCommandToServer("simon: get potion");
      response = sendCommandToServer("simon: inv");
      response = response.toLowerCase();
      assertTrue(response.contains("potion"), "Did not see the potion in the inventory after an attempt was made to get it");
      response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertFalse(response.contains("potion"), "Potion is still present in the room after an attempt was made to get it");
  }

  // Test that we can goto a different location (we won't get very far if we can't move around the game !)
  @Test
  void testGoto()
  {
      sendCommandToServer("simon: goto forest");
      String response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertTrue(response.contains("key"), "Failed attempt to use 'goto' command to move to the forest - there is no key in the current location");
  }

  // Add more unit tests or integration tests here.

  @Test
  void testMapLoading() {
      // 从 server 中获取加载好的游戏地图
      java.util.HashMap<String, Location> map = server.getGameMap();

      // 1. 验证关键房间和 storeroom 是否存在
      assertTrue(map.containsKey("cabin"), "Map should contain 'cabin'");
      assertTrue(map.containsKey("forest"), "Map should contain 'forest'");
      assertTrue(map.containsKey("storeroom"), "Map should contain 'storeroom'");

      // 2. 验证 cabin 中的实体解析是否正确
      Location cabin = map.get("cabin");
      assertEquals("A log cabin in the woods", cabin.getDescription(), "Cabin description is wrong");

      boolean foundAxe = false;
      boolean foundTrapdoor = false;
      for (GameEntity entity : cabin.getEntities()) {
          if (entity.getName().equals("axe") && entity instanceof Artefact) foundAxe = true;
          if (entity.getName().equals("trapdoor") && entity instanceof Furniture) foundTrapdoor = true;
      }
      assertTrue(foundAxe, "Cabin should contain the artefact 'axe'");
      assertTrue(foundTrapdoor, "Cabin should contain the furniture 'trapdoor'");

      // 3. 验证路径连通性
      assertTrue(cabin.getPaths().containsKey("forest"), "Cabin should have a path to 'forest'");
  }

    @Test
    void testGameActionModel() {
        GameAction action = new GameAction();

        action.addTrigger("OPEN");
        action.addSubject("trapdoor");
        action.setNarration("You open the door.");

        assertTrue(action.getTriggers().contains("open"), "Trigger should be converted to lowercase 'open'");
        assertFalse(action.getTriggers().contains("OPEN"), "Uppercase trigger should not exist");
        assertTrue(action.getSubjects().contains("trapdoor"), "Subject should be added");
        assertEquals("You open the door.", action.getNarration(), "Narration should match");
        assertTrue(action.getConsumed().isEmpty(), "Consumed should be empty if nothing added");
    }

    @Test
    void testServerActionParsing() {
        // 1. 从 server 获取已加载的动作列表
        java.util.HashSet<GameAction> actions = server.getValidActions();

        // 2. 确保没有解析失败，列表中应该有动作
        assertFalse(actions.isEmpty(), "Server should have parsed some actions from XML");

        // 3. 在基础的 basic-actions.xml 中，应该有两个主要动作: open 和 chop
        boolean foundOpenAction = false;
        boolean foundChopAction = false;

        for (GameAction action : actions) {
            // 检查 open 动作
            if (action.getTriggers().contains("open") && action.getTriggers().contains("unlock")) {
                foundOpenAction = true;
                assertTrue(action.getSubjects().contains("trapdoor"), "Open action needs trapdoor");
                assertTrue(action.getConsumed().contains("key"), "Open action consumes key");
                assertTrue(action.getProduced().contains("cellar"), "Open action produces cellar");
            }

            // 检查 chop 动作 (它测试了 consumed 标签)
            if (action.getTriggers().contains("chop")) {
                foundChopAction = true;
                assertTrue(action.getSubjects().contains("tree") && action.getSubjects().contains("axe"));
                assertTrue(action.getConsumed().contains("tree"), "Chop action should consume tree");
            }
        }

        // 4. 断言这两个动作都被成功解析了
        assertTrue(foundOpenAction, "The 'open' action was not parsed correctly.");
        assertTrue(foundChopAction, "The 'chop' action was not parsed correctly.");
    }
}
