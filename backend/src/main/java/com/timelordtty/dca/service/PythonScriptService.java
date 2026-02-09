package com.timelordtty.dca.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Python脚本执行服务
 * 用于调用Python脚本执行行情数据采集等任务
 */
@Service
public class PythonScriptService {

    private static final Logger logger = LoggerFactory.getLogger(PythonScriptService.class);

    // 项目根目录（相对于jar包或工作目录）
    private static final String PROJECT_ROOT = detectProjectRoot();
    private static final String PYTHON_CMD = detectPythonCommand(); // 自动检测python或python3

    /**
     * 检测项目根目录
     * 通过检查 scripts 目录是否存在来定位项目根目录
     */
    private static String detectProjectRoot() {
        String userDir = System.getProperty("user.dir");
        Path currentPath = Paths.get(userDir);
        
        // 检查当前目录及其父目录，找到包含 scripts 目录的项目根目录
        Path checkPath = currentPath;
        int maxDepth = 5; // 最多向上查找5级，防止无限循环
        
        for (int i = 0; i < maxDepth; i++) {
            // 检查是否存在 scripts/market 目录
            Path scriptsPath = checkPath.resolve("scripts").resolve("market");
            if (java.nio.file.Files.exists(scriptsPath) && java.nio.file.Files.isDirectory(scriptsPath)) {
                if (!checkPath.equals(currentPath)) {
                    logger.info("检测到项目根目录: {} (从 {} 向上查找)", checkPath, currentPath);
                } else {
                    logger.info("使用当前目录作为项目根目录: {}", checkPath);
                }
                return checkPath.toString();
            }
            
            // 向上查找
            Path parentPath = checkPath.getParent();
            if (parentPath == null || parentPath.equals(checkPath)) {
                break; // 已到达根目录
            }
            checkPath = parentPath;
        }
        
        // 如果找不到，使用当前目录（可能是从项目根目录启动）
        logger.warn("未找到 scripts/market 目录，使用当前目录作为项目根目录: {}", userDir);
        return userDir;
    }

    /**
     * 检测Python命令
     * 优先尝试python3，如果不存在则尝试python
     */
    private static String detectPythonCommand() {
        String[] commands = {"python3", "python"};
        for (String cmd : commands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    logger.info("检测到Python命令: {}", cmd);
                    return cmd;
                }
            } catch (Exception e) {
                // 继续尝试下一个命令
            }
        }
        // 如果都检测不到，默认使用python3（Linux常见）
        logger.warn("无法检测Python命令，默认使用python3");
        return "python3";
    }

    /**
     * 执行Python脚本（同步）
     *
     * @param scriptPath 脚本路径（相对于项目根目录）
     * @param args 脚本参数
     * @return 执行结果（标准输出）
     * @throws IOException 执行失败
     */
    public String executeScript(String scriptPath, String... args) throws IOException {
        Path fullPath = Paths.get(PROJECT_ROOT, scriptPath);
        List<String> command = new ArrayList<>();
        command.add(PYTHON_CMD);
        command.add(fullPath.toString());
        for (String arg : args) {
            command.add(arg);
        }

        logger.info("执行Python脚本: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // 合并错误流到标准输出
        
        // 设置环境变量，确保Python输出UTF-8编码（Windows兼容）
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");  // Python 3.7+
        
        try {
            Process process = processBuilder.start();
            
            // 读取输出（使用UTF-8编码，Windows兼容）
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Python输出: {}", line);
                }
            }

            int exitCode = process.waitFor();
            String outputStr = output.toString();

            if (exitCode != 0) {
                logger.error("Python脚本执行失败，退出码: {}, 输出: {}", exitCode, outputStr);
                throw new IOException("Python脚本执行失败: " + outputStr);
            }

            logger.info("Python脚本执行成功");
            return outputStr;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python脚本执行被中断", e);
        }
    }

    /**
     * 执行历史行情补齐脚本
     *
     * @return 执行结果
     */
    public String backfillMarketHistory() {
        try {
            return executeScript("scripts/market/backfill_fund_nav_history.py");
        } catch (IOException e) {
            logger.error("历史行情补齐失败", e);
            return "失败: " + e.getMessage();
        }
    }

    /**
     * 执行基金净值采集脚本
     *
     * @return 执行结果
     */
    public String collectFundNav() {
        try {
            return executeScript("scripts/market/fund_collector.py");
        } catch (IOException e) {
            logger.error("基金净值采集失败", e);
            return "失败: " + e.getMessage();
        }
    }

    /**
     * 执行ETF实时行情采集脚本
     *
     * @return 执行结果
     */
    public String collectETFRealtime() {
        try {
            return executeScript("scripts/market/etf_collector.py", "--type", "realtime");
        } catch (IOException e) {
            logger.error("ETF实时行情采集失败", e);
            return "失败: " + e.getMessage();
        }
    }

    /**
     * 执行ETF日K线采集脚本
     *
     * @return 执行结果
     */
    public String collectETFDaily() {
        try {
            return executeScript("scripts/market/etf_collector.py", "--type", "daily");
        } catch (IOException e) {
            logger.error("ETF日K线采集失败", e);
            return "失败: " + e.getMessage();
        }
    }
}
