package com.example.observability.model;

import java.math.BigDecimal;

public record Order(
    String id,
    String userId,
    BigDecimal amount,
    String status
) {}
