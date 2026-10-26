package com.vertex.csms.customer;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vertex.csms.common.ApiException;
import com.vertex.csms.security.CurrentUser;

@Service
public class CustomerService {

    private static final String MOBILE_RULE = "^[6-9][0-9]{9}$";
    private static final String NAME_RULE = "^[A-Za-z .()]{2,80}$";

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public record CustomerRequest(String name, String mobile, String area, LocalDate birthday, LocalDate anniversary,
                                  Boolean waOptIn, ConsentMethod consentMethod, BigDecimal creditLimit) {}

    public record ConsentRequest(boolean optIn, ConsentMethod method) {}

    public static String normaliseMobile(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        return digits;
    }

    public static void checkMobile(String mobile) {
        if (!mobile.matches(MOBILE_RULE)) {
            throw ApiException.badRequest("ERR-CUS-001", "Mobile number must be 10 digits and start with 6, 7, 8 or 9.", "mobile");
        }
    }

    /** Register a customer at the counter (REQ-CUS-1/2, SRS 10.3.2 validation). */
    @Transactional
    public Customer create(CustomerRequest req) {
        String mobile = normaliseMobile(req.mobile());
        checkMobile(mobile);
        if (customers.existsByMobile(mobile)) {
            throw ApiException.conflict("ERR-CUS-002", "Mobile number " + mobile + " is already registered.", "mobile");
        }
        Customer c = new Customer();
        c.setMobile(mobile);
        applyDetails(c, req);
        if (Boolean.TRUE.equals(req.waOptIn())) {
            if (req.consentMethod() == null) {
                throw ApiException.badRequest("ERR-VAL-001", "Choose how the customer gave WhatsApp consent.", "consentMethod");
            }
            c.setConsent(true, req.consentMethod(), CurrentUser.id());
        }
        return customers.save(c);
    }

    @Transactional
    public Customer update(Long id, CustomerRequest req) {
        Customer c = get(id);
        applyDetails(c, req);
        return c;
    }

    @Transactional
    public Customer setConsent(Long id, ConsentRequest req) {
        Customer c = get(id);
        if (req.optIn() && req.method() == null) {
            throw ApiException.badRequest("ERR-VAL-001", "Choose how the customer gave WhatsApp consent.", "method");
        }
        c.setConsent(req.optIn(), req.method(), CurrentUser.id());
        return c;
    }

    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("INFO-CUS-001", "Customer not found."));
    }

    private void applyDetails(Customer c, CustomerRequest req) {
        String name = req.name() == null ? "" : req.name().trim().replaceAll("\\s+", " ");
        if (!name.matches(NAME_RULE)) {
            throw ApiException.badRequest("ERR-VAL-001", "Name must be 2-80 letters (spaces and . allowed).", "name");
        }
        if (req.area() != null && req.area().length() > 60) {
            throw ApiException.badRequest("ERR-VAL-001", "Area can be at most 60 characters.", "area");
        }
        if (req.birthday() != null && req.birthday().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("ERR-VAL-001", "Birthday cannot be in the future.", "birthday");
        }
        c.setName(name);
        c.setArea(req.area() == null || req.area().isBlank() ? null : req.area().trim());
        c.setBirthday(req.birthday());
        c.setAnniversary(req.anniversary());
        // Only the Owner decides credit limits.
        if (req.creditLimit() != null && CurrentUser.isOwner()) {
            if (req.creditLimit().signum() < 0) {
                throw ApiException.badRequest("ERR-VAL-001", "Credit limit cannot be negative.", "creditLimit");
            }
            c.setCreditLimit(req.creditLimit());
        }
    }
}
