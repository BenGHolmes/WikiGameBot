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

//        buildSqlDB();
        buildGraph();
    }

    private static void setupSql() throws Exception {
        // Build the SQL database of pages and redirects
        Class.forName(driver);
        JsonReader reader = new JsonReader(new FileReader(new File("./res/files/sqlCreds.json")));
        Type type = new TypeToken<String[]>() {}.getType();
        String[] sqlCreds = gson.fromJson(reader, type);
        sql = DriverManager.getConnection(url, sqlCreds[0], sqlCreds[1]);
        sql.setAutoCommit(false);

        // Clear database
//        sql.prepareStatement("DELETE FROM pages").execute();
//        sql.prepareStatement("ALTER TABLE pages AUTO_INCREMENT = 0").execute();
//        sql.commit();
    }

    private static void buildSqlDB() throws Exception{
        File inputFile = new File("./res/files/wiki.xml");
        BufferedReader in = new BufferedReader(new FileReader(inputFile));

        int count = 0;

        String line;
        String link;
        String currPage;

        boolean keep;

        while ((line = in.readLine()) != null) {
            if (line.trim().startsWith("<title")) {
                count ++;
                if (count % 10000 == 0) {
                    System.out.println(count + " pages done");
                    sql.commit();
                }

                link = null;
                keep = true;
                currPage = line.trim().replace("<title>", "").replace("</title>", "");

                while ((line = in.readLine()) != null) {
                    if (line.trim().equals("</page>")) break;
                    if (line.contains("#REDIRECT") && line.contains("<text")) {

                        // Find the Page that the redirect points to
                        Matcher linkMatcher = linkPattern.matcher(line);
                        if (linkMatcher.find()) {
                            link = linkMatcher.group();

                            // Remove links to specific sections [[Page#Section]] and only keep the link to the Page
                            if (link.contains("#") && link.length() > 1)
                                link = link.split("#")[0];

                            link = link.replace("[[", "").replace("]]", "");
                        }
                        break;
                    }
                }

                // Break if this page references a special wikipedia link rather than a Page
                for (String x : removeList) {
                    if (currPage.contains(x + ":") || ((link != null) && link.contains(x + ":"))) {
                        keep = false;
                        break;
                    }
                }

                if (keep) addPageToDB(currPage, link);
            }
        }

        sql.commit();
    }

    private static void buildGraph() throws Exception{
        //TODO: This
    }

    private static void addPageToDB(String page, String redirect) throws Exception{
        page = format(page);
        if (redirect != null) redirect = format(redirect);
        String cmd = "INSERT INTO pages (Title, Redirect) VALUES ('" + page + "','" + redirect + "')";
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
