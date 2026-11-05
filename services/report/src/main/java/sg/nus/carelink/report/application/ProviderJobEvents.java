package sg.nus.carelink.report.application;

import sg.nus.carelink.report.domain.model.ProviderJob;

/** Application port: tells notification what became of a provider's job, in the transaction that changed it. */
public interface ProviderJobEvents {

    void changed(ProviderJob job);
}
