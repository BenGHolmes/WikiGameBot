import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import jnr.ffi.annotations.In;
import net.bytebuddy.asm.Advice;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;


public class WikiBot {
    private static String[] loginCreds;
    private static WebDriver browser;
    private static boolean inGame;
    private static String[] targets;
    private static HashMap<Integer, HashSet<Integer>> graph;
    private static LinkedList<String[]> paths;
    private static final String driver = "com.mysql.jdbc.Driver";
    private static final String url = "jdbc:mysql://localhost:3306/wikiGame";
    private static Gson gson;
    private static Connection sql;



    public static void main(String[] args) throws Exception{
        loadLoginCreds();
        browser = new ChromeDriver();
        inGame = false;
        targets = new String[2];
        paths = new LinkedList<>();
        gson = new Gson();
        graph = new HashMap<>();

//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            public void run(){
//                System.out.println("Exiting");
//                browser.close();
//            }
//        });

        setupSQL();
        
        login();

        wait(2);

        while(true){
            playGame();
        }
    }

    private static void loadLoginCreds(){
        Gson gson = new Gson();
        try {
            File loginCredFile = new File("./res/files/loginCreds.json");
            JsonReader reader = new JsonReader(new FileReader(loginCredFile));
            Type type = new TypeToken<String[]>() {}.getType();
            loginCreds = gson.fromJson(reader, type);
        } catch (Exception e){
            System.exit(1);
        }
    }

    private static void playGame() throws Exception{
        getTargets();
        System.out.println("Waiting for game to start. Pre-planning paths from " + targets[0] + " to " + targets[1]);
        ArrayList<String> path = fastestPath(targets[0], targets[1]);
        System.out.println("Done path planning.  Path: " + path);

        // If path planning is done before the game starts, wait for it to start
        while(browser.findElements(By.xpath("//button[@id='playNowButton']")).isEmpty())
            wait(1);

        browser.findElements(By.xpath("//button[@id='playNowButton']")).get(0).click();

        wait(3);

        while(browser.getCurrentUrl().contains("/wiki/") && !path.isEmpty()){
            followPath(path);
            wait(3);
            browser.findElements(By.xpath("//button[@class='btn-lg']")).get(0).click();
        }
    }

    private static void login(){
        browser.get("https://www.thewikigame.com/");
        browser.findElement(By.className("seperate-login-link")).click();

        browser.findElement(By.name("username")).sendKeys(loginCreds[0]);
        browser.findElement(By.name("password")).sendKeys(loginCreds[1]);
        browser.findElement(By.xpath("//button[text()='Login']")).click();
    }

    private static void wait(int time){
        try{
            TimeUnit.SECONDS.sleep(time);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void getTargets(){
        List<WebElement> elements = browser.findElements(By.xpath("//div[@class='col-12 wgg-article-link']"));
        targets[0] = elements.get(0).getText();
        targets[1] = elements.get(1).getText();
    }

    private static LinkedList<String[]> getPaths(){
        LinkedList<String[]> paths = new LinkedList<>();
        // TODO: Find paths in the graph
        String[] path3 = {"Spelling"};
        paths.add(path3);

        paths.sort(new Comparator<String[]>() {
            @Override
            public int compare(String[] o1, String[] o2) {
                return o1.length - o2.length;
            }
        });

        return paths;
    }

    private static void followPath(ArrayList<String> path){
        for (int i = 1; i < path.size(); i++){
//            List<WebElement> elements = browser.findElements(By.xpath("//a[@href='/wiki/" + path.get(i).trim().replace(" ", "_") + "']"));
            List<WebElement> elements = browser.findElements(By.tagName("a"));
            if(elements.isEmpty()){
                System.out.println("Can't find link with xpath: " + "//a[@href='/wiki/" + path.get(i).trim().replace(" ", "_") + "']");
            } else {
                for (WebElement elem : elements) {
                    String href = elem.getAttribute("href");
                    if (href != null && href.contains("/wiki/")) {
                        href = href.split("/wiki/")[1];
                        if (href.toLowerCase().equals(path.get(i).toLowerCase().replace(" ", "_"))) {
                            new Actions(browser).moveToElement(elem).build().perform();
                            elem.click();
                            wait(1);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void setupSQL() throws Exception{
        Class.forName(driver);
        JsonReader reader = new JsonReader(new FileReader(new File("./res/files/sqlCreds.json")));
        Type type = new TypeToken<String[]>() {}.getType();
        String[] sqlCreds = gson.fromJson(reader, type);
        sql = DriverManager.getConnection(url, sqlCreds[0], sqlCreds[1]);
        sql.setAutoCommit(false);
    }

    private static ArrayList<String> fastestPath(String from, String to) throws Exception{

        System.out.println("Started");

        String idCommands = "SELECT ID,Title FROM pages WHERE Title LIKE '" + from + "' OR Title LIKE '" + to + "'";
        ResultSet idSet = sql.createStatement().executeQuery(idCommands);

        int toId = 0;
        int fromId = 0;

        LinkedList<Integer> queue = new LinkedList<>();
        HashSet<Integer> visited = new HashSet<>();
        HashMap<Integer, Integer> steps = new HashMap<>();

        while (idSet.next()){
            if (idSet.getString("Title").equals(to)) toId = idSet.getInt("ID");
            else fromId = idSet.getInt("ID");
        }

        System.out.println("From " + fromId + " to " + toId);

        if(fromId == 0 || toId == 0) throw new Exception("Can't find start or end page");

        idSet.close();
        queue.add(fromId);
        visited.add(fromId);
        steps.put(fromId, null);

        Type hashSetType = new TypeToken<HashSet<Integer>>() {}.getType();

        while (!queue.isEmpty()) {
            int parent = queue.removeFirst();

            String childCommand = "SELECT Children FROM graph WHERE ID=" + parent;
            ResultSet childSet = sql.createStatement().executeQuery(childCommand);
            if(childSet.next()) {
                HashSet<Integer> children = gson.fromJson(childSet.getString("Children"), hashSetType);

                for (int child : children) {
                    if (!visited.contains(child)) {
                        queue.add(child);
                        visited.add(child);
                        steps.put(child, parent);
                    }

                    if (child == toId) {
                        ArrayList<Integer> pathInt = new ArrayList();
                        for (Integer next = child; next != null; next = steps.get(next)) {
                            pathInt.add(next);
                        }

                        ArrayList<String> pathString = new ArrayList<>();
                        while(pathString.size() < pathInt.size()) pathString.add("a");

                        String pathComand = "SELECT ID, Title, Redirect FROM pages WHERE ID in (" + pathInt.toString().replace("[","").replace("]","") + ")";
                        ResultSet pathSet = sql.createStatement().executeQuery(pathComand);
                        int last = pathInt.size() - 1;

                        while(pathSet.next()){
                            int id = pathSet.getInt("ID");
                            pathString.set(last - pathInt.indexOf(id), pathSet.getString("Title"));
                        }

                        pathSet.close();
                        childSet.close();
                        return pathString;
                    }
                }
            }

            childSet.close();
        }
        return new ArrayList<>();
    }
}
