package downloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MyDownloader {
    private String url;
    private String savePath;
    private int numThreads;
    private Recorder recorder;

    public MyDownloader(String url, int numThreads) {
        this.url = url;
        int index = url.lastIndexOf('/');
        this.savePath = "src/download/" + url.substring(index+1);
        this.numThreads = numThreads;
    }

    public MyDownloader(String url){
        this(url,  5);
    }

    public void download() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .headers("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15")
                .HEAD()
                .build();

        Instant startTime = Instant.now();

        HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());

        int statusCode = response.statusCode();

        System.out.println("cost " + Duration.between(startTime, Instant.now()).toSeconds() +"s to connect");

        if(statusCode != 200){
            throw new IOException("Failed ot download file. Status code: " + statusCode);
        }

        long fileSize = Long.parseLong(response.headers().firstValue("Content-Length").orElse("0"));
        long partSize = fileSize / numThreads;

        this.recorder = new Recorder(fileSize, numThreads);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1);
        service.scheduleAtFixedRate(recorder, 0, 1, TimeUnit.SECONDS);
        System.out.println("start downloading");

        for(int i = 0; i < numThreads; i++){
            long start = i * partSize;
            long end = (i == numThreads - 1) ? fileSize - 1 : start + partSize - 1;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    downloadRange(httpClient, httpRequest, start, end);
                } catch (IOException | InterruptedException e){
                    e.printStackTrace();
                }
            }, executorService);
            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join();

        recorder.finished();

        executorService.shutdown();
        service.shutdown();
    }

    private void downloadRange(HttpClient httpClient, HttpRequest httpRequest, long start, long end) throws IOException, InterruptedException {
        HttpRequest request = httpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 206) {
            Path outputPath = Paths.get(savePath);
            try (RandomAccessFile outputFile = new RandomAccessFile(outputPath.toFile(), "rw");
                 InputStream inputStream = response.body()) {
                 outputFile.seek(start); // Seek to the start position of the part

                 byte[] buffer = new byte[8192];
                 int bytesRead;
                 while ((bytesRead = inputStream.read(buffer)) != -1) {
                     outputFile.write(buffer, 0, bytesRead); // Write the downloaded part directly to the output file
                     this.recorder.updateSize(bytesRead);
                 }
                 this.recorder.updateFinished(1);
            }
        } else  {
            throw new IOException("Failed to download file part. Status code: " + response.statusCode());
        }

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        MyDownloader downloader = new MyDownloader("http://testfiles.hostnetworks.com.au/300MB.iso", 12);
        downloader.download();
    }
}
