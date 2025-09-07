package org.example.bench;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@BenchmarkMode(Mode.AverageTime)                // хотим среднее время
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
@State(Scope.Benchmark)
public class SearchLatencyBenchmark {

    @Param({"dataset"})             // папка с .txt
    public String datasetDir;

    @Param({"true"})                // compound files
    public boolean useCompound;

    @Param({"contents"})
    public String field;

    @Param({"queries.txt"})         // файл с запросами (по одному в строке); если нет — используются дефолтные
    public String queriesFile;

    @Param({"10"})                  // лимит результатов
    public int topK;

    private Analyzer analyzer;
    private Path indexPath;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    private QueryParser parser;
    private Queries queries;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        analyzer = new StandardAnalyzer();
        parser = new QueryParser(field, analyzer);
        queries = Queries.fromFileOrDefault(
                queriesFile == null ? null : Paths.get(queriesFile));

        // строим постоянный индекс один раз
        indexPath = Files.createTempDirectory("lucene-search-bench-");
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setUseCompoundFile(useCompound);

        FieldType contentsType = new FieldType(TextField.TYPE_NOT_STORED);
        contentsType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        contentsType.freeze();

        Path docsPath = Paths.get(datasetDir);
        if (!Files.isDirectory(docsPath)) {
            throw new IllegalStateException("Dataset folder not found: " + docsPath.toAbsolutePath());
        }

        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, iwc)) {

            AtomicInteger cnt = new AtomicInteger();
            try (Stream<Path> paths = Files.walk(docsPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p, StandardCharsets.UTF_8);
                                Document doc = new Document();
                                doc.add(new Field("contents", content, contentsType));
                                doc.add(new StringField("path", p.toAbsolutePath().toString(), Field.Store.YES));
                                writer.addDocument(doc);
                                cnt.incrementAndGet();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            writer.commit();
        }

        Directory dir = FSDirectory.open(indexPath);
        reader = DirectoryReader.open(dir);
        searcher = new IndexSearcher(reader);
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (reader != null) reader.close();
        MoreFiles.deleteRecursively(indexPath);
    }

    @Benchmark
    public int searchOnce() throws Exception {
        String qText = queries.next();
        Query q = parser.parse(qText);
        TopDocs td = searcher.search(q, topK);
        int sum = 0;
        for (ScoreDoc sd : td.scoreDocs) {
            sum += sd.doc; // потребляем, чтобы не выкинули оптимизацией
        }
        return sum;
    }
}
