package com.yellow.trade.mappers;

/** An unpublished outbox_event, as the relay sends it. */
public class OutboxRow {

    private String eventId;
    private String topic;
    private String messageKey;
    private String envelope;
    private int attempts;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getMessageKey() { return messageKey; }
    public void setMessageKey(String messageKey) { this.messageKey = messageKey; }
    public String getEnvelope() { return envelope; }
    public void setEnvelope(String envelope) { this.envelope = envelope; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
