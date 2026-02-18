package com.example.trader.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;

public record UpdateNodePositionReq(
        @NotNull @JsonSetter(nulls = Nulls.FAIL) Double x,
        @NotNull @JsonSetter(nulls = Nulls.FAIL) Double y
) {}
