package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.AllocationPolicyDTO.*;
import com.timelordtty.dca.service.AllocationPolicyService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2/allocation-policies")
public class AllocationPolicyController {
    private final AllocationPolicyService service;
    public AllocationPolicyController(AllocationPolicyService service) { this.service=service; }
    @PostMapping public Policy create(@RequestBody Config c) throws IOException { return service.create(c); }
    @GetMapping public List<Policy> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) throws IOException { return service.list(page,size); }
    @GetMapping("/{id}") public Policy detail(@PathVariable String id) throws IOException { return service.detail(id); }
    @PatchMapping("/{id}") public Policy edit(@PathVariable String id,@RequestBody Config c) throws IOException { return service.edit(id,c); }
    @PostMapping("/{id}/evaluate") public Evaluation evaluate(@PathVariable String id) throws IOException { return service.evaluate(id); }
    @GetMapping("/{id}/preview") public com.timelordtty.dca.dto.RebalancePreviewDTO preview(@PathVariable String id) throws IOException { return service.preview(id); }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        return ResponseEntity.status(e instanceof IOException?503:e instanceof IllegalStateException?409:400)
                .body(Map.of("message",e instanceof IOException?"配置观察暂不可用，请稍后重试":e.getMessage()));
    }
}
