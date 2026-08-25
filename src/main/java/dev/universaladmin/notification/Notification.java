package dev.universaladmin.notification;

public record Notification(String title, String message, NotificationSeverity severity) {

    public static Notification info(String message) {
        return new Notification(null, message, NotificationSeverity.INFO);
    }
}
