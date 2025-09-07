package org.example;

import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.search.DocIdSetIterator; // фикс

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * Инспектор индекса. Печатает файлы сегментов и постинги для заданного термина.
 *
 * Запуск в IDE:
 *   Run 'IndexInspector.main()' с Program arguments:
 *     index [field] [term]
 *   где:
 *     index  — путь к папке индекса (по умолчанию "index")
 *     field  — поле (по умолчанию "contents")
 *     term   — термин (опционально; если не задан — просто статистика)
 */
public class IndexInspector {

    public static void main(String[] args) throws Exception {
        Path indexPath = Paths.get(args.length >= 1 ? args[0] : "index");
        String field = args.length >= 2 ? args[1] : "contents";
        String term = args.length >= 3 ? args[2] : null;

        if (!Files.isDirectory(indexPath)) {
            System.err.println("Индекс не найден по пути: " + indexPath.toAbsolutePath());
            System.err.println("Сначала запусти IndexBuilder, чтобы создать индекс.");
            return;
        }

        try (FSDirectory dir = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            System.out.println("=== FILES IN INDEX DIR ===");
            listFiles(indexPath);

            System.out.println("\n=== SEGMENT STATS ===");
            for (LeafReaderContext leafCtx : reader.leaves()) {
                LeafReader leaf = leafCtx.reader();
                String segName = (leaf instanceof SegmentReader)
                        ? ((SegmentReader) leaf).getSegmentName()
                        : ("leaf@" + leafCtx.ord);
                System.out.printf("Segment: %s, maxDoc=%d, numDocs=%d%n",
                        segName, leaf.maxDoc(), leaf.numDocs());

                FieldInfos finfos = leaf.getFieldInfos(); // экземплярный метод
                for (FieldInfo fi : finfos) {
                    Terms t = leaf.terms(fi.name);
                    if (t != null) {
                        System.out.printf("  Field: %s, hasFreqs=%s, hasPositions=%s, hasOffsets=%s, docCount=%d%n",
                                fi.name, t.hasFreqs(), t.hasPositions(), t.hasOffsets(), t.getDocCount());
                    }
                }
            }

            if (term != null) {
                System.out.println("\n=== POSTINGS FOR TERM ===");
                showPostings(reader, field, term);
            } else {
                System.out.println("\n(термин не задан; укажи [field] [term], чтобы вывести постинги)");
            }
        }
    }

    private static void listFiles(Path indexPath) throws IOException {
        Map<String, Long> byExt = new TreeMap<>();
        long total = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(indexPath)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    long sz = Files.size(p);
                    total += sz;
                    String name = p.getFileName().toString();
                    String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "(noext)";
                    byExt.merge(ext, sz, Long::sum);
                    System.out.printf("%8.2f MB  %s%n", sz / (1024.0 * 1024.0), name);
                }
            }
        }
        System.out.println("--- Size by extension ---");
        byExt.forEach((ext, sz) ->
                System.out.printf("%-6s %8.2f MB%n", ext, sz / (1024.0 * 1024.0)));
        System.out.printf("TOTAL  %8.2f MB%n", total / (1024.0 * 1024.0));
    }

    private static void showPostings(DirectoryReader reader, String field, String termText) throws IOException {
        BytesRef term = new BytesRef(termText);
        for (LeafReaderContext leafCtx : reader.leaves()) {
            LeafReader leaf = leafCtx.reader();
            Terms terms = leaf.terms(field);
            if (terms == null) continue;

            TermsEnum te = terms.iterator();
            if (!te.seekExact(term)) continue;

            int df = te.docFreq();
            long ttf = terms.hasFreqs() ? te.totalTermFreq() : -1;
            System.out.printf("Leaf ord=%d: df=%d, ttf=%s%n",
                    leafCtx.ord, df, (ttf >= 0 ? Long.toString(ttf) : "n/a"));

            int flags = PostingsEnum.NONE;
            if (terms.hasFreqs()) flags |= PostingsEnum.FREQS;
            if (terms.hasPositions()) flags |= PostingsEnum.POSITIONS | PostingsEnum.OFFSETS | PostingsEnum.PAYLOADS;

            PostingsEnum pe = te.postings(null, flags);
            if (pe == null) continue;

            while (pe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                int docID = pe.docID();
                int freq = terms.hasFreqs() ? pe.freq() : 1;
                System.out.printf("  docID=%d, freq=%d%n", docID, freq);

                if (terms.hasPositions()) {
                    for (int i = 0; i < freq; i++) {
                        int pos = pe.nextPosition();
                        int start = pe.startOffset();
                        int end = pe.endOffset();
                        System.out.printf("    pos=%d, offsets=[%d,%d]%n", pos, start, end);
                    }
                }
            }
        }
    }
}
