package com.yellow.trade.notifications;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.security.AccountNotReachableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationsController.class)
@Import(GlobalExceptionHandler.class)
class NotificationsControllerTest {

    private static final UUID ID = UUID.fromString("0b1d7c9e-1f2a-4b3c-8d4e-5f6a7b8c9d0e");

    @Autowired private MockMvc mvc;
    @MockitoBean private NotificationService notifications;

    @Test
    @DisplayName("answers the history newest first, in the contract's shape, the destination masked")
    void history() throws Exception {
        when(notifications.history(3L, 50)).thenReturn(List.of(new Notification(ID, NotificationKind.ORDER_FILLED,
                "Bought 2 ITC.NS at ₹399.50", "Your order to buy 2 ITC.NS was executed.", AlertChannel.EMAIL,
                "r•••@example.com", DeliveryStatus.SENT, Instant.parse("2026-10-06T10:00:00Z"),
                Instant.parse("2026-10-06T10:00:02Z"), null)));

        mvc.perform(get("/api/v1/accounts/3/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":"0b1d7c9e-1f2a-4b3c-8d4e-5f6a7b8c9d0e","kind":"ORDER_FILLED",
                          "subject":"Bought 2 ITC.NS at ₹399.50","body":"Your order to buy 2 ITC.NS was executed.",
                          "channel":"EMAIL","destination":"r•••@example.com","status":"SENT",
                          "createdAt":"2026-10-06T10:00:00Z","sentAt":"2026-10-06T10:00:02Z","readAt":null}]""", true));
    }

    @Test
    @DisplayName("a QUEUED notification has no channel yet: null, not a guess")
    void queuedHasNoChannel() throws Exception {
        when(notifications.history(3L, 10)).thenReturn(List.of(new Notification(ID, NotificationKind.ORDER_REJECTED,
                "Order rejected: buy 2 ITC.NS", "…", null, null, DeliveryStatus.QUEUED,
                Instant.parse("2026-10-06T10:00:00Z"), null, null)));

        mvc.perform(get("/api/v1/accounts/3/notifications").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channel").doesNotExist())
                .andExpect(jsonPath("$[0].status").value("QUEUED"));
    }

    @Test
    @DisplayName("a limit outside 1 to 100 is VAL-422, and nothing is read")
    void limitOutOfRange() throws Exception {
        for (String limit : new String[] {"0", "101", "lots"}) {
            mvc.perform(get("/api/v1/accounts/3/notifications").param("limit", limit))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("the unread count, for the bell")
    void unread() throws Exception {
        when(notifications.unread(3L)).thenReturn(new UnreadCount(2));

        mvc.perform(get("/api/v1/accounts/3/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"unread\":2}", true));
    }

    @Test
    @DisplayName("marking one read is 204")
    void markRead() throws Exception {
        mvc.perform(post("/api/v1/accounts/3/notifications/" + ID + "/read"))
                .andExpect(status().isNoContent());

        verify(notifications).markRead(3L, ID);
    }

    @Test
    @DisplayName("one not on this account is 404 NTF-404")
    void notFound() throws Exception {
        doThrow(new NotificationNotFoundException()).when(notifications).markRead(3L, ID);

        mvc.perform(post("/api/v1/accounts/3/notifications/" + ID + "/read"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"errorCode\":\"NTF-404\",\"message\":\"Notification not found\"}", true));
    }

    @Test
    @DisplayName("another account is 403 ACC-403, on every route")
    void anotherAccount() throws Exception {
        when(notifications.history(anyLong(), anyInt())).thenThrow(new AccountNotReachableException());
        when(notifications.unread(anyLong())).thenThrow(new AccountNotReachableException());
        doThrow(new AccountNotReachableException()).when(notifications).markRead(4L, ID);

        mvc.perform(get("/api/v1/accounts/4/notifications"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACC-403"));
        mvc.perform(get("/api/v1/accounts/4/notifications/unread"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACC-403"));
        mvc.perform(post("/api/v1/accounts/4/notifications/" + ID + "/read"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }

    @Test
    @DisplayName("an id that is not a UUID is VAL-422")
    void badId() throws Exception {
        mvc.perform(post("/api/v1/accounts/3/notifications/not-a-uuid/read"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }
}
