package org.example.bench;

import org.openjdk.jmh.runner.*;
import org.openjdk.jmh.runner.options.*;

public class JmhRunner {
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(IndexingBenchmark.class.getSimpleName())
                .include(SearchLatencyBenchmark.class.getSimpleName())
                .detectJvmArgs()  // подхватит -Xmx из IDE, если задашь
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
