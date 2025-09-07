package org.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class IndexBuilder {

    // Usage (IDE Program arguments):
    //   [0]=docsDir (opt, default "dataset")
    //   [1]=indexDir (opt, default "index")
    //   [2]=--cfs=false|true (opt, default true)
    public static void main(String[] args) throws IOException {
        Path docsPath  = Paths.get(args.length >= 1 ? args[0] : "dataset");
        Path indexPath = Paths.get(args.length >= 2 ? args[1] : "index");

        boolean useCompound = true;
        for (String a : args) {
            if (a.startsWith("--cfs=")) {
                useCompound = Boolean.parseBoolean(a.substring("--cfs=".length()).trim());
            }
        }

        System.out.println("Working dir: " + Paths.get("").toAbsolutePath());
        System.out.println("Docs dir   : " + docsPath.toAbsolutePath());
        System.out.println("Index dir  : " + indexPath.toAbsolutePath());
        System.out.println("UseCompound: " + useCompound);

        if (!Files.isDirectory(docsPath)) {
            System.err.println("Папка с документами не найдена: " + docsPath.toAbsolutePath());
            return;
        }
        Files.createDirectories(indexPath);

        try (Stream<Path> s = Files.list(docsPath)) {
            long entries = s.count();
            System.out.println("В каталоге найдено записей (файлы/папки): " + entries);
        }

        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setUseCompoundFile(useCompound);

        // Тип поля с позициями и ОФФСЕТАМИ (для подсветки сниппетов)
        FieldType contentsType = new FieldType(TextField.TYPE_STORED); // Store.YES
        contentsType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        contentsType.freeze();

        AtomicInteger totalIndexed = new AtomicInteger();

        try (Directory dir = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(dir, iwc)) {

            try (Stream<Path> paths = Files.walk(docsPath)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p, StandardCharsets.UTF_8);
                                Document doc = new Document();
                                // основное поле для поиска и подсветки
                                doc.add(new Field("contents", content, contentsType));
                                // дополнительное поле (можно использовать как источник сниппета)
                                doc.add(new StoredField("snippet_source", content));
                                // путь до файла
                                doc.add(new StringField("path", p.toAbsolutePath().toString(), Field.Store.YES));
                                writer.addDocument(doc);
                                System.out.println("Indexed: " + p.getFileName());
                                totalIndexed.incrementAndGet();
                            } catch (IOException e) {
                                System.err.println("Skip " + p + " due to IO error: " + e.getMessage());
                            }
                        });
            }
            writer.commit();
        }

        System.out.printf("Проиндексировано документов: %d%n", totalIndexed.get());
        System.out.println("Готово. Индекс: " + indexPath.toAbsolutePath());
    }
}
