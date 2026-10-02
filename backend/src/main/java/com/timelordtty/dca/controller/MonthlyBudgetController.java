package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.MonthlyBudgetDTO.*;
import com.timelordtty.dca.service.MonthlyBudgetService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2/monthly-budgets")
public class MonthlyBudgetController {
    private final MonthlyBudgetService service;
    public MonthlyBudgetController(MonthlyBudgetService service) { this.service=service; }
    @PostMapping public Budget create(@RequestBody Config c) throws IOException { return service.create(c); }
    @GetMapping public List<Budget> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) throws IOException { return service.list(page,size); }
    @GetMapping("/{id}") public Budget detail(@PathVariable String id) throws IOException { return service.detail(id); }
    @PatchMapping("/{id}") public Budget edit(@PathVariable String id,@RequestBody Config c) throws IOException { return service.edit(id,c); }
    @GetMapping("/{id}/comparison") public Comparison comparison(@PathVariable String id) throws IOException { return service.comparison(id); }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        return ResponseEntity.status(e instanceof IOException?503:e instanceof IllegalStateException?409:400)
                .body(Map.of("message",e instanceof IOException?"预算对比暂不可用，请稍后重试":e.getMessage()));
    }
}
