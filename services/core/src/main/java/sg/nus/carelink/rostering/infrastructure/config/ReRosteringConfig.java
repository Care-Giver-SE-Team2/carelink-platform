package sg.nus.carelink.rostering.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import sg.nus.carelink.rostering.application.RosterLookbacks;
import sg.nus.carelink.rostering.domain.model.FamilyResponseWindow;

/**
 * Turns the configured UC-MG04 numbers into the plain objects the application and domain layers
 * take, so neither has to know Spring configuration exists.
 */
@Configuration
@EnableConfigurationProperties(ReRosteringProperties.class)
public class ReRosteringConfig {

	@Bean
	FamilyResponseWindow familyResponseWindow(ReRosteringProperties properties) {
		return new FamilyResponseWindow(properties.getFamilyWindow(), properties.getDecisionLead());
	}

	@Bean
	RosterLookbacks rosterLookbacks(ReRosteringProperties properties) {
		return new RosterLookbacks(properties.getContinuityLookback(), properties.getSpotCheckLookback());
	}
}
