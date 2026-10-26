package com.vertex.csms.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShopController {

    private final ShopProperties shop;

    public ShopController(ShopProperties shop) {
        this.shop = shop;
    }

    @GetMapping("/api/v1/public/shop")
    public ShopProperties shop() {
        return shop;
    }
}
