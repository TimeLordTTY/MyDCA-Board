package com.timelordtty.dca.controller;

import com.timelordtty.dca.dto.RiskWatchDTO.*;
import com.timelordtty.dca.service.RiskWatchService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v2/risk-watch-rules")
public class RiskWatchController {
    private final RiskWatchService service;
    public RiskWatchController(RiskWatchService service) { this.service=service; }
    @PostMapping public Rule create(@RequestBody Config request) throws IOException { return service.create(request); }
    @GetMapping public List<Rule> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) throws IOException { return service.list(page,size); }
    @GetMapping("/{id}") public Rule detail(@PathVariable String id) throws IOException { return service.detail(id); }
    @PatchMapping("/{id}") public Rule edit(@PathVariable String id,@RequestBody Config request) throws IOException { return service.edit(id,request); }
    @PostMapping("/{id}/evaluate") public Snapshot evaluate(@PathVariable String id) throws IOException { return service.evaluate(id); }
    @GetMapping("/{id}/snapshots") public List<Snapshot> history(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) throws IOException { return service.history(id,page,size); }
    @GetMapping("/{id}/events") public List<Event> events(@PathVariable String id,@RequestParam(defaultValue="false") boolean unresolved,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) throws IOException { return service.events(id,unresolved,page,size); }
    @PatchMapping("/{id}/events/{fingerprint}/acknowledge") public void acknowledge(@PathVariable String id,@PathVariable String fingerprint) throws IOException { service.acknowledge(id,fingerprint); }
    @PatchMapping("/{id}/mute") public void mute(@PathVariable String id,@RequestBody MuteRequest request) throws IOException { service.mute(id,request.mutedUntil()); }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,IOException.class})
    public ResponseEntity<Map<String,String>> error(Exception e) {
        return ResponseEntity.status(e instanceof IOException?503:e instanceof IllegalStateException?409:400)
                .body(Map.of("message",e instanceof IOException?"风险观察暂不可用，请稍后重试":e.getMessage()));
    }
}
