package dev.universaladmin.status;

/**
 * Report counts for displays. Provided by the Reports module through the
 * {@link dev.universaladmin.core.ServiceRegistry}; answers from memory
 * (refreshed every few seconds and whenever a report changes), safe on the
 * main thread.
 *
 * @since 1.2
 */
public interface ReportOverview {

    /** Reports that are open or being worked on. */
    int openReports();
}
