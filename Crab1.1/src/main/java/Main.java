import java.util.*;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.util.ArrayList;

public class Main {

    private final static int WARMUP_SIZE = 10000;
    static List<String> testList = new ArrayList<>(WARMUP_SIZE);

    /* Static portion that's used to warm the JVM up */
    static {
        for (int i = 0; i < WARMUP_SIZE; i++)
            testList.add(Integer.toString(i));
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException, CsvException {
        Crab c = new Crab();

        Crab.Crawl_Type crawl = Crab.Crawl_Type.Sentiment;

        Crab.CrabCrawl(crawl);
    }
}