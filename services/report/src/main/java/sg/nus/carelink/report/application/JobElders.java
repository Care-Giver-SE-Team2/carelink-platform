package sg.nus.carelink.report.application;

/**
 * Application port: what a provider is told about the elder a job is for. Before it accepts, only
 * what it decides on, which names nobody; once it has accepted, where to go.
 */
public interface JobElders {

    Brief brief(Long elderId);

    Address address(Long elderId);

    /**
     * @param summary e.g. "Female, 87 · uses a walking aid"
     * @param district the postal district, two digits; empty when the elder's postal code is unknown
     */
    record Brief(String summary, String languages, String district, String areaName) {
    }

    record Address(String text, String accessNotes) {
    }
}
