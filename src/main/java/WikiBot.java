import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.bytebuddy.asm.Advice;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.sql.Driver;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;


public class WikiBot {
    private static String[] loginCreds;
    private static WebDriver browser;
    private static boolean inGame;
    private static String[] targets;
    private static LinkedList<String[]> paths;

    public static void main(String[] args){
        loadLoginCreds();
        browser = new ChromeDriver();
        inGame = false;
        targets = new String[2];
        paths = new LinkedList<>();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run(){
                System.out.println("Exiting");
                browser.close();
            }
        });

        // Navigate to wiki game and log in
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

    private static void playGame(){
        getTargets();
        System.out.println("Waiting for game to start. Pre-planning paths from " + targets[0] + " to " + targets[1]);
        paths = getPaths();
        System.out.println("Done path planning");

        // If path planning is done before the game starts, wait for it to start
        while(browser.findElements(By.xpath("//button[@id='playNowButton']")).isEmpty())
            wait(1);

        browser.findElements(By.xpath("//button[@id='playNowButton']")).get(0).click();

        wait(3);

        while(browser.getCurrentUrl().contains("/wiki/") && !paths.isEmpty()){
            followPath(paths.removeFirst());
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

    private static void followPath(String[] path){
        for (String link : path){
            List<WebElement> elements = browser.findElements(By.xpath("//a[@href='/wiki/" + link.replace(" ", "_") + "']"));
            if(elements.isEmpty()){
                System.out.println("No matches");
                System.exit(1);
            } else {
                elements.get(0).click();
            }
        }
    }

    private static void loadMap(){
        //TODO: Load this
    }
}
