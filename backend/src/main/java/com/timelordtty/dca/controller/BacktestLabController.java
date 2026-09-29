package com.timelordtty.dca.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.BacktestResultDTO;
import com.timelordtty.dca.dto.BacktestRunDTO;
import com.timelordtty.dca.service.BacktestLabService;
import com.timelordtty.dca.service.BacktestCompareService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/backtest-lab")
public class BacktestLabController {
    private final BacktestLabService service;
    private final BacktestCompareService compareService;
    private final ObjectMapper mapper = new ObjectMapper();
    public BacktestLabController(BacktestLabService service, BacktestCompareService compareService) {
        this.service = service;
        this.compareService = compareService;
    }

    public record RunRequest(String data, String strategy, String version, Map<String, Object> params) {}
    public record CompareRequest(List<String> runIds) {}

    @GetMapping("/datasets")
    public List<String> datasets() throws IOException { return service.datasets(); }

    @GetMapping("/recent")
    public List<BacktestResultDTO> recent() throws IOException { return service.recent().stream().map(this::dto).toList(); }

    @GetMapping("/runs")
    public List<BacktestRunDTO> history(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {
        return service.history(page, size);
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<BacktestRunDTO> detail(@PathVariable String id) throws IOException {
        BacktestRunDTO run = service.detail(id);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(run);
    }

    @PostMapping("/runs")
    public BacktestResultDTO run(@RequestBody RunRequest request) throws Exception {
        return dto(service.run(request.data(), request.strategy(), request.version(), request.params()));
    }

    @PostMapping("/runs/compare")
    public JsonNode compare(@RequestBody CompareRequest request) throws IOException {
        return compareService.compare(request.runIds());
    }

    private BacktestResultDTO dto(JsonNode result) { return mapper.convertValue(result, BacktestResultDTO.class); }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, IOException.class,
            InterruptedException.class, java.util.concurrent.TimeoutException.class})
    public ResponseEntity<Map<String, String>> error(Exception error) {
        String message = error instanceof IOException || error instanceof InterruptedException
                ? "回测服务暂不可用，请稍后重试" : error.getMessage();
        HttpStatus status = error instanceof IOException || error instanceof InterruptedException
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("message", message == null ? "回测失败" : message));
    }
}
