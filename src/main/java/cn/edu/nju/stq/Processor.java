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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        new TreeMap<>(outCountLog).forEach((analysisInfo, strB) -> {
            log.info("print output line count res of {}", analysisInfo);
            System.out.println(strB.toString());
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
        int posEcho = 0, iter = 1;
        String line;
        String tmpTag = echosStr[posEcho] + iter;
        Map<String, Integer> outRelCount = new TreeMap<>();
        Map<String, Integer> CGECount = new TreeMap<>();
        Map<String, Integer> VPTCount = new TreeMap<>();
        Map<String, Integer> IFPTCount = new TreeMap<>();
        String pattern1 = ".__invocation\\s*=\\s*\"(.*?)\".*?\\.__method\\s*=\\s*\"(.*?)\"";
        String pattern2 = ".__value\\s*=\\s*\"(.*?)\".*?\\.var\\s*=\\s*\"(.*?)\"";
        String pattern3 = ".__value\\s*=\\s*\"(.*?)\".*?\\.__sig\\s*=\\s*\"(.*?)\".*?\\.__basevalue\\s*=\\s*\"(.*?)\"";

        Pattern regexCGEPattern = Pattern.compile(pattern1);
        Pattern regexVPTPattern = Pattern.compile(pattern2);
        Pattern regexIFPTPattern = Pattern.compile(pattern3);

        // 我们需要对这些部分进行处理
        // RmainAnalysis_CallGraphEdge:
        // RmainAnalysis_CallGraphEdge{.__callerCtx = "<<immutable-context>>", .__invocation = "<main-thread-init>/0", .__calleeCtx = "<<immutable-context>>", .__method = "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>"}: +1
        // RmainAnalysis_VarPointsTo:
        // RmainAnalysis_VarPointsTo{.__hctx = "<<immutable-hcontext>>", .__value = "<sun.misc.Perf: java.nio.ByteBuffer createLong(java.lang.String,int,int,long)>/new java.nio.DirectByteBuffer/0", .__ctx = "<<immutable-context>>", .var = "<java.nio.ByteBufferAsLongBufferB: java.nio.LongBuffer put(int,long)>/$stack4"}: +1
        // RmainAnalysis_InstanceFieldPointsTo:
        // RmainAnalysis_InstanceFieldPointsTo{.__hctx = "<<immutable-hcontext>>", .__value = "<sun.misc.Perf: java.nio.ByteBuffer createLong(java.lang.String,int,int,long)>/new java.nio.DirectByteBuffer/0", .__sig = "<java.nio.DirectLongBufferU: java.lang.Object att>", .__basehctx = "<<immutable-hcontext>>", .__basevalue = "<java.nio.DirectByteBuffer: java.nio.LongBuffer asLongBuffer()>/new java.nio.DirectLongBufferU/0"}: +1
        String currentRelName = "";

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.equals(tmpTag)) {
                if (posEcho == 2) {
                    posEcho = 0;
                    iter++;
                    //  在这里将统计数据生成（因为这里是某一次评估的结束点）
                    outCountLog.get(analysisInfo).append(generateCountInfo(outRelCount, iter))
                            .append("CGECount:\t").append(generateCountNumberWithoutContext(CGECount, iter))
                            .append("VPTCount:\t").append(generateCountNumberWithoutContext(VPTCount, iter))
                            .append("IFPTCount:\t").append(generateCountNumberWithoutContext(IFPTCount, iter))
                            .append('\n');
                } else posEcho++;
                tmpTag = echosStr[posEcho] + iter;
                line = br.readLine();
                // 在有了echo的情况下 没有理由认为这里还存在null的可能 但是还是放一个在这里（顺带一提 这里null程序读取部分就直接结束了）
                if (line == null) continue;
                if (!line.trim().startsWith("Timestamp: ")) {
                    throw new RuntimeException("错误的时间戳命令输出格式： " + line);
                }
                times.get(analysisInfo).add(Long.parseLong(line.trim().substring(11)));
            } else {
                if (line.endsWith(" +1")) {
                    Integer i = outRelCount.computeIfPresent(currentRelName, (k, v) -> v + 1);
                    outRelCount.computeIfPresent(currentRelName + "_add", (k, v) -> v + 1);
                    if (i == null) log.warn("this line from file {} generate error: {}", zipEntry, line);
                    //  以下是统计去除上下文信息后的条数
                    if (line.startsWith("RmainAnalysis_CallGraphEdge{")) {
                        Matcher matcher = regexCGEPattern.matcher(line);
                        if (matcher.find()) {
                            CGECount.compute(matcher.group(1) + "#" + matcher.group(2),
                                    (k, v) -> v == null ? 1 : v + 1);
                        }
                    } else if (line.startsWith("RmainAnalysis_VarPointsTo{")) {
                        Matcher matcher = regexVPTPattern.matcher(line);
                        if (matcher.find()) {
                            VPTCount.compute(matcher.group(1) + "#" + matcher.group(2),
                                    (k, v) -> v == null ? 1 : v + 1);
                        }
                    } else if (line.startsWith("RmainAnalysis_InstanceFieldPointsTo{")) {
                        Matcher matcher = regexIFPTPattern.matcher(line);
                        if (matcher.find()) {
                            IFPTCount.compute(matcher.group(1) + "#" + matcher.group(2) + "#" + matcher.group(3),
                                    (k, v) -> v == null ? 1 : v + 1);
                        }
                    }
                } else if (line.endsWith(" -1")) {
                    Integer i = outRelCount.computeIfPresent(currentRelName, (k, v) -> v - 1);
                    outRelCount.computeIfPresent(currentRelName + "_del", (k, v) -> v + 1);
                    if (i == null) log.warn("this line from file {} generate error: {}", zipEntry, line);
                    //  以下是统计去除上下文信息后的条数
                    if (line.startsWith("RmainAnalysis_CallGraphEdge{")) {
                        Matcher matcher = regexCGEPattern.matcher(line);
                        if (matcher.find()) {
                            CGECount.compute(matcher.group(1) + "#" + matcher.group(2),
                                    (k, v) -> v == null ? 0 : v - 1);
                        }
                    } else if (line.startsWith("RmainAnalysis_VarPointsTo{")) {
                        Matcher matcher = regexVPTPattern.matcher(line);
                        if (matcher.find()) {
                            VPTCount.compute(matcher.group(1) + "#" + matcher.group(2),
                                    (k, v) -> v == null ? 0 : v - 1);
                        }
                    } else if (line.startsWith("RmainAnalysis_InstanceFieldPointsTo{")) {
                        Matcher matcher = regexIFPTPattern.matcher(line);
                        if (matcher.find()) {
                            IFPTCount.compute(matcher.group(1) + "#" + matcher.group(2) + "#" + matcher.group(3),
                                    (k, v) -> v == null ? 0 : v - 1);
                        }
                    }
                } else if (line.endsWith(":")) {
                    currentRelName = line.substring(0, line.length() - 1);
                    outRelCount.putIfAbsent(currentRelName, 0);
                    outRelCount.putIfAbsent(currentRelName + "_add", 0);
                    outRelCount.putIfAbsent(currentRelName + "_del", 0);
                    // 增加一些更细节的输出
                }
            }
        }

        log.info("zip file done name: {}", zipEntry);
    }

    private StringBuilder generateCountNumberWithoutContext(Map<String, Integer> counts, int iter) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String withoutContext : counts.keySet()) {
            if (counts.get(withoutContext) > 0) count++;
        }
        sb.append("iter ").append(iter - 1).append('\t').append(count).append('\n');
        return sb;
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

    /**
     * 简单测试正则代码
     *
     * @author shentianqi.stq
     * @version 2023/09/05
     */
    public static void main(String[] args) {
        // RmainAnalysis_CallGraphEdge:
        // RmainAnalysis_CallGraphEdge{
        // .__callerCtx = "<<immutable-context>>",
        // .__invocation = "<main-thread-init>/0",
        // .__calleeCtx = "<<immutable-context>>",
        // .__method = "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>"}: +1

        // RmainAnalysis_VarPointsTo:
        // RmainAnalysis_VarPointsTo{
        // .__hctx = "<<immutable-hcontext>>",
        // .__value = "<sun.misc.Perf: java.nio.ByteBuffer createLong(java.lang.String,int,int,long)>/new java.nio.DirectByteBuffer/0",
        // .__ctx = "<<immutable-context>>",
        // .var = "<java.nio.ByteBufferAsLongBufferB: java.nio.LongBuffer put(int,long)>/$stack4"}: +1
        // RmainAnalysis_InstanceFieldPointsTo:
        // RmainAnalysis_InstanceFieldPointsTo{.__hctx = "<<immutable-hcontext>>",
        // .__value = "<sun.misc.Perf: java.nio.ByteBuffer createLong(java.lang.String,int,int,long)>/new java.nio.DirectByteBuffer/0",
        // .__sig = "<java.nio.DirectLongBufferU: java.lang.Object att>",
        // .__basehctx = "<<immutable-hcontext>>",
        // .__basevalue = "<java.nio.DirectByteBuffer: java.nio.LongBuffer asLongBuffer()>/new java.nio.DirectLongBufferU/0"}: +1
        String str1 = "RmainAnalysis_CallGraphEdge{.__callerCtx = \"<<immutable-context>>\", .__invocation = \"<main-thread-init>/0\", .__calleeCtx = \"<<immutable-context>>\", .__method = \"<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>\"}: +1";
        String str2 = "RmainAnalysis_VarPointsTo{.__hctx = \"<<immutable-hcontext>>\", .__value = \"<sun.misc.Perf: java.nio.ByteBuffer createLong(java.lang.String,int,int,long)>/new java.nio.DirectByteBuffer/0\", .__ctx = \"<<immutable-context>>\", .var = \"<java.nio.ByteBufferAsLongBufferB: java.nio.LongBuffer put(int,long)>/$stack4\"}: +1";
        String str3 = "RmainAnalysis_InstanceFieldPointsTo{.__hctx = \"<<immutable-hcontext>>\", .__value = \"<sun.misc.Perf: java.nio.ByteBuffer createLong(java.lang.String,int,int,long)>/new java.nio.DirectByteBuffer/0\", .__sig = \"<java.nio.DirectLongBufferU: java.lang.Object att>\", .__basehctx = \"<<immutable-hcontext>>\", .__basevalue = \"<java.nio.DirectByteBuffer: java.nio.LongBuffer asLongBuffer()>/new java.nio.DirectLongBufferU/0\"}: +1";
        String pattern1 = ".__invocation\\s*=\\s*\"(.*?)\".*?\\.__method\\s*=\\s*\"(.*?)\"";
        String pattern2 = ".__value\\s*=\\s*\"(.*?)\".*?\\.var\\s*=\\s*\"(.*?)\"";
        String pattern3 = ".__value\\s*=\\s*\"(.*?)\".*?\\.__sig\\s*=\\s*\"(.*?)\".*?\\.__basevalue\\s*=\\s*\"(.*?)\"";

        Pattern regexPattern1 = Pattern.compile(pattern1);
        Matcher matcher = regexPattern1.matcher(str1);
        System.out.println();
    }
}
