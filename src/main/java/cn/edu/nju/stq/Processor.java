package cn.edu.nju.stq;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 主要处理两个部分的内容 首先是时间信息，其次就是output数量的统计
 */
@Slf4j
public class Processor {

    final String[] echosStr = new String[]{"input start for iter ", "compute start for iter ", "compute end for iter "};

    Map<String, List<Integer>> times = new ConcurrentHashMap<>();

    public void readInfoFromZip(String fileName) {
        try (ZipFile zipFile = new ZipFile(fileName)) {
            zipFile.stream().parallel()
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
     */
    public void printRes() {
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
        // TODO 让我们首先在这里实现一个时间统计器
        //  这里的关键就在于我们只需要简单的处理TimeStamp附近的内容即可
        //
        int posEcho = 0, iter = 1;
        String line;
        String tmpTag = echosStr[posEcho] + iter;
        while ((line = br.readLine()) != null) {
            if (line.equals(tmpTag)) {
                line = br.readLine();
                // 在有了echo的情况下 没有理由认为这里还存在
                if (line ==null) continue;
            }
        }
//            log.info("get line: {}", line);
    }

    /**
     * 将输出文件中包含的命令转化为可读性更好的形式
     * 举例：
     * jedis_analysis1.jedis.txt -> jedis-analysis1 (NOTE: 从用户的角度说，我不认为他们最终需要知道具体分析规则中的不同)
     */
    private String resolveAnalysisInfoFromFilename(String filename) {
        if (filename.endsWith(".txt")) {
            // 需要过滤掉那些不是txt结尾的文件
            StringBuilder sb = new StringBuilder();
            sb.append(filename.split("\\.")[1]).append('-').append(filename.split("\\.")[0].substring(filename.indexOf('_') + 1));
            return sb.toString();
        }
        return "";
    }
}
