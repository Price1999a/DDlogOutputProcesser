package cn.edu.nju.stq;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 主要处理两个部分的内容 首先是时间信息，其次就是output数量的统计
 */
public class Processor {
    public void readInfoFromZip(String fileName) {
        try (ZipFile zipFile = new ZipFile(fileName)) {
            zipFile.stream().parallel().forEach(System.out::println);
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
    private void processZipEntry(ZipEntry zipEntry) {
        // 我们可以实用化
    }
}
