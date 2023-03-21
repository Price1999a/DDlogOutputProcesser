package cn.edu.nju.stq;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 主要处理两个部分的内容 首先是时间信息，其次就是output数量的统计
 */
@Slf4j
public class Processor {

    final String[] echosStr = new String[]{"input start for iter ", "compute start for iter ", "compute end for iter "};

    /**
     * 对每一个文件 这里都会包含一个list 按照每三个为一组记录时间
     */
    Map<String, List<Long>> times = new ConcurrentHashMap<>();

    Map<String, StringBuilder> outCountLog = new ConcurrentHashMap<>();

    public void readInfoFromZip(String fileName) {
        try (ZipFile zipFile = new ZipFile(fileName)) {
            zipFile.stream()
                    .parallel()
                    .forEach(zipEntry -> {
                        try {
                            processZipEntry(zipFile, zipEntry);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 为了在全部处理完成之后将程序对应的统计信息进行生成
     * 先生成
     */
    public void printRes() {
        TreeMap<String, List<Long>> treeMapIncTime = new TreeMap<>(times);
//        TreeMap<String, StringBuilder> treeMapOutCount = new TreeMap<>(outCountLog);
        new TreeMap<>(outCountLog).forEach((analysisInfo, strB) -> {
            log.info("print output line count res of {}", analysisInfo);
            System.out.println(strB.toString());

//            System.out.println();
        });

        treeMapIncTime.forEach((analysisInfo, timeList) -> {
            log.info("print res of {}", analysisInfo);
            if (timeList == null || timeList.size() < 3) {
                log.warn("{} did not complete the first calculation.", analysisInfo);
            } else {
                // 在这里进行处理
                // 首先处理init内容 之后处理inc部分
                // 0,1,2, 3,4,5, 6,7,8
                Long time1 = timeList.get(0), time2 = timeList.get(2);
                // 这里要从ns转换到s
                System.out.println("First time: " + ns2sec(time2 - time1));
                int totalTime = timeList.size() / 3 - 1;                    // 总增量的次数
                long totalIncTime = 0;
                for (int i = 0; i < totalTime; i++) {
                    time1 = timeList.get(i * 3 + 3);
                    time2 = timeList.get(i * 3 + 3 + 2);
                    totalIncTime += time2 - time1;
                    System.out.println("inc" + (i + 1) + " time: " + ns2sec(time2 - time1));
                }
                System.out.println("timeList.size(): " + timeList.size());
                System.out.println("Total inc count: " + totalTime + " avg time: " + ns2sec(((double) totalIncTime) / totalTime));
                System.out.println();
            }
        });
    }

    private double ns2sec(double ns) {
        return Math.round(ns / 1_000_000) / (double) 1_000;
    }

    /**
     *
     */
    private void processZipEntry(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
        // 首先实现一个打印信息的版本
        InputStream inputStream = zipFile.getInputStream(zipEntry);
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        log.info("zip file name: {}", zipEntry);
        String analysisInfo = resolveAnalysisInfoFromFilename(zipEntry.toString());
        if ("".equals(analysisInfo)) return;
        log.info("analysis info: {}", analysisInfo);
        times.put(analysisInfo, new ArrayList<>());
        outCountLog.put(analysisInfo, new StringBuilder());
        //  让我们首先在这里实现一个时间统计器
        //  这里的关键就在于我们只需要简单的处理TimeStamp附近的内容即可
        //
        int posEcho = 0, iter = 1;
        String line;
        String tmpTag = echosStr[posEcho] + iter;
        Map<String, Integer> outRelCount = new TreeMap<>();
        String currentRelName = "";

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.equals(tmpTag)) {
                if (posEcho == 2) {
                    posEcho = 0;
                    iter++;
                    //  在这里将统计数据生成（因为这里是某一次评估的结束点）
                    outCountLog.get(analysisInfo).append(generateCountInfo(outRelCount, iter)).append('\n');
                } else posEcho++;
                tmpTag = echosStr[posEcho] + iter;
                line = br.readLine();
                // 在有了echo的情况下 没有理由认为这里还存在null的可能 但是还是放一个在这里（顺带一提 这里null程序读取部分就直接结束了）
                if (line == null) continue;
                if (!line.trim().startsWith("Timestamp: ")) {
                    throw new RuntimeException("错误的时间戳命令输出格式： " + line);
                }
//                log.info(line.substring(11));
                times.get(analysisInfo).add(Long.parseLong(line.trim().substring(11)));
            } else {
                if (line.endsWith(" +1")) {
                    Integer i = outRelCount.computeIfPresent(currentRelName, (k, v) -> v + 1);
                    if (i == null) log.warn("this line from file {} generate error: {}", zipEntry, line);
                } else if (line.endsWith(" -1")) {
                    Integer i = outRelCount.computeIfPresent(currentRelName, (k, v) -> v - 1);
                    if (i == null) log.warn("this line from file {} generate error: {}", zipEntry, line);
                } else if (line.endsWith(":")) {
                    currentRelName = line.substring(0, line.length() - 1);
                    outRelCount.putIfAbsent(currentRelName, 0);
                }
            }
        }

        log.info("zip file done name: {}", zipEntry);
    }

    private StringBuilder generateCountInfo(Map<String, Integer> counts, int iter) {
        StringBuilder sb = new StringBuilder();
        sb.append("iter ").append(iter - 1).append('\n');
        for (String analysisInfo : counts.keySet()) {
            sb.append(analysisInfo).append(":\t").append(counts.get(analysisInfo)).append("\n");
        }
        return sb;
    }

    /**
     * 将输出文件中包含的命令转化为可读性更好的形式
     * 举例：
     * jedis_analysis1.jedis.txt -> jedis-analysis1 (NOTE: 从用户的角度说，我不认为他们最终需要知道具体分析规则中的不同)
     */
    private String resolveAnalysisInfoFromFilename(String filename) {
        if (filename.endsWith(".txt")) {
            // 需要过滤掉那些不是txt结尾的文件
            return filename.split("\\.")[1] + '-' + filename.split("\\.")[0].substring(filename.indexOf('_') + 1);
        }
        return "";
    }
}
