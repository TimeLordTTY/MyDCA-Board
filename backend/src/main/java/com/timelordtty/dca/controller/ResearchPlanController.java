package com.timelordtty.dca.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timelordtty.dca.service.ResearchPlanService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2/research-plans")
public class ResearchPlanController {
    private final ResearchPlanService service;
    public ResearchPlanController(ResearchPlanService service) { this.service = service; }
    @PostMapping public ObjectNode create(@RequestBody ResearchPlanService.Create request) throws IOException { return service.create(request); }
    @GetMapping public List<ObjectNode> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) throws IOException { return service.list(page, size); }
    @GetMapping("/{id}") public ResponseEntity<ObjectNode> detail(@PathVariable String id) throws IOException {
        var plan = service.detail(id); return plan == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(plan);
    }
    @PatchMapping("/{id}") public ObjectNode edit(@PathVariable String id, @RequestBody ResearchPlanService.Edit request) throws IOException { return service.edit(id, request); }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception error) {
        return ResponseEntity.status(error instanceof IOException ? 503 : error instanceof IllegalStateException ? 409 : 400)
                .body(Map.of("message", error instanceof IOException ? "研究方案暂不可用，请稍后重试" : error.getMessage()));
    }
}
