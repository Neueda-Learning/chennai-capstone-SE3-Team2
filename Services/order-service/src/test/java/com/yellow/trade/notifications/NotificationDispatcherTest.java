package com.yellow.trade.notifications;

import com.yellow.trade.notifications.NotificationExceptions.MailDeliveryException;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.preferences.api.ChannelResolver;
import com.yellow.trade.preferences.api.ResolvedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final String ADDRESS = "rohan.nair@example.com";

    @Mock private NotificationMapper mapper;

    private final Deque<Supplier<ResolvedChannel>> resolutions = new ArrayDeque<>();
    private final ChannelResolver resolver = accountId -> resolutions.pop().get();
    private final List<String> mailed = new ArrayList<>();
    private RuntimeException mailFailure;
    private final NotificationMailSender mail = (to, subject, body) -> {
        if (mailFailure != null) {
            throw mailFailure;
        }
        mailed.add(to + " | " + subject + " | " + body);
    };

    private NotificationDispatcher dispatcher() {
        return new NotificationDispatcher(mapper, resolver, mail, Clock.fixed(NOW, ZoneOffset.UTC), 20);
    }

    private static NotificationRow queued(int attempts) {
        NotificationRow row = new NotificationRow();
        row.setNotificationId(UUID.randomUUID());
        row.setClientId(3L);
        row.setKind("ORDER_FILLED");
        row.setSubject("Bought 2 TATASTEEL.NS at ₹178.72");
        row.setBody("Your order to buy 2 TATASTEEL.NS was executed.");
        row.setStatus("QUEUED");
        row.setAttempts(attempts);
        return row;
    }

    private void due(NotificationRow... rows) {
        when(mapper.lockDue(NOW, 20)).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("the channel comes from preferences at send time: email to the profile's address, recorded masked")
    void emailFromPreferences() {
        NotificationRow row = queued(0);
        due(row);
        resolutions.add(() -> new ResolvedChannel(AlertChannel.EMAIL, ADDRESS, false));

        dispatcher().dispatchOnce();

        assertThat(mailed, is(List.of(ADDRESS + " | Bought 2 TATASTEEL.NS at ₹178.72 | "
                + "Your order to buy 2 TATASTEEL.NS was executed.")));
        verify(mapper).markSent(row.getNotificationId(), "EMAIL", "r•••@example.com", NOW);
    }

    @Test
    @DisplayName("IN_APP: nothing is mailed, and the notification is delivered to the inbox")
    void inApp() {
        NotificationRow row = queued(0);
        due(row);
        resolutions.add(() -> new ResolvedChannel(AlertChannel.IN_APP, null, false));

        dispatcher().dispatchOnce();

        assertThat(mailed, is(empty()));
        verify(mapper).markSent(row.getNotificationId(), "IN_APP", null, NOW);
    }

    @Test
    @DisplayName("the preference changes between two messages: each goes where preferences said at its moment")
    void channelFollowsThePreference() {
        NotificationRow first = queued(0);
        NotificationRow second = queued(0);
        due(first, second);
        resolutions.add(() -> new ResolvedChannel(AlertChannel.EMAIL, ADDRESS, true));
        resolutions.add(() -> new ResolvedChannel(AlertChannel.IN_APP, null, false));

        dispatcher().dispatchOnce();

        assertThat(mailed.size(), is(1));
        verify(mapper).markSent(first.getNotificationId(), "EMAIL", "r•••@example.com", NOW);
        verify(mapper).markSent(second.getNotificationId(), "IN_APP", null, NOW);
    }

    @Test
    @DisplayName("preferences failing leaves it QUEUED and tried again later: never sent on a guess")
    void resolverDown() {
        NotificationRow row = queued(0);
        due(row);
        resolutions.add(() -> {
            throw new IllegalStateException("database connection lost");
        });

        dispatcher().dispatchOnce();

        assertThat(mailed, is(empty()));
        verify(mapper).retryLater(eq(row.getNotificationId()), anyString(), eq(NOW.plus(Duration.ofSeconds(30))));
        verify(mapper, never()).markSent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an account that does not exist fails at once: there is nobody to tell")
    void unknownAccount() {
        NotificationRow row = queued(0);
        due(row);
        resolutions.add(() -> {
            throw new ChannelResolver.UnknownAccountException(3L);
        });

        dispatcher().dispatchOnce();

        verify(mapper).markFailed(eq(row.getNotificationId()), isNull(), isNull(), anyString());
    }

    @Test
    @DisplayName("the mail server refusing is retried later, each wait longer than the last")
    void mailRefusedIsRetried() {
        NotificationRow row = queued(3);
        due(row);
        resolutions.add(() -> new ResolvedChannel(AlertChannel.EMAIL, ADDRESS, false));
        mailFailure = new MailDeliveryException("mail server did not accept the message", null);

        dispatcher().dispatchOnce();

        verify(mapper).retryLater(eq(row.getNotificationId()), anyString(), eq(NOW.plus(Duration.ofSeconds(120))));
    }

    @Test
    @DisplayName("on the fifth refusal it is FAILED, with the channel it was tried on, and its error names no address")
    void mailRefusedForGood() {
        NotificationRow row = queued(NotificationDispatcher.MAX_ATTEMPTS - 1);
        due(row);
        resolutions.add(() -> new ResolvedChannel(AlertChannel.EMAIL, ADDRESS, false));
        mailFailure = new MailDeliveryException("mail server did not accept the message",
                new RuntimeException("550 no such user " + ADDRESS));

        dispatcher().dispatchOnce();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(mapper).markFailed(eq(row.getNotificationId()), eq("EMAIL"), eq("r•••@example.com"), error.capture());
        assertThat(error.getValue(), containsString("mail server"));
        assertThat(error.getValue(), not(containsString(ADDRESS)));
    }

    @Test
    @DisplayName("one message failing does not hold up the next in the batch")
    void oneFailureDoesNotStopTheBatch() {
        NotificationRow failing = queued(0);
        NotificationRow fine = queued(0);
        due(failing, fine);
        resolutions.add(() -> {
            throw new IllegalStateException("blip");
        });
        resolutions.add(() -> new ResolvedChannel(AlertChannel.IN_APP, null, false));

        dispatcher().dispatchOnce();

        verify(mapper).markSent(fine.getNotificationId(), "IN_APP", null, NOW);
    }
}
