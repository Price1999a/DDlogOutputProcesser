package cn.edu.nju.stq;

import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

@Slf4j
public class Main {
    public static void main(String[] args) {
        run(args);
    }

    private static Options buildOption() {
        return new Options()
                .addOption("h", "打印帮助信息")
                .addOption("i", "input", true, "输入的压缩文件");
    }

    private static void run(String[] a) {
        CommandLineParser parser = new DefaultParser();
        Options options = buildOption();
        try {
            CommandLine commandLine = parser.parse(options, a);
            if (commandLine.hasOption('h') || !commandLine.hasOption('i')) {
                printHelpInfo(options);
            } else {
                log.info("input file: {}", commandLine.getOptionValue('i'));
                Processer processer = new Processer();
                processer.readInfoFromZip(commandLine.getOptionValue('i'));
                processer.printRes();
            }
        } catch (ParseException e) {
            printHelpInfo(options);
            throw new RuntimeException(e);
        }
    }

    private static void printHelpInfo(Options o) {
        new HelpFormatter().printHelp("java -jar DDlogOutputProcesser-0.1-SNAPSHOT.jar", o, true);
    }
}