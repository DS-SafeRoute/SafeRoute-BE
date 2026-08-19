package com.saferoute.domain.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(CctvCodeAllocator.class)
class CctvCodeAllocatorIntegrationTest {

    @Autowired
    private CctvCodeAllocator allocator;

    @Test
    void 순차_및_동시_채번에서도_코드가_중복되지_않는다() throws Exception {
        assertThat(allocator.allocate()).isEqualTo("CCTV_001");
        assertThat(allocator.allocate()).isEqualTo("CCTV_002");

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<String>) allocator::allocate)
                    .toList();
            List<String> codes = executor.invokeAll(tasks).stream()
                    .map(CctvCodeAllocatorIntegrationTest::resultOf)
                    .toList();

            assertThat(new HashSet<>(codes)).hasSize(codes.size());
            assertThat(codes).allMatch(code -> code.matches("CCTV_\\d{3,}"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String resultOf(Future<String> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
