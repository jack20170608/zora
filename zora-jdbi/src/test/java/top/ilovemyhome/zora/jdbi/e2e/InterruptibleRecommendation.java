package top.ilovemyhome.zora.jdbi.e2e;

public class InterruptibleRecommendation {
    static String loadRecommendations() throws InterruptedException {
        for (int page = 1; page <= 10; page++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("推荐查询已取消");
            }
            Thread.sleep(100); // 模拟可中断的网络等待
            System.out.println("已读取第 " + page + " 页");
        }
        return "推荐列表";
    }

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            try {
                System.out.println(loadRecommendations());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(e.getMessage());
                System.out.println("清理本地状态后结束任务");
            }
        }, "recommendation-worker");

        worker.start();
        Thread.sleep(180);
        worker.interrupt();
        worker.join();
    }
}
