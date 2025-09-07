package org.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SearchCLI {

    // Usage (IDE Program arguments):
    //   [0] = indexDir (opt, default "index")
    //   [1] = field    (opt, default "contents")
    //   [2] = --topK=N (opt, default 10)
    // затем интерактивный ввод запроса в консоли (пустая строка — выход)
    public static void main(String[] args) throws Exception {
        Path indexPath = Paths.get(args.length >= 1 ? args[0] : "index");
        String field   = args.length >= 2 ? args[1] : "contents";
        int topK       = 10;
        for (String a : args) {
            if (a.startsWith("--topK=")) {
                topK = Integer.parseInt(a.substring("--topK=".length()));
            }
        }

        Analyzer analyzer = new StandardAnalyzer();
        try (FSDirectory dir = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser qp = new QueryParser(field, analyzer);

            UnifiedHighlighter uh = new UnifiedHighlighter(searcher, analyzer);
            uh.setMaxLength(10000);

            System.out.println("Search ready. Index=" + indexPath.toAbsolutePath());
            System.out.println("Field=" + field + ", topK=" + topK);
            System.out.println("Введите запрос (пустая строка = выход):");

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.print("> ");
                String line = br.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) break;

                Query q;
                try {
                    q = qp.parse(line);
                } catch (Exception e) {
                    System.out.println("Ошибка парсинга: " + e.getMessage());
                    continue;
                }

                TopDocs top = searcher.search(q, topK);
                if (top.scoreDocs.length == 0) {
                    System.out.println("(нет результатов)");
                    continue;
                }

                String[] fragments = uh.highlight(field, q, top);
                for (int i = 0; i < top.scoreDocs.length; i++) {
                    ScoreDoc sd = top.scoreDocs[i];
                    Document d = searcher.doc(sd.doc);

                    String path = d.get("path");
                    String snippet = (fragments != null && fragments.length > i && fragments[i] != null)
                            ? fragments[i]
                            : "(сниппет недоступен)";

                    System.out.printf("#%d score=%.4f%n", i + 1, sd.score);
                    System.out.println("path: " + path);
                    System.out.println(snippet.replace('\n', ' '));
                    System.out.println();
                }
            }
        }
    }
}
