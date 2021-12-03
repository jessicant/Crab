/*
 ***LICENSE***
 Copyright (c) 2021 l33pf (https://github.com/l33pf) & jelph (https://github.com/jelph)

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 **/

import java.io.*;
import java.util.*;
import java.util.Stack;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.opencsv.exceptions.CsvException;
import org.jsoup.*;
import org.jsoup.nodes.Document;

import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

public final class Crab {

    public static final CrabStack urlStack = new CrabStack();

    private static final int numOfThreads = (Runtime.getRuntime().availableProcessors())+1;
    private static final int CAPACITY = Integer.MAX_VALUE; //10

    public static final Queue<String> keyWordsList = new ConcurrentLinkedQueue<String>();
    public static final Queue<String> visitedList = new ConcurrentLinkedQueue<>();

    public static ConcurrentHashMap<String,SentimentType> con_map = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String,SentimentType> full_sentiment_map = new ConcurrentHashMap<>();

    public static ConcurrentLinkedQueue<String> url_queue = new ConcurrentLinkedQueue<>();

    final static ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    final static Lock r = rwl.readLock();
    final static Lock w = rwl.writeLock();

    public static Crawl_Type crab_crawl_type;

    public static ThreadPoolExecutor exec = new ThreadPoolExecutor(numOfThreads/2, numOfThreads,
            1L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(CAPACITY),
            Executors.defaultThreadFactory(),
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());


    Crab() throws IOException {
        Utility.SerializeConMap(con_map);
        Utility.SerializeConMap(full_sentiment_map,"f_map_ser");
    }

    /*
        Re-entrant lock method for placing sentiment results into data structure.
     */
    public static void put(String val, SentimentType sentiment){
        w.lock();
        try{
            switch (crab_crawl_type) {
                case Sentiment -> con_map.putIfAbsent(val, sentiment);
            }
        }finally {
            w.unlock();
        }
    }

    public synchronized static void sentiment(final String URL){

        w.lock();
        try{

                Document doc = Jsoup.connect(URL).get();
                String toAnalyse = doc.title();

                put(URL,SentimentType.fromInt(SentimentAnalyser.analyse(toAnalyse)));

                System.out.println("Added and Visited: " + URL + "\n");

                Utility.writeURLSentimentResult(URL,SentimentType.fromInt(SentimentAnalyser.analyse(toAnalyse)));

                int full_result = analyseFullPage(URL);

                urlStack.safePush(URL); //add to the stack to look at the  links

                full_sentiment_map.put(URL,SentimentType.fromInt(full_result));

                System.out.println("Full analysis done on: " + URL + "\n");

                Utility.writeSentimentResult(URL,SentimentType.fromInt(full_result));

                visitedList.add(URL);

            }catch (Exception e){

            }
        finally {
            w.unlock();
        }
    }

    public static int analyseFullPage(String URL){

        int sentiment = 0;
        int headerSentiment = 0;
        int hSentiment = 0;

        try{
            Document doc = Jsoup.connect(URL).get();
            Elements content = doc.select("article");
            Elements contents = content.select("p");
            String heading = doc.head().text();
            hSentiment = SentimentAnalyser.analyse(heading);

            if (content.size() == 0) {
                contents = doc.select("p");

                for(Element e : contents){
                    sentiment += SentimentAnalyser.analyse(e.text());
                }
            } else {
                for (Element e : contents) {
                    sentiment += SentimentAnalyser.analyse(e.text());
                }
            }

            //Look at header tags for any further info, May get better accuracy
            Elements hTags = doc.select("h1, h2, h3, h4, h5, h6");

            for(Element h : hTags){
                headerSentiment += SentimentAnalyser.analyse(h.text());
            }

            if(headerSentiment > sentiment){
                if(headerSentiment < hSentiment){
                    return hSentiment;
                }
                return headerSentiment;
            }else if(headerSentiment < sentiment){
                if(headerSentiment < hSentiment){
                    return hSentiment;
                }
                return sentiment;
            }else{
                return sentiment;
            }

        }catch(Exception e){

        }
        return 0;
    }

    public static void CrabCrawl(Crawl_Type crawl) throws IOException, CsvException, ClassNotFoundException, InterruptedException {

        /* Read in the URL Seed set supplied into a stack */
        URLSeed.readIn(urlStack);

        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        if(urlStack.size()==0){
            throw new IllegalArgumentException("no URL Seed set supplied. \n");
        }

        switch(crawl){

            /* This option will do a single sentiment crawl based on what is within the URL seed set */
            case Sentiment:
                crab_crawl_type = Crawl_Type.Sentiment;

                con_map = Utility.DeserializeConMap();

           //     full_sentiment_map = Utility.DeserializeConMap("f_map_ser");

                while(urlStack.size() != 0){
                    exec.submit(new SentimentCrawlBasisRunnable(urlStack.safePop()));
                }

                exec.shutdown();

                break;
        }

        if((crawl == Crawl_Type.Sentiment) || (crawl == Crawl_Type.FullSentiment) || (crawl == Crawl_Type.keyWordSentiment)){
            exec.shutdown();
        }

        switch(crawl){
            case Sentiment: case AverageSentiment:
       //         Utility.writeToCSV(con_map);
       //         Utility.writeToCSV_FullAnalysis(full_sentiment_map);
                break;

        }
        Utility.SerializeConMap(con_map);
        Utility.SerializeConMap(full_sentiment_map,"f_map_ser");
    }

    /**
     * Enum for the Crawl Type used
     */
    public enum Crawl_Type {
        Sentiment,
        FullSentiment,
        keyWordSentiment,
        AverageSentiment
    }

}