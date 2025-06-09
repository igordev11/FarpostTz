package com.farpost;


import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length != 4) {
            printUsageAndExit();
        }

        double availability = -1;
        long responseTime = -1;

        for (int i = 0; i < args.length; i += 2) {
            String flag = args[i];
            String value = args[i + 1];
            try {
                if ("-u".equals(flag)) {
                    availability = Double.parseDouble(value);
                } else if ("-t".equals(flag)) {
                    responseTime = Long.parseLong(value);
                } else {
                    printUsageAndExit();
                }
            } catch (NumberFormatException e) {
                System.err.println("Ошибка: неверный формат числового значения для " + flag);
                printUsageAndExit();
            }
        }

        if (availability < 0 || responseTime < 0) {
            printUsageAndExit();
        }

        try {
            Analyzer analyzer = new Analyzer(System.in, availability, responseTime);
            analyzer.run();
        } catch (IOException e) {
            System.err.println("Ошибка чтения из входного потока: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Использование: java -jar analyze.jar -u <доступность> -t <время_ответа_мс>");
        System.err.println("Пример: cat access.log | java -jar analyze.jar -u 99.9 -t 45");
        System.exit(1);
    }
}
