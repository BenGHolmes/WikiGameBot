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

import jnr.ffi.annotations.In;
import org.apache.commons.lang3.StringUtils;

import javax.print.DocFlavor;

public class FormatData {

    static ArrayList<String> removeList;
    static Pattern linkPattern;
    static HashMap<Integer, HashSet<Integer>> graph;
    static HashSet<Integer> redirects;

    private static final String driver = "com.mysql.jdbc.Driver";
    private static final String url = "jdbc:mysql://localhost:3306/wikiGame";

    private static Connection sql;
    private static Gson gson;
    private static HashSet<String> inSql;
    private static int sqlIndex;
    private static HashMap<String, Integer> tempMap;

    public static void main(String[] args) throws Exception {
        gson = new Gson();
        graph = new HashMap<>();
        redirects = new HashSet<>();
        sqlIndex = 1;
        inSql = new HashSet<>();
        tempMap = new HashMap<>();

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

//        clearDB();
//        buildSqlDB();
        buildGraph();
    }

    private static void setupSql() throws Exception {
        Class.forName(driver);
        JsonReader reader = new JsonReader(new FileReader(new File("./res/files/sqlCreds.json")));
        Type type = new TypeToken<String[]>() {}.getType();
        String[] sqlCreds = gson.fromJson(reader, type);
        sql = DriverManager.getConnection(url, sqlCreds[0], sqlCreds[1]);
        sql.setAutoCommit(false);
    }

    private static void clearDB() throws Exception{
        sql.prepareStatement("DELETE FROM pages").execute();
        sql.prepareStatement("ALTER TABLE pages AUTO_INCREMENT = 0").execute();
        sql.commit();
        System.out.println("DB Cleared");
    }

    private static void buildSqlDB() throws Exception{
        File inputFile = new File("./res/files/wiki.xml");
        BufferedReader in = new BufferedReader(new FileReader(inputFile));

        int count = 0;

        String line;
        String currPage;

        boolean keep;
        boolean redirect;

        while ((line = in.readLine()) != null) {
            if (line.trim().startsWith("<title")) {
                count ++;
                if (count % 10000 == 0) {
                    System.out.println(count + " pages done");
                    sql.commit();
                }

                keep = true;
                redirect = false;
                currPage = line.trim().replace("<title>", "").replace("</title>", "");
                ArrayList<String> links = new ArrayList<>();

                // Break if this page references a special wikipedia link rather than a Page
                for (String x : removeList) {
                    if (currPage.contains(x + ":")) {
                        keep = false;
                        break;
                    }
                }

                if(keep) {
                    while ((line = in.readLine()) != null) {
                        if (line.trim().equals("</page>")) break;

                        if (line.contains("<text") && line.contains("#REDIRECT")) redirect = true;

                        // Find the Page that the redirect points to
                        Matcher linkMatcher = linkPattern.matcher(line);
                        while (linkMatcher.find()) {
                            String link = linkMatcher.group();
                            keep = true;

                            if (!StringUtils.isAsciiPrintable(link))
                                keep = false;

                            for (String x : removeList) {
                                if (link.contains(x + ":")) {
                                    keep = false;
                                    break;
                                }
                            }

                            if (keep) {
                                String page = link.split("\\|")[0].replace("[[", "").replace("]]", "");
                                links.add(format(page));
                            }
                        }
                    }

                    addPageToDB(format(currPage), links, redirect);
                }
            }
        }

        sql.commit();
    }

    private static void buildGraph() throws Exception {
        int lowBound = 0;
        int upBound = 1000;
        int count = 0;

        while (lowBound < 20E6) {

            String cmd = "SELECT ID,Links FROM pages WHERE ID <= " + upBound + " AND ID > " + lowBound;
            Statement statement = sql.createStatement();
            ResultSet res = statement.executeQuery(cmd);

            while (res.next()) {
                count++;
                System.out.println(count + " pages graphed.");

                HashSet<Integer> links = new HashSet<>();
                String linkString = res.getString("Links")
                        .replace("'", "''")
                        .replace("[", "'")
                        .replace(", ", "','")
                        .replace("]", "'");

                Statement linkIdStatement = sql.createStatement();
                try {
                    ResultSet linkIds = linkIdStatement.executeQuery("SELECT ID FROM pages WHERE Title IN (" + linkString + ")");
                    while (linkIds.next())
                        links.add(linkIds.getInt("ID"));

                    linkIdStatement.close();

                    graph.put(res.getInt("ID"), links);
                } catch (Exception e){
                    System.out.println("SELECT ID FROM pages WHERE Title IN (" + linkString + ")");
                    throw e;
                }
            }

            statement.close();
            lowBound = upBound;
            upBound+=1000;
            statement.close();
        }
    }

    private static void addPageToDB(String page, List<String> links, boolean redirect) throws Exception{
        String cmd = "INSERT INTO pages (ID, Title, Links, Redirect) VALUES (" + sqlIndex + ",'" + page + "','"
                + links.toString() + "'," + redirect + ")";
        sqlIndex++;

        try {
            PreparedStatement statement = sql.prepareStatement(cmd);
            statement.execute();
            statement.close();
        } catch (Exception e) {
            System.out.println("SQL Syntax broke. Ignoring command: " + cmd);
        }
    }

    private static String format(String string){
        return string.replace("'", "''")
                .replace("\\", "\\\\");
    }
}
