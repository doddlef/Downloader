package test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParallelDownloader {
    public static String url;

    public static void main(String[] args) {
        String fileUrl = "https://lx-sycdn.kuwo.cn/dfb1c013ceeb74f19050b33d287d3086/66162ba6/resource/n2/84/37/3608362553.mp3";
        url = fileUrl;
        String savePath = "src/download/test.mp3";
        int numThreads = 5;

        try {
            downloadFileInParts(fileUrl, savePath, numThreads);
            System.out.println("File downloaded successfully!");
        } catch (IOException | InterruptedException e) {
            System.err.println("Error downloading file: " + e.getMessage());
        }
    }

    public static void downloadFileInParts(String fileUrl, String savePath, int numThreads) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .HEAD()
                .build();

        HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
        int statusCode = response.statusCode();

        if (statusCode != 200) {
            throw new IOException("Failed to download file. Status code: " + statusCode);
        }

        long fileSize = Long.parseLong(response.headers().firstValue("Content-Length").orElse("0"));
        long partSize = fileSize / numThreads;

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            long start = i * partSize;
            long end = (i == numThreads - 1) ? fileSize - 1 : start + partSize - 1;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    downloadRange(httpClient, httpRequest, savePath, start, end);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, executorService);
            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join();

        executorService.shutdown();
    }

    public static void downloadRange(HttpClient httpClient, HttpRequest httpRequest, String savePath, long start, long end) throws IOException, InterruptedException {
        HttpRequest requestWithRange = httpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        HttpResponse<byte[]> response = httpClient.send(requestWithRange, HttpResponse.BodyHandlers.ofByteArray());
        byte[] responseBody = response.body();

        if (response.statusCode() == 206 && responseBody != null) {
            Path outputPath = Paths.get(savePath);
            try (RandomAccessFile outputFile = new RandomAccessFile(outputPath.toFile(), "rw")) {
                outputFile.seek(start); // Seek to the start position of the part
                outputFile.write(responseBody); // Write the downloaded part directly to the output file
            }
        } else {
            throw new IOException("Failed to download file part. Status code: " + response.statusCode());
        }
    }
}
