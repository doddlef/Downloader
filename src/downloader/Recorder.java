package downloader;

public class Recorder implements Runnable{
    private long totalSize;
    private int numThreads;
    private volatile long current = 0;
    private long previous = 0;
    private volatile int threadFinished = 0;
    private int time = 0;

    public Recorder(long totalSize, int numThreads) {
        this.totalSize = totalSize;
        this.numThreads = numThreads;
    }

    public void updateSize(long size){
        this.current += size;
    }

    public void updateFinished(int i){
        this.threadFinished += i;
    }


    @Override
    public void run() {
        time+=1;
        double step = (current - previous) / 1024d / 1024d;
        System.out.print('\r');
        System.out.print(String.format("speed: %.2f MB/s, total: %.2f/%.2f MB", step, current/1024d/1024d, totalSize/1024d/1024d));
        System.out.print(String.format(" finished thread %d/%d", threadFinished, numThreads));
        previous = current;
    }

    public void finished(){
        System.out.print('\r');
        System.out.print(String.format("download finished, size: %.2f MB, time: %d s", totalSize/1024d/1024d, time));
        System.out.println(String.format(", average speed: %.2f MB/s.", totalSize/1024d/1024d/time));
    }
}
