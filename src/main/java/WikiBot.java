import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import jnr.ffi.annotations.In;
import net.bytebuddy.asm.Advice;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.MapUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;

public class WikiBot {
    /*
     * TODO:
     *  1) Replace all wait(time) with pauses until page has loaded, link is clickable, etc
     */

    private static String[] loginCreds;
    private static WebDriver browser;
    private static String[] targets;
    private static HashMap<String,Integer> idMap;
    private static HashMap<Integer,String> titleMap;


    private static LinkedList<List<String>> paths;
    private static final String driver = "com.mysql.jdbc.Driver";
    private static final String url = "jdbc:mysql://localhost:3306/wikiGame";
    private static Gson gson;
    private static Connection sql;



    public static void main(String[] args) throws Exception{
        targets = new String[2];
        paths = new LinkedList<>();
        gson = new Gson();
        idMap = new HashMap<>();
        titleMap = new HashMap<>();

        setupSQL();
        loadMap();

        browser = new ChromeDriver();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run(){
                System.out.println("Exiting");
                browser.close();
            }
        });
        loadLoginCreds();
        login();

        wait(2.);

        while(true){
            try {
                playGame();
            } catch (Exception e){
                e.printStackTrace();
                if(e instanceof UnhandledAlertException){
                    browser.switchTo().alert().dismiss();
                }
                wait(5.);
            }
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
        System.out.println("Starting game");
        int wins = 0;
        System.out.println(browser.findElements(By.xpath("//div[@class='col-12 wgg-article-link']")).get(0).getText());
        System.out.println(targets[0]);
        while (browser.findElements(By.xpath("//div[@class='col-12 wgg-article-link']")).get(0).getText().equals(targets[0])){
            wait(0.1);
        }

        getTargets();
        System.out.println("Waiting for game to start. Pre-planning paths from " + targets[0] + " to " + targets[1]);
        fastestPath(targets[0], targets[1]);
        System.out.println("Done path planning.");

        // If path planning is done before the game starts, wait for it to start
        while(browser.findElements(By.xpath("//button[@id='playNowButton']")).isEmpty())
            wait(1.);

        browser.findElements(By.xpath("//button[@id='playNowButton']")).get(0).click();
        wait(1.);

        while(browser.getCurrentUrl().contains("/wiki/") && !paths.isEmpty()){
            boolean done = false;
            try {
                followPath(paths.removeFirst());
                List<WebElement> elements = browser.findElements(By.tagName("button"));
                for (WebElement elem : elements){
                    if(elem.getText().equals("FIND ANOTHER PATH!")) {
                        elem.click();
                        wins ++;
                        done = true;
                        wait(1.);
                    }
                }

                if(!done && !reset()) {
                    break;
                }
            } catch (Exception e){
                reset();
            }
        }

        System.out.println("Game finished. " + wins + " wins");
    }

    private static void login(){
        browser.get("https://www.thewikigame.com/");
        browser.findElement(By.className("seperate-login-link")).click();

        browser.findElement(By.name("username")).sendKeys(loginCreds[0]);
        browser.findElement(By.name("password")).sendKeys(loginCreds[1]);
        browser.findElement(By.xpath("//button[text()='Login']")).click();
    }

    private static void wait(double time){
        try{
            TimeUnit.MILLISECONDS.sleep((int)time*1000);
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

    private static void followPath(List<String> path) throws Exception{
        System.out.println("Following: " + path);
        for (int i = 1; i < path.size(); i++){
            if(!browser.getCurrentUrl().contains("/wiki/")) break;
            String xpath = "//a[contains(@href, '/wiki/" + path.get(i).replace(" ", "_") + "')]";
            List<WebElement> elements = browser.findElements(By.xpath(xpath));
            if(elements.isEmpty()){
                System.out.println("Can't find link with xpath: " + "//a[@href='/wiki/" + path.get(i).trim().replace(" ", "_") + "']");
            } else {
                for (WebElement elem : elements) {
                    if(!browser.getCurrentUrl().contains("/wiki/")) break;
                    new Actions(browser).moveToElement(elem).build().perform();
                    wait(1.);
                    elem.click();
                    wait(1.);
                    break;
                }
            }
        }

        System.out.println("PATH DONE");
        wait(1.);
    }

    private static void setupSQL() throws Exception{
        Class.forName(driver);
        JsonReader reader = new JsonReader(new FileReader(new File("./res/files/sqlCreds.json")));
        Type type = new TypeToken<String[]>() {}.getType();
        String[] sqlCreds = gson.fromJson(reader, type);
        sql = DriverManager.getConnection(url, sqlCreds[0], sqlCreds[1]);
        sql.setAutoCommit(false);
    }

    private static void loadMap() throws Exception{
        System.out.println("Setting up.");

        int lowBound = 0;
        int upBound = 1000;
        int count = 0;
        int percent = 0;

        while (lowBound < 15E6) {
            String cmd = "SELECT ID,Title FROM pages WHERE ID <= " + upBound + " AND ID > " + lowBound;
            Statement statement = sql.createStatement();
            ResultSet res = statement.executeQuery(cmd);

            while (res.next()) {
                count++;
                if(count % 1444444 == 0){
                    percent += 10;
                    System.out.print("\rSetup " + percent + "% complete");
                }
                idMap.put(res.getString("Title"), res.getInt("ID"));
            }

            statement.close();
            lowBound = upBound;
            upBound+=1000;
        }

        for (String key : idMap.keySet()){
            titleMap.put(idMap.get(key), key);
        }

        System.out.println("\rSetup done.");
    }

    private static void fastestPath(String from, String to) throws Exception{
        System.out.println("Started");
        paths.clear();

        Integer toId = idMap.get(to);
        Integer fromId = idMap.get(from);

        LinkedList<Integer> queue = new LinkedList<>();
        HashSet<Integer> visited = new HashSet<>();
        HashMap<Integer, Integer> steps = new HashMap<>();

        System.out.println("From " + fromId + " to " + toId);

        if(fromId == null || toId == null) throw new Exception("Can't find start or end page");

        queue.add(fromId);
        visited.add(fromId);
        steps.put(fromId, null);

        Type hashSetType = new TypeToken<HashSet<Integer>>() {}.getType();

        long start = Calendar.getInstance().getTimeInMillis();

        while (!queue.isEmpty()) {
            int parent = queue.removeFirst();

            String childCommand = "SELECT Children FROM graph WHERE ID=" + parent;
            ResultSet childSet = sql.createStatement().executeQuery(childCommand);
            if(childSet.next()) {
                HashSet<Integer> children = gson.fromJson(childSet.getString("Children"), hashSetType);

                for (int child : children) {
                    if (!visited.contains(child)) {
                        if(!(child==toId))visited.add(child);
                        queue.add(child);
                        steps.put(child, parent);
                    }

                    if (child == toId) {
                        ArrayList<Integer> pathInt = new ArrayList();
                        for (Integer next = child; next != null; next = steps.get(next)) {
                            pathInt.add(next);
                        }

                        int max = pathInt.size() - 1;
                        ArrayList<String> pathString = new ArrayList<>();
                        for (int i = max; i >= 0 ; i--){
                            pathString.add(titleMap.get(pathInt.get(i)));
                        }

                        childSet.close();

                        paths.add(pathString);
                        if(paths.size() > 20 || (Calendar.getInstance().getTimeInMillis() - start) > 30000) {
                            System.out.println("Found " + paths.size() + " paths in " + (Calendar.getInstance().getTimeInMillis() - start)/1000 + "s");
                            return;
                        }
                    }
                }
            }

            childSet.close();
        }
    }

    private static boolean reset(){
        browser.findElement(By.className("navbar-brand")).click();
        wait(1.);
        while(browser.findElements(By.xpath("//button[@id='playNowButton']")).isEmpty())
            wait(1.);

        String to = browser.findElements(By.xpath("//div[@class='col-12 wgg-article-link']")).get(0).getText();
        boolean same = to.equals(targets[0]);

        if(same){
            browser.findElements(By.xpath("//button[@id='playNowButton']")).get(0).click();
            wait(1.);
        }

        return same;
    }
}
