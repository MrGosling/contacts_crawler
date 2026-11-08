package com.example.contactscrawler.crawler.engine;

import com.example.contactscrawler.crawler.model.FetchResult;
import com.example.contactscrawler.extract.ContactExtractionService;
import com.example.contactscrawler.extract.ExtractionResult;
import com.example.contactscrawler.model.Contact;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Параллельное извлечение контактов из набора страниц в кастомном ForkJoinPool (commonPool не используется).
 * ExtractTask extends RecursiveTask: делит список пополам до THRESHOLD страниц; порядок результатов совпадает
 * с последовательной обработкой.
 */
@Service
public class ParallelExtractionService {

    static final int THRESHOLD = 4;

    private final ContactExtractionService extraction;
    private final ForkJoinPool pool;

    public ParallelExtractionService(ContactExtractionService extraction, ForkJoinPool crawlerForkJoinPool) {
        this.extraction = extraction;
        this.pool = crawlerForkJoinPool;
    }

    public List<Contact> extractAll(List<FetchResult> pages) {
        return extractResults(pages).stream().flatMap(r -> r.contacts().stream()).toList();
    }

    /** Результат на каждую страницу (в том же порядке); не-OK страницы → пустой результат. */
    public List<ExtractionResult> extractResults(List<FetchResult> pages) {
        if (pages.isEmpty()) {
            return List.of();
        }
        return pool.invoke(new ExtractTask(pages, 0, pages.size()));
    }

    /** Последовательная версия (эталон для тестов). */
    public List<Contact> extractAllSequential(List<FetchResult> pages) {
        return pages.stream().map(this::extractOne).flatMap(r -> r.contacts().stream()).toList();
    }

    private ExtractionResult extractOne(FetchResult page) {
        if (page == null || !page.isOk()) {
            return ExtractionResult.EMPTY;
        }
        return extraction.extract(Jsoup.parse(page.body(), page.finalUrl().toString()), page.finalUrl());
    }

    private final class ExtractTask extends RecursiveTask<List<ExtractionResult>> {
        private final List<FetchResult> pages;
        private final int from;
        private final int to;

        ExtractTask(List<FetchResult> pages, int from, int to) {
            this.pages = pages;
            this.from = from;
            this.to = to;
        }

        @Override
        protected List<ExtractionResult> compute() {
            if (to - from <= THRESHOLD) {
                List<ExtractionResult> out = new ArrayList<>(to - from);
                for (int i = from; i < to; i++) {
                    out.add(extractOne(pages.get(i)));
                }
                return out;
            }
            int mid = (from + to) >>> 1;
            ExtractTask left = new ExtractTask(pages, from, mid);
            ExtractTask right = new ExtractTask(pages, mid, to);
            left.fork();
            List<ExtractionResult> rightResult = right.compute();
            List<ExtractionResult> out = new ArrayList<>(left.join());
            out.addAll(rightResult);
            return out;
        }
    }
}
