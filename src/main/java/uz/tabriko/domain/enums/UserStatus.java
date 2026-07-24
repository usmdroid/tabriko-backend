package uz.tabriko.domain.enums;

public enum UserStatus {
    ACTIVE,
    BLOCKED,
    PENDING,
    SUSPENDED,
    // Hidden from the app (feeds/search/direct fetch) but kept in storage;
    // superadmin can still see it in the admin list to restore or delete.
    ARCHIVED,
    // Soft-deleted (reason + who + when recorded); listed only in the admin
    // "deleted accounts" tab. A future scheduled job will hard-purge these.
    DELETED
}
