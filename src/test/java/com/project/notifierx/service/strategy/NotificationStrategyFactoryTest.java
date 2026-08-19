package com.project.notifierx.service.strategy;

import com.project.notifierx.domain.ChannelType;
import com.project.notifierx.exception.UnsupportedChannelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStrategyFactoryTest {

    @Mock
    private NotificationStrategy emailStrategy;

    @Mock
    private NotificationStrategy smsStrategy;

    @Mock
    private NotificationStrategy inAppStrategy;

    private NotificationStrategyFactory factory;

    @BeforeEach
    void setUp() {
        when(emailStrategy.supportsChannel()).thenReturn(ChannelType.EMAIL);
        when(smsStrategy.supportsChannel()).thenReturn(ChannelType.SMS);
        when(inAppStrategy.supportsChannel()).thenReturn(ChannelType.IN_APP);

        factory = new NotificationStrategyFactory(
                List.of(emailStrategy, smsStrategy, inAppStrategy));
    }

    @Test
    @DisplayName("getStrategy(EMAIL) returns the email strategy")
    void getStrategy_returnsEmailStrategy_forEmailChannel() {
        NotificationStrategy resolved = factory.getStrategy(ChannelType.EMAIL);
        assertThat(resolved).isSameAs(emailStrategy);
    }

    @Test
    @DisplayName("getStrategy(SMS) returns the SMS strategy")
    void getStrategy_returnsSmsStrategy_forSmsChannel() {
        NotificationStrategy resolved = factory.getStrategy(ChannelType.SMS);
        assertThat(resolved).isSameAs(smsStrategy);
    }

    @Test
    @DisplayName("getStrategy(IN_APP) returns the in-app strategy")
    void getStrategy_returnsInAppStrategy_forInAppChannel() {
        NotificationStrategy resolved = factory.getStrategy(ChannelType.IN_APP);
        assertThat(resolved).isSameAs(inAppStrategy);
    }

    @Test
    @DisplayName("getStrategy(null) throws UnsupportedChannelException")
    void getStrategy_throwsException_forNullChannel() {
        assertThatThrownBy(() -> factory.getStrategy(null))
                .isInstanceOf(UnsupportedChannelException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("getStrategy throws UnsupportedChannelException for an unmapped channel")
    void getStrategy_throwsException_forUnmappedChannel() {
        when(emailStrategy.supportsChannel()).thenReturn(ChannelType.EMAIL);
        NotificationStrategyFactory partialFactory =
                new NotificationStrategyFactory(List.of(emailStrategy));

        assertThatThrownBy(() -> partialFactory.getStrategy(ChannelType.SMS))
                .isInstanceOf(UnsupportedChannelException.class)
                .hasMessageContaining("SMS");
    }

    @Test
    @DisplayName("UnsupportedChannelException carries the offending channel")
    void getStrategy_exceptionCarriesChannel_forUnmappedChannel() {
        NotificationStrategyFactory partialFactory =
                new NotificationStrategyFactory(List.of(emailStrategy));

        UnsupportedChannelException ex = org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedChannelException.class,
                () -> partialFactory.getStrategy(ChannelType.IN_APP)
        );
        assertThat(ex.getChannel()).isEqualTo(ChannelType.IN_APP);
    }

    @Test
    @DisplayName("Factory construction fails fast when two strategies share the same channel")
    void constructor_throwsIllegalStateException_forDuplicateChannel() {
        NotificationStrategy duplicateEmail = org.mockito.Mockito.mock(NotificationStrategy.class);
        when(duplicateEmail.supportsChannel()).thenReturn(ChannelType.EMAIL);

        assertThatThrownBy(() ->
                new NotificationStrategyFactory(List.of(emailStrategy, duplicateEmail)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("All three channels are resolvable from a fully-populated factory")
    void getStrategy_resolvesAllChannels_fromFullyPopulatedFactory() {
        for (ChannelType channel : ChannelType.values()) {
            assertThat(factory.getStrategy(channel))
                    .as("Strategy for channel %s should be resolved", channel)
                    .isNotNull();
        }
    }
}