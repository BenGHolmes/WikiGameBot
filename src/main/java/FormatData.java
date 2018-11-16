import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.*;

public class FormatData {

    static LinkedList<String> removeList;
    static Pattern linkPattern;

    static HashMap<String, String> redirects;
    static HashMap<Integer, String> titleMap;
    static HashMap<String, Integer> indexMap;

    public static void main(String[] args){
        Gson gson = new Gson();
        redirects = new HashMap<>();
        titleMap = new HashMap<>();
        indexMap = new HashMap<>();

        // List of special wikipedia links that shouldn't be included
        removeList = new LinkedList<>(Arrays.asList(
            "Media", "Special", "Talk", "User", "User talk", "Wikipedia", "Wikipedia talk", "File", "File talk",
            "MediaWiki", "MediaWiki talk", "Template", "Template talk", "Help", "Help talk", "Category",
            "Category talk", "Portal", "Portal talk", "Book", "Book talk", "Draft", "Draft talk", "Education Program",
            "Education Program talk", "TimedText", "TimedText talk", "Module", "Module talk", "Gadget", "Gadget talk",
            "Gadget definition", "Gadget definition talk"
        ));


        // Pattern to find links in articles. Links are formatted as either:
        //      a) [[Page Title and Inline Text]]
        //      b) [[Page Title|Inline Text]]
        linkPattern = Pattern.compile("\\[\\[[^\\]\\|]+\\|?[^\\|\\]]*\\]\\]");

        File redirectsJson = new File("./res/files/redirects.json");
        File pagesJson = new File("./res/files/pages.json");

        // Build or load redirects file
        if (redirectsJson.exists()){
            System.out.println("Loading redirects from JSON");
            try {
                JsonReader reader = new JsonReader(new FileReader(redirectsJson));
                Type type = new TypeToken<HashMap<String, String>>() {}.getType();
                redirects = gson.fromJson(reader, type);
            } catch (Exception e){
                e.printStackTrace();
                buildRedirects();
            }
        } else {
            buildRedirects();
        }

        // Build or load pages file
        if (pagesJson.exists()){
            System.out.println("Loading pages from JSON");
            try{
                JsonReader reader = new JsonReader(new FileReader(pagesJson));
                Type type = new TypeToken<HashMap<Integer, String>>(){}.getType();
                indexMap = gson.fromJson(reader, type);
            } catch (Exception e){
                e.printStackTrace();
                buildPages();
            }
        } else {
            buildPages();
        }

        redirects.clear();

        for(Map.Entry<String, Integer> entry : indexMap.entrySet()){
            titleMap.put(entry.getValue(), entry.getKey());
        }

//        buildLinksJson();
    }

    private static void buildLinksJson(){
        System.out.println("Building pages");
        try {
            File inputFile = new File("./res/files/wiki.xml");
            BufferedReader in = new BufferedReader(new FileReader(inputFile));

            String line;
            String currPage;
            boolean remove;

            int count = 0;
            boolean keep;

            while ((line = in.readLine()) != null) {

                // If open title tage, we've reached a new page
                if (line.trim().startsWith("<title")) {
                    currPage = line.trim().replace("<title>", "").replace("</title>", "");
                    count++;
                    if (count % 10000 == 0) System.out.println(count);

                    while ((line = in.readLine()) != null) {

                        if (line.trim().equals("</page>") || (line.contains("#REDIRECT") && line.contains("<text")))
                            break;
                        if (line.trim().startsWith("<text")) {

                            // Find the Page that the redirect points to
                            Matcher linkMatcher = linkPattern.matcher(line);
                            while (linkMatcher.find()) {
                                String link = linkMatcher.group();

                                keep = true;
                                for (String x : removeList){
                                    if (link.contains("[[:"+x+":") || link.contains("[["+x+":")) {
                                        keep = false;
                                        break;
                                    }
                                }

                                if (keep) {
                                    String page = link.split("\\|")[0].replace("[[", "").replace("]]", "");
                                    String text;
                                    if (link.contains("|"))
                                        text = link.split("\\|")[1].replace("]]", "");
                                    else
                                        text = page;


                                }
                            }
                            break;
                        }
                    }
                }
            }

            Gson gson = new Gson();
            FileWriter writer = new FileWriter(new File("./res/files/redirects.json"));
            writer.write(gson.toJson(redirects));
            writer.flush();
            writer.close();

            System.out.println(count + " redirects done.");

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void buildPages(){
        System.out.println("Building pages");
        try {
            File inputFile = new File("./res/files/wiki.xml");
            BufferedReader in = new BufferedReader(new FileReader(inputFile));

            String line;
            String currPage;
            boolean remove;

            int count = 0;
            boolean keep;

            while ((line = in.readLine()) != null) {

                // If open title tage, we've reached a new page
                if (line.trim().startsWith("<title")) {
                    currPage = line.replace("<title>", "").replace("</title>", "").trim();
                    if (!indexMap.containsKey(currPage))
                        indexMap.put(currPage, indexMap.size());

                    while ((line = in.readLine()) != null) {

                        if (line.trim().equals("</page>") || (line.contains("#REDIRECT") && line.contains("<text")))
                            break;
                        if (line.trim().startsWith("<text")) {
                            count++;
                            if (count % 100000 == 0) System.out.println(count);

                            // Find the Page that the redirect points to
                            Matcher linkMatcher = linkPattern.matcher(line);
                            while (linkMatcher.find()) {
                                String link = linkMatcher.group();

                                keep = true;
                                for (String x : removeList){
                                    if (link.contains("[[:"+x+":") || link.contains("[["+x+":")) {
                                        keep = false;
                                        break;
                                    }
                                }

                                if (keep) {
                                    String page = link.split("\\|")[0].replace("[[", "").replace("]]", "").trim();
                                    if (!indexMap.containsKey(page))
                                        indexMap.put(page,indexMap.size());
                                }
                            }
                            break;
                        }
                    }
                }
            }

            Gson gson = new Gson();
            FileWriter writer = new FileWriter(new File("./res/files/pages.json"));
            writer.write(gson.toJson(titleMap));
            writer.flush();
            writer.close();

            System.out.println(count + " pages done.");

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void buildRedirects(){
        System.out.println("Building redirects");
        try {
            File inputFile = new File("./res/files/wiki.xml");
            BufferedReader in = new BufferedReader(new FileReader(inputFile));

            String line;
            String currPage;
            boolean remove;

            int count = 0;

            while ((line = in.readLine()) != null) {

                // If open title tage, we've reached a new page
                if (line.trim().startsWith("<title")) {
                    currPage = line.trim().replace("<title>", "").replace("</title>", "");

                    while ((line = in.readLine()) != null) {
                        if (line.trim().equals("</page>")) break;
                        if (line.contains("#REDIRECT") && line.contains("<text")) {

                            remove = false;

                            // Break if this page references a special wikipedia link rather than a Page
                            for (String x : removeList){
                                if (line.contains("[["+x+":") || currPage.contains("[["+x+":")) {
                                    remove = true;
                                    break;
                                }
                            }

                            if (remove) break;

                            // Find the Page that the redirect points to
                            Matcher linkMatcher = linkPattern.matcher(line);
                            if (linkMatcher.find()) {
                                count++;
                                if (count % 10000 == 0) System.out.println(count);
                                String link = linkMatcher.group();

                                // Remove links to specific sections [[Page#Section]] and only keep the link to the Page
                                if (link.contains("#") && link.length() > 1){
                                    link = link.split("#")[0];
                                }

                                link = link.replace("[[","")
                                        .replace("]]","");

                                redirects.put(currPage, link);
                            }
                            break;
                        }
                    }
                }
            }

            Gson gson = new Gson();
            FileWriter writer = new FileWriter(new File("./res/files/redirects.json"));
            writer.write(gson.toJson(redirects));
            writer.flush();
            writer.close();

            System.out.println(count + " redirects done.");

        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
