package org.example.bench;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@BenchmarkMode(Mode.Throughput)               // хотим ops/sec
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@State(Scope.Benchmark)
public class IndexingBenchmark {

    @Param({"dataset"})            // путь к папке с .txt (можно поставить абсолютный)
    public String datasetDir;

    @Param({"true"})               // использовать compound files или нет
    public boolean useCompound;

    private Analyzer analyzer;
    private FieldType contentsType;

    @Setup(Level.Trial)
    public void setup() {
        analyzer = new StandardAnalyzer();
        contentsType = new FieldType(TextField.TYPE_NOT_STORED);
        contentsType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        contentsType.freeze();
    }

    @TearDown(Level.Trial)
    public void teardown() {
        // no-op
    }

    @Benchmark
    public int buildIndex() throws Exception {
        Path docsPath = Paths.get(datasetDir);
        if (!Files.isDirectory(docsPath)) {
            throw new IllegalStateException("Dataset folder not found: " + docsPath.toAbsolutePath());
        }

        Path indexPath = Files.createTempDirectory("lucene-index-bench-");
        int indexed = 0;

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setUseCompoundFile(useCompound);

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
            indexed = cnt.get();
        } finally {
            // подчистим temp-индекс
            MoreFiles.deleteRecursively(indexPath);
        }
        return indexed; // возвращаем кол-во документов (чтобы jmh не выкинул вызов)
    }
}
