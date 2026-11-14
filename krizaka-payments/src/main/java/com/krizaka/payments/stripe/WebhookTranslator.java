package com.krizaka.payments.stripe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.krizaka.payments.PaymentEvent;
import com.krizaka.payments.PaymentsException;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The processor's event, in the domain's words. Anything the domain does not need is {@code
 * ignored}.
 */
public final class WebhookTranslator {

  private static final Set<String> LIVE = Set.of("active", "trialing");

  private WebhookTranslator() {}

  /**
   * Translates an event.
   *
   * @param rawBody the event's JSON
   * @return the domain's event
   * @throws PaymentsException 400 when the body is not an event
   */
  public static PaymentEvent translate(String rawBody) {
    JsonObject event = parse(rawBody);
    String id = text(event, "id");
    String type = text(event, "type");
    if (id == null || type == null) {
      throw new PaymentsException(400, "The body is not an event");
    }
    return dispatch(id, type, dataObject(event));
  }

  private static JsonObject parse(String rawBody) {
    try {
      return JsonParser.parseString(rawBody).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new PaymentsException(400, "The body is not an event", e);
    }
  }

  private static PaymentEvent dispatch(String id, String type, JsonObject object) {
    return switch (type) {
      case "checkout.session.completed" -> checkout(id, object);
      case "customer.subscription.created",
              "customer.subscription.updated",
              "customer.subscription.deleted" ->
          subscription(id, type, object);
      default -> new PaymentEvent.Ignored(id);
    };
  }

  private static JsonObject dataObject(JsonObject event) {
    JsonObject data = event.has("data") ? event.getAsJsonObject("data") : null;
    return data != null && data.has("object") ? data.getAsJsonObject("object") : new JsonObject();
  }

  private static PaymentEvent checkout(String id, JsonObject o) {
    JsonObject metadata = metadata(o);
    String accountId = text(metadata, "account_id");
    if (accountId == null) {
      accountId = text(o, "client_reference_id");
    }
    if (accountId == null) {
      return new PaymentEvent.Ignored(id);
    }
    String customerId = text(o, "customer");
    String mode = text(o, "mode");
    String kind = text(metadata, "kind");
    if ("payment".equals(mode) && "paid".equals(text(o, "payment_status")) && kind != null) {
      return new PaymentEvent.PaymentPaid(id, kind, accountId, customerId, text(o, "id"));
    }
    if ("subscription".equals(mode)) {
      return new PaymentEvent.SubscriptionStarted(
          id, accountId, customerId, text(o, "subscription"));
    }
    return new PaymentEvent.Ignored(id);
  }

  private static PaymentEvent subscription(String id, String type, JsonObject o) {
    String status =
        "customer.subscription.deleted".equals(type) ? "canceled" : orEmpty(text(o, "status"));
    return new PaymentEvent.SubscriptionChanged(
        id,
        text(metadata(o), "account_id"),
        text(o, "customer"),
        orEmpty(text(o, "id")),
        status,
        LIVE.contains(status),
        periodEnd(o));
  }

  private static @Nullable Long periodEnd(JsonObject o) {
    Long fromItem = firstItemPeriodEnd(o.get("items"));
    return fromItem != null ? fromItem : number(o, "current_period_end");
  }

  private static @Nullable Long firstItemPeriodEnd(@Nullable JsonElement items) {
    if (items == null || !items.isJsonObject()) {
      return null;
    }
    JsonElement data = items.getAsJsonObject().get("data");
    if (data == null || !data.isJsonArray() || data.getAsJsonArray().isEmpty()) {
      return null;
    }
    return number(data.getAsJsonArray().get(0), "current_period_end");
  }

  private static @Nullable Long number(JsonElement element, String key) {
    if (!element.isJsonObject()) {
      return null;
    }
    JsonElement value = element.getAsJsonObject().get(key);
    return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
        ? value.getAsLong()
        : null;
  }

  private static JsonObject metadata(JsonObject o) {
    JsonElement metadata = o.get("metadata");
    return metadata != null && metadata.isJsonObject()
        ? metadata.getAsJsonObject()
        : new JsonObject();
  }

  private static @Nullable String text(JsonObject o, String key) {
    JsonElement value = o.get(key);
    return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
        ? value.getAsString()
        : null;
  }

  private static String orEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
