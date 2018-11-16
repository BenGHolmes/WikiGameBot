import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lmdbjava.*;
import java.sql.*;


public class WikiGraph {
    private HashMap<String, Integer> indexMap;
    private HashMap<Integer, String> pageMap;
    private HashMap<Integer, HashSet<Integer>> graph;

    public WikiGraph() {
        graph = new HashMap<>();
        pageMap = new HashMap<>();
        File graphFile = new File("./res/files/graph.json");
        File pagesFile = new File("./res/files/pages.json");
    }

    public WikiGraph(String graphFile, String pagesFile) {
        graph = new HashMap<>();
        pageMap = new HashMap<>();
        loadPagesJson(pagesFile);
        loadGraphJson(graphFile);
    }

    public HashMap<Integer, HashSet<Integer>> getGraph() {
        return graph;
    }

    public HashMap<String, Integer> getPages() {
        return indexMap;
    }

    public Map<String, Integer> loadPagesJson(String filename) {
        Gson gson = new Gson();

        try {
            JsonReader reader = new JsonReader(new FileReader(filename));
            Type type = new TypeToken<HashMap<String, Integer>>() {
            }.getType();
            this.pageMap = gson.fromJson(reader, type);
            return this.indexMap;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<Integer, HashSet<Integer>> loadGraphJson(String filename) {
        //TODO: This

        Gson gson = new Gson();

        try {
            JsonReader reader = new JsonReader(new FileReader(filename));
            Type type = new TypeToken<HashMap<Integer, HashSet<Integer>>>() {
            }.getType();
            this.graph = gson.fromJson(reader, type);
            return this.graph;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }
}
