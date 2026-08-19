package com.project.notifierx.dto;

import com.project.notifierx.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendNotificationRequest(

        @NotNull(message = "channel must not be null")
        ChannelType channel,

        @NotBlank(message = "recipient must not be blank")
        String recipient,

        @NotBlank(message = "payload must not be blank")
        String payload
) {}