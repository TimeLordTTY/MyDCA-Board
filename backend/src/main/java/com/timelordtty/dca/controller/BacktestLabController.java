package com.timelordtty.dca.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.BacktestResultDTO;
import com.timelordtty.dca.service.BacktestLabService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/backtest-lab")
public class BacktestLabController {
    private final BacktestLabService service;
    private final ObjectMapper mapper = new ObjectMapper();
    public BacktestLabController(BacktestLabService service) { this.service = service; }

    public record RunRequest(String data, String strategy, String version, Map<String, Object> params) {}

    @GetMapping("/datasets")
    public List<String> datasets() throws IOException { return service.datasets(); }

    @GetMapping("/recent")
    public List<BacktestResultDTO> recent() { return service.recent().stream().map(this::dto).toList(); }

    @PostMapping("/runs")
    public BacktestResultDTO run(@RequestBody RunRequest request) throws Exception {
        return dto(service.run(request.data(), request.strategy(), request.version(), request.params()));
    }

    private BacktestResultDTO dto(JsonNode result) { return mapper.convertValue(result, BacktestResultDTO.class); }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, IOException.class, InterruptedException.class})
    public ResponseEntity<Map<String, String>> error(Exception error) {
        String message = error instanceof IOException || error instanceof InterruptedException
                ? "回测服务暂不可用，请稍后重试" : error.getMessage();
        return ResponseEntity.badRequest().body(Map.of("message", message == null ? "回测失败" : message));
    }
}
