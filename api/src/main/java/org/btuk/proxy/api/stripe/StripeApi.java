package org.btuk.proxy.api.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.java.Log;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.role.RoleManager;
import org.btuk.proxy.core.service.MinecraftUserResolver;
import org.btuk.proxy.database.dto.StripePaymentDTO;
import org.btuk.proxy.database.sql.StripeSQL;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Log
@Path("/stripe")
public class StripeApi {

    private final StripeSQL stripeSQL;
    private final RoleManager roleManager;
    private final MinecraftUserResolver userResolver;
    private final Config config;

    public StripeApi() {
        this(null, null, null, null);
    }

    @Inject
    public StripeApi(StripeSQL stripeSQL, RoleManager roleManager, MinecraftUserResolver userResolver, Config config) {
        this.stripeSQL = stripeSQL;
        this.roleManager = roleManager;
        this.userResolver = userResolver;
        this.config = config;
    }

    @POST
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleWebhook(@Context HttpHeaders headers, InputStream requestBody) {
        // 1. Read the raw payload
        String payload;
        try {
            payload = new BufferedReader(new InputStreamReader(requestBody, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warning("Unable to read stripe webhook payload: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Unable to read request body")
                .build();
        }

        // 2. Get the Stripe-Signature header
        String sigHeader = headers.getHeaderString("Stripe-Signature");
        if (sigHeader == null) {
            log.warning("Stripe webhook received with missing Stripe-Signature header");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Missing Stripe-Signature header")
                .build();
        }

        // 3. Verify signature and construct the Event
        String secret = getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.severe("Stripe webhook secret is not configured in config or STRIPE_WEBHOOK_SECRET env var");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Webhook secret not configured")
                .build();
        }

        final Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, secret);
        } catch (SignatureVerificationException e) {
            log.warning("Stripe signature verification failed: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid signature")
                .build();
        }

        // 4. Idempotency check
        if (stripeSQL != null && stripeSQL.isEventProcessed(event.getId())) {
            log.info("Stripe event " + event.getId() + " already processed. Skipping.");
            return Response.ok("already processed").build();
        }

        // 5. Record event as RECEIVED in DB
        if (stripeSQL != null) {
            stripeSQL.recordEvent(event.getId(), event.getType(), "RECEIVED", System.currentTimeMillis());
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();
                    handleCheckoutSessionCompleted(session);
                }
                case "invoice.paid" -> {
                    Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();
                    handleInvoicePaid(invoice);
                }
                case "charge.refunded" -> {
                    Charge charge = (Charge) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();
                    handleChargeRefunded(charge);
                }
                case "customer.subscription.created",
                     "customer.subscription.updated",
                     "customer.subscription.deleted" -> {
                    Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();
                    handleSubscriptionChange(subscription);
                }
                case "invoice.payment_failed" -> {
                    Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();
                    handleInvoicePaymentFailed(invoice);
                }
                default -> {
                    log.fine("Unhandled Stripe event type: " + event.getType());
                }
            }

            // 6. Mark as successfully processed in DB
            if (stripeSQL != null) {
                stripeSQL.markEventProcessed(event.getId());
            }

        } catch (Exception e) {
            log.severe("Error processing Stripe event " + event.getId() + " (" + event.getType() + "): " + e.getMessage());
            if (stripeSQL != null) {
                stripeSQL.markEventFailed(event.getId(), e.getMessage());
            }
        }

        return Response.ok("success").build();
    }

    public void handleCheckoutSessionCompleted(Session session) {
        String username = extractMinecraftUsername(session);
        String customerId = session.getCustomer();
        String subscriptionId = session.getSubscription();

        log.info("Processing checkout.session.completed for session=" + session.getId() +
            ", customer=" + customerId + ", user=" + username + ", mode=" + session.getMode());

        Optional<UUID> resolvedUuid = resolveUuid(username);
        if (resolvedUuid.isEmpty()) {
            log.severe("Could not resolve Minecraft UUID for username '" + username + "' in checkout session " + session.getId());
            return;
        }

        UUID uuid = resolvedUuid.get();

        // Save customer mapping in DB
        if (stripeSQL != null && customerId != null) {
            stripeSQL.saveOrUpdateCustomer(customerId, uuid.toString(), username);
        }

        String mode = session.getMode();
        boolean isSubscription = "subscription".equalsIgnoreCase(mode) || subscriptionId != null;

        if (isSubscription) {
            int durationMonths = extractSessionDurationMonths(session);
            if (stripeSQL != null && subscriptionId != null) {
                stripeSQL.saveOrUpdateSubscription(
                    subscriptionId,
                    customerId,
                    uuid.toString(),
                    "active",
                    null,
                    durationMonths,
                    null,
                    null
                );
            }
            log.info("Saved subscription mapping for subscription " + subscriptionId + " -> player " + uuid + " (" + durationMonths + " months)");
        } else {
            // One-time payment (role-month = 1 month, role-quarter = 3 months)
            int durationMonths = extractSessionDurationMonths(session);
            String paymentId = session.getPaymentIntent() != null ? session.getPaymentIntent() : session.getId();
            long amount = session.getAmountTotal() != null ? session.getAmountTotal() : 0;
            String currency = session.getCurrency();
            long created = session.getCreated() != null ? session.getCreated() * 1000 : System.currentTimeMillis();

            if (stripeSQL != null) {
                stripeSQL.recordPayment(
                    paymentId,
                    null,
                    customerId,
                    null,
                    uuid.toString(),
                    "ONE_TIME",
                    amount,
                    currency,
                    durationMonths,
                    "PAID",
                    created
                );
            }

            if (roleManager != null) {
                String role = getRewardRoleName();
                if (durationMonths >= 12) {
                    roleManager.addTemporaryRoleYears(uuid, role, durationMonths / 12);
                    log.info("Granted temporary role '" + role + "' for " + (durationMonths / 12) + " year(s) to " + uuid + " (one-time payment)");
                } else {
                    roleManager.addTemporaryRoleMonths(uuid, role, durationMonths);
                    log.info("Granted temporary role '" + role + "' for " + durationMonths + " month(s) to " + uuid + " (one-time payment)");
                }
            }
        }
    }

    public void handleInvoicePaid(Invoice invoice) {
        String customerId = invoice.getCustomer();
        String subscriptionId = null;
        if (invoice.getParent() != null && "subscription_details".equals(invoice.getParent().getType())) {
            subscriptionId = invoice.getParent().getSubscriptionDetails().getSubscription();
        }
        String invoiceId = invoice.getId();
        String chargeId = null;
        if (invoice.getPayments() != null && invoice.getPayments().getData() != null && !invoice.getPayments().getData().isEmpty()) {
            InvoicePayment payment = invoice.getPayments().getData().getFirst();
            chargeId = payment.getId();
        }

        log.info("Processing invoice.paid: invoice=" + invoiceId + ", subscription=" + subscriptionId + ", customer=" + customerId);

        if (stripeSQL != null && stripeSQL.isPaymentProcessed(invoiceId)) {
            log.info("Invoice " + invoiceId + " already recorded as paid. Skipping grant.");
            return;
        }

        // Resolve Minecraft UUID
        UUID uuid = null;
        if (stripeSQL != null) {
            if (subscriptionId != null) {
                String subUuid = stripeSQL.getMinecraftUuidBySubscription(subscriptionId);
                if (subUuid != null) {
                    try {
                        uuid = UUID.fromString(subUuid);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (uuid == null && customerId != null) {
                String cusUuid = stripeSQL.getMinecraftUuidByCustomer(customerId);
                if (cusUuid != null) {
                    try {
                        uuid = UUID.fromString(cusUuid);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        if (uuid == null && invoice.getMetadata() != null) {
            String metaUser = invoice.getMetadata().get("minecraft_username");
            if (metaUser == null) metaUser = invoice.getMetadata().get("minecraft_uuid");
            if (metaUser != null) {
                Optional<UUID> resolved = resolveUuid(metaUser);
                if (resolved.isPresent()) {
                    uuid = resolved.get();
                }
            }
        }

        if (uuid == null) {
            log.severe("Cannot find Minecraft UUID for invoice " + invoiceId + " (customer: " + customerId + ", subscription: " + subscriptionId + ")");
            return;
        }

        int durationMonths = calculateInvoiceDurationMonths(invoice);
        long amount = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : 0;
        String currency = invoice.getCurrency();
        long created = invoice.getCreated() != null ? invoice.getCreated() * 1000 : System.currentTimeMillis();

        if (stripeSQL != null) {
            stripeSQL.recordPayment(
                invoiceId,
                chargeId,
                customerId,
                subscriptionId,
                uuid.toString(),
                subscriptionId != null ? "SUBSCRIPTION" : "ONE_TIME",
                amount,
                currency,
                durationMonths,
                "PAID",
                created
            );
        }

        if (roleManager != null) {
            String role = getRewardRoleName();
            if (durationMonths >= 12) {
                roleManager.addTemporaryRoleYears(uuid, role, durationMonths / 12);
                log.info("Granted temporary role '" + role + "' for " + (durationMonths / 12) + " year(s) to " + uuid + " (invoice.paid)");
            } else {
                roleManager.addTemporaryRoleMonths(uuid, role, durationMonths);
                log.info("Granted temporary role '" + role + "' for " + durationMonths + " month(s) to " + uuid + " (invoice.paid)");
            }
        }
    }

    public void handleChargeRefunded(Charge charge) {
        String chargeId = charge.getId();
        String paymentIntentId = charge.getPaymentIntent();
        String customerId = charge.getCustomer();

        log.info("Processing charge.refunded: charge=" + chargeId + ", paymentIntent=" + paymentIntentId + ", customer=" + customerId);

        StripePaymentDTO payment = null;
        if (stripeSQL != null) {
            payment = stripeSQL.getPaymentByChargeId(chargeId);
            if (payment == null && paymentIntentId != null) {
                payment = stripeSQL.getPayment(paymentIntentId);
            }
            if (payment == null && customerId != null) {
                payment = stripeSQL.getLatestPaymentByCustomer(customerId);
            }
        }

        if (payment != null) {
            try {
                UUID uuid = UUID.fromString(payment.minecraftUuid());
                int durationMonths = payment.durationDays(); // stored as months

                if (stripeSQL != null) {
                    stripeSQL.markPaymentRefunded(payment.id(), System.currentTimeMillis());
                }

                if (roleManager != null) {
                    String role = getRewardRoleName();
                    if (durationMonths >= 12) {
                        roleManager.reduceTemporaryRoleYears(uuid, role, durationMonths / 12);
                        log.info("Revoked/reduced temporary role '" + role + "' by " + (durationMonths / 12) + " year(s) for " + uuid + " due to refund");
                    } else {
                        roleManager.reduceTemporaryRoleMonths(uuid, role, durationMonths > 0 ? durationMonths : 1);
                        log.info("Revoked/reduced temporary role '" + role + "' by " + durationMonths + " month(s) for " + uuid + " due to refund");
                    }
                }
            } catch (Exception e) {
                log.severe("Failed to process refund for payment " + payment.id() + ": " + e.getMessage());
            }
        } else {
            log.warning("Received charge.refunded for charge " + chargeId + " but no matching payment found in DB");
        }
    }

    public void handleSubscriptionChange(Subscription subscription) {
        String subId = subscription.getId();
        String status = subscription.getStatus();
        SubscriptionItemCollection items = subscription.getItems();
        Long start = null;
        Long end = null;
        if (items != null && items.getData() != null && !items.getData().isEmpty()) {
            SubscriptionItem item = items.getData().getFirst();
            start = item.getCurrentPeriodStart() != null ? item.getCurrentPeriodStart() * 1000 : null;
            end = item.getCurrentPeriodEnd() != null ? item.getCurrentPeriodEnd() * 1000 : null;
        }

        log.info("Processing subscription change: " + subId + " -> status: " + status);

        if (stripeSQL != null) {
            stripeSQL.updateSubscriptionStatus(subId, status, start, end);
        }
    }

    public void handleInvoicePaymentFailed(Invoice invoice) {
        String invoiceId = invoice.getId();
        String subscriptionId = null;
        if (invoice.getParent() != null && "subscription_details".equals(invoice.getParent().getType())) {
            subscriptionId = invoice.getParent().getSubscriptionDetails().getSubscription();
        }
        String customerId = invoice.getCustomer();

        log.warning("Invoice payment failed: invoice=" + invoiceId + ", subscription=" + subscriptionId + ", customer=" + customerId);

        if (stripeSQL != null && subscriptionId != null) {
            stripeSQL.updateSubscriptionStatus(subscriptionId, "past_due", null, null);
        }
    }

    // --- Helper Methods ---

    public String extractMinecraftUsername(Session session) {
        if (session == null) return null;

        // 1. Custom fields in Stripe Checkout (e.g. 'Minecraft username')
        if (session.getCustomFields() != null) {
            for (Session.CustomField field : session.getCustomFields()) {
                String key = field.getKey();
                String label = field.getLabel() != null ? field.getLabel().getCustom() : null;

                boolean keyMatches = key != null && (key.equalsIgnoreCase("minecraft_username")
                    || key.equalsIgnoreCase("minecraftusername")
                    || key.toLowerCase(Locale.ROOT).contains("minecraft")
                    || key.equalsIgnoreCase("username"));

                boolean labelMatches = label != null && label.toLowerCase(Locale.ROOT).contains("minecraft");

                if (keyMatches || labelMatches) {
                    if (field.getText() != null && field.getText().getValue() != null && !field.getText().getValue().isBlank()) {
                        return field.getText().getValue().trim();
                    }
                }
            }
        }

        // 2. Metadata on Session
        if (session.getMetadata() != null) {
            String val = session.getMetadata().get("minecraft_username");
            if (val == null) val = session.getMetadata().get("minecraft_uuid");
            if (val == null) val = session.getMetadata().get("username");
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }

        // 3. Client reference ID
        if (session.getClientReferenceId() != null && !session.getClientReferenceId().isBlank()) {
            return session.getClientReferenceId().trim();
        }

        return null;
    }

    public Optional<UUID> resolveUuid(String usernameOrUuid) {
        if (userResolver != null) {
            return userResolver.resolve(usernameOrUuid);
        }
        if (usernameOrUuid != null) {
            try {
                return Optional.of(UUID.fromString(usernameOrUuid.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return Optional.empty();
    }

    public int parseDurationMonthsFromDescription(String desc) {
        if (desc == null || desc.isBlank()) return 0;
        String lower = desc.toLowerCase(Locale.ROOT).trim();

        if (lower.contains("role-yearly") || lower.contains("yearly") || lower.contains("annual") || lower.contains("1-year") || lower.contains("1 year")) {
            return 12; // 1 year
        }
        if (lower.contains("role-quarterly") || lower.contains("role-quarter") || lower.contains("quarterly") || lower.contains("quarter") || lower.contains("3-month") || lower.contains("3 month") || lower.contains("3-monthly") || lower.contains("3 monthly")) {
            return 3; // 1 quarter (3 months)
        }
        if (lower.contains("role-monthly") || lower.contains("role-month") || lower.contains("monthly") || lower.contains("1-month") || lower.contains("1 month")) {
            return 1; // 1 month
        }

        return 0;
    }

    public int extractSessionDurationMonths(Session session) {
        if (session == null) return 1;

        // Check metadata descriptions
        if (session.getMetadata() != null) {
            for (String val : session.getMetadata().values()) {
                int fromMeta = parseDurationMonthsFromDescription(val);
                if (fromMeta > 0) return fromMeta;
            }
            String monthsStr = session.getMetadata().get("duration_months");
            if (monthsStr == null) monthsStr = session.getMetadata().get("months");
            if (monthsStr != null) {
                try {
                    int m = Integer.parseInt(monthsStr.trim());
                    if (m > 0) return m;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Check custom field keys/labels
        if (session.getCustomFields() != null) {
            for (Session.CustomField field : session.getCustomFields()) {
                int fromKey = parseDurationMonthsFromDescription(field.getKey());
                if (fromKey > 0) return fromKey;
            }
        }

        // Check client reference ID
        if (session.getClientReferenceId() != null) {
            int fromRef = parseDurationMonthsFromDescription(session.getClientReferenceId());
            if (fromRef > 0) return fromRef;
        }

        return 1;
    }

    public int calculateInvoiceDurationMonths(Invoice invoice) {
        if (invoice == null) return 1;

        if (invoice.getLines() != null && invoice.getLines().getData() != null) {
            for (InvoiceLineItem line : invoice.getLines().getData()) {
                String desc = line.getDescription();
                if (desc != null) {
                    int fromDesc = parseDurationMonthsFromDescription(desc);
                    if (fromDesc > 0) {
                        return fromDesc;
                    }
                }

                // Check line item period start and end if available
                if (line.getPeriod() != null && line.getPeriod().getStart() != null && line.getPeriod().getEnd() != null) {
                    long diffSeconds = line.getPeriod().getEnd() - line.getPeriod().getStart();
                    long days = Math.round(diffSeconds / 86400.0);
                    if (days >= 300) {
                        return 12; // 1 year
                    } else if (days >= 75) {
                        return 3;  // 1 quarter (3 months)
                    } else if (days >= 20) {
                        return 1;  // 1 month
                    }
                }
            }
        }

        return 1;
    }

    private String getWebhookSecret() {
        if (config != null) {
            String secret = config.getString("stripe.webhook_secret");
            if (secret != null && !secret.isBlank()) {
                return secret.trim();
            }
        }
        String env = System.getenv("STRIPE_WEBHOOK_SECRET");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    private String getRewardRoleName() {
        if (config != null) {
            String role = config.getString("stripe.reward_role");
            if (role != null && !role.isBlank()) {
                return role.trim();
            }
        }
        return "reward";
    }
}
