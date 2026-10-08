package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.GoalForecastDTO.*;
import com.timelordtty.dca.service.GoalForecastService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/goals")
public class GoalForecastController {
    private final GoalForecastService service;
    public GoalForecastController(GoalForecastService service) { this.service=service; }
    /** POST carries bounded scenario inputs; transaction and behavior are read-only. */
    @PostMapping("/{id}/forecast")
    public Forecast forecast(@PathVariable String id,@RequestBody Request request) throws IOException {
        return service.forecast(id,request);
    }
    @ExceptionHandler({IllegalArgumentException.class,IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        return ResponseEntity.status(e instanceof IOException?503:400)
                .body(Map.of("message",e instanceof IOException?"预测读取暂不可用":e.getMessage()));
    }
}
