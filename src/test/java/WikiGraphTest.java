import org.junit.Test;
import static org.junit.Assert.*;


public class WikiGraphTest {
    @Test
    public void importExportTest(){
        WikiGraph graph = new WikiGraph("./res/files/testGraph.json", "./res/files/testPages.json");

//        graph.exportGraphJson();

        WikiGraph b = new WikiGraph("./res/files/graph.json", "./res/files/pages.json");

        assertEquals(b.getGraph(), graph.getGraph());
        assertEquals(b.getPages(), graph.getPages());
    }

    @Test
    public void xmlTest(){
        WikiGraph graph = new WikiGraph();
    }
}