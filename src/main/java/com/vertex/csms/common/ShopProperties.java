package com.vertex.csms.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "csms.shop")
public record ShopProperties(String name, String address, String gstin, String phone, String locationUrl) {}
