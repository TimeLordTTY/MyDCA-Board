package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.MultiGoalScenarioDTO.*;
import com.timelordtty.dca.service.MultiGoalScenarioService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/goals/scenarios")
public class MultiGoalScenarioController {
    private final MultiGoalScenarioService service;
    public MultiGoalScenarioController(MultiGoalScenarioService service) { this.service=service; }
    @PostMapping("/compare")
    public Result compare(@RequestBody Request request) throws IOException { return service.compare(request); }
    @ExceptionHandler({IllegalArgumentException.class,IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        return ResponseEntity.status(e instanceof IOException?503:400)
                .body(Map.of("message",e instanceof IOException?"情景读取暂不可用":e.getMessage()));
    }
}
