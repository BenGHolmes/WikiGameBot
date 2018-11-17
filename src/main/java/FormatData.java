import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.*;

public class FormatData {

    static ArrayList<String> removeList;
    static Pattern linkPattern;

    private static final String driver = "com.mysql.jdbc.Driver";
    private static final String url = "jdbc:mysql://localhost:3306/wikiGame";

    private static Connection sql;
    private static Gson gson;

    public static void main(String[] args) throws Exception {
        gson = new Gson();

        // List of special wikipedia links that shouldn't be included
        removeList = new ArrayList<>(Arrays.asList(
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

        setupSql();

        buildSqlDB();
    }

    private static void setupSql() throws Exception {
        // Build the SQL database of pages and redirects
        Class.forName(driver);
        JsonReader reader = new JsonReader(new FileReader(new File("./res/files/sqlCreds.json")));
        Type type = new TypeToken<String[]>() {}.getType();
        String[] sqlCreds = gson.fromJson(reader, type);
        sql = DriverManager.getConnection(url, sqlCreds[0], sqlCreds[1]);

        System.out.println(pageInDB("TEST"));

        // Clear both databases
        sql.prepareStatement("DELETE FROM pages").execute();
        sql.prepareStatement("DELETE FROM redirects");
    }

//    private static void buildLinksJson(){
//        System.out.println("Building pages");
//        try {
//            File inputFile = new File("./res/files/wiki.xml");
//            BufferedReader in = new BufferedReader(new FileReader(inputFile));
//
//            String line;
//            String currPage;
//            boolean remove;
//
//            int count = 0;
//            boolean keep;
//
//            while ((line = in.readLine()) != null) {
//
//                // If open title tage, we've reached a new page
//                if (line.trim().startsWith("<title")) {
//                    currPage = line.trim().replace("<title>", "").replace("</title>", "");
//                    count++;
//                    if (count % 10000 == 0) System.out.println(count);
//
//                    while ((line = in.readLine()) != null) {
//
//                        if (line.trim().equals("</page>") || (line.contains("#REDIRECT") && line.contains("<text")))
//                            break;
//                        if (line.trim().startsWith("<text")) {
//
//                            // Find the Page that the redirect points to
//                            Matcher linkMatcher = linkPattern.matcher(line);
//                            while (linkMatcher.find()) {
//                                String link = linkMatcher.group();
//
//                                keep = true;
//                                for (String x : removeList){
//                                    if (link.contains("[[:"+x+":") || link.contains("[["+x+":")) {
//                                        keep = false;
//                                        break;
//                                    }
//                                }
//
//                                if (keep) {
//                                    String page = link.split("\\|")[0].replace("[[", "").replace("]]", "");
//                                    String text;
//                                    if (link.contains("|"))
//                                        text = link.split("\\|")[1].replace("]]", "");
//                                    else
//                                        text = page;
//
//
//                                }
//                            }
//                            break;
//                        }
//                    }
//                }
//            }
//
//            Gson gson = new Gson();
//            FileWriter writer = new FileWriter(new File("./res/files/redirects.json"));
//            writer.write(gson.toJson(redirects));
//            writer.flush();
//            writer.close();
//
//            System.out.println(count + " redirects done.");
//
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//    }

//    private static void buildPages(){
//        System.out.println("Building pages");
//        try {
//            File inputFile = new File("./res/files/wiki.xml");
//            BufferedReader in = new BufferedReader(new FileReader(inputFile));
//
//            String line;
//            String currPage;
//
//            int count = 0;
//            boolean keep;
//
//            while ((line = in.readLine()) != null) {
//
//                // If open title tag, we've reached a new page
//                if (line.trim().startsWith("<title")) {
//                    currPage = line.replace("<title>", "").replace("</title>", "").trim();
//                    if (!indexMap.containsKey(currPage))
//                        indexMap.put(currPage, indexMap.size());
//
//                    while ((line = in.readLine()) != null) {
//
//                        if (line.trim().equals("</page>") || (line.contains("#REDIRECT") && line.contains("<text")))
//                            break;
//                        if (line.trim().startsWith("<text")) {
//                            count++;
//                            if (count % 100000 == 0) System.out.println(count);
//
//                            // Find the Page that the redirect points to
//                            Matcher linkMatcher = linkPattern.matcher(line);
//                            while (linkMatcher.find()) {
//                                String link = linkMatcher.group();
//
//                                keep = true;
//                                for (String x : removeList){
//                                    if (link.contains("[[:"+x+":") || link.contains("[["+x+":")) {
//                                        keep = false;
//                                        break;
//                                    }
//                                }
//
//                                if (keep) {
//                                    String page = link.split("\\|")[0].replace("[[", "").replace("]]", "").trim();
//                                    if (!indexMap.containsKey(page))
//                                        indexMap.put(page,indexMap.size());
//                                }
//                            }
//                            break;
//                        }
//                    }
//                }
//            }
//
//            Gson gson = new Gson();
//            FileWriter writer = new FileWriter(new File("./res/files/pages.json"));
//            writer.write(gson.toJson(titleMap));
//            writer.flush();
//            writer.close();
//
//            System.out.println(count + " pages done.");
//
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//    }

//    private static void buildRedirects(){
//        System.out.println("Building redirects");
//        try {
//            File inputFile = new File("./res/files/wiki.xml");
//            BufferedReader in = new BufferedReader(new FileReader(inputFile));
//
//            String line;
//            String currPage;
//            boolean remove;
//
//            int count = 0;
//
//            while ((line = in.readLine()) != null) {
//
//                // If open title tage, we've reached a new page
//                if (line.trim().startsWith("<title")) {
//                    currPage = line.trim().replace("<title>", "").replace("</title>", "");
//
//                    while ((line = in.readLine()) != null) {
//                        if (line.trim().equals("</page>")) break;
//                        if (line.contains("#REDIRECT") && line.contains("<text")) {
//
//                            remove = false;
//
//                            // Break if this page references a special wikipedia link rather than a Page
//                            for (String x : removeList){
//                                if (line.contains("[["+x+":") || currPage.contains("[["+x+":")) {
//                                    remove = true;
//                                    break;
//                                }
//                            }
//
//                            if (remove) break;
//
//                            // Find the Page that the redirect points to
//                            Matcher linkMatcher = linkPattern.matcher(line);
//                            if (linkMatcher.find()) {
//                                count++;
//                                if (count % 10000 == 0) System.out.println(count);
//                                String link = linkMatcher.group();
//
//                                // Remove links to specific sections [[Page#Section]] and only keep the link to the Page
//                                if (link.contains("#") && link.length() > 1){
//                                    link = link.split("#")[0];
//                                }
//
//                                link = link.replace("[[","")
//                                        .replace("]]","");
//
//                                redirects.put(currPage, link);
//                            }
//                            break;
//                        }
//                    }
//                }
//            }
//
//            Gson gson = new Gson();
//            FileWriter writer = new FileWriter(new File("./res/files/redirects.json"));
//            writer.write(gson.toJson(redirects));
//            writer.flush();
//            writer.close();
//
//            System.out.println(count + " redirects done.");
//
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//    }

    private static void buildSqlDB() throws Exception{
        File inputFile = new File("./res/files/wiki.xml");
        BufferedReader in = new BufferedReader(new FileReader(inputFile));

        String line;
        String currPage;

        boolean keep;

        while ((line = in.readLine()) != null) {
            if (line.trim().startsWith("<title")) {
                currPage = line.trim().replace("<title>", "").replace("</title>", "");
                if (!pageInDB(currPage))
                    addPageToDB(currPage);

                while ((line = in.readLine()) != null) {
                    if (line.trim().equals("</page>")) break;
                    if (line.contains("#REDIRECT") && line.contains("<text")) {
                        keep = true;

                        // Break if this page references a special wikipedia link rather than a Page
                        for (String x : removeList) {
                            if (line.contains("[[" + x + ":") || currPage.contains("[[" + x + ":")) {
                                keep = false;
                                break;
                            }
                        }

                        if (!keep) break;

                        // Find the Page that the redirect points to
                        Matcher linkMatcher = linkPattern.matcher(line);
                        if (linkMatcher.find()) {
                            String link = linkMatcher.group();

                            // Remove links to specific sections [[Page#Section]] and only keep the link to the Page
                            if (link.contains("#") && link.length() > 1)
                                link = link.split("#")[0];

                            link = link.replace("[[", "").replace("]]", "");

                            addRedirectToDB(currPage);
                        }
                        break;
                    }
                }
            }
        }
    }

    private static boolean pageInDB(String page) throws Exception{
        page = page.replace("'", "''");
        System.out.println(page);
        String sqlCommand = "SELECT COUNT(*) FROM pages WHERE Title='" + page + "'";
        ResultSet set = sql.createStatement().executeQuery(sqlCommand);

        set.next();
        return set.getInt(1) == 1;
    }

    private static void addPageToDB(String page) throws Exception{
        //TODO: This
    }

    private static void addRedirectToDB(String redirect) throws Exception{
        //TODO: This
    }
}
