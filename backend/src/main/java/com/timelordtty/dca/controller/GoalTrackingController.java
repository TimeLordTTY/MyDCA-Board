package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.GoalTrackingDTO.*;
import com.timelordtty.dca.service.GoalTrackingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2/goals")
public class GoalTrackingController {
    private final GoalTrackingService service;
    public GoalTrackingController(GoalTrackingService service) { this.service=service; }
    @PostMapping public Goal create(@RequestBody Config c) throws IOException { return service.create(c); }
    @GetMapping public List<Goal> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) throws IOException { return service.list(page,size); }
    @GetMapping("/{id}") public Goal detail(@PathVariable String id) throws IOException { return service.detail(id); }
    @PatchMapping("/{id}") public Goal edit(@PathVariable String id,@RequestBody Config c) throws IOException { return service.edit(id,c); }
    @GetMapping("/{id}/progress") public Progress progress(@PathVariable String id) throws IOException { return service.progress(id); }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        return ResponseEntity.status(e instanceof IOException?503:e instanceof IllegalStateException?409:400)
                .body(Map.of("message",e instanceof IOException?"目标进度暂不可用，请稍后重试":e.getMessage()));
    }
}
