package com.vertex.csms.billing;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.vertex.csms.product.TaxClass;

/** Loads slabs from the gst_slab table, so a GST rate change is a data change, not a code change. */
@Service
public class GstSlabService {

    private final GstSlabRepository repo;

    public GstSlabService(GstSlabRepository repo) {
        this.repo = repo;
    }

    public List<TaxCalculator.Slab> slabs(TaxClass taxClass, LocalDate day) {
        return repo.findInForce(taxClass, day).stream()
                .map(s -> new TaxCalculator.Slab(s.getMinTaxable(), s.getMaxTaxable(), s.getRate()))
                .toList();
    }
}
