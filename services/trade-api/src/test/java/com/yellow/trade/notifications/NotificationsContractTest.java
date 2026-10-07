package com.yellow.trade.notifications;

import com.yellow.trade.modules.ModuleContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** openapi/notifications.yaml, written before the controller, is what the code is held to. */
class NotificationsContractTest {

    private static ModuleContract contract() throws IOException {
        return ModuleContract.of("openapi/notifications.yaml");
    }

    @Test
    @DisplayName("describes exactly the routes the controller serves, and no route delivers anything")
    void routes() throws IOException {
        assertThat(contract().describedRoutes(), is(ModuleContract.servedRoutes(NotificationsController.class)));
    }

    @Test
    @DisplayName("each schema's properties are the record's fields")
    void schemas() throws IOException {
        assertThat(contract().properties("Notification"), is(ModuleContract.fields(Notification.class)));
        assertThat(contract().properties("UnreadCount"), is(ModuleContract.fields(UnreadCount.class)));
    }

    @Test
    @DisplayName("the kinds and the delivery states are the ones the code knows")
    void enums() {
        assertThat(names(NotificationKind.values()),
                is(Set.of("ORDER_FILLED", "ORDER_REJECTED", "ORDER_CANCELLED", "PRICE_ALERT")));
        assertThat(names(DeliveryStatus.values()), is(Set.of("QUEUED", "SENT", "FAILED")));
    }

    @Test
    @DisplayName("the code this module adds is in the file's catalogue")
    void errorCode() throws IOException {
        assertThat(contract().errorCodes(), hasItem(NotificationNotFoundException.CODE));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }
}
