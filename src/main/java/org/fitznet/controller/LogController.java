package org.fitznet.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/logs")
public class LogController {

    private static final String LOG_FILE_PATH = "logs/application.log";

    @GetMapping
    public String logsPage(Model model) {
        model.addAttribute("title", "FitzBot Logs");
        return "logs";
    }

    @GetMapping("/api/tail")
    @ResponseBody
    public LogResponse getTailLogs(@RequestParam(defaultValue = "100") int lines) {
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                return new LogResponse(Collections.singletonList("Log file not found: " + LOG_FILE_PATH), 0);
            }

            List<String> allLines = Files.readAllLines(logPath);
            int totalLines = allLines.size();

            // Get the last 'lines' number of lines
            List<String> tailLines = allLines.stream()
                    .skip(Math.max(0, totalLines - lines))
                    .collect(Collectors.toList());

            return new LogResponse(tailLines, totalLines);
        } catch (IOException e) {
            log.error("Error reading log file", e);
            return new LogResponse(Collections.singletonList("Error reading log file: " + e.getMessage()), 0);
        }
    }

    @GetMapping("/api/search")
    @ResponseBody
    public LogResponse searchLogs(@RequestParam String query,
                                 @RequestParam(defaultValue = "100") int maxResults) {
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                return new LogResponse(Collections.singletonList("Log file not found: " + LOG_FILE_PATH), 0);
            }

            List<String> matchingLines = Files.lines(logPath)
                    .filter(line -> line.toLowerCase().contains(query.toLowerCase()))
                    .limit(maxResults)
                    .collect(Collectors.toList());

            return new LogResponse(matchingLines, matchingLines.size());
        } catch (IOException e) {
            log.error("Error searching log file", e);
            return new LogResponse(Collections.singletonList("Error searching log file: " + e.getMessage()), 0);
        }
    }

    @GetMapping("/api/level/{level}")
    @ResponseBody
    public LogResponse getLogsByLevel(@PathVariable String level,
                                     @RequestParam(defaultValue = "100") int maxResults) {
        try {
            Path logPath = Paths.get(LOG_FILE_PATH);
            if (!Files.exists(logPath)) {
                return new LogResponse(Collections.singletonList("Log file not found: " + LOG_FILE_PATH), 0);
            }

            String levelPattern = level.toUpperCase();
            List<String> matchingLines = Files.lines(logPath)
                    .filter(line -> line.contains(levelPattern))
                    .limit(maxResults)
                    .collect(Collectors.toList());

            return new LogResponse(matchingLines, matchingLines.size());
        } catch (IOException e) {
            log.error("Error filtering logs by level", e);
            return new LogResponse(Collections.singletonList("Error filtering logs: " + e.getMessage()), 0);
        }
    }

    public static class LogResponse {
        public final List<String> lines;
        public final int totalCount;

        public LogResponse(List<String> lines, int totalCount) {
            this.lines = lines;
            this.totalCount = totalCount;
        }
    }
}
