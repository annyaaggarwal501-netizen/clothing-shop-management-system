package com.vertex.csms.returns;

import org.springframework.web.bind.annotation.*;

import com.vertex.csms.common.ApiException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ReturnController {

    private final ReturnService service;
    private final CreditNoteRepository creditNotes;

    public ReturnController(ReturnService service, CreditNoteRepository creditNotes) {
        this.service = service;
        this.creditNotes = creditNotes;
    }

    @GetMapping("/returns/lookup")
    public ReturnService.Lookup lookup(@RequestParam String invoice) {
        return service.lookup(invoice);
    }

    @PostMapping("/returns")
    public ReturnService.ReturnResult create(@RequestBody @Valid ReturnService.ReturnRequest req) {
        return service.process(req);
    }

    /** Check a credit note before using it as payment at the POS. */
    @GetMapping("/credit-notes/{cnNo}")
    public CreditNote creditNote(@PathVariable String cnNo) {
        return creditNotes.findByCnNo(cnNo.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("ERR-VAL-001", "Credit note " + cnNo + " not found."));
    }
}
