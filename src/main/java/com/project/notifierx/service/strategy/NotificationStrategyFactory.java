package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import com.project.notifierx.exception.UnsupportedChannelException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationStrategyFactory {

    private final Map<ChannelType, NotificationStrategy> strategyMap;

    public NotificationStrategyFactory(List<NotificationStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationStrategy::supportsChannel,
                        Function.identity()
                ));
    }

    public NotificationStrategy getStrategy(ChannelType channel) {
        if (channel == null) {
            throw new UnsupportedChannelException("Channel must not be null");
        }
        NotificationStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new UnsupportedChannelException(channel);
        }
        return strategy;
    }
}