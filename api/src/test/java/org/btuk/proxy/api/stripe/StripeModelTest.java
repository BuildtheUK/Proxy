package org.btuk.proxy.api.stripe;

import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.InvoiceLineItemCollection;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StripeModelTest {

    @Test
    public void testCheckoutSessionGrantOneTime() {
        AtomicInteger grantedMonths = new AtomicInteger(0);
        UUID testUuid = UUID.randomUUID();

        RoleManager mockRoleManager = new RoleManager() {
            @Override
            public CompletableFuture<Boolean> addTemporaryRoleMonths(UUID uuid, String roleName, int months) {
                grantedMonths.addAndGet(months);
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> addTemporaryRoleYears(UUID uuid, String roleName, int years) {
                grantedMonths.addAndGet(years * 12);
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> addTemporaryRole(UUID uuid, String roleName, Duration duration) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> removeRole(UUID uuid, String roleName) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> reduceTemporaryRoleMonths(UUID uuid, String roleName, int months) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> reduceTemporaryRoleYears(UUID uuid, String roleName, int years) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<Boolean> reduceTemporaryRole(UUID uuid, String roleName, Duration duration) {
                return CompletableFuture.completedFuture(true);
            }
        };

        MinecraftUserResolver mockResolver = new MinecraftUserResolver(null, null, null) {
            @Override
            public Optional<UUID> resolve(String usernameOrUuid) {
                return Optional.of(testUuid);
            }
        };

        StripeApi api = new StripeApi(null, mockRoleManager, mockResolver, null);

        Session session = new Session();
        session.setId("cs_test_123");
        session.setMode("payment");
        session.setClientReferenceId("Steve");
        Map<String, String> meta = new HashMap<>();
        meta.put("product", "role-quarter");
        session.setMetadata(meta);

        api.handleCheckoutSessionCompleted(session);

        assertEquals(3, grantedMonths.get());
    }

    @Test
    public void testInvoicePaidDurationCalculation() {
        StripeApi api = new StripeApi();

        Invoice invoice = new Invoice();
        InvoiceLineItemCollection lines = new InvoiceLineItemCollection();
        List<InvoiceLineItem> data = new ArrayList<>();

        InvoiceLineItem item = new InvoiceLineItem();
        item.setDescription("Subscription: role-yearly");
        data.add(item);
        lines.setData(data);
        invoice.setLines(lines);

        int months = api.calculateInvoiceDurationMonths(invoice);
        assertEquals(12, months);
    }
}
