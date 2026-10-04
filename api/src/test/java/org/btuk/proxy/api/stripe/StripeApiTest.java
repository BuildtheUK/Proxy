package org.btuk.proxy.api.stripe;

import com.stripe.model.checkout.Session;
import org.btuk.proxy.core.role.RoleManager;
import org.btuk.proxy.core.service.MinecraftUserResolver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StripeApiTest {

    @Test
    public void testDurationParsing() {
        StripeApi api = new StripeApi();

        assertEquals(1, api.parseDurationMonthsFromDescription("role-monthly"));
        assertEquals(3, api.parseDurationMonthsFromDescription("role-quarterly"));
        assertEquals(12, api.parseDurationMonthsFromDescription("role-yearly"));

        assertEquals(1, api.parseDurationMonthsFromDescription("role-month"));
        assertEquals(3, api.parseDurationMonthsFromDescription("role-quarter"));

        assertEquals(12, api.parseDurationMonthsFromDescription("Annual Subscription"));
        assertEquals(3, api.parseDurationMonthsFromDescription("3 months pass"));
        assertEquals(0, api.parseDurationMonthsFromDescription("random-product"));
    }

    @Test
    public void testExtractMinecraftUsernameFromCustomField() {
        StripeApi api = new StripeApi();
        Session session = new Session();

        Session.CustomField field = new Session.CustomField();
        field.setKey("minecraft_username");
        Session.CustomField.Text text = new Session.CustomField.Text();
        text.setValue("SteveCraft");
        field.setText(text);

        List<Session.CustomField> fields = new ArrayList<>();
        fields.add(field);
        session.setCustomFields(fields);

        String username = api.extractMinecraftUsername(session);
        assertEquals("SteveCraft", username);
    }

    @Test
    public void testExtractMinecraftUsernameFromMetadata() {
        StripeApi api = new StripeApi();
        Session session = new Session();

        Map<String, String> meta = new HashMap<>();
        meta.put("minecraft_username", "AlexBuilder");
        session.setMetadata(meta);

        String username = api.extractMinecraftUsername(session);
        assertEquals("AlexBuilder", username);
    }

    @Test
    public void testExtractMinecraftUsernameFromClientReferenceId() {
        StripeApi api = new StripeApi();
        Session session = new Session();
        session.setClientReferenceId("Notch");

        String username = api.extractMinecraftUsername(session);
        assertEquals("Notch", username);
    }

    @Test
    public void testExtractMinecraftUsernameEmpty() {
        StripeApi api = new StripeApi();
        Session session = new Session();
        assertNull(api.extractMinecraftUsername(session));
    }
}
